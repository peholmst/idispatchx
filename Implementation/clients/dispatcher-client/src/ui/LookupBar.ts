import STYLES from './LookupBar.css?inline';
import { t, getLocale } from '../i18n/index.ts';
import { parseCoordinates, validateFinlandBounds } from '../geo/coordinates.ts';
import type { GeocodingClient, GeocodeResult } from '../gis/GeocodingClient.ts';
import { GeocodingTimeoutError } from '../gis/GeocodingClient.ts';
import { HttpError } from '../http/HttpClient.ts';
import type OlMap from 'ol/Map';
import type VectorSource from 'ol/source/Vector';
import Feature from 'ol/Feature';
import Point from 'ol/geom/Point';
import { Style, Circle as CircleStyle, Fill, Stroke } from 'ol/style';
import VectorLayer from 'ol/layer/Vector';
import { fromLonLat } from 'ol/proj';
import type { Coordinate } from 'ol/coordinate';

const MARKER_STYLE = new Style({
    image: new CircleStyle({
        radius: 8,
        fill: new Fill({ color: '#ff6600' }),
        stroke: new Stroke({ color: '#ffffff', width: 2 }),
    }),
});

const DEBOUNCE_MS = 300;
const MIN_QUERY_LEN = 3;

export class LookupBar extends HTMLElement {
    static readonly TAG = 'idispatch-lookup-bar' as const;

    #shadow: ShadowRoot;
    #geocodingClient: GeocodingClient | null = null;
    #olMap: OlMap | null = null;
    #markerSource: VectorSource | null = null;
    #markerFeature: Feature<Point> | null = null;
    #markerCoord: Coordinate | null = null;
    #debounceTimer: ReturnType<typeof setTimeout> | null = null;
    #searchSeq = 0;

    // UI elements
    #addressInput: HTMLInputElement | null = null;
    #coordsInput: HTMLInputElement | null = null;
    #spinner: HTMLSpanElement | null = null;
    #inlineError: HTMLSpanElement | null = null;
    #dropdown: HTMLDivElement | null = null;
    #unavailableBanner: HTMLDivElement | null = null;

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
    }

    initialize(geocodingClient: GeocodingClient, olMap: OlMap, markerSource: VectorSource): void {
        this.#geocodingClient = geocodingClient;
        this.#olMap = olMap;
        this.#markerSource = markerSource;

        const markerLayer = new VectorLayer({
            source: markerSource,
            style: MARKER_STYLE,
        });
        olMap.addLayer(markerLayer);

        olMap.on('moveend', () => {
            if (this.#markerFeature && this.#markerCoord) {
                const center = olMap.getView().getCenter();
                if (center && !coordsClose(center, this.#markerCoord)) {
                    this.#clearMarker();
                }
            }
        });
    }

    connectedCallback(): void {
        const style = document.createElement('style');
        style.textContent = STYLES;
        this.#shadow.appendChild(style);
        this.#render();
    }

    #render(): void {
        const addrLabel = document.createElement('label');
        addrLabel.className = 'form-label';
        addrLabel.textContent = t('lookup.addressLabel');

        const addrInput = document.createElement('input');
        addrInput.type = 'text';
        addrInput.className = 'lookup-input';
        addrInput.placeholder = t('lookup.addressPlaceholder');
        this.#addressInput = addrInput;

        const coordsLabel = document.createElement('label');
        coordsLabel.className = 'form-label';
        coordsLabel.textContent = t('lookup.coordsLabel');

        const coordsInput = document.createElement('input');
        coordsInput.type = 'text';
        coordsInput.className = 'lookup-input coords-input';
        coordsInput.placeholder = t('lookup.coordsPlaceholder');
        this.#coordsInput = coordsInput;

        const lookupBtn = document.createElement('button');
        lookupBtn.type = 'button';
        lookupBtn.className = 'btn';
        lookupBtn.textContent = t('lookup.button');

        const clearBtn = document.createElement('button');
        clearBtn.type = 'button';
        clearBtn.className = 'btn btn-clear';
        clearBtn.textContent = t('lookup.clear');
        clearBtn.title = t('lookup.clear');

        const spinner = document.createElement('span');
        spinner.className = 'spinner';
        spinner.hidden = true;
        this.#spinner = spinner;

        const inlineError = document.createElement('span');
        inlineError.className = 'inline-error';
        inlineError.hidden = true;
        this.#inlineError = inlineError;

        const dropdown = document.createElement('div');
        dropdown.className = 'results-dropdown';
        dropdown.hidden = true;
        this.#dropdown = dropdown;

        addrInput.addEventListener('input', () => {
            this.#hideError();
            this.#hideUnavailableBanner();
            this.#scheduleSearch(addrInput.value.trim());
        });

        addrInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                this.#triggerLookup();
            }
        });

        coordsInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                this.#triggerLookup();
            }
        });

        coordsInput.addEventListener('input', () => {
            this.#hideError();
        });

        lookupBtn.addEventListener('click', () => this.#triggerLookup());
        clearBtn.addEventListener('click', () => this.#clearAll());

        this.#shadow.append(
            addrLabel, addrInput,
            coordsLabel, coordsInput,
            lookupBtn, clearBtn,
            spinner, inlineError,
            dropdown,
        );
    }

    #scheduleSearch(query: string): void {
        if (this.#debounceTimer !== null) clearTimeout(this.#debounceTimer);
        if (query.length < MIN_QUERY_LEN) {
            this.#hideDropdown();
            return;
        }
        this.#debounceTimer = setTimeout(() => {
            void this.#doAddressSearch(query);
        }, DEBOUNCE_MS);
    }

    #triggerLookup(): void {
        const addr = this.#addressInput?.value.trim() ?? '';
        const coords = this.#coordsInput?.value.trim() ?? '';

        this.#hideError();
        this.#hideUnavailableBanner();

        if (addr) {
            if (addr.length < MIN_QUERY_LEN) {
                this.#showError(t('lookup.tooShort'));
                return;
            }
            if (this.#debounceTimer !== null) clearTimeout(this.#debounceTimer);
            void this.#doAddressSearch(addr);
        } else if (coords) {
            this.#doCoordinateLookup(coords);
        }
    }

    async #doAddressSearch(query: string): Promise<void> {
        const seq = ++this.#searchSeq;
        this.#hideDropdown();
        this.#showSpinner();

        try {
            const response = await this.#geocodingClient!.search(query);
            if (seq !== this.#searchSeq) return; // superseded by a newer search
            this.#hideSpinner();

            if (response.resultCount === 0) {
                this.#showDropdown([]);
                return;
            }

            if (response.resultCount === 1) {
                this.#selectResult(response.results[0]);
                return;
            }

            this.#showDropdown(response.results);
        } catch (err) {
            if (seq !== this.#searchSeq) return;
            this.#hideSpinner();

            if (err instanceof GeocodingTimeoutError) {
                this.#showUnavailableBanner(t('lookup.timeout'));
            } else if (err instanceof HttpError || err instanceof Error) {
                this.#showUnavailableBanner(t('lookup.unavailable'));
            }
        }
    }

    #doCoordinateLookup(input: string): void {
        const parsed = parseCoordinates(input);
        if (!parsed) {
            this.#showError(t('lookup.coordsInvalid'));
            this.#coordsInput?.classList.add('error');
            return;
        }
        if (!validateFinlandBounds(parsed.lat, parsed.lon)) {
            this.#showError(t('lookup.coordsOutOfBounds'));
            this.#coordsInput?.classList.add('error');
            return;
        }

        this.#coordsInput?.classList.remove('error');
        const coord = fromLonLat([parsed.lon, parsed.lat], 'EPSG:3067');
        this.#centerAndMark(coord);
    }

    #selectResult(result: GeocodeResult): void {
        this.#hideDropdown();
        const coord = fromLonLat(
            [result.coordinates.longitude, result.coordinates.latitude],
            'EPSG:3067',
        );
        this.#centerAndMark(coord);
    }

    #centerAndMark(coord: Coordinate): void {
        this.#clearMarker();
        this.#markerCoord = coord;

        const feature = new Feature({ geometry: new Point(coord) });
        this.#markerFeature = feature;
        this.#markerSource?.addFeature(feature);

        this.#olMap?.getView().animate({ center: coord, duration: 300 });
    }

    #clearMarker(): void {
        if (this.#markerFeature && this.#markerSource) {
            this.#markerSource.removeFeature(this.#markerFeature);
        }
        this.#markerFeature = null;
        this.#markerCoord = null;
    }

    #clearAll(): void {
        this.#clearMarker();
        if (this.#addressInput) this.#addressInput.value = '';
        if (this.#coordsInput) this.#coordsInput.value = '';
        this.#coordsInput?.classList.remove('error');
        this.#hideDropdown();
        this.#hideError();
        this.#hideUnavailableBanner();
    }

    #showDropdown(results: GeocodeResult[]): void {
        const dropdown = this.#dropdown;
        if (!dropdown) return;
        dropdown.innerHTML = '';

        if (results.length === 0) {
            const msg = document.createElement('div');
            msg.className = 'no-results';
            msg.textContent = t('lookup.noResults');
            dropdown.appendChild(msg);
            dropdown.hidden = false;
            return;
        }

        const locale = getLocale();
        const localePref = locale === 'fi' ? ['fi', 'sv', 'en'] : locale === 'sv' ? ['sv', 'fi', 'en'] : ['en', 'fi', 'sv'];

        for (const result of results) {
            const item = document.createElement('div');
            item.className = 'result-item';
            item.setAttribute('role', 'option');

            const typeLabel = document.createElement('span');
            typeLabel.className = 'result-type';
            typeLabel.textContent = t(
                result.type === 'address' ? 'lookup.typeAddress' :
                result.type === 'intersection' ? 'lookup.typeIntersection' :
                'lookup.typePlace'
            );

            const nameEl = document.createElement('span');
            nameEl.className = 'result-name';
            nameEl.textContent = pickName(result.name, localePref);

            const municipalityEl = document.createElement('span');
            municipalityEl.className = 'result-municipality';
            municipalityEl.textContent = pickName(result.municipality.name, localePref);

            item.append(typeLabel, nameEl, municipalityEl);
            item.addEventListener('click', () => this.#selectResult(result));
            dropdown.appendChild(item);
        }

        dropdown.hidden = false;
    }

    #hideDropdown(): void {
        if (this.#dropdown) this.#dropdown.hidden = true;
    }

    #showSpinner(): void {
        if (this.#spinner) this.#spinner.hidden = false;
    }

    #hideSpinner(): void {
        if (this.#spinner) this.#spinner.hidden = true;
    }

    #showError(msg: string): void {
        if (this.#inlineError) {
            this.#inlineError.textContent = msg;
            this.#inlineError.hidden = false;
        }
    }

    #hideError(): void {
        if (this.#inlineError) this.#inlineError.hidden = true;
    }

    #showUnavailableBanner(msg: string): void {
        if (!this.#unavailableBanner) {
            const banner = document.createElement('div');
            banner.className = 'unavailable-banner';
            this.#shadow.appendChild(banner);
            this.#unavailableBanner = banner;
        }
        this.#unavailableBanner.textContent = msg;
        this.#unavailableBanner.hidden = false;
    }

    #hideUnavailableBanner(): void {
        if (this.#unavailableBanner) this.#unavailableBanner.hidden = true;
    }
}

function pickName(names: Record<string, string>, pref: string[]): string {
    for (const lang of pref) {
        if (names[lang]) return names[lang];
    }
    return Object.values(names)[0] ?? '';
}

function coordsClose(a: Coordinate, b: Coordinate, tolerance = 1): boolean {
    return Math.abs(a[0] - b[0]) < tolerance && Math.abs(a[1] - b[1]) < tolerance;
}
