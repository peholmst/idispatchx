# UC: Close Incident

**Primary actor**: Dispatcher

## Preconditions

* Dispatcher is logged into System.
* Dispatcher has an incident open that is not in state `ended`.

## Postconditions

* The incident's `state` is `ended`.
* The incident's `incident_ended` timestamp is set to the time of closure.
* An automatic `IncidentLogEntry` has been created recording the state change to `ended`.

## Triggers

* Dispatcher issues a Close Incident command.

## Main Success Scenario

1. Dispatcher issues the Close Incident command.
2. System verifies that all `IncidentUnit` records for the incident have `unit_unassigned_at` set (i.e., no units are currently assigned).
3. System transitions the incident's `state` to `ended`.
4. System sets `incident_ended` to the current timestamp.
5. System creates an automatic `IncidentLogEntry` recording the state change.
6. System updates the incident details view to reflect the closed state.
7. The closed incident is visible to all dispatchers.
8. The incident becomes eligible for archival (archival is asynchronous and outside the scope of this use case).

## Exceptions

* **Exception: Incident is already in state `ended`**
  * System displays an error indicating the incident is already closed.
  * No changes are made.

* **Exception: Units are still assigned to the incident**
  * If any `IncidentUnit` record exists without `unit_unassigned_at` set, System displays an error listing the units that are still assigned.
  * Dispatcher must unassign all units before closing the incident.
  * No changes are made to the incident state.

Network or server failures are handled per the [NFR: Availability](../../NonFunctionalRequirements/Availability.md) degraded operation rules.

## Relevant NFRs

* [NFR: Availability](../../NonFunctionalRequirements/Availability.md) - degraded operation without CAD Server
* [NFR: Performance](../../NonFunctionalRequirements/Performance.md) - propagation of changes to other dispatchers within seconds
* [NFR: Security](../../NonFunctionalRequirements/Security.md) - authenticated dispatcher access

## Relevant Domain Concepts

* [Domain Concept: Incident](../../Domain/Incident.md) - incident lifecycle, `ended` state, `incident_ended` timestamp, `IncidentLogEntry`
