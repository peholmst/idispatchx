# UC: Create Incident

**Primary actor**: Dispatcher

## Preconditions

* Dispatcher is logged into System.

## Postconditions

* A new incident in state `new` has been recorded in System.
* The incident's `incident_created` timestamp is set to the time of creation.
* The incident is visible to all dispatchers.

## Triggers

* Dispatcher issues a Create Incident command.

## Main Success Scenario

1. Dispatcher issues the Create Incident command.
   * This may occur when a unit reports an incident directly (e.g., flagged by a citizen, drive-by observation) or when the dispatcher needs to create an incident proactively.
2. System creates a new incident in state `new` with `incident_created` set to the current timestamp.
3. System displays the incident details view for the new incident.
4. Dispatcher enters incident details as needed:
   * `incident_type`
   * `incident_priority`
   * `location`
   * `description`
5. The new incident is visible to all dispatchers.
6. Dispatcher proceeds with incident handling (assigning units, dispatching, etc.).

## Alternative Flow A: Create Incident With Initial Details

1. Dispatcher issues the Create Incident command with initial details (type, priority, location, description).
2. System creates a new incident in state `new` with the provided details and `incident_created` set to the current timestamp.
3. Use case continues from step 3 of the Main Success Scenario.

## Exceptions

This use case has no exceptional flows.

Network or server failures are handled per the [NFR: Availability](../../NonFunctionalRequirements/Availability.md) degraded operation rules.

## Relevant NFRs

* [NFR: Availability](../../NonFunctionalRequirements/Availability.md) - degraded operation without CAD Server
* [NFR: Performance](../../NonFunctionalRequirements/Performance.md) - propagation of changes to other dispatchers within seconds
* [NFR: Security](../../NonFunctionalRequirements/Security.md) - authenticated dispatcher access

## Relevant Domain Concepts

* [Domain Concept: Incident](../../Domain/Incident.md) - incident creation, `new` state, `incident_created` timestamp
* [Domain Concept: IncidentType](../../Domain/IncidentType.md) - incident classification
* [Domain Concept: Location](../../Domain/Location.md) - location information
