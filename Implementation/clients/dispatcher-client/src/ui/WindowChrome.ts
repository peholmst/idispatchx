// Shared header and footer Web Components used by both the primary and secondary windows.

import STYLES from './WindowChrome.css?inline';
import { t } from '../i18n/index.ts';
import type { OperationalStatusService, OperationalStatus } from '../cad/OperationalStatusService.ts';

const HELSINKI_TZ = 'Europe/Helsinki';

// Reuse a single formatter instance — constructing Intl objects is expensive.
// formatToParts() is used to assemble an explicit "DD.MM.YYYY HH:mm:ss" string
// rather than relying on toLocaleString(), which can include locale-specific
// literals (e.g. 'klo' in fi-FI) that diverge from the intended format.
const HELSINKI_FORMATTER = new Intl.DateTimeFormat('fi-FI', {
    timeZone: HELSINKI_TZ,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
});

/** Returns "DD.MM.YYYY HH:mm:ss" in the Europe/Helsinki timezone, 24-hour clock. */
function formatHelsinki(date: Date): string {
    const parts = HELSINKI_FORMATTER.formatToParts(date);
    const p = (type: Intl.DateTimeFormatPartTypes): string =>
        parts.find(part => part.type === type)?.value ?? '00';
    return `${p('day')}.${p('month')}.${p('year')} ${p('hour')}:${p('minute')}:${p('second')}`;
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

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
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

        const appName = document.createElement('span');
        appName.className = 'header-app-name';
        appName.textContent = 'iDispatchX';

        this.#clockEl = document.createElement('span');
        this.#clockEl.className = 'header-clock';
        this.#clockEl.setAttribute('aria-live', 'off');
        this.#clockEl.setAttribute('aria-label', 'Current date and time');

        left.append(logo, appName, this.#clockEl);

        // Center: named slot for caller-provided action buttons
        const center = document.createElement('div');
        center.className = 'header-center';

        const actionsSlot = document.createElement('slot');
        actionsSlot.name = 'actions';
        center.appendChild(actionsSlot);

        // Right: named slot for caller-provided layout-preset buttons
        const right = document.createElement('div');
        right.className = 'header-right';

        const layoutSlot = document.createElement('slot');
        layoutSlot.name = 'layout';
        right.appendChild(layoutSlot);

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
 * indicator on the right. Set `username` and `operationalStatusService`
 * before or after connection.
 */
export class WindowFooter extends HTMLElement {
    static readonly TAG = 'idispatch-window-footer' as const;

    #shadow: ShadowRoot;
    #usernameEl: HTMLSpanElement | null = null;
    #modeDot: HTMLSpanElement | null = null;
    #modeText: HTMLSpanElement | null = null;
    // Buffers values set before connectedCallback creates the elements
    #pendingUsername = '';
    #statusService: OperationalStatusService | null = null;

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
    }

    set username(value: string) {
        this.#pendingUsername = value;
        if (this.#usernameEl) {
            this.#usernameEl.textContent = value;
        }
    }

    set operationalStatusService(service: OperationalStatusService) {
        this.#statusService = service;
        if (this.#modeText) {
            this.#applyStatus(service.getStatus());
            service.onStatusChanged(status => this.#applyStatus(status));
        }
    }

    connectedCallback(): void {
        this.classList.add('window-footer-host');

        const style = document.createElement('style');
        style.textContent = STYLES;

        this.#usernameEl = document.createElement('span');
        this.#usernameEl.className = 'footer-left';
        this.#usernameEl.textContent = this.#pendingUsername;

        const modeEl = document.createElement('span');
        modeEl.className = 'footer-right';

        this.#modeDot = document.createElement('span');
        this.#modeDot.className = 'mode-dot';
        this.#modeDot.setAttribute('aria-hidden', 'true');

        this.#modeText = document.createElement('span');

        modeEl.append(this.#modeDot, this.#modeText);
        this.#shadow.append(style, this.#usernameEl, modeEl);

        if (this.#statusService) {
            this.#applyStatus(this.#statusService.getStatus());
            this.#statusService.onStatusChanged(status => this.#applyStatus(status));
        } else {
            modeEl.hidden = true;
        }
    }

    #applyStatus(status: OperationalStatus): void {
        if (!this.#modeText || !this.#modeDot) return;

        if (!status.cadServerConnected) {
            this.#modeText.textContent = t('footer.degradedMode.noServer');
            this.#modeDot.classList.add('mode-dot--degraded');
        } else if (!status.cadArchiveAvailable && !status.gisServerAvailable) {
            this.#modeText.textContent = t('footer.degradedMode');
            this.#modeDot.classList.add('mode-dot--degraded');
        } else if (!status.cadArchiveAvailable) {
            this.#modeText.textContent = t('footer.degradedMode.noArchive');
            this.#modeDot.classList.add('mode-dot--degraded');
        } else if (!status.gisServerAvailable) {
            this.#modeText.textContent = t('footer.degradedMode.noGis');
            this.#modeDot.classList.add('mode-dot--degraded');
        } else {
            this.#modeText.textContent = t('footer.normalMode');
            this.#modeDot.classList.remove('mode-dot--degraded');
        }
    }
}
