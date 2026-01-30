# Use Cases README

This directory contains the use cases of iDispatchX, grouped by primary actor.

Use cases describe the **behavior** of the system as observed by its users and external actors. They define how the system is used to achieve specific goals under normal and exceptional conditions.

## Scope and Responsibilities

Use cases:

* Describe normal and exceptional flows of interaction
* Describe **observable behavior**, not implementation details
* Capture human decision-making and system responses
* May include degraded operation as defined in the Availability NFR
* Reference domain concepts without redefining them

Use cases do **not**:

* Define system architecture or container responsibilities (see C4 specifications)
* Define data structures, invariants, or validation rules (see domain model)
* Repeat Non-Functional Requirements

## Files

### Dispatcher

| File | Description |
|------|-------------|
| [UC-Close-Incident.md](Dispatcher/UC-Close-Incident.md) | Close an incident (stub) |
| [UC-Create-Incident.md](Dispatcher/UC-Create-Incident.md) | Create a new incident (stub) |
| [UC-Create-Incident-From-Call.md](Dispatcher/UC-Create-Incident-From-Call.md) | Create incident from an active call (stub) |
| [UC-Dispatch-Units.md](Dispatcher/UC-Dispatch-Units.md) | Dispatch units to an incident (stub) |
| [UC-Enter-Call-Details.md](Dispatcher/UC-Enter-Call-Details.md) | Record incoming call with caller info, location, description |
| [UC-Put-Incident-On-Hold.md](Dispatcher/UC-Put-Incident-On-Hold.md) | Put an incident on hold (stub) |

### Admin, Observer, Station, Unit

No use cases defined yet.

## Authority and Constraints

Use cases must be consistent with:

* C4 architectural specifications
* All applicable Non-Functional Requirements

When use case behavior is constrained by an NFR, the relevant NFR must be explicitly referenced.

Use cases must not introduce behavior that violates an NFR.

## Structure and Granularity

* Use cases are grouped by **primary actor**
* One use case describes one primary actor pursuing one goal
* Cross-cutting or multi-actor behavior must be described through separate use cases

## Usage Guidelines

* Read the relevant use case before implementing a feature
* Do not infer or invent behavior not explicitly described
* If required behavior is unclear or missing, ask for clarification
* Use use cases as the primary reference for behavioral correctness

## Evolution and Stability

Use cases may evolve as operational practices and requirements change.

Changes to use cases should be evaluated for impact on:
* domain concepts
* Non-Functional Requirements
* existing implementations
