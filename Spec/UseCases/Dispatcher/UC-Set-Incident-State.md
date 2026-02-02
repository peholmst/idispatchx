# UC: Set Incident State

**Primary actor**: Dispatcher

## Preconditions

* Dispatcher is logged into System.
* Dispatcher has an incident open that is not in state `ended`.

## Postconditions

* The incident's `state` is updated to the target state.
* An automatic `IncidentLogEntry` has been created recording the state change.

## Triggers

* Dispatcher issues a Set Incident State command with a target state (`queued`, `active`, or `monitored`).

## Main Success Scenario

1. Dispatcher issues the Set Incident State command with a target state.
2. System verifies the transition from the current state to the target state is allowed per the domain model.
3. System verifies all state-specific invariants are satisfied (see State-Specific Invariants below).
4. System transitions the incident's `state` to the target state.
5. System creates an automatic `IncidentLogEntry` recording the state change.
6. System updates the incident details view to reflect the new state.
7. The updated incident is visible to all dispatchers.

## State-Specific Invariants

Before transitioning to certain states, the following must be satisfied:

**Transition to `queued`:**
* `incident_type` must be set
* `incident_priority` must be set
* `location` must be set

**Transition to `active`:**
* `incident_type` must be set
* `incident_priority` must be set
* `location` must be set
* At least one `IncidentUnit` must exist (unit must be assigned)

**Transition to `monitored`:**
* No additional invariants beyond allowed transitions

## Exceptions

* **Exception: Incident is in state `ended`**
  * System displays an error indicating the incident is closed and cannot be modified.
  * No changes are made.

* **Exception: Invalid state transition**
  * If the transition from the current state to the target state is not in the allowed transitions, System displays an error indicating the transition is not permitted.
  * No changes are made.

* **Exception: Missing required fields**
  * If transitioning to `queued` or `active` and `incident_type`, `incident_priority`, or `location` is not set, System displays an error listing the missing fields.
  * No changes are made.

* **Exception: No units assigned**
  * If transitioning to `active` and no `IncidentUnit` records exist, System displays an error indicating at least one unit must be assigned.
  * No changes are made.

Network or server failures are handled per the [NFR: Availability](../../NonFunctionalRequirements/Availability.md) degraded operation rules.

## Relevant NFRs

* [NFR: Availability](../../NonFunctionalRequirements/Availability.md) - degraded operation without CAD Server
* [NFR: Performance](../../NonFunctionalRequirements/Performance.md) - propagation of changes to other dispatchers within seconds
* [NFR: Security](../../NonFunctionalRequirements/Security.md) - authenticated dispatcher access

## Relevant Domain Concepts

* [Domain Concept: Incident](../../Domain/Incident.md) - incident lifecycle, allowed state transitions, state-specific invariants, `IncidentLogEntry`
