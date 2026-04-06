# Technical Design: CAD Server WebSocket/REST API

## Overview

This document describes the REST and WebSocket API design for the CAD Server. The API serves four client types with distinct interaction patterns:

| Client | REST | WebSocket |
|--------|------|-----------|
| Dispatcher Client | Commands and queries | All operational events |
| Mobile Unit Client | Status, location, and staffing updates; alert acknowledgment | Alerts from CAD Server |
| Station Alert Client | — | Alerts from CAD Server |
| Admin Client | Reference data management and session control | — |

## References

- [ADR-0005: Session State During Failover](../ADR/ADR-0005-session-state-during-failover.md) — WebSocket sessions not preserved; clients must reconnect and re-authenticate
- [ADR-0008: CAD Server Ports-and-Adapters Architecture](../ADR/ADR-0008-cad-server-ports-and-adapters.md) — REST and WebSocket adapters as primary ports
- [Technical Design: CAD Server Domain Core](CAD-Server-Domain-Core.md) — Command/event model, idempotency, WAL sequence numbers
- [NFR: Security](../NonFunctionalRequirements/Security.md) — Authentication, authorization, session handling, auditability
- [NFR: Performance](../NonFunctionalRequirements/Performance.md) — Cross-dispatcher sync within seconds; alerts within seconds
- [NFR: Availability](../NonFunctionalRequirements/Availability.md) — Client reconnection, degraded modes, command idempotency
- [C4: Containers](../C4/Containers.md) — Client types, communication patterns, tech stack
- [Domain: Call](../Domain/Call.md), [Incident](../Domain/Incident.md), [Unit](../Domain/Unit.md), [UnitStatus](../Domain/UnitStatus.md), [AlertTarget](../Domain/AlertTarget.md), [Station](../Domain/Station.md), [IncidentType](../Domain/IncidentType.md)
- [Domain: Location](../Domain/Location.md), [MultilingualName](../Domain/MultilingualName.md)
- Use Cases: [UC-Enter-Call-Details](../UseCases/Dispatcher/UC-Enter-Call-Details.md), [UC-Create-Incident](../UseCases/Dispatcher/UC-Create-Incident.md), [UC-Create-Incident-From-Call](../UseCases/Dispatcher/UC-Create-Incident-From-Call.md), [UC-Attach-Call-To-Incident](../UseCases/Dispatcher/UC-Attach-Call-To-Incident.md), [UC-Detach-Call-From-Incident](../UseCases/Dispatcher/UC-Detach-Call-From-Incident.md), [UC-Assign-Units-To-Incident](../UseCases/Dispatcher/UC-Assign-Units-To-Incident.md), [UC-Dispatch-Units](../UseCases/Dispatcher/UC-Dispatch-Units.md), [UC-Set-Incident-State](../UseCases/Dispatcher/UC-Set-Incident-State.md), [UC-Close-Incident](../UseCases/Dispatcher/UC-Close-Incident.md)

---

## 1. Common Design Principles

### 1.1 REST and WebSocket Separation

Per the C4 containers specification:

- **REST**: all state-changing commands and all read queries
- **WebSocket**: all events and subscriptions

Dispatcher and Mobile Unit Clients fetch initial operational state via REST GET requests on connection, then receive incremental updates via WebSocket. On reconnection after failover (per ADR-0005), clients must re-authenticate and re-fetch state via REST — the server makes no guarantees about delivering missed events.

### 1.2 Authentication

All endpoints require JWT bearer authentication per the Security NFR. The exceptions are `/health` and `/auth/backchannel-logout`, which are unauthenticated (the latter is called by the OIDC provider, not by clients).

**Request header:**
```
Authorization: Bearer <jwt-token>
```

The JWT is obtained from the OIDC provider and contains:
- `sub`: user/device identifier
- `roles`: array of role claims (exactly one role per user)
- `exp`: token expiration timestamp
- `sid`: session identifier (used for back-channel logout)

**Additional claims by role:**
- `Unit` role: `unit_id` — Nano ID of the associated Unit
- `Station` role: `alert_target_id` — Nano ID of the associated AlertTarget (`station_alert_client` type)

The CAD Server validates JWTs by:
1. Fetching and caching the OIDC provider JWKS at startup
2. Verifying the JWT signature against the JWKS
3. Checking token expiration (`exp` claim)
4. Verifying the issuer (`iss` claim) matches the configured OIDC provider
5. Checking that the session (`sid` claim) has not been revoked via back-channel logout

### 1.3 Role-Based Access Control

Per the Security NFR:

| Role | Permitted API Access |
|------|---------------------|
| `Dispatcher` | All Dispatcher REST endpoints (read and write) + Dispatcher WebSocket |
| `Observer` | Read-only Dispatcher REST endpoints (GET only) + Dispatcher WebSocket |
| `Unit` | Mobile Unit REST endpoints + Mobile Unit WebSocket |
| `Station` | Station Alert WebSocket only |
| `Admin` | Admin REST endpoints only |

The server rejects requests with HTTP 403 Forbidden when the JWT role does not permit access to the requested endpoint.

### 1.4 Idempotency

Per the Availability NFR, all state-changing commands must be idempotent. Every REST request that mutates state must include an `X-Command-Id` header:

```
X-Command-Id: <uuid-v4>
```

The server stores processed command IDs with their outcomes. If the same command ID is received again (e.g., after a timeout and retry), the server returns the same response as the first successful execution without re-executing the command. Command ID entries expire after a configurable retention period.

Read-only requests (GET) do not require `X-Command-Id`.

### 1.5 Error Response Format

All error responses use a consistent JSON format:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable description",
    "details": { }
  },
  "timestamp": "2026-04-03T10:30:00Z",
  "path": "/api/v1/incidents/abc123/set-state"
}
```

**Standard error codes:**

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Request body or parameter validation failure |
| `INVALID_STATE_TRANSITION` | 409 | Target state is not reachable from the current state |
| `INVARIANT_VIOLATION` | 409 | A domain invariant prevents the operation |
| `RESOURCE_NOT_FOUND` | 404 | Referenced resource does not exist |
| `UNAUTHORIZED` | 401 | Missing or invalid JWT |
| `FORBIDDEN` | 403 | Valid JWT but insufficient role |
| `DUPLICATE_COMMAND` | 409 | Command with this ID already processed (different outcome) |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

### 1.6 API Versioning

All REST endpoints use URL path versioning at `/api/v1/`. Breaking changes require a new version path.

WebSocket connection URLs follow the same versioning: `/ws/v1/dispatcher`, `/ws/v1/unit`, `/ws/v1/station`.

### 1.7 Data Formats

- **Timestamps**: ISO 8601 in UTC, e.g. `"2026-04-03T10:30:00Z"`
- **Coordinates**: EPSG:4326, max 6 decimal places, Finland bounds (lat 58.84–70.09, lon 19.08–31.59)
- **Nano IDs**: 21-character URL-safe strings
- **Phone numbers**: E.164 format
- **Language codes**: ISO 639 (`fi`, `sv`, `en`, `sme`, `smn`, `sms`)
- **Content type**: `application/json` for all REST endpoints

---

## 2. Shared JSON Representations

### 2.1 Location

Location is represented as a type-discriminated object. The `type` field determines which additional fields are present.

**ExactAddress:**
```json
{
  "type": "exact_address",
  "municipality": { "code": "091", "name": { "fi": "Helsinki", "sv": "Helsingfors" } },
  "addressName": { "fi": "Mannerheimintie", "sv": "Mannerheimvägen" },
  "addressNumber": "1",
  "coordinates": { "latitude": 60.169857, "longitude": 24.938379 },
  "additionalDetails": "Entrance B"
}
```

**RoadIntersection:**
```json
{
  "type": "road_intersection",
  "municipality": { "code": "091", "name": { "fi": "Helsinki", "sv": "Helsingfors" } },
  "roadNameA": { "fi": "Mannerheimintie", "sv": "Mannerheimvägen" },
  "roadNameB": { "fi": "Kaivokatu", "sv": "Brunnsgatan" },
  "coordinates": { "latitude": 60.170800, "longitude": 24.940200 },
  "additionalDetails": null
}
```

**NamedPlace:**
```json
{
  "type": "named_place",
  "municipality": { "code": "091", "name": { "fi": "Helsinki", "sv": "Helsingfors" } },
  "name": { "fi": "Mannerheiminaukio", "sv": "Mannerheimplatsen" },
  "coordinates": { "latitude": 60.170500, "longitude": 24.939500 },
  "additionalDetails": null
}
```

**RelativeLocation:**
```json
{
  "type": "relative_location",
  "municipality": { "code": "091", "name": { "fi": "Helsinki", "sv": "Helsingfors" } },
  "referencePlace": { "fi": "Simonkenttä" },
  "additionalDetails": "200 m north of the park entrance",
  "coordinates": { "latitude": 60.168000, "longitude": 24.935000 }
}
```

**Validation rules** (per Domain: Location):
- `addressNumber`: max 30 characters
- `additionalDetails`: max 1000 characters; required for `relative_location`
- `coordinates` omitted when unknown; must not be inferred by the system
- Coordinates: max 6 decimal places, within Finland bounds

### 2.2 MultilingualName

All language versions present in the data are included; the server must not infer or omit translations.

```json
{ "fi": "Nimi", "sv": "Namn", "en": "Name" }
```

### 2.3 Staffing

```json
{
  "officers": 1,
  "subOfficers": 0,
  "crew": 3
}
```

All counts are non-negative integers.

---

## 3. REST API: Dispatcher Client

Base path: `/api/v1/`

Required role: `Dispatcher` (read/write) or `Observer` (GET only).

### 3.1 Call Management

#### Create Call

```
POST /api/v1/calls
X-Command-Id: <uuid>
```

Creates a new call in state `active`. The `receivingDispatcher` is set from the JWT `sub` claim.

**Request body:**
```json
{
  "callerName": "Matti Meikäläinen",
  "callerPhoneNumber": "+358401234567",
  "location": { ... },
  "description": "Smoke visible from the third floor window"
}
```

All fields optional; a call may be created with no details and filled in incrementally.

**Response 201 Created:**
```json
{
  "callId": "V1StGXR8_Z5jdHi6B-myT",
  "state": "active",
  "receivingDispatcher": "user-sub-here",
  "callStarted": "2026-04-03T10:30:00Z"
}
```

---

#### Update Call Details

```
PATCH /api/v1/calls/{callId}
X-Command-Id: <uuid>
```

Updates mutable call fields. Only fields present in the request body are changed; omitted fields are left unchanged. Only permitted while `state = active`.

**Request body (all fields optional):**
```json
{
  "callerName": "Matti Meikäläinen",
  "callerPhoneNumber": "+358401234567",
  "location": { ... },
  "description": "Smoke visible from the third floor window",
  "outcome": "caller_advised",
  "outcomeRationale": "Advised to ventilate; no fire detected"
}
```

`outcome` may be set or changed while the call is active. Setting `outcome` to `incident_created` or `attached_to_incident` directly is not permitted — those outcomes are set by specific commands.

**Response: 200 OK** (no body)

**Errors:**
- 404: call not found
- 409: call is in state `ended`

---

#### End Call

```
POST /api/v1/calls/{callId}/end
X-Command-Id: <uuid>
```

Transitions the call to state `ended`. Requires `outcome` to be set (either already on the call or provided in this request).

**Request body:**
```json
{
  "outcome": "caller_advised",
  "outcomeRationale": "Advised to ventilate; no fire detected"
}
```

`outcome` and `outcomeRationale` are required here only if not already set on the call. If the call already has an `outcome`, these fields may be omitted. Providing them overrides the existing values.

**Response: 200 OK** (no body)

**Errors:**
- 404: call not found
- 400: `outcome` not set and not provided
- 400: `outcomeRationale` required for the given outcome but missing
- 409: call already ended

---

#### Attach Call to Incident

```
POST /api/v1/calls/{callId}/attach-to-incident
X-Command-Id: <uuid>
```

Links the call to an existing incident. Sets `outcome = attached_to_incident` and `incidentId`. Creates an automatic `IncidentLogEntry` on the incident.

**Request body:**
```json
{
  "incidentId": "V1StGXR8_Z5jdHi6B-myT"
}
```

**Response: 200 OK** (no body)

**Errors:**
- 404: call or incident not found
- 409: call is in state `ended`
- 409: incident is in state `ended`
- 409: call already has `outcome = incident_created` (cannot attach calls linked by incident creation)

---

#### Detach Call from Incident

```
POST /api/v1/calls/{callId}/detach-from-incident
X-Command-Id: <uuid>
```

Detaches the call from its incident. Permitted only when `outcome = attached_to_incident`. Clears `incidentId` and `outcome`. Creates an automatic `IncidentLogEntry` on the incident.

**Request body:** empty (`{}`)

**Response: 200 OK** (no body)

**Errors:**
- 404: call not found
- 409: call is in state `ended`
- 409: call has `outcome != attached_to_incident`

---

#### List Active Calls

```
GET /api/v1/calls
```

Returns all calls in state `active`. Ended calls are excluded.

**Response 200 OK:**
```json
{
  "calls": [
    {
      "callId": "V1StGXR8_Z5jdHi6B-myT",
      "state": "active",
      "receivingDispatcher": "user-sub-here",
      "callStarted": "2026-04-03T10:30:00Z",
      "callerName": "Matti Meikäläinen",
      "callerPhoneNumber": "+358401234567",
      "location": { ... },
      "description": "Smoke visible",
      "outcome": null,
      "outcomeRationale": null,
      "incidentId": null
    }
  ]
}
```

---

#### Get Call

```
GET /api/v1/calls/{callId}
```

**Response 200 OK:** single call object as above.

**Errors:** 404 if not found.

---

### 3.2 Incident Management

#### Create Incident

```
POST /api/v1/incidents
X-Command-Id: <uuid>
```

Creates a new incident in state `new`. May optionally be created from an existing call, in which case the call's location is copied to the incident and the call's `outcome` is set to `incident_created`.

**Request body:**
```json
{
  "sourceCallId": "V1StGXR8_Z5jdHi6B-myT",
  "incidentType": "A31",
  "incidentPriority": "A",
  "location": { ... },
  "description": "Smoke visible from third floor"
}
```

All fields optional. When `sourceCallId` is provided, `location` is copied from the call if not supplied in the request. If `location` is provided in the request alongside `sourceCallId`, the request location takes precedence.

**Response 201 Created:**
```json
{
  "incidentId": "6byYFiLM_BkBZ5IFKhbRF"
}
```

**Errors:**
- 404: `sourceCallId` not found
- 409: call referenced by `sourceCallId` is in state `ended`
- 409: call referenced by `sourceCallId` already has `outcome = incident_created` or `outcome = attached_to_incident` (the call is already linked to an incident; detach it first)

---

#### Update Incident Details

```
PATCH /api/v1/incidents/{incidentId}
X-Command-Id: <uuid>
```

Updates mutable incident attributes. Only fields present in the request are changed. Not permitted when `state = ended`.

Each changed field produces an automatic `IncidentLogEntry`.

**Request body (all fields optional):**
```json
{
  "incidentType": "A31",
  "incidentPriority": "B",
  "location": { ... },
  "description": "Updated description"
}
```

`incidentType` is the IncidentType code string. Set to `null` to clear.

**Response: 200 OK** (no body)

**Errors:**
- 404: incident not found
- 409: incident is in state `ended`

---

#### Set Incident State

```
POST /api/v1/incidents/{incidentId}/set-state
X-Command-Id: <uuid>
```

Transitions the incident to a new state. This covers UC-Set-Incident-State (`queued`, `active`, `monitored`) and UC-Close-Incident (`ended`).

Allowed transitions per the domain model:
```
new → queued | active | monitored | ended
queued → active | monitored | ended
active → monitored | ended
monitored → queued | active | ended
ended → (terminal, no transitions)
```

**Request body:**
```json
{
  "state": "queued"
}
```

**Response: 200 OK** (no body)

**Errors:**
- 404: incident not found
- 409: `INVALID_STATE_TRANSITION` — transition not allowed
- 409: `INVARIANT_VIOLATION` — transition to `queued` or `active` requires `incidentType`, `incidentPriority`, and `location`
- 409: `INVARIANT_VIOLATION` — transition to `active` requires at least one assigned `IncidentUnit`
- 409: `INVARIANT_VIOLATION` — transition to `ended` requires all `IncidentUnit` records to have `unitUnassignedAt` set

---

#### Add Manual Log Entry

```
POST /api/v1/incidents/{incidentId}/log-entries
X-Command-Id: <uuid>
```

Appends a manual `IncidentLogEntry` with free-form text.

**Request body:**
```json
{
  "description": "Unit confirmed receipt over radio"
}
```

**Response: 200 OK** (no body)

**Errors:**
- 404: incident not found
- 409: incident is in state `ended`
- 400: `description` missing or empty; max 1000 characters

---

#### Assign Units to Incident

```
POST /api/v1/incidents/{incidentId}/units
X-Command-Id: <uuid>
```

Assigns one or more units to the incident. Handles three scenarios:
- Normal assignment from `available_over_radio` or `available_at_station`
- Reassignment from another incident (unit in `assigned_radio`, `assigned_station`, `dispatched`, `en_route`, or `on_scene`)
- Assignment with immediate state (`en_route` or `on_scene`) per UC-Assign-Units-To-Incident Alternative Flow B

The server detects the unit's current state and applies the appropriate transition automatically.

**Request body:**
```json
{
  "unitIds": ["unitId1", "unitId2"],
  "immediateState": "en_route"
}
```

`immediateState` is optional. When omitted, units are assigned to `assigned_radio` or `assigned_station` based on their current availability state. Valid values: `en_route`, `on_scene`.

For immediate state transitions, the server performs automatic intermediate transitions through `dispatching` and `dispatched` without waiting for Alert Target acknowledgment. The `unitDispatched` timestamp is set to the time the command is processed.

**Response: 200 OK** (no body)

**Errors:**
- 404: incident or any referenced unit not found
- 409: incident is in state `ended`
- 409: `INVARIANT_VIOLATION` — any unit in state `unavailable` or `inactive`

---

#### Unassign Unit from Incident

```
POST /api/v1/incidents/{incidentId}/units/{incidentUnitId}/unassign
X-Command-Id: <uuid>
```

Explicitly unassigns a unit from the incident. Permitted only when the unit is in state `assigned_radio` or `assigned_station`. Sets `unitUnassignedAt` on the `IncidentUnit` and transitions the unit back to `available_over_radio` or `available_at_station`.

**Request body:** empty (`{}`)

**Response: 200 OK** (no body)

**Errors:**
- 404: incident or `IncidentUnit` not found
- 409: unit is not in `assigned_radio` or `assigned_station` state

---

#### Dispatch New Units

```
POST /api/v1/incidents/{incidentId}/dispatch-new
X-Command-Id: <uuid>
```

Dispatches all units in state `assigned_radio` or `assigned_station`. For each unit, transitions to `dispatching` and sends alerts to active Alert Targets. Transitions the incident to `active` if not already. Corresponds to the Main Success Scenario of UC-Dispatch-Units.

**Request body:** empty (`{}`)

**Response: 200 OK** (no body)

**Errors:**
- 404: incident not found
- 409: incident is in state `ended`
- 400: incident is missing `incidentType`, `incidentPriority`, or `location`
- 400: no units in `assigned_radio` or `assigned_station` state

---

#### Dispatch Selected Units

```
POST /api/v1/incidents/{incidentId}/dispatch-selected
X-Command-Id: <uuid>
```

Dispatches or re-dispatches the specified units. For units in `assigned_radio` or `assigned_station`, transitions to `dispatching` and sends alerts. For units already in `dispatching`, `dispatched`, `en_route`, or `on_scene`, re-sends alerts without state changes. Corresponds to UC-Dispatch-Units Alternative Flow A.

**Request body:**
```json
{
  "unitIds": ["unitId1", "unitId2"]
}
```

**Response: 200 OK** (no body)

**Errors:**
- 404: incident or any referenced unit not found
- 409: incident is in state `ended`
- 400: incident is missing `incidentType`, `incidentPriority`, or `location` (for units that would transition to `dispatching`)

---

#### Manually Confirm Dispatch

```
POST /api/v1/incidents/{incidentId}/units/{incidentUnitId}/confirm-dispatch
X-Command-Id: <uuid>
```

Manually transitions a unit from `dispatching` to `dispatched` when Alert Target acknowledgment is unavailable. Sets `unitDispatched` on the `IncidentUnit` to the current timestamp. Creates an automatic `IncidentLogEntry` recording the manual confirmation. No alerts are sent.

**Request body:** empty (`{}`)

**Response: 200 OK** (no body)

**Errors:**
- 404: incident or `IncidentUnit` not found
- 409: unit is not in `dispatching` state

---

#### List Incidents

```
GET /api/v1/incidents?includeEnded=false
```

Returns incidents visible to dispatchers. By default excludes incidents in state `ended` (which are hidden in normal dispatcher views per the domain model).

**Query parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `includeEnded` | boolean | `false` | Include incidents in `ended` state (not yet archived) |

**Response 200 OK:**
```json
{
  "incidents": [
    {
      "incidentId": "6byYFiLM_BkBZ5IFKhbRF",
      "state": "active",
      "incidentCreated": "2026-04-03T10:28:00Z",
      "incidentEnded": null,
      "incidentType": "A31",
      "incidentPriority": "A",
      "location": { ... },
      "description": "Smoke visible from third floor",
      "callIds": ["V1StGXR8_Z5jdHi6B-myT"]
    }
  ]
}
```

This response provides a summary view. Full incident detail (units, log entries) is available via the individual incident endpoint.

---

#### Get Incident

```
GET /api/v1/incidents/{incidentId}
```

Returns full incident detail including units and log entries.

**Response 200 OK:**
```json
{
  "incidentId": "6byYFiLM_BkBZ5IFKhbRF",
  "state": "active",
  "incidentCreated": "2026-04-03T10:28:00Z",
  "incidentEnded": null,
  "incidentType": "A31",
  "incidentPriority": "A",
  "location": { ... },
  "description": "Smoke visible from third floor",
  "callIds": ["V1StGXR8_Z5jdHi6B-myT"],
  "units": [
    {
      "incidentUnitId": "abc123",
      "unitId": "unitId1",
      "callSign": "P111",
      "unitStaffing": { "officers": 1, "subOfficers": 0, "crew": 3 },
      "unitAssignedAt": "2026-04-03T10:29:00Z",
      "unitDispatchedAt": "2026-04-03T10:29:30Z",
      "unitEnRouteAt": null,
      "unitOnSceneAt": null,
      "unitAvailableAt": null,
      "unitBackAtStationAt": null,
      "unitUnassignedAt": null
    }
  ],
  "logEntries": [
    {
      "logEntryId": "def456",
      "logTimestamp": "2026-04-03T10:28:00Z",
      "dispatcher": null,
      "entryType": "automatic",
      "changeData": { "type": "incident_created" }
    },
    {
      "logEntryId": "def457",
      "logTimestamp": "2026-04-03T10:29:00Z",
      "dispatcher": "user-sub-here",
      "entryType": "automatic",
      "changeData": { "type": "unit_assigned", "unitId": "unitId1", "callSign": "P111" }
    }
  ]
}
```

**Errors:** 404 if not found.

---

### 3.3 Unit Status (Dispatcher View)

#### List Active Units with Status

```
GET /api/v1/units
```

Returns all active units together with their current `UnitStatus`.

**Response 200 OK:**
```json
{
  "units": [
    {
      "unitId": "unitId1",
      "callSign": "P111",
      "state": "active",
      "stationId": "stationId1",
      "status": {
        "state": "on_scene",
        "stateChangedAt": "2026-04-03T10:35:00Z",
        "staffing": { "officers": 1, "subOfficers": 0, "crew": 3 },
        "staffingChangedAt": "2026-04-03T08:00:00Z",
        "coordinates": { "latitude": 60.169857, "longitude": 24.938379 },
        "coordinatesChangedAt": "2026-04-03T10:36:00Z",
        "assignedToIncidentId": "6byYFiLM_BkBZ5IFKhbRF",
        "assignedToIncidentAt": "2026-04-03T10:29:00Z"
      }
    }
  ]
}
```

---

#### Set Unit Status (Dispatcher Manual Override)

```
POST /api/v1/units/{unitId}/status
X-Command-Id: <uuid>
```

Allows a dispatcher to manually set a unit's status when the Mobile Unit Client is unavailable (per the Availability NFR degraded mode). Permitted states: `unavailable`, `available_over_radio`, `available_at_station`.

The dispatcher cannot directly set `assigned_radio`, `assigned_station`, `dispatching`, or `dispatched` — those are controlled by the system. `en_route` and `on_scene` may only be set via the Mobile Unit Client.

**Request body:**
```json
{
  "state": "available_over_radio"
}
```

**Response: 200 OK** (no body)

**Errors:**
- 404: unit not found
- 400: `state` is not one of the dispatcher-settable values
- 409: `INVALID_STATE_TRANSITION` — transition not permitted from current state

---

### 3.4 Reference Data Reads

#### List Incident Types

```
GET /api/v1/incident-types
```

**Response 200 OK:**
```json
{
  "incidentTypes": [
    {
      "code": "A31",
      "description": { "fi": "Rakennuspalo", "sv": "Byggnadsbrand" }
    }
  ]
}
```

---

#### List Stations

```
GET /api/v1/stations
```

Returns all active stations.

**Response 200 OK:**
```json
{
  "stations": [
    {
      "stationId": "stationId1",
      "name": { "fi": "Hakunilan paloasema", "sv": "Håkansböle brandstation" },
      "location": { ... }
    }
  ]
}
```

---

## 4. REST API: Mobile Unit Client

Base path: `/api/v1/unit/`

Required role: `Unit`. The unit identity is taken from the `unit_id` JWT claim; it is not repeated in the URL.

### 4.1 Update Unit Status

```
POST /api/v1/unit/status
X-Command-Id: <uuid>
```

The unit updates its own operational status. Permitted states: `unavailable`, `available_over_radio`, `available_at_station`, `en_route`, `on_scene`.

The unit may not change its own status when in `assigned_radio`, `assigned_station`, or `dispatching` — those states are controlled exclusively by the system.

**Request body:**
```json
{
  "state": "en_route"
}
```

**Response: 200 OK** (no body)

**Errors:**
- 400: `state` is not a unit-settable value
- 409: `INVALID_STATE_TRANSITION`
- 409: unit is in a system-controlled state (`assigned_radio`, `assigned_station`, or `dispatching`)

---

### 4.2 Report Location

```
POST /api/v1/unit/location
X-Command-Id: <uuid>
```

Reports the unit's current GPS position. Per the Performance NFR, units send location updates every 10–20 seconds. Coordinate updates are not required to appear in the audit log.

**Request body:**
```json
{
  "coordinates": { "latitude": 60.169857, "longitude": 24.938379 }
}
```

**Response: 200 OK** (no body)

**Errors:**
- 400: coordinates outside Finland bounds or exceed 6 decimal places

---

### 4.3 Report Staffing

```
POST /api/v1/unit/staffing
X-Command-Id: <uuid>
```

Reports the unit's current crew composition.

**Request body:**
```json
{
  "staffing": { "officers": 1, "subOfficers": 0, "crew": 3 }
}
```

**Response: 200 OK** (no body)

**Errors:**
- 400: negative counts

---

### 4.4 Acknowledge Alert

```
POST /api/v1/unit/alerts/acknowledge
X-Command-Id: <uuid>
```

Acknowledges technical receipt and display of a dispatch alert. This is the `mobile_unit_client` Alert Target acknowledgment that triggers the `dispatching` → `dispatched` transition (if no other Alert Target has already acknowledged). Sets `unitDispatched` on the corresponding `IncidentUnit` if this is the first acknowledgment.

The Mobile Unit Client sends this request after successfully rendering the alert on screen.

**Request body:**
```json
{
  "incidentId": "6byYFiLM_BkBZ5IFKhbRF"
}
```

**Response: 200 OK** (no body)

**Errors:**
- 404: incident not found or unit not assigned to incident
- 409: unit not in `dispatching` or already in a post-dispatch state

---

## 5. REST API: Admin Client

Base path: `/api/v1/admin/`

Required role: `Admin`.

Reference data (Units, Stations, AlertTargets, IncidentTypes) is stored in local configuration files. Changes via the Admin API are written to those files and trigger an in-memory reload.

### 5.1 Unit Administration

#### List All Units

```
GET /api/v1/admin/units
```

Returns all units including inactive ones.

**Response 200 OK:**
```json
{
  "units": [
    {
      "unitId": "unitId1",
      "callSign": "P111",
      "state": "active",
      "stationId": "stationId1"
    }
  ]
}
```

---

#### Create Unit

```
POST /api/v1/admin/units
X-Command-Id: <uuid>
```

Creates a new unit and a corresponding `UnitStatus` (initial state: `unavailable`).

**Request body:**
```json
{
  "callSign": "P111",
  "stationId": "stationId1"
}
```

**Response 201 Created:**
```json
{ "unitId": "unitId1" }
```

**Errors:**
- 400: `callSign` format invalid per Callsign domain rules
- 409: `callSign` already in use

---

#### Update Unit

```
PUT /api/v1/admin/units/{unitId}
X-Command-Id: <uuid>
```

Updates the unit's call sign or station.

**Request body:**
```json
{
  "callSign": "P112",
  "stationId": "stationId2"
}
```

**Response: 200 OK** (no body)

**Errors:**
- 404: unit not found
- 409: new `callSign` already in use

---

#### Activate Unit

```
POST /api/v1/admin/units/{unitId}/activate
X-Command-Id: <uuid>
```

Transitions unit from `inactive` to `active`.

**Request body:** empty (`{}`)

**Response: 200 OK** (no body)

---

#### Deactivate Unit

```
POST /api/v1/admin/units/{unitId}/deactivate
X-Command-Id: <uuid>
```

Transitions unit from `active` to `inactive`. Not permitted while the unit's `UnitStatus` has `assignedToIncidentId` set.

**Request body:** empty (`{}`)

**Response: 200 OK** (no body)

**Errors:**
- 409: unit is currently assigned to an incident

---

#### Delete Unit

```
DELETE /api/v1/admin/units/{unitId}
X-Command-Id: <uuid>
```

Permanently removes the unit and its `UnitStatus`. Only permitted when the unit is `inactive` and not assigned to any incident.

**Response: 200 OK** (no body)

**Errors:**
- 409: unit is `active` or assigned to an incident

---

### 5.2 Station Administration

#### List All Stations

```
GET /api/v1/admin/stations
```

Returns all stations including inactive ones.

---

#### Create Station

```
POST /api/v1/admin/stations
X-Command-Id: <uuid>
```

**Request body:**
```json
{
  "name": { "fi": "Hakunilan paloasema", "sv": "Håkansböle brandstation" },
  "location": { ... }
}
```

**Response 201 Created:**
```json
{ "stationId": "stationId1" }
```

---

#### Update Station

```
PUT /api/v1/admin/stations/{stationId}
X-Command-Id: <uuid>
```

**Request body:** same as create. All fields required.

**Response: 200 OK** (no body)

---

#### Activate / Deactivate Station

```
POST /api/v1/admin/stations/{stationId}/activate
POST /api/v1/admin/stations/{stationId}/deactivate
```

**Request body:** empty (`{}`). **Response: 200 OK** (no body).

---

### 5.3 Alert Target Administration

#### List All Alert Targets

```
GET /api/v1/admin/alert-targets
```

Returns all alert targets. Configuration fields containing PII (email addresses, phone numbers) are included.

---

#### Create Alert Target

```
POST /api/v1/admin/alert-targets
X-Command-Id: <uuid>
```

**Request body:**
```json
{
  "name": "P111 Mobile Client",
  "targetType": "mobile_unit_client",
  "configuration": {
    "clientId": "V1StGXR8_Z5jdHi6B-myT"
  },
  "unitIds": ["unitId1"]
}
```

Configuration varies by `targetType`:
- `station_alert_client`: `{ "clientId": "<nano-id>" }`
- `mobile_unit_client`: `{ "clientId": "<nano-id>" }`
- `email`: `{ "emailAddresses": ["person@example.com"] }`
- `sms`: `{ "phoneNumbers": ["+358401234567"] }`

**Response 201 Created:**
```json
{ "alertTargetId": "targetId1" }
```

**Errors:**
- 400: `name` not unique
- 400: configuration invalid for `targetType`
- 400: email addresses or phone numbers malformed

---

#### Update Alert Target

```
PUT /api/v1/admin/alert-targets/{alertTargetId}
X-Command-Id: <uuid>
```

**Request body:** same as create. All fields required. **Response: 200 OK** (no body).

---

#### Activate / Deactivate Alert Target

```
POST /api/v1/admin/alert-targets/{alertTargetId}/activate
POST /api/v1/admin/alert-targets/{alertTargetId}/deactivate
```

**Request body:** empty (`{}`). **Response: 200 OK** (no body).

---

### 5.4 Incident Type Administration

#### List All Incident Types

```
GET /api/v1/admin/incident-types
```

---

#### Create Incident Type

```
POST /api/v1/admin/incident-types
X-Command-Id: <uuid>
```

**Request body:**
```json
{
  "code": "A31",
  "description": { "fi": "Rakennuspalo", "sv": "Byggnadsbrand" }
}
```

**Response 201 Created:** `{ "code": "A31" }`

**Errors:**
- 409: `code` already in use

---

#### Update Incident Type

```
PUT /api/v1/admin/incident-types/{code}
X-Command-Id: <uuid>
```

**Request body:** `{ "description": { ... } }` (code is immutable).

**Response: 200 OK** (no body).

---

#### Delete Incident Type

```
DELETE /api/v1/admin/incident-types/{code}
X-Command-Id: <uuid>
```

**Response: 200 OK** (no body).

---

### 5.5 Session Management

#### List Active Sessions

```
GET /api/v1/admin/sessions
```

Returns all currently authenticated WebSocket and REST sessions.

**Response 200 OK:**
```json
{
  "sessions": [
    {
      "sessionId": "oidc-session-id",
      "userId": "user-sub-here",
      "role": "Dispatcher",
      "connectedAt": "2026-04-03T08:00:00Z",
      "lastSeenAt": "2026-04-03T10:30:00Z",
      "clientType": "dispatcher_websocket"
    }
  ]
}
```

---

#### Terminate Session

```
DELETE /api/v1/admin/sessions/{sessionId}
X-Command-Id: <uuid>
```

Immediately terminates the session. All WebSocket connections associated with this session are closed. Subsequent REST requests using tokens from this session are rejected with HTTP 401. This event is logged per the Security NFR.

**Response: 200 OK** (no body).

**Errors:** 404 if session not found.

---

## 6. WebSocket API

### 6.1 Dispatcher Client WebSocket

**Connection URL:** `ws://cad-server/ws/v1/dispatcher`

Required role: `Dispatcher` or `Observer`.

The JWT is passed as a query parameter on the initial HTTP upgrade request:

```
GET /ws/v1/dispatcher?token=<jwt> HTTP/1.1
Upgrade: websocket
```

On successful upgrade, the server sends a `connected` message. The client then fetches current operational state via the REST GET endpoints (calls, incidents, units) and relies on this WebSocket for subsequent changes.

Per ADR-0005, on reconnect (e.g., after failover), the client must re-authenticate with a fresh JWT and re-fetch state via REST.

#### Message Envelope (Server → Client)

All messages from server to client use this envelope:

```json
{
  "type": "event.type",
  "sequenceNumber": 12345,
  "timestamp": "2026-04-03T10:30:00Z",
  "payload": { ... }
}
```

`sequenceNumber` is derived from the WAL and increases monotonically. Clients may use it to detect gaps on reconnect, but cannot request missed events — they must re-fetch state via REST.

#### Event Catalog

**Connection event:**

```json
{
  "type": "connected",
  "sequenceNumber": 12345,
  "timestamp": "2026-04-03T10:30:00Z",
  "payload": {
    "serverId": "cad-primary",
    "serverTime": "2026-04-03T10:30:00Z"
  }
}
```

**Call events:**

| Type | Trigger |
|------|---------|
| `call.created` | New call created |
| `call.updated` | Call details changed (caller info, location, description, outcome) |
| `call.ended` | Call transitioned to `ended` |
| `call.attached_to_incident` | Call attached to an incident |
| `call.detached_from_incident` | Call detached from its incident |

`call.created` payload:
```json
{
  "callId": "V1StGXR8_Z5jdHi6B-myT",
  "state": "active",
  "receivingDispatcher": "user-sub-here",
  "callStarted": "2026-04-03T10:30:00Z",
  "callerName": null,
  "callerPhoneNumber": null,
  "location": null,
  "description": null,
  "outcome": null,
  "outcomeRationale": null,
  "incidentId": null
}
```

`call.updated` and `call.ended` payloads include the complete updated call object (same structure).

`call.attached_to_incident` payload:
```json
{ "callId": "...", "incidentId": "..." }
```

`call.detached_from_incident` payload:
```json
{ "callId": "...", "formerIncidentId": "..." }
```

---

**Incident events:**

| Type | Trigger |
|------|---------|
| `incident.created` | New incident created |
| `incident.details_updated` | `incidentType`, `incidentPriority`, `location`, or `description` changed |
| `incident.state_changed` | Incident state transitioned |
| `incident.log_entry_added` | New log entry (automatic or manual) |
| `incident.unit_assigned` | Unit assigned (`IncidentUnit` created) |
| `incident.unit_unassigned` | Unit unassigned (`unitUnassignedAt` set on `IncidentUnit`) |

`incident.created` payload (summary — clients fetch full detail via REST if needed):
```json
{
  "incidentId": "6byYFiLM_BkBZ5IFKhbRF",
  "state": "new",
  "incidentCreated": "2026-04-03T10:28:00Z",
  "incidentType": null,
  "incidentPriority": null,
  "location": null,
  "description": null
}
```

`incident.state_changed` payload:
```json
{
  "incidentId": "6byYFiLM_BkBZ5IFKhbRF",
  "previousState": "new",
  "newState": "queued"
}
```

`incident.details_updated` payload:
```json
{
  "incidentId": "6byYFiLM_BkBZ5IFKhbRF",
  "incidentType": "A31",
  "incidentPriority": "A",
  "location": { ... },
  "description": "Updated description"
}
```

`incident.log_entry_added` payload:
```json
{
  "incidentId": "6byYFiLM_BkBZ5IFKhbRF",
  "logEntry": {
    "logEntryId": "def457",
    "logTimestamp": "2026-04-03T10:29:00Z",
    "dispatcher": "user-sub-here",
    "entryType": "automatic",
    "changeData": { "type": "unit_assigned", "unitId": "unitId1", "callSign": "P111" }
  }
}
```

`incident.unit_assigned` payload:
```json
{
  "incidentId": "6byYFiLM_BkBZ5IFKhbRF",
  "incidentUnit": {
    "incidentUnitId": "abc123",
    "unitId": "unitId1",
    "callSign": "P111",
    "unitAssignedAt": "2026-04-03T10:29:00Z"
  }
}
```

`incident.unit_unassigned` payload:
```json
{
  "incidentId": "6byYFiLM_BkBZ5IFKhbRF",
  "incidentUnitId": "abc123",
  "unitUnassignedAt": "2026-04-03T11:00:00Z"
}
```

---

**Unit Status events:**

| Type | Trigger |
|------|---------|
| `unit_status.state_changed` | Unit status state transitioned |
| `unit_status.location_updated` | Unit reported new coordinates |
| `unit_status.staffing_updated` | Unit reported new staffing |
| `unit_status.dispatch_timeout` | Unit remained in `dispatching` state beyond configured timeout |

`unit_status.state_changed` payload:
```json
{
  "unitId": "unitId1",
  "callSign": "P111",
  "previousState": "assigned_station",
  "newState": "dispatching",
  "stateChangedAt": "2026-04-03T10:29:30Z",
  "assignedToIncidentId": "6byYFiLM_BkBZ5IFKhbRF"
}
```

`unit_status.location_updated` payload:
```json
{
  "unitId": "unitId1",
  "coordinates": { "latitude": 60.169857, "longitude": 24.938379 },
  "coordinatesChangedAt": "2026-04-03T10:36:00Z"
}
```

`unit_status.staffing_updated` payload:
```json
{
  "unitId": "unitId1",
  "staffing": { "officers": 1, "subOfficers": 0, "crew": 3 },
  "staffingChangedAt": "2026-04-03T08:00:00Z"
}
```

`unit_status.dispatch_timeout` payload:
```json
{
  "unitId": "unitId1",
  "callSign": "P111",
  "incidentId": "6byYFiLM_BkBZ5IFKhbRF",
  "dispatchingStartedAt": "2026-04-03T10:29:30Z"
}
```

---

**IncidentUnit timestamp update events:**

These occur when `unitDispatchedAt`, `unitEnRouteAt`, `unitOnSceneAt`, `unitAvailableAt`, or `unitBackAtStationAt` are set on an `IncidentUnit`. They are not automatic log entries per the domain model, but dispatchers need to see them.

| Type | Trigger |
|------|---------|
| `incident.unit_timestamp_updated` | An `IncidentUnit` timestamp has been set |

Payload:
```json
{
  "incidentId": "6byYFiLM_BkBZ5IFKhbRF",
  "incidentUnitId": "abc123",
  "field": "unitDispatchedAt",
  "value": "2026-04-03T10:29:35Z"
}
```

---

### 6.2 Mobile Unit Client WebSocket

**Connection URL:** `ws://cad-server/ws/v1/unit?token=<jwt>`

Required role: `Unit`. The `unit_id` JWT claim identifies the unit.

The Mobile Unit WebSocket is **receive-only from the application perspective** — the server sends alerts to the unit, and the unit acknowledges via REST (Section 4.4). The WebSocket connection also uses standard WebSocket ping/pong frames for keep-alive.

#### Event Catalog (Server → Client)

**Alert event:**

Sent when a unit is dispatched to an incident (transition to `dispatching`). Also sent when the unit is re-dispatched (already past `dispatching`).

```json
{
  "type": "alert",
  "sequenceNumber": 12346,
  "timestamp": "2026-04-03T10:29:30Z",
  "payload": {
    "incidentId": "6byYFiLM_BkBZ5IFKhbRF",
    "incidentType": {
      "code": "A31",
      "description": { "fi": "Rakennuspalo", "sv": "Byggnadsbrand" }
    },
    "incidentPriority": "A",
    "location": { ... },
    "description": "Smoke visible from third floor",
    "assignedUnits": [
      { "callSign": "P111" },
      { "callSign": "P112" }
    ]
  }
}
```

**Connected event:**

```json
{
  "type": "connected",
  "sequenceNumber": 12345,
  "timestamp": "2026-04-03T10:30:00Z",
  "payload": { "serverTime": "2026-04-03T10:30:00Z" }
}
```

---

### 6.3 Station Alert Client WebSocket

**Connection URL:** `ws://cad-server/ws/v1/station?token=<jwt>`

Required role: `Station`. The `alert_target_id` JWT claim identifies the `station_alert_client` AlertTarget.

Unlike Mobile Unit Client, the Station Alert Client has no REST API. Acknowledgment is sent as a WebSocket message from client to server.

#### Messages: Server → Client

**Alert event:**

Sent when any unit associated with this Alert Target is dispatched.

```json
{
  "type": "alert",
  "sequenceNumber": 12346,
  "timestamp": "2026-04-03T10:29:30Z",
  "payload": {
    "alertId": "alert-nano-id",
    "incidentId": "6byYFiLM_BkBZ5IFKhbRF",
    "incidentType": {
      "code": "A31",
      "description": { "fi": "Rakennuspalo", "sv": "Byggnadsbrand" }
    },
    "incidentPriority": "A",
    "location": { ... },
    "description": "Smoke visible from third floor",
    "dispatchedUnits": [
      { "callSign": "P111" },
      { "callSign": "P112" }
    ]
  }
}
```

**Connected event:** same structure as Mobile Unit.

---

#### Messages: Client → Server

**Alert acknowledgment:**

Sent after the Station Alert Client has received and rendered the alert. The server uses this to trigger the `dispatching` → `dispatched` transition for units associated with this Alert Target (if this is the first acknowledgment).

```json
{
  "type": "alert.acknowledge",
  "payload": {
    "alertId": "alert-nano-id"
  }
}
```

The `alertId` is taken from the alert event payload. If the server cannot find a matching pending dispatch for this `alertId` and this Alert Target, the acknowledgment is silently ignored (idempotent).

---

## 7. OIDC Back-Channel Logout

```
POST /auth/backchannel-logout
```

Unauthenticated endpoint for OIDC provider callbacks. Accepts the OIDC back-channel logout token and invalidates all sessions associated with the `sid` claim.

**Request body:** `application/x-www-form-urlencoded` per OIDC spec.

**Response:** 200 OK on success; 400 if the logout token is malformed or invalid.

**Effect:**
1. All WebSocket connections with the matching `sid` are immediately closed.
2. Subsequent REST requests using JWT tokens with the same `sid` are rejected with HTTP 401.
3. The termination is logged per the Security NFR (`forced session termination` event).

---

## 8. Health Check

```
GET /health
```

No authentication required. This endpoint must not be exposed through the public reverse proxy (per Security NFR — internal infrastructure endpoint only).

**Response 200 OK:**
```json
{
  "status": "UP",
  "components": {
    "wal": { "status": "UP" },
    "archive": { "status": "UP" },
    "oidcJwks": { "status": "UP" }
  }
}
```

**Response 200 OK (degraded — archive unavailable):**
```json
{
  "status": "DEGRADED",
  "components": {
    "wal": { "status": "UP" },
    "archive": { "status": "DOWN", "error": "Connection refused" },
    "oidcJwks": { "status": "UP" }
  }
}
```

When only the archive component is unavailable, the server returns HTTP 200 with `status: "DEGRADED"` rather than 503. The server continues processing calls and incidents per the Availability NFR degraded mode, so it must not be taken out of service by a load balancer. HTTP 503 is reserved for failures that genuinely prevent the server from handling requests (e.g., WAL unavailable or JWKS unreachable).

---

## 9. Package Structure

Following the ports-and-adapters architecture of ADR-0008, the REST and WebSocket adapters form the primary adapter layer:

```
net.pkhapps.idispatchx.cad/
├── domain/           # (Defined in CAD-Server-Domain-Core.md)
├── port/             # (Defined in CAD-Server-Domain-Core.md)
├── application/      # (Defined in CAD-Server-Domain-Core.md)
└── adapter/
    ├── primary/
    │   ├── rest/
    │   │   ├── dispatcher/
    │   │   │   ├── CallController.java            # /api/v1/calls endpoints
    │   │   │   ├── IncidentController.java         # /api/v1/incidents endpoints
    │   │   │   ├── UnitController.java             # /api/v1/units endpoints
    │   │   │   └── ReferenceDataController.java    # /api/v1/incident-types, /api/v1/stations
    │   │   ├── unit/
    │   │   │   └── MobileUnitController.java       # /api/v1/unit/* endpoints
    │   │   ├── admin/
    │   │   │   ├── UnitAdminController.java        # /api/v1/admin/units
    │   │   │   ├── StationAdminController.java     # /api/v1/admin/stations
    │   │   │   ├── AlertTargetAdminController.java # /api/v1/admin/alert-targets
    │   │   │   ├── IncidentTypeAdminController.java# /api/v1/admin/incident-types
    │   │   │   └── SessionController.java          # /api/v1/admin/sessions
    │   │   └── shared/
    │   │       ├── CommandIdExtractor.java         # X-Command-Id header extraction
    │   │       ├── ErrorResponse.java              # Error response DTO
    │   │       └── GlobalExceptionHandler.java     # Centralized error mapping
    │   └── websocket/
    │       ├── dispatcher/
    │       │   ├── DispatcherWebSocketHandler.java # Javalin WS handler
    │       │   └── DispatcherSession.java          # Per-connection session state
    │       ├── unit/
    │       │   ├── MobileUnitWebSocketHandler.java
    │       │   └── MobileUnitSession.java
    │       └── station/
    │           ├── StationAlertWebSocketHandler.java
    │           └── StationAlertSession.java
    └── secondary/
        ├── wal/       # WalPort implementation
        ├── snapshot/  # SnapshotPort implementation
        ├── archive/   # ArchivePort implementation
        ├── alert/     # EmailPort, SmsPort, ClientAlertPort implementations
        └── clock/     # ClockPort implementation

# Supporting infrastructure in the adapter layer:
net.pkhapps.idispatchx.cad.adapter.broadcast/
├── EventBroadcaster.java          # Receives domain events, routes to subscribers
├── DispatcherBroadcastService.java# Sends events to all dispatcher WebSocket sessions
├── AlertBroadcastService.java     # Sends alerts to unit and station WS sessions
└── SessionRegistry.java           # Tracks active WebSocket sessions by role/id

net.pkhapps.idispatchx.cad.adapter.auth/
├── JwtAuthHandler.java            # Javalin before-handler for JWT validation
├── JwksClient.java                # OIDC JWKS fetching and caching
├── TokenValidator.java            # JWT signature, expiration, and claim validation
├── RevokedSessionStore.java       # In-memory store of revoked session IDs
└── BackChannelLogoutHandler.java  # /auth/backchannel-logout endpoint

net.pkhapps.idispatchx.cad/
└── Main.java                      # Javalin setup, dependency wiring, startup
```

---

## 10. Request Validation

### 10.1 Common Validation

| Rule | Constraint |
|------|------------|
| `X-Command-Id` header | Required on all state-changing requests; must be a valid UUID v4 |
| Nano IDs | 21-character URL-safe alphanumeric strings |
| Timestamps | ISO 8601 UTC; must not be in the future for client-supplied values |

### 10.2 Field-Level Validation

| Field | Constraint |
|-------|------------|
| `callerName` | Max 100 characters |
| `callerPhoneNumber` | E.164 format; max 15 digits |
| `description` (Call, Incident, log entry) | Max 1000 UTF-8 characters |
| `outcomeRationale` | Max 1000 UTF-8 characters |
| `additionalDetails` (Location) | Max 1000 characters |
| `addressNumber` | Max 30 characters |
| Coordinates (latitude) | 58.84° to 70.09°; max 6 decimal places |
| Coordinates (longitude) | 19.08° to 31.59°; max 6 decimal places |
| `callSign` | Alphanumeric + hyphens/spaces; max 20 characters; per SM 2021:35 formatting rules |

### 10.3 State-Specific Validation

Validated at the domain layer before executing the command:

- Incident state transitions: per domain model state machine
- Unit status transitions: per domain model state machine
- Incident requires `incidentType`, `incidentPriority`, `location` before `queued` or `active`
- Incident requires at least one `IncidentUnit` before `active`
- Incident requires all `IncidentUnit` records unassigned before `ended`
- Call requires `outcome` before `ended`
- Calls with `outcome = incident_created` cannot be attached/detached

---

## 11. Performance Considerations

### 11.1 Event Broadcasting

Per the Performance NFR, changes made by one dispatcher must appear on other dispatchers' screens within seconds. The `EventBroadcaster` receives domain events synchronously after WAL commit and dispatches them to WebSocket sessions. Broadcasting is asynchronous (non-blocking) with respect to the originating command handler.

### 11.2 Alert Delivery

Per the Performance NFR, alerts must reach Station Alert Clients and Mobile Unit Clients within seconds. Alert delivery via WebSocket is triggered immediately when the unit transitions to `dispatching`. Email and SMS delivery are asynchronous but initiated concurrently.

### 11.3 WAL Commit Latency

The WAL-before-state constraint (Performance NFR) means every state-changing REST response is delayed by WAL sync time. The WAL implementation must minimize sync latency (e.g., using `fsync` with appropriate buffer sizing and O_DIRECT).

### 11.4 WebSocket Connection Management

- Standard WebSocket ping/pong frames for keep-alive (no application-level heartbeat)
- Sessions registered in `SessionRegistry` for O(1) lookup by unit ID and alert target ID during alert routing
- Dispatcher sessions receive all events without filtering

---

## 12. Verification Strategy

### 12.1 Unit Tests

- **Request validation**: All field constraints enforced; invalid requests produce correct error codes
- **Role authorization**: Each endpoint rejects the wrong role with 403
- **Idempotency**: Same command ID returns same result; second execution not re-applied
- **Event broadcasting**: Domain events are translated to correct WebSocket message type and payload
- **Alert routing**: Alerts delivered to correct unit and station sessions based on AlertTarget configuration

### 12.2 Integration Tests

**Authentication:**
- Valid JWT with correct role: 200/201 OK
- Valid JWT with wrong role: 403 Forbidden
- Expired JWT: 401 Unauthorized
- Missing Authorization header: 401 Unauthorized
- Back-channel logout invalidates subsequent requests: 401 Unauthorized

**Dispatcher REST commands (all require Dispatcher role; Observer receives 403):**
- Create call → `call.created` event broadcast to dispatcher WebSocket
- End call → `call.updated` event with `state = ended`
- Create incident → `incident.created` event broadcast
- Assign units → `incident.unit_assigned` event; unit status state changed
- Dispatch new units → unit transitions to `dispatching`; alert sent via `ClientAlertPort`
- Manual dispatch confirm → unit transitions to `dispatched`; log entry created
- Set incident state with missing required fields → 409 with `INVARIANT_VIOLATION`
- Close incident with unassigned units → 409 with `INVARIANT_VIOLATION`

**WebSocket event delivery:**
- Dispatcher connects; fetches REST state; subsequent commands produce events on the WebSocket
- Two Dispatcher sessions: command from one produces events on both
- On disconnect and reconnect, events from between connections are not replayed
- Observer receives all events but command REST requests return 403

**Mobile Unit:**
- Unit reports status → `unit_status.state_changed` broadcast to dispatchers
- Unit reports location → `unit_status.location_updated` broadcast
- Alert received via WebSocket → unit acknowledges via REST → unit transitions to `dispatched`
- Unit cannot change status when in `assigned_radio`: 409

**Station Alert:**
- Alert WebSocket message delivered on dispatch
- Station acknowledges alert → `dispatching` → `dispatched` transition (if first ack)
- Acknowledgment for unknown `alertId` silently ignored

**Admin:**
- Create unit → unit visible in dispatcher `/api/v1/units`
- Deactivate unit while assigned to incident → 409
- Terminate session → WebSocket connection closed; subsequent REST request with same token → 401

**Dispatch timeout:**
- Unit in `dispatching` beyond configured timeout → `unit_status.dispatch_timeout` event; `IncidentLogEntry` created

### 12.3 Failover Tests

- Primary server stops; warm standby takes over; Dispatcher Client reconnects and re-fetches state; operational picture matches pre-failover state
- Command sent during failover (retried with same command ID): processed exactly once

### 12.4 Auditability Verification

Per the Security NFR, verify that audit logs capture:
- WebSocket connection and disconnection
- Successful JWT authentication
- Failed authentication attempts
- Forced session terminations (via Admin API)
- Each Dispatcher command (call, incident, unit operations)
- Each Admin command
- No PII in audit log entries
