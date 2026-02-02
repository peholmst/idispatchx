# UC: Detach Call From Incident

**Primary actor**: Dispatcher

## Preconditions

* Dispatcher is logged into System.
* Dispatcher has a call open in state `active`.
* The call is attached to an incident with `outcome = attached_to_incident`.

## Postconditions

* The call's `incident_id` is cleared.
* The call's `outcome` is cleared.
* An automatic `IncidentLogEntry` has been created on the incident recording the call detachment.

## Triggers

* Dispatcher issues a command to detach the call from the incident.

## Main Success Scenario

1. Dispatcher issues a command to detach the call from the incident.
2. System clears the call's `incident_id`.
3. System clears the call's `outcome`.
4. System creates an automatic `IncidentLogEntry` on the incident recording the call detachment.
5. System no longer displays the incident details alongside the call details.
6. Dispatcher continues call handling and sets a new outcome before ending the call (see [UC-Enter-Call-Details](UC-Enter-Call-Details.md)).

## Exceptions

This use case has no exceptional flows.

The precondition that `outcome = attached_to_incident` prevents detachment of calls where the incident was created from the call (`outcome = incident_created`). Per the domain model, such calls cannot be detached.

Network or server failures are handled per the [NFR: Availability](../../NonFunctionalRequirements/Availability.md) degraded operation rules.

## Relevant NFRs

* [NFR: Availability](../../NonFunctionalRequirements/Availability.md) - degraded operation without CAD Server
* [NFR: Performance](../../NonFunctionalRequirements/Performance.md) - propagation of changes to other dispatchers within seconds
* [NFR: Security](../../NonFunctionalRequirements/Security.md) - authenticated dispatcher access

## Relevant Domain Concepts

* [Domain Concept: Call](../../Domain/Call.md) - call lifecycle, `outcome`, `incident_id`, detachment rules
* [Domain Concept: Incident](../../Domain/Incident.md) - `IncidentLogEntry` for call detachment
