# Domain Concept: Unit

A Unit represents an **operational resource** that may be assigned to incidents, dispatched, and tracked by the system.

A Unit has a stable internal identity used by the CAD Server and a nationally unique call sign used for external reference and archival purposes.

The CAD Server models **current units only**. Historical participation of units in incidents is captured via `IncidentUnit`.

---

## Attributes

* `id` (required)  
  * Type: Nano ID  
  * Internal identifier used within CAD Server

* `call_sign` (required)  
  * Type: String  
  * Nationally unique identifier for the unit  
  * Formatting rules are defined elsewhere and must be strictly enforced

* `state` (required)  
  * Type: `active` | `inactive`

* `station` (required)  
  * Type: Reference to [Station](Station.md)

---

## Invariants

* `id` must be unique within the system
* `call_sign` must be unique across all units in Finland
* `call_sign` is authoritative for external reference and archival
* `station` must always reference a valid Station
* Units in state `inactive` must not be considered for dispatch, assignment, alerting, or display in operational user interfaces
* A Unit must not transition to state `inactive` while it has a corresponding `UnitStatus`
  with `assigned_to_incident_id` set

---

## Semantics

### Identity and Authority

A Unit has two identifiers with distinct purposes:

* `id`  
  Used internally by the CAD Server for referencing and data integrity

* `call_sign`  
  Used as the authoritative identifier for archival, reporting, and cross-system reference

When incidents and related data are archived, the `call_sign` is preserved as the authoritative unit identifier.

---

### Active vs Inactive Units

Units represent **current operational resources**.

* `active` units are eligible for assignment, dispatch, alerting, and display
* `inactive` units represent temporarily or permanently decommissioned resources

Deactivating a unit acts as a **soft delete**:

* the Unit remains defined in the system
* the Unit is visible only in the Admin Client
* the Unit must not appear in dispatcher views, maps, alerts, or selection lists

Reactivating a unit restores its operational visibility without changing its identity or historical references.

Deactivation must not be used to implicitly end a unit’s participation in an incident.

A Unit may only be deactivated after it has been explicitly unassigned from all incidents.


---

### Relationship to Unit Status

Every Unit has exactly one corresponding `UnitStatus`.

The lifecycle relationship between Unit and UnitStatus is strict:

* When a Unit is created, a corresponding `UnitStatus` **must be created**
* When a Unit is deleted, the corresponding `UnitStatus` **must be deleted**
* When a Unit is deactivated, the `UnitStatus` **remains**, but must not be surfaced in operational contexts

UnitStatus represents **current operational truth** and is not a historical record.

---

### Scope of the Domain Concept

The Unit domain concept intentionally does **not** model:

* staffing (see `Staffing`)
* current operational state (see `UnitStatus`)
* historical incident participation (see `IncidentUnit`)
* capabilities or qualifications

In future versions, Units may be extended with capability metadata to support **optional response suggestion mechanisms**.  
Such extensions must remain advisory and must not imply automatic dispatch or inferred behavior.

---

## Authority and Management

Units are authoritative reference data.

* Units are managed by authorized administrators using the Admin Client
* Unit definitions are stored in a local reference file
* Reference data is loaded at system startup and reloaded dynamically when modified

Manual creation, modification, or deletion of Units by dispatchers during incident handling is not permitted.

---

## Lifecycle

Units have a simple lifecycle:

* `active`  
  The unit is operational and available for use by the system

* `inactive`  
  The unit is not operational and must not participate in system operations

Units may transition freely between `active` and `inactive` states.

Deletion of a Unit removes it entirely from the system and also removes its associated UnitStatus.  
Deletion is an administrative operation and is not reversible.

---

## Scope

Units are valid only within the Finnish operational context.

---

## Relevant Non-Functional Requirements

* [NFR: Maintainability](../NonFunctionalRequirements/Maintainability.md)  
  Unit identity and lifecycle must remain stable over long system lifetimes

---

## Notes

Unit intentionally models **existence and identity**, not behavior.

All operational behavior is expressed through:

* `UnitStatus` (current state)
* `IncidentUnit` (historical participation)

This separation prevents timeline inference and preserves auditability.
