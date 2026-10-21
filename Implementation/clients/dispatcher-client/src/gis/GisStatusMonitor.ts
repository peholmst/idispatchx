import type { GeocodingClient, GeocodeResponse, Geocoder } from './GeocodingClient.ts';

type AvailabilityListener = (available: boolean) => void;

/**
 * Wraps {@link GeocodingClient} and tracks consecutive failures/successes to
 * detect GIS server availability transitions.
 *
 * After `failureThreshold` consecutive failures the GIS server is considered
 * unavailable. After `successThreshold` consecutive successes it is considered
 * available again. Both thresholds default to 3.
 *
 * Listeners registered via {@link onAvailabilityChanged} are called on each
 * transition.
 *
 * Use this as a drop-in replacement for {@link GeocodingClient} — it exposes
 * the same `search()` method and simply delegates to the wrapped client.
 */
export class GisStatusMonitor implements Geocoder {
    readonly #inner: GeocodingClient;
    readonly #failureThreshold: number;
    readonly #successThreshold: number;
    #consecutiveFailures = 0;
    #consecutiveSuccesses = 0;
    #available = true;
    readonly #listeners: AvailabilityListener[] = [];

    constructor(inner: GeocodingClient, failureThreshold = 3, successThreshold = 1) {
        this.#inner = inner;
        this.#failureThreshold = failureThreshold;
        this.#successThreshold = successThreshold;
    }

    async search(q: string, limit?: number): Promise<GeocodeResponse> {
        try {
            const result = await this.#inner.search(q, limit);
            this.#recordSuccess();
            return result;
        } catch (err) {
            this.#recordFailure();
            throw err;
        }
    }

    isAvailable(): boolean {
        return this.#available;
    }

    onAvailabilityChanged(listener: AvailabilityListener): void {
        this.#listeners.push(listener);
    }

    #recordSuccess(): void {
        this.#consecutiveFailures = 0;
        this.#consecutiveSuccesses++;
        if (!this.#available && this.#consecutiveSuccesses >= this.#successThreshold) {
            this.#available = true;
            this.#notify(true);
        }
    }

    #recordFailure(): void {
        this.#consecutiveSuccesses = 0;
        this.#consecutiveFailures++;
        if (this.#available && this.#consecutiveFailures >= this.#failureThreshold) {
            this.#available = false;
            this.#notify(false);
        }
    }

    #notify(available: boolean): void {
        for (const l of this.#listeners) {
            try { l(available); } catch (err) {
                console.error('[GisStatusMonitor] Listener threw:', err);
            }
        }
    }
}
