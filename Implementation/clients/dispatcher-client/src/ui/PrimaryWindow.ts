// Root Web Component for the primary dispatcher window.
// Renders a header (with primary actions) and footer around an empty body placeholder.

import STYLES from './PrimaryWindow.css?inline';
import { WindowHeader, WindowFooter } from './WindowChrome.ts';
import { t } from '../i18n/index.ts';

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

        const newCallBtn = document.createElement('button');
        newCallBtn.type = 'button';
        newCallBtn.className = 'header-action-btn';
        newCallBtn.textContent = t('primaryWindow.newCall');
        newCallBtn.slot = 'actions';

        const newIncidentBtn = document.createElement('button');
        newIncidentBtn.type = 'button';
        newIncidentBtn.className = 'header-action-btn';
        newIncidentBtn.textContent = t('primaryWindow.newIncident');
        newIncidentBtn.slot = 'actions';

        header.append(newCallBtn, newIncidentBtn);

        const layoutButtons = this.#makeLayoutButtons();
        header.append(...layoutButtons);

        const body = document.createElement('div');
        body.className = 'window-body';

        const footer = document.createElement(WindowFooter.TAG) as WindowFooter;
        footer.username = this.#username;

        this.#shadow.append(style, header, body, footer);
    }

    disconnectedCallback(): void {
        this.#unregisterOpenFlag();
    }

    /** Builds the four layout-preset buttons for the header-right slot. */
    #makeLayoutButtons(): HTMLButtonElement[] {
        const presets: { bars: number[]; title: string }[] = [
            { bars: [10, 4, 4], title: 'Call focused (Ctrl+Shift+1)' },
            { bars: [6, 6, 6],  title: 'Equal columns (Ctrl+Shift+2)' },
            { bars: [4, 10, 4], title: 'Incident focused (Ctrl+Shift+3)' },
            { bars: [4, 4, 10], title: 'Lists focused (Ctrl+Shift+4)' },
        ];

        // Buttons are visual-only placeholders; click handlers will be added
        // when layout switching behaviour is implemented.
        return presets.map((preset, i) => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = i === 1 ? 'layout-btn active' : 'layout-btn';
            btn.title = preset.title;
            btn.slot = 'layout';
            for (const w of preset.bars) {
                const bar = document.createElement('span');
                bar.className = 'layout-bar';
                bar.style.width = `${w}px`;
                btn.appendChild(bar);
            }
            return btn;
        });
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
