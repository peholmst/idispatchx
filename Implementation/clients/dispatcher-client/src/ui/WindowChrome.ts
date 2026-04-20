// Shared header and footer Web Components used by both the primary and secondary windows.

import STYLES from './WindowChrome.css?inline';

const HELSINKI_TZ = 'Europe/Helsinki';

/** Formats a Date as "DD.MM.YYYY HH:mm:ss" in the Europe/Helsinki timezone, 24-hour clock. */
function formatHelsinki(date: Date): string {
    return date.toLocaleString('fi-FI', {
        timeZone: HELSINKI_TZ,
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
    });
}

/**
 * `<idispatch-window-header>` Web Component.
 *
 * Displays the application logo, a live clock (Europe/Helsinki, 24-hour), and
 * optional primary action buttons. Set `showPrimaryActions = true` before the
 * element is connected to render the "New Call" and "New Incident" buttons.
 */
export class WindowHeader extends HTMLElement {
    static readonly TAG = 'idispatch-window-header' as const;

    #shadow: ShadowRoot;
    #clockEl: HTMLSpanElement | null = null;
    #tickId: ReturnType<typeof setInterval> | null = null;
    #showPrimaryActions = false;

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
    }

    set showPrimaryActions(value: boolean) {
        this.#showPrimaryActions = value;
    }

    connectedCallback(): void {
        // Add class before injecting shadow styles so :host(.window-header-host) matches
        this.classList.add('window-header-host');

        const style = document.createElement('style');
        style.textContent = STYLES;

        // Left: logo + live clock
        const left = document.createElement('div');
        left.className = 'header-left';

        const logo = document.createElement('div');
        logo.className = 'brand-logo';
        logo.textContent = 'iD';
        logo.setAttribute('aria-hidden', 'true');

        this.#clockEl = document.createElement('span');
        this.#clockEl.className = 'header-clock';
        this.#clockEl.setAttribute('aria-live', 'off');
        this.#clockEl.setAttribute('aria-label', 'Current date and time');

        left.append(logo, this.#clockEl);

        // Center: primary actions (primary window only)
        const center = document.createElement('div');
        center.className = 'header-center';

        if (this.#showPrimaryActions) {
            const newCallBtn = document.createElement('button');
            newCallBtn.type = 'button';
            newCallBtn.className = 'header-action-btn';
            newCallBtn.textContent = 'New Call';

            const newIncidentBtn = document.createElement('button');
            newIncidentBtn.type = 'button';
            newIncidentBtn.className = 'header-action-btn';
            newIncidentBtn.textContent = 'New Incident';

            center.append(newCallBtn, newIncidentBtn);
        }

        // Right: layout customization (placeholder — buttons added in a later iteration)
        const right = document.createElement('div');
        right.className = 'header-right';

        this.#shadow.append(style, left, center, right);

        // Start clock, then tick every second
        this.#tick();
        this.#tickId = setInterval(() => { this.#tick(); }, 1000);
    }

    disconnectedCallback(): void {
        if (this.#tickId !== null) {
            clearInterval(this.#tickId);
            this.#tickId = null;
        }
    }

    #tick(): void {
        if (this.#clockEl) {
            this.#clockEl.textContent = formatHelsinki(new Date());
        }
    }
}

/**
 * `<idispatch-window-footer>` Web Component.
 *
 * Displays the current username on the left and the normal/degraded mode
 * indicator on the right. Set `username` before or after connection.
 */
export class WindowFooter extends HTMLElement {
    static readonly TAG = 'idispatch-window-footer' as const;

    #shadow: ShadowRoot;
    #usernameEl: HTMLSpanElement | null = null;

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
    }

    set username(value: string) {
        if (this.#usernameEl) {
            this.#usernameEl.textContent = value;
        }
    }

    connectedCallback(): void {
        this.classList.add('window-footer-host');

        const style = document.createElement('style');
        style.textContent = STYLES;

        this.#usernameEl = document.createElement('span');
        this.#usernameEl.className = 'footer-left';

        const modeEl = document.createElement('span');
        modeEl.className = 'footer-right';
        // Placeholder — actual degraded-mode detection is added in a later iteration
        modeEl.textContent = 'Normal';

        this.#shadow.append(style, this.#usernameEl, modeEl);
    }
}
