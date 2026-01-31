# Domain Concept: Unit Status

The Unit Status represents the **current operational state** of a unit in the system.
Every unit has **exactly one** Unit Status at any given time.

Unit Status reflects *current truth*, not history. Historical participation in incidents is represented separately via [`IncidentUnit`](Incident.md).

Changes to Unit Status may be of interest to other parts of the system and should be propagated.

## Attributes

* `unit` (required)
  * Type: Reference to [`Unit`](Unit.md)
* `state` (required)
  * Type:  
  `unavailable` | `available_over_radio` | `available_at_station` |  
  `assigned_radio` | `assigned_station` |  
  `dispatched` | `en_route` | `on_scene`
* `state_changed_at` (required)
  * Type: Timestamp (UTC)
* `staffing` (optional)
  * Type: Embedded [`Staffing`](Staffing.md)
* `staffing_changed_at` (optional)
  * Type: Timestamp (UTC)
* `coordinates` (optional)
  * Type: Decimal degrees (EPSG:4326)
* `coordinates_changed_at` (optional)
  * Type: Timestamp (UTC)
* `assigned_to_incident_id` (optional)
  * Type: Reference to [`Incident`](Incident.md)
* `assigned_to_incident_at` (optional)
  * Type: Timestamp (UTC)

## Invariants

* `state_changed_at` must always be set to the timestamp when `state` was changed
* `staffing_changed_at` must always be set to the timestamp when `staffing` was changed
* `coordinates_changed_at` must always be set to the timestamp when `coordinates` was changed
* `assigned_to_incident_at` must always be set when `assigned_to_incident_id` is set, and cleared when `assigned_to_incident_id` is cleared

All changes to Unit Status must be recorded in an **append-only audit log**.
The audit log does not need to be immediately accessible from any user interface.

Coordinate updates represent **transient telemetry**.  
They are not required to be retained in the append-only audit log and may be sampled, aggregated, or discarded according to non-functional requirements.

Multiple attributes may change as part of a single logical update, each with its own timestamp.

## Lifecycle

A unit is always in exactly one of the following states:

* `unavailable`  
The unit is not able to respond to incidents

* `available_over_radio`  
The unit is mobile, reachable over radio, and available for assignment

* `available_at_station`  
The unit is at its ordinary station and available for assignment

* `assigned_radio`  
The unit has been assigned to an incident while mobile

* `assigned_station`  
The unit has been assigned to an incident while at station

* `dispatched`  
The unit has been dispatched to an incident

* `en_route`  
The unit is en route to an incident

* `on_scene`  
The unit is at the scene of an incident

### Allowed State Transitions

* `unavailable` → `available_over_radio` | `available_at_station`
* `available_over_radio` → `assigned_radio` | `available_at_station` | `unavailable`
* `available_at_station` → `assigned_station` | `available_over_radio` | `unavailable`
* `assigned_radio` → `available_over_radio` | `dispatched`
* `assigned_station` → `available_at_station` | `dispatched`
* `dispatched` → `available_over_radio` | `available_at_station` | `en_route` | `unavailable`
* `en_route` → `available_over_radio` | `available_at_station` | `on_scene` | `unavailable`
* `on_scene` → `available_over_radio` | `available_at_station` | `unavailable`

When a unit is first added to the system, its initial state is `unavailable`.

## Semantics

### Operational Availability vs Assignment

Operational availability and incident assignment are **independent concepts**.

A unit may be operationally available (`available_over_radio`) while still assigned to an incident for the purpose of responsibility, logging, and incident closure.

Operational availability determines whether a unit may be assigned to *new* incidents.
Assignment determines whether a unit is still considered part of an incident’s timeline.

### Assignment Semantics

At any given time, a unit may be assigned to **at most one incident in Unit Status**.

Assignment history across incidents is represented exclusively via [`IncidentUnit`](Incident.md) records.

A unit may be assigned to an incident only when its state is:

* `available_over_radio`
* `available_at_station`

When this happens, the system performs the following automatic transitions:

* `available_over_radio` → `assigned_radio`
* `available_at_station` → `assigned_station`

Setting `assigned_to_incident_id` and `assigned_to_incident_at` represents the start of an operational assignment.

### Unassignment Semantics

A unit is automatically unassigned from an incident when it transitions to:
* `available_at_station`, or
* `unavailable`.

When automatic unassignment occurs:

* `assigned_to_incident_id` is cleared in Unit Status
* `unit_unassigned_at` is set on the corresponding [`IncidentUnit`](Incident.md)

This marks the end of the unit's operational responsibility for that incident.

#### Explicit Unassignment

A dispatcher may explicitly unassign a unit from an incident before dispatch. This is permitted only when the unit is in one of the following states:
* `assigned_radio`
* `assigned_station`

This allows a dispatcher to correct an accidental assignment before the unit has been dispatched.

When explicit unassignment is performed:

* If the unit is in `assigned_radio`, the system transitions it to `available_over_radio`
* If the unit is in `assigned_station`, the system transitions it to `available_at_station`
* `assigned_to_incident_id` is cleared in Unit Status
* `unit_unassigned_at` is set on the corresponding [`IncidentUnit`](Incident.md)

For reassignment to another incident, see [Reassignment Between Incidents](#reassignment-between-incidents).

#### Reporting Availability

When a unit transitions to `available_over_radio` while still assigned to an incident:

* `unit_available` is set on the corresponding [`IncidentUnit`](Incident.md)
* The unit remains assigned to the incident
* `assigned_to_incident_id` is NOT cleared

This records the timestamp when the unit reported operational availability, while preserving the assignment for administrative purposes (responsibility tracking, logging, incident closure coordination).

### Authority and Control

The following states may be set directly by the dispatcher or the unit:
* `unavailable`
* `available_over_radio`
* `available_at_station`
* `en_route`
* `on_scene`

The following states are set exclusively by the system:
* `assigned_radio`
* `assigned_station`
* `dispatched`

While a unit is in state `assigned_radio` or `assigned_station`, it cannot change its own state.
During this phase, state transitions are controlled exclusively by the system.

### Dispatch and Immediate Transitions

Normally, dispatching a unit results in:

* `assigned_*` → `dispatched`

However, a dispatcher may assign a unit and immediately mark it as `en_route` or `on_scene`, for example:

* when a unit reports an incident directly (drive-by)
* when a unit requests assignment to an existing incident

In these cases, the system performs the following automatic transitions:

* `en_route`:  
  (`assigned_radio` | `assigned_station`) → `dispatched` → `en_route`
* `on_scene`:  
  (`assigned_radio` | `assigned_station`) → `dispatched` → `en_route` → `on_scene`

### Reassignment Between Incidents

A unit may be transferred from one incident to another.

From the dispatcher's perspective, reassignment is a **single operation**. The dispatcher selects the unit and the target incident, and optionally specifies the desired state (`dispatched`, `en_route`, or `on_scene`).

The system performs the following steps automatically:

1. Transitions the unit to `available_over_radio` (setting `unit_available` on the current [`IncidentUnit`](Incident.md))
2. Clears `assigned_to_incident_id` and sets `unit_unassigned_at` on the current [`IncidentUnit`](Incident.md)
3. Assigns the unit to the new incident (setting `assigned_to_incident_id` and creating a new [`IncidentUnit`](Incident.md))
4. Transitions the unit to the dispatcher-specified state (or `assigned_radio` if no state was specified)

Each assignment interval is represented by a separate [`IncidentUnit`](Incident.md) record.

### Relationship to Incident Closure

An incident must not transition to ended while any [`IncidentUnit`](Incident.md) exists without `unit_unassigned_at` set.

Unit Status provides the operational signals that determine when assignment ends, but **incident closure rules are enforced at the [Incident](Incident.md) level**.

## Validation Rules

* `coordinates` must conform to [Coordinate Precision and Bounds](../NonFunctionalRequirements/Internationalization.md#coordinate-precision-and-bounds):
  * Maximum 6 decimal places
  * Latitude: 58.84° to 70.09°
  * Longitude: 19.08° to 31.59°

## Relevant NFRs

* [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md) - timestamps must be stored in UTC, coordinates must be in EPSG:4326 with defined precision and bounds
