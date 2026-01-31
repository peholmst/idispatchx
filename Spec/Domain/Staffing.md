# Domain Concept: Staffing

Staffing represents the **declared crew composition** of a unit at a given point in time.

Staffing consists of counts of personnel by role category and does not identify individual persons.
It reflects operational capacity, not identity, qualifications, or availability of specific crew members.

Examples:

* An incident command unit may have staffing `1+0+1` (1 officer, 0 subofficers, 1 crew member)
* A pumper may have staffing `0+1+3` (0 officers, 1 subofficer, 3 crew members)

Staffing may change over time and is always interpreted in the context in which it is referenced (e.g. [UnitStatus](UnitStatus.md), [IncidentUnit](Incident.md)).

## Attributes

* `officers` (optional)
  * Type: Integer
  * Default value: `0`
* `subofficers` (optional)
  * Type: Integer
  * Default value: `0`
* `crew` (optional)
  * Type: Integer
  * Default value: `0`

All attributes are explicit counts. If not provided, each attribute defaults to zero.

## Invariants

* No attribute may be negative
* The sum of all attributes may be zero (e.g. unmanned unit or unknown staffing)

## Semantics

Staffing represents **declared staffing**, not verified presence.

* Staffing does not imply that personnel are physically present
* Staffing does not imply readiness, qualification, or fitness
* Staffing does not change automatically based on unit movement or incident state

When referenced from other domain concepts:

* `UnitStatus.staffing` represents the unit’s current declared staffing
* `IncidentUnit.unit_staffing` represents staffing as recorded for that assignment interval

No inference or automatic adjustment of staffing values is permitted.

## Notes

Staffing intentionally avoids modeling individual personnel, certifications, or shift details.
Such concepts, if required, belong in separate domain models.
