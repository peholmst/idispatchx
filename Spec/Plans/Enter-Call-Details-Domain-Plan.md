# Enter Call Details — Domain Model and Command Handlers Plan

This document covers the CAD Server backend implementation: domain primitives, aggregate roots,
commands, domain events, in-memory repositories, command handlers, and WAL serialization/replay.

Related plans:
- [Endpoints Plan](Enter-Call-Details-Endpoints-Plan.md) — REST and WebSocket adapters
- [UI Plan](Enter-Call-Details-UI-Plan.md) — Dispatcher Client

## References

- [UC: Enter Call Details](../UseCases/Dispatcher/UC-Enter-Call-Details.md)
- [UC: Create Incident From Call](../UseCases/Dispatcher/UC-Create-Incident-From-Call.md)
- [UC: Attach Call To Incident](../UseCases/Dispatcher/UC-Attach-Call-To-Incident.md)
- [UC: Detach Call From Incident](../UseCases/Dispatcher/UC-Detach-Call-From-Incident.md)
- [Technical Design: CAD Server Domain Core](../TechnicalDesigns/CAD-Server-Domain-Core.md)
- [Domain: Call](../Domain/Call.md)
- [Domain: Incident](../Domain/Incident.md)
- [Domain: Location](../Domain/Location.md)
- [NFR: Availability](../NonFunctionalRequirements/Availability.md)
- [NFR: Performance](../NonFunctionalRequirements/Performance.md)
- [NFR: Security](../NonFunctionalRequirements/Security.md)

---

## Out of Scope

The following are explicitly out of scope for this plan, to be addressed in future issues:

- Full Incident lifecycle: state transitions to `queued`, `active`, `monitored`, `ended`
- Unit assignment, dispatch, and incident log entries for unit events
- Incident creation without a source call (UC-Create-Incident)
- Incident state management UI in the Dispatcher Client
- Standalone incident creation in the Dispatcher Client header

---

## Coding Conventions

All new Java code must follow these conventions (applies to every task in this plan):

- Use `@Nullable T` for optional record component types and optional method/constructor parameters.
- Use `Optional<T>` only for method return values that are not record component getters.
- Do **not** pass `Optional<T>` as a parameter; use `@Nullable T` instead.
- Self-validating primitives reject invalid values in their constructors; they never silently truncate.

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
| **Total** | | **24** | |

---

## Phase 1: Domain Primitives and Location Value Objects

Foundation value objects required by both the Call and Incident domain models.

### Task 1.1: Location Value Objects

**Status:** Not Started

**Description:**
Implement the four Location variants from [Domain: Location](../Domain/Location.md) as a Java sealed
interface with record implementations.

`Municipality`, `MultilingualName`, and `Coordinates` already exist in the `shared/java-common`
module at `net.pkhapps.idispatchx.common.domain.model.*`. Do **not** re-create them; import and use
the existing classes.

**Package:** `net.pkhapps.idispatchx.cad.domain.model.shared.location`

**Files to Create:**
- `Location.java` — sealed interface with four record implementations:
  `ExactAddress`, `RoadIntersection`, `NamedPlace`, `RelativeLocation`

**Variant fields:**
- `ExactAddress`: required `municipality`, `addressName`; optional `@Nullable addressNumber` (max 30 chars), `@Nullable coordinates`, `@Nullable additionalDetails` (max 1000 chars)
- `RoadIntersection`: required `municipality`, `roadNameA`, `roadNameB`; optional `@Nullable coordinates`, `@Nullable additionalDetails`
- `NamedPlace`: required `municipality`, `name`; optional `@Nullable coordinates`, `@Nullable additionalDetails`
- `RelativeLocation`: required `municipality`, `referencePlace`, `additionalDetails`; optional `@Nullable coordinates`

**Acceptance Criteria:**
- [ ] `Location` is a sealed interface; each variant is a Java record implementing it
- [ ] All required fields reject null on construction
- [ ] `addressNumber` rejects values exceeding 30 characters
- [ ] `additionalDetails` rejects values exceeding 1000 characters
- [ ] `Municipality`, `MultilingualName`, and `Coordinates` from `java-common` are used directly — no local copies
- [ ] Unit tests cover all variant constructors, required/optional field enforcement, and boundary validation

**Dependencies:** None

---

### Task 1.2: Shared Domain Primitives

**Status:** Not Started

**Description:**
Implement self-validating domain primitives needed by both the Call and Incident aggregates.

**Package:** `net.pkhapps.idispatchx.cad.domain.model.shared`

**Files to Create:**
- `CallId.java` — record wrapping Nano ID string (21 URL-safe chars)
- `CallerName.java` — record wrapping String; max 100 characters
- `PhoneNumber.java` — record wrapping String; E.164 format as specified in [Domain: Call](../Domain/Call.md); generic name because phone numbers may be needed in other contexts in the future
- `Description.java` — record wrapping String; max 1000 UTF-8 characters; used for call descriptions, outcome rationales, incident descriptions, and incident log entry text

**Note:** Do **not** create `UserId.java`. Use `net.pkhapps.idispatchx.common.auth.UserId` from the `java-common` module. This class is already fully implemented (with validation and the `SYSTEM` constant for system-originated commands).

**Acceptance Criteria:**
- [ ] `PhoneNumber` accepts valid E.164 numbers with and without leading `+`; rejects non-digits, numbers exceeding 15 digits
- [ ] `CallerName` rejects values exceeding 100 characters
- [ ] `Description` rejects values exceeding 1000 UTF-8 characters
- [ ] `CallId` rejects null and empty/invalid Nano ID strings
- [ ] `UserId` is imported from `net.pkhapps.idispatchx.common.auth.UserId`; no local copy is created
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
Implement the `Call` entity per [Domain: Call](../Domain/Call.md), following the entity design pattern
from the technical design (WAL-before-state, prepare methods, `applyEvent`).

**Package:** `net.pkhapps.idispatchx.cad.domain.model.call`

**Files to Modify:**
- `Call.java` — replace placeholder with full implementation

**Call attributes (all as per domain spec):**
- `id: CallId` (required, immutable)
- `state: CallState`
- `receivingDispatcher: UserId` (required, immutable)
- `callStarted: Instant` (required, immutable)
- `callEnded: @Nullable Instant` (set when `state → ENDED`)
- `callerName: @Nullable CallerName`
- `callerPhoneNumber: @Nullable PhoneNumber`
- `location: @Nullable Location`
- `description: @Nullable Description`
- `outcome: @Nullable CallOutcome` (optional while active; required on end)
- `outcomeRationale: @Nullable Description` (required when outcome requires it)
- `incidentId: @Nullable IncidentId`

**Prepare methods to implement:**
- `static CallCreationResult create(CallId, UserId, Instant, @Nullable CallerName, @Nullable PhoneNumber, @Nullable Location, @Nullable Description)` — factory; returns `(CallCreatedEvent, Call)`
- `PendingMutation<CallUpdatedEvent> prepareUpdate(@Nullable CallerName, @Nullable PhoneNumber, @Nullable Location, @Nullable Description, @Nullable CallOutcome, @Nullable Description outcomeRationale)` — validates that `outcome` is not `INCIDENT_CREATED` or `ATTACHED_TO_INCIDENT` (those are set by specific commands); only sets fields that are non-null
- `PendingMutation<CallEndedEvent> prepareEnd(@Nullable CallOutcome, @Nullable Description outcomeRationale)` — validates: call must be in `ACTIVE` state; `outcome` must be set (either already on call or provided); `outcomeRationale` must be present when outcome requires it
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
- [ ] No `Optional<T>` used as parameter type anywhere in this class
- [ ] Unit tests cover all invariants, edge cases, and WAL replay correctness
- [ ] Entity is not internally synchronized (external synchronization via `EntityLockManager`)

**Dependencies:** Tasks 1.1, 1.2, 2.1, 2.2

---

### Task 2.4: Call Repository Interface

**Status:** Not Started

**Description:**
Define the `CallRepository` interface extending the generic `Repository` from the technical design,
with call-specific query methods.

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

The minimal Incident implementation needed to support call-to-incident linking. Full Incident lifecycle
(unit assignment, state transitions beyond `new`, dispatch) is out of scope.

### Task 3.1: Incident Identifiers, State, and Priority

**Status:** Not Started

**Description:**
Define Incident identifiers, the state enum, and the priority value object.

**Package:** `net.pkhapps.idispatchx.cad.domain.model.incident`

**Files to Create:**
- `IncidentId.java` — record wrapping Nano ID string (21 URL-safe chars)
- `IncidentState.java` — enum: `NEW`, `QUEUED`, `ACTIVE`, `MONITORED`, `ENDED`
- `IncidentPriority.java` — enum: `A`, `B`, `C`, `D`, `N`; each value represents the corresponding priority level from the domain model
- `IncidentType.java` — record wrapping a String code (non-null, non-empty); represents the incident type code

**Acceptance Criteria:**
- [ ] All five `IncidentState` values defined
- [ ] All five `IncidentPriority` values defined, matching `A | B | C | D | N` from the domain spec
- [ ] `IncidentId` validates Nano ID format

**Dependencies:** None

---

### Task 3.2: IncidentLogEntry

**Status:** Not Started

**Description:**
Implement the `IncidentLogEntry` value object per [Domain: Incident](../Domain/Incident.md).

Per the domain spec, the `dispatcher` field is optional on all log entry types:
- For **automatic** entries, `dispatcher` is set when the change was triggered by a dispatcher action
  (e.g., a dispatcher-issued command that causes an automatic state change); it is null when the
  change was triggered by the system alone.
- For **manual** entries, `dispatcher` is always present since a dispatcher authored the entry.

**Package:** `net.pkhapps.idispatchx.cad.domain.model.incident`

**Files to Create:**
- `IncidentLogEntryId.java` — record wrapping Nano ID string
- `IncidentLogEntry.java` — sealed interface with two record implementations:
  - `AutomaticEntry(IncidentLogEntryId id, Instant logTimestamp, @Nullable UserId dispatcher, JsonNode changeData)` — system-generated; `changeData` is a structured JSON object; `dispatcher` is set when a dispatcher action triggered this entry
  - `ManualEntry(IncidentLogEntryId id, Instant logTimestamp, UserId dispatcher, Description description)` — dispatcher-authored; `dispatcher` is always required

**Acceptance Criteria:**
- [ ] `AutomaticEntry` has an optional `@Nullable UserId dispatcher` field
- [ ] `ManualEntry` requires a non-null `dispatcher` user ID and a non-null `description`
- [ ] `description` max 1000 UTF-8 characters enforced via `Description` value object
- [ ] Entries are immutable records

**Dependencies:** Tasks 1.2, 3.1

---

### Task 3.3: Incident Entity

**Status:** Not Started

**Description:**
Implement the `Incident` entity per [Domain: Incident](../Domain/Incident.md) to the extent needed
for this UC: creation in state `NEW`, call linking, log entry creation. Full state transition logic
is out of scope.

**Package:** `net.pkhapps.idispatchx.cad.domain.model.incident`

**Files to Modify:**
- `Incident.java` — replace placeholder with implementation

**Attributes (subset needed for this UC):**
- `id: IncidentId` (required, immutable)
- `state: IncidentState` (required; initially `NEW`)
- `incidentCreated: Instant` (required, immutable)
- `incidentType: @Nullable IncidentType`
- `incidentPriority: @Nullable IncidentPriority`
- `location: @Nullable Location`
- `description: @Nullable Description`
- `logEntries: List<IncidentLogEntry>` (append-only)

**Prepare methods to implement:**
- `static IncidentCreationResult create(IncidentId, Instant, @Nullable IncidentType, @Nullable IncidentPriority, @Nullable Location, @Nullable Description)` — factory; creates incident in state `NEW`
- `PendingMutation<IncidentLogEntryAddedEvent> prepareAddCallLinkedLogEntry(IncidentLogEntryId, Instant, @Nullable UserId dispatcher, CallId)` — adds automatic log entry recording call linkage; validates incident is not in `ENDED` state
- `PendingMutation<IncidentLogEntryAddedEvent> prepareAddCallDetachedLogEntry(IncidentLogEntryId, Instant, @Nullable UserId dispatcher, CallId)` — adds automatic log entry recording call detachment
- `void applyEvent(DomainEvent)` — for WAL replay

**Acceptance Criteria:**
- [ ] Incident is created in state `NEW` with correct attributes
- [ ] `incidentPriority` uses `IncidentPriority` enum, not a raw `String`
- [ ] `description` uses `Description` value object, not a raw `String`
- [ ] `prepareAddCallLinkedLogEntry` rejects incidents in `ENDED` state
- [ ] `logEntries` is append-only; entries cannot be modified or removed
- [ ] No `Optional<T>` used as parameter type anywhere in this class
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
  - `Stream<Incident> findActive()` — returns incidents not in state `ENDED`

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
- `CreateCallCommand.java` — record; `commandId`, `userId` (UserId from `common.auth`), `@Nullable IPAddress ipAddress`, `@Nullable CallerName callerName`, `@Nullable PhoneNumber callerPhoneNumber`, `@Nullable Location location`, `@Nullable Description description`
- `UpdateCallDetailsCommand.java` — record; `commandId`, `userId`, `@Nullable IPAddress ipAddress`, `callId`, `@Nullable CallerName callerName`, `@Nullable PhoneNumber callerPhoneNumber`, `@Nullable Location location`, `@Nullable Description description`, `@Nullable CallOutcome outcome`, `@Nullable Description outcomeRationale`
- `EndCallCommand.java` — record; `commandId`, `userId`, `@Nullable IPAddress ipAddress`, `callId`, `@Nullable CallOutcome outcome`, `@Nullable Description outcomeRationale`
- `AttachCallToIncidentCommand.java` — record; `commandId`, `userId`, `@Nullable IPAddress ipAddress`, `callId`, `incidentId`
- `DetachCallFromIncidentCommand.java` — record; `commandId`, `userId`, `@Nullable IPAddress ipAddress`, `callId`

**Note:** `userId` and `ipAddress` are required by the `Command` interface (from issue #71/#73). The REST controller sets `userId` from the authenticated JWT and `ipAddress` from the HTTP request; they are consumed by `IdempotentCommandDispatcher` for audit logging.

**Acceptance Criteria:**
- [ ] Each command implements the `Command` interface (`commandId()`, `userId()`, `ipAddress()`)
- [ ] `userId` is `net.pkhapps.idispatchx.common.auth.UserId`; `ipAddress` is `net.pkhapps.idispatchx.common.auth.IPAddress`
- [ ] Optional fields use `@Nullable T` (not `Optional<T>`)
- [ ] `PhoneNumber` used for caller phone numbers (not `CallerPhoneNumber`)
- [ ] `Description` used for call description and outcome rationale (not separate types)

**Dependencies:** Tasks 1.2, 2.3, 3.1

---

### Task 4.2: Incident Creation Command

**Status:** Not Started

**Description:**
Define the command for creating an incident from a call.

**Package:** `net.pkhapps.idispatchx.cad.domain.command`

**Files to Create:**
- `CreateIncidentFromCallCommand.java` — record; `commandId`, `userId` (UserId from `common.auth`), `@Nullable IPAddress ipAddress`, `sourceCallId`, `@Nullable IncidentType incidentType`, `@Nullable IncidentPriority incidentPriority`, `@Nullable Location location`, `@Nullable Description description`

**Acceptance Criteria:**
- [ ] Implements `Command` interface (`commandId()`, `userId()`, `ipAddress()`)
- [ ] `sourceCallId` is required (non-null)
- [ ] `IncidentPriority` enum used (not a raw String)
- [ ] `Description` used for description field

**Dependencies:** Tasks 1.2, 2.3, 3.1

---

### Task 4.3: Call Domain Events

**Status:** Not Started

**Description:**
Define domain events for all call state changes. All events implement `DomainEvent` and are written to WAL.

**Package:** `net.pkhapps.idispatchx.cad.domain.event`

**Files to Create:**
- `CallCreatedEvent.java` — record; `eventId`, `timestamp`, `causedBy`, `callId`, `receivingDispatcher`, `callStarted`, `@Nullable CallerName callerName`, `@Nullable PhoneNumber callerPhoneNumber`, `@Nullable Location location`, `@Nullable Description description`
- `CallUpdatedEvent.java` — record; `eventId`, `timestamp`, `causedBy`, `callId`, `@Nullable CallerName callerName`, `@Nullable PhoneNumber callerPhoneNumber`, `@Nullable Location location`, `@Nullable Description description`, `@Nullable CallOutcome outcome`, `@Nullable Description outcomeRationale` — includes all mutable fields that may have changed
- `CallEndedEvent.java` — record; `eventId`, `timestamp`, `causedBy`, `callId`, `callEnded`, `outcome`, `@Nullable Description outcomeRationale`, `@Nullable IncidentId incidentId`
- `CallAttachedToIncidentEvent.java` — record; `eventId`, `timestamp`, `causedBy`, `callId`, `incidentId`
- `CallDetachedFromIncidentEvent.java` — record; `eventId`, `timestamp`, `causedBy`, `callId`, `formerIncidentId`

**Note on `causedBy`:** Per the `DomainEvent` interface, `causedBy` is of type `@Nullable CommandId` (from `net.pkhapps.idispatchx.cad.domain.command.CommandId`), not `UserId`. It links the event back to the command that triggered it for idempotency tracking.

**Acceptance Criteria:**
- [ ] Each event implements the `DomainEvent` interface (`eventId()`, `timestamp()`, `causedBy()`)
- [ ] `causedBy` is `@Nullable CommandId`, not `UserId`
- [ ] Events are immutable records
- [ ] Optional fields use `@Nullable T` for record components
- [ ] `PhoneNumber` used in place of any caller-specific phone type
- [ ] `Description` used for all description and rationale fields

**Dependencies:** Tasks 1.1, 1.2, 2.1

---

### Task 4.4: Incident Domain Events

**Status:** Not Started

**Description:**
Define the domain events for incident creation and log entry addition.

**Package:** `net.pkhapps.idispatchx.cad.domain.event`

**Files to Create:**
- `IncidentCreatedEvent.java` — record; `eventId`, `timestamp`, `causedBy`, `incidentId`, `incidentCreated`, `@Nullable CallId sourceCallId`, `@Nullable IncidentType incidentType`, `@Nullable IncidentPriority incidentPriority`, `@Nullable Location location`, `@Nullable Description description`
- `IncidentLogEntryAddedEvent.java` — record; `eventId`, `timestamp`, `causedBy`, `incidentId`, `logEntry` (a snapshot of the complete `IncidentLogEntry`)

**Acceptance Criteria:**
- [ ] Each event implements the `DomainEvent` interface (`eventId()`, `timestamp()`, `causedBy()`)
- [ ] `causedBy` is `@Nullable CommandId`
- [ ] Events are immutable records
- [ ] `IncidentPriority` enum used (not raw String)
- [ ] `Description` used for description field

**Dependencies:** Tasks 1.1, 3.1, 3.2

---

## Phase 5: In-Memory Repositories

In-memory implementations using `ConcurrentHashMap` for thread-safe reads. Writes are protected
by `EntityLockManager`.

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

One handler per command, following the WAL-before-state pattern from the technical design.
All handlers extend `CommandHandler<C, R>` and use `EntityLockManager`.

### Task 6.1: Create Call Command Handler

**Status:** Not Started

**Description:**
Handle `CreateCallCommand`: generate `CallId`, create `Call` entity, write `CallCreatedEvent` to WAL,
add call to repository, return call ID.

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
Handle `UpdateCallDetailsCommand`: locate call, call `prepareUpdate`, write `CallUpdatedEvent`,
apply mutation.

**Package:** `net.pkhapps.idispatchx.cad.application.handler`

**Files to Create:**
- `UpdateCallDetailsCommandHandler.java`

**Acceptance Criteria:**
- [ ] Returns `404` equivalent if call not found
- [ ] Rejects if call is in `ENDED` state (409)
- [ ] Rejects if `outcome` is `INCIDENT_CREATED` or `ATTACHED_TO_INCIDENT` (use specific commands for those)
- [ ] Only updates fields present (non-null) in the command; absent fields are unchanged
- [ ] `CallUpdatedEvent` written to WAL before mutation
- [ ] Unit tests: mock `WalPort`, verify partial updates, validate rejections

**Dependencies:** Tasks 2.3, 4.1, 5.1, 6.1

---

### Task 6.3: End Call Command Handler

**Status:** Not Started

**Description:**
Handle `EndCallCommand`: locate call, call `prepareEnd`, write `CallEndedEvent`, apply mutation,
schedule archival if call is not linked to an incident.

**Package:** `net.pkhapps.idispatchx.cad.application.handler`

**Files to Create:**
- `EndCallCommandHandler.java`

**Archival scheduling:** After successful WAL write and mutation, if `call.incidentId` is absent,
invoke `ArchivePort.scheduleUnlinkedCallArchival(callId)`. Per the Availability NFR, if `ArchivePort`
is unavailable, this must not prevent the call from being ended — log the failure and continue.

**Note on ArchivePort:** `ArchivePort` does not yet exist. As part of this task, create it as a new
secondary port interface in `net.pkhapps.idispatchx.cad.port.secondary.archive` with a single method
`void scheduleUnlinkedCallArchival(CallId callId)`. The actual archival implementation is out of scope;
provide a no-op stub that logs a warning.

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
Handle `AttachCallToIncidentCommand`: locate call and incident, validate preconditions, write events
atomically (call update + incident log entry), apply mutations.

Per the technical design, this is a cross-aggregate operation. The handler must acquire locks on both
the call and the incident (in a deterministic order via `EntityLockManager`), then write both events
as a batch to the WAL.

**Note on batch WAL writes:** `CommandHandler.handle()` is `final` and calls `walPort.write()` for a
single event. Cross-aggregate handlers that need `walPort.writeBatch()` cannot use that method as-is.
Before implementing Tasks 6.4–6.6, extend `CommandHandler` to support batch operations — for example,
by adding an overrideable `protected LockScope determineLockScope(C command)` / `protected List<DomainEvent>
prepareEvents(C command)` pattern, or by making `walPort` accessible (e.g., `protected`) to
subclasses that need to call `writeBatch` themselves. Align this with the technical design before
starting implementation.

**Package:** `net.pkhapps.idispatchx.cad.application.handler`

**Files to Create:**
- `AttachCallToIncidentCommandHandler.java`

**Acceptance Criteria:**
- [ ] Returns `404` if call or incident not found
- [ ] Rejects if call is in `ENDED` state (409)
- [ ] Rejects if incident is in `ENDED` state (409)
- [ ] Rejects if call already has `outcome = INCIDENT_CREATED` (409)
- [ ] Both `CallAttachedToIncidentEvent` and `IncidentLogEntryAddedEvent` written as a batch to WAL before either mutation is applied
- [ ] `IncidentLogEntryAddedEvent` carries the `AutomaticEntry` with `dispatcher` set to the issuing dispatcher's `UserId`
- [ ] Locks on call and incident acquired in deterministic order (sorted by ID string to prevent deadlocks)
- [ ] Unit tests: verify both events in WAL batch, verify rejections

**Dependencies:** Tasks 2.3, 3.3, 4.3, 4.4, 5.1, 5.2

---

### Task 6.5: Detach Call From Incident Command Handler

**Status:** Not Started

**Description:**
Handle `DetachCallFromIncidentCommand`: locate call, locate incident (via call's `incidentId`),
validate preconditions, write events as batch, apply mutations.

**Package:** `net.pkhapps.idispatchx.cad.application.handler`

**Files to Create:**
- `DetachCallFromIncidentCommandHandler.java`

**Acceptance Criteria:**
- [ ] Returns `404` if call not found
- [ ] Rejects if call is in `ENDED` state (409)
- [ ] Rejects if call's `outcome != ATTACHED_TO_INCIDENT` (409)
- [ ] Both `CallDetachedFromIncidentEvent` and `IncidentLogEntryAddedEvent` written as a batch before mutations
- [ ] `IncidentLogEntryAddedEvent` carries the `AutomaticEntry` with `dispatcher` set to the issuing dispatcher's `UserId`
- [ ] After mutation: `call.incidentId` is cleared and `call.outcome` is cleared
- [ ] Locks acquired in deterministic order on both aggregates
- [ ] Unit tests verify all rejections and WAL batch write

**Dependencies:** Tasks 2.3, 3.3, 4.3, 4.4, 5.1, 5.2

---

### Task 6.6: Create Incident From Call Command Handler

**Status:** Not Started

**Description:**
Handle `CreateIncidentFromCallCommand`: locate source call, validate preconditions, create new
`Incident`, link call to incident, write events as batch, apply mutations.

**Package:** `net.pkhapps.idispatchx.cad.application.handler`

**Files to Create:**
- `CreateIncidentFromCallCommandHandler.java`

**Detailed behavior:**
1. Locate source call; reject if not found (404) or `ENDED` (409)
2. Reject if call already has `outcome = INCIDENT_CREATED` or `outcome = ATTACHED_TO_INCIDENT` (409)
3. Generate new `IncidentId` using Nano ID
4. If `location` is absent in command and call has a `location`, copy call location to incident (independent copy)
5. Write as a batch: `IncidentCreatedEvent`, `CallUpdatedEvent` (setting `outcome = INCIDENT_CREATED`, `incidentId`), `IncidentLogEntryAddedEvent` (recording call linkage; `dispatcher` set to issuing dispatcher's `UserId`)
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
- [ ] `IncidentLogEntryAddedEvent` carries `dispatcher` set to the issuing dispatcher's `UserId`
- [ ] Unit tests verify all validation cases, location copying, WAL batch, and returned incident ID

**Dependencies:** Tasks 2.3, 3.3, 4.2, 4.3, 4.4, 5.1, 5.2

---

## Phase 7: WAL Serialization and Replay

JSON serialization/deserialization for all new domain events, and WAL replay handlers.

### Task 7.1: Event Serialization

**Status:** Not Started

**Description:**
Register the new domain event types with the existing WAL serialization infrastructure and add
Jackson support for the `Location` sealed interface.

The WAL adapter uses `WalMapperFactory` (in `net.pkhapps.idispatchx.cad.adapter.secondary.wal`)
which configures Jackson with `@JsonTypeInfo(Id.CLASS)` via a mixin on `DomainEvent`. New event
types serialize and deserialize automatically once their record classes exist on the classpath and
are passed to `WalMapperFactory.buildJson()` / `buildSmile()` at configuration time.

The `Location` sealed interface (Task 1.1) requires a polymorphic Jackson mapping. Add a mixin or
`@JsonSubTypes` registration in `WalMapperFactory` so each variant serializes with a `"type"`
discriminator matching the REST API format (`exact_address`, `road_intersection`, `named_place`,
`relative_location`).

**Package:** `net.pkhapps.idispatchx.cad.adapter.secondary.wal`

**Files to Create or Modify:**
- `WalMapperFactory.java` — add `Location` mixin (or `@JsonSubTypes`) so all four `Location`
  variants serialize with the correct `"type"` discriminator; register `IncidentPriority` as
  a single-letter string value

**Note:** There is no `EventSerializer.java` or `EventDeserializer.java` to create. New event
record types are picked up automatically via Jackson's `@type` (FQN) mechanism already in place.

**Acceptance Criteria:**
- [ ] All new event types round-trip through the WAL without data loss
- [ ] All optional fields serialize as `null` (not omitted) for deterministic round-trips
- [ ] `Location` variants serialize with `"type"` field matching REST API format
- [ ] `IncidentPriority` serializes as its single-letter string value (`"A"`, `"B"`, etc.)
- [ ] Round-trip tests: serialize event → deserialize → compare with original for each new event type
- [ ] Integration test: write event to `FileBasedWalAdapter`, replay, verify event received

**Dependencies:** Tasks 4.3, 4.4

---

### Task 7.2: WAL Replay Handlers

**Status:** Not Started

**Description:**
Extend the WAL replay service (in `application/replay/`) to handle all new domain events during
startup. Each event handler reconstructs the in-memory state of calls and incidents.

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

## Execution Notes

### Recommended Execution Order

1. **Start with Phase 1**: domain primitives are needed everywhere.
2. **Phases 2 and 3 can be done in parallel**: Call entity and Incident entity are independent.
3. **Phase 4 after Phases 1–3**: commands and events reference domain model types.
4. **Phase 5 after Phase 4**: repositories need entity types.
5. **Phase 6 after Phases 4–5**: command handlers need repositories, commands, and events.
6. **Phase 7 can start once Phase 6 is done**.

### Cross-Aggregate Locking

Tasks 6.4, 6.5, and 6.6 involve cross-aggregate operations (Call + Incident). Always acquire locks
in the same deterministic order (sort IDs lexicographically) to avoid deadlocks per the technical
design.

### Archival Port Extension

Task 6.3 requires extending `ArchivePort` with a `scheduleUnlinkedCallArchival(CallId)` method.
The actual archival implementation (writing to PostgreSQL) is out of scope; the method may be a
no-op stub that logs a warning. This allows the command handler to correctly signal the intent
without blocking on a full archival implementation.

### Test Coverage Priority

Focus unit tests on:
- Domain invariants in `Call.prepare*()` and `Incident.prepare*()` methods
- WAL-before-state pattern in command handlers (mock WalPort, verify write happens before repository mutation)
