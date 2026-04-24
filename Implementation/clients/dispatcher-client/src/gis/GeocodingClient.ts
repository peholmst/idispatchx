import type { HttpClient } from '../http/HttpClient.ts';

export interface GeocodeResult {
    type: 'address' | 'place' | 'intersection';
    name?: Record<string, string>;
    number?: string;
    roadA?: Record<string, string>;
    roadB?: Record<string, string>;
    municipality: {
        code: string;
        name: Record<string, string>;
    };
    coordinates: {
        latitude: number;
        longitude: number;
    };
}

export interface GeocodeResponse {
    results: GeocodeResult[];
    query: string;
    resultCount: number;
}

export class GeocodingTimeoutError extends Error {
    constructor() {
        super('GIS Server request timed out');
        this.name = 'GeocodingTimeoutError';
    }
}

export class GeocodingClient {
    readonly #baseUrl: string;
    readonly #http: HttpClient;
    readonly #timeoutMs: number;

    constructor(baseUrl: string, http: HttpClient, timeoutMs = 10_000) {
        this.#baseUrl = baseUrl;
        this.#http = http;
        this.#timeoutMs = timeoutMs;
    }

    async search(q: string, limit?: number): Promise<GeocodeResponse> {
        const url = new URL(`${this.#baseUrl}/api/v1/geocode/search`);
        url.searchParams.set('q', q);
        if (limit !== undefined) {
            url.searchParams.set('limit', String(limit));
        }

        const timeoutErr = new GeocodingTimeoutError();
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(timeoutErr), this.#timeoutMs);

        try {
            return await this.#http.get<GeocodeResponse>(url.toString(), {
                signal: controller.signal,
            });
        } catch (e) {
            // When the internal timeout fires, abort() throws our custom error.
            // Re-throw it distinctly so callers can distinguish timeout from cancel.
            if ((e instanceof Error) && e.name === 'AbortError' &&
                controller.signal.reason instanceof GeocodingTimeoutError) {
                throw controller.signal.reason;
            }
            throw e;
        } finally {
            clearTimeout(timeoutId);
        }
    }
}
