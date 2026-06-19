import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CadRestClient, CadApiError } from './CadRestClient.ts';
import { HttpError } from '../http/HttpClient.ts';
import type { CallSummary, IncidentSummary } from './types.ts';

function makeHttp() {
    return {
        get: vi.fn(),
        post: vi.fn(),
        patch: vi.fn(),
        put: vi.fn(),
        delete: vi.fn(),
    };
}

const BASE = 'http://cad.local';

describe('CadRestClient', () => {
    let http: ReturnType<typeof makeHttp>;
    let client: CadRestClient;

    beforeEach(() => {
        http = makeHttp();
        client = new CadRestClient(BASE, http as never);
    });

    describe('createCall', () => {
        it('posts to /api/v1/calls with X-Command-Id header', async () => {
            const result = { callId: 'abc', state: 'active', receivingDispatcher: 'u1', callStarted: '2026-01-01T00:00:00Z' };
            http.post.mockResolvedValue(result);

            const params = { callerName: 'Test' };
            const res = await client.createCall(params);

            expect(res.callId).toBe('abc');
            expect(http.post).toHaveBeenCalledOnce();
            const [url, body, opts] = http.post.mock.calls[0] as [string, unknown, { headers: Record<string, string> }];
            expect(url).toBe(`${BASE}/api/v1/calls`);
            expect(body).toEqual(params);
            expect(opts.headers['X-Command-Id']).toMatch(/^[0-9a-f-]{36}$/);
        });

        it('generates a fresh X-Command-Id on each call', async () => {
            http.post.mockResolvedValue({ callId: 'x' });
            await client.createCall();
            await client.createCall();
            const id1 = (http.post.mock.calls[0] as [string, unknown, { headers: Record<string, string> }])[2].headers['X-Command-Id'];
            const id2 = (http.post.mock.calls[1] as [string, unknown, { headers: Record<string, string> }])[2].headers['X-Command-Id'];
            expect(id1).not.toBe(id2);
        });
    });

    describe('updateCall', () => {
        it('patches /api/v1/calls/{callId}', async () => {
            http.patch.mockResolvedValue(undefined);
            await client.updateCall('call1', { callerName: 'Updated' });

            const [url, body, opts] = http.patch.mock.calls[0] as [string, unknown, { headers: Record<string, string> }];
            expect(url).toBe(`${BASE}/api/v1/calls/call1`);
            expect(body).toEqual({ callerName: 'Updated' });
            expect(opts.headers['X-Command-Id']).toBeTruthy();
        });
    });

    describe('endCall', () => {
        it('posts to /api/v1/calls/{callId}/end', async () => {
            http.post.mockResolvedValue(undefined);
            await client.endCall('call1', { outcome: 'caller_advised' });

            const [url, body] = http.post.mock.calls[0] as [string, unknown];
            expect(url).toBe(`${BASE}/api/v1/calls/call1/end`);
            expect(body).toEqual({ outcome: 'caller_advised' });
        });
    });

    describe('attachCallToIncident', () => {
        it('posts incidentId to attach endpoint', async () => {
            http.post.mockResolvedValue(undefined);
            await client.attachCallToIncident('call1', 'incident1');

            const [url, body] = http.post.mock.calls[0] as [string, unknown];
            expect(url).toBe(`${BASE}/api/v1/calls/call1/attach-to-incident`);
            expect(body).toEqual({ incidentId: 'incident1' });
        });
    });

    describe('detachCallFromIncident', () => {
        it('posts empty body to detach endpoint', async () => {
            http.post.mockResolvedValue(undefined);
            await client.detachCallFromIncident('call1');

            const [url, body] = http.post.mock.calls[0] as [string, unknown];
            expect(url).toBe(`${BASE}/api/v1/calls/call1/detach-from-incident`);
            expect(body).toEqual({});
        });
    });

    describe('listActiveCalls', () => {
        it('returns the calls array', async () => {
            const call: CallSummary = {
                callId: 'c1', state: 'active', receivingDispatcher: 'u1',
                callStarted: '2026-01-01T00:00:00Z', callerName: null, callerPhoneNumber: null,
                location: null, description: null, outcome: null, outcomeRationale: null, incidentId: null,
            };
            http.get.mockResolvedValue({ calls: [call] });

            const result = await client.listActiveCalls();
            expect(result).toHaveLength(1);
            expect(result[0].callId).toBe('c1');
            expect(http.get).toHaveBeenCalledWith(`${BASE}/api/v1/calls`);
        });
    });

    describe('listIncidents', () => {
        it('excludes ended by default', async () => {
            http.get.mockResolvedValue({ incidents: [] });
            await client.listIncidents();

            const [url] = http.get.mock.calls[0] as [string];
            expect(url).not.toContain('includeEnded');
        });

        it('includes ended when requested', async () => {
            http.get.mockResolvedValue({ incidents: [] });
            await client.listIncidents(true);

            const [url] = http.get.mock.calls[0] as [string];
            expect(url).toContain('includeEnded=true');
        });

        it('returns the incidents array', async () => {
            const incident: IncidentSummary = {
                incidentId: 'i1', state: 'active', incidentCreated: '2026-01-01T00:00:00Z',
                incidentEnded: null, incidentType: 'A31', incidentPriority: 'A',
                location: null, description: null, callIds: [],
            };
            http.get.mockResolvedValue({ incidents: [incident] });
            const result = await client.listIncidents();
            expect(result[0].incidentId).toBe('i1');
        });
    });

    describe('createIncidentFromCall', () => {
        it('posts to /api/v1/incidents with sourceCallId', async () => {
            http.post.mockResolvedValue({ incidentId: 'i1' });
            const result = await client.createIncidentFromCall({ sourceCallId: 'call1' });

            expect(result.incidentId).toBe('i1');
            const [url, body] = http.post.mock.calls[0] as [string, unknown];
            expect(url).toBe(`${BASE}/api/v1/incidents`);
            expect(body).toEqual({ sourceCallId: 'call1' });
        });
    });

    describe('error handling', () => {
        it('wraps HttpError into CadApiError with parsed code', async () => {
            const errorBody = JSON.stringify({ error: { code: 'RESOURCE_NOT_FOUND', message: 'not found' } });
            http.get.mockRejectedValue(new HttpError(404, errorBody, `${BASE}/api/v1/calls`));

            await expect(client.listActiveCalls()).rejects.toBeInstanceOf(CadApiError);
            try {
                await client.listActiveCalls();
            } catch (err) {
                expect(err).toBeInstanceOf(CadApiError);
                expect((err as CadApiError).code).toBe('RESOURCE_NOT_FOUND');
                expect((err as CadApiError).status).toBe(404);
            }
        });

        it('uses UNKNOWN_ERROR when body is not parseable JSON', async () => {
            http.get.mockRejectedValue(new HttpError(500, 'plain text error', `${BASE}/api/v1/calls`));

            try {
                await client.listActiveCalls();
            } catch (err) {
                expect(err).toBeInstanceOf(CadApiError);
                expect((err as CadApiError).code).toBe('UNKNOWN_ERROR');
            }
        });

        it('rethrows non-HttpError errors unchanged', async () => {
            const networkError = new TypeError('Failed to fetch');
            http.get.mockRejectedValue(networkError);

            await expect(client.listActiveCalls()).rejects.toBe(networkError);
        });
    });
});
