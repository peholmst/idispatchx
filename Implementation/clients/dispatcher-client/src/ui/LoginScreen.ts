// Login / session-expired screen Web Component.
// Displayed while the OIDC flow is in progress or after a session ends.

import STYLES from './LoginScreen.css?inline';
import { t, type TranslationKey } from '../i18n/index.ts';

type LoginScreenStatus = 'loading' | 'session-expired' | 'idle-timeout' | 'max-lifetime' | 'forced-logout';

const STATUS_KEYS: Record<LoginScreenStatus, TranslationKey> = {
    'loading':        'login.signingIn',
    'session-expired': 'login.sessionExpired',
    'idle-timeout':   'login.idleTimeout',
    'max-lifetime':   'login.maxLifetime',
    // Set by AppShell when the server revokes the session (back-channel logout
    // or admin termination) or when OIDC discovery fails.
    'forced-logout':  'login.forcedLogout',
};

/** Event dispatched when the user clicks "Sign in again". Bubbles through the DOM. */
export class LoginRequestedEvent extends CustomEvent<void> {
    static readonly TYPE = 'login-requested' as const;

    constructor() {
        super(LoginRequestedEvent.TYPE, { bubbles: true, composed: true });
    }
}

/**
 * `<idispatch-login-screen>` Web Component.
 *
 * Attributes:
 *   status — one of: "loading" (default), "session-expired", "idle-timeout", "max-lifetime"
 */
export class LoginScreen extends HTMLElement {
    static readonly TAG = 'idispatch-login-screen' as const;

    #shadow: ShadowRoot;
    #messageEl: HTMLParagraphElement | null = null;
    #loadingIndicatorEl: HTMLDivElement | null = null;
    #signInButtonEl: HTMLButtonElement | null = null;

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
    }

    static get observedAttributes(): string[] {
        return ['status'];
    }

    connectedCallback(): void {
        this.#render();
    }

    attributeChangedCallback(_name: string, _old: string | null, _value: string | null): void {
        if (this.isConnected) {
            this.#updateContent();
        }
    }

    #render(): void {
        const style = document.createElement('style');
        style.textContent = STYLES;

        const overlay = document.createElement('div');
        overlay.className = 'login-overlay';
        overlay.setAttribute('role', 'status');
        overlay.setAttribute('aria-live', 'polite');

        const card = document.createElement('div');
        card.className = 'login-card';

        const logo = document.createElement('div');
        logo.className = 'brand-logo';
        logo.textContent = 'iD';
        logo.setAttribute('aria-hidden', 'true');

        const title = document.createElement('h1');
        title.textContent = 'iDispatchX';

        this.#messageEl = document.createElement('p');
        this.#messageEl.className = 'status-message';

        this.#loadingIndicatorEl = document.createElement('div');
        this.#loadingIndicatorEl.className = 'loading-indicator';
        this.#loadingIndicatorEl.setAttribute('aria-hidden', 'true');

        this.#signInButtonEl = document.createElement('button');
        this.#signInButtonEl.className = 'sign-in-button';
        this.#signInButtonEl.textContent = t('login.signInAgain');
        this.#signInButtonEl.addEventListener('click', () => {
            this.dispatchEvent(new LoginRequestedEvent());
        });

        card.append(logo, title, this.#messageEl, this.#loadingIndicatorEl, this.#signInButtonEl);
        overlay.appendChild(card);
        this.#shadow.append(style, overlay);

        this.#updateContent();
    }

    #updateContent(): void {
        if (!this.#messageEl || !this.#loadingIndicatorEl || !this.#signInButtonEl) return;

        const status = (this.getAttribute('status') ?? 'loading') as LoginScreenStatus;
        const isLoading = status === 'loading';

        const key = STATUS_KEYS[status] ?? STATUS_KEYS['loading'];
        this.#messageEl.textContent = t(key);
        this.#loadingIndicatorEl.style.display = isLoading ? 'block' : 'none';
        this.#signInButtonEl.style.display = isLoading ? 'none' : 'inline-block';
    }
}
