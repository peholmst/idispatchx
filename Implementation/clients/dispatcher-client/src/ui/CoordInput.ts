import STYLES from './CoordInput.css?inline';
import { t } from '../i18n/index.ts';
import { parseCoordinates, formatCoordinates } from '../geo/coordinates.ts';

const COORD_FORMAT_KEY = 'idispatch:coordFormat';

/**
 * Coordinate input field with a DD/DDM/DMS format toggle.
 *
 * The selected format is persisted to localStorage. Native input and keydown
 * events from the inner <input> bubble out through the shadow boundary via
 * event retargeting, so consumers can attach listeners on this host element.
 */
export class CoordInput extends HTMLElement {
    static readonly TAG = 'idispatch-coord-input' as const;

    #shadow: ShadowRoot;
    #format: 'DD' | 'DDM' | 'DMS';
    #input: HTMLInputElement | null = null;
    #formatBtns: HTMLButtonElement[] = [];

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
        const stored = localStorage.getItem(COORD_FORMAT_KEY);
        this.#format = (stored === 'DD' || stored === 'DDM' || stored === 'DMS') ? stored : 'DDM';
    }

    connectedCallback(): void {
        const style = document.createElement('style');
        style.textContent = STYLES;

        const input = document.createElement('input');
        input.type = 'text';
        input.className = 'input';
        input.placeholder = this.#placeholder();
        this.#input = input;

        const formatGroup = document.createElement('div');
        formatGroup.className = 'format-group';
        this.#formatBtns = (['DD', 'DDM', 'DMS'] as const).map(fmt => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'format-btn';
            if (fmt === this.#format) btn.classList.add('active');
            btn.textContent = fmt;
            btn.dataset.format = fmt;
            btn.addEventListener('click', () => this.#setFormat(fmt));
            formatGroup.appendChild(btn);
            return btn;
        });

        this.#shadow.append(style, input, formatGroup);
    }

    /** The current text value of the coordinate input. */
    get value(): string {
        return this.#input?.value ?? '';
    }

    set value(v: string) {
        if (this.#input) this.#input.value = v;
    }

    /** The currently selected coordinate format. */
    get format(): 'DD' | 'DDM' | 'DMS' {
        return this.#format;
    }

    /** Toggles the error border on the input. */
    setError(hasError: boolean): void {
        this.#input?.classList.toggle('error', hasError);
    }

    /** Clears the value and removes error styling. */
    clear(): void {
        if (this.#input) this.#input.value = '';
        this.setError(false);
    }

    #placeholder(): string {
        if (this.#format === 'DD') return t('lookup.coordsPlaceholder.DD');
        if (this.#format === 'DMS') return t('lookup.coordsPlaceholder.DMS');
        return t('lookup.coordsPlaceholder.DDM');
    }

    #setFormat(fmt: 'DD' | 'DDM' | 'DMS'): void {
        const raw = this.#input?.value.trim() ?? '';
        if (raw) {
            const parsed = parseCoordinates(raw);
            if (parsed && this.#input) {
                this.#input.value = formatCoordinates(parsed.lat, parsed.lon, fmt);
            }
        }
        this.#format = fmt;
        localStorage.setItem(COORD_FORMAT_KEY, fmt);
        if (this.#input) this.#input.placeholder = this.#placeholder();
        for (const btn of this.#formatBtns) {
            btn.classList.toggle('active', btn.dataset.format === fmt);
        }
    }
}
