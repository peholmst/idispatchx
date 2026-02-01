# UC: Enter Call Details

**Primary actor**: Dispatcher

## Preconditions

* Dispatcher is logged into System.

## Postconditions

* A call has been recorded in System.
* The call's `receiving_dispatcher` is set to the current dispatcher.
* The call's `call_started` timestamp has been recorded.

If the call has ended:

* The call's `state` is `ended`.
* The call's `call_ended` timestamp has been recorded.
* The call's `outcome` has been set.
* If `outcome` is `incident_created` or `attached_to_incident`, the call's `incident_id` references the associated incident.
* If `outcome` is `caller_advised`, `hoax`, `accidental`, or `other_no_actions_taken`, the call's `outcome_rationale` has been recorded.

## Triggers

* Dispatcher issues a command for entering call details into System.

## Main Success Scenario

1. Dispatcher issues a command to enter call details.
2. System creates a new call in state `active` with the current dispatcher as `receiving_dispatcher` and the current time as `call_started`.
3. The new call is visible to all dispatchers, with details appearing as they are entered.
4. System allows the dispatcher to enter details about the call:
   * Caller contact information (`caller_name`, `caller_phone_number`)
   * Location information
   * Description of the call
5. Dispatcher enters the call details.
6. If the coordinates of the call are known, System checks for other active calls or incidents in the vicinity and displays them on screen.
7. Dispatcher determines the appropriate action based on call content:
   * **7a. Call concerns an already reported incident:**
     1. Dispatcher attaches the call to the existing incident.
     2. System sets the call's `outcome` to `attached_to_incident` and `incident_id` to the selected incident.
     3. System creates an automatic `IncidentLogEntry` on the incident recording the call linkage.
     4. Dispatcher ends the call.
   * **7b. Call concerns a new incident:**
     1. Dispatcher creates a new incident (see [UC-Create-Incident-From-Call](UC-Create-Incident-From-Call.md)).
     2. Dispatcher continues call handling in the context of the new incident.
     3. Dispatcher ends the call when appropriate.
   * **7c. Call does not require a response:**
     1. Dispatcher selects the appropriate `outcome` (`caller_advised`, `hoax`, `accidental`, or `other_no_actions_taken`).
     2. Dispatcher enters the `outcome_rationale`.
     3. Dispatcher ends the call.
8. When the call is ended, System sets `state` to `ended` and records `call_ended` timestamp.
9. If the call is not attached to an incident, System schedules the call for independent archival.

## Exceptions

* **Exception: Call location cannot be determined**
  * Dispatcher can manually filter the list of active calls and incidents and attach the call to any of them if necessary.
  * Dispatcher can enter location details manually based on information from the caller.

## Relevant NFRs

* [NFR: Availability](../../NonFunctionalRequirements/Availability.md) - degraded operation without GIS Server; addresses and coordinates can be entered manually
* [NFR: Performance](../../NonFunctionalRequirements/Performance.md) - propagation of changes to other dispatchers within seconds
* [NFR: Security](../../NonFunctionalRequirements/Security.md) - authenticated dispatcher access

## Relevant Domain Concepts

* [Domain Concept: Call](../../Domain/Call.md) - call lifecycle, `state`, `outcome`, `incident_id`, `outcome_rationale`
* [Domain Concept: Incident](../../Domain/Incident.md) - `IncidentLogEntry` for call linkage
* [Domain Concept: Location](../../Domain/Location.md) - location information
