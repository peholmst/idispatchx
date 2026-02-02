# UC: Attach Call To Incident

**Primary actor**: Dispatcher

## Preconditions

* Dispatcher is logged into System.
* Dispatcher has a call open in state `active`.
* The call is not already attached to an incident (i.e., `incident_id` is not set).
* An incident exists that is not in state `ended`.

## Postconditions

* The call's `outcome` is set to `attached_to_incident`.
* The call's `incident_id` references the selected incident.
* An automatic `IncidentLogEntry` has been created on the incident recording the call linkage.

## Triggers

* Dispatcher issues a command to attach the open call to an incident.

## Main Success Scenario

1. Dispatcher selects an incident to attach the call to.
2. Dispatcher issues a command to attach the call to the selected incident.
3. System sets the call's `outcome` to `attached_to_incident` and sets the call's `incident_id` to the selected incident.
4. System creates an automatic `IncidentLogEntry` on the incident recording the call linkage.
5. System displays the incident details alongside the call details.
6. Dispatcher continues call handling and ends the call when appropriate (see [UC-Enter-Call-Details](UC-Enter-Call-Details.md)).

## Exceptions

* **Exception: The selected incident is in state `ended`**
  * System displays an error indicating the incident cannot accept new calls.
  * Dispatcher may select a different incident or create a new incident (see [UC-Create-Incident-From-Call](UC-Create-Incident-From-Call.md)).

Network or server failures are handled per the [NFR: Availability](../../NonFunctionalRequirements/Availability.md) degraded operation rules.

## Relevant NFRs

* [NFR: Availability](../../NonFunctionalRequirements/Availability.md) - degraded operation without CAD Server
* [NFR: Performance](../../NonFunctionalRequirements/Performance.md) - propagation of changes to other dispatchers within seconds
* [NFR: Security](../../NonFunctionalRequirements/Security.md) - authenticated dispatcher access

## Relevant Domain Concepts

* [Domain Concept: Call](../../Domain/Call.md) - call lifecycle, `outcome`, `incident_id`
* [Domain Concept: Incident](../../Domain/Incident.md) - incident lifecycle, `IncidentLogEntry`
