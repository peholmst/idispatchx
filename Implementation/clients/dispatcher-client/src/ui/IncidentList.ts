import STYLES from './IncidentList.css?inline';
import type { CadRestClient } from '../cad/CadRestClient.ts';
import type { DispatcherWebSocketClient } from '../cad/DispatcherWebSocketClient.ts';
import type { IncidentSummary, Location, Coordinates } from '../cad/types.ts';
import { filterByVicinity } from '../geo/vicinity.ts';
import { t } from '../i18n/index.ts';

export class IncidentSelectedEvent extends CustomEvent<string> {
    static readonly TYPE = 'incident-selected' as const;
    constructor(incidentId: string) {
        super(IncidentSelectedEvent.TYPE, { detail: incidentId, bubbles: true, composed: true });
    }
}

type SortColumn = 'incidentCreated' | 'incidentType' | 'incidentPriority' | 'location' | 'state' | 'linkedCalls';
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
            return muniName ? `${muniName}, ${a}` : a;
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
        });
    } catch {
        return iso;
    }
}

/**
 * `<idispatch-incident-list>` — live incident list with filtering, sorting, and vicinity support.
 */
export class IncidentList extends HTMLElement {
    static readonly TAG = 'idispatch-incident-list' as const;

    #shadow: ShadowRoot;
    #cadRest: CadRestClient | null = null;
    #wsClient: DispatcherWebSocketClient | null = null;

    #incidents = new Map<string, IncidentSummary>();
    #filterText = '';
    #showEnded = false;
    #sortCol: SortColumn = 'incidentCreated';
    #sortDir: SortDir = 'desc';
    #vicinityCenter: Coordinates | null = null;
    #vicinityRadius = 1000;

    #filterInput!: HTMLInputElement;
    #showEndedToggle!: HTMLInputElement;
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
        void this.#loadIncidents();
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

        // Filter row
        const filterRow = document.createElement('div');
        filterRow.className = 'filter-row';

        this.#filterInput = document.createElement('input');
        this.#filterInput.type = 'search';
        this.#filterInput.className = 'filter-input';
        this.#filterInput.placeholder = t('incidentList.filter.placeholder');
        this.#filterInput.addEventListener('input', () => {
            this.#filterText = this.#filterInput.value.toLowerCase();
            this.#renderTable();
        });

        const endedToggleWrapper = document.createElement('label');
        endedToggleWrapper.className = 'ended-toggle';
        this.#showEndedToggle = document.createElement('input');
        this.#showEndedToggle.type = 'checkbox';
        this.#showEndedToggle.addEventListener('change', () => {
            this.#showEnded = this.#showEndedToggle.checked;
            void this.#loadIncidents();
        });
        const endedLabel = document.createElement('span');
        endedLabel.textContent = t('incidentList.showEnded');
        endedToggleWrapper.append(this.#showEndedToggle, endedLabel);

        filterRow.append(this.#filterInput, endedToggleWrapper);

        // Table
        const tableWrapper = document.createElement('div');
        tableWrapper.className = 'table-wrapper';
        const table = document.createElement('table');
        table.className = 'incident-table';

        const thead = document.createElement('thead');
        const headerRow = document.createElement('tr');
        const headers: [SortColumn, string][] = [
            ['incidentCreated', t('incidentList.header.created')],
            ['incidentType', t('incidentList.header.type')],
            ['incidentPriority', t('incidentList.header.priority')],
            ['location', t('incidentList.header.location')],
            ['state', t('incidentList.header.state')],
            ['linkedCalls', t('incidentList.header.linkedCalls')],
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

    async #loadIncidents(): Promise<void> {
        if (!this.#cadRest) return;
        try {
            const incidents = await this.#cadRest.listIncidents(this.#showEnded);
            this.#incidents.clear();
            for (const inc of incidents) {
                this.#incidents.set(inc.incidentId, inc);
            }
            this.#renderTable();
        } catch (err) {
            console.error('[IncidentList] Failed to load incidents:', err);
        }
    }

    #subscribeWebSocket(): void {
        if (!this.#wsClient) return;

        this.#wsClient.onIncidentCreated((payload) => {
            const summary: IncidentSummary = {
                incidentId: payload.incidentId,
                state: payload.state,
                incidentCreated: payload.incidentCreated,
                incidentEnded: null,
                incidentType: payload.incidentType,
                incidentPriority: payload.incidentPriority,
                location: payload.location,
                description: payload.description,
                callIds: [],
            };
            this.#incidents.set(payload.incidentId, summary);
            this.#renderTable();
        });

        this.#wsClient.onIncidentStateChanged((payload) => {
            const inc = this.#incidents.get(payload.incidentId);
            if (inc) {
                const updated: IncidentSummary = {
                    ...inc,
                    state: payload.newState,
                    incidentEnded: payload.newState === 'ended' ? new Date().toISOString() : inc.incidentEnded,
                };
                this.#incidents.set(payload.incidentId, updated);
                if (!this.#showEnded && payload.newState === 'ended') {
                    this.#incidents.delete(payload.incidentId);
                }
                this.#renderTable();
            }
        });

        this.#wsClient.onIncidentDetailsUpdated((payload) => {
            const inc = this.#incidents.get(payload.incidentId);
            if (inc) {
                this.#incidents.set(payload.incidentId, {
                    ...inc,
                    incidentType: payload.incidentType ?? inc.incidentType,
                    incidentPriority: payload.incidentPriority ?? inc.incidentPriority,
                    location: payload.location ?? inc.location,
                    description: payload.description ?? inc.description,
                });
                this.#renderTable();
            }
        });

        this.#wsClient.onIncidentLogEntryAdded((payload) => {
            // A log entry may include a call link — refresh callIds count
            const inc = this.#incidents.get(payload.incidentId);
            if (inc) {
                const changeData = payload.logEntry.entryType === 'automatic'
                    ? (payload.logEntry as { changeData: { type?: string; callId?: string } }).changeData
                    : null;
                if (changeData?.type === 'call_attached' && changeData.callId) {
                    this.#incidents.set(payload.incidentId, {
                        ...inc,
                        callIds: [...new Set([...inc.callIds, changeData.callId])],
                    });
                    this.#renderTable();
                }
            }
        });
    }

    #toggleSort(col: SortColumn): void {
        if (this.#sortCol === col) {
            this.#sortDir = this.#sortDir === 'asc' ? 'desc' : 'asc';
        } else {
            this.#sortCol = col;
            this.#sortDir = col === 'incidentCreated' ? 'desc' : 'asc';
        }
        this.#renderTable();
    }

    #renderTable(): void {
        let items = [...this.#incidents.values()];

        // Hide ended by default
        if (!this.#showEnded) {
            items = items.filter((i) => i.state !== 'ended');
        }

        // Vicinity filter
        if (this.#vicinityCenter) {
            const itemsWithCoords = items.map((inc) => ({
                ...inc,
                coordinates: locationCoordinates(inc.location) ?? null,
            }));
            const filtered = filterByVicinity(itemsWithCoords, this.#vicinityCenter, this.#vicinityRadius);
            const filteredIds = new Set(filtered.map((i) => i.incidentId));
            items = items.filter((i) => filteredIds.has(i.incidentId));
        }

        // Text filter
        if (this.#filterText) {
            items = items.filter((i) => {
                return (
                    (i.incidentType?.toLowerCase().includes(this.#filterText) ?? false) ||
                    (i.description?.toLowerCase().includes(this.#filterText) ?? false) ||
                    locationSummary(i.location).toLowerCase().includes(this.#filterText) ||
                    i.state.toLowerCase().includes(this.#filterText)
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

        this.#tableBody.innerHTML = '';
        for (const inc of items) {
            const tr = document.createElement('tr');
            tr.className = `incident-row state-${inc.state}`;
            tr.dataset.incidentId = inc.incidentId;
            tr.setAttribute('tabindex', '0');
            tr.setAttribute('role', 'row');

            const cells = [
                formatTime(inc.incidentCreated),
                inc.incidentType ?? '—',
                inc.incidentPriority ?? '—',
                locationSummary(inc.location),
                t(`incident.state.${inc.state}` as Parameters<typeof t>[0]),
                String(inc.callIds.length),
            ];
            for (const text of cells) {
                const td = document.createElement('td');
                td.className = 'td';
                td.textContent = text;
                tr.appendChild(td);
            }

            tr.addEventListener('click', () => {
                this.dispatchEvent(new IncidentSelectedEvent(inc.incidentId));
            });
            tr.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    this.dispatchEvent(new IncidentSelectedEvent(inc.incidentId));
                }
            });

            this.#tableBody.appendChild(tr);
        }

        if (items.length === 0) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 6;
            td.className = 'td empty-cell';
            td.textContent = t('incidentList.empty');
            tr.appendChild(td);
            this.#tableBody.appendChild(tr);
        }
    }

    #cellValue(inc: IncidentSummary): string {
        switch (this.#sortCol) {
            case 'incidentCreated': return inc.incidentCreated;
            case 'incidentType': return inc.incidentType ?? '';
            case 'incidentPriority': return inc.incidentPriority ?? '';
            case 'location': return locationSummary(inc.location);
            case 'state': return inc.state;
            case 'linkedCalls': return String(inc.callIds.length).padStart(5, '0');
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
