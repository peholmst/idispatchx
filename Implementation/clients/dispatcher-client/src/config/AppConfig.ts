// Runtime configuration for the iDispatchX Dispatcher Client.
// The config is loaded from /config.json at startup, allowing ops to replace
// it per environment without rebuilding. Falls back to window.__APP_CONFIG__
// for environments that inject config via the HTML template.

export interface OidcConfig {
    readonly issuer: string;
    readonly clientId: string;
    readonly redirectUri: string;
    readonly scopes: string[];
    readonly postLogoutRedirectUri: string;
}

export interface SessionConfig {
    readonly idleTimeoutSeconds: number;
    readonly maxLifetimeSeconds: number;
}

export interface AppConfig {
    readonly oidc: OidcConfig;
    readonly session: SessionConfig;
}

declare global {
    interface Window {
        __APP_CONFIG__?: AppConfig;
    }
}

export async function loadAppConfig(): Promise<AppConfig> {
    let raw: unknown;

    // VITE_CONFIG_URL allows test environments to serve a different config file
    // without modifying the default config.json (set via webServer.env in playwright.config.ts).
    const configUrl = (import.meta.env['VITE_CONFIG_URL'] as string | undefined) ?? '/config.json';

    try {
        const response = await fetch(configUrl);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        raw = await response.json();
    } catch {
        if (window.__APP_CONFIG__ !== undefined) {
            raw = window.__APP_CONFIG__;
        } else {
            throw new Error(
                'Failed to load /config.json and no window.__APP_CONFIG__ fallback is present. ' +
                'Ensure config.json is served alongside the application.'
            );
        }
    }

    return validateConfig(raw);
}

function validateConfig(raw: unknown): AppConfig {
    if (typeof raw !== 'object' || raw === null) {
        throw new Error('AppConfig must be a JSON object');
    }

    const obj = raw as Record<string, unknown>;

    if (typeof obj['oidc'] !== 'object' || obj['oidc'] === null) {
        throw new Error('AppConfig.oidc is required');
    }
    const oidc = obj['oidc'] as Record<string, unknown>;

    requireNonBlankString(oidc, 'oidc.issuer');
    requireNonBlankString(oidc, 'oidc.clientId');
    requireNonBlankString(oidc, 'oidc.redirectUri');
    requireNonBlankString(oidc, 'oidc.postLogoutRedirectUri');

    if (!Array.isArray(oidc['scopes']) || oidc['scopes'].length === 0) {
        throw new Error('AppConfig.oidc.scopes must be a non-empty array');
    }

    if (typeof obj['session'] !== 'object' || obj['session'] === null) {
        throw new Error('AppConfig.session is required');
    }
    const session = obj['session'] as Record<string, unknown>;

    requirePositiveNumber(session, 'session.idleTimeoutSeconds');
    requirePositiveNumber(session, 'session.maxLifetimeSeconds');

    return {
        oidc: {
            issuer: oidc['issuer'] as string,
            clientId: oidc['clientId'] as string,
            redirectUri: oidc['redirectUri'] as string,
            scopes: oidc['scopes'] as string[],
            postLogoutRedirectUri: oidc['postLogoutRedirectUri'] as string,
        },
        session: {
            idleTimeoutSeconds: session['idleTimeoutSeconds'] as number,
            maxLifetimeSeconds: session['maxLifetimeSeconds'] as number,
        },
    };
}

function requireNonBlankString(obj: Record<string, unknown>, path: string): void {
    const key = path.split('.').pop()!;
    if (typeof obj[key] !== 'string' || (obj[key] as string).trim() === '') {
        throw new Error(`AppConfig.${path} must be a non-blank string`);
    }
}

function requirePositiveNumber(obj: Record<string, unknown>, path: string): void {
    const key = path.split('.').pop()!;
    if (typeof obj[key] !== 'number' || (obj[key] as number) <= 0) {
        throw new Error(`AppConfig.${path} must be a positive number`);
    }
}
