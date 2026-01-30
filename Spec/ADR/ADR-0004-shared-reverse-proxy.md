# ADR-0004: Shared Reverse Proxy for CAD and GIS Servers

## Status
Accepted

## Context

For development, demos, and small-scale deployments, it is desirable
to run the CAD Server and GIS Server on the same physical machine.

Accessing multiple backend services should be simple for:
* developers
* demos
* local testing

## Decision

The system must support running both the CAD Server and GIS Server
behind a single reverse proxy.

The reverse proxy may differentiate services using:
* subdomains, or
* URL context paths

Both approaches must be supported.

## Consequences

* Demos and local deployments are simplified
* TLS termination can be centralized
* Backend services must not assume direct public exposure
* Path handling and CORS configuration must be correct
* Reverse proxy must support WebSocket upgrade for CAD Server connections (used by Dispatcher, Station Alert, and Mobile Unit clients)
* Authorization headers (JWT bearer tokens) must be forwarded to backend servers
* OIDC redirect URIs must use the proxy's external URL, not backend internal URLs

## Cross-References

* [C4 Containers](../C4/Containers.md) — WebSocket endpoints (lines 67-69), JWT auth (lines 72, 218)
* [NFR Security](../NonFunctionalRequirements/Security.md) — TLS encryption requirement
