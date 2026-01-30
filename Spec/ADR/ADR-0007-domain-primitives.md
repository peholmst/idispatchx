# ADR-0007: Domain Primitives

## Status
Accepted

## Context

The domain model defines concepts with validation rules and invariants.
These rules ensure data integrity and consistency across the system.

However, primitive types (strings, integers, booleans) carry no domain meaning by themselves.
A string representing a municipality code is indistinguishable from a string representing
a caller's phone number at the type level.

This leads to several problems:

* Validation logic is scattered and may be applied inconsistently
* Parameters of the same primitive type can be confused (e.g., swapping two string arguments)
* Domain rules are implicit rather than explicit in the code
* Invalid values can propagate through the system before being caught

## Decision

Domain primitives must be used wherever a value has domain-specific meaning or validation rules.

A domain primitive is a value object that:

* Wraps one or more primitive values with domain-specific meaning
* Validates all input in the constructor (valid at creation = always valid)
* Is immutable after construction
* Encapsulates the underlying value(s)

This decision applies to all implementations regardless of programming language.

Which specific types warrant domain primitives is an implementation detail.
As a guideline, domain primitives are appropriate when:

* The value has validation rules defined in the domain model
* The value represents a distinct domain concept (e.g., municipality code, phone number)
* Confusion with other values of the same underlying type is possible

## Consequences

* Validation is centralized in the domain primitive constructor
* Invalid values cannot exist; if a domain primitive instance exists, it is valid
* Type safety prevents parameter confusion at compile time
* Code becomes more self-documenting through explicit types
* Serialization adapters may be required for JSON, database, or wire formats
* Small runtime overhead for object creation (typically negligible)

## Cross-References

* [Domain Model README](../Domain/README.md) — Defines validation rules and invariants pattern (line 24)
* [NFR Maintainability](../NonFunctionalRequirements/Maintainability.md) — Explicitness over implicitness (line 15)
