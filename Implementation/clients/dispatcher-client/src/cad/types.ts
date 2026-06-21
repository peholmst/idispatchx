export type CallState = 'active' | 'ended';

export type CallOutcome =
    | 'incident_created'
    | 'attached_to_incident'
    | 'caller_advised'
    | 'hoax'
    | 'accidental'
    | 'other_no_actions_taken';

export interface Coordinates {
    latitude: number;
    longitude: number;
}

export type MultilingualName = Record<string, string>;

export interface Municipality {
    code: string | null;
    name: MultilingualName;
}

export interface ExactAddressLocation {
    type: 'exact_address';
    municipality: Municipality;
    addressName: MultilingualName;
    addressNumber: string | null;
    coordinates: Coordinates | null;
    additionalDetails: string | null;
}

export interface RoadIntersectionLocation {
    type: 'road_intersection';
    municipality: Municipality;
    roadNameA: MultilingualName;
    roadNameB: MultilingualName;
    coordinates: Coordinates | null;
    additionalDetails: string | null;
}

export interface NamedPlaceLocation {
    type: 'named_place';
    municipality: Municipality;
    name: MultilingualName;
    coordinates: Coordinates | null;
    additionalDetails: string | null;
}

export interface RelativeLocation {
    type: 'relative_location';
    municipality: Municipality;
    referencePlace: MultilingualName;
    additionalDetails: string;
    coordinates: Coordinates | null;
}

export type Location =
    | ExactAddressLocation
    | RoadIntersectionLocation
    | NamedPlaceLocation
    | RelativeLocation;

export interface CallSummary {
    callId: string;
    state: CallState;
    receivingDispatcher: string;
    callStarted: string;
    callerName: string | null;
    callerPhoneNumber: string | null;
    location: Location | null;
    description: string | null;
    outcome: CallOutcome | null;
    outcomeRationale: string | null;
    incidentId: string | null;
}

export type Call = CallSummary;

export type IncidentState = 'new' | 'queued' | 'active' | 'monitored' | 'ended';

export type IncidentPriority = 'A' | 'B' | 'C' | 'D' | 'N';

export interface IncidentSummary {
    incidentId: string;
    state: IncidentState;
    incidentCreated: string;
    incidentEnded: string | null;
    incidentType: string | null;
    incidentPriority: IncidentPriority | null;
    location: Location | null;
    description: string | null;
    callIds: string[];
}

export interface IncidentUnit {
    incidentUnitId: string;
    unitId: string;
    callSign: string;
    unitStaffing?: { officers: number; subOfficers: number; crew: number };
    unitAssignedAt: string;
    unitDispatchedAt: string | null;
    unitEnRouteAt: string | null;
    unitOnSceneAt: string | null;
    unitAvailableAt: string | null;
    unitBackAtStationAt: string | null;
    unitUnassignedAt: string | null;
}

export interface AutomaticLogEntry {
    logEntryId: string;
    logTimestamp: string;
    dispatcher: string | null;
    entryType: 'automatic';
    changeData: Record<string, unknown>;
}

export interface ManualLogEntry {
    logEntryId: string;
    logTimestamp: string;
    dispatcher: string | null;
    entryType: 'manual';
    description: string;
}

export type IncidentLogEntry = AutomaticLogEntry | ManualLogEntry;

export interface Incident extends IncidentSummary {
    units: IncidentUnit[];
    logEntries: IncidentLogEntry[];
}

// WebSocket event payload types

export type CallCreatedPayload = CallSummary;
export type CallUpdatedPayload = CallSummary;
export type CallEndedPayload = CallSummary;

export interface CallAttachedToIncidentPayload {
    callId: string;
    incidentId: string;
}

export interface CallDetachedFromIncidentPayload {
    callId: string;
    formerIncidentId: string;
}

export interface IncidentCreatedPayload {
    incidentId: string;
    state: IncidentState;
    incidentCreated: string;
    incidentType: string | null;
    incidentPriority: IncidentPriority | null;
    location: Location | null;
    description: string | null;
}

export interface IncidentDetailsUpdatedPayload {
    incidentId: string;
    incidentType: string | null;
    incidentPriority: IncidentPriority | null;
    location: Location | null;
    description: string | null;
}

export interface IncidentStateChangedPayload {
    incidentId: string;
    previousState: IncidentState;
    newState: IncidentState;
}

export interface IncidentLogEntryAddedPayload {
    incidentId: string;
    logEntry: IncidentLogEntry;
}

export interface SystemStatus {
    cadArchiveAvailable: boolean;
}

export interface ConnectedPayload {
    serverId?: string;
    serverTime: string;
    systemStatus?: SystemStatus;
}

export interface SystemStatusChangedPayload {
    cadArchiveAvailable: boolean;
}
