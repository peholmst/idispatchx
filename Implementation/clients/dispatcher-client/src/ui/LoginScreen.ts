// Login / session-expired screen Web Component.
// Displayed while the OIDC flow is in progress or after a session ends.

const STYLES = `
  :host {
    display: block;
  }

  .overlay {
    position: fixed;
    inset: 0;
    z-index: 9999;
    background: #1e1e1e;
    display: grid;
    place-items: center;
    font-family: "Segoe UI", system-ui, -apple-system, sans-serif;
    color: #cccccc;
  }

  .card {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;
    padding: 40px 48px;
    background: #252526;
    border: 1px solid #454545;
    border-radius: 6px;
    min-width: 320px;
    text-align: center;
  }

  .logo {
    width: 48px;
    height: 48px;
    background: #0078d4;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    font-weight: 700;
    color: #ffffff;
    letter-spacing: -0.5px;
  }

  h1 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    color: #ffffff;
  }

  .message {
    margin: 0;
    font-size: 14px;
    color: #9d9d9d;
    max-width: 260px;
    line-height: 1.5;
  }

  .spinner {
    width: 24px;
    height: 24px;
    border: 2px solid #454545;
    border-top-color: #0078d4;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }

  @keyframes spin {
    to { transform: rotate(360deg); }
  }

  .btn {
    margin-top: 8px;
    padding: 8px 24px;
    background: #0078d4;
    color: #ffffff;
    border: none;
    border-radius: 3px;
    font-size: 14px;
    font-family: inherit;
    cursor: pointer;
    transition: background 0.15s;
  }

  .btn:hover {
    background: #106ebe;
  }

  .btn:active {
    background: #005a9e;
  }
`;

type LoginScreenStatus = 'loading' | 'session-expired' | 'idle-timeout' | 'max-lifetime' | 'forced-logout';

const STATUS_MESSAGES: Record<LoginScreenStatus, string> = {
    'loading': 'Signing in…',
    'session-expired': 'You have been signed out.',
    'idle-timeout': 'You were signed out due to inactivity.',
    'max-lifetime': 'Your session has reached its maximum duration.',
    // Set by AppShell when the server revokes the session (back-channel logout
    // or admin termination) or when OIDC discovery fails.
    'forced-logout': 'You have been signed out.',
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
    #spinnerEl: HTMLDivElement | null = null;
    #btnEl: HTMLButtonElement | null = null;

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
        overlay.className = 'overlay';
        overlay.setAttribute('role', 'status');
        overlay.setAttribute('aria-live', 'polite');

        const card = document.createElement('div');
        card.className = 'card';

        const logo = document.createElement('div');
        logo.className = 'logo';
        logo.textContent = 'iD';
        logo.setAttribute('aria-hidden', 'true');

        const title = document.createElement('h1');
        title.textContent = 'iDispatchX';

        this.#messageEl = document.createElement('p');
        this.#messageEl.className = 'message';

        this.#spinnerEl = document.createElement('div');
        this.#spinnerEl.className = 'spinner';
        this.#spinnerEl.setAttribute('aria-hidden', 'true');

        this.#btnEl = document.createElement('button');
        this.#btnEl.className = 'btn';
        this.#btnEl.textContent = 'Sign in again';
        this.#btnEl.addEventListener('click', () => {
            this.dispatchEvent(new LoginRequestedEvent());
        });

        card.append(logo, title, this.#messageEl, this.#spinnerEl, this.#btnEl);
        overlay.appendChild(card);
        this.#shadow.append(style, overlay);

        this.#updateContent();
    }

    #updateContent(): void {
        if (!this.#messageEl || !this.#spinnerEl || !this.#btnEl) return;

        const status = (this.getAttribute('status') ?? 'loading') as LoginScreenStatus;
        const isLoading = status === 'loading';

        this.#messageEl.textContent = STATUS_MESSAGES[status] ?? STATUS_MESSAGES['loading'];
        this.#spinnerEl.style.display = isLoading ? 'block' : 'none';
        this.#btnEl.style.display = isLoading ? 'none' : 'inline-block';
    }
}
