# ADR-0002: Docker Compose for Development HA Mode

## Status
Accepted

## Context

iDispatchX supports high-availability (HA) deployments in production.
Developers need a practical way to run and test the system locally
without requiring complex infrastructure.

Development environments should approximate production topology
as closely as reasonable.

## Decision

Docker Compose is used to start the full iDispatchX system in a
single-machine HA-like configuration during development.

This includes:
* Multiple server containers where applicable
* Shared storage for WAL replication
* Reverse proxy configuration

Docker Compose is used only for development and testing, not as a
recommended production deployment mechanism.

## Consequences

* Developers can start the full system with minimal setup
* HA behavior can be exercised early
* Some production characteristics (latency, failure domains) are not replicated
* Docker Compose files may grow in complexity and must be maintained
