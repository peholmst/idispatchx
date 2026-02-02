# UC: Assign Units To Incident

**Primary actor**: Dispatcher

## Preconditions

* Dispatcher is logged into System.
* Dispatcher has an incident open that is not in state `ended`.

## Postconditions

* One or more units have been assigned to the incident.
* For each assigned unit:
  * A new `IncidentUnit` record has been created on the incident with `unit_assigned_at` set.
  * The unit's `UnitStatus.assigned_to_incident_id` references the incident.
  * The unit's `UnitStatus.state` has transitioned to `assigned_radio` or `assigned_station`.
* An automatic `IncidentLogEntry` has been created on the incident for each unit assignment.

## Triggers

* Dispatcher issues a command to assign one or more units to the incident.

## Main Success Scenario

1. Dispatcher views the list of available units (units in state `available_over_radio` or `available_at_station`).
2. Dispatcher optionally filters the list by unit call sign.
3. Dispatcher selects one or more units to assign.
4. Dispatcher issues the Assign to Incident command.
5. For each selected unit, System:
   * Creates an `IncidentUnit` record on the incident with `unit_assigned_at` set to the current timestamp.
   * Sets the unit's `UnitStatus.assigned_to_incident_id` to the incident.
   * Transitions the unit's `UnitStatus.state`:
     * `available_over_radio` → `assigned_radio`
     * `available_at_station` → `assigned_station`
   * Creates an automatic `IncidentLogEntry` on the incident recording the unit assignment.
6. System updates the incident details view to show the newly assigned units.
7. The unit assignments are visible to all dispatchers.

## Alternative Flows

### Alternative Flow A: Reassign Unit From Another Incident

1. Dispatcher selects a unit that is currently assigned to another incident (in state `assigned_radio`, `assigned_station`, `dispatched`, `en_route`, or `on_scene`).
2. Dispatcher issues the Reassign to Incident command.
3. System performs the reassignment as a single operation:
   * Transitions the unit to `available_over_radio` (setting `unit_available` on the current `IncidentUnit`).
   * Clears `UnitStatus.assigned_to_incident_id` and sets `unit_unassigned_at` on the current `IncidentUnit`.
   * Creates a new `IncidentUnit` record on the target incident with `unit_assigned_at` set.
   * Sets `UnitStatus.assigned_to_incident_id` to the target incident.
   * Transitions the unit's `UnitStatus.state` to `assigned_radio`.
   * Creates an automatic `IncidentLogEntry` on the target incident recording the unit assignment.
4. Use case continues from step 6 of the Main Success Scenario.

### Alternative Flow B: Assign Unit With Immediate State

1. Dispatcher selects an available unit and specifies an immediate state (`en_route` or `on_scene`).
   * This is used when a unit reports an incident directly (drive-by) or requests assignment to an existing incident.
2. Dispatcher issues the Assign to Incident command with the specified state.
3. System creates the `IncidentUnit` record and sets `UnitStatus.assigned_to_incident_id` as in the Main Success Scenario.
4. System performs automatic state transitions through intermediate states:
   * For `en_route`: (`assigned_radio` | `assigned_station`) → `dispatching` → `dispatched` → `en_route`
   * For `on_scene`: (`assigned_radio` | `assigned_station`) → `dispatching` → `dispatched` → `en_route` → `on_scene`
5. For immediate transitions, the `dispatching` → `dispatched` transition occurs immediately without waiting for Alert Target acknowledgment. The `unit_dispatched` timestamp is set to the time the command was processed.
6. Each intermediate transition records its corresponding timestamp in `IncidentUnit`.
7. If the incident is not yet in state `active`, System automatically transitions it to `active`.
8. Use case continues from step 6 of the Main Success Scenario.

## Exceptions

* **Exception: Selected unit is in state `unavailable` or `inactive`**
  * System displays an error indicating the unit is not available for assignment.
  * Dispatcher may select a different unit.

* **Exception: Incident is in state `ended`**
  * System displays an error indicating the incident cannot accept unit assignments.
  * Dispatcher must select or create a different incident.

Network or server failures are handled per the [NFR: Availability](../../NonFunctionalRequirements/Availability.md) degraded operation rules.

## Relevant NFRs

* [NFR: Availability](../../NonFunctionalRequirements/Availability.md) - degraded operation without CAD Server; dispatcher can set unit status manually if Mobile Unit Client is unavailable
* [NFR: Performance](../../NonFunctionalRequirements/Performance.md) - propagation of changes to other dispatchers within seconds
* [NFR: Security](../../NonFunctionalRequirements/Security.md) - authenticated dispatcher access

## Relevant Domain Concepts

* [Domain Concept: Unit](../../Domain/Unit.md) - unit identity and call sign
* [Domain Concept: UnitStatus](../../Domain/UnitStatus.md) - unit state transitions, assignment semantics, reassignment between incidents
* [Domain Concept: Incident](../../Domain/Incident.md) - `IncidentUnit`, `IncidentLogEntry`, incident state transitions
