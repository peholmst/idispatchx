// iDispatchX Dispatcher Client — entry point
//
// Boot sequence:
//   1. Register custom elements
//   2. Mount AppShell immediately (shows loading screen)
//   3. Load runtime config from /config.json
//   4. Wire dependencies
//   5. Initialize OIDC auth (may redirect browser to OIDC provider)

import { loadAppConfig } from './config/AppConfig.ts';
import { OidcClient } from './auth/OidcClient.ts';
import { AuthState } from './auth/AuthState.ts';
import { SessionManager } from './auth/SessionManager.ts';
import { HttpClient } from './http/HttpClient.ts';
import { AppShell } from './ui/AppShell.ts';
import { LoginScreen } from './ui/LoginScreen.ts';
import { WindowHeader, WindowFooter } from './ui/WindowChrome.ts';
import { LauncherPage } from './ui/LauncherPage.ts';
import { PrimaryWindow } from './ui/PrimaryWindow.ts';
import { SecondaryWindow } from './ui/SecondaryWindow.ts';
import { WINDOW_TYPE_KEY } from './ui/windowType.ts';
import type { WindowType } from './ui/windowType.ts';

// Register custom elements before any template or innerHTML usage
customElements.define(LoginScreen.TAG, LoginScreen);
customElements.define(WindowHeader.TAG, WindowHeader);
customElements.define(WindowFooter.TAG, WindowFooter);
customElements.define(LauncherPage.TAG, LauncherPage);
customElements.define(PrimaryWindow.TAG, PrimaryWindow);
customElements.define(SecondaryWindow.TAG, SecondaryWindow);
customElements.define(AppShell.TAG, AppShell);

async function boot(): Promise<void> {
    const appEl = document.getElementById('app');
    if (!appEl) {
        throw new Error('Missing #app element in index.html');
    }

    // Determine the window type from the URL param, persisting it to sessionStorage
    // so it survives the OIDC redirect that erases the query string.
    const urlWindowType = new URLSearchParams(window.location.search).get('window');
    if (urlWindowType === 'primary' || urlWindowType === 'secondary') {
        sessionStorage.setItem(WINDOW_TYPE_KEY, urlWindowType);
    }
    const storedWindowType = sessionStorage.getItem(WINDOW_TYPE_KEY);
    const windowType: WindowType =
        storedWindowType === 'primary' ? 'primary' :
        storedWindowType === 'secondary' ? 'secondary' :
        'launcher';

    // Load runtime configuration
    const config = await loadAppConfig();

    // Construct OIDC objects (pure construction, no network calls yet)
    const oidcClient = new OidcClient(config.oidc);
    const authState = new AuthState(oidcClient, config);
    const sessionManager = new SessionManager(authState, config.session);

    // The HttpClient is the entry point for all authenticated API calls.
    // On 401, it signals that the server has revoked the session.
    const httpClient = new HttpClient(authState, () => {
        authState.forceLogout('forced-logout');
    });

    // Mount the AppShell AFTER initialize() so that connectedCallback fires
    // with a fully wired authState — ensuring event listeners are registered.
    const shell = document.createElement(AppShell.TAG) as AppShell;
    shell.initialize(authState, sessionManager, windowType);
    appEl.appendChild(shell);

    // Expose authState for Playwright E2E tests in development mode
    if (import.meta.env.DEV) {
        (window as Window & { __authState?: AuthState; __httpClient?: HttpClient }).__authState = authState;
        (window as Window & { __authState?: AuthState; __httpClient?: HttpClient }).__httpClient = httpClient;
    }

    // Start the OIDC boot sequence. This may redirect the browser.
    await authState.initialize();
}

boot().catch((err: unknown) => {
    console.error('[main] Fatal boot error:', err);

    const appEl = document.getElementById('app');
    if (appEl) {
        appEl.innerHTML = `
            <div style="
                position:fixed;inset:0;background:#1e1e1e;color:#f48771;
                display:grid;place-items:center;
                font-family:'Segoe UI',system-ui,sans-serif;font-size:14px;
            ">
                <div style="text-align:center;max-width:400px;padding:32px">
                    <strong>iDispatchX could not start.</strong><br><br>
                    Failed to load configuration. Please contact your system administrator.<br><br>
                    <code style="font-size:12px;color:#9d9d9d">${String(err)}</code>
                </div>
            </div>`;
    }
});
