// Shared types for OIDC authentication in the Dispatcher Client.

/** Raw response from the OIDC token endpoint. */
export interface TokenResponse {
    access_token: string;
    token_type: string;
    expires_in: number;
    refresh_token?: string;
    refresh_expires_in?: number;
    id_token?: string;
    scope?: string;
}

/**
 * Parsed JWT claims relevant to iDispatchX.
 * Roles are extracted from the top-level "roles" claim per the Security NFR
 * (ADR-0001 requires standard claims only; backend TokenValidator uses "roles").
 */
export interface ParsedToken {
    sub: string;
    exp: number;
    iat: number;
    iss: string;
    aud?: string | string[];
    sid?: string;
    preferred_username?: string;
    email?: string;
    roles?: string[];
}

/** Hydrated token set. The access token lives in memory only. */
export interface TokenSet {
    accessToken: string;
    idToken: string | null;
    refreshToken: string | null;
    /** Expiry as Unix epoch milliseconds. */
    expiresAt: number;
    parsedAccess: ParsedToken;
}

/** Subset of the OIDC discovery document that the client uses. */
export interface OidcDiscovery {
    issuer: string;
    authorization_endpoint: string;
    token_endpoint: string;
    end_session_endpoint?: string;
    jwks_uri: string;
}

/** PKCE session state stored in sessionStorage during the redirect round-trip. */
export interface PkceSession {
    codeVerifier: string;
    /** Random nonce; compared to the state param returned in the callback URL. */
    state: string;
    /** Timestamp when the PKCE session was started, to detect stale sessions. */
    startedAt: number;
}

/** Auth state snapshot used for events and rendering decisions. */
export type AuthStatus =
    | { kind: 'unauthenticated' }
    | { kind: 'authenticating' }
    | { kind: 'authenticated'; tokenSet: TokenSet }
    | { kind: 'expired'; reason: 'idle-timeout' | 'max-lifetime' | 'forced-logout' };
