# ADR-0005: Session State During Failover

## Status
Accepted

## Context

CAD Server supports high-availability deployments with a warm standby
that takes over using persisted operational state from the WAL
(per NFR Availability:90-94).

Clients connect to CAD Server via WebSocket for real-time events.
During failover, the question arises: what happens to active sessions?

NFR Security:50 states: "Authentication state must not survive server
failover unless explicitly intended."

## Decision

WebSocket sessions are not preserved during failover. All active
WebSocket connections are terminated when a failover occurs.

Clients must:
* Detect connection loss
* Reconnect automatically using exponential backoff with jitter
* Re-authenticate after reconnecting

No authentication or JWT validation state is persisted in the WAL.

## Consequences

* Failover implementation is simpler (no session state replication)
* Clients experience a brief interruption during failover
* All clients must implement robust reconnection logic
* Exponential backoff with jitter prevents thundering herd on failover recovery
* Re-authentication ensures session security is not compromised by failover

## Cross-References

* [NFR Security](../NonFunctionalRequirements/Security.md) — Auth state and failover (line 50)
* [NFR Availability](../NonFunctionalRequirements/Availability.md) — CAD Server failover (lines 90-94), client reconnection (line 102)
* [C4 Containers](../C4/Containers.md) — WebSocket endpoints (lines 67-69)
