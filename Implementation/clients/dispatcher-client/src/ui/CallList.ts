import STYLES from './CallList.css?inline';
import type { CadRestClient } from '../cad/CadRestClient.ts';
import type { DispatcherWebSocketClient } from '../cad/DispatcherWebSocketClient.ts';
import type { CallSummary, Location, Coordinates } from '../cad/types.ts';
import { filterByVicinity } from '../geo/vicinity.ts';
import { t } from '../i18n/index.ts';

export class CallSelectedEvent extends CustomEvent<string> {
    static readonly TYPE = 'call-selected' as const;
    constructor(callId: string) {
        super(CallSelectedEvent.TYPE, { detail: callId, bubbles: true, composed: true });
    }
}

type SortColumn = 'callStarted' | 'callerName' | 'callerPhoneNumber' | 'location' | 'state' | 'outcome' | 'receivingDispatcher';
type SortDir = 'asc' | 'desc';

function locationSummary(loc: Location | null): string {
    if (!loc) return '';
    const muniName = loc.municipality?.name['fi'] ?? '';
    switch (loc.type) {
        case 'exact_address': {
            const name = loc.addressName['fi'] ?? Object.values(loc.addressName)[0] ?? '';
            return muniName ? `${muniName}, ${name}` : name;
        }
        case 'road_intersection': {
            const a = loc.roadNameA['fi'] ?? Object.values(loc.roadNameA)[0] ?? '';
            const b = loc.roadNameB['fi'] ?? Object.values(loc.roadNameB)[0] ?? '';
            return muniName ? `${muniName}, ${a} / ${b}` : `${a} / ${b}`;
        }
        case 'named_place': {
            const name = loc.name['fi'] ?? Object.values(loc.name)[0] ?? '';
            return muniName ? `${muniName}, ${name}` : name;
        }
        case 'relative_location': {
            const ref = loc.referencePlace['fi'] ?? Object.values(loc.referencePlace)[0] ?? '';
            return muniName ? `${muniName}, ${ref}` : ref;
        }
    }
}

function locationCoordinates(loc: Location | null): Coordinates | undefined {
    return loc?.coordinates ?? undefined;
}

function formatTime(iso: string): string {
    try {
        return new Date(iso).toLocaleTimeString('fi-FI', {
            timeZone: 'Europe/Helsinki',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
        });
    } catch {
        return iso;
    }
}

/**
 * `<idispatch-call-list>` — live call list with filtering, sorting, and vicinity support.
 */
export class CallList extends HTMLElement {
    static readonly TAG = 'idispatch-call-list' as const;

    #shadow: ShadowRoot;
    #cadRest: CadRestClient | null = null;
    #wsClient: DispatcherWebSocketClient | null = null;

    #calls = new Map<string, CallSummary>();
    #filterText = '';
    #sortCol: SortColumn = 'callStarted';
    #sortDir: SortDir = 'desc';
    #vicinityCenter: Coordinates | null = null;
    #vicinityRadius = 1000;

    #filterInput!: HTMLInputElement;
    #tableBody!: HTMLTableSectionElement;
    #vicinityBanner!: HTMLDivElement;

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
    }

    initialize(cadRest: CadRestClient, wsClient: DispatcherWebSocketClient): void {
        this.#cadRest = cadRest;
        this.#wsClient = wsClient;
    }

    connectedCallback(): void {
        const style = document.createElement('style');
        style.textContent = STYLES;
        this.#shadow.appendChild(style);
        this.#buildDom();
        this.#subscribeWebSocket();
        void this.#loadCalls();
    }

    setVicinityFilter(center: Coordinates | null, radiusMeters = 1000): void {
        this.#vicinityCenter = center;
        this.#vicinityRadius = radiusMeters;
        this.#updateVicinityBanner();
        this.#renderTable();
    }

    #buildDom(): void {
        // Vicinity banner
        this.#vicinityBanner = document.createElement('div');
        this.#vicinityBanner.className = 'vicinity-banner hidden';
        const bannerText = document.createElement('span');
        bannerText.className = 'banner-text';
        bannerText.textContent = t('vicinity.banner');
        const clearBtn = document.createElement('button');
        clearBtn.type = 'button';
        clearBtn.className = 'banner-clear';
        clearBtn.textContent = t('vicinity.clear');
        clearBtn.addEventListener('click', () => this.setVicinityFilter(null));
        this.#vicinityBanner.append(bannerText, clearBtn);

        // Filter input
        const filterRow = document.createElement('div');
        filterRow.className = 'filter-row';
        this.#filterInput = document.createElement('input');
        this.#filterInput.type = 'search';
        this.#filterInput.className = 'filter-input';
        this.#filterInput.placeholder = t('callList.filter.placeholder');
        this.#filterInput.addEventListener('input', () => {
            this.#filterText = this.#filterInput.value.toLowerCase();
            this.#renderTable();
        });
        filterRow.appendChild(this.#filterInput);

        // Table
        const tableWrapper = document.createElement('div');
        tableWrapper.className = 'table-wrapper';
        const table = document.createElement('table');
        table.className = 'call-table';

        const thead = document.createElement('thead');
        const headerRow = document.createElement('tr');
        const headers: [SortColumn, string][] = [
            ['callStarted', t('callList.header.started')],
            ['callerName', t('callList.header.callerName')],
            ['callerPhoneNumber', t('callList.header.callerPhone')],
            ['location', t('callList.header.location')],
            ['state', t('callList.header.state')],
            ['outcome', t('callList.header.outcome')],
            ['receivingDispatcher', t('callList.header.dispatcher')],
        ];
        for (const [col, label] of headers) {
            const th = document.createElement('th');
            th.className = 'th';
            th.dataset.col = col;
            th.textContent = label;
            th.addEventListener('click', () => this.#toggleSort(col));
            headerRow.appendChild(th);
        }
        thead.appendChild(headerRow);

        this.#tableBody = document.createElement('tbody');
        table.append(thead, this.#tableBody);
        tableWrapper.appendChild(table);

        this.#shadow.append(this.#vicinityBanner, filterRow, tableWrapper);
    }

    async #loadCalls(): Promise<void> {
        if (!this.#cadRest) return;
        try {
            const calls = await this.#cadRest.listActiveCalls();
            this.#calls.clear();
            for (const call of calls) {
                this.#calls.set(call.callId, call);
            }
            this.#renderTable();
        } catch (err) {
            console.error('[CallList] Failed to load calls:', err);
        }
    }

    #subscribeWebSocket(): void {
        if (!this.#wsClient) return;

        this.#wsClient.onCallCreated((payload) => {
            this.#calls.set(payload.callId, payload);
            this.#renderTable();
        });

        this.#wsClient.onCallUpdated((payload) => {
            if (this.#calls.has(payload.callId)) {
                this.#calls.set(payload.callId, payload);
                this.#renderTable();
            }
        });

        this.#wsClient.onCallEnded((payload) => {
            this.#calls.delete(payload.callId);
            this.#renderTable();
        });

        this.#wsClient.onCallAttachedToIncident((payload) => {
            const call = this.#calls.get(payload.callId);
            if (call) {
                this.#calls.set(payload.callId, {
                    ...call,
                    incidentId: payload.incidentId,
                    outcome: 'attached_to_incident',
                });
                this.#renderTable();
            }
        });

        this.#wsClient.onCallDetachedFromIncident((payload) => {
            const call = this.#calls.get(payload.callId);
            if (call) {
                this.#calls.set(payload.callId, { ...call, incidentId: null, outcome: null });
                this.#renderTable();
            }
        });
    }

    #toggleSort(col: SortColumn): void {
        if (this.#sortCol === col) {
            this.#sortDir = this.#sortDir === 'asc' ? 'desc' : 'asc';
        } else {
            this.#sortCol = col;
            this.#sortDir = col === 'callStarted' ? 'desc' : 'asc';
        }
        this.#renderTable();
    }

    #renderTable(): void {
        let items = [...this.#calls.values()];

        // Vicinity filter
        if (this.#vicinityCenter) {
            const itemsWithCoords = items.map((c) => ({
                ...c,
                coordinates: locationCoordinates(c.location) ?? null,
            }));
            const filtered = filterByVicinity(itemsWithCoords, this.#vicinityCenter, this.#vicinityRadius);
            const filteredIds = new Set(filtered.map((c) => c.callId));
            items = items.filter((c) => filteredIds.has(c.callId));
        }

        // Text filter
        if (this.#filterText) {
            items = items.filter((c) => {
                return (
                    (c.callerName?.toLowerCase().includes(this.#filterText) ?? false) ||
                    (c.callerPhoneNumber?.toLowerCase().includes(this.#filterText) ?? false) ||
                    (c.description?.toLowerCase().includes(this.#filterText) ?? false) ||
                    locationSummary(c.location).toLowerCase().includes(this.#filterText)
                );
            });
        }

        // Sort
        items.sort((a, b) => {
            const va = this.#cellValue(a);
            const vb = this.#cellValue(b);
            const cmp = va < vb ? -1 : va > vb ? 1 : 0;
            return this.#sortDir === 'asc' ? cmp : -cmp;
        });

        // Render rows
        this.#tableBody.innerHTML = '';
        for (const call of items) {
            const tr = document.createElement('tr');
            tr.className = 'call-row';
            tr.dataset.callId = call.callId;
            tr.setAttribute('tabindex', '0');
            tr.setAttribute('role', 'row');

            const cells = [
                formatTime(call.callStarted),
                call.callerName ?? '',
                call.callerPhoneNumber ?? '',
                locationSummary(call.location),
                call.state,
                call.outcome ?? '',
                call.receivingDispatcher,
            ];
            for (const text of cells) {
                const td = document.createElement('td');
                td.className = 'td';
                td.textContent = text;
                tr.appendChild(td);
            }

            tr.addEventListener('click', () => {
                this.dispatchEvent(new CallSelectedEvent(call.callId));
            });
            tr.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    this.dispatchEvent(new CallSelectedEvent(call.callId));
                }
            });

            this.#tableBody.appendChild(tr);
        }

        if (items.length === 0) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 7;
            td.className = 'td empty-cell';
            td.textContent = t('callList.empty');
            tr.appendChild(td);
            this.#tableBody.appendChild(tr);
        }
    }

    #cellValue(call: CallSummary): string {
        switch (this.#sortCol) {
            case 'callStarted': return call.callStarted;
            case 'callerName': return call.callerName ?? '';
            case 'callerPhoneNumber': return call.callerPhoneNumber ?? '';
            case 'location': return locationSummary(call.location);
            case 'state': return call.state;
            case 'outcome': return call.outcome ?? '';
            case 'receivingDispatcher': return call.receivingDispatcher;
        }
    }

    #updateVicinityBanner(): void {
        if (this.#vicinityCenter) {
            this.#vicinityBanner.classList.remove('hidden');
        } else {
            this.#vicinityBanner.classList.add('hidden');
        }
    }
}
