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
