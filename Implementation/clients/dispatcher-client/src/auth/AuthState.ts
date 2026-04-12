// Central auth state machine for the Dispatcher Client.
// Holds the access token in memory only. Orchestrates the OIDC boot flow,
// schedules token refresh, and emits AuthChangedEvent to subscribers.

import type { AppConfig } from '../config/AppConfig.ts';
import type { OidcDiscovery, PkceSession, TokenResponse, TokenSet } from './types.ts';
import type { AuthStatus } from './types.ts';
import { OidcClient } from './OidcClient.ts';

// sessionStorage key constants
const KEY_REFRESH_TOKEN = 'idispatchx.refresh_token';
const KEY_ID_TOKEN = 'idispatchx.id_token';
const KEY_LOGIN_AT = 'idispatchx.login_at';
const KEY_PKCE = 'idispatchx.pkce';
const KEY_DISCOVERY = 'idispatchx.oidc_discovery';

/** Maximum age for a cached OIDC discovery document (10 minutes). */
const DISCOVERY_CACHE_TTL_MS = 10 * 60 * 1000;

/** Refresh the access token this many seconds before it expires. */
const REFRESH_BEFORE_EXPIRY_S = 60;

/** Custom event emitted whenever auth status changes. */
export class AuthChangedEvent extends CustomEvent<AuthStatus> {
    static readonly TYPE = 'auth-changed' as const;

    constructor(status: AuthStatus) {
        super(AuthChangedEvent.TYPE, { detail: status, bubbles: false });
    }
}

/** Emitted when the session is within 5 minutes of expiring (idle or max-lifetime). */
export class SessionWarningEvent extends CustomEvent<{ reason: 'idle-timeout' | 'max-lifetime'; secondsRemaining: number }> {
    static readonly TYPE = 'session-warning' as const;

    constructor(reason: 'idle-timeout' | 'max-lifetime', secondsRemaining: number) {
        super(SessionWarningEvent.TYPE, { detail: { reason, secondsRemaining }, bubbles: false });
    }
}

export class AuthState extends EventTarget {
    // Access token is NEVER written to storage — memory only.
    #accessToken: string | null = null;
    #status: AuthStatus = { kind: 'unauthenticated' };
    #discovery: OidcDiscovery | null = null;
    #refreshTimerId: ReturnType<typeof setTimeout> | null = null;

    constructor(
        private readonly client: OidcClient,
        private readonly config: AppConfig,
    ) {
        super();
    }

    /**
     * Boot sequence. Called once by main.ts.
     * 1. Load/cache OIDC discovery.
     * 2. If ?code= in URL: exchange code (PKCE callback).
     * 3. Else if refresh token in sessionStorage: try refresh.
     * 4. Else: start PKCE redirect to OIDC provider.
     */
    async initialize(): Promise<void> {
        this.#emit({ kind: 'authenticating' });

        try {
            this.#discovery = await this.#loadDiscovery();
        } catch (err) {
            console.error('[AuthState] OIDC discovery failed:', err);
            // Emit a recoverable expired state so AppShell shows the login screen
            // with a "Sign in again" button. Emitting 'unauthenticated' here would
            // render the indefinite loading spinner, leaving users with no retry path.
            this.#emit({ kind: 'expired', reason: 'forced-logout' });
            return;
        }

        const params = new URL(window.location.href).searchParams;
        const code = params.get('code');
        const returnedState = params.get('state');

        if (code !== null) {
            await this.#handleCallback(code, returnedState);
            return;
        }

        const storedRefreshToken = sessionStorage.getItem(KEY_REFRESH_TOKEN);
        if (storedRefreshToken !== null) {
            const refreshed = await this.#tryRefresh(storedRefreshToken);
            if (refreshed) return;
        }

        // No code, no usable refresh token → start PKCE flow
        await this.#startPkceRedirect();
    }

    /** Returns the current auth status snapshot (synchronous). */
    getStatus(): AuthStatus {
        return this.#status;
    }

    /** Returns the in-memory access token, or null if not authenticated. */
    getAccessToken(): string | null {
        return this.#accessToken;
    }

    /** Returns the cached discovery document, or null before initialize(). */
    getDiscovery(): OidcDiscovery | null {
        return this.#discovery;
    }

    /**
     * Triggers an explicit refresh (e.g. called by refresh timer).
     * On failure, treats the session as force-expired.
     */
    async refresh(): Promise<void> {
        const refreshToken = sessionStorage.getItem(KEY_REFRESH_TOKEN);
        if (!refreshToken || !this.#discovery) {
            this.forceLogout('forced-logout');
            return;
        }

        const refreshed = await this.#tryRefresh(refreshToken);
        if (!refreshed) {
            this.forceLogout('forced-logout');
        }
    }

    /**
     * Explicit user-initiated logout.
     * Clears all tokens, then redirects to the OIDC end_session_endpoint.
     */
    async logout(): Promise<void> {
        const idToken = sessionStorage.getItem(KEY_ID_TOKEN);
        this.#clearTokens();
        this.#emit({ kind: 'unauthenticated' });

        if (this.#discovery) {
            const url = this.client.buildLogoutUrl(this.#discovery, idToken);
            window.location.href = url;
        } else {
            window.location.href = this.config.oidc.postLogoutRedirectUri;
        }
    }

    /**
     * Called by SessionManager or HttpClient on timeout/401.
     * Clears tokens and emits an expired event, but does NOT redirect immediately —
     * AppShell shows the overlay first, then redirects on "login-requested".
     */
    forceLogout(reason: 'idle-timeout' | 'max-lifetime' | 'forced-logout'): void {
        this.#clearTokens();
        this.#emit({ kind: 'expired', reason });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    async #loadDiscovery(): Promise<OidcDiscovery> {
        const cached = sessionStorage.getItem(KEY_DISCOVERY);
        if (cached !== null) {
            try {
                const parsed = JSON.parse(cached) as { issuer: string; doc: OidcDiscovery; cachedAt: number };
                const age = Date.now() - parsed.cachedAt;
                if (parsed.issuer === this.config.oidc.issuer && age < DISCOVERY_CACHE_TTL_MS) {
                    return parsed.doc;
                }
            } catch {
                // Corrupt cache — ignore and re-fetch
            }
        }

        const doc = await this.client.discover();
        sessionStorage.setItem(KEY_DISCOVERY, JSON.stringify({
            issuer: this.config.oidc.issuer,
            doc,
            cachedAt: Date.now(),
        }));
        return doc;
    }

    async #handleCallback(code: string, returnedState: string | null): Promise<void> {
        const pkceRaw = sessionStorage.getItem(KEY_PKCE);
        sessionStorage.removeItem(KEY_PKCE);

        // Clean the callback params from the browser URL immediately
        history.replaceState(null, '', window.location.pathname);

        if (pkceRaw === null) {
            // No PKCE session found — the callback URL is stale or the user
            // navigated directly to a callback URL. Start a fresh login flow.
            console.warn('[AuthState] No PKCE session in storage; starting fresh login flow');
            await this.#startPkceRedirect();
            return;
        }

        let pkce: PkceSession;
        try {
            pkce = JSON.parse(pkceRaw) as PkceSession;
        } catch {
            console.warn('[AuthState] Corrupt PKCE session data; starting fresh login flow');
            await this.#startPkceRedirect();
            return;
        }

        // CSRF check: the state returned in the callback must match the stored nonce.
        if (returnedState !== pkce.state) {
            console.error('[AuthState] PKCE state mismatch — possible CSRF; discarding callback and starting fresh login flow');
            await this.#startPkceRedirect();
            return;
        }

        try {
            const tokenResponse = await this.client.exchangeCode(this.#discovery!, code, pkce);
            this.#applyTokenResponse(tokenResponse);
        } catch (err) {
            console.error('[AuthState] Code exchange failed:', err);
            // Show an error screen that lets the user retry
            this.#emit({ kind: 'expired', reason: 'forced-logout' });
        }
    }

    async #tryRefresh(refreshToken: string): Promise<boolean> {
        try {
            const tokenResponse = await this.client.refreshTokens(this.#discovery!, refreshToken);
            this.#applyTokenResponse(tokenResponse);
            return true;
        } catch (err) {
            console.warn('[AuthState] Token refresh failed:', err);
            sessionStorage.removeItem(KEY_REFRESH_TOKEN);
            return false;
        }
    }

    async #startPkceRedirect(): Promise<void> {
        const pkce: PkceSession = {
            codeVerifier: this.client.generateCodeVerifier(),
            state: this.client.generateState(),
            startedAt: Date.now(),
        };
        sessionStorage.setItem(KEY_PKCE, JSON.stringify(pkce));

        const authUrl = await this.client.buildAuthUrl(this.#discovery!, pkce);
        window.location.href = authUrl;
    }

    #applyTokenResponse(response: TokenResponse): void {
        const parsed = this.client.parseToken(response.access_token);
        const expiresAt = Date.now() + response.expires_in * 1000;

        this.#accessToken = response.access_token;

        const tokenSet: TokenSet = {
            accessToken: response.access_token,
            idToken: response.id_token ?? null,
            refreshToken: response.refresh_token ?? null,
            expiresAt,
            parsedAccess: parsed,
        };

        // Persist refresh token and id token to sessionStorage for page refreshes
        if (response.refresh_token) {
            sessionStorage.setItem(KEY_REFRESH_TOKEN, response.refresh_token);
        } else {
            sessionStorage.removeItem(KEY_REFRESH_TOKEN);
        }

        if (response.id_token) {
            sessionStorage.setItem(KEY_ID_TOKEN, response.id_token);
        }

        // Record login time only on first authentication (not on refresh)
        if (sessionStorage.getItem(KEY_LOGIN_AT) === null) {
            sessionStorage.setItem(KEY_LOGIN_AT, String(Date.now()));
        }

        this.#scheduleRefresh(response.expires_in);
        this.#emit({ kind: 'authenticated', tokenSet });
    }

    #scheduleRefresh(expiresInSeconds: number): void {
        if (this.#refreshTimerId !== null) {
            clearTimeout(this.#refreshTimerId);
        }
        const delayMs = Math.max(0, (expiresInSeconds - REFRESH_BEFORE_EXPIRY_S) * 1000);
        this.#refreshTimerId = setTimeout(() => {
            void this.refresh();
        }, delayMs);
    }

    #clearTokens(): void {
        if (this.#refreshTimerId !== null) {
            clearTimeout(this.#refreshTimerId);
            this.#refreshTimerId = null;
        }
        this.#accessToken = null;
        sessionStorage.removeItem(KEY_REFRESH_TOKEN);
        sessionStorage.removeItem(KEY_ID_TOKEN);
        sessionStorage.removeItem(KEY_LOGIN_AT);
        sessionStorage.removeItem(KEY_PKCE);
    }

    #emit(status: AuthStatus): void {
        this.#status = status;
        this.dispatchEvent(new AuthChangedEvent(status));
    }
}
