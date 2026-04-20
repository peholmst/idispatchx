// Root Web Component for the secondary dispatcher window.
// Renders a header (without primary actions) and footer around an empty body placeholder.

import STYLES from './SecondaryWindow.css?inline';
import { WindowHeader, WindowFooter } from './WindowChrome.ts';

/** localStorage key set by this window so the launcher can detect it is open. */
export const SECONDARY_WINDOW_OPEN_KEY = 'idispatch:window:secondary' as const;

/**
 * `<idispatch-secondary-window>` Web Component.
 *
 * Full-viewport column flex: WindowHeader → .window-body → WindowFooter.
 * The header has no primary action buttons (those belong to the primary window).
 */
export class SecondaryWindow extends HTMLElement {
    static readonly TAG = 'idispatch-secondary-window' as const;

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
        this.#registerOpenFlag();

        const style = document.createElement('style');
        style.textContent = STYLES;

        const header = document.createElement(WindowHeader.TAG) as WindowHeader;
        header.showPrimaryActions = false;

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
        try {
            const opener = window.opener as Window | null;
            if (opener && !opener.closed) {
                opener.sessionStorage.setItem(SECONDARY_WINDOW_OPEN_KEY, '1');
            }
        } catch {
            // ignore
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
                opener.sessionStorage.removeItem(SECONDARY_WINDOW_OPEN_KEY);
            }
        } catch {
            // ignore
        }
    }
}
