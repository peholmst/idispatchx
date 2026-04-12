// Root Web Component for the Dispatcher Client.
// Manages authentication state display, session warning banners,
// and transitions between login and authenticated views.

import STYLES from './AppShell.css?inline';
import { AuthChangedEvent, AuthState, SessionWarningEvent } from '../auth/AuthState.ts';
import type { AuthStatus } from '../auth/types.ts';
import { LoginRequestedEvent, LoginScreen } from './LoginScreen.ts';
import type { SessionManager } from '../auth/SessionManager.ts';

/**
 * `<idispatch-app-shell>` Web Component.
 * Rendered by main.ts; dependencies are injected via initialize().
 */
export class AppShell extends HTMLElement {
    static readonly TAG = 'idispatch-app-shell' as const;

    #shadow: ShadowRoot;
    #authState: AuthState | null = null;
    #sessionManager: SessionManager | null = null;
    #warningBanner: HTMLDivElement | null = null;

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
    initialize(authState: AuthState, sessionManager: SessionManager): void {
        this.#authState = authState;
        this.#sessionManager = sessionManager;
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
    }

    #onStatusChanged(status: AuthStatus): void {
        if (status.kind === 'authenticated') {
            this.#sessionManager!.start();
        } else if (status.kind === 'expired' || status.kind === 'unauthenticated') {
            this.#sessionManager!.stop();
            this.#dismissWarningBanner();
        }
        this.#renderStatus(status);
    }

    #renderStatus(status: AuthStatus): void {
        // Remove any existing login screen
        const existingLogin = this.#shadow.querySelector(LoginScreen.TAG);
        if (existingLogin) {
            existingLogin.remove();
        }

        // Remove any existing app content placeholder
        const existingContent = this.#shadow.querySelector('.app-content');
        if (existingContent) {
            existingContent.remove();
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
                const content = document.createElement('div');
                content.className = 'app-content';
                content.setAttribute('data-username', status.tokenSet.parsedAccess.preferred_username ?? status.tokenSet.parsedAccess.sub);
                // Placeholder: full dispatcher UI will be built here in future iterations
                this.#shadow.appendChild(content);
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
