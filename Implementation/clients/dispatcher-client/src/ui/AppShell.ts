// Root Web Component for the Dispatcher Client.
// Manages authentication state display, session warning banners,
// and transitions between login and authenticated views.

import STYLES from './AppShell.css?inline';
import { AuthChangedEvent, AuthState, SessionWarningEvent } from '../auth/AuthState.ts';
import type { AuthStatus } from '../auth/types.ts';
import { LoginRequestedEvent, LoginScreen } from './LoginScreen.ts';
import type { SessionManager } from '../auth/SessionManager.ts';
import type { WindowType } from './windowType.ts';
import { SESSION_CHANNEL_NAME } from './windowType.ts';
import { LauncherPage } from './LauncherPage.ts';
import { PrimaryWindow } from './PrimaryWindow.ts';
import { SecondaryWindow } from './SecondaryWindow.ts';
import type { HttpClient } from '../http/HttpClient.ts';

/**
 * `<idispatch-app-shell>` Web Component.
 * Rendered by main.ts; dependencies are injected via initialize().
 */
export class AppShell extends HTMLElement {
    static readonly TAG = 'idispatch-app-shell' as const;

    #shadow: ShadowRoot;
    #authState: AuthState | null = null;
    #sessionManager: SessionManager | null = null;
    #windowType: WindowType = 'launcher';
    #http: HttpClient | null = null;
    #gisServerUrl = '';
    #warningBanner: HTMLDivElement | null = null;
    #renderedStatusKind: AuthStatus['kind'] | null = null;
    // BroadcastChannel used by dispatcher windows (primary/secondary) to receive
    // session events from the launcher — notably sign-out propagation.
    #sessionChannel: BroadcastChannel | null = null;

    // Bound handler references for removeEventListener
    #onAuthChanged: ((e: Event) => void) | null = null;
    #onSessionWarning: ((e: Event) => void) | null = null;
    #onLoginRequested: ((e: Event) => void) | null = null;

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
    }

    /**
     * Injects dependencies after the element is constructed.
     * Called by main.ts before the element is connected to the DOM.
     */
    initialize(authState: AuthState, sessionManager: SessionManager, windowType: WindowType, http: HttpClient, gisServerUrl: string): void {
        this.#authState = authState;
        this.#sessionManager = sessionManager;
        this.#windowType = windowType;
        this.#http = http;
        this.#gisServerUrl = gisServerUrl;
    }

    connectedCallback(): void {
        const style = document.createElement('style');
        style.textContent = STYLES;
        this.#shadow.appendChild(style);

        if (!this.#authState || !this.#sessionManager) {
            // initialize() was not called — render an error state
            const errEl = document.createElement('p');
            errEl.textContent = 'Internal error: AppShell not initialized.';
            this.#shadow.appendChild(errEl);
            return;
        }

        // Show current status immediately
        this.#renderStatus(this.#authState.getStatus());

        // Subscribe to future state changes
        this.#onAuthChanged = (e: Event) => {
            const status = (e as AuthChangedEvent).detail;
            this.#onStatusChanged(status);
        };
        this.#authState.addEventListener(AuthChangedEvent.TYPE, this.#onAuthChanged);

        this.#onSessionWarning = (e: Event) => {
            const { reason, secondsRemaining } = (e as SessionWarningEvent).detail;
            this.#showWarningBanner(reason, secondsRemaining);
        };
        this.#authState.addEventListener(SessionWarningEvent.TYPE, this.#onSessionWarning);

        // Handle "Sign in again" from the LoginScreen
        this.#onLoginRequested = () => {
            void this.#authState!.initialize();
        };
        this.#shadow.addEventListener(LoginRequestedEvent.TYPE, this.#onLoginRequested);
    }

    disconnectedCallback(): void {
        if (this.#authState) {
            if (this.#onAuthChanged) {
                this.#authState.removeEventListener(AuthChangedEvent.TYPE, this.#onAuthChanged);
            }
            if (this.#onSessionWarning) {
                this.#authState.removeEventListener(SessionWarningEvent.TYPE, this.#onSessionWarning);
            }
        }
        if (this.#onLoginRequested) {
            this.#shadow.removeEventListener(LoginRequestedEvent.TYPE, this.#onLoginRequested);
        }
        this.#closeSessionChannel();
    }

    #onStatusChanged(status: AuthStatus): void {
        if (status.kind === 'authenticated') {
            this.#sessionManager!.start();
            // Dispatcher windows listen for session events from the launcher
            // (e.g. sign-out) so they can respond immediately without waiting
            // for a token expiry or a 401 from the server.
            if (this.#windowType !== 'launcher') {
                this.#openSessionChannel();
            }
            // Token refresh keeps kind === 'authenticated'. The window element
            // already holds a live authState reference so no re-render is needed —
            // avoiding a full map reset on each token rotation.
            if (this.#renderedStatusKind === 'authenticated') return;
        } else if (status.kind === 'expired' || status.kind === 'unauthenticated') {
            this.#sessionManager!.stop();
            this.#dismissWarningBanner();
            this.#closeSessionChannel();
        }
        this.#renderStatus(status);
    }

    #openSessionChannel(): void {
        if (this.#sessionChannel) return;
        this.#sessionChannel = new BroadcastChannel(SESSION_CHANNEL_NAME);
        this.#sessionChannel.onmessage = (e: MessageEvent<{ type: string }>) => {
            if (e.data.type === 'sign-out') {
                // The launcher has initiated an OIDC logout. Show the signed-out
                // overlay immediately rather than waiting for a 401.
                void this.#authState!.forceLogout('forced-logout');
            }
        };
        // Announce to the launcher that this dispatcher window is authenticated.
        // This lets the launcher arm its beforeunload guard even if it was
        // refreshed while this window was still in its OIDC flow (and therefore
        // hadn't written its sessionStorage flag yet at the time the launcher's
        // connectedCallback ran).
        this.#sessionChannel.postMessage({ type: 'window-authenticated', windowType: this.#windowType });
    }

    #closeSessionChannel(): void {
        this.#sessionChannel?.close();
        this.#sessionChannel = null;
    }

    #renderStatus(status: AuthStatus): void {
        this.#renderedStatusKind = status.kind;
        // Remove any existing authenticated view or login screen
        for (const tag of [
            LoginScreen.TAG,
            LauncherPage.TAG,
            PrimaryWindow.TAG,
            SecondaryWindow.TAG,
        ]) {
            this.#shadow.querySelector(tag)?.remove();
        }

        switch (status.kind) {
            case 'unauthenticated':
            case 'authenticating': {
                const screen = document.createElement(LoginScreen.TAG) as LoginScreen;
                screen.setAttribute('status', 'loading');
                this.#shadow.appendChild(screen);
                break;
            }

            case 'authenticated': {
                const username =
                    status.tokenSet.parsedAccess.preferred_username ??
                    status.tokenSet.parsedAccess.sub;

                switch (this.#windowType) {
                    case 'launcher': {
                        const launcher = document.createElement(LauncherPage.TAG) as LauncherPage;
                        launcher.initialize(this.#authState!, username);
                        this.#shadow.appendChild(launcher);
                        break;
                    }
                    case 'primary': {
                        const primary = document.createElement(PrimaryWindow.TAG) as PrimaryWindow;
                        primary.initialize(username);
                        this.#shadow.appendChild(primary);
                        break;
                    }
                    case 'secondary': {
                        const secondary = document.createElement(SecondaryWindow.TAG) as SecondaryWindow;
                        secondary.initialize(username, this.#gisServerUrl, this.#authState!, this.#http!);
                        this.#shadow.appendChild(secondary);
                        break;
                    }
                }
                break;
            }

            case 'expired': {
                const screen = document.createElement(LoginScreen.TAG) as LoginScreen;
                screen.setAttribute('status', status.reason);
                this.#shadow.appendChild(screen);
                break;
            }
        }
    }

    #showWarningBanner(reason: 'idle-timeout' | 'max-lifetime', secondsRemaining: number): void {
        this.#dismissWarningBanner();

        const banner = document.createElement('div');
        banner.className = 'session-warning';
        banner.setAttribute('role', 'alert');

        const minutesRemaining = Math.ceil(secondsRemaining / 60);
        const cause = reason === 'idle-timeout' ? 'inactivity' : 'reaching the maximum session duration';
        const messageEl = document.createElement('span');
        messageEl.className = 'warning-message';
        messageEl.textContent = `Your session will expire in approximately ${minutesRemaining} minute${minutesRemaining === 1 ? '' : 's'} due to ${cause}. Please save your work.`;

        const dismissBtn = document.createElement('button');
        dismissBtn.className = 'dismiss-button';
        dismissBtn.textContent = 'Dismiss';
        dismissBtn.setAttribute('type', 'button');
        dismissBtn.addEventListener('click', () => this.#dismissWarningBanner());

        banner.appendChild(messageEl);
        banner.appendChild(dismissBtn);
        this.#shadow.appendChild(banner);
        this.#warningBanner = banner;
    }

    #dismissWarningBanner(): void {
        if (this.#warningBanner) {
            this.#warningBanner.remove();
            this.#warningBanner = null;
        }
    }
}
