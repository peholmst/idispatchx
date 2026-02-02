# UC: Dispatch Units

**Primary actor**: Dispatcher

## Preconditions

* Dispatcher is logged into System.
* Dispatcher has an incident open that is not in state `ended`.
* The incident has `incident_priority` set.
* The incident has `incident_type` set.
* The incident has `location` set.
* The incident has at least one unit assigned (at least one `IncidentUnit` without `unit_unassigned_at`).

## Postconditions

* For each newly dispatched unit:
  * The unit's `UnitStatus.state` has transitioned to `dispatching`.
  * Alerts have been sent to the unit's configured Alert Targets.
* If the incident was not already in state `active`, it has transitioned to `active`.
* For each re-dispatched unit (already in `dispatching`, `dispatched`, `en_route`, or `on_scene` state):
  * Alerts have been re-sent to the unit's configured Alert Targets.
  * No timestamps or state changes have occurred.

## Triggers

* Dispatcher issues a Dispatch New Units command, or
* Dispatcher issues a Dispatch Selected Unit command.

## Main Success Scenario: Dispatch New Units

1. Dispatcher issues the Dispatch New Units command.
2. System identifies all units assigned to the incident that have never been dispatched (units in state `assigned_radio` or `assigned_station`).
3. For each identified unit, System:
   * Transitions the unit's `UnitStatus.state` to `dispatching`.
   * Sends alerts to the unit's configured Alert Targets.
4. If the incident is not in state `active`, System transitions it to `active`.
5. System updates the incident details view to show units in `dispatching` state.
6. When the first Alert Target for a unit acknowledges that the alert has been technically delivered:
   * System transitions the unit's `UnitStatus.state` from `dispatching` to `dispatched`.
   * System sets `unit_dispatched` on the corresponding `IncidentUnit` to the acknowledgment timestamp.
7. The dispatch status is visible to all dispatchers.

## Alternative Flow A: Dispatch Selected Unit

1. Dispatcher selects one or more units from the incident's assigned units.
2. Dispatcher issues the Dispatch Selected Unit command.
3. For each selected unit:
   * **If the unit is in state `assigned_radio` or `assigned_station`:**
     * System transitions the unit's `UnitStatus.state` to `dispatching`.
     * System sends alerts to the unit's configured Alert Targets.
     * When the first Alert Target acknowledges delivery, System transitions to `dispatched` and sets `unit_dispatched` on the corresponding `IncidentUnit`.
   * **If the unit is already in state `dispatching`, `dispatched`, `en_route`, or `on_scene`:**
     * System re-sends alerts to the unit's configured Alert Targets.
     * No timestamps or state changes occur.
4. If the incident is not in state `active`, System transitions it to `active`.
5. Use case continues from step 5 of the Main Success Scenario.

## Alternative Flow B: Manual Dispatch Confirmation

1. Dispatcher observes a unit in `dispatching` state (e.g., after dispatch timeout notification or when Alert Target acknowledgment is unavailable).
2. Dispatcher confirms through other means (e.g., radio) that the unit has received the alert.
3. Dispatcher issues a Manual Dispatch Confirmation command for the unit.
4. System:
   * Transitions the unit's `UnitStatus.state` from `dispatching` to `dispatched`.
   * Sets `unit_dispatched` on the corresponding `IncidentUnit` to the current timestamp.
   * Creates an automatic `IncidentLogEntry` recording the manual dispatch confirmation.
5. No alerts are sent.

## Exceptions

* **Exception: Incident is missing required attributes**
  * If `incident_priority`, `incident_type`, or `location` is not set, System displays an error indicating which attributes are missing.
  * Dispatcher must set the missing attributes before dispatching.

* **Exception: No units available to dispatch**
  * If Dispatch New Units is issued but no units are in `assigned_radio` or `assigned_station` state, System displays a message indicating there are no new units to dispatch.
  * Dispatcher may use Dispatch Selected Unit to re-alert already-dispatched units.

* **Exception: Unit has no active Alert Targets**
  * If a unit to be dispatched has no active Alert Targets, System displays a warning indicating the unit cannot receive alerts.
  * The unit is not transitioned to `dispatching` state.
  * Dispatcher may contact the unit over radio and use Manual Dispatch Confirmation (Alternative Flow B) to complete the dispatch.

* **Exception: Dispatch timeout**
  * If a unit remains in `dispatching` state longer than the configured timeout period without any Alert Target acknowledgment:
    * System creates an automatic `IncidentLogEntry` recording the dispatch timeout.
    * System notifies the dispatcher.
  * Dispatcher may take manual action (e.g., contact the unit over radio).
  * Dispatcher may use Manual Dispatch Confirmation (Alternative Flow B) if they confirm the unit received the alert through other means.

* **Exception: Alert delivery fails to all Alert Targets**
  * If alerts cannot be delivered to any of a unit's Alert Targets, System displays a warning.
  * The unit remains in `dispatching` state until timeout or manual intervention.
  * Dispatcher may use Manual Dispatch Confirmation (Alternative Flow B) if they confirm the unit received the alert over radio.

* **Exception: Incident is in state `ended`**
  * System displays an error indicating the incident cannot accept dispatches.
  * Dispatcher must create a new incident if further dispatch is required.

Network or server failures are handled per the [NFR: Availability](../../NonFunctionalRequirements/Availability.md) degraded operation rules.

## Relevant NFRs

* [NFR: Availability](../../NonFunctionalRequirements/Availability.md) - alerts must reach units through at least one channel; degraded operation without all alert channels
* [NFR: Performance](../../NonFunctionalRequirements/Performance.md) - alerts must be delivered to Station Alert Clients and Mobile Unit Clients within seconds
* [NFR: Security](../../NonFunctionalRequirements/Security.md) - authenticated dispatcher access

## Relevant Domain Concepts

* [Domain Concept: Unit](../../Domain/Unit.md) - unit identity
* [Domain Concept: UnitStatus](../../Domain/UnitStatus.md) - `dispatching` and `dispatched` states, state transitions, dispatch timeout
* [Domain Concept: Incident](../../Domain/Incident.md) - `IncidentUnit`, `unit_dispatched` timestamp, incident state transitions to `active`
* [Domain Concept: AlertTarget](../../Domain/AlertTarget.md) - configured channels for alert delivery to units
