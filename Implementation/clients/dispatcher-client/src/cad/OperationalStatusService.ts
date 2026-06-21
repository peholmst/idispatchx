import type { DispatcherWebSocketClient } from './DispatcherWebSocketClient.ts';

export interface OperationalStatus {
    cadServerConnected: boolean;
    cadArchiveAvailable: boolean;
    gisServerAvailable: boolean;
}

type StatusListener = (status: OperationalStatus) => void;

/**
 * Merges CAD Server WebSocket signals and GIS availability into a single
 * observable {@link OperationalStatus}.
 *
 * Wire it up after constructing {@link DispatcherWebSocketClient}:
 *   - Call {@link attachWebSocketClient} to subscribe to `system.status_changed`
 *     and connection events.
 *   - Call {@link notifyGisAvailability} whenever the GIS monitor detects a change.
 *
 * The initial state assumes everything is available. The first WebSocket `connected`
 * message or GIS probe result overrides these assumptions.
 */
export class OperationalStatusService {
    #cadServerConnected = false;
    #cadArchiveAvailable = true;
    #gisServerAvailable = true;
    readonly #listeners: StatusListener[] = [];

    attachWebSocketClient(ws: DispatcherWebSocketClient): void {
        ws.onConnected(payload => {
            const archiveAvailable = payload.systemStatus?.cadArchiveAvailable ?? true;
            this.#update({ cadArchiveAvailable: archiveAvailable });
        });

        ws.onSystemStatusChanged(payload => {
            this.#update({ cadArchiveAvailable: payload.cadArchiveAvailable });
        });

        ws.onConnectionChanged(connected => {
            this.#update({ cadServerConnected: connected });
        });
    }

    notifyGisAvailability(available: boolean): void {
        this.#update({ gisServerAvailable: available });
    }

    getStatus(): OperationalStatus {
        return {
            cadServerConnected: this.#cadServerConnected,
            cadArchiveAvailable: this.#cadArchiveAvailable,
            gisServerAvailable: this.#gisServerAvailable,
        };
    }

    isNormal(): boolean {
        const s = this.getStatus();
        return s.cadServerConnected && s.cadArchiveAvailable && s.gisServerAvailable;
    }

    onStatusChanged(listener: StatusListener): void {
        this.#listeners.push(listener);
    }

    #update(patch: Partial<OperationalStatus>): void {
        const before = this.getStatus();
        if ('cadServerConnected' in patch) this.#cadServerConnected = patch.cadServerConnected!;
        if ('cadArchiveAvailable' in patch) this.#cadArchiveAvailable = patch.cadArchiveAvailable!;
        if ('gisServerAvailable' in patch) this.#gisServerAvailable = patch.gisServerAvailable!;
        const after = this.getStatus();

        if (
            before.cadServerConnected !== after.cadServerConnected ||
            before.cadArchiveAvailable !== after.cadArchiveAvailable ||
            before.gisServerAvailable !== after.gisServerAvailable
        ) {
            for (const l of this.#listeners) {
                try { l(after); } catch (err) {
                    console.error('[OperationalStatusService] Listener threw:', err);
                }
            }
        }
    }
}
