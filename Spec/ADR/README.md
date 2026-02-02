# Architectural Decision Records (ADR)

This directory contains Architectural Decision Records (ADRs) for iDispatchX.

ADRs document significant architectural decisions, their context, the chosen solution,
and the alternatives that were considered.

## Files

| File | Description |
|------|-------------|
| [ADR-0001-oidc-provider-neutrality.md](ADR-0001-oidc-provider-neutrality.md) | OIDC provider independence for production portability |
| [ADR-0002-docker-compose-development-ha.md](ADR-0002-docker-compose-development-ha.md) | Docker Compose for local HA-like development setup |
| [ADR-0003-partial-ha-testing.md](ADR-0003-partial-ha-testing.md) | Simulating component failures for HA testing |
| [ADR-0004-shared-reverse-proxy.md](ADR-0004-shared-reverse-proxy.md) | Single reverse proxy for CAD and GIS servers |
| [ADR-0005-session-state-during-failover.md](ADR-0005-session-state-during-failover.md) | WebSocket session handling during failover |
| [ADR-0006-wal-format-and-semantics.md](ADR-0006-wal-format-and-semantics.md) | Write-Ahead Log format, replay, and purging |
| [ADR-0007-domain-primitives.md](ADR-0007-domain-primitives.md) | Mandates domain primitives for type-safe validation |
| [ADR-0008-cad-server-ports-and-adapters.md](ADR-0008-cad-server-ports-and-adapters.md) | CAD Server hexagonal architecture with primary and secondary ports |
| [ADR-0009-archunit-and-package-visibility.md](ADR-0009-archunit-and-package-visibility.md) | ArchUnit tests and package visibility for enforcing architectural boundaries |

## Purpose

ADRs exist to capture *why* the system is designed the way it is, especially when:

* Multiple reasonable alternatives exist
* The decision has long-term architectural impact
* The rationale may not be obvious from code or diagrams

## Scope

ADRs may cover decisions related to:

* Technology choices
* Development and deployment practices
* Architectural constraints and trade-offs
* Testing and operational considerations

ADRs do **not** override Non-Functional Requirements or C4 specifications.
In case of conflict, NFRs and C4 documents take precedence.

## Stability

ADRs are historical records.

If a decision changes, a **new ADR** must be added describing the updated decision.
Existing ADRs must not be modified to reflect new decisions.
