# Technical Design: CAD Server Domain Core

## Overview

This document describes the technical design for the domain core of the CAD Server. The design follows hexagonal (ports-and-adapters) architecture with:

- All operational data loaded into memory on application startup (via WAL replay)
- Thread-safe mutable entities with WAL-before-state-update semantics
- Clear separation: Primary ports (inbound) → Domain Core → Secondary ports (outbound)

## References

- [ADR-0008: CAD Server Ports-and-Adapters Architecture](../ADR/ADR-0008-cad-server-ports-and-adapters.md)
- [NFR: Performance](../NonFunctionalRequirements/Performance.md) - WAL sync constraint
- [NFR: Availability](../NonFunctionalRequirements/Availability.md) - Idempotency requirement
- [Domain: Incident](../Domain/Incident.md)
- [Domain: UnitStatus](../Domain/UnitStatus.md)
- [Domain: Call](../Domain/Call.md)

---

## 1. Package Structure

```
net.pkhapps.idispatchx.cad/
├── domain/
│   ├── model/                    # Domain entities and value objects
│   │   ├── incident/             # Incident, IncidentUnit, IncidentLogEntry
│   │   ├── call/                 # Call
│   │   ├── unit/                 # Unit, UnitStatus
│   │   ├── reference/            # Station, AlertTarget, IncidentType
│   │   └── shared/               # Value objects and domain primitives
│   ├── event/                    # Domain events (written to WAL)
│   ├── command/                  # Command objects
│   ├── service/                  # Domain services (cross-aggregate operations)
│   ├── repository/               # Repository interfaces
│   └── statemachine/             # State machine implementations
├── port/
│   ├── primary/                  # Primary port interfaces (use case handlers)
│   └── secondary/                # Secondary port interfaces
│       ├── wal/                  # WalPort
│       ├── archive/              # ArchivePort
│       ├── alert/                # EmailPort, SmsPort, ClientAlertPort
│       └── clock/                # ClockPort
└── application/                  # Application layer
    ├── handler/                  # Command handlers
    └── replay/                   # WAL replay service
```

---

## 2. Domain Primitives

Domain primitives are self-validating value objects that encapsulate validation rules at the type level. Invalid values cannot exist in the system.

### 2.1 Design Principles

- **Immutable**: Java records
- **Self-validating**: Constructor throws `IllegalArgumentException` for invalid values
- **Type-safe**: Prevents mixing up different string-based identifiers

### 2.2 Example

```java
public record PhoneNumber(String value) {
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");

    public PhoneNumber {
        Objects.requireNonNull(value, "phone number must not be null");
        if (!E164_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid E.164 format: " + value);
        }
    }
}
```

### 2.3 Catalog

| Primitive | Wraps | Validation Rules |
|-----------|-------|------------------|
| `EmailAddress` | String | RFC-compliant format, max 254 chars |
| `PhoneNumber` | String | E.164 format (+358401234567), max 15 digits |
| `CallSign` | String | Alphanumeric + hyphens/spaces, max 20 chars |
| `Coordinates` | double, double | Finland bounds (lat 58.84-70.09, lon 19.08-31.59), max 6 decimals |
| `MultilingualName` | Map | ISO 639 language codes, max 200 chars per value |
| `StationAlertClientId` | String | Nano ID format (21 URL-safe chars) |
| `MobileUnitClientId` | String | Nano ID format (21 URL-safe chars) |

---

## 3. Core Design Pattern: WAL-Before-State

### 3.1 Critical Constraint

Per the Performance NFR:

> No state can be updated before the event changing the state has successfully been written to the WAL and synced.

### 3.2 Command Handler Pattern

All state-changing operations flow through command handlers:

1. **Acquire lock** on affected aggregate(s)
2. **Validate and prepare** - compute event and mutation, but do NOT mutate yet
3. **Write to WAL** - block until synced to disk
4. **Apply mutation** - only after WAL confirms durability
5. **Return result**

```java
public final R handle(C command) {
    try (var lock = lockManager.acquire(determineLockScope(command))) {
        var pending = prepareExecution(command);  // No mutation yet
        walPort.write(pending.event());           // Blocks until synced
        pending.applyMutation().run();            // Now mutate
        return buildResult(command, pending.event());
    }
}
```

The `PendingMutation` record separates event creation from state mutation:

```java
public record PendingMutation<E extends DomainEvent>(E event, Runnable applyMutation) {}
```

---

## 4. Thread-Safety Model

### 4.1 Design Decision: External Synchronization

Entities are **NOT** internally synchronized. Thread-safety is achieved through:

1. **EntityLockManager** - Acquires `ReentrantLock` per aggregate root
2. **Lock ordering** - Keys sorted before acquisition to prevent deadlocks
3. **ConcurrentHashMap** - Thread-safe reads for repositories

**Rationale**: Internal entity locks lead to deadlocks in cross-entity operations. External synchronization is simpler to test and reason about.

### 4.2 Lock Granularity

| Aggregate | Lock Scope | Notes |
|-----------|------------|-------|
| Incident | Per incident ID | Contains IncidentUnit[], IncidentLogEntry[] |
| Call | Per call ID | Linked to Incident via incident_id |
| Unit + UnitStatus | Per unit ID | 1:1 relationship, treated as single aggregate |
| Reference Data | Global lock | Station, AlertTarget, IncidentType |

---

## 5. Entity Design

### 5.1 Principles

- **Mutable but controlled**: State changes only via `prepare*()` methods
- **No setters**: Mutations return `(Event, Runnable)` pairs for WAL-before-state pattern
- **Version tracking**: For optimistic locking during WAL replay
- **WAL replay support**: `applyEvent()` method reconstructs state from events

### 5.2 Entity Pattern

```java
public final class Incident extends Entity<IncidentId> {
    private IncidentState state;
    // ... other fields

    // Factory method returns entity + event
    public static IncidentCreationResult create(...) { ... }

    // Prepare methods return event + deferred mutation
    public StateTransitionResult prepareStateTransition(IncidentState newState, ...) {
        IncidentStateMachine.validateTransition(this.state, newState);
        validatePreconditions(newState);

        var event = new IncidentStateChangedEvent(...);
        Runnable mutation = () -> { this.state = newState; incrementVersion(); };

        return new StateTransitionResult(event, mutation);
    }

    // For WAL replay
    void applyEvent(DomainEvent event) { ... }
}
```

### 5.3 Aggregates

| Aggregate | Root Entity | Contains |
|-----------|-------------|----------|
| Incident | `Incident` | `IncidentUnit[]`, `IncidentLogEntry[]` |
| Call | `Call` | — |
| Unit | `Unit` | `UnitStatus` (1:1) |

---

## 6. State Machines

State machines are implemented as static utility classes with transition maps.

### 6.1 Incident States

```
new → queued | active | monitored | ended
queued → active | monitored | ended
active → monitored | ended
monitored → queued | active | ended
ended → (terminal)
```

**Preconditions:**
- `queued`/`active`: type, priority, location required
- `active`: at least 1 unit assigned
- `ended`: all IncidentUnits must have `unit_unassigned_at` set

### 6.2 UnitStatus States (9-state machine)

```
unavailable → available_over_radio | available_at_station
available_over_radio → assigned_radio | available_at_station | unavailable
available_at_station → assigned_station | available_over_radio | unavailable
assigned_radio → available_over_radio | dispatching
assigned_station → available_at_station | dispatching
dispatching → dispatched | available_over_radio | available_at_station | unavailable
dispatched → available_over_radio | available_at_station | en_route | unavailable
en_route → available_over_radio | available_at_station | on_scene | unavailable
on_scene → available_over_radio | available_at_station | unavailable
```

**Authority:**
- System only: `assigned_radio`, `assigned_station`, `dispatching`
- Dispatcher/Unit: `unavailable`, `available_*`, `en_route`, `on_scene`
- System or manual: `dispatched`

**Auto-unassignment**: Transition to `available_at_station` or `unavailable` clears incident assignment.

---

## 7. Secondary Ports

### 7.1 WalPort

```java
public interface WalPort {
    void write(DomainEvent event);              // Blocks until synced
    void writeBatch(List<DomainEvent> events);  // Atomic batch
    void replay(Consumer<DomainEvent> consumer); // Startup reconstruction
    void truncate(EventId upToEventId);         // Post-archival cleanup
}
```

### 7.2 ArchivePort

```java
public interface ArchivePort {
    void archiveIncident(Incident incident, List<Call> relatedCalls);
    void archiveUnlinkedCall(Call call);
    boolean isAvailable();  // Degraded mode detection
}
```

### 7.3 Alert Ports

```java
public interface EmailPort {
    CompletableFuture<AlertAcknowledgment> sendAlert(List<EmailAddress> addresses, AlertContent content);
}

public interface SmsPort {
    CompletableFuture<AlertAcknowledgment> sendAlert(List<PhoneNumber> phoneNumbers, AlertContent content);
}

public interface ClientAlertPort {
    CompletableFuture<AlertAcknowledgment> sendToStationAlertClient(StationAlertClientId clientId, AlertContent content);
    CompletableFuture<AlertAcknowledgment> sendToMobileUnitClient(MobileUnitClientId clientId, AlertContent content);
}
```

### 7.4 ClockPort

```java
public interface ClockPort {
    Instant now();  // Abstracted for testability
}
```

---

## 8. Repositories

In-memory repositories using `ConcurrentHashMap` for thread-safe reads. Writes are protected by `EntityLockManager`.

```java
public interface Repository<E extends Entity<ID>, ID> {
    Optional<E> findById(ID id);
    Stream<E> findAll();
    void add(E entity);
    void remove(ID id);
    boolean existsById(ID id);
}
```

Specialized repositories add query methods:
- `IncidentRepository`: `findActive()`, `findByState()`, `findReadyForArchival()`
- `UnitRepository`: `findByCallSign()`, `findActive()`, `findByStation()`
- `CallRepository`: `findByIncidentId()`, `findActive()`

---

## 9. Domain Services

Domain services coordinate cross-aggregate operations:

| Service | Purpose |
|---------|---------|
| `DispatchService` | Coordinates Incident, Unit, UnitStatus, AlertTargets during dispatch |
| `UnitAssignmentService` | Handles assignment, reassignment, immediate transitions |
| `ArchivalService` | Coordinates incident archival to PostgreSQL |

Services follow the same pattern: validate preconditions, prepare events/mutations, return preparation result for the command handler to execute.

---

## 10. Commands and Events

### 10.1 Commands

Commands represent intent and carry idempotency keys:

```java
public sealed interface Command {
    CommandId commandId();  // For idempotency
    UserId issuedBy();
}
```

Key commands: `CreateIncidentCommand`, `TransitionIncidentStateCommand`, `AssignUnitCommand`, `DispatchUnitsCommand`, `CreateCallCommand`, `EndCallCommand`, `AttachCallToIncidentCommand`

### 10.2 Events

Events represent facts written to WAL:

```java
public sealed interface DomainEvent {
    EventId eventId();
    Instant timestamp();
    CommandId causedBy();  // For idempotency tracking
}
```

Key events: `IncidentCreatedEvent`, `IncidentStateChangedEvent`, `UnitAssignedEvent`, `UnitDispatchingEvent`, `UnitDispatchedEvent`, `CallCreatedEvent`, `CallEndedEvent`

### 10.3 Idempotency

Per Availability NFR, all commands must be idempotent. `IdempotencyTracker` stores processed `CommandId` → result mappings with time-based expiration.

---

## 11. WAL Replay

On startup, `WalReplayService` reconstructs in-memory state by replaying all events:

1. Call `walPort.replay(consumer)`
2. For each event, dispatch to appropriate handler:
   - `IncidentCreatedEvent` → create Incident, add to repository
   - `IncidentStateChangedEvent` → find Incident, call `applyEvent()`
   - `IncidentArchivedEvent` → remove Incident from repository
   - Similar for Call, Unit, UnitStatus events

---

## 12. Validation

Validation is enforced at three levels:

1. **Domain primitives** - Self-validating value objects (coordinates, phone numbers, emails)
2. **Entity methods** - Business rules in `prepare*()` methods
3. **State machines** - Transition validation

### Text Length Limits

| Field | Max Length |
|-------|------------|
| Incident/Call description | 1000 chars |
| Call outcome_rationale | 1000 chars |
| Location additional_details | 1000 chars |
| Caller name | 100 chars |
| Address number | 30 chars |

---

## 13. Key Invariants

| Invariant | Enforcement |
|-----------|-------------|
| WAL-before-state | CommandHandler |
| Unit-UnitStatus 1:1 | UnitRepository |
| Incident closure requires unassigned units | Incident.prepareStateTransition() |
| Cannot attach call to ended incident | Call.prepareAttachToIncident() |
| Call outcome immutable once ended | Call.prepareEndCall() |
| Unit deactivation blocked while assigned | Unit.prepareDeactivation() |
| State machine transitions | StateMachine utility classes |
| Command idempotency | IdempotencyTracker |
| Domain primitive validation | Self-validating records |

---

## 14. Verification Strategy

### Unit Tests
- State machines: all valid/invalid transitions
- Invariant enforcement: precondition violations throw exceptions
- Command handlers with mock WalPort: verify event written before mutation

### Integration Tests
- WAL replay: write events, replay, verify state reconstructed
- Cross-aggregate operations: dispatch, assignment, archival flows

### Concurrency Tests
- Parallel commands on same aggregate (should serialize)
- Parallel commands on different aggregates (should execute concurrently)
- WAL ordering: verify state not visible before WAL sync completes
