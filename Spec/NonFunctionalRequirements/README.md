# Non-Functional Requirements README

This directory contains the Non-Functional Requirements (NFRs) of iDispatchX.

Non-Functional Requirements define system-wide constraints and quality attributes such as availability, security, performance, maintainability, and internationalization.

## Files

| File | Description |
|------|-------------|
| [Availability.md](Availability.md) | HA configuration, failover, degraded modes, recovery objectives |
| [Internationalization.md](Internationalization.md) | Finland-specific geospatial data, coordinate systems, language support |
| [Maintainability.md](Maintainability.md) | 15-year lifespan guidelines, dependencies, documentation standards |
| [Performance.md](Performance.md) | UI response times, alert delivery, cross-dispatcher sync |
| [Security.md](Security.md) | Authentication, authorization, session handling, data access control |

## Authority

Non-Functional Requirements are **authoritative**.

In case of conflicts:
* NFRs take precedence over use cases
* NFRs take precedence over domain concepts
* NFRs take precedence over architectural and implementation decisions

No feature, design, or implementation choice may violate an NFR.

## Scope and Responsibilities

Non-Functional Requirements:

* Define constraints that apply across all containers and clients
* Describe *what must be true*, not *how it is implemented*
* May influence architecture, domain modeling, and use case behavior
* Are written to be testable through observable system behavior

Non-Functional Requirements do **not**:

* Describe user workflows or business processes
* Define data structures or domain semantics
* Specify APIs or protocols beyond what is necessary to state a constraint

## Structure

* Each NFR is defined in its own file
* Each NFR focuses on a single quality attribute or concern
* NFRs may reference each other where dependencies exist

## Usage Guidelines

* All NFRs must be read and understood before implementing any feature
* Use cases must reference relevant NFRs when behavior is constrained by them
* Domain concepts must align with applicable NFRs
* Implementation decisions must explicitly justify any trade-offs against NFRs

## Stability Expectations

Non-Functional Requirements are expected to be stable over time.

Changes to NFRs may have wide-ranging impact on:
* Architecture
* Domain models
* Existing implementations
* Operational procedures

Such changes should be made cautiously and reviewed thoroughly.
