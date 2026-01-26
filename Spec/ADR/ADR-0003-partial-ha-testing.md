# ADR-0003: Partial HA Failure Testing with Docker Compose

## Status
Accepted

## Context

High availability is a critical non-functional requirement of iDispatchX.
However, HA logic is difficult to validate without the ability to simulate failures.

Developers need a way to intentionally stop or kill system components and
observe system behavior under partial failure conditions.

## Decision

The Docker Compose setup must support partial HA testing.

It must be possible to:
* Stop or kill individual containers
* Observe failover behavior
* Continue operating the system in degraded or HA modes

No container should rely on undeclared implicit dependencies that prevent
isolated failure testing.

## Consequences

* HA logic can be tested during development
* Degraded modes are exercised realistically
* Docker Compose configuration must avoid tight coupling
* Startup and shutdown ordering must be explicit
