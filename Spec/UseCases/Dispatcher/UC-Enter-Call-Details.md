# UC: Enter Call Details

**Primary actor**: Dispatcher

## Preconditions

* Dispatcher is logged into System.

## Postconditions

* A new call has been recorded in System.

## Triggers

* Dispatcher issues a command for entering call details into System.

## Main Success Scenario

* A new call is visible to all dispatchers, with details appearing as they are entered.
* System allows the dispatcher to enter details about the call:
  * Caller contact information
  * Location information
  * Description of the call
* Dispatcher enters the call details.
* If the coordinates of the call are known, System checks if there are other active calls or incidents in the vicinity of the coordinates and shows them on the screen.
* If Dispatcher determines the call concerns an already reported incident, Dispatcher attaches the call to the incident for future reference, and ends the call.
* If Dispatcher determines the call concerns a new incident, Dispatcher creates a new incident (separate use case), attaches the call to the incident and continues the call in the context of the incident.
* If Dispatcher determines the call does not require a response, Dispatcher enters the reason for not taking further action, and ends the call.
* If the call is not attached to an active incident, System schedules the call for archival at the next convenient time.

## Exceptions

* Exception: Call location cannot be determined
  * Dispatcher can manually filter the list of active calls and incidents and attach the call to any of them if necessary.

## Relevant NFRs

* [NFR: Availability](../../NonFunctionalRequirements/Availability.md) - degraded operation without GIS Server
* [NFR: Performance](../../NonFunctionalRequirements/Performance.md) - propagation of changes to other dispatchers within seconds
* [NFR: Security](../../NonFunctionalRequirements/Security.md) - authenticated dispatcher access

## Relevant Domain Concepts

* [Domain Concept: Call](../../Domain/Call.md) - call details
* [Domain Concept: Location](../../Domain/Location.md) - location information
