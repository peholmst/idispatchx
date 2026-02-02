# ADR-0009: ArchUnit Tests and Package Visibility for Architectural Enforcement

## Status
Accepted

## Context

Architectural decisions like ADR-0008 (ports-and-adapters) define boundaries between
components. However, in Java, these boundaries exist only as conventions unless actively
enforced. Developers can inadvertently introduce prohibited dependencies, and such
violations may go unnoticed until the architecture has significantly eroded.

Two complementary mechanisms exist to enforce architectural boundaries in Java:

1. **Package visibility** — Java's default (package-private) access modifier restricts
   access to classes and members within the same package. This is a compile-time guarantee.

2. **ArchUnit** — A library for writing unit tests that verify architectural rules,
   such as layer dependencies, naming conventions, and package coupling. This catches
   violations at test time.

Neither mechanism alone is sufficient:

* Package visibility cannot express rules across packages (e.g., "adapters may not
  depend on other adapters")
* ArchUnit tests can be ignored or forgotten if not part of the standard test suite

## Decision

All Java containers (CAD Server, GIS Server, GIS Data Importer) must:

1. **Use package visibility by default** — Classes and members should be package-private
   unless they need to be accessed from outside their package. This provides compile-time
   enforcement of boundaries within a package hierarchy.

2. **Include ArchUnit tests** — Each container must have ArchUnit tests that verify
   there is no prohibited coupling between architectural components. At minimum, these
   tests must verify:
   * Domain code does not depend on adapter code
   * Adapters do not depend on other adapters
   * Port interfaces reside in the appropriate packages
   * No cyclic dependencies between packages

ArchUnit tests must run as part of the standard test suite and fail the build on
violation.

## Consequences

* Architectural boundaries are enforced automatically, not just documented
* Package visibility provides immediate compiler feedback for local violations
* ArchUnit tests catch cross-package violations that package visibility cannot express
* New developers receive immediate feedback when they violate architectural rules
* ArchUnit adds a test dependency to Java containers
* Architectural rules must be explicitly codified in tests, which requires upfront effort
* Package structure must be designed to align with architectural boundaries

## Cross-References

* [ADR-0008 CAD Server Ports-and-Adapters](ADR-0008-cad-server-ports-and-adapters.md) — Architecture that ArchUnit tests will enforce
* [C4 Containers](../C4/Containers.md) — Lists Java containers subject to this decision
