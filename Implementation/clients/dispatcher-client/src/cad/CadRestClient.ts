import type { HttpClient } from '../http/HttpClient.ts';
import { HttpError } from '../http/HttpClient.ts';
import type {
    Call,
    CallOutcome,
    CallSummary,
    Incident,
    IncidentPriority,
    IncidentSummary,
    Location,
} from './types.ts';

export class CadApiError extends Error {
    constructor(
        readonly code: string,
        readonly status: number,
        message: string,
    ) {
        super(message);
        this.name = 'CadApiError';
    }
}

export interface CreateCallParams {
    callerName?: string;
    callerPhoneNumber?: string;
    location?: Location;
    description?: string;
}

export interface UpdateCallParams {
    callerName?: string | null;
    callerPhoneNumber?: string | null;
    location?: Location | null;
    description?: string | null;
    outcome?: CallOutcome;
    outcomeRationale?: string;
}

export interface EndCallParams {
    outcome?: CallOutcome;
    outcomeRationale?: string;
}

export interface CreateIncidentParams {
    sourceCallId?: string;
    incidentType?: string;
    incidentPriority?: IncidentPriority;
    location?: Location;
    description?: string;
}

export class CadRestClient {
    readonly #baseUrl: string;
    readonly #http: HttpClient;

    constructor(baseUrl: string, http: HttpClient) {
        this.#baseUrl = baseUrl;
        this.#http = http;
    }

    async createCall(params: CreateCallParams = {}): Promise<{ callId: string }> {
        return this.#execute(() =>
            this.#http.post<{ callId: string }>(
                `${this.#baseUrl}/api/v1/calls`,
                params,
                { headers: { 'X-Command-Id': crypto.randomUUID() } },
            )
        );
    }

    async updateCall(callId: string, params: UpdateCallParams): Promise<void> {
        return this.#execute(() =>
            this.#http.patch<void>(
                `${this.#baseUrl}/api/v1/calls/${callId}`,
                params,
                { headers: { 'X-Command-Id': crypto.randomUUID() } },
            )
        );
    }

    async endCall(callId: string, params: EndCallParams = {}): Promise<void> {
        return this.#execute(() =>
            this.#http.post<void>(
                `${this.#baseUrl}/api/v1/calls/${callId}/end`,
                params,
                { headers: { 'X-Command-Id': crypto.randomUUID() } },
            )
        );
    }

    async attachCallToIncident(callId: string, incidentId: string): Promise<void> {
        return this.#execute(() =>
            this.#http.post<void>(
                `${this.#baseUrl}/api/v1/calls/${callId}/attach-to-incident`,
                { incidentId },
                { headers: { 'X-Command-Id': crypto.randomUUID() } },
            )
        );
    }

    async detachCallFromIncident(callId: string): Promise<void> {
        return this.#execute(() =>
            this.#http.post<void>(
                `${this.#baseUrl}/api/v1/calls/${callId}/detach-from-incident`,
                {},
                { headers: { 'X-Command-Id': crypto.randomUUID() } },
            )
        );
    }

    async listActiveCalls(): Promise<CallSummary[]> {
        const response = await this.#execute(() =>
            this.#http.get<{ calls: CallSummary[] }>(`${this.#baseUrl}/api/v1/calls`)
        );
        return response.calls;
    }

    async getCall(callId: string): Promise<Call> {
        return this.#execute(() =>
            this.#http.get<Call>(`${this.#baseUrl}/api/v1/calls/${callId}`)
        );
    }

    async createIncidentFromCall(params: CreateIncidentParams): Promise<{ incidentId: string }> {
        return this.#execute(() =>
            this.#http.post<{ incidentId: string }>(
                `${this.#baseUrl}/api/v1/incidents`,
                params,
                { headers: { 'X-Command-Id': crypto.randomUUID() } },
            )
        );
    }

    async listIncidents(includeEnded = false): Promise<IncidentSummary[]> {
        const url = new URL(`${this.#baseUrl}/api/v1/incidents`);
        if (includeEnded) url.searchParams.set('includeEnded', 'true');
        const response = await this.#execute(() =>
            this.#http.get<{ incidents: IncidentSummary[] }>(url.toString())
        );
        return response.incidents;
    }

    async getIncident(incidentId: string): Promise<Incident> {
        return this.#execute(() =>
            this.#http.get<Incident>(`${this.#baseUrl}/api/v1/incidents/${incidentId}`)
        );
    }

    async #execute<T>(fn: () => Promise<T>): Promise<T> {
        try {
            return await fn();
        } catch (err) {
            if (err instanceof HttpError) {
                const code = this.#extractErrorCode(err.body);
                throw new CadApiError(code, err.status, err.message);
            }
            throw err;
        }
    }

    #extractErrorCode(body: string): string {
        try {
            const parsed = JSON.parse(body) as { error?: { code?: string } };
            return parsed.error?.code ?? 'UNKNOWN_ERROR';
        } catch {
            return 'UNKNOWN_ERROR';
        }
    }
}
