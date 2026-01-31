# Domain Concept Review: All Domain Concepts

**Review Date:** 2026-01-31
**Concepts Reviewed:** Call.md, Incident.md, IncidentType.md, Location.md, MultilingualName.md, Municipality.md, Staffing.md, Station.md, Unit.md, UnitStatus.md

---

## Summary

This review identifies inconsistencies, contradictions, unclear specifications, and missing information across all domain concepts following the resolution of issues from the previous review.

| Category | Count |
|----------|-------|
| Critical Issues | 0 |
| Inconsistencies | 2 |
| Unclear Specifications | 3 |
| Missing Specifications | 4 |

The domain model is substantially improved from the previous review. No critical contradictions remain.

---

## Inconsistencies

### I1: IncidentType Code Maximum Length

**Severity:** Low
**Location:** IncidentType.md, line 25

The specification states:
> "`code` must not exceed 1000 characters"

A maximum of 1000 characters seems excessive for a code field. Typical incident type codes are short alphanumeric identifiers (e.g., "FIRE01", "MED03").

**Recommendation:** Reduce the maximum length to a more reasonable value (e.g., 20-50 characters) or document why such a large limit is needed.

---

### I2: Staffing Default Values vs Required Attributes

**Severity:** Low
**Location:** Staffing.md, lines 17-25

The specification states that `officers`, `subofficers`, and `crew` are all "(required)" with "Default value: `0`".

If attributes have default values, they are typically not marked as required since the default can be applied. This creates ambiguity about whether the caller must explicitly provide zero or whether zero is inferred.

**Recommendation:** Clarify whether:
- These attributes must always be explicitly provided (remove "Default value"), or
- These attributes default to zero if not provided (change from "required" to "optional with default")

---

## Unclear Specifications

### U1: IncidentUnit.unit_back_at_station Trigger

**Severity:** Medium
**Location:** Incident.md, lines 63-64; UnitStatus.md

`IncidentUnit` includes a `unit_back_at_station` timestamp, but it is unclear when this timestamp is set:

- UnitStatus does not have a state that explicitly represents "back at station"
- The `available_at_station` state exists, but transitioning to it triggers automatic unassignment
- If unassignment clears the IncidentUnit link, how can `unit_back_at_station` be recorded?

**Questions:**
- Is `unit_back_at_station` set when the unit transitions to `available_at_station`?
- If so, is it set before or after `unit_unassigned_at`?
- Can both timestamps have the same value?

**Recommendation:** Document the exact conditions under which `unit_back_at_station` is set and its relationship to automatic unassignment.

---

### U2: Operational Availability and Assignment Constraint

**Severity:** Low
**Location:** UnitStatus.md, lines 96-99

The specification states:
> "A unit may be operationally available (`available_over_radio`) while still assigned to an incident"
> "Operational availability determines whether a unit may be assigned to *new* incidents."

However, a unit can only be assigned to one incident at a time (line 103). If a unit is `available_over_radio` but still assigned to an incident, it cannot be assigned to a new incident without explicit unassignment or reassignment.

**Recommendation:** Clarify that operational availability is a *necessary but not sufficient* condition for new assignment. The unit must also not be currently assigned to another incident (or must be explicitly unassigned/reassigned).

---

### U3: IncidentLogEntry for IncidentUnit Timestamp Updates

**Severity:** Low
**Location:** Incident.md, lines 108-119

The automatic log entry triggers include "An `IncidentUnit` is added" but do not mention updates to IncidentUnit timestamps (e.g., `unit_dispatched`, `unit_en_route`, `unit_on_scene`, `unit_available`).

**Question:** Should updates to IncidentUnit timestamps trigger automatic log entries?

**Recommendation:** Clarify whether IncidentUnit timestamp updates are logged, and if so, add them to the trigger list.

---

## Missing Specifications

### M1: Call.id Uniqueness

**Severity:** Low
**Location:** Call.md

Unlike other domain concepts (Incident, Unit, Station), Call.md does not explicitly state that `id` must be unique.

**Recommendation:** Add uniqueness constraint for `Call.id` in the Invariants section.

---

### M2: Staffing Maximum Values

**Severity:** Low
**Location:** Staffing.md

The specification states that no attribute may be negative (line 31) but does not specify maximum values for `officers`, `subofficers`, or `crew`.

**Questions:**
- Is there a practical upper limit?
- Should the system validate against unreasonable values (e.g., 1000 officers)?

**Recommendation:** Either add reasonable maximum values or explicitly state that no upper bound is enforced.

---

### M3: Location Text Field Validation

**Severity:** Low
**Location:** Location.md

Validation rules specify maximum length for `additional_details` (1000 characters) but not for:
- `address_number` (ExactAddress variant)

**Recommendation:** Add maximum length constraints for `address_number` or explicitly state that no limit applies.

---

### M4: UnitStatus Initial State Timestamp

**Severity:** Low
**Location:** UnitStatus.md

When a UnitStatus is created for a new unit (initial state `unavailable`, line 88), the specification does not clarify what `state_changed_at` should be set to.

**Question:** Should `state_changed_at` be set to the unit creation timestamp?

**Recommendation:** Clarify the initial value of `state_changed_at` when UnitStatus is first created.

---

## Positive Observations

The following areas are well-specified and consistent:

1. **Type notation** is now consistent across all domain concepts ("Reference to [X]" vs "Embedded [X]")
2. **NFR references** are present in all applicable domain concepts
3. **Coordinate handling** is consistently specified with precision and bounds
4. **Archival semantics** are clearly defined with proper reference handling
5. **State transition rules** are explicit and well-documented
6. **Timestamp handling** consistently references UTC storage requirements
7. **Cross-references** between domain concepts are accurate and bidirectional
8. **Mutability rules** are clearly specified for Call outcome and incident linkage

---

## Recommendations Summary

1. **Low Priority:** Reduce IncidentType code maximum length (I1)
2. **Low Priority:** Clarify Staffing default value semantics (I2)
3. **Medium Priority:** Document `unit_back_at_station` trigger conditions (U1)
4. **Low Priority:** Clarify operational availability vs assignment constraint (U2)
5. **Low Priority:** Clarify IncidentUnit timestamp update logging (U3)
6. **Low Priority:** Add Call.id uniqueness constraint (M1)
7. **Low Priority:** Consider Staffing maximum values (M2)
8. **Low Priority:** Add Location text field validation (M3)
9. **Low Priority:** Clarify UnitStatus initial state timestamp (M4)

---

*End of Review*
