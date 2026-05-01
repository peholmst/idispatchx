# Enter Call Details Implementation Plan

This document contains the implementation plan for UC-Enter-Call-Details and its directly referenced sub-use cases:
UC-Create-Incident-From-Call, UC-Attach-Call-To-Incident, and UC-Detach-Call-From-Incident.

## References

- [UC: Enter Call Details](../UseCases/Dispatcher/UC-Enter-Call-Details.md)
- [UC: Create Incident From Call](../UseCases/Dispatcher/UC-Create-Incident-From-Call.md)
- [UC: Attach Call To Incident](../UseCases/Dispatcher/UC-Attach-Call-To-Incident.md)
- [UC: Detach Call From Incident](../UseCases/Dispatcher/UC-Detach-Call-From-Incident.md)
- [Technical Design: CAD Server Domain Core](../TechnicalDesigns/CAD-Server-Domain-Core.md)
- [Technical Design: CAD Server WebSocket/REST API](../TechnicalDesigns/CAD-Server-WebSocket-REST-API.md)
- [Domain: Call](../Domain/Call.md)
- [Domain: Incident](../Domain/Incident.md)
- [Domain: Location](../Domain/Location.md)
- [Domain: Municipality](../Domain/Municipality.md)
- [Domain: MultilingualName](../Domain/MultilingualName.md)
- [NFR: Availability](../NonFunctionalRequirements/Availability.md)
- [NFR: Performance](../NonFunctionalRequirements/Performance.md)
- [NFR: Security](../NonFunctionalRequirements/Security.md)
- [C4: Containers](../C4/Containers.md)
- [UX Guidelines](../UXDesigns/Dispatcher-Client-UX-Guidelines.md)

---

## Out of Scope

The following are explicitly out of scope for this plan, to be addressed in future issues:

- Full Incident lifecycle: state transitions to `queued`, `active`, `monitored`, `ended`
- Unit assignment, dispatch, and incident log entries for unit events
- Incident creation without a source call (UC-Create-Incident)
- Incident state management UI in the Dispatcher Client
- Standalone incident creation in the Dispatcher Client header

---

## Plan Overview

| Phase | Description | Tasks | Status |
|-------|-------------|-------|--------|
| 1 | Domain Primitives and Location Value Objects | 2 | Not Started |
| 2 | Call Domain Model | 4 | Not Started |
| 3 | Incident Domain Foundation | 4 | Not Started |
| 4 | Commands and Domain Events | 4 | Not Started |
| 5 | In-Memory Repositories | 2 | Not Started |
| 6 | Command Handlers | 6 | Not Started |
| 7 | WAL Serialization and Replay | 2 | Not Started |
| 8 | REST API Endpoints (CAD Server) | 3 | Not Started |
| 9 | WebSocket Event Broadcasting (CAD Server) | 2 | Not Started |
| 10 | Dispatcher Client — API Client Layer | 3 | Not Started |
| 11 | Dispatcher Client — Call Management UI | 4 | Not Started |
| 12 | Dispatcher Client — Incident Integration | 2 | Not Started |
| **Total** | | **38** | |

---

## Phase 1: Domain Primitives and Location Value Objects

Foundation value objects required by both the Call and Incident domain models. All classes go in the CAD Server's `domain/model/shared/` and `domain/model/call/` packages.

### Task 1.1: Location Value Objects

**Status:** Not Started

**Description:**
Implement the four Location variants from [Domain: Location](../Domain/Location.md) as Java sealed interfaces/records. Also implement the Municipality and MultilingualName value objects they depend on.

**Package:** `net.pkhapps.idispatchx.cad.domain.model.shared`

**Files to Create:**
- `location/Location.java` — sealed interface with `ExactAddress`, `RoadIntersection`, `NamedPlace`, `RelativeLocation` record implementations
- `location/Coordinates.java` — record; validates Finland bounds (lat 58.84–70.09, lon 19.08–31.59) and max 6 decimal places
- `location/Municipality.java` — record; `code` (String) and `name` (MultilingualName)
- `location/MultilingualName.java` — record; wraps `Map<String, String>` (ISO 639 language codes → text, max 200 chars per value)

**Acceptance Criteria:**
- [ ] `Location` is a sealed interface; each variant is a Java record implementing it
- [ ] `ExactAddress`: required `municipality`, `addressName`; optional `addressNumber` (max 30 chars), `coordinates`, `additionalDetails` (max 1000 chars)
- [ ] `RoadIntersection`: required `municipality`, `roadNameA`, `roadNameB`; optional `coordinates`, `additionalDetails`
- [ ] `NamedPlace`: required `municipality`, `name`; optional `coordinates`, `additionalDetails`
- [ ] `RelativeLocation`: required `municipality`, `referencPlace`, `additionalDetails`; optional `coordinates`
- [ ] `Coordinates` rejects values outside Finland bounds and more than 6 decimal places
- [ ] `MultilingualName` rejects language codes that are not valid ISO 639 codes
- [ ] `MultilingualName` rejects values longer than 200 characters
- [ ] `Municipality` requires a non-null, non-empty `code` and a `name`
- [ ] Unit tests cover all variant constructors, required/optional field enforcement, and boundary validation

**Dependencies:** None

---

### Task 1.2: Call Domain Primitives

**Status:** Not Started

**Description:**
Implement self-validating domain primitives needed for the Call aggregate. These extend the catalog in the technical design.

**Package:** `net.pkhapps.idispatchx.cad.domain.model.shared`

**Files to Create:**
- `CallId.java` — record wrapping Nano ID string (21 URL-safe chars)
- `UserId.java` — record wrapping a string (OIDC `sub` claim); non-null, non-empty
- `CallerName.java` — record wrapping String; max 100 characters
- `CallerPhoneNumber.java` — record wrapping String; E.164 format as specified in [Domain: Call](../Domain/Call.md)
- `CallDescription.java` — record wrapping String; max 1000 UTF-8 characters
- `OutcomeRationale.java` — record wrapping String; max 1000 UTF-8 characters

**Acceptance Criteria:**
- [ ] `CallerPhoneNumber` accepts valid E.164 numbers with and without leading `+`; rejects non-digits, numbers exceeding 15 digits
- [ ] `CallerName` rejects values exceeding 100 characters
- [ ] `CallDescription` and `OutcomeRationale` reject values exceeding 1000 UTF-8 characters
- [ ] `CallId` and `UserId` reject null and empty strings
- [ ] Unit tests cover valid and invalid values for each primitive

**Dependencies:** None

---

## Phase 2: Call Domain Model

Implementation of the `Call` aggregate root, its state machine, and repository interface.

### Task 2.1: Call State and Outcome Enums

**Status:** Not Started

**Description:**
Define the `CallState` and `CallOutcome` enums as specified in [Domain: Call](../Domain/Call.md).

**Package:** `net.pkhapps.idispatchx.cad.domain.model.call`

**Files to Create:**
- `CallState.java` — enum: `ACTIVE`, `ENDED`
- `CallOutcome.java` — enum: `INCIDENT_CREATED`, `ATTACHED_TO_INCIDENT`, `CALLER_ADVISED`, `HOAX`, `ACCIDENTAL`, `OTHER_NO_ACTIONS_TAKEN`

**Acceptance Criteria:**
- [ ] Enum values match domain specification exactly
- [ ] No additional states or outcomes are added

**Dependencies:** None

---

### Task 2.2: Call State Machine

**Status:** Not Started

**Description:**
Implement the `CallStateMachine` as a static utility class, following the pattern in the technical design.

**Package:** `net.pkhapps.idispatchx.cad.domain.statemachine`

**Files to Create:**
- `CallStateMachine.java` — static utility; validates `ACTIVE → ENDED` transition; throws `IllegalStateException` for invalid transitions

**Acceptance Criteria:**
- [ ] Only `ACTIVE → ENDED` is accepted
- [ ] Any other transition throws `IllegalStateException`
- [ ] Unit tests cover the valid transition and all invalid transitions

**Dependencies:** Task 2.1

---

### Task 2.3: Call Entity

**Status:** Not Started

**Description:**
Implement the `Call` entity per [Domain: Call](../Domain/Call.md), following the entity design pattern from the technical design (WAL-before-state, prepare methods, `applyEvent`).

**Package:** `net.pkhapps.idispatchx.cad.domain.model.call`

**Files to Modify:**
- `Call.java` — replace placeholder with full implementation

**Call attributes (all as per domain spec):**
- `id: CallId` (required, immutable)
- `state: CallState`
- `receivingDispatcher: UserId` (required, immutable)
- `callStarted: Instant` (required, immutable)
- `callEnded: Instant` (optional; set when `state → ENDED`)
- `callerName: CallerName` (optional)
- `callerPhoneNumber: CallerPhoneNumber` (optional)
- `location: Location` (optional)
- `description: CallDescription` (optional)
- `outcome: CallOutcome` (optional while active; required on end)
- `outcomeRationale: OutcomeRationale` (required when outcome requires it)
- `incidentId: IncidentId` (optional)

**Prepare methods to implement:**
- `static CallCreationResult create(CallId, UserId, Instant, Optional<CallerName>, Optional<CallerPhoneNumber>, Optional<Location>, Optional<CallDescription>)` — factory; returns `(CallCreatedEvent, Call)`
- `PendingMutation<CallUpdatedEvent> prepareUpdate(Optional<CallerName>, Optional<CallerPhoneNumber>, Optional<Location>, Optional<CallDescription>, Optional<CallOutcome>, Optional<OutcomeRationale>)` — validates that `outcome` is not `INCIDENT_CREATED` or `ATTACHED_TO_INCIDENT` (those are set by specific commands); only sets fields that are present
- `PendingMutation<CallEndedEvent> prepareEnd(Optional<CallOutcome>, Optional<OutcomeRationale>)` — validates: call must be in `ACTIVE` state; `outcome` must be set (either already on call or provided); `outcomeRationale` must be present when outcome requires it
- `PendingMutation<CallAttachedToIncidentEvent> prepareAttachToIncident(IncidentId, Instant)` — validates: call in `ACTIVE` state; call does not already have `outcome = INCIDENT_CREATED`
- `PendingMutation<CallDetachedFromIncidentEvent> prepareDetachFromIncident()` — validates: call in `ACTIVE` state; `outcome = ATTACHED_TO_INCIDENT`
- `void applyEvent(DomainEvent)` — dispatches to private apply methods for each event type, for WAL replay

**Acceptance Criteria:**
- [ ] All attributes are present with correct types and mutability
- [ ] `outcome` may be set and changed while `ACTIVE`; is immutable after `ENDED`
- [ ] `incidentId` may be assigned before call ends; immutable after `ENDED`
- [ ] `prepareEnd` rejects calls already in `ENDED` state
- [ ] `prepareEnd` rejects when no outcome is set or available
- [ ] `prepareEnd` rejects when outcome requires rationale but none is provided
- [ ] `prepareAttachToIncident` rejects calls with `outcome = INCIDENT_CREATED`
- [ ] `prepareDetachFromIncident` rejects calls not in `ATTACHED_TO_INCIDENT` outcome
- [ ] `applyEvent` correctly reconstructs all state from events (for WAL replay)
- [ ] Unit tests cover all invariants, edge cases, and WAL replay correctness
- [ ] Entity is not internally synchronized (external synchronization via `EntityLockManager`)

**Dependencies:** Tasks 1.1, 1.2, 2.1, 2.2

---

### Task 2.4: Call Repository Interface

**Status:** Not Started

**Description:**
Define the `CallRepository` interface extending the generic `Repository` from the technical design, with call-specific query methods.

**Package:** `net.pkhapps.idispatchx.cad.domain.repository`

**Files to Create:**
- `CallRepository.java` — interface extending `Repository<Call, CallId>`; adds:
  - `Stream<Call> findActive()` — returns calls in state `ACTIVE`
  - `Stream<Call> findByIncidentId(IncidentId incidentId)` — returns calls linked to a given incident

**Acceptance Criteria:**
- [ ] Interface matches the methods described above
- [ ] Extends generic `Repository<Call, CallId>`

**Dependencies:** Task 2.3

---

## Phase 3: Incident Domain Foundation

The minimal Incident implementation needed to support call-to-incident linking. Full Incident lifecycle (unit assignment, state transitions beyond `new`, dispatch) is out of scope.

### Task 3.1: Incident Identifiers and State Enum

**Status:** Not Started

**Description:**
Define Incident identifiers and the state enum. Only states needed for this UC are required to be fully usable; other states must be defined but transition logic for them is out of scope.

**Package:** `net.pkhapps.idispatchx.cad.domain.model.incident`

**Files to Create:**
- `IncidentId.java` — record wrapping Nano ID string (21 URL-safe chars)
- `IncidentState.java` — enum: `NEW`, `QUEUED`, `ACTIVE`, `MONITORED`, `ENDED`
- `IncidentType.java` — record wrapping a String code (non-null, non-empty); represents the incident type code

**Acceptance Criteria:**
- [ ] All five states defined
- [ ] `IncidentId` validates Nano ID format

**Dependencies:** None

---

### Task 3.2: IncidentLogEntry

**Status:** Not Started

**Description:**
Implement the `IncidentLogEntry` value object per [Domain: Incident](../Domain/Incident.md).

**Package:** `net.pkhapps.idispatchx.cad.domain.model.incident`

**Files to Create:**
- `IncidentLogEntryId.java` — record wrapping Nano ID string
- `IncidentLogEntry.java` — sealed interface with two record implementations:
  - `AutomaticEntry(IncidentLogEntryId id, Instant logTimestamp, JsonNode changeData)` — system-generated; `changeData` is a structured JSON object
  - `ManualEntry(IncidentLogEntryId id, Instant logTimestamp, UserId dispatcher, String description)` — dispatcher-authored; `description` max 1000 UTF-8 chars

**Acceptance Criteria:**
- [ ] `AutomaticEntry` has no `dispatcher` field
- [ ] `ManualEntry` requires a `dispatcher` user ID and a non-empty `description`
- [ ] `description` max 1000 UTF-8 characters enforced
- [ ] Entries are immutable records

**Dependencies:** Task 1.2 (for `UserId`)

---

### Task 3.3: Incident Entity

**Status:** Not Started

**Description:**
Implement the `Incident` entity per [Domain: Incident](../Domain/Incident.md) to the extent needed for this UC: creation in state `NEW`, call linking, log entry creation. Full state transition logic is out of scope.

**Package:** `net.pkhapps.idispatchx.cad.domain.model.incident`

**Files to Modify:**
- `Incident.java` — replace placeholder with implementation

**Attributes (subset needed for this UC):**
- `id: IncidentId` (required, immutable)
- `state: IncidentState` (required; initially `NEW`)
- `incidentCreated: Instant` (required, immutable)
- `incidentType: IncidentType` (optional)
- `incidentPriority: String` (optional; values `A`, `B`, `C`, `D`, `N`)
- `location: Location` (optional)
- `description: String` (optional; max 1000 UTF-8 chars)
- `logEntries: List<IncidentLogEntry>` (append-only)

**Prepare methods to implement:**
- `static IncidentCreationResult create(IncidentId, Instant, Optional<IncidentType>, Optional<String>, Optional<Location>, Optional<String>)` — factory; creates incident in state `NEW`; optionally links a source call (call `outcome` and `incidentId` are set in `AttachCallToIncidentCommandHandler`, not here)
- `PendingMutation<IncidentLogEntryAddedEvent> prepareAddCallLinkedLogEntry(IncidentLogEntryId, Instant, CallId)` — adds automatic log entry recording call linkage; validates incident is not in `ENDED` state
- `PendingMutation<IncidentLogEntryAddedEvent> prepareAddCallDetachedLogEntry(IncidentLogEntryId, Instant, CallId)` — adds automatic log entry recording call detachment
- `void applyEvent(DomainEvent)` — for WAL replay

**Acceptance Criteria:**
- [ ] Incident is created in state `NEW` with correct attributes
- [ ] `prepareAddCallLinkedLogEntry` rejects incidents in `ENDED` state (per domain: cannot link calls to ended incidents)
- [ ] `logEntries` is append-only; entries cannot be modified or removed
- [ ] `applyEvent` correctly reconstructs state from events
- [ ] Unit tests cover creation, log entry addition, invariants

**Dependencies:** Tasks 1.1, 1.2, 3.1, 3.2

---

### Task 3.4: Incident Repository Interface

**Status:** Not Started

**Description:**
Define the `IncidentRepository` interface with the minimal query methods needed for this UC.

**Package:** `net.pkhapps.idispatchx.cad.domain.repository`

**Files to Create:**
- `IncidentRepository.java` — interface extending `Repository<Incident, IncidentId>`; adds:
  - `Stream<Incident> findActive()` — returns incidents not in state `ENDED` (for vicinity check and attach validation)

**Acceptance Criteria:**
- [ ] Interface compiles and matches described methods

**Dependencies:** Task 3.3

---

## Phase 4: Commands and Domain Events

Command objects and domain events for all call operations and incident creation from call.

### Task 4.1: Call Commands

**Status:** Not Started

**Description:**
Define command objects for all call operations.

**Package:** `net.pkhapps.idispatchx.cad.domain.command`

**Files to Create:**
- `CreateCallCommand.java` — record; `commandId`, `issuedBy` (UserId), `callerName?`, `callerPhoneNumber?`, `location?`, `description?`
- `UpdateCallDetailsCommand.java` — record; `commandId`, `issuedBy`, `callId`, `callerName?`, `callerPhoneNumber?`, `location?`, `description?`, `outcome?`, `outcomeRationale?`
- `EndCallCommand.java` — record; `commandId`, `issuedBy`, `callId`, `outcome?`, `outcomeRationale?`
- `AttachCallToIncidentCommand.java` — record; `commandId`, `issuedBy`, `callId`, `incidentId`
- `DetachCallFromIncidentCommand.java` — record; `commandId`, `issuedBy`, `callId`

**Acceptance Criteria:**
- [ ] Each command implements the `Command` sealed interface (`commandId()`, `issuedBy()`)
- [ ] Optional fields use `Optional<T>` or `@Nullable T` consistently with existing commands

**Dependencies:** Tasks 1.2, 2.3, 3.1

---

### Task 4.2: Incident Creation Command

**Status:** Not Started

**Description:**
Define the command for creating an incident from a call.

**Package:** `net.pkhapps.idispatchx.cad.domain.command`

**Files to Create:**
- `CreateIncidentFromCallCommand.java` — record; `commandId`, `issuedBy`, `sourceCallId`, `incidentType?`, `incidentPriority?`, `location?`, `description?`

**Acceptance Criteria:**
- [ ] Implements `Command` interface
- [ ] `sourceCallId` is required (non-null)

**Dependencies:** Tasks 1.2, 2.3, 3.1

---

### Task 4.3: Call Domain Events

**Status:** Not Started

**Description:**
Define domain events for all call state changes. All events implement `DomainEvent` and are written to WAL.

**Package:** `net.pkhapps.idispatchx.cad.domain.event`

**Files to Create:**
- `CallCreatedEvent.java` — record; `eventId`, `timestamp`, `causedBy`, `callId`, `receivingDispatcher`, `callStarted`, `callerName?`, `callerPhoneNumber?`, `location?`, `description?`
- `CallUpdatedEvent.java` — record; `eventId`, `timestamp`, `causedBy`, `callId`, `callerName?`, `callerPhoneNumber?`, `location?`, `description?`, `outcome?`, `outcomeRationale?` — includes all mutable fields that may have changed
- `CallEndedEvent.java` — record; `eventId`, `timestamp`, `causedBy`, `callId`, `callEnded`, `outcome`, `outcomeRationale?`, `incidentId?`
- `CallAttachedToIncidentEvent.java` — record; `eventId`, `timestamp`, `causedBy`, `callId`, `incidentId`
- `CallDetachedFromIncidentEvent.java` — record; `eventId`, `timestamp`, `causedBy`, `callId`, `formerIncidentId`

**Acceptance Criteria:**
- [ ] Each event implements the `DomainEvent` sealed interface
- [ ] Events are immutable records
- [ ] All optional fields use `Optional<T>` or `@Nullable T` consistently

**Dependencies:** Tasks 1.1, 1.2, 2.1

---

### Task 4.4: Incident Domain Events

**Status:** Not Started

**Description:**
Define the domain events for incident creation and log entry addition.

**Package:** `net.pkhapps.idispatchx.cad.domain.event`

**Files to Create:**
- `IncidentCreatedEvent.java` — record; `eventId`, `timestamp`, `causedBy`, `incidentId`, `incidentCreated`, `sourceCallId?`, `incidentType?`, `incidentPriority?`, `location?`, `description?`
- `IncidentLogEntryAddedEvent.java` — record; `eventId`, `timestamp`, `causedBy`, `incidentId`, `logEntry` (a snapshot of the complete `IncidentLogEntry`)

**Acceptance Criteria:**
- [ ] Each event implements `DomainEvent`
- [ ] Events are immutable records

**Dependencies:** Tasks 1.1, 3.1, 3.2

---

## Phase 5: In-Memory Repositories

In-memory implementations using `ConcurrentHashMap` for thread-safe reads. Writes are protected by `EntityLockManager`.

### Task 5.1: In-Memory Call Repository

**Status:** Not Started

**Description:**
Implement `InMemoryCallRepository`.

**Package:** `net.pkhapps.idispatchx.cad.domain.repository`

**Files to Create:**
- `InMemoryCallRepository.java` — uses `ConcurrentHashMap<CallId, Call>`; implements all methods from `CallRepository`

**Acceptance Criteria:**
- [ ] `findAll()` and `findActive()` return correct results
- [ ] `findByIncidentId()` filters by `incidentId` attribute
- [ ] Thread-safe reads via `ConcurrentHashMap`; writes protected by caller's lock
- [ ] Unit tests for all query methods

**Dependencies:** Task 2.4

---

### Task 5.2: In-Memory Incident Repository (Minimal)

**Status:** Not Started

**Description:**
Implement `InMemoryIncidentRepository` for the operations needed in this UC.

**Package:** `net.pkhapps.idispatchx.cad.domain.repository`

**Files to Create:**
- `InMemoryIncidentRepository.java` — uses `ConcurrentHashMap<IncidentId, Incident>`; implements all methods from `IncidentRepository`

**Acceptance Criteria:**
- [ ] `findActive()` returns all incidents not in `ENDED` state
- [ ] Thread-safe reads; writes protected by caller's lock
- [ ] Unit tests for all query methods

**Dependencies:** Task 3.4

---

## Phase 6: Command Handlers

One handler per command, following the WAL-before-state pattern from the technical design. All handlers extend `CommandHandler<C, R>` and use `EntityLockManager`.

### Task 6.1: Create Call Command Handler

**Status:** Not Started

**Description:**
Handle `CreateCallCommand`: generate `CallId`, create `Call` entity, write `CallCreatedEvent` to WAL, add call to repository, return call ID.

**Package:** `net.pkhapps.idispatchx.cad.application.handler`

**Files to Create:**
- `CreateCallCommandHandler.java`

**Acceptance Criteria:**
- [ ] Generates a unique `CallId` using Nano ID
- [ ] `callStarted` is obtained from `ClockPort`
- [ ] `CallCreatedEvent` is written to WAL before the call is added to the repository
- [ ] Call is added to `CallRepository` after WAL write confirms
- [ ] Returns the `CallId` of the new call
- [ ] Unit tests: mock `WalPort`, verify event written before repository mutation

**Dependencies:** Tasks 2.3, 2.4, 4.1, 5.1

---

### Task 6.2: Update Call Details Command Handler

**Status:** Not Started

**Description:**
Handle `UpdateCallDetailsCommand`: locate call, call `prepareUpdate`, write `CallUpdatedEvent`, apply mutation.

**Package:** `net.pkhapps.idispatchx.cad.application.handler`

**Files to Create:**
- `UpdateCallDetailsCommandHandler.java`

**Acceptance Criteria:**
- [ ] Returns `404` equivalent if call not found
- [ ] Rejects if call is in `ENDED` state (409)
- [ ] Rejects if `outcome` is `INCIDENT_CREATED` or `ATTACHED_TO_INCIDENT` (use specific commands for those)
- [ ] Only updates fields present in the command; absent fields are unchanged
- [ ] `CallUpdatedEvent` written to WAL before mutation
- [ ] Unit tests: mock `WalPort`, verify partial updates, validate rejections

**Dependencies:** Tasks 2.3, 4.1, 5.1, 6.1

---

### Task 6.3: End Call Command Handler

**Status:** Not Started

**Description:**
Handle `EndCallCommand`: locate call, call `prepareEnd`, write `CallEndedEvent`, apply mutation, schedule archival if call is not linked to an incident.

**Package:** `net.pkhapps.idispatchx.cad.application.handler`

**Files to Create:**
- `EndCallCommandHandler.java`

**Archival scheduling:** After successful WAL write and mutation, if `call.incidentId` is absent, invoke `ArchivePort.scheduleUnlinkedCallArchival(callId)`. Per the Availability NFR, if `ArchivePort` is unavailable, this must not prevent the call from being ended — log the failure and continue.

**Acceptance Criteria:**
- [ ] Returns `404` equivalent if call not found
- [ ] Rejects if call already in `ENDED` state (409)
- [ ] Rejects if no outcome is set and none provided (400)
- [ ] Rejects if rationale required but missing (400)
- [ ] `callEnded` timestamp obtained from `ClockPort`
- [ ] `CallEndedEvent` written to WAL before mutation
- [ ] Archival scheduling invoked for unlinked calls; failure is logged and does not fail the command
- [ ] Unit tests cover all validation cases and archival trigger

**Dependencies:** Tasks 2.3, 4.3, 5.1, 6.1

---

### Task 6.4: Attach Call To Incident Command Handler

**Status:** Not Started

**Description:**
Handle `AttachCallToIncidentCommand`: locate call and incident, validate preconditions, write events atomically (call update + incident log entry), apply mutations.

Per the technical design, this is a cross-aggregate operation. The handler must acquire locks on both the call and the incident (in a deterministic order via `EntityLockManager`), then write both events as a batch to the WAL.

**Package:** `net.pkhapps.idispatchx.cad.application.handler`

**Files to Create:**
- `AttachCallToIncidentCommandHandler.java`

**Acceptance Criteria:**
- [ ] Returns `404` if call or incident not found
- [ ] Rejects if call is in `ENDED` state (409)
- [ ] Rejects if incident is in `ENDED` state (409)
- [ ] Rejects if call already has `outcome = INCIDENT_CREATED` (409)
- [ ] Both `CallAttachedToIncidentEvent` and `IncidentLogEntryAddedEvent` written as a batch to WAL before either mutation is applied
- [ ] Locks on call and incident acquired in deterministic order (sorted by ID string to prevent deadlocks)
- [ ] Unit tests: verify both events in WAL batch, verify rejections

**Dependencies:** Tasks 2.3, 3.3, 4.3, 4.4, 5.1, 5.2

---

### Task 6.5: Detach Call From Incident Command Handler

**Status:** Not Started

**Description:**
Handle `DetachCallFromIncidentCommand`: locate call, locate incident (via call's `incidentId`), validate preconditions, write events as batch, apply mutations.

**Package:** `net.pkhapps.idispatchx.cad.application.handler`

**Files to Create:**
- `DetachCallFromIncidentCommandHandler.java`

**Acceptance Criteria:**
- [ ] Returns `404` if call not found
- [ ] Rejects if call is in `ENDED` state (409)
- [ ] Rejects if call's `outcome != ATTACHED_TO_INCIDENT` (409)
- [ ] Both `CallDetachedFromIncidentEvent` and `IncidentLogEntryAddedEvent` written as a batch before mutations
- [ ] After mutation: `call.incidentId` is cleared and `call.outcome` is cleared
- [ ] Locks acquired in deterministic order on both aggregates
- [ ] Unit tests verify all rejections and WAL batch write

**Dependencies:** Tasks 2.3, 3.3, 4.3, 4.4, 5.1, 5.2

---

### Task 6.6: Create Incident From Call Command Handler

**Status:** Not Started

**Description:**
Handle `CreateIncidentFromCallCommand`: locate source call, validate preconditions, create new `Incident`, link call to incident, write events as batch (IncidentCreated + CallUpdated + IncidentLogEntryAdded), apply mutations.

**Package:** `net.pkhapps.idispatchx.cad.application.handler`

**Files to Create:**
- `CreateIncidentFromCallCommandHandler.java`

**Detailed behavior:**
1. Locate source call; reject if not found (404) or `ENDED` (409)
2. Reject if call already has `outcome = INCIDENT_CREATED` or `outcome = ATTACHED_TO_INCIDENT` (409)
3. Generate new `IncidentId` using Nano ID
4. If `location` is absent in command and call has a `location`, copy call location to incident (independent copy)
5. Write as a batch: `IncidentCreatedEvent`, `CallUpdatedEvent` (setting `outcome = INCIDENT_CREATED`, `incidentId`), `IncidentLogEntryAddedEvent` (recording call linkage)
6. Apply mutations: add incident to `IncidentRepository`, update call in `CallRepository`
7. Return new `IncidentId`

**Acceptance Criteria:**
- [ ] Returns `404` if source call not found
- [ ] Rejects if call is in `ENDED` state (409)
- [ ] Rejects if call already linked to an incident (409)
- [ ] `incidentCreated` timestamp from `ClockPort`
- [ ] Call location is copied to incident when no location provided in command (independent copy — same value, no reference sharing)
- [ ] All three events written as a single WAL batch before any mutation
- [ ] Locks acquired on source call (incident is new, no pre-existing lock needed)
- [ ] Unit tests verify all validation cases, location copying, WAL batch, and returned incident ID

**Dependencies:** Tasks 2.3, 3.3, 4.2, 4.3, 4.4, 5.1, 5.2

---

## Phase 7: WAL Serialization and Replay

JSON serialization/deserialization for all new domain events, and WAL replay handlers.

### Task 7.1: Event Serialization

**Status:** Not Started

**Description:**
Implement Jackson-based JSON serialization and deserialization for all new domain events. Each event type needs a unique `type` discriminator field in the JSON envelope. Location variants must also be serialized using their `type` discriminator (per the REST API specification's `exact_address`, `road_intersection`, `named_place`, `relative_location` values).

**Package:** `net.pkhapps.idispatchx.cad.adapter.secondary.wal`

**Files to Create or Modify:**
- `EventSerializer.java` — serialize any `DomainEvent` to JSON; add handling for new event types
- `EventDeserializer.java` — deserialize JSON to the correct `DomainEvent` subtype; add handling for new event types
- `LocationSerializer.java` — serialize/deserialize `Location` sealed interface with type discriminator

**Acceptance Criteria:**
- [ ] Each event type has a unique `type` discriminator value in the JSON
- [ ] All optional fields serialize as `null` (not omitted) for deterministic round-trips
- [ ] Location variants serialize with `"type"` field matching REST API format
- [ ] Round-trip tests: serialize event → deserialize → compare with original
- [ ] Integration test: write event to WAL, replay WAL, verify event received

**Dependencies:** Tasks 4.3, 4.4

---

### Task 7.2: WAL Replay Handlers

**Status:** Not Started

**Description:**
Extend the WAL replay service (in `application/replay/`) to handle all new domain events during startup. Each event handler reconstructs the in-memory state of calls and incidents.

**Package:** `net.pkhapps.idispatchx.cad.application.replay`

**Files to Modify:**
- `WalReplayService.java` (or the equivalent replay dispatch class) — add dispatch cases for:
  - `CallCreatedEvent` → create `Call`, add to `CallRepository`
  - `CallUpdatedEvent` → find call, call `applyEvent`
  - `CallEndedEvent` → find call, call `applyEvent`
  - `CallAttachedToIncidentEvent` → find call, call `applyEvent`
  - `CallDetachedFromIncidentEvent` → find call, call `applyEvent`
  - `IncidentCreatedEvent` → create `Incident`, add to `IncidentRepository`
  - `IncidentLogEntryAddedEvent` → find incident, call `applyEvent`

**Acceptance Criteria:**
- [ ] Replay of a sequence of events produces identical in-memory state to live execution
- [ ] Replay handles events in correct order (WAL sequence guarantees)
- [ ] Integration tests: write multiple events, replay full WAL, verify final state of calls and incidents matches expected

**Dependencies:** Tasks 5.1, 5.2, 7.1

---

## Phase 8: REST API Endpoints (CAD Server)

REST adapters for Dispatcher Client call management. All follow the request/response format in [Technical Design: CAD Server WebSocket/REST API](../TechnicalDesigns/CAD-Server-WebSocket-REST-API.md).

### Task 8.1: Call Controller

**Status:** Not Started

**Description:**
Implement `CallController` with all call management endpoints.

**Package:** `net.pkhapps.idispatchx.cad.adapter.primary.rest.dispatcher`

**Files to Create:**
- `CallController.java` — registers routes with Javalin
- `CallDtos.java` — request and response record classes

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

**Dependencies:** Tasks 6.1–6.5, Phase 5

---

### Task 8.2: Incident Controller (Partial)

**Status:** Not Started

**Description:**
Implement the portions of `IncidentController` needed for this UC: creating incidents from calls and reading incident summaries (for call attachment UI and vicinity check).

**Package:** `net.pkhapps.idispatchx.cad.adapter.primary.rest.dispatcher`

**Files to Create:**
- `IncidentController.java` — registers routes with Javalin (placeholder; will be extended in future issues)
- `IncidentDtos.java` — request and response record classes

**Endpoints for this UC:**

| Method | Path | Handler |
|--------|------|---------|
| `POST` | `/api/v1/incidents` | `CreateIncidentFromCallCommand` (requires `sourceCallId`; other fields optional) |
| `GET` | `/api/v1/incidents` | List incidents (summary; `?includeEnded=false`) |
| `GET` | `/api/v1/incidents/{incidentId}` | Get full incident detail including `logEntries` and `callIds` |

**Acceptance Criteria:**
- [ ] `POST /api/v1/incidents` with `sourceCallId`: creates incident from call; responds 201 with `incidentId`; 404 if call not found; 409 if call ended or already linked
- [ ] `GET /api/v1/incidents`: returns incident summaries with `callIds` field listing linked call IDs
- [ ] `GET /api/v1/incidents/{incidentId}`: returns full incident including `logEntries`
- [ ] Role enforcement: `Dispatcher`/`Observer` only
- [ ] `X-Command-Id` required on POST
- [ ] Unit tests for creation and retrieval

**Dependencies:** Tasks 6.6, Phase 5

---

### Task 8.3: Request Validation and Error Handling

**Status:** Not Started

**Description:**
Extend the shared request validation infrastructure for the new endpoints. Ensure the global exception handler maps domain exceptions to the correct HTTP status codes and error formats.

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
Extend `EventBroadcaster` to handle all new call and incident-related events, translating them into the WebSocket message format defined in section 6.1 of the API design.

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

**Dependencies:** Tasks 4.3, 4.4

---

### Task 9.2: Dispatcher WebSocket Session for Call Events

**Status:** Not Started

**Description:**
Verify that `DispatcherWebSocketHandler` and `DispatcherSession` correctly receive and forward call event messages to connected clients. No structural changes are expected — this task verifies integration and adds test coverage.

**Package:** `net.pkhapps.idispatchx.cad.adapter.primary.websocket.dispatcher`

**Acceptance Criteria:**
- [ ] Call events sent via `DispatcherBroadcastService` reach all connected dispatcher sessions
- [ ] Observer sessions receive events but their command REST requests are rejected (403)
- [ ] Integration test: two dispatcher clients connected; command from one produces events on both

**Dependencies:** Task 9.1

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
  - `IncidentState`
  - `IncidentSummary` (as returned in GET /api/v1/incidents list)
  - `Incident` (full incident object)
  - `IncidentLogEntry` (automatic and manual variants)
  - WebSocket event payload types: `CallCreatedPayload`, `CallUpdatedPayload`, `CallEndedPayload`, `CallAttachedToIncidentPayload`, `CallDetachedFromIncidentPayload`, `IncidentCreatedPayload`, `IncidentLogEntryAddedPayload`

**Acceptance Criteria:**
- [ ] All types match the JSON representations in the API design exactly
- [ ] Discriminated unions use the `type` literal field
- [ ] Types are exported from a single barrel file

**Dependencies:** None (specification-driven)

---

### Task 10.2: CAD REST Client

**Status:** Not Started

**Description:**
Implement a `CadRestClient` class providing async methods for all call and incident REST operations in this UC. Uses the existing `HttpClient` infrastructure.

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
- `createIncidentFromCall(params): Promise<{ incidentId: string }>` — POST /api/v1/incidents
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
Implement a `DispatcherWebSocketClient` that connects to `/api/v1/ws/dispatcher`, receives server events, and dispatches them to registered handlers. Implements reconnection with exponential back-off per the Availability NFR.

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
Implement `<idispatch-location-entry>` custom element — a form panel that allows the dispatcher to select a location variant and enter its fields.

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
Implement `<idispatch-call-detail-form>` custom element — the full call editing panel shown in the left column of the primary window.

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
- **Copy Location to Incident** — visible when call is linked to an incident and has a location; copies location to incident (this is an update incident location command — defer to future issue if incident update not implemented yet; mark as disabled with tooltip)

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
Implement `<idispatch-call-list>` custom element — the call list shown in the upper part of the third column.

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
Implement client-side vicinity filtering on the call and incident lists. When the dispatcher enters coordinates for the current call, the lists switch to a "vicinity" mode showing only calls and incidents whose coordinates fall within a configurable radius (default: 1 km).

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
Implement `<idispatch-incident-list>` custom element — the incident list shown in the lower part of the third column. Scope: display and selection only; full incident editing is in a future issue.

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
Wire the call and incident UI components into `PrimaryWindow.ts`. Implement the three-column layout with call detail form (left), incident detail placeholder (center — full detail is a future issue), and call/incident lists (right).

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

### Recommended Execution Order

1. **Start with Phase 1**: location value objects and call primitives are needed everywhere.
2. **Phases 2 and 3 can be done in parallel**: Call entity and Incident entity are independent.
3. **Phase 4 after Phases 1–3**: commands and events reference domain model types.
4. **Phase 5 after Phase 4**: repositories need entity types.
5. **Phase 6 after Phases 4–5**: command handlers need repositories, commands, and events.
6. **Phases 7 and 8–9 can start in parallel once Phase 6 is done**.
7. **Phase 10 is independent of backend phases** and can start in parallel with Phase 6.
8. **Phases 11 and 12 require Phase 10 to be done first**.

### Cross-Aggregate Locking

Tasks 6.4, 6.5, and 6.6 involve cross-aggregate operations (Call + Incident). Always acquire locks in the same deterministic order (sort IDs lexicographically) to avoid deadlocks per the technical design.

### Archival Port Extension

Task 6.3 requires extending `ArchivePort` with a `scheduleUnlinkedCallArchival(CallId)` method. The actual archival implementation (writing to PostgreSQL) is out of scope; the method may be a no-op stub that logs a warning. This allows the command handler to correctly signal the intent without blocking on a full archival implementation.

### Dispatcher Client: Auto-Save Debounce

The call detail form debounces field changes (Task 11.2). This means a call is created first (with an empty body), then filled in incrementally via PATCH. This matches the REST API design where all fields are optional.

### Test Coverage Priority

Focus unit tests on:
- Domain invariants in `Call.prepare*()` and `Incident.prepare*()` methods
- WAL-before-state pattern in command handlers (mock WalPort, verify write happens before repository mutation)
- REST endpoint validation (role enforcement, X-Command-Id, field constraints)
- Haversine distance calculation for the vicinity filter
