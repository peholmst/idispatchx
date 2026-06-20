// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { DispatcherWebSocketClient } from './DispatcherWebSocketClient.ts';

// Mock WebSocket
class MockWebSocket {
    static instances: MockWebSocket[] = [];

    url: string;
    onopen: (() => void) | null = null;
    onmessage: ((e: { data: string }) => void) | null = null;
    onerror: (() => void) | null = null;
    onclose: (() => void) | null = null;
    readyState = 0; // CONNECTING

    constructor(url: string) {
        this.url = url;
        MockWebSocket.instances.push(this);
    }

    send(_data: string) {}
    close() {
        this.readyState = 3; // CLOSED
        this.onclose?.();
    }

    // Test helpers
    simulateOpen() {
        this.readyState = 1;
        this.onopen?.();
    }
    simulateMessage(data: unknown) {
        this.onmessage?.({ data: JSON.stringify(data) });
    }
    simulateClose() {
        this.readyState = 3;
        this.onclose?.();
    }
}

function makeAuthState(token = 'test-token') {
    return { getAccessToken: () => token } as never;
}

const BASE = 'http://cad.local';

describe('DispatcherWebSocketClient', () => {
    let origWebSocket: typeof WebSocket;

    beforeEach(() => {
        MockWebSocket.instances = [];
        origWebSocket = window.WebSocket;
        window.WebSocket = MockWebSocket as never;
        vi.useFakeTimers();
    });

    afterEach(() => {
        window.WebSocket = origWebSocket;
        vi.useRealTimers();
    });

    it('includes JWT token in the connection URL', () => {
        const client = new DispatcherWebSocketClient(BASE, makeAuthState('my-token'));
        client.connect();

        expect(MockWebSocket.instances).toHaveLength(1);
        expect(MockWebSocket.instances[0].url).toContain('token=my-token');
        expect(MockWebSocket.instances[0].url).toContain('ws://cad.local/api/v1/ws/dispatcher');
        client.disconnect();
    });

    it('converts http URL to ws URL', () => {
        const client = new DispatcherWebSocketClient('https://cad.local', makeAuthState('t'));
        client.connect();
        expect(MockWebSocket.instances[0].url).toContain('wss://cad.local');
        client.disconnect();
    });

    it('notifies connection handlers on open', () => {
        const client = new DispatcherWebSocketClient(BASE, makeAuthState());
        const handler = vi.fn();
        client.onConnectionChanged(handler);
        client.connect();

        MockWebSocket.instances[0].simulateOpen();
        expect(handler).toHaveBeenCalledWith(true);
        client.disconnect();
    });

    it('notifies connection handlers on close', () => {
        const client = new DispatcherWebSocketClient(BASE, makeAuthState());
        const handler = vi.fn();
        client.onConnectionChanged(handler);
        client.connect();
        MockWebSocket.instances[0].simulateOpen();

        MockWebSocket.instances[0].simulateClose();
        expect(handler).toHaveBeenCalledWith(false);
        client.disconnect();
    });

    it('dispatches call.created events to registered handlers', () => {
        const client = new DispatcherWebSocketClient(BASE, makeAuthState());
        const handler = vi.fn();
        client.onCallCreated(handler);
        client.connect();

        const ws = MockWebSocket.instances[0];
        ws.simulateOpen();
        ws.simulateMessage({
            type: 'call.created',
            sequenceNumber: 1,
            timestamp: '2026-01-01T00:00:00Z',
            payload: { callId: 'c1', state: 'active' },
        });

        expect(handler).toHaveBeenCalledOnce();
        expect(handler.mock.calls[0][0]).toMatchObject({ callId: 'c1' });
        client.disconnect();
    });

    it('dispatches incident.created events', () => {
        const client = new DispatcherWebSocketClient(BASE, makeAuthState());
        const handler = vi.fn();
        client.onIncidentCreated(handler);
        client.connect();

        MockWebSocket.instances[0].simulateOpen();
        MockWebSocket.instances[0].simulateMessage({
            type: 'incident.created',
            sequenceNumber: 2,
            timestamp: '2026-01-01T00:00:00Z',
            payload: { incidentId: 'i1' },
        });

        expect(handler).toHaveBeenCalledOnce();
        expect(handler.mock.calls[0][0]).toMatchObject({ incidentId: 'i1' });
        client.disconnect();
    });

    it('tracks the latest sequence number', () => {
        const client = new DispatcherWebSocketClient(BASE, makeAuthState());
        client.connect();
        MockWebSocket.instances[0].simulateOpen();
        MockWebSocket.instances[0].simulateMessage({ type: 'call.created', sequenceNumber: 42, timestamp: '', payload: {} });

        expect(client.lastSequenceNumber).toBe(42);
        client.disconnect();
    });

    it('reconnects after disconnect with exponential back-off', () => {
        const client = new DispatcherWebSocketClient(BASE, makeAuthState());
        client.connect();
        MockWebSocket.instances[0].simulateOpen();
        MockWebSocket.instances[0].simulateClose();

        expect(MockWebSocket.instances).toHaveLength(1); // no reconnect yet

        vi.advanceTimersByTime(1_000); // first delay
        expect(MockWebSocket.instances).toHaveLength(2);

        MockWebSocket.instances[1].simulateOpen();
        MockWebSocket.instances[1].simulateClose();

        vi.advanceTimersByTime(2_000); // doubled delay
        expect(MockWebSocket.instances).toHaveLength(3);

        client.disconnect();
    });

    it('resets reconnect delay after successful connection', () => {
        const client = new DispatcherWebSocketClient(BASE, makeAuthState());
        client.connect();
        MockWebSocket.instances[0].simulateOpen();
        MockWebSocket.instances[0].simulateClose();
        vi.advanceTimersByTime(1_000);

        MockWebSocket.instances[1].simulateOpen();  // reset delay
        MockWebSocket.instances[1].simulateClose();

        vi.advanceTimersByTime(1_000); // should reconnect after 1s again (reset)
        expect(MockWebSocket.instances).toHaveLength(3);
        client.disconnect();
    });

    it('stops reconnecting after disconnect() is called', () => {
        const client = new DispatcherWebSocketClient(BASE, makeAuthState());
        client.connect();
        MockWebSocket.instances[0].simulateOpen();

        client.disconnect();
        vi.advanceTimersByTime(10_000);
        expect(MockWebSocket.instances).toHaveLength(1);
    });

    it('silently ignores malformed JSON messages', () => {
        const client = new DispatcherWebSocketClient(BASE, makeAuthState());
        const handler = vi.fn();
        client.onCallCreated(handler);
        client.connect();

        MockWebSocket.instances[0].simulateOpen();
        MockWebSocket.instances[0].onmessage?.({ data: 'not json at all' });

        expect(handler).not.toHaveBeenCalled();
        client.disconnect();
    });
});
