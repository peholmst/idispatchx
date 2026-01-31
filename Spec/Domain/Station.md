# Domain Concept: Station

A Station represents a **physical location** where units and personnel are normally quartered when not assigned to incidents.

Stations are used to:

* determine alert fan-out to Station Alert Clients
* provide spatial context on dispatcher map views
* serve as a home location for units when returning from incidents

Stations do not represent organizational hierarchy, staffing levels, or operational readiness.

## Attributes

* `id` (required)
  * Type: Nano ID
* `state`: (required)
  * Type: `active` | `inactive`
* `name`: (required)
  * Type: Embedded [MultilingualName](MultilingualName.md)
* `location`: (required)
  * Type: Embedded [Location](Location.md)

## Invariants

* `id` must be unique within the Station reference set
* `state` must always be set
* `name` must be present (may be empty, but never null)
* `location` must be present and valid according to [`Location`](Location.md) domain rules
* Stations in state `inactive` must not be used for alert fan-out

## Semantics

Stations represent places, not capabilities.

* A Station does not imply that units are currently present
* A Station does not imply staffing levels or personnel availability
* A Station does not imply operational readiness

A Station’s `location` represents the nominal physical position of the station facility.
It must not be inferred, adjusted, or replaced based on unit movement or telemetry.

Stations may be referenced by units, alerts, and map displays, but they do not own or control those entities.

Changing a station’s state does not alter its identity, location, or historical references.
Reactivating a station restores its eligibility for alert fan-out without affecting past incidents or records.

## Authority and Management

Stations are authoritative reference data.

* Stations are managed by authorized administrators using the Admin Client
* Station definitions are stored in a local reference file
* Reference data is loaded at system startup and reloaded dynamically when modified

Stations are always available during normal and degraded operation.

Manual creation or modification of Stations by dispatchers during incident handling is not permitted.

Existing incidents and historical records retain references to Stations even if a Station later becomes `inactive`.

## Lifecycle

Stations have a simple, reversible lifecycle:

* `active`  
  The station is operational and may be used for alert fan-out and display

* `inactive`  
  The station is temporarily or permanently not operational and must not be used for alert fan-out  
  Historical references remain valid

Stations may transition freely between `active` and `inactive` states as administrative decisions change.

## Scope

Stations are valid only within the Finnish operational context.

## Relevant Non-Functional Requirements

* [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md)  
Station names must preserve all provided language variants and must not be inferred or translated automatically.

## Notes

Station intentionally does not model:

* personnel
* staffing
* command responsibility
* unit availability
* alerting logic

These concerns belong to other domain concepts and operational components.
