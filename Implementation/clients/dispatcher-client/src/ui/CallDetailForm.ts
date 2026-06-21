import STYLES from './CallDetailForm.css?inline';
import { LocationEntry, LocationChangedEvent } from './LocationEntry.ts';
import type { CadRestClient, UpdateCallParams } from '../cad/CadRestClient.ts';
import type { DispatcherWebSocketClient } from '../cad/DispatcherWebSocketClient.ts';
import type { GeocodingClient } from '../gis/GeocodingClient.ts';
import type { Call, CallSummary, CallOutcome, Coordinates } from '../cad/types.ts';
import { t } from '../i18n/index.ts';

const AUTOSAVE_DEBOUNCE_MS = 500;

export class CoordinatesKnownEvent extends CustomEvent<Coordinates | null> {
    static readonly TYPE = 'coordinates-known' as const;
    constructor(coordinates: Coordinates | null) {
        super(CoordinatesKnownEvent.TYPE, { detail: coordinates, bubbles: true, composed: true });
    }
}

export class CallSelectedEvent extends CustomEvent<string> {
    static readonly TYPE = 'call-detail-loaded' as const;
    constructor(callId: string) {
        super(CallSelectedEvent.TYPE, { detail: callId, bubbles: true, composed: true });
    }
}

/**
 * `<idispatch-call-detail-form>` — call editing form.
 * Debounces field changes and saves via PATCH. Applies WebSocket updates in real time.
 */
export class CallDetailForm extends HTMLElement {
    static readonly TAG = 'idispatch-call-detail-form' as const;

    #shadow: ShadowRoot;
    #cadRest: CadRestClient | null = null;
    #wsClient: DispatcherWebSocketClient | null = null;
    #geocodingClient: GeocodingClient | null = null;
    #currentCall: Call | null = null;
    #autosaveTimer: ReturnType<typeof setTimeout> | null = null;
    #pendingUpdate: Record<string, unknown> = {};
    #lastCoordinates: Coordinates | null = null;

    // DOM refs
    #callerNameInput!: HTMLInputElement;
    #callerPhoneInput!: HTMLInputElement;
    #locationEntry!: LocationEntry;
    #descriptionTextarea!: HTMLTextAreaElement;
    #outcomeSelect!: HTMLSelectElement;
    #outcomeRationaleWrapper!: HTMLDivElement;
    #outcomeRationaleTextarea!: HTMLTextAreaElement;
    #outcomeReadonlyLabel!: HTMLSpanElement;
    #endCallBtn!: HTMLButtonElement;
    #createIncidentBtn!: HTMLButtonElement;
    #attachToIncidentBtn!: HTMLButtonElement;
    #detachFromIncidentBtn!: HTMLButtonElement;
    #copyLocationBtn!: HTMLButtonElement;
    #statusMsg!: HTMLDivElement;
    #emptyState!: HTMLDivElement;
    #formBody!: HTMLDivElement;

    constructor() {
        super();
        this.#shadow = this.attachShadow({ mode: 'open' });
    }

    initialize(
        cadRest: CadRestClient,
        wsClient: DispatcherWebSocketClient,
        geocodingClient: GeocodingClient,
    ): void {
        this.#cadRest = cadRest;
        this.#wsClient = wsClient;
        this.#geocodingClient = geocodingClient;
    }

    connectedCallback(): void {
        const style = document.createElement('style');
        style.textContent = STYLES;
        this.#shadow.appendChild(style);
        this.#buildDom();
        this.#subscribeToWebSocket();
    }

    disconnectedCallback(): void {
        if (this.#autosaveTimer !== null) clearTimeout(this.#autosaveTimer);
    }

    loadCall(call: Call): void {
        this.#currentCall = call;
        this.#pendingUpdate = {};
        this.#populateForm(call);
        this.#emptyState.classList.add('hidden');
        this.#formBody.classList.remove('hidden');
        this.#updateButtonStates();
    }

    clearCall(): void {
        this.#currentCall = null;
        this.#pendingUpdate = {};
        this.#formBody.classList.add('hidden');
        this.#emptyState.classList.remove('hidden');
        this.#emitCoordinatesKnown(null);
    }

    #buildDom(): void {
        // Empty state
        this.#emptyState = document.createElement('div');
        this.#emptyState.className = 'empty-state';
        this.#emptyState.textContent = t('call.empty');

        this.#formBody = document.createElement('div');
        this.#formBody.className = 'form-body hidden';

        // Caller name
        const callerNameRow = this.#makeTextRow('call.callerName', 'caller-name', (el) => {
            this.#callerNameInput = el as HTMLInputElement;
            el.setAttribute('maxlength', '100');
        });

        // Caller phone
        const callerPhoneRow = this.#makeTextRow('call.callerPhone', 'caller-phone', (el) => {
            this.#callerPhoneInput = el as HTMLInputElement;
        });

        // Location entry
        const locationWrapper = document.createElement('div');
        locationWrapper.className = 'field-section';
        this.#locationEntry = document.createElement(LocationEntry.TAG) as LocationEntry;
        if (this.#geocodingClient) {
            this.#locationEntry.initialize(this.#geocodingClient);
        }
        locationWrapper.appendChild(this.#locationEntry);

        // Description
        const descriptionWrapper = document.createElement('div');
        descriptionWrapper.className = 'field-wrapper';
        const descLabel = document.createElement('label');
        descLabel.className = 'field-label';
        descLabel.htmlFor = 'call-description';
        descLabel.textContent = t('call.description');
        this.#descriptionTextarea = document.createElement('textarea');
        this.#descriptionTextarea.id = 'call-description';
        this.#descriptionTextarea.className = 'field-textarea';
        this.#descriptionTextarea.rows = 3;
        this.#descriptionTextarea.setAttribute('maxlength', '1000');
        descriptionWrapper.append(descLabel, this.#descriptionTextarea);

        // Outcome
        const outcomeWrapper = document.createElement('div');
        outcomeWrapper.className = 'field-wrapper';
        const outcomeLabel = document.createElement('label');
        outcomeLabel.className = 'field-label';
        outcomeLabel.htmlFor = 'call-outcome';
        outcomeLabel.textContent = t('call.outcome');
        const outcomeRow = document.createElement('div');
        outcomeRow.className = 'outcome-row';
        this.#outcomeSelect = document.createElement('select');
        this.#outcomeSelect.id = 'call-outcome';
        this.#outcomeSelect.className = 'field-select';
        const noOutcomeOpt = document.createElement('option');
        noOutcomeOpt.value = '';
        noOutcomeOpt.textContent = `— ${t('call.outcome.none')} —`;
        this.#outcomeSelect.appendChild(noOutcomeOpt);
        for (const [value, key] of [
            ['caller_advised', 'call.outcome.callerAdvised'],
            ['hoax', 'call.outcome.hoax'],
            ['accidental', 'call.outcome.accidental'],
            ['other_no_actions_taken', 'call.outcome.otherNoActionsTaken'],
        ] as const) {
            const opt = document.createElement('option');
            opt.value = value;
            opt.textContent = t(key);
            this.#outcomeSelect.appendChild(opt);
        }
        this.#outcomeReadonlyLabel = document.createElement('span');
        this.#outcomeReadonlyLabel.className = 'outcome-readonly hidden';
        outcomeRow.append(this.#outcomeSelect, this.#outcomeReadonlyLabel);
        outcomeWrapper.append(outcomeLabel, outcomeRow);

        // Outcome rationale
        this.#outcomeRationaleWrapper = document.createElement('div');
        this.#outcomeRationaleWrapper.className = 'field-wrapper hidden';
        const rationaleLabel = document.createElement('label');
        rationaleLabel.className = 'field-label';
        rationaleLabel.htmlFor = 'call-rationale';
        rationaleLabel.textContent = t('call.outcomeRationale');
        this.#outcomeRationaleTextarea = document.createElement('textarea');
        this.#outcomeRationaleTextarea.id = 'call-rationale';
        this.#outcomeRationaleTextarea.className = 'field-textarea';
        this.#outcomeRationaleTextarea.rows = 2;
        this.#outcomeRationaleTextarea.setAttribute('maxlength', '1000');
        this.#outcomeRationaleWrapper.append(rationaleLabel, this.#outcomeRationaleTextarea);

        // Status message
        this.#statusMsg = document.createElement('div');
        this.#statusMsg.className = 'status-msg hidden';

        // Action buttons
        const actions = document.createElement('div');
        actions.className = 'form-actions';

        this.#endCallBtn = this.#makeBtn('call.action.endCall', 'btn-primary', () => void this.#onEndCall());
        this.#createIncidentBtn = this.#makeBtn('call.action.createIncident', 'btn-secondary', () => void this.#onCreateIncident());
        this.#attachToIncidentBtn = this.#makeBtn('call.action.attachToIncident', 'btn-secondary', () => void this.#onAttachToIncident());
        this.#detachFromIncidentBtn = this.#makeBtn('call.action.detachFromIncident', 'btn-secondary', () => void this.#onDetachFromIncident());
        this.#copyLocationBtn = this.#makeBtn('call.action.copyLocation', 'btn-secondary', () => {});
        this.#copyLocationBtn.disabled = true;
        this.#copyLocationBtn.title = t('call.action.copyLocationDisabled');

        actions.append(
            this.#endCallBtn,
            this.#createIncidentBtn,
            this.#attachToIncidentBtn,
            this.#detachFromIncidentBtn,
            this.#copyLocationBtn,
        );

        this.#formBody.append(
            callerNameRow,
            callerPhoneRow,
            locationWrapper,
            descriptionWrapper,
            outcomeWrapper,
            this.#outcomeRationaleWrapper,
            this.#statusMsg,
            actions,
        );

        this.#shadow.append(this.#emptyState, this.#formBody);
        this.#attachFormListeners();
    }

    #makeTextRow(labelKey: string, id: string, configure: (el: HTMLElement) => void): HTMLDivElement {
        const wrapper = document.createElement('div');
        wrapper.className = 'field-wrapper';
        const label = document.createElement('label');
        label.className = 'field-label';
        label.htmlFor = id;
        label.textContent = t(labelKey as Parameters<typeof t>[0]);
        const input = document.createElement('input');
        input.type = 'text';
        input.id = id;
        input.className = 'field-input';
        configure(input);
        wrapper.append(label, input);
        return wrapper;
    }

    #makeBtn(labelKey: string, className: string, onClick: () => void): HTMLButtonElement {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = `action-btn ${className}`;
        btn.textContent = t(labelKey as Parameters<typeof t>[0]);
        btn.addEventListener('click', onClick);
        return btn;
    }

    #attachFormListeners(): void {
        this.#callerNameInput.addEventListener('input', () => {
            this.#scheduleSave({ callerName: this.#callerNameInput.value || null });
        });

        this.#callerPhoneInput.addEventListener('input', () => {
            this.#scheduleSave({ callerPhoneNumber: this.#callerPhoneInput.value || null });
        });

        this.#descriptionTextarea.addEventListener('input', () => {
            this.#scheduleSave({ description: this.#descriptionTextarea.value || null });
        });

        this.#outcomeSelect.addEventListener('change', () => {
            const outcome = (this.#outcomeSelect.value as CallOutcome) || null;
            // Clearing the outcome is not supported — only schedule a save when a value is selected.
            if (outcome !== null) {
                this.#scheduleSave({ outcome });
            }
            this.#toggleRationaleField(outcome);
        });

        this.#outcomeRationaleTextarea.addEventListener('input', () => {
            // Always include the current outcome so the server never receives rationale without outcome.
            const outcome = (this.#outcomeSelect.value as CallOutcome) || this.#currentCall?.outcome || null;
            this.#scheduleSave({
                outcomeRationale: this.#outcomeRationaleTextarea.value || null,
                ...(outcome !== null ? { outcome } : {}),
            });
        });

        this.#locationEntry.addEventListener(LocationChangedEvent.TYPE, () => {
            const loc = this.#locationEntry.value;
            // null from LocationEntry can mean intentional clear (all fields empty) OR invalid/
            // incomplete input. Only schedule a clear when the form is genuinely blank; skip
            // invalid states so transient editing does not overwrite a saved location.
            if (loc === null && !this.#locationEntry.isBlank) return;
            this.#scheduleSave({ location: loc });

            const coords = loc?.coordinates ?? null;
            if (coords?.latitude !== this.#lastCoordinates?.latitude ||
                coords?.longitude !== this.#lastCoordinates?.longitude) {
                this.#lastCoordinates = coords;
                this.#emitCoordinatesKnown(coords);
            }
        });
    }

    #scheduleSave(partial: Record<string, unknown>): void {
        Object.assign(this.#pendingUpdate, partial);
        if (this.#autosaveTimer !== null) clearTimeout(this.#autosaveTimer);
        this.#autosaveTimer = setTimeout(() => void this.#flushSave(), AUTOSAVE_DEBOUNCE_MS);
    }

    async #flushSave(): Promise<void> {
        if (!this.#currentCall || !this.#cadRest || Object.keys(this.#pendingUpdate).length === 0) return;
        const update = { ...this.#pendingUpdate };
        this.#pendingUpdate = {};
        // Strip only undefined (absent fields). null values represent explicit clears and must
        // be included in the PATCH payload so the server can clear the field.
        const filteredUpdate = Object.fromEntries(
            Object.entries(update).filter(([, v]) => v !== undefined)
        );
        if (Object.keys(filteredUpdate).length === 0) return;
        try {
            await this.#cadRest.updateCall(this.#currentCall.callId, filteredUpdate as UpdateCallParams);
        } catch (err) {
            console.error('[CallDetailForm] Auto-save failed:', err);
            Object.assign(this.#pendingUpdate, update); // restore for retry
            throw err; // propagate so end-call / create-incident can abort
        }
    }

    async #onEndCall(): Promise<void> {
        if (!this.#currentCall || !this.#cadRest) return;
        const outcome = (this.#outcomeSelect.value as CallOutcome) || this.#currentCall.outcome;
        if (!outcome) {
            this.#showStatus(t('call.error.outcomeRequired'), 'error');
            return;
        }
        this.#setLoading(true);
        try {
            await this.#flushSave();
            await this.#cadRest.endCall(this.#currentCall.callId, { outcome });
        } catch (err) {
            this.#showStatus(String(err), 'error');
        } finally {
            this.#setLoading(false);
        }
    }

    async #onCreateIncident(): Promise<void> {
        if (!this.#currentCall || !this.#cadRest) return;
        this.#setLoading(true);
        try {
            await this.#flushSave();
            await this.#cadRest.createIncidentFromCall({ sourceCallId: this.#currentCall.callId });
        } catch (err) {
            this.#showStatus(String(err), 'error');
        } finally {
            this.#setLoading(false);
        }
    }

    async #onAttachToIncident(): Promise<void> {
        const incidentId = await this.#pickIncident();
        if (!incidentId || !this.#currentCall || !this.#cadRest) return;
        this.#setLoading(true);
        try {
            await this.#cadRest.attachCallToIncident(this.#currentCall.callId, incidentId);
        } catch (err) {
            this.#showStatus(String(err), 'error');
        } finally {
            this.#setLoading(false);
        }
    }

    async #onDetachFromIncident(): Promise<void> {
        if (!this.#currentCall || !this.#cadRest) return;
        this.#setLoading(true);
        try {
            await this.#cadRest.detachCallFromIncident(this.#currentCall.callId);
        } catch (err) {
            this.#showStatus(String(err), 'error');
        } finally {
            this.#setLoading(false);
        }
    }

    async #pickIncident(): Promise<string | null> {
        if (!this.#cadRest) return null;
        const incidents = await this.#cadRest.listIncidents().catch(() => []);
        if (incidents.length === 0) {
            this.#showStatus(t('call.error.noIncidents'), 'info');
            return null;
        }

        // Simple overlay picker
        return new Promise((resolve) => {
            const overlay = document.createElement('div');
            overlay.className = 'incident-picker-overlay';

            const title = document.createElement('div');
            title.className = 'picker-title';
            title.textContent = t('call.action.selectIncident');

            const list = document.createElement('ul');
            list.className = 'picker-list';

            for (const inc of incidents) {
                const li = document.createElement('li');
                li.className = 'picker-item';
                const stateTag = inc.state;
                const typePart = inc.incidentType ?? '—';
                const priorityPart = inc.incidentPriority ?? '—';
                li.textContent = `${typePart} / ${priorityPart} [${stateTag}]`;
                li.addEventListener('click', () => {
                    overlay.remove();
                    resolve(inc.incidentId);
                });
                list.appendChild(li);
            }

            const cancelBtn = document.createElement('button');
            cancelBtn.className = 'picker-cancel';
            cancelBtn.type = 'button';
            cancelBtn.textContent = t('call.action.cancel');
            cancelBtn.addEventListener('click', () => {
                overlay.remove();
                resolve(null);
            });

            overlay.append(title, list, cancelBtn);
            this.#shadow.appendChild(overlay);
        });
    }

    #subscribeToWebSocket(): void {
        if (!this.#wsClient) return;

        this.#wsClient.onCallUpdated((payload: CallSummary) => {
            if (this.#currentCall && payload.callId === this.#currentCall.callId) {
                this.#currentCall = payload;
                this.#populateForm(payload);
                this.#updateButtonStates();
            }
        });

        this.#wsClient.onCallEnded((payload: CallSummary) => {
            if (this.#currentCall && payload.callId === this.#currentCall.callId) {
                this.#currentCall = payload;
                this.#populateForm(payload);
                this.#updateButtonStates();
            }
        });

        this.#wsClient.onCallAttachedToIncident((payload) => {
            if (this.#currentCall && payload.callId === this.#currentCall.callId) {
                this.#currentCall = { ...this.#currentCall, incidentId: payload.incidentId, outcome: 'attached_to_incident' };
                this.#populateForm(this.#currentCall);
                this.#updateButtonStates();
            }
        });

        this.#wsClient.onCallDetachedFromIncident((payload) => {
            if (this.#currentCall && payload.callId === this.#currentCall.callId) {
                this.#currentCall = { ...this.#currentCall, incidentId: null, outcome: null };
                this.#populateForm(this.#currentCall);
                this.#updateButtonStates();
            }
        });
    }

    #populateForm(call: Call): void {
        this.#callerNameInput.value = call.callerName ?? '';
        this.#callerPhoneInput.value = call.callerPhoneNumber ?? '';
        this.#descriptionTextarea.value = call.description ?? '';
        this.#locationEntry.value = call.location;
        // Emit coordinates so vicinity filters activate when a call is selected/loaded.
        const coords = call.location?.coordinates ?? null;
        if (coords?.latitude !== this.#lastCoordinates?.latitude ||
            coords?.longitude !== this.#lastCoordinates?.longitude) {
            this.#lastCoordinates = coords;
            this.#emitCoordinatesKnown(coords);
        }

        // Outcome
        const readonlyOutcomes: CallOutcome[] = ['incident_created', 'attached_to_incident'];
        if (call.outcome && readonlyOutcomes.includes(call.outcome)) {
            this.#outcomeSelect.classList.add('hidden');
            this.#outcomeReadonlyLabel.classList.remove('hidden');
            this.#outcomeReadonlyLabel.textContent = t(`call.outcome.${call.outcome}` as Parameters<typeof t>[0]);
        } else {
            this.#outcomeSelect.classList.remove('hidden');
            this.#outcomeReadonlyLabel.classList.add('hidden');
            this.#outcomeSelect.value = call.outcome ?? '';
        }
        this.#toggleRationaleField(call.outcome);
        this.#outcomeRationaleTextarea.value = call.outcomeRationale ?? '';
    }

    #toggleRationaleField(outcome: CallOutcome | null | undefined): void {
        const rationaleRequired: CallOutcome[] = ['caller_advised', 'hoax', 'accidental', 'other_no_actions_taken'];
        const show = !!outcome && rationaleRequired.includes(outcome);
        this.#outcomeRationaleWrapper.classList.toggle('hidden', !show);
    }

    #updateButtonStates(): void {
        const call = this.#currentCall;
        const active = call?.state === 'active';
        const hasOutcome = !!call?.outcome;
        const isAttached = call?.outcome === 'attached_to_incident';
        const isLinkedToIncident = !!call?.incidentId;
        const hasLocation = !!call?.location;

        this.#endCallBtn.disabled = !active;
        this.#createIncidentBtn.disabled = !active || hasOutcome;
        this.#attachToIncidentBtn.disabled = !active || hasOutcome;
        this.#detachFromIncidentBtn.disabled = !isAttached;
        this.#detachFromIncidentBtn.classList.toggle('hidden', !isAttached);
        this.#copyLocationBtn.disabled = true; // deferred to future issue
        this.#copyLocationBtn.classList.toggle('hidden', !isLinkedToIncident || !hasLocation);
    }

    #showStatus(message: string, type: 'error' | 'info'): void {
        this.#statusMsg.textContent = message;
        this.#statusMsg.className = `status-msg status-${type}`;
        this.#statusMsg.classList.remove('hidden');
        setTimeout(() => this.#statusMsg.classList.add('hidden'), 4000);
    }

    #setLoading(loading: boolean): void {
        this.#endCallBtn.disabled = loading;
        this.#createIncidentBtn.disabled = loading;
        this.#attachToIncidentBtn.disabled = loading;
    }

    #emitCoordinatesKnown(coordinates: Coordinates | null): void {
        this.dispatchEvent(new CoordinatesKnownEvent(coordinates));
    }
}
