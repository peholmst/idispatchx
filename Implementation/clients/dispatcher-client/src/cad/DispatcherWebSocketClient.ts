import type { AuthState } from '../auth/AuthState.ts';
import type {
    CallCreatedPayload,
    CallUpdatedPayload,
    CallEndedPayload,
    CallAttachedToIncidentPayload,
    CallDetachedFromIncidentPayload,
    IncidentCreatedPayload,
    IncidentDetailsUpdatedPayload,
    IncidentStateChangedPayload,
    IncidentLogEntryAddedPayload,
    ConnectedPayload,
    SystemStatusChangedPayload,
} from './types.ts';

const INITIAL_RECONNECT_DELAY_MS = 1_000;
const MAX_RECONNECT_DELAY_MS = 30_000;

interface WsMessage {
    type: string;
    sequenceNumber: number;
    timestamp: string;
    payload: unknown;
}

type Handler<T> = (payload: T) => void;

interface HandlerMap {
    'call.created': Handler<CallCreatedPayload>[];
    'call.updated': Handler<CallUpdatedPayload>[];
    'call.ended': Handler<CallEndedPayload>[];
    'call.attached_to_incident': Handler<CallAttachedToIncidentPayload>[];
    'call.detached_from_incident': Handler<CallDetachedFromIncidentPayload>[];
    'incident.created': Handler<IncidentCreatedPayload>[];
    'incident.details_updated': Handler<IncidentDetailsUpdatedPayload>[];
    'incident.state_changed': Handler<IncidentStateChangedPayload>[];
    'incident.log_entry_added': Handler<IncidentLogEntryAddedPayload>[];
    'system.status_changed': Handler<SystemStatusChangedPayload>[];
}

export class DispatcherWebSocketClient {
    readonly #wsUrl: string;
    readonly #authState: AuthState;

    #ws: WebSocket | null = null;
    #reconnectDelay = INITIAL_RECONNECT_DELAY_MS;
    #reconnectTimerId: ReturnType<typeof setTimeout> | null = null;
    #stopped = false;
    #lastSequenceNumber = 0;

    readonly #handlers: HandlerMap = {
        'call.created': [],
        'call.updated': [],
        'call.ended': [],
        'call.attached_to_incident': [],
        'call.detached_from_incident': [],
        'incident.created': [],
        'incident.details_updated': [],
        'incident.state_changed': [],
        'incident.log_entry_added': [],
        'system.status_changed': [],
    };
    #connectionHandlers: Handler<boolean>[] = [];
    #connectedHandlers: Handler<ConnectedPayload>[] = [];

    constructor(cadServerUrl: string, authState: AuthState) {
        this.#wsUrl = cadServerUrl.replace(/^http/, 'ws') + '/api/v1/ws/dispatcher';
        this.#authState = authState;
    }

    /** Start the WebSocket connection. */
    connect(): void {
        this.#stopped = false;
        this.#openConnection();
    }

    /** Permanently close the connection and stop reconnecting. */
    disconnect(): void {
        this.#stopped = true;
        this.#cancelReconnect();
        this.#ws?.close();
        this.#ws = null;
    }

    get lastSequenceNumber(): number {
        return this.#lastSequenceNumber;
    }

    onCallCreated(handler: Handler<CallCreatedPayload>): void {
        this.#handlers['call.created'].push(handler);
    }

    onCallUpdated(handler: Handler<CallUpdatedPayload>): void {
        this.#handlers['call.updated'].push(handler);
    }

    onCallEnded(handler: Handler<CallEndedPayload>): void {
        this.#handlers['call.ended'].push(handler);
    }

    onCallAttachedToIncident(handler: Handler<CallAttachedToIncidentPayload>): void {
        this.#handlers['call.attached_to_incident'].push(handler);
    }

    onCallDetachedFromIncident(handler: Handler<CallDetachedFromIncidentPayload>): void {
        this.#handlers['call.detached_from_incident'].push(handler);
    }

    onIncidentCreated(handler: Handler<IncidentCreatedPayload>): void {
        this.#handlers['incident.created'].push(handler);
    }

    onIncidentDetailsUpdated(handler: Handler<IncidentDetailsUpdatedPayload>): void {
        this.#handlers['incident.details_updated'].push(handler);
    }

    onIncidentStateChanged(handler: Handler<IncidentStateChangedPayload>): void {
        this.#handlers['incident.state_changed'].push(handler);
    }

    onIncidentLogEntryAdded(handler: Handler<IncidentLogEntryAddedPayload>): void {
        this.#handlers['incident.log_entry_added'].push(handler);
    }

    onSystemStatusChanged(handler: Handler<SystemStatusChangedPayload>): void {
        this.#handlers['system.status_changed'].push(handler);
    }

    /** Register a handler that receives `true` on connect and `false` on disconnect. */
    onConnectionChanged(handler: Handler<boolean>): void {
        this.#connectionHandlers.push(handler);
    }

    /** Register a handler called with the connected payload on each successful WebSocket handshake. */
    onConnected(handler: Handler<ConnectedPayload>): void {
        this.#connectedHandlers.push(handler);
    }

    #openConnection(): void {
        if (this.#stopped) return;

        const token = this.#authState.getAccessToken();
        const url = token ? `${this.#wsUrl}?token=${encodeURIComponent(token)}` : this.#wsUrl;

        const ws = new WebSocket(url);
        this.#ws = ws;

        ws.onopen = () => {
            this.#reconnectDelay = INITIAL_RECONNECT_DELAY_MS;
            this.#notifyConnection(true);
        };

        ws.onmessage = (event: MessageEvent<string>) => {
            this.#handleMessage(event.data);
        };

        ws.onerror = () => {
            // onclose fires after onerror; reconnect logic lives there
        };

        ws.onclose = () => {
            this.#ws = null;
            this.#notifyConnection(false);
            if (!this.#stopped) {
                this.#scheduleReconnect();
            }
        };
    }

    #handleMessage(data: string): void {
        let msg: WsMessage;
        try {
            msg = JSON.parse(data) as WsMessage;
        } catch {
            return;
        }

        if (typeof msg.sequenceNumber === 'number') {
            this.#lastSequenceNumber = msg.sequenceNumber;
        }

        if (msg.type === 'connected') {
            this.#notifyConnected(msg.payload as ConnectedPayload);
            return;
        }

        this.#dispatch(msg.type, msg.payload);
    }

    #dispatch(type: string, payload: unknown): void {
        const handlers = this.#handlers[type as keyof HandlerMap];
        if (handlers) {
            for (const h of handlers) {
                try {
                    (h as Handler<unknown>)(payload);
                } catch (err) {
                    console.error(`[DispatcherWebSocketClient] Handler for "${type}" threw:`, err);
                }
            }
        }
    }

    #notifyConnection(connected: boolean): void {
        for (const h of this.#connectionHandlers) {
            try {
                h(connected);
            } catch (err) {
                console.error('[DispatcherWebSocketClient] Connection handler threw:', err);
            }
        }
    }

    #notifyConnected(payload: ConnectedPayload): void {
        for (const h of this.#connectedHandlers) {
            try {
                h(payload);
            } catch (err) {
                console.error('[DispatcherWebSocketClient] Connected handler threw:', err);
            }
        }
    }

    #scheduleReconnect(): void {
        this.#cancelReconnect();
        this.#reconnectTimerId = setTimeout(() => {
            this.#reconnectTimerId = null;
            this.#openConnection();
        }, this.#reconnectDelay);

        this.#reconnectDelay = Math.min(this.#reconnectDelay * 2, MAX_RECONNECT_DELAY_MS);
    }

    #cancelReconnect(): void {
        if (this.#reconnectTimerId !== null) {
            clearTimeout(this.#reconnectTimerId);
            this.#reconnectTimerId = null;
        }
    }
}
