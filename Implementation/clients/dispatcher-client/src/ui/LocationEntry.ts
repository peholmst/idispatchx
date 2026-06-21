import STYLES from './LocationEntry.css?inline';
import { CoordInput } from './CoordInput.ts';
import { parseCoordinates, validateFinlandBounds, formatCoordinates } from '../geo/coordinates.ts';
import type { Geocoder, GeocodeResult } from '../gis/GeocodingClient.ts';
import type {
    Location,
    Municipality,
    ExactAddressLocation,
    RoadIntersectionLocation,
    NamedPlaceLocation,
    RelativeLocation,
    Coordinates,
} from '../cad/types.ts';
import { t } from '../i18n/index.ts';

export type LocationVariant =
    | 'exact_address'
    | 'road_intersection'
    | 'named_place'
    | 'relative_location';

export class LocationChangedEvent extends CustomEvent<Location | null> {
    static readonly TYPE = 'location-changed' as const;
    constructor(location: Location | null) {
        super(LocationChangedEvent.TYPE, { detail: location, bubbles: true, composed: true });
    }
}

/**
 * `<idispatch-location-entry>` — flat location form.
 *
 * Variant resolves automatically:
 *   Number filled (no cross street, no approx) → ExactAddress
 *   Cross street filled (no number, no approx)  → RoadIntersection
 *   Neither + approx unchecked                  → NamedPlace
 *   Neither + approx checked                    → RelativeLocation
 */
export class LocationEntry extends HTMLElement {
    static readonly TAG = 'idispatch-location-entry' as const;

    #shadow: ShadowRoot;
    #geocodingClient: Geocoder | null = null;

    #municipalityInput!: HTMLInputElement;
    #locationNameInput!: HTMLInputElement;
    #numberInput!: HTMLInputElement;
    #crossStreetInput!: HTMLInputElement;
    #coordInput!: CoordInput;
    #detailsTextarea!: HTMLTextAreaElement;
    #approximateCheckbox!: HTMLInputElement;
    #variantBadge!: HTMLSpanElement;
    #locationNameLabel!: HTMLLabelElement;
    #geocodingDropdown!: HTMLUListElement;
    #coordError!: HTMLSpanElement;
    #mutualExclusionError!: HTMLSpanElement;
    #detailsError!: HTMLSpanElement;

    #municipality: Municipality | null = null;
    #geocodingResults: GeocodeResult[] = [];
    #geocodingTimer: ReturnType<typeof setTimeout> | null = null;
    #suppressChange = false;

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
    }

    initialize(geocodingClient: Geocoder): void {
        this.#geocodingClient = geocodingClient;
    }

    connectedCallback(): void {
        const style = document.createElement('style');
        style.textContent = STYLES;
        this.#shadow.appendChild(style);
        this.#buildDom();
        this.#attachListeners();
    }

    get value(): Location | null {
        return this.#computeValue();
    }

    set value(loc: Location | null) {
        this.#applyLocation(loc);
    }

    /** Returns true when every input field is empty — distinguishes an intentional clear from an invalid/incomplete state. */
    get isBlank(): boolean {
        return !this.#municipalityInput?.value.trim() &&
               !this.#locationNameInput?.value.trim() &&
               !this.#numberInput?.value.trim() &&
               !this.#crossStreetInput?.value.trim() &&
               !this.#coordInput?.value.trim() &&
               !this.#detailsTextarea?.value.trim();
    }

    #buildDom(): void {
        const root = document.createElement('div');
        root.className = 'location-entry';

        const header = document.createElement('div');
        header.className = 'location-header';
        const title = document.createElement('span');
        title.className = 'location-title';
        title.textContent = t('location.title');
        this.#variantBadge = document.createElement('span');
        this.#variantBadge.className = 'variant-badge';
        header.append(title, this.#variantBadge);

        // Municipality
        const municipalityWrapper = document.createElement('div');
        municipalityWrapper.className = 'field-wrapper';
        const municipalityLabel = document.createElement('label');
        municipalityLabel.className = 'field-label';
        municipalityLabel.htmlFor = 'location-municipality';
        municipalityLabel.textContent = t('location.municipality');
        this.#municipalityInput = document.createElement('input');
        this.#municipalityInput.type = 'text';
        this.#municipalityInput.id = 'location-municipality';
        this.#municipalityInput.className = 'field-input';
        municipalityWrapper.append(municipalityLabel, this.#municipalityInput);

        // Location name with geocoding dropdown
        const locationNameWrapper = document.createElement('div');
        locationNameWrapper.className = 'field-wrapper geocoding-wrapper';
        this.#locationNameLabel = document.createElement('label');
        this.#locationNameLabel.className = 'field-label';
        this.#locationNameLabel.htmlFor = 'location-name';
        this.#locationNameLabel.textContent = t('location.locationName');
        this.#locationNameInput = document.createElement('input');
        this.#locationNameInput.type = 'text';
        this.#locationNameInput.id = 'location-name';
        this.#locationNameInput.className = 'field-input';
        this.#geocodingDropdown = document.createElement('ul');
        this.#geocodingDropdown.className = 'geocoding-dropdown hidden';
        this.#geocodingDropdown.setAttribute('role', 'listbox');
        locationNameWrapper.append(this.#locationNameLabel, this.#locationNameInput, this.#geocodingDropdown);

        // Number / Cross street row
        const numberCrossRow = document.createElement('div');
        numberCrossRow.className = 'number-cross-row';
        const numberGroup = document.createElement('div');
        numberGroup.className = 'field-group';
        const numberLabel = document.createElement('label');
        numberLabel.className = 'field-label';
        numberLabel.htmlFor = 'location-number';
        numberLabel.textContent = t('location.number');
        this.#numberInput = document.createElement('input');
        this.#numberInput.type = 'text';
        this.#numberInput.id = 'location-number';
        this.#numberInput.className = 'field-input field-input-narrow';
        numberGroup.append(numberLabel, this.#numberInput);
        const orLabel = document.createElement('span');
        orLabel.className = 'or-label';
        orLabel.textContent = t('location.or');
        const crossGroup = document.createElement('div');
        crossGroup.className = 'field-group';
        const crossLabel = document.createElement('label');
        crossLabel.className = 'field-label';
        crossLabel.htmlFor = 'location-cross-street';
        crossLabel.textContent = t('location.crossStreet');
        this.#crossStreetInput = document.createElement('input');
        this.#crossStreetInput.type = 'text';
        this.#crossStreetInput.id = 'location-cross-street';
        this.#crossStreetInput.className = 'field-input';
        crossGroup.append(crossLabel, this.#crossStreetInput);
        numberCrossRow.append(numberGroup, orLabel, crossGroup);

        this.#mutualExclusionError = document.createElement('span');
        this.#mutualExclusionError.className = 'field-error hidden';
        this.#mutualExclusionError.textContent = t('location.error.bothNumberAndCrossStreet');

        // Coordinates
        const coordWrapper = document.createElement('div');
        coordWrapper.className = 'field-wrapper';
        const coordLabel = document.createElement('label');
        coordLabel.className = 'field-label';
        coordLabel.htmlFor = 'location-coords';
        coordLabel.textContent = t('lookup.coordsLabel');
        this.#coordInput = document.createElement(CoordInput.TAG) as CoordInput;
        this.#coordInput.id = 'location-coords';
        coordWrapper.append(coordLabel, this.#coordInput);

        this.#coordError = document.createElement('span');
        this.#coordError.className = 'field-error hidden';

        // Details
        const detailsWrapper = document.createElement('div');
        detailsWrapper.className = 'field-wrapper';
        const detailsLabel = document.createElement('label');
        detailsLabel.className = 'field-label';
        detailsLabel.htmlFor = 'location-details';
        detailsLabel.textContent = t('location.details');
        this.#detailsTextarea = document.createElement('textarea');
        this.#detailsTextarea.id = 'location-details';
        this.#detailsTextarea.className = 'field-textarea';
        this.#detailsTextarea.rows = 2;
        detailsWrapper.append(detailsLabel, this.#detailsTextarea);

        this.#detailsError = document.createElement('span');
        this.#detailsError.className = 'field-error hidden';
        this.#detailsError.textContent = t('location.error.detailsRequired');

        // Approximate checkbox
        const approxRow = document.createElement('div');
        approxRow.className = 'approx-row';
        this.#approximateCheckbox = document.createElement('input');
        this.#approximateCheckbox.type = 'checkbox';
        this.#approximateCheckbox.id = 'location-approximate';
        const approxLabel = document.createElement('label');
        approxLabel.htmlFor = 'location-approximate';
        approxLabel.className = 'approx-label';
        approxLabel.textContent = t('location.approximate');
        approxRow.append(this.#approximateCheckbox, approxLabel);

        root.append(
            header,
            municipalityWrapper,
            locationNameWrapper,
            numberCrossRow,
            this.#mutualExclusionError,
            coordWrapper,
            this.#coordError,
            detailsWrapper,
            this.#detailsError,
            approxRow,
        );
        this.#shadow.appendChild(root);
        this.#updateVariantBadge();
    }

    #attachListeners(): void {
        this.#municipalityInput.addEventListener('input', () => {
            this.#municipality = null;
            this.#onAnyFieldChange();
        });

        this.#locationNameInput.addEventListener('input', () => {
            this.#onLocationNameChange();
        });

        this.#numberInput.addEventListener('input', () => {
            this.#updateMutualExclusion();
            this.#onAnyFieldChange();
        });

        this.#crossStreetInput.addEventListener('input', () => {
            this.#updateMutualExclusion();
            this.#onAnyFieldChange();
        });

        this.#coordInput.addEventListener('input', () => {
            this.#validateCoords();
            this.#onAnyFieldChange();
        });

        this.#detailsTextarea.addEventListener('input', () => {
            this.#validateDetails();
            this.#onAnyFieldChange();
        });

        this.#approximateCheckbox.addEventListener('change', () => {
            this.#locationNameLabel.textContent = this.#approximateCheckbox.checked
                ? t('location.referencePlace')
                : t('location.locationName');
            this.#updateVariantBadge();
            this.#onAnyFieldChange();
        });

        document.addEventListener('click', (e) => {
            if (!this.#shadow.contains(e.target as Node)) {
                this.#hideGeocodingDropdown();
            }
        });
    }

    #onLocationNameChange(): void {
        const q = this.#locationNameInput.value.trim();
        if (this.#geocodingTimer !== null) clearTimeout(this.#geocodingTimer);

        if (q.length >= 3 && this.#geocodingClient) {
            this.#geocodingTimer = setTimeout(() => {
                void this.#runGeocoding(q);
            }, 300);
        } else {
            this.#hideGeocodingDropdown();
        }
        this.#onAnyFieldChange();
    }

    async #runGeocoding(q: string): Promise<void> {
        if (!this.#geocodingClient) return;
        try {
            const response = await this.#geocodingClient.search(q, 8);
            this.#geocodingResults = response.results;
            this.#showGeocodingDropdown();
        } catch {
            this.#hideGeocodingDropdown();
        }
    }

    #showGeocodingDropdown(): void {
        this.#geocodingDropdown.innerHTML = '';
        if (this.#geocodingResults.length === 0) {
            this.#geocodingDropdown.classList.add('hidden');
            return;
        }
        for (const result of this.#geocodingResults) {
            const li = document.createElement('li');
            li.className = 'geocoding-item';
            li.setAttribute('role', 'option');
            li.textContent = this.#formatGeocodingResult(result);
            li.addEventListener('mousedown', (e) => {
                e.preventDefault(); // prevent blur on the input
                this.#applyGeocodingResult(result);
                this.#hideGeocodingDropdown();
            });
            this.#geocodingDropdown.appendChild(li);
        }
        this.#geocodingDropdown.classList.remove('hidden');
    }

    #formatGeocodingResult(result: GeocodeResult): string {
        const parts: string[] = [];
        const name = result.name
            ? (result.name['fi'] ?? Object.values(result.name)[0] ?? '')
            : '';
        if (name) parts.push(name);
        if (result.number) parts.push(result.number);
        const muniName = result.municipality.name['fi'] ?? Object.values(result.municipality.name)[0] ?? '';
        if (muniName) parts.push(muniName);
        return parts.join(', ');
    }

    #applyGeocodingResult(result: GeocodeResult): void {
        this.#suppressChange = true;

        this.#municipality = { code: result.municipality.code, name: result.municipality.name };
        const muniName = result.municipality.name['fi'] ?? Object.values(result.municipality.name)[0] ?? '';
        this.#municipalityInput.value = muniName;

        if (result.type === 'address' && result.name) {
            const name = result.name['fi'] ?? Object.values(result.name)[0] ?? '';
            this.#locationNameInput.value = name;
            if (result.number) {
                this.#numberInput.value = result.number;
                this.#crossStreetInput.value = '';
            }
        } else if (result.type === 'intersection' && result.roadA && result.roadB) {
            const roadA = result.roadA['fi'] ?? Object.values(result.roadA)[0] ?? '';
            const roadB = result.roadB['fi'] ?? Object.values(result.roadB)[0] ?? '';
            this.#locationNameInput.value = roadA;
            this.#crossStreetInput.value = roadB;
            this.#numberInput.value = '';
        } else if (result.type === 'place' && result.name) {
            const name = result.name['fi'] ?? Object.values(result.name)[0] ?? '';
            this.#locationNameInput.value = name;
        }

        if (result.coordinates) {
            const { latitude: lat, longitude: lon } = result.coordinates;
            this.#coordInput.value = formatCoordinates(lat, lon, this.#coordInput.format);
        }

        this.#suppressChange = false;
        this.#updateVariantBadge();
        this.#updateMutualExclusion();
        this.#onAnyFieldChange();
    }

    #hideGeocodingDropdown(): void {
        this.#geocodingDropdown.classList.add('hidden');
        this.#geocodingResults = [];
    }

    #updateMutualExclusion(): void {
        const hasNumber = this.#numberInput.value.trim() !== '';
        const hasCross = this.#crossStreetInput.value.trim() !== '';
        const conflict = hasNumber && hasCross;

        this.#mutualExclusionError.classList.toggle('hidden', !conflict);
        this.#numberInput.classList.toggle('deemphasized', hasCross && !hasNumber);
        this.#crossStreetInput.classList.toggle('deemphasized', hasNumber && !hasCross);
    }

    #validateCoords(): void {
        const raw = this.#coordInput.value.trim();
        if (!raw) {
            this.#coordError.classList.add('hidden');
            return;
        }
        const parsed = parseCoordinates(raw);
        if (!parsed) {
            this.#coordError.textContent = t('lookup.coordsInvalid');
            this.#coordError.classList.remove('hidden');
        } else if (!validateFinlandBounds(parsed.lat, parsed.lon)) {
            this.#coordError.textContent = t('lookup.coordsOutOfBounds');
            this.#coordError.classList.remove('hidden');
        } else {
            this.#coordError.classList.add('hidden');
        }
    }

    #validateDetails(): void {
        const variant = this.#resolveVariant();
        const empty = this.#detailsTextarea.value.trim() === '';
        if (variant === 'relative_location' && empty) {
            this.#detailsError.classList.remove('hidden');
        } else {
            this.#detailsError.classList.add('hidden');
        }
    }

    #resolveVariant(): LocationVariant {
        const hasNumber = this.#numberInput.value.trim() !== '';
        const hasCross = this.#crossStreetInput.value.trim() !== '';
        const approx = this.#approximateCheckbox.checked;

        if (hasNumber && !hasCross) return 'exact_address';
        if (hasCross && !hasNumber) return 'road_intersection';
        if (approx) return 'relative_location';
        return 'named_place';
    }

    #updateVariantBadge(): void {
        if (!this.#variantBadge) return;
        const variant = this.#resolveVariant();
        const labels: Record<LocationVariant, string> = {
            exact_address: t('location.variant.exactAddress'),
            road_intersection: t('location.variant.roadIntersection'),
            named_place: t('location.variant.namedPlace'),
            relative_location: t('location.variant.relativeLocation'),
        };
        this.#variantBadge.textContent = labels[variant];
    }

    #onAnyFieldChange(): void {
        if (this.#suppressChange) return;
        this.#updateVariantBadge();
        this.#validateDetails();
        this.dispatchEvent(new LocationChangedEvent(this.#computeValue()));
    }

    #computeValue(): Location | null {
        const variant = this.#resolveVariant();
        const hasNumber = this.#numberInput.value.trim() !== '';
        const hasCross = this.#crossStreetInput.value.trim() !== '';
        if (hasNumber && hasCross) return null; // conflicting input

        const muniName = this.#municipalityInput.value.trim();
        const municipality: Municipality = this.#municipality ?? { code: null, name: { fi: muniName } };
        const coordinates = this.#parseCoordinates();
        const details = this.#detailsTextarea.value.trim() || null;
        const locationName = this.#locationNameInput.value.trim();

        if (variant === 'exact_address') {
            if (!locationName) return null;
            const loc: ExactAddressLocation = {
                type: 'exact_address',
                municipality,
                addressName: { fi: locationName },
                addressNumber: this.#numberInput.value.trim() || null,
                coordinates,
                additionalDetails: details,
            };
            return loc;
        }

        if (variant === 'road_intersection') {
            const roadB = this.#crossStreetInput.value.trim();
            if (!locationName || !roadB) return null;
            const loc: RoadIntersectionLocation = {
                type: 'road_intersection',
                municipality,
                roadNameA: { fi: locationName },
                roadNameB: { fi: roadB },
                coordinates,
                additionalDetails: details,
            };
            return loc;
        }

        if (variant === 'named_place') {
            if (!locationName) return null;
            const loc: NamedPlaceLocation = {
                type: 'named_place',
                municipality,
                name: { fi: locationName },
                coordinates,
                additionalDetails: details,
            };
            return loc;
        }

        // relative_location
        const detailsValue = this.#detailsTextarea.value.trim();
        if (!locationName || !detailsValue) return null;
        const loc: RelativeLocation = {
            type: 'relative_location',
            municipality,
            referencePlace: { fi: locationName },
            additionalDetails: detailsValue,
            coordinates,
        };
        return loc;
    }

    #parseCoordinates(): Coordinates | null {
        const raw = this.#coordInput?.value?.trim() ?? '';
        if (!raw) return null;
        const parsed = parseCoordinates(raw);
        if (!parsed || !validateFinlandBounds(parsed.lat, parsed.lon)) return null;
        return { latitude: parsed.lat, longitude: parsed.lon };
    }

    #applyLocation(loc: Location | null): void {
        this.#suppressChange = true;

        if (!loc) {
            this.#municipalityInput.value = '';
            this.#locationNameInput.value = '';
            this.#numberInput.value = '';
            this.#crossStreetInput.value = '';
            this.#coordInput?.clear();
            this.#detailsTextarea.value = '';
            this.#approximateCheckbox.checked = false;
            this.#municipality = null;
            this.#locationNameLabel.textContent = t('location.locationName');
            this.#suppressChange = false;
            this.#updateVariantBadge();
            return;
        }

        this.#municipality = loc.municipality;
        const muniName = loc.municipality.name['fi'] ?? Object.values(loc.municipality.name)[0] ?? '';
        this.#municipalityInput.value = muniName;
        this.#detailsTextarea.value = loc.additionalDetails ?? '';
        this.#numberInput.value = '';
        this.#crossStreetInput.value = '';
        this.#approximateCheckbox.checked = false;
        this.#locationNameLabel.textContent = t('location.locationName');

        if (loc.coordinates) {
            this.#coordInput.value = formatCoordinates(
                loc.coordinates.latitude,
                loc.coordinates.longitude,
                this.#coordInput.format,
            );
        } else {
            this.#coordInput?.clear();
        }

        switch (loc.type) {
            case 'exact_address':
                this.#locationNameInput.value = loc.addressName['fi'] ?? Object.values(loc.addressName)[0] ?? '';
                this.#numberInput.value = loc.addressNumber ?? '';
                break;
            case 'road_intersection':
                this.#locationNameInput.value = loc.roadNameA['fi'] ?? Object.values(loc.roadNameA)[0] ?? '';
                this.#crossStreetInput.value = loc.roadNameB['fi'] ?? Object.values(loc.roadNameB)[0] ?? '';
                break;
            case 'named_place':
                this.#locationNameInput.value = loc.name['fi'] ?? Object.values(loc.name)[0] ?? '';
                break;
            case 'relative_location':
                this.#locationNameInput.value = loc.referencePlace['fi'] ?? Object.values(loc.referencePlace)[0] ?? '';
                this.#approximateCheckbox.checked = true;
                this.#locationNameLabel.textContent = t('location.referencePlace');
                break;
        }

        this.#suppressChange = false;
        this.#updateVariantBadge();
        this.#updateMutualExclusion();
    }
}
