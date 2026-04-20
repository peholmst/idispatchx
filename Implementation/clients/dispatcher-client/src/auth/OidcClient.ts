// Stateless OIDC / PKCE mechanics for the Dispatcher Client.
// All state is managed by AuthState; this class only handles network calls
// and cryptographic operations.

import type { OidcConfig } from '../config/AppConfig.ts';
import type { OidcDiscovery, ParsedToken, PkceSession, TokenResponse } from './types.ts';

export class OidcClient {
    constructor(private readonly config: OidcConfig) {}

    /**
     * Fetches and parses the OIDC discovery document.
     * Constructs the URL as {issuer}/.well-known/openid-configuration per the spec.
     */
    async discover(): Promise<OidcDiscovery> {
        const url = `${this.config.issuer}/.well-known/openid-configuration`;
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`OIDC discovery failed: HTTP ${response.status} from ${url}`);
        }
        const doc = await response.json() as Record<string, unknown>;

        if (typeof doc['authorization_endpoint'] !== 'string') {
            throw new Error('OIDC discovery document missing authorization_endpoint');
        }
        if (typeof doc['token_endpoint'] !== 'string') {
            throw new Error('OIDC discovery document missing token_endpoint');
        }
        if (typeof doc['jwks_uri'] !== 'string') {
            throw new Error('OIDC discovery document missing jwks_uri');
        }

        return {
            issuer: typeof doc['issuer'] === 'string' ? doc['issuer'] : this.config.issuer,
            authorization_endpoint: doc['authorization_endpoint'] as string,
            token_endpoint: doc['token_endpoint'] as string,
            end_session_endpoint: typeof doc['end_session_endpoint'] === 'string'
                ? doc['end_session_endpoint']
                : undefined,
            jwks_uri: doc['jwks_uri'] as string,
        };
    }

    /**
     * Generates a cryptographically random PKCE code verifier.
     * Uses 32 random bytes encoded as base64url per RFC 7636.
     */
    generateCodeVerifier(): string {
        const array = new Uint8Array(32);
        crypto.getRandomValues(array);
        return base64urlEncode(array);
    }

    /**
     * Derives the PKCE code challenge from the verifier using the S256 method.
     * SHA-256 hash of the verifier, base64url-encoded.
     */
    async generateCodeChallenge(verifier: string): Promise<string> {
        const encoder = new TextEncoder();
        const data = encoder.encode(verifier);
        const digest = await crypto.subtle.digest('SHA-256', data);
        return base64urlEncode(new Uint8Array(digest));
    }

    /** Generates a random state nonce for CSRF protection. */
    generateState(): string {
        const array = new Uint8Array(16);
        crypto.getRandomValues(array);
        return base64urlEncode(array);
    }

    /**
     * Builds the full authorization URL to redirect the browser to.
     * Includes PKCE challenge and state nonce.
     */
    async buildAuthUrl(discovery: OidcDiscovery, pkceSession: PkceSession): Promise<string> {
        const challenge = await this.generateCodeChallenge(pkceSession.codeVerifier);
        const params = new URLSearchParams({
            response_type: 'code',
            client_id: this.config.clientId,
            redirect_uri: this.config.redirectUri,
            scope: this.config.scopes.join(' '),
            state: pkceSession.state,
            code_challenge: challenge,
            code_challenge_method: 'S256',
        });
        return `${discovery.authorization_endpoint}?${params.toString()}`;
    }

    /**
     * Exchanges the authorization code for tokens at the token endpoint.
     * POSTs with code, code_verifier, and redirect_uri per RFC 7636.
     */
    async exchangeCode(
        discovery: OidcDiscovery,
        code: string,
        pkceSession: PkceSession,
    ): Promise<TokenResponse> {
        const body = new URLSearchParams({
            grant_type: 'authorization_code',
            client_id: this.config.clientId,
            code,
            redirect_uri: this.config.redirectUri,
            code_verifier: pkceSession.codeVerifier,
        });

        const response = await fetch(discovery.token_endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body.toString(),
        });

        if (!response.ok) {
            const text = await response.text().catch(() => '');
            throw new Error(`Token exchange failed: HTTP ${response.status}: ${text}`);
        }

        return response.json() as Promise<TokenResponse>;
    }

    /**
     * Refreshes tokens using a stored refresh token.
     * POSTs grant_type=refresh_token to the token endpoint.
     */
    async refreshTokens(discovery: OidcDiscovery, refreshToken: string): Promise<TokenResponse> {
        const body = new URLSearchParams({
            grant_type: 'refresh_token',
            client_id: this.config.clientId,
            refresh_token: refreshToken,
        });

        const response = await fetch(discovery.token_endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body.toString(),
        });

        if (!response.ok) {
            const text = await response.text().catch(() => '');
            throw new Error(`Token refresh failed: HTTP ${response.status}: ${text}`);
        }

        return response.json() as Promise<TokenResponse>;
    }

    /**
     * Builds the RP-initiated logout URL.
     * Uses end_session_endpoint if available (required for proper server-side session
     * termination). Falls back to postLogoutRedirectUri if the provider does not
     * advertise an end_session_endpoint.
     */
    buildLogoutUrl(discovery: OidcDiscovery, idToken: string | null): string {
        if (!discovery.end_session_endpoint) {
            return this.config.postLogoutRedirectUri;
        }

        const params = new URLSearchParams({
            post_logout_redirect_uri: this.config.postLogoutRedirectUri,
        });

        if (idToken !== null) {
            params.set('id_token_hint', idToken);
        }

        return `${discovery.end_session_endpoint}?${params.toString()}`;
    }

    /**
     * Parses a JWT without signature verification.
     * The server validates the signature; the client only needs claims for UI
     * and expiry scheduling purposes.
     */
    parseToken(jwt: string): ParsedToken {
        const parts = jwt.split('.');
        if (parts.length !== 3) {
            throw new Error('Invalid JWT format: expected 3 parts');
        }

        const payload = parts[1];
        if (payload === undefined) {
            throw new Error('Invalid JWT: missing payload');
        }

        // base64url → base64 → decode
        const padded = payload.replace(/-/g, '+').replace(/_/g, '/');
        const padding = (4 - (padded.length % 4)) % 4;
        const base64 = padded + '='.repeat(padding);
        const decoded = atob(base64);
        const parsed = JSON.parse(decoded) as Record<string, unknown>;

        if (typeof parsed['sub'] !== 'string') {
            throw new Error('JWT missing required sub claim');
        }
        if (typeof parsed['exp'] !== 'number') {
            throw new Error('JWT missing required exp claim');
        }

        return {
            sub: parsed['sub'] as string,
            exp: parsed['exp'] as number,
            iat: typeof parsed['iat'] === 'number' ? parsed['iat'] as number : 0,
            iss: typeof parsed['iss'] === 'string' ? parsed['iss'] as string : '',
            aud: parsed['aud'] as string | string[] | undefined,
            sid: typeof parsed['sid'] === 'string' ? parsed['sid'] as string : undefined,
            preferred_username: typeof parsed['preferred_username'] === 'string'
                ? parsed['preferred_username'] as string
                : undefined,
            email: typeof parsed['email'] === 'string' ? parsed['email'] as string : undefined,
            roles: Array.isArray(parsed['roles'])
                ? (parsed['roles'] as unknown[]).filter((r): r is string => typeof r === 'string')
                : undefined,
        };
    }
}

/**
 * Encodes a Uint8Array as base64url (RFC 4648 §5, no padding).
 * Replaces '+' with '-', '/' with '_', and strips trailing '='.
 */
function base64urlEncode(data: Uint8Array): string {
    // Build a binary string from the byte array for btoa()
    let binary = '';
    for (let i = 0; i < data.length; i++) {
        binary += String.fromCharCode(data[i]!);
    }
    return btoa(binary)
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=+$/, '');
}
