# Domain Concept: Incident

An incident is a record of a real or perceived event that requires, or may require, attention from the authorities (e.g. fire, accident, medical emergency, crime in progress).

Incidents with priority `N` do not represent real-world incidents, but intra-agency operational orders (e.g. unit relocation, standby coverage, or area transfer).

An incident may exist without any associated calls and may be created proactively by a dispatcher.


## Attributes

* `id` (required)
  * Type: Nano ID
* `state` (required)
  * Type: `new` | `queued` | `active` | `monitored` | `ended`
* `incident_created` (required)
  * Type: Timestamp (UTC)
* `incident_ended` (optional)
  * Type: Timestamp (UTC)
  * Must only be set when `state → ended`
  * Immutable once set
* `incident_type` (optional)
  * Type: Reference to [IncidentType](IncidentType.md)
* `incident_priority` (optional)
  * Type: `A` | `B` | `C` | `D` | `N`
* `location` (optional)
  * Type: Embedded [Location](Location.md)
* `description` (optional)
  * Type: Text
  * Free-form human description; may be empty or missing
* `calls[0..n]` 
  * Type: Reference to [Call](Call.md), mapped by `Call.incident_id`
  * Calls may be linked to an incident at any time during its lifecycle. After an incident has ended, linking additional calls is not permitted because the incident may no longer be available in the system.
* `units[0..n]`
  * Type: IncidentUnit (see below)
* `logEntries[0..n]`
  * Type: IncidentLogEntry (see below)



### IncidentUnit

Represents the participation of a unit in an incident. This is a historical record and does not represent current unit state.

* `id` (required)
  * Type: Nano ID
* `unit` (required)
  * Type: Reference to [Unit](Unit.md) 
* `unit_staffing` (optional)
  * Type: Embedded [Staffing](Staffing.md)
* `unit_assigned_at` (required)
  * Type: Timestamp (UTC)
* `unit_unassigned_at` (optional)
  * Type: Timestamp (UTC)
* `unit_dispatched` (optional)
  * Type: Timestamp (UTC)
* `unit_en_route` (optional)
  * Type: Timestamp (UTC)
* `unit_on_scene` (optional)
  * Type: Timestamp (UTC)
* `unit_available` (optional)
  * Type: Timestamp (UTC)
* `unit_back_at_station` (optional)
  * Type: Timestamp (UTC)

An `IncidentUnit` represents a single, continuous assignment of a unit to an incident. It is created when a unit is assigned to an incident.

Dispatch (`unit_dispatched`) may occur later or not at all.

When a unit is unassigned from an incident, `unit_unassigned_at` is set and the `IncidentUnit` record becomes immutable. Setting `unit_unassigned_at` corresponds to clearing `assigned_to_incident_id` in `UnitStatus` for that assignment.

Before `unit_unassigned_at` is set, an `IncidentUnit` may receive additional timestamps
reflecting the unit’s progress. `IncidentUnit` records are append-only; reassignment results in a new record.

Staffing and unit timestamps are copied from [`UnitStatus` updates](UnitStatus.md).
No inference or interpolation of timestamps is permitted.

When the system creates automatic intermediate state transitions in `UnitStatus` (see [Dispatch and Immediate Transitions](UnitStatus.md#dispatch-and-immediate-transitions)), each intermediate transition is a `UnitStatus` update and records its corresponding timestamp in `IncidentUnit`. For example, if a dispatcher marks a unit directly as `en_route`, the system transitions through `dispatched`, and both `unit_dispatched` and `unit_en_route` are recorded.

If a state is not transitioned through (neither explicitly nor via automatic intermediate transitions), its corresponding timestamp is left empty.

An incident may contain multiple `IncidentUnit` entries referencing the same unit, provided their assignment intervals do not overlap.


### IncidentLogEntry

* `id` (required)
  * Type: Nano ID
* `log_timestamp` (required)
  * Type: Timestamp (UTC)
  * Reflects the time the entry was recorded by the system and must not be supplied or overridden manually
* `dispatcher` (optional)
  * Type: User ID
  * Set to the dispatcher's user ID if the change resulted from dispatcher input
  * Empty if the change was triggered by the system
* `entry_type` (required)
  * Type: `manual` | `automatic`

For `manual` entries:
* `description` (required)
  * Type: Text

For `automatic` entries:
* `change_data` (required)
  * Type: Structured data (implementation detail, e.g., JSON)
  * Contains the type of change and the new value

#### Automatic Log Entry Triggers

An automatic `IncidentLogEntry` is created when any of the following changes occur:

* `state` change
* `incident_type` change
* `incident_priority` change
* `location` change
* `description` change
* A call is linked to the incident
* A call is detached from the incident
* An `IncidentUnit` is added

Automatic log entries are immutable and must not be edited or deleted.

#### Manual Log Entries

Dispatchers may append manual log entries with free-form text descriptions.


## Invariants

Always required:

* `id`
* `state`
* `incident_created`

Conditionally required:

* `incident_type`, `incident_priority`, and `location` must all be set before transition to:
  * `state → active`
  * `state → queued`
* `units` must contain at least one entry before:
  * `state → active`

Priority `N` incidents:

* Must not represent real-world emergencies
* Must not be linked to external calls originating from the public
* Must have an `incident_type` (operational orders have their own incident type codes)
* Must have a `location`; if not otherwise applicable, the assigned unit's station location may be used
* Are still subject to full logging and lifecycle rules

Unit timestamps:

* For a given `IncidentUnit`, timestamps must be monotonically non-decreasing when present and applicable (e.g. `unit_assigned_at ≤ unit_dispatched ≤ unit_en_route ≤ ...`)
  * No timestamp may precede `unit_assigned_at`.
  * If `unit_unassigned_at` is set, it must be greater than or equal to all other timestamps in the same IncidentUnit.

An incident must not transition to `ended` while there are any `IncidentUnit` records
with an unset `unit_unassigned_at` attribute.

## Validation Rules

* `description` fields (`Incident` and `IncidentLogEntry`) have a maximum length of 1000 UTF-8 characters


## Lifecycle

An incident progresses through the following states:

* `new`  
   Incident created; no operational decisions made yet   
* `queued`  
   Action is required, but no suitable units are currently available
* `active`  
  One or more units have been dispatched
* `monitored`  
  The situation is being observed without immediate dispatch
  Examples:
  * Smoke reports near an already active terrain fire
  * Post-clearance fire watch or inspection period
* `ended`  
  The incident has concluded and may be scheduled for archival. Archival is asynchronous and outside the scope of the domain model.

### Allowed State Transitions

* `new` → `queued` | `active` | `monitored` | `ended`
* `queued` → `active` | `monitored` | `ended`
* `active` → `monitored` | `ended`
* `monitored` → `queued` | `active` | `ended`

An incident in state `ended` must never transition to any other state.
If further action is required, a new incident must be created.

### State Transition Authorization

**Automatic transitions:**

* The system automatically transitions an incident to `active` when a unit is dispatched to the incident.

**Manual transitions:**

* A dispatcher may manually transition an incident to `queued`, `active`, or `monitored`, subject to the allowed state transitions.
* A dispatcher ends an incident by issuing an explicit end command, which transitions the incident to `ended`.

All state transitions, whether automatic or manual, must respect the allowed state transitions and invariants (e.g., all units must be unassigned before transitioning to `ended`).

## Archival

Archival removes an incident and its associated data from live operational storage.

### Preconditions

An incident may only be archived when:

* The incident is in state `ended`
* All `IncidentUnit` records have `unit_unassigned_at` set (guaranteed by the transition to `ended`)

### Archival Scope

When an incident is archived:

* The incident record is archived
* All linked [`Call`](Call.md) records are archived together with the incident
* All `IncidentUnit` records are archived together with the incident
* All `IncidentLogEntry` records are archived together with the incident

At the time of archival, there are no live references to the incident:

* `UnitStatus.assigned_to_incident_id` cannot reference the incident (units must be unassigned before `ended`)
* `Call.incident_id` references are archived together with the incident

Archival is asynchronous and the scheduling mechanism is outside the scope of the domain model.
