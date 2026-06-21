import STYLES from './SecondaryWindow.css?inline';
import OL_CSS from 'ol/ol.css?inline';
import { WindowHeader, WindowFooter } from './WindowChrome.ts';
import { LookupBar } from './LookupBar.ts';
import { LayersClient } from '../gis/LayersClient.ts';
import { GeocodingClient } from '../gis/GeocodingClient.ts';
import type { HttpClient } from '../http/HttpClient.ts';
import { UnauthorizedError } from '../http/HttpClient.ts';
import type { AuthState } from '../auth/AuthState.ts';
import { t } from '../i18n/index.ts';
import OlMap from 'ol/Map';
import View from 'ol/View';
import TileLayer from 'ol/layer/Tile';
import VectorSource from 'ol/source/Vector';
import WMTS from 'ol/source/WMTS';
import WMTSTileGrid from 'ol/tilegrid/WMTS';
import { fromLonLat } from 'ol/proj';
import { register } from 'ol/proj/proj4';
import type Tile from 'ol/Tile';
import type ImageTile from 'ol/ImageTile';
import proj4 from 'proj4';

// Register EPSG:3067 (ETRS89 / TM35FIN) once at module load time.
proj4.defs('EPSG:3067', '+proj=utm +zone=35 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs +type=crs');
register(proj4);

const ETRS_RESOLUTIONS = Array.from({ length: 16 }, (_, i) => 8192 / Math.pow(2, i));
const ETRS_MATRIX_IDS = Array.from({ length: 16 }, (_, i) => String(i));
// Top-left origin of the ETRS-TM35FIN tile matrix set (EPSG:3067 metres).
const ETRS_ORIGIN: [number, number] = [-548576.0, 8388608.0];

/** localStorage key set by this window so the launcher can detect it is open. */
export const SECONDARY_WINDOW_OPEN_KEY = 'idispatch:window:secondary' as const;

export class SecondaryWindow extends HTMLElement {
    static readonly TAG = 'idispatch-secondary-window' as const;

    #shadow: ShadowRoot;
    #username = '';
    #gisServerUrl = '';
    #authState: AuthState | null = null;
    #http: HttpClient | null = null;
    #olMap: OlMap | null = null;
    #tileLayer: TileLayer<WMTS> | null = null;
    #baseLayerSelect: HTMLSelectElement | null = null;
    #gisErrorBanner: HTMLDivElement | null = null;
    #onBeforeUnload: (() => void) | null = null;

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
    }

    initialize(username: string, gisServerUrl: string, authState: AuthState, http: HttpClient): void {
        this.#username = username;
        this.#gisServerUrl = gisServerUrl;
        this.#authState = authState;
        this.#http = http;
    }

    connectedCallback(): void {
        this.#registerOpenFlag();

        const style = document.createElement('style');
        style.textContent = STYLES;

        const olStyle = document.createElement('style');
        olStyle.textContent = OL_CSS;

        const header = document.createElement(WindowHeader.TAG) as WindowHeader;
        header.append(...this.#makeLayoutButtons());

        const body = document.createElement('div');
        body.className = 'window-body';

        const mapContainer = document.createElement('div');
        mapContainer.className = 'map-container';

        mapContainer.appendChild(this.#makeToolbar());

        const gisErrorBanner = document.createElement('div');
        gisErrorBanner.className = 'gis-error-banner';
        gisErrorBanner.hidden = true;
        this.#gisErrorBanner = gisErrorBanner;
        mapContainer.appendChild(gisErrorBanner);

        const lookupBarEl = document.createElement(LookupBar.TAG) as LookupBar;
        mapContainer.appendChild(lookupBarEl);

        const mapEl = document.createElement('div');
        mapEl.className = 'ol-map-target';
        mapContainer.appendChild(mapEl);

        body.appendChild(mapContainer);

        const footer = document.createElement(WindowFooter.TAG) as WindowFooter;
        footer.username = this.#username;

        this.#shadow.append(style, olStyle, header, body, footer);

        this.#initMap(mapEl, lookupBarEl);
        void this.#loadLayers();
    }

    disconnectedCallback(): void {
        this.#unregisterOpenFlag();
        this.#olMap?.setTarget(undefined);
        this.#olMap = null;
    }

    #makeToolbar(): HTMLElement {
        const toolbar = document.createElement('div');
        toolbar.className = 'map-toolbar';

        const baseLabel = document.createElement('span');
        baseLabel.className = 'map-toolbar-label';
        baseLabel.textContent = t('lookup.toolbar.base');

        const select = document.createElement('select');
        select.className = 'layer-select';
        select.disabled = true;

        const loadingOpt = document.createElement('option');
        loadingOpt.textContent = t('lookup.layers.loading');
        select.appendChild(loadingOpt);
        this.#baseLayerSelect = select;

        select.addEventListener('change', () => {
            if (select.value) this.#updateTileLayerSource(select.value);
        });

        const sep = document.createElement('span');
        sep.className = 'map-toolbar-separator';

        const layersLabel = document.createElement('span');
        layersLabel.className = 'map-toolbar-label';
        layersLabel.textContent = t('lookup.toolbar.layers');

        const layerGroup = document.createElement('div');
        layerGroup.className = 'map-toolbar-group';

        for (const name of ['Stations', 'Units', 'Incidents', 'Calls']) {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'layer-toggle active';
            btn.innerHTML = `<span class="toggle-dot"></span> ${name}`;
            btn.addEventListener('click', () => btn.classList.toggle('active'));
            layerGroup.appendChild(btn);
        }

        toolbar.append(baseLabel, select, sep, layersLabel, layerGroup);
        return toolbar;
    }

    #initMap(mapEl: HTMLElement, lookupBarEl: LookupBar): void {
        this.#tileLayer = new TileLayer<WMTS>({ source: undefined });

        const markerSource = new VectorSource();

        this.#olMap = new OlMap({
            target: mapEl,
            layers: [this.#tileLayer],
            view: new View({
                projection: 'EPSG:3067',
                center: fromLonLat([26, 65], 'EPSG:3067'),
                zoom: 5,
                resolutions: ETRS_RESOLUTIONS,
            }),
        });

        lookupBarEl.initialize(
            new GeocodingClient(this.#gisServerUrl, this.#http!),
            this.#olMap,
            markerSource,
        );
    }

    #buildWmtsSource(layerId: string): WMTS {
        const authState = this.#authState!;
        const gisServerUrl = this.#gisServerUrl;

        const tileGrid = new WMTSTileGrid({
            origin: ETRS_ORIGIN,
            resolutions: ETRS_RESOLUTIONS,
            matrixIds: ETRS_MATRIX_IDS,
            tileSize: 256,
        });

        return new WMTS({
            layer: layerId,
            style: 'default',
            matrixSet: 'ETRS-TM35FIN',
            tileGrid,
            url: `${gisServerUrl}/wmts/${layerId}/ETRS-TM35FIN/{TileMatrix}/{TileRow}/{TileCol}.png`,
            requestEncoding: 'REST',
            projection: 'EPSG:3067',
            tileLoadFunction: (tile: Tile, src: string) => {
                const img = (tile as ImageTile).getImage();
                if (!(img instanceof HTMLImageElement)) return;
                fetch(src, {
                    headers: { 'Authorization': `Bearer ${authState.getAccessToken() ?? ''}` },
                })
                    .then(r => r.blob())
                    .then(blob => {
                        const url = URL.createObjectURL(blob);
                        img.onload = () => URL.revokeObjectURL(url);
                        img.src = url;
                    })
                    .catch(() => { /* tile load failed */ });
            },
        });
    }

    #updateTileLayerSource(layerId: string): void {
        this.#tileLayer?.setSource(this.#buildWmtsSource(layerId));
    }

    async #loadLayers(): Promise<void> {
        const select = this.#baseLayerSelect;
        if (!select || !this.#http) return;

        const client = new LayersClient(this.#gisServerUrl, this.#http);
        try {
            const layers = await client.getLayers();

            select.innerHTML = '';
            if (layers.length === 0) {
                const opt = document.createElement('option');
                opt.value = '';
                opt.textContent = t('lookup.layers.unavailable');
                select.appendChild(opt);
                select.disabled = true;
                return;
            }

            for (const layer of layers) {
                const opt = document.createElement('option');
                opt.value = layer.id;
                opt.textContent = layer.title;
                select.appendChild(opt);
            }
            select.disabled = false;
            this.#updateTileLayerSource(layers[0].id);
        } catch (err) {
            select.innerHTML = '';
            const opt = document.createElement('option');
            opt.value = '';
            opt.textContent = t('lookup.layers.unavailable');
            select.appendChild(opt);
            select.disabled = true;

            if (err instanceof UnauthorizedError && this.#gisErrorBanner) {
                this.#gisErrorBanner.textContent = t('gis.unavailable.unauthorized');
                this.#gisErrorBanner.hidden = false;
            }
        }
    }

    #makeLayoutButtons(): HTMLButtonElement[] {
        const presets: { blocks: number[]; title: string }[] = [
            { blocks: [18, 3],  title: 'Map only (Ctrl+Shift+1)' },
            { blocks: [14, 7],  title: 'Map 2:1 Dashboard (Ctrl+Shift+2)' },
            { blocks: [10, 10], title: 'Map 1:1 Dashboard (Ctrl+Shift+3)' },
            { blocks: [7, 14],  title: 'Map 1:2 Dashboard (Ctrl+Shift+4)' },
            { blocks: [3, 18],  title: 'Dashboard only (Ctrl+Shift+5)' },
        ];

        return presets.map((preset, i) => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = i === 2 ? 'layout-btn-secondary active' : 'layout-btn-secondary';
            btn.title = preset.title;
            btn.slot = 'layout';
            for (const w of preset.blocks) {
                const block = document.createElement('span');
                block.className = 'layout-block';
                block.style.width = `${w}px`;
                btn.appendChild(block);
            }
            return btn;
        });
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
