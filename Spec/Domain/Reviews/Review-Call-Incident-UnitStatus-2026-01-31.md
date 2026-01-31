# Domain Concept Review: Call, Incident, UnitStatus

**Review Date:** 2026-01-31
**Concepts Reviewed:** Call.md, Incident.md, UnitStatus.md
**Related Concepts:** Location.md, Unit.md, IncidentType.md, Staffing.md

---

## Summary

This review identifies inconsistencies, contradictions, unclear specifications, and missing information across the Call, Incident, and UnitStatus domain concepts.

| Category | Count |
|----------|-------|
| Critical Issues | 2 |
| Inconsistencies | 2 |
| Unclear Specifications | 4 |
| Missing Specifications | 8 |

**Note:** Unit.md, IncidentType.md, and Staffing.md are intentionally empty placeholders and are excluded from this review.

---

## Critical Issues

### C1: Contradiction in Unit Reassignment Semantics

**Severity:** Critical
**Location:** UnitStatus.md, lines 96, 119-128, 174-178

**Contradiction:**

1. UnitStatus.md line 96 states:
   > "A unit may be operationally available (`available_over_radio`) while still assigned to an incident"

2. UnitStatus.md lines 119-128 state that a unit remains assigned until transitioning to `available_at_station` or `unavailable`. The `available_over_radio` state does NOT clear the assignment.

3. UnitStatus.md lines 174-176 state:
   > "A unit may be transferred from one incident to another. In such cases, the unit first transitions to `available_over_radio`, after which it may be assigned..."

4. UnitStatus.md lines 107-109 state a unit can only be assigned when in `available_over_radio` or `available_at_station` AND implicitly when not already assigned (line 103: "at most one incident").

**The contradiction:** If a unit transitions to `available_over_radio`, it remains assigned to the old incident. The spec then claims it can be assigned to a new incident, but this would violate the single-assignment constraint.

**Missing:** The mechanism for clearing the assignment when reassigning via `available_over_radio` is not specified.

**Recommendation:** Clarify the reassignment process. Options:
- Add `available_over_radio` as a trigger for clearing `assigned_to_incident_id` (changes unassignment semantics)
- Require units to pass through `available_at_station` or `unavailable` before reassignment
- Add an explicit "unassign" action that can be performed in the `available_over_radio` state

---

### C2: Contradiction in Priority N Incident Location Requirement

**Severity:** Critical
**Location:** Incident.md, lines 110-112, 120

**Contradiction:**

1. Lines 110-112 state:
   > "`incident_type`, `incident_priority`, and `location` must all be set before transition to: `state → active`, `state → queued`"

2. Line 120 states:
   > "May omit `location` but not `incident_type`"

**The contradiction:** Priority N incidents can omit location, but cannot transition to `active` or `queued` without location. This means Priority N incidents can only be in states `new`, `monitored`, or `ended`.

**Questions:**
- Is this intentional? Are operational orders (Priority N) never meant to be `active` or `queued`?
- If Priority N incidents can be `active`, the location requirement must be relaxed for them.

**Recommendation:** Clarify whether Priority N incidents can be `active` or `queued`. If yes, amend the invariant to exclude Priority N from the location requirement for these states.

---

## Inconsistencies

### I1: Domain README File Index is Incomplete

**Severity:** Medium
**Location:** Spec/Domain/README.md

The file index table does not include:
- UnitStatus.md
- Unit.md
- IncidentType.md
- Staffing.md
- Station.md (referenced by Location.md line 3)

Additionally, Incident.md is described as "(stub)" in the table but is fully specified.

**Recommendation:** Update the README.md file index to include all domain concept files with accurate descriptions.

---

### I2: Inconsistent Use of "Type" for References vs Embedded Types

**Severity:** Low
**Location:** Call.md, Incident.md, UnitStatus.md

The specifications inconsistently describe attribute types:
- Some use "Type: Reference to [X]" (e.g., Call.md line 31)
- Some use "Type: [X]" without "Reference" (e.g., Call.md line 23, Incident.md line 27)

For Location, the pattern "Type: [Location]" is used, but Location is likely embedded, not a reference.

**Recommendation:** Establish a consistent convention:
- "Reference to [X]" for foreign keys / entity references
- "[X]" or "Embedded [X]" for value objects / nested structures

---

## Unclear Specifications

### U1: IncidentUnit Timestamp Recording for Skipped States

**Severity:** Medium
**Location:** Incident.md lines 56-64, 75-76; UnitStatus.md lines 165-170

UnitStatus.md allows dispatchers to mark a unit directly as `en_route` or `on_scene`, with the system automatically creating intermediate transitions (e.g., `assigned_radio` → `dispatched` → `en_route`).

Incident.md states that timestamps are "copied from UnitStatus updates" and "No inference or interpolation of timestamps is permitted."

**Unclear:**
- When a unit skips `dispatched` and goes directly to `en_route`, should `unit_dispatched` be set in IncidentUnit?
- If the system creates automatic intermediate transitions, are those "UnitStatus updates" that should be recorded?
- If not, `unit_en_route` could be set without `unit_dispatched`, which might violate the monotonicity invariant depending on interpretation.

**Recommendation:** Clarify whether system-generated intermediate state transitions in UnitStatus result in IncidentUnit timestamps, or whether only explicitly reported states are recorded.

---

### U2: Call Outcome Mutability

**Severity:** Medium
**Location:** Call.md lines 27, 46-48

The specification states:
- `outcome` is required when `state = ended`
- `incident_id` may be assigned before the call is ended (line 49)

**Unclear:**
- Can `outcome` be set before the call ends?
- Can `outcome` be changed after it is set?
- What happens if a call is initially marked `incident_created` but later determined to be a `hoax`?

**Recommendation:** Specify whether `outcome` is mutable, and if so, under what conditions. Consider adding immutability rules similar to `incident_ended` in Incident.md.

---

### U3: Automatic Log Entry Generation

**Severity:** Medium
**Location:** Incident.md lines 95-97

The specification states:
> "All changes to an `Incident` result in an automatic `IncidentLogEntry`."

**Unclear:**
- What constitutes a "change"? (attribute updates, state transitions, call linkage, unit assignment?)
- Who is recorded as the `dispatcher` for automatic entries?
- What is the format of the `description` for automatic entries?
- Are automatic entries created for IncidentUnit timestamp updates?

**Recommendation:** Provide examples or a detailed list of events that trigger automatic log entries, and specify how the `dispatcher` and `description` fields are populated.

---

### U4: UnitStatus Coordinates Format and Precision

**Severity:** Low
**Location:** UnitStatus.md lines 25-28

Coordinates are specified as "Decimal degrees (EPSG:4326)" but:
- No precision requirements are stated (how many decimal places?)
- No validation rules for coordinate ranges
- The Internationalization NFR mentions EPSG:4326 but UnitStatus doesn't reference it

**Recommendation:** Add a validation rules section to UnitStatus.md specifying coordinate precision and valid ranges, and reference the Internationalization NFR.

---

## Missing Specifications

### M1: Call.incident_id Mutability

**Location:** Call.md

Not specified:
- Can `incident_id` be changed after initial assignment?
- Can a call be detached from an incident?

---

### M2: Incident Archival Impact on References

**Location:** Incident.md, UnitStatus.md

Not specified:
- What happens to `UnitStatus.assigned_to_incident_id` when an incident is archived?
- What happens to `Call.incident_id` when an incident is archived?
- Can references point to archived incidents?

---

### M3: UnitStatus Staffing Source

**Location:** UnitStatus.md

Not specified:
- How is `staffing` initially populated?
- Can staffing be set independently of the Unit's default staffing?
- Who can change staffing?

---

### M4: UnitStatus Validation Rules Section

**Location:** UnitStatus.md

Unlike Call.md and Incident.md, UnitStatus.md has no Validation Rules section. Missing:
- Maximum/minimum coordinate precision
- Any constraints on `state_changed_at`, `staffing_changed_at`, etc.

---

### M5: IncidentUnit Text Field Constraints

**Location:** Incident.md

No maximum length is specified for any IncidentUnit fields (though none are text fields currently, this should be explicitly stated if that is intentional).

---

### M6: Call Attachment to Existing Incident

**Location:** Call.md

The `attached_to_incident` outcome is listed but not explained:
- How does this differ from `incident_created`?
- Are there any validation rules for attaching to an existing incident?
- Can a call be attached to an incident in any state?

---

### M7: Incident State Transition Authorization

**Location:** Incident.md

Not specified:
- Who can perform state transitions?
- Are certain transitions restricted to specific roles?

---

### M8: NFR References

**Location:** Incident.md, UnitStatus.md

Call.md includes a "Relevant NFRs" section referencing Internationalization. Neither Incident.md nor UnitStatus.md has this section, despite both handling timestamps and UnitStatus handling coordinates.

**Recommendation:** Add "Relevant NFRs" sections to Incident.md and UnitStatus.md referencing Internationalization (timestamps in UTC, coordinates in EPSG:4326).

---

## Recommendations Summary

1. **High Priority:** Resolve the reassignment semantics contradiction (C1)
2. **High Priority:** Clarify Priority N incident location requirements (C2)
3. **Medium Priority:** Update Domain README.md file index (I1)
4. **Medium Priority:** Clarify IncidentUnit timestamp recording behavior (U1)
5. **Medium Priority:** Specify Call outcome mutability rules (U2)
6. **Medium Priority:** Detail automatic log entry generation rules (U3)
7. **Low Priority:** Add validation rules and NFR references to UnitStatus.md
8. **Low Priority:** Standardize reference notation across all domain concepts

---

*End of Review*
