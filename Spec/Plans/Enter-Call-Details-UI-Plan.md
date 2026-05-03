# Enter Call Details — Dispatcher Client UI Plan

This document covers the Dispatcher Client (TypeScript) implementation: API client types, REST and
WebSocket client code, and the call management UI components.

Related plans:
- [Domain Plan](Enter-Call-Details-Domain-Plan.md) — domain model, commands, handlers, WAL
- [Endpoints Plan](Enter-Call-Details-Endpoints-Plan.md) — REST and WebSocket adapters

## References

- [UC: Enter Call Details](../UseCases/Dispatcher/UC-Enter-Call-Details.md)
- [UC: Create Incident From Call](../UseCases/Dispatcher/UC-Create-Incident-From-Call.md)
- [UC: Attach Call To Incident](../UseCases/Dispatcher/UC-Attach-Call-To-Incident.md)
- [UC: Detach Call From Incident](../UseCases/Dispatcher/UC-Detach-Call-From-Incident.md)
- [Technical Design: CAD Server WebSocket/REST API](../TechnicalDesigns/CAD-Server-WebSocket-REST-API.md)
- [UX Guidelines](../UXDesigns/Dispatcher-Client-UX-Guidelines.md)
- [NFR: Availability](../NonFunctionalRequirements/Availability.md)
- [NFR: Performance](../NonFunctionalRequirements/Performance.md)

---

## Out of Scope

- Full incident editing UI (state transitions, unit assignment): future issue
- Standalone incident creation in the Dispatcher Client header: future issue

---

## Plan Overview

| Phase | Description | Tasks | Status |
|-------|-------------|-------|--------|
| 10 | Dispatcher Client — API Client Layer | 3 | Not Started |
| 11 | Dispatcher Client — Call Management UI | 4 | Not Started |
| 12 | Dispatcher Client — Incident Integration | 2 | Not Started |
| **Total** | | **9** | |

---

## Phase 10: Dispatcher Client — API Client Layer

TypeScript client code for communicating with the CAD Server REST and WebSocket APIs.

### Task 10.1: CAD Server Types

**Status:** Not Started

**Description:**
Define TypeScript types for all CAD Server entities and events used in this UC.

**Package:** `@idispatchx/dispatcher-client`

**Files to Create:**
- `src/cad/types.ts` — TypeScript type definitions:
  - `CallState`, `CallOutcome` (string literal unions)
  - `Location` (discriminated union with `type` literal for each variant)
  - `Coordinates`, `Municipality`, `MultilingualName`
  - `Call` (full call object as returned by GET /api/v1/calls/{callId})
  - `CallSummary` (as returned in GET /api/v1/calls list)
  - `IncidentState`, `IncidentPriority` (string literal unions)
  - `IncidentSummary` (as returned in GET /api/v1/incidents list)
  - `Incident` (full incident object)
  - `IncidentLogEntry` (automatic and manual variants as discriminated union on `entry_type`)
  - WebSocket event payload types: `CallCreatedPayload`, `CallUpdatedPayload`, `CallEndedPayload`, `CallAttachedToIncidentPayload`, `CallDetachedFromIncidentPayload`, `IncidentCreatedPayload`, `IncidentLogEntryAddedPayload`

**Acceptance Criteria:**
- [ ] All types match the JSON representations in the API design exactly
- [ ] Discriminated unions use the `type` literal field for Location, and `entry_type` for log entries
- [ ] `IncidentPriority` type covers all five values (`A`, `B`, `C`, `D`, `N`)
- [ ] Types are exported from a single barrel file

**Dependencies:** None (specification-driven)

---

### Task 10.2: CAD REST Client

**Status:** Not Started

**Description:**
Implement a `CadRestClient` class providing async methods for all call and incident REST operations
in this UC. Uses the existing `HttpClient` infrastructure.

**Package:** `@idispatchx/dispatcher-client`

**Files to Create:**
- `src/cad/CadRestClient.ts`

**Methods to implement:**
- `createCall(params): Promise<{ callId: string }>` — POST /api/v1/calls
- `updateCall(callId, params): Promise<void>` — PATCH /api/v1/calls/{callId}
- `endCall(callId, params): Promise<void>` — POST /api/v1/calls/{callId}/end
- `attachCallToIncident(callId, incidentId): Promise<void>` — POST /api/v1/calls/{callId}/attach-to-incident
- `detachCallFromIncident(callId): Promise<void>` — POST /api/v1/calls/{callId}/detach-from-incident
- `listActiveCalls(): Promise<CallSummary[]>` — GET /api/v1/calls
- `getCall(callId): Promise<Call>` — GET /api/v1/calls/{callId}
- `createIncidentFromCall(params): Promise<{ incidentId: string }>` — POST /api/v1/incidents (with `sourceCallId`)
- `listIncidents(includeEnded?: boolean): Promise<IncidentSummary[]>` — GET /api/v1/incidents
- `getIncident(incidentId): Promise<Incident>` — GET /api/v1/incidents/{incidentId}

**Acceptance Criteria:**
- [ ] Each method generates a fresh `X-Command-Id` UUID for mutating requests
- [ ] Bearer token obtained from `SessionManager`
- [ ] HTTP errors mapped to typed `CadApiError` with error code
- [ ] Unit tests mock the HTTP layer and verify request format, headers, and body

**Dependencies:** Task 10.1

---

### Task 10.3: Dispatcher WebSocket Client

**Status:** Not Started

**Description:**
Implement a `DispatcherWebSocketClient` that connects to `/api/v1/ws/dispatcher`, receives server
events, and dispatches them to registered handlers. Implements reconnection with exponential
back-off per the Availability NFR.

**Package:** `@idispatchx/dispatcher-client`

**Files to Create:**
- `src/cad/DispatcherWebSocketClient.ts`

**Responsibilities:**
- Establish WebSocket connection with JWT token as query parameter
- Dispatch received messages to typed event handlers by `type` field
- Reconnect automatically on disconnect with exponential back-off
- Expose `onCall*`, `onIncident*` handler registration methods for call and incident events defined in this UC
- Track the latest `sequenceNumber` for gap detection on reconnect

**Acceptance Criteria:**
- [ ] Reconnect with exponential back-off; cap at a reasonable maximum interval
- [ ] Disconnection and reconnection are surfaced to the UI layer
- [ ] Typed event handlers invoked for each message type from this UC
- [ ] Unit tests: mock WebSocket; verify event dispatch and reconnection behavior

**Dependencies:** Task 10.1

---

## Phase 11: Dispatcher Client — Call Management UI

Web Components for the call detail form and call list in the primary window.

### Task 11.1: Location Entry Component

**Status:** Not Started

**Description:**
Implement `<idispatch-location-entry>` custom element — a form panel that allows the dispatcher
to select a location variant and enter its fields.

**Package:** `@idispatchx/dispatcher-client`

**Files to Create:**
- `src/ui/LocationEntry.ts`
- `src/ui/LocationEntry.css`

**Behavior:**
- Variant selector: `ExactAddress`, `RoadIntersection`, `NamedPlace`, `RelativeLocation`
- Renders the appropriate fields for the selected variant
- `ExactAddress` fields: municipality picker, address name (text input), address number (text input, optional), coordinates (CoordInput, optional), additional details (textarea, optional)
- `RoadIntersection` fields: municipality picker, road name A, road name B, coordinates (optional), additional details (optional)
- `NamedPlace` fields: municipality picker, place name, coordinates (optional), additional details (optional)
- `RelativeLocation` fields: municipality picker, reference place, additional details (required), coordinates (optional)
- Geocoding integration: when address is entered, offer a "Look Up Address" button that calls the GIS Server geocoding API to resolve coordinates (using existing `GeocodingClient`)
- Fires a `location-changed` custom event when the location value changes
- Exposes `value` property returning a `Location | null`
- Displays validation errors inline (missing required fields)

**Acceptance Criteria:**
- [ ] All four variants render correctly with correct required/optional fields
- [ ] Geocoding button visible and functional for `ExactAddress` variant
- [ ] Coordinates display in DDM format by default per UX guidelines; can switch to DD and DMS
- [ ] Validation errors shown inline without blocking UI
- [ ] `location-changed` event fires on any field change
- [ ] `value` returns `null` if the location is incomplete/invalid
- [ ] Unit tests verify DOM structure and event dispatch for each variant

**Dependencies:** Task 10.2 (for geocoding integration)

---

### Task 11.2: Call Detail Form Component

**Status:** Not Started

**Description:**
Implement `<idispatch-call-detail-form>` custom element — the full call editing panel shown in
the left column of the primary window.

**Package:** `@idispatchx/dispatcher-client`

**Files to Create:**
- `src/ui/CallDetailForm.ts`
- `src/ui/CallDetailForm.css`

**Fields:**
- Caller name (text input, max 100 chars)
- Caller phone number (text input, E.164)
- Location (`<idispatch-location-entry>`)
- Description (textarea, max 1000 chars)
- Outcome selector (visible when setting outcome manually): dropdown/button group for `caller_advised`, `hoax`, `accidental`, `other_no_actions_taken`; `incident_created` and `attached_to_incident` shown as read-only labels when set by specific actions
- Outcome rationale (textarea; visible when outcome requires it)

**Actions (buttons at the bottom):**
- **End Call** — enabled when call is active and outcome is set; calls `endCall`
- **Create Incident** — calls `createIncidentFromCall`; disabled if call already has an outcome
- **Attach to Incident** — opens incident picker; calls `attachCallToIncident`
- **Detach from Incident** — visible and enabled only when `outcome = attached_to_incident`; calls `detachCallFromIncident`
- **Copy Location to Incident** — visible when call is linked to an incident and has a location; copies location to incident (defer to future issue if incident update not implemented yet; mark as disabled with tooltip)

**Real-time behavior:**
- Form fields debounce changes and call `updateCall` after 500 ms of inactivity
- All changes from other dispatchers (via WebSocket) are reflected in the form immediately
- When call coordinates become known, fire a `coordinates-known` event so the list column can apply the vicinity filter

**Acceptance Criteria:**
- [ ] All fields render and bind to call data correctly
- [ ] Debounced auto-save calls `updateCall` after field changes
- [ ] End Call validates outcome is set; shows inline error if not
- [ ] Create Incident, Attach/Detach actions invoke correct API calls and update UI on success
- [ ] WebSocket updates applied to form without user action
- [ ] `coordinates-known` event fired when location with coordinates is set
- [ ] Keyboard navigable per UX guidelines
- [ ] Unit tests for field binding, action handling, and WS update application

**Dependencies:** Tasks 10.2, 10.3, 11.1

---

### Task 11.3: Call List Component

**Status:** Not Started

**Description:**
Implement `<idispatch-call-list>` custom element — the call list shown in the upper part of the
third column.

**Package:** `@idispatchx/dispatcher-client`

**Files to Create:**
- `src/ui/CallList.ts`
- `src/ui/CallList.css`

**Columns:** call started time, caller name, caller phone number, location summary (municipality + address/place name), state, outcome, receiving dispatcher.

**Behavior:**
- Fetches initial data via `listActiveCalls()` on mount
- Updates in real time via WebSocket events (`call.created`, `call.updated`, `call.ended`)
- Text filter (caller name, description keywords)
- Sort by any column; default: most recent first
- Clicking a call opens it in `<idispatch-call-detail-form>`
- Supports vicinity filter mode (see Task 11.4)

**Acceptance Criteria:**
- [ ] All columns display correct data
- [ ] Real-time updates add/update/remove rows as calls are created/updated/ended
- [ ] Text filter reduces visible rows correctly
- [ ] Clicking a row fires a `call-selected` custom event with the call ID
- [ ] Vicinity filter mode shows only nearby calls when active

**Dependencies:** Tasks 10.2, 10.3

---

### Task 11.4: Vicinity Filter

**Status:** Not Started

**Description:**
Implement client-side vicinity filtering on the call and incident lists. When the dispatcher enters
coordinates for the current call, the lists switch to a "vicinity" mode showing only calls and
incidents whose coordinates fall within a configurable radius (default: 1 km).

**Package:** `@idispatchx/dispatcher-client`

**Files to Create:**
- `src/geo/vicinity.ts` — `filterByVicinity(items: {coordinates?}[], center: Coordinates, radiusMeters: number): typeof items` utility using the Haversine formula

**Integration:**
- `CallDetailForm` fires `coordinates-known` event when call coordinates are set (or cleared)
- Primary window listens for `coordinates-known` and toggles vicinity mode on both `CallList` and `IncidentList`
- Both lists show a visible banner when vicinity filter is active ("Showing calls and incidents near [location]") with a "Clear filter" button
- Clearing the filter restores the normal list view

**Acceptance Criteria:**
- [ ] Haversine distance calculation is correct (unit tested)
- [ ] Lists switch to vicinity mode within 200 ms of `coordinates-known` event
- [ ] Banner is visually prominent per UX guidelines
- [ ] "Clear filter" restores the full list
- [ ] Filter is also cleared when the current call ends or a different call is selected
- [ ] Unit tests for `filterByVicinity` utility

**Dependencies:** Tasks 11.2, 11.3

---

## Phase 12: Dispatcher Client — Incident Integration

Minimal incident UI needed for the call-to-incident linking flow.

### Task 12.1: Incident List Component

**Status:** Not Started

**Description:**
Implement `<idispatch-incident-list>` custom element — the incident list shown in the lower part
of the third column. Scope: display and selection only; full incident editing is in a future issue.

**Package:** `@idispatchx/dispatcher-client`

**Files to Create:**
- `src/ui/IncidentList.ts`
- `src/ui/IncidentList.css`

**Columns:** incident created time, incident type (code + localized description), incident priority, location summary, state, number of linked calls.

**Behavior:**
- Fetches initial data via `listIncidents()` on mount
- Updates via WebSocket events (`incident.created`, `incident.state_changed`, `incident.details_updated`, `incident.log_entry_added`)
- Text filter; incident state filter; sort by any column; default: most recent first
- Ended incidents hidden by default; toggle to show
- Clicking an incident fires `incident-selected` event
- Supports vicinity filter mode (Task 11.4)

**Acceptance Criteria:**
- [ ] All columns display correct data from incident summaries
- [ ] Real-time updates reflect server changes
- [ ] State filter correctly includes/excludes ended incidents
- [ ] `incident-selected` event fired on row click

**Dependencies:** Tasks 10.2, 10.3

---

### Task 12.2: Primary Window Layout Integration

**Status:** Not Started

**Description:**
Wire the call and incident UI components into `PrimaryWindow.ts`. Implement the three-column layout
with call detail form (left), incident detail placeholder (center — full detail is a future issue),
and call/incident lists (right).

**Package:** `@idispatchx/dispatcher-client`

**Files to Modify:**
- `src/ui/PrimaryWindow.ts` — add:
  - "New Call" action in header: calls `createCall()`, opens new call in `<idispatch-call-detail-form>`
  - Left column: renders `<idispatch-call-detail-form>` for the currently selected call (or empty state)
  - Center column: incident detail placeholder (static "select an incident" message)
  - Right column: `<idispatch-call-list>` + `<idispatch-incident-list>`
  - Coordinates-known ↔ vicinity filter wiring
  - Call selection propagation from list to form
- `src/ui/PrimaryWindow.css` — three-column layout

**Acceptance Criteria:**
- [ ] "New Call" creates a call and immediately shows it in the left column
- [ ] Selecting a call from the list loads it into the form
- [ ] Vicinity filter activates/deactivates correctly when call coordinates change
- [ ] Incident list reflects real-time updates from the WebSocket client
- [ ] UI responds to input within 100–200 ms per the Performance NFR

**Dependencies:** Tasks 11.1–11.4, 12.1

---

## Execution Notes

Phase 10 is independent of the backend phases and can start in parallel with Domain Plan Phase 6.
Phases 11 and 12 require Phase 10 to be complete first.

### Dispatcher Client: Auto-Save Debounce

The call detail form debounces field changes (Task 11.2). This means a call is created first
(with an empty body), then filled in incrementally via PATCH. This matches the REST API design
where all call fields are optional.

### Test Coverage Priority

Focus unit tests on:
- REST endpoint validation (role enforcement, X-Command-Id, field constraints) — Endpoints Plan
- Haversine distance calculation for the vicinity filter
