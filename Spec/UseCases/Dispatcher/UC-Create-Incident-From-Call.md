# UC: Create Incident From Call

**Primary actor**: Dispatcher

## Preconditions

* Dispatcher is logged into System.
* Dispatcher has a call open in state `active`.
* The call is not already attached to an incident (i.e., `incident_id` is not set).

## Postconditions

* A new incident in state `new` has been recorded in System.
* The call's `outcome` is set to `incident_created`.
* The call's `incident_id` references the new incident.
* An automatic `IncidentLogEntry` has been created on the incident recording the call linkage.

## Triggers

* Dispatcher issues a command for creating a new incident from the open call.

## Main Success Scenario

1. Dispatcher issues a command to create a new incident from the open call.
2. System creates a new incident in state `new`.
3. System sets the call's `outcome` to `incident_created` and sets the call's `incident_id` to the new incident.
4. System creates an automatic `IncidentLogEntry` on the incident recording the call linkage.
5. If the call has a location, System copies it to the incident location.
   * The incident location becomes an independent copy; subsequent edits to either location do not affect the other.
   * Dispatcher can issue a command to copy the call location into the incident location at any time.
6. System displays the incident details alongside the call details.
7. The new incident is visible to all dispatchers.
8. Dispatcher continues call handling in the context of the new incident (see [UC-Enter-Call-Details](UC-Enter-Call-Details.md)).

## Exceptions

This use case has no exceptional flows.

Network or server failures are handled per the [NFR: Availability](../../NonFunctionalRequirements/Availability.md) degraded operation rules.

## Relevant NFRs

* [NFR: Availability](../../NonFunctionalRequirements/Availability.md) - degraded operation without CAD Server
* [NFR: Performance](../../NonFunctionalRequirements/Performance.md) - propagation of changes to other dispatchers within seconds
* [NFR: Security](../../NonFunctionalRequirements/Security.md) - authenticated dispatcher access

## Relevant Domain Concepts

* [Domain Concept: Call](../../Domain/Call.md) - call details, `outcome`, `incident_id`
* [Domain Concept: Incident](../../Domain/Incident.md) - incident lifecycle, `IncidentLogEntry`
* [Domain Concept: Location](../../Domain/Location.md) - location information
