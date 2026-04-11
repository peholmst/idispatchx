// Base HTTP client for the Dispatcher Client.
// Attaches Authorization: Bearer headers to every request and detects 401
// responses so that forced session termination (back-channel logout or admin
// revocation) can be handled gracefully.

import type { AuthState } from '../auth/AuthState.ts';

export class HttpError extends Error {
    constructor(
        readonly status: number,
        readonly body: string,
        readonly url: string,
    ) {
        super(`HTTP ${status} from ${url}: ${body}`);
    }
}

export class UnauthorizedError extends Error {
    constructor() {
        super('Unauthorized — session has been revoked or token has expired');
    }
}

export class HttpClient {
    constructor(
        private readonly authState: AuthState,
        private readonly onUnauthorized: () => void,
    ) {}

    async get<T>(url: string, options?: RequestInit): Promise<T> {
        return this.#request<T>(url, { ...options, method: 'GET' });
    }

    async post<T>(url: string, body: unknown, options?: RequestInit): Promise<T> {
        return this.#request<T>(url, {
            ...options,
            method: 'POST',
            headers: this.#mergeHeaders(options?.headers, { 'Content-Type': 'application/json' }),
            body: JSON.stringify(body),
        });
    }

    async put<T>(url: string, body: unknown, options?: RequestInit): Promise<T> {
        return this.#request<T>(url, {
            ...options,
            method: 'PUT',
            headers: this.#mergeHeaders(options?.headers, { 'Content-Type': 'application/json' }),
            body: JSON.stringify(body),
        });
    }

    async patch<T>(url: string, body: unknown, options?: RequestInit): Promise<T> {
        return this.#request<T>(url, {
            ...options,
            method: 'PATCH',
            headers: this.#mergeHeaders(options?.headers, { 'Content-Type': 'application/json' }),
            body: JSON.stringify(body),
        });
    }

    async delete(url: string, options?: RequestInit): Promise<void> {
        await this.#request<void>(url, { ...options, method: 'DELETE' });
    }

    async #request<T>(url: string, init: RequestInit): Promise<T> {
        const headers = new Headers(init.headers);

        const token = this.authState.getAccessToken();
        if (token !== null) {
            headers.set('Authorization', `Bearer ${token}`);
        }

        const response = await fetch(url, { ...init, headers });

        if (response.status === 401) {
            this.onUnauthorized();
            throw new UnauthorizedError();
        }

        if (!response.ok) {
            const body = await response.text().catch(() => '');
            throw new HttpError(response.status, body, url);
        }

        if (response.status === 204 || response.headers.get('Content-Length') === '0') {
            return undefined as T;
        }

        return response.json() as Promise<T>;
    }

    #mergeHeaders(
        existing: HeadersInit | undefined,
        additions: Record<string, string>,
    ): Headers {
        const headers = new Headers(existing);
        for (const [key, value] of Object.entries(additions)) {
            if (!headers.has(key)) {
                headers.set(key, value);
            }
        }
        return headers;
    }
}
