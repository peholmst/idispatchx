# Enter Call Details — REST and WebSocket Endpoints Plan

This document covers the CAD Server REST API adapters and WebSocket broadcasting for the
UC-Enter-Call-Details use case.

Related plans:
- [Domain Plan](Enter-Call-Details-Domain-Plan.md) — domain model, commands, handlers, WAL
- [UI Plan](Enter-Call-Details-UI-Plan.md) — Dispatcher Client

## References

- [UC: Enter Call Details](../UseCases/Dispatcher/UC-Enter-Call-Details.md)
- [UC: Create Incident From Call](../UseCases/Dispatcher/UC-Create-Incident-From-Call.md)
- [UC: Attach Call To Incident](../UseCases/Dispatcher/UC-Attach-Call-To-Incident.md)
- [UC: Detach Call From Incident](../UseCases/Dispatcher/UC-Detach-Call-From-Incident.md)
- [Technical Design: CAD Server WebSocket/REST API](../TechnicalDesigns/CAD-Server-WebSocket-REST-API.md)
- [NFR: Availability](../NonFunctionalRequirements/Availability.md)
- [NFR: Performance](../NonFunctionalRequirements/Performance.md)
- [NFR: Security](../NonFunctionalRequirements/Security.md)

---

## Plan Overview

| Phase | Description | Tasks | Status |
|-------|-------------|-------|--------|
| 8 | REST API Endpoints (CAD Server) | 3 | Not Started |
| 9 | WebSocket Event Broadcasting (CAD Server) | 2 | Not Started |
| **Total** | | **5** | |

---

## Phase 8: REST API Endpoints (CAD Server)

REST adapters for Dispatcher Client call management. All follow the request/response format in
[Technical Design: CAD Server WebSocket/REST API](../TechnicalDesigns/CAD-Server-WebSocket-REST-API.md).

### Task 8.1: Call Controller

**Status:** Not Started

**Description:**
Implement `CallController` with all call management endpoints.

**Package:** `net.pkhapps.idispatchx.cad.adapter.primary.rest.dispatcher`

**Files to Create:**
- `CallController.java` — registers routes with Javalin
- `CallDtos.java` — request and response record classes

**ADR-0004 compliance:** The controller must support a configurable context path (reverse proxy / URL prefix). Do not hardcode `/api/v1` as a literal string; use whatever path-prefix configuration mechanism the Javalin bootstrap already establishes for the application.

**Command dispatch:** All mutating endpoints must call `IdempotentCommandDispatcher.dispatch(handler, command)` rather than invoking a `CommandHandler` directly. `CommandHandler.handle()` is package-private and cannot be called from this package. The dispatcher handles idempotency and audit logging transparently — controllers do not call `CommandLogPort` directly.

Each command record must be populated with `userId` (from the JWT) and `@Nullable ipAddress` (from the HTTP request) before dispatch.

**Endpoints:**

| Method | Path | Handler |
|--------|------|---------|
| `POST` | `/api/v1/calls` | `CreateCallCommand` |
| `PATCH` | `/api/v1/calls/{callId}` | `UpdateCallDetailsCommand` |
| `POST` | `/api/v1/calls/{callId}/end` | `EndCallCommand` |
| `POST` | `/api/v1/calls/{callId}/attach-to-incident` | `AttachCallToIncidentCommand` |
| `POST` | `/api/v1/calls/{callId}/detach-from-incident` | `DetachCallFromIncidentCommand` |
| `GET` | `/api/v1/calls` | List active calls |
| `GET` | `/api/v1/calls/{callId}` | Get single call |

**Acceptance Criteria:**
- [ ] All endpoints require `Dispatcher` role (write) or `Observer` role (GET only); reject with 403 otherwise
- [ ] All mutating endpoints require `X-Command-Id` header (UUID v4); reject with 400 if missing or malformed
- [ ] All mutating endpoints dispatch via `IdempotentCommandDispatcher`; no direct `CommandHandler.handle()` calls
- [ ] Each command is constructed with `userId` (from JWT) and `ipAddress` (from HTTP request, nullable)
- [ ] `POST /api/v1/calls`: all body fields optional; responds 201 with `callId`, `state`, `receivingDispatcher`, `callStarted`
- [ ] `PATCH /api/v1/calls/{callId}`: partial update; 200 on success; 404 if not found; 409 if `ENDED`
- [ ] `POST /api/v1/calls/{callId}/end`: 200 on success; 400 if outcome missing; 400 if rationale missing; 409 if already ended
- [ ] `POST /api/v1/calls/{callId}/attach-to-incident`: 200 on success; 404 if call or incident not found; 409 per domain rules
- [ ] `POST /api/v1/calls/{callId}/detach-from-incident`: 200 on success; 404/409 per domain rules
- [ ] `GET /api/v1/calls`: returns all active calls in the response format specified in the API design
- [ ] `GET /api/v1/calls/{callId}`: returns single call; 404 if not found
- [ ] Error responses use the standard error format
- [ ] Field-level validation matches `CAD-Server-WebSocket-REST-API.md` section 10
- [ ] Unit tests for all endpoints: valid requests, role enforcement, validation errors, domain errors

**Dependencies:** Domain Plan Tasks 6.1–6.5, Domain Plan Phase 5

---

### Task 8.2: Incident Controller (Partial)

**Status:** Not Started

**Description:**
Implement the portions of `IncidentController` needed for this UC: creating incidents from calls
and reading incident summaries (for call attachment UI and vicinity check).

**Package:** `net.pkhapps.idispatchx.cad.adapter.primary.rest.dispatcher`

**ADR-0004 compliance:** Same as Task 8.1 — use the application's configurable context path, not a hardcoded prefix.

**Command dispatch:** Same as Task 8.1 — mutating endpoints use `IdempotentCommandDispatcher.dispatch()`; commands are populated with `userId` and `@Nullable ipAddress` from the request.

**Files to Create:**
- `IncidentController.java` — registers routes with Javalin (partial; will be extended in future issues)
- `IncidentDtos.java` — request and response record classes

**Endpoints for this UC:**

| Method | Path | Handler |
|--------|------|---------|
| `POST` | `/api/v1/incidents` | Create incident (see note on `sourceCallId` below) |
| `GET` | `/api/v1/incidents` | List incidents (summary; `?includeEnded=false`) |
| `GET` | `/api/v1/incidents/{incidentId}` | Get full incident detail including `logEntries` and `callIds` |

**Note on `POST /api/v1/incidents` and `sourceCallId`:**
Per the API design, all fields including `sourceCallId` are **optional** in this endpoint.
When `sourceCallId` is present, the endpoint dispatches `CreateIncidentFromCallCommand`, and the
location is copied from the call if not supplied in the request body.
Standalone incident creation (without `sourceCallId`) is out of scope for this issue. In this
implementation, if `sourceCallId` is absent, respond with `501 Not Implemented` and an appropriate
error message rather than treating the field as required. This preserves the endpoint contract for
future implementation of UC-Create-Incident.

**Acceptance Criteria:**
- [ ] `POST /api/v1/incidents` dispatched via `IdempotentCommandDispatcher`; command populated with `userId` and `ipAddress`
- [ ] `POST /api/v1/incidents` with `sourceCallId`: creates incident from call; responds 201 with `incidentId`; 404 if call not found; 409 if call ended or already linked
- [ ] `POST /api/v1/incidents` without `sourceCallId`: responds 501 (not yet implemented); does **not** respond 400
- [ ] `GET /api/v1/incidents`: returns incident summaries with `callIds` field listing linked call IDs
- [ ] `GET /api/v1/incidents/{incidentId}`: returns full incident including `logEntries`
- [ ] Role enforcement: `Dispatcher`/`Observer` only
- [ ] `X-Command-Id` required on POST
- [ ] Unit tests for creation (with `sourceCallId`), the 501 path (without `sourceCallId`), and retrieval

**Dependencies:** Domain Plan Task 6.6, Domain Plan Phase 5

---

### Task 8.3: Request Validation and Error Handling

**Status:** Not Started

**Description:**
Extend the shared request validation infrastructure for the new endpoints. Ensure the global
exception handler maps domain exceptions to the correct HTTP status codes and error formats.

**Package:** `net.pkhapps.idispatchx.cad.adapter.primary.rest.shared`

**Files to Create or Modify:**
- `GlobalExceptionHandler.java` — add mappings for call/incident domain exceptions (entity not found → 404, invariant violation → 409, validation error → 400)
- `CommandIdExtractor.java` — verify already handles `X-Command-Id` extraction (extend if needed)

**Acceptance Criteria:**
- [ ] Domain `EntityNotFoundException` → 404 with `RESOURCE_NOT_FOUND` error code
- [ ] Domain `InvariantViolationException` → 409 with `INVARIANT_VIOLATION` error code
- [ ] Domain `ValidationException` → 400 with `VALIDATION_ERROR` error code
- [ ] All error responses use the standard envelope format from section 1.5 of the API design
- [ ] No stack traces or internal detail leaked in error responses

**Dependencies:** Tasks 8.1, 8.2

---

## Phase 9: WebSocket Event Broadcasting (CAD Server)

Broadcast domain events from call operations to all connected Dispatcher Client WebSocket sessions.

### Task 9.1: Event-to-WebSocket Message Translation

**Status:** Not Started

**Description:**
Extend `EventBroadcaster` to handle all new call and incident-related events, translating them into
the WebSocket message format defined in section 6.1 of the API design.

**Package:** `net.pkhapps.idispatchx.cad.adapter.broadcast`

**Files to Create or Modify:**
- `EventBroadcaster.java` — add dispatch cases for:
  - `CallCreatedEvent` → `call.created` message
  - `CallUpdatedEvent` → `call.updated` message
  - `CallEndedEvent` → `call.ended` message
  - `CallAttachedToIncidentEvent` → `call.attached_to_incident` message
  - `CallDetachedFromIncidentEvent` → `call.detached_from_incident` message
  - `IncidentCreatedEvent` → `incident.created` message
  - `IncidentLogEntryAddedEvent` → `incident.log_entry_added` message
- `DispatcherBroadcastService.java` — add methods for sending each new message type to all dispatcher WebSocket sessions

**WebSocket message payloads:** per section 6.1 of `CAD-Server-WebSocket-REST-API.md` exactly. For example:
- `call.created`: full call object (all fields, nulls explicit)
- `call.attached_to_incident`: `{ "callId": "...", "incidentId": "..." }`
- `call.detached_from_incident`: `{ "callId": "...", "formerIncidentId": "..." }`

**Acceptance Criteria:**
- [ ] Broadcasting is asynchronous and non-blocking with respect to the originating command handler
- [ ] Each event type produces exactly one WebSocket message with the correct type string and payload
- [ ] `sequenceNumber` in the envelope is derived from the WAL sequence number of the event
- [ ] Unit tests: mock `SessionRegistry`; verify each domain event produces the correct WS message

**Dependencies:** Domain Plan Tasks 4.3, 4.4

---

### Task 9.2: Dispatcher WebSocket Session for Call Events

**Status:** Not Started

**Description:**
Verify that `DispatcherWebSocketHandler` and `DispatcherSession` correctly receive and forward
call event messages to connected clients. No structural changes are expected — this task verifies
integration and adds test coverage.

**Package:** `net.pkhapps.idispatchx.cad.adapter.primary.websocket.dispatcher`

**Acceptance Criteria:**
- [ ] Call events sent via `DispatcherBroadcastService` reach all connected dispatcher sessions
- [ ] Observer sessions receive events but their command REST requests are rejected (403)
- [ ] Integration test: two dispatcher clients connected; command from one produces events on both

**Dependencies:** Task 9.1

---

## Execution Notes

Phases 8 and 9 depend on the domain plan being complete. They can be worked on in parallel with
each other once Domain Plan Phase 6 is done.
