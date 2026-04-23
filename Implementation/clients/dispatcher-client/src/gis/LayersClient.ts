import type { HttpClient } from '../http/HttpClient.ts';

export interface LayerInfo {
    id: string;
    title: string;
}

interface LayersResponse {
    layers: LayerInfo[];
}

export class LayersClient {
    readonly #baseUrl: string;
    readonly #http: HttpClient;

    constructor(baseUrl: string, http: HttpClient) {
        this.#baseUrl = baseUrl;
        this.#http = http;
    }

    async getLayers(): Promise<LayerInfo[]> {
        const response = await this.#http.get<LayersResponse>(`${this.#baseUrl}/api/v1/layers`);
        return response.layers;
    }
}
