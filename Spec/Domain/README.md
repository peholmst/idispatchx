# Domain Model README

This directory contains the domain concepts of iDispatchX.

Domain concepts describe the *meaning*, *structure*, and *invariants* of the information handled by the system. They define what the system knows, not how it is implemented or presented.

## Files

| File | Description |
|------|-------------|
| [AlertTarget.md](AlertTarget.md) | Delivery channel for dispatch alerts (client, email, SMS) |
| [Call.md](Call.md) | Emergency call record with caller info, location, state |
| [Incident.md](Incident.md) | Incident requiring emergency response, with units, log entries, lifecycle |
| [IncidentType.md](IncidentType.md) | Incident classification by nature and nominal scale |
| [Location.md](Location.md) | Location variants: exact address, intersection, freeform, coordinates |
| [MultilingualName.md](MultilingualName.md) | Name with multiple language versions (Finnish, Swedish, Sami) |
| [Municipality.md](Municipality.md) | Finnish municipality with code and multilingual name |
| [Staffing.md](Staffing.md) | Declared crew composition by role category (officers, subofficers, crew) |
| [Station.md](Station.md) | Physical location where units are quartered |
| [Unit.md](Unit.md) | Operational resource with identity and station assignment |
| [UnitStatus.md](UnitStatus.md) | Current operational state and incident assignment of a unit |

## Purpose

The domain model serves as the authoritative source for:

* Core concepts such as calls, incidents, locations, and reference data
* Required and optional attributes
* Invariants that must always hold
* Validation rules that constrain acceptable data
* Semantics needed for degraded operation

Domain concepts are shared across all containers and clients.

## Scope and Responsibilities

Domain concepts:

* Describe **what data represents**, not how it is stored or transmitted
* Define **invariants and validation rules**, but not UI behavior or error handling
* May reference Non-Functional Requirements when domain constraints are affected
* Must support degraded operation as defined in availability requirements

Domain concepts do **not**:

* Describe user interactions (see use cases)
* Define APIs, database schemas, or message formats
* Specify performance characteristics
* Contain implementation details

## Authority and Degraded Modes

Some domain concepts rely on authoritative reference data (e.g., municipalities, official names).  
The domain model explicitly supports degraded operation when such data is unavailable.

When reference data is unavailable:

* Manual entry of domain concepts may be allowed
* Manually entered data is explicitly marked as non-authoritative
* The system must not invent, infer, or reconcile authoritative data automatically

## Optionality and Absence of Data

The domain model distinguishes between:

* **Absence of a concept** (e.g., no location known)
* **Presence of a concept with unknown or incomplete information**

Null values must not be used to represent missing information inside an existing domain concept unless explicitly stated.  
When applicable, absence of information is represented using empty collections or optional attributes, as defined by each concept.

## Precision and Inference

The system must not artificially increase precision or completeness of domain data.

In particular:

* Coordinates must not be inferred if not explicitly provided
* Names must not be invented or translated automatically
* Language, location, or authority must not be assumed when unspecified

## Relationship to Other Specifications

* Use cases describe **behavior** using domain concepts
* Non-Functional Requirements constrain **how domain concepts may be used**
* C4 specifications describe **where domain concepts live and interact**

Domain concepts should be referenced by use cases and other specifications, not duplicated.

---

## Stability Expectations

Domain concepts are expected to be relatively stable over time.

Changes to domain concepts should be made cautiously, as they may affect:

* Multiple containers
* Persistent data
* Auditability
* Operational procedures

Backward compatibility should be preserved whenever possible.
