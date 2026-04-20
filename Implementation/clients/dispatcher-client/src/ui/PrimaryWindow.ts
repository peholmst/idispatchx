// Root Web Component for the primary dispatcher window.
// Renders a header (with primary actions) and footer around an empty body placeholder.

import STYLES from './PrimaryWindow.css?inline';
import { WindowHeader, WindowFooter } from './WindowChrome.ts';

/** localStorage key set by this window so the launcher can detect it is open. */
export const PRIMARY_WINDOW_OPEN_KEY = 'idispatch:window:primary' as const;

/**
 * `<idispatch-primary-window>` Web Component.
 *
 * Full-viewport column flex: WindowHeader → .window-body → WindowFooter.
 * The header includes "New Call" and "New Incident" action buttons.
 */
export class PrimaryWindow extends HTMLElement {
    static readonly TAG = 'idispatch-primary-window' as const;

    #shadow: ShadowRoot;
    #username = '';
    #onBeforeUnload: (() => void) | null = null;

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
    }

    initialize(username: string): void {
        this.#username = username;
    }

    connectedCallback(): void {
        // Signal to the launcher (via its sessionStorage, accessible as window.opener)
        // that this window is open. Cleared on beforeunload.
        this.#registerOpenFlag();

        const style = document.createElement('style');
        style.textContent = STYLES;

        const header = document.createElement(WindowHeader.TAG) as WindowHeader;
        header.showPrimaryActions = true;

        const body = document.createElement('div');
        body.className = 'window-body';

        const footer = document.createElement(WindowFooter.TAG) as WindowFooter;
        footer.username = this.#username;

        this.#shadow.append(style, header, body, footer);
    }

    disconnectedCallback(): void {
        this.#unregisterOpenFlag();
    }

    #registerOpenFlag(): void {
        // Write to the opener's sessionStorage (same-origin, so this is permitted).
        // This lets the launcher detect the window is open even after the launcher
        // has been refreshed and lost its in-memory window reference.
        try {
            const opener = window.opener as Window | null;
            if (opener && !opener.closed) {
                opener.sessionStorage.setItem(PRIMARY_WINDOW_OPEN_KEY, '1');
            }
        } catch {
            // window.opener may be null or cross-origin in some edge cases; ignore
        }

        this.#onBeforeUnload = () => { this.#unregisterOpenFlag(); };
        window.addEventListener('beforeunload', this.#onBeforeUnload);
    }

    #unregisterOpenFlag(): void {
        if (this.#onBeforeUnload) {
            window.removeEventListener('beforeunload', this.#onBeforeUnload);
            this.#onBeforeUnload = null;
        }
        try {
            const opener = window.opener as Window | null;
            if (opener && !opener.closed) {
                opener.sessionStorage.removeItem(PRIMARY_WINDOW_OPEN_KEY);
            }
        } catch {
            // ignore
        }
    }
}
