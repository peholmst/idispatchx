# ADR-0008: CAD Server Ports-and-Adapters Architecture

## Status
Accepted

## Context

CAD Server is the central operational hub of iDispatchX. It handles commands and events
from multiple client types (Dispatcher, Mobile Unit, Station Alert, Admin) and integrates
with external systems (SMTP, SMS Gateway) and persistence mechanisms (CAD Archive, CAD WAL).

As the system grows, clear separation between domain logic and infrastructure concerns
becomes critical for:

* Testability — domain logic should be testable without spinning up real databases, WebSockets, or external services
* Maintainability — changing a persistence mechanism or adding a new client type should not require modifying core business logic
* Flexibility — different deployment scenarios may require swapping implementations (e.g., in-memory WAL for testing, different SMS providers)

A traditional layered architecture risks tight coupling between business logic and
infrastructure, making these goals harder to achieve.

## Decision

CAD Server will use a ports-and-adapters (hexagonal) architecture.

The domain and application logic reside at the center, exposed through well-defined ports.
Adapters connect the outside world to these ports.

### Primary Ports (Inbound)

Primary ports define how external actors interact with the application. Each client type
has its own adapter that translates protocol-specific requests into application commands:

| Adapter | Protocol | Purpose |
|---------|----------|---------|
| Dispatcher Client Adapter | REST + WebSocket | Call/incident management, unit dispatch, event subscriptions |
| Mobile Unit Client Adapter | REST + WebSocket | Status updates, location reporting, alert acknowledgments |
| Station Alert Client Adapter | WebSocket | Alert event subscriptions |
| Admin Client Adapter | REST | Administrative operations |

The specific primary port interfaces these adapters connect to are yet to be decided
and will emerge as use cases are implemented.

### Secondary Ports (Outbound)

Secondary ports define how the application interacts with external systems and
infrastructure. The application depends on port interfaces, not concrete implementations:

| Port | Implementation | Purpose |
|------|----------------|---------|
| Archive Port | CAD Archive (PostgreSQL) | Persisting closed calls and incidents |
| WAL Port | CAD WAL (file system) | Write-ahead logging for recovery and HA |
| Email Port | SMTP Server | Sending email notifications |
| SMS Port | SMS Gateway | Sending SMS notifications |

## Consequences

* Domain logic is isolated from transport protocols and infrastructure details
* Each adapter can be developed, tested, and replaced independently
* Unit testing domain logic requires only port stubs/mocks, not full infrastructure
* Integration testing can verify adapters against real infrastructure in isolation
* New client types or external integrations require only new adapters, not core changes
* Port interfaces must be designed carefully to avoid leaking infrastructure concerns
* Developers must understand the architectural boundaries when adding features

## Cross-References

* [C4 Containers](../C4/Containers.md) — Defines CAD Server responsibilities and integrations
* [ADR-0006 WAL Format and Semantics](ADR-0006-wal-format-and-semantics.md) — WAL implementation details
