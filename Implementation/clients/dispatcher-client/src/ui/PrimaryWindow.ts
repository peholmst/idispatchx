import STYLES from './PrimaryWindow.css?inline';
import { WindowHeader, WindowFooter } from './WindowChrome.ts';
import { CallDetailForm, CoordinatesKnownEvent } from './CallDetailForm.ts';
import { CallList, CallSelectedEvent } from './CallList.ts';
import { IncidentList } from './IncidentList.ts';
import { CadRestClient } from '../cad/CadRestClient.ts';
import { DispatcherWebSocketClient } from '../cad/DispatcherWebSocketClient.ts';
import { GeocodingClient } from '../gis/GeocodingClient.ts';
import type { HttpClient } from '../http/HttpClient.ts';
import type { AuthState } from '../auth/AuthState.ts';
import { t } from '../i18n/index.ts';

export const PRIMARY_WINDOW_OPEN_KEY = 'idispatch:window:primary' as const;

/**
 * `<idispatch-primary-window>` — three-column layout:
 *   Left: call detail form  |  Centre: incident detail placeholder  |  Right: call + incident lists
 */
export class PrimaryWindow extends HTMLElement {
    static readonly TAG = 'idispatch-primary-window' as const;

    #shadow: ShadowRoot;
    #username = '';
    #cadRest: CadRestClient | null = null;
    #wsClient: DispatcherWebSocketClient | null = null;

    #callDetailForm: CallDetailForm | null = null;
    #callList: CallList | null = null;
    #incidentList: IncidentList | null = null;
    #connectionBanner: HTMLDivElement | null = null;
    #onBeforeUnload: (() => void) | null = null;

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
    }

    initialize(
        username: string,
        cadServerUrl: string,
        gisServerUrl: string,
        http: HttpClient,
        authState: AuthState,
    ): void {
        this.#username = username;
        this.#cadRest = new CadRestClient(cadServerUrl, http);
        this.#wsClient = new DispatcherWebSocketClient(cadServerUrl, authState);

        const geocodingClient = new GeocodingClient(gisServerUrl, http);

        this.#stashedGeocodingClient = geocodingClient;
    }

    // Temporary stash between initialize() and connectedCallback()
    #stashedGeocodingClient: GeocodingClient | null = null;

    connectedCallback(): void {
        this.#registerOpenFlag();

        const style = document.createElement('style');
        style.textContent = STYLES;
        this.#shadow.appendChild(style);

        this.#buildLayout();
        this.#wsClient?.connect();
        this.#wireWebSocketConnection();
    }

    disconnectedCallback(): void {
        this.#wsClient?.disconnect();
        this.#unregisterOpenFlag();
    }

    #buildLayout(): void {
        // Header
        const header = document.createElement(WindowHeader.TAG) as WindowHeader;
        const newCallBtn = document.createElement('button');
        newCallBtn.type = 'button';
        newCallBtn.className = 'header-action-btn';
        newCallBtn.textContent = t('primaryWindow.newCall');
        newCallBtn.slot = 'actions';
        newCallBtn.addEventListener('click', () => void this.#onNewCall());

        const newIncidentBtn = document.createElement('button');
        newIncidentBtn.type = 'button';
        newIncidentBtn.className = 'header-action-btn';
        newIncidentBtn.textContent = t('primaryWindow.newIncident');
        newIncidentBtn.slot = 'actions';

        header.append(newCallBtn, newIncidentBtn);
        header.append(...this.#makeLayoutButtons());

        // Connection banner (hidden by default)
        this.#connectionBanner = document.createElement('div');
        this.#connectionBanner.className = 'connection-banner hidden';

        // Body — three columns
        const body = document.createElement('div');
        body.className = 'window-body';

        // Left column: call detail form
        const leftCol = document.createElement('div');
        leftCol.className = 'col col-left';
        this.#callDetailForm = document.createElement(CallDetailForm.TAG) as CallDetailForm;
        if (this.#cadRest && this.#wsClient && this.#stashedGeocodingClient) {
            this.#callDetailForm.initialize(this.#cadRest, this.#wsClient, this.#stashedGeocodingClient);
        }
        leftCol.appendChild(this.#callDetailForm);

        // Centre column: incident detail placeholder
        const centreCol = document.createElement('div');
        centreCol.className = 'col col-centre';
        const incidentPlaceholder = document.createElement('div');
        incidentPlaceholder.className = 'incident-placeholder';
        incidentPlaceholder.textContent = t('primaryWindow.empty');
        centreCol.appendChild(incidentPlaceholder);

        // Right column: call list + incident list
        const rightCol = document.createElement('div');
        rightCol.className = 'col col-right';

        this.#callList = document.createElement(CallList.TAG) as CallList;
        if (this.#cadRest && this.#wsClient) {
            this.#callList.initialize(this.#cadRest, this.#wsClient);
        }
        this.#callList.className = 'right-top';

        this.#incidentList = document.createElement(IncidentList.TAG) as IncidentList;
        if (this.#cadRest && this.#wsClient) {
            this.#incidentList.initialize(this.#cadRest, this.#wsClient);
        }
        this.#incidentList.className = 'right-bottom';

        rightCol.append(this.#callList, this.#incidentList);
        body.append(leftCol, centreCol, rightCol);

        // Footer
        const footer = document.createElement(WindowFooter.TAG) as WindowFooter;
        footer.username = this.#username;

        this.#shadow.append(header, this.#connectionBanner, body, footer);

        // Wire up cross-component events (vicinity, call selection)
        this.#shadow.addEventListener(CallSelectedEvent.TYPE, (e: Event) => {
            void this.#onCallSelected((e as CallSelectedEvent).detail);
        });

        this.#shadow.addEventListener(CoordinatesKnownEvent.TYPE, (e: Event) => {
            const coords = (e as CoordinatesKnownEvent).detail;
            this.#callList?.setVicinityFilter(coords);
            this.#incidentList?.setVicinityFilter(coords);
        });
    }

    async #onNewCall(): Promise<void> {
        if (!this.#cadRest) return;
        try {
            const { callId } = await this.#cadRest.createCall();
            const call = await this.#cadRest.getCall(callId);
            this.#callDetailForm?.loadCall(call);
        } catch (err) {
            console.error('[PrimaryWindow] Failed to create call:', err);
        }
    }

    async #onCallSelected(callId: string): Promise<void> {
        if (!this.#cadRest) return;
        try {
            const call = await this.#cadRest.getCall(callId);
            this.#callDetailForm?.loadCall(call);
        } catch (err) {
            console.error('[PrimaryWindow] Failed to load call:', err);
        }
    }

    #wireWebSocketConnection(): void {
        if (!this.#wsClient) return;
        this.#wsClient.onConnectionChanged((connected: boolean) => {
            if (!this.#connectionBanner) return;
            if (connected) {
                this.#connectionBanner.classList.add('hidden');
                // The server does not replay missed events on reconnect, so re-fetch
                // via REST to pick up any calls or incidents that changed while offline.
                this.#callList?.refresh();
                this.#incidentList?.refresh();
            } else {
                this.#connectionBanner.textContent = t('ws.reconnecting');
                this.#connectionBanner.classList.remove('hidden');
            }
        });
    }

    #makeLayoutButtons(): HTMLButtonElement[] {
        const presets: { bars: number[]; title: string }[] = [
            { bars: [10, 4, 4], title: 'Call focused (Ctrl+Shift+1)' },
            { bars: [6, 6, 6],  title: 'Equal columns (Ctrl+Shift+2)' },
            { bars: [4, 10, 4], title: 'Incident focused (Ctrl+Shift+3)' },
            { bars: [4, 4, 10], title: 'Lists focused (Ctrl+Shift+4)' },
        ];
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
        try {
            const opener = window.opener as Window | null;
            if (opener && !opener.closed) {
                opener.sessionStorage.setItem(PRIMARY_WINDOW_OPEN_KEY, '1');
            }
        } catch { /* cross-origin */ }
        this.#onBeforeUnload = () => this.#unregisterOpenFlag();
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
        } catch { /* cross-origin */ }
    }
}
