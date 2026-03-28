# Docker Deployment Operations Note

This document covers the reverse proxy requirements for deploying the GIS Server
(and eventually the CAD Server) behind a shared nginx reverse proxy, as required
by [ADR-0004](../../../Spec/ADR/ADR-0004-shared-reverse-proxy.md).

## Paths exposed through the reverse proxy

The reverse proxy **must** forward requests to the following path prefixes:

| Path prefix (relative to context path) | Service    |
|-----------------------------------------|------------|
| `/wmts/`                                | GIS Server |
| `/api/v1/`                              | GIS Server |

When using a URL context path (e.g., `GIS_CONTEXT_PATH=/gis`), all paths above
are mounted under that prefix:
- `/gis/wmts/`
- `/gis/api/v1/`

## Paths that must NOT be proxied

The `/health` endpoint **must not** be exposed through the public reverse proxy.
This is required by the [Security NFR](../../../Spec/NonFunctionalRequirements/Security.md).
The health endpoint is intended for internal infrastructure monitoring only
(container orchestration, load balancer health checks on the internal network).

Do not add a `location /health` (or `location /gis/health`) block in the public
server configuration.

## Authorization header forwarding

JWT bearer tokens are used for all authenticated GIS Server endpoints
(see [ADR-0004](../../../Spec/ADR/ADR-0004-shared-reverse-proxy.md)).
The reverse proxy must forward the `Authorization` header to the backend:

```nginx
proxy_set_header Authorization $http_authorization;
proxy_pass_request_headers on;
```

Without this, all authenticated requests will fail with `401 Unauthorized`.

## WebSocket upgrade (CAD Server)

The CAD Server (not yet implemented) requires WebSocket support for real-time
communication with Dispatcher, Station Alert, and Mobile Unit clients.
The reverse proxy must include WebSocket upgrade headers on CAD Server locations:

```nginx
proxy_http_version 1.1;
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
```

This is **not** required for the GIS Server (which uses plain HTTP only).

## OIDC redirect URIs (CAD Server)

When the CAD Server is deployed behind the reverse proxy, OIDC redirect URIs
configured in the identity provider must use the proxy's **external** URL, not
the backend's internal address. For example:

- Correct: `https://cad.example.com/api/v1/auth/callback`
- Incorrect: `http://cad-server-1:8080/api/v1/auth/callback`

This applies to the CAD Server OIDC client registration. The GIS Server does
not perform redirect-based OIDC flows (it validates bearer tokens directly).

## References

- [ADR-0004: Shared Reverse Proxy for CAD and GIS Servers](../../../Spec/ADR/ADR-0004-shared-reverse-proxy.md)
- [ADR-0002: Docker Compose for Development HA Mode](../../../Spec/ADR/ADR-0002-docker-compose-development-ha.md)
- [ADR-0003: Partial HA Failure Testing with Docker Compose](../../../Spec/ADR/ADR-0003-partial-ha-testing.md)
- [NFR Security](../../../Spec/NonFunctionalRequirements/Security.md)
