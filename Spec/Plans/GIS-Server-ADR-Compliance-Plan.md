# GIS Server ADR Compliance Plan

This plan covers the work required to make the GIS Server implementation compliant
with the four non-compliant ADRs identified in the compliance audit of 2026-03-28.
ADR-0005 and ADR-0006 were found not applicable and require no action.

## References

- [ADR-0002: Docker Compose for Development HA Mode](../ADR/ADR-0002-docker-compose-development-ha.md)
- [ADR-0003: Partial HA Failure Testing with Docker Compose](../ADR/ADR-0003-partial-ha-testing.md)
- [ADR-0004: Shared Reverse Proxy for CAD and GIS Servers](../ADR/ADR-0004-shared-reverse-proxy.md)
- [ADR-0007: Domain Primitives](../ADR/ADR-0007-domain-primitives.md)
- [ADR-0009: ArchUnit Tests and Package Visibility](../ADR/ADR-0009-archunit-and-package-visibility.md)
- [NFR Security](../NonFunctionalRequirements/Security.md)
- [NFR Availability](../NonFunctionalRequirements/Availability.md)

---

## Plan Overview

| Phase | ADRs | Description | Tasks | Status |
|-------|------|-------------|-------|--------|
| 1 | ADR-0007 | Domain Primitive Consistency | 2 | Not Started |
| 2 | ADR-0009 | Package Visibility and ArchUnit | 4 | Not Started |
| 3 | ADR-0004 | Reverse Proxy Support | 4 | Not Started |
| 4 | ADR-0002, ADR-0003 | Deployment Infrastructure | 3 | Not Started |
| **Total** | | | **13** | |

Phase 1 must be completed before Phase 2 (ArchUnit tests verify the corrected
structure). Phases 3 and 4 are independent of Phases 1–2 and can be worked in
parallel. Phase 4 depends on Phase 3 (the nginx config references the context path).

---

## Phase 1: Domain Primitive Consistency (ADR-0007)

The `TileCoordinates` domain primitive already exists and validates all three tile
coordinate components together. However, `TileService` accepts raw `int` parameters,
and `WmtsController` creates a `TileCoordinates` only to discard it before calling
the service. These two tasks fix that round-trip.

---

### Task 1.1 — Change `TileService.getTile()` to accept `TileCoordinates`

**Status:** Not Started

**Description:**
Replace the three raw `int zoom, int row, int col` parameters with a single
`TileCoordinates coordinates` parameter. The service no longer needs to receive
or pass the individual values separately.

**Files to modify:**
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/service/tile/TileService.java`

**Changes:**
- Change the signature of `getTile(String layerName, int zoom, int row, int col)` to
  `getTile(String layerName, TileCoordinates coordinates)`.
- Inside the method body, replace references to `zoom`, `row`, `col` with
  `coordinates.zoom()`, `coordinates.row()`, `coordinates.col()`.
- The internal helper `getTilePath` may be refactored similarly or kept private with
  the coordinate values passed through.

**Acceptance criteria:**
- Method signature uses `TileCoordinates`, not three separate `int` parameters.
- All internal usages of the coordinates use the record accessor methods.
- No raw coordinate integers are visible in the public API of `TileService`.
- All existing `TileService` tests continue to pass (update call sites in tests).

**Dependencies:** None

---

### Task 1.2 — Update `WmtsController` to pass `TileCoordinates` to the service

**Status:** Not Started

**Description:**
`WmtsController.handleGetTile` currently creates a `TileCoordinates` instance for
validation only and then discards it, passing the raw integers to the service.
After Task 1.1, the service requires a `TileCoordinates` argument; this task updates
the controller to pass the already-created instance directly.

**Files to modify:**
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/api/wmts/WmtsController.java`

**Changes:**
- After parsing and validating the tile coordinates, retain the `TileCoordinates`
  instance (e.g., `var coords = TileCoordinates.of(zoom, row, col)`).
- Pass `coords` to `tileService.getTile(layerName, coords)`.
- Remove the now-redundant separate validation try-catch block that creates a
  `TileCoordinates` only to throw it away; the constructor throws on invalid input,
  so the single `TileCoordinates.of(...)` call is sufficient.

**Acceptance criteria:**
- The controller creates exactly one `TileCoordinates` instance per tile request.
- That instance is passed directly to the service without decomposing it back to ints.
- The duplicate validation block is removed.
- All existing integration tests for the WMTS endpoint continue to pass.

**Dependencies:** Task 1.1

---

## Phase 2: Package Visibility and ArchUnit (ADR-0009)

ADR-0009 requires two complementary mechanisms: package-private visibility by default,
and ArchUnit tests that enforce cross-package rules at build time. These four tasks
address the visibility violations first, then add ArchUnit to detect future regressions.

---

### Task 2.1 — Make service-internal classes package-private

**Status:** Not Started

**Description:**
The following classes are visible only within their own package yet are declared
`public`. Reducing their access to package-private enforces the boundary at compile
time and signals to future developers that these are implementation details.

**Files to modify (remove `public` from class declaration):**

In `service/geocode/`:
- `AddressPointSearcher.java` — only instantiated in `GisServer` and injected into `GeocodeService`
- `RoadSegmentSearcher.java` — same pattern
- `NamedPlaceSearcher.java` — same pattern
- `IntersectionSearcher.java` — same pattern

In `service/tile/`:
- `TileResampler.java` — only instantiated in `GisServer`, used only by `TileService`
- `TileCache.java` — same pattern
- `LayerDiscovery.java` — only instantiated in `GisServer` at startup

**Changes:**
- Remove the `public` modifier from each class declaration.
- `GisServer.java` (in the root server package) imports and instantiates all of these
  classes; because it is in a different package it will no longer compile once they are
  package-private. See Task 2.2 for how to resolve this.

**Acceptance criteria:**
- Each listed class is declared with no access modifier (package-private).
- The project compiles after the changes described in Tasks 2.2 and 2.3 are also applied.

**Dependencies:** None (but must be coordinated with Task 2.2)

---

### Task 2.2 — Resolve `GisServer` cross-package wiring and `DatabaseUnavailableException` coupling

**Status:** Not Started

**Description:**
Two coupling problems exist that must be resolved in conjunction with Task 2.1:

**Problem A — `GisServer` wires across package boundaries.**
`GisServer` (package `...gis.server`) directly imports and constructs classes from
`service/geocode/` and `service/tile/`. Once those classes become package-private, the
existing imports in `GisServer` will break. The recommended resolution is to introduce
package-level factory methods or constructors within each service package that assemble
the service graph and return only the public-facing service object. For example:
- `GeocodeService.create(DSLContext)` — a static factory in the `service/geocode` package
  that constructs the private searchers internally and returns a configured `GeocodeService`.
- `TileService.create(Path, Map<String,TileLayer>)` — similarly in `service/tile`.

This keeps `GisServer` free of knowledge about internal service collaborators.

**Problem B — `DatabaseUnavailableException` is in the service package but imported by
adapter-layer classes.**
`GeocodeController` and `GlobalExceptionHandler` both import
`DatabaseUnavailableException` from `service/geocode`. This creates a prohibited
adapter → service dependency. The fix is to move `DatabaseUnavailableException` to the
`api/geocode` package (or a shared `api` package), where it logically belongs as a
signal that the adapter layer must handle.

**Files to modify:**
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/service/geocode/GeocodeService.java` — add a static `create(DSLContext)` factory
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/service/tile/TileService.java` — add a static `create(Path, Map<String,TileLayer>)` factory (or `LayerDiscovery` result can be passed in as-is; see note below)
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/GisServer.java` — replace direct construction of internal searchers/helpers with factory method calls
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/service/geocode/DatabaseUnavailableException.java` — move to `api/geocode/` package
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/api/geocode/GeocodeController.java` — update import
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/api/error/GlobalExceptionHandler.java` — update import
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/service/geocode/GeocodeService.java` — update throw site import after move

Note: `LayerDiscovery` discovers layers from the filesystem and returns a
`Map<String, TileLayer>`. Since `TileService` already accepts this map as a constructor
argument, and `TileResampler` and `TileCache` have no parameters, the factory method
`TileService.create(Path tileDirectory, Map<String, TileLayer> layers)` can be a
simple static method that constructs the private collaborators internally.

**Acceptance criteria:**
- `GisServer` does not import any class from `service/geocode` or `service/tile`
  other than `GeocodeService` and `TileService` (the public service facades).
- `GisServer` does not import `LayerDiscovery`, `TileResampler`, `TileCache`,
  `AddressPointSearcher`, `RoadSegmentSearcher`, `NamedPlaceSearcher`, or
  `IntersectionSearcher`.
- `DatabaseUnavailableException` resides in `api/geocode` and is not imported by
  anything in `service/geocode`.
- The project compiles and all tests pass.

**Dependencies:** Task 2.1

---

### Task 2.3 — Add ArchUnit dependency to the parent POM

**Status:** Not Started

**Description:**
ArchUnit is not yet declared in the parent POM. It must be added so all Java containers
can use it without individually managing the version.

**Files to modify:**
- `Implementation/pom.xml`

**Changes:**
- Add an `archunit.version` property (use `1.3.0`).
- Add a `dependencyManagement` entry for `com.tngtech.archunit:archunit-junit5` with
  `<scope>test</scope>`.

**Acceptance criteria:**
- `archunit.version` property is present.
- `archunit-junit5` appears in `<dependencyManagement>` with test scope.
- The GIS Server POM can declare the dependency without specifying a version.

**Dependencies:** None

---

### Task 2.4 — Add ArchUnit dependency to the GIS Server POM and create architecture tests

**Status:** Not Started

**Description:**
An ArchUnit test class must be created that encodes the layering rules for the GIS
Server. The tests must run as part of the standard `mvn test` build and fail the build
on any violation.

**Files to create/modify:**
- `Implementation/servers/gis-server/pom.xml` — add `archunit-junit5` test dependency
- `Implementation/servers/gis-server/src/test/java/net/pkhapps/idispatchx/gis/server/GisServerArchitectureTest.java` — new file

**Architecture rules to encode (minimum required by ADR-0009):**

```
Root package: net.pkhapps.idispatchx.gis.server

Layers (for dependency direction enforcement):
  - "API Adapters"    : "..gis.server.api.."
  - "Auth Adapters"   : "..gis.server.auth.."
  - "Service"         : "..gis.server.service.."
  - "Repository"      : "..gis.server.repository.."
  - "Model"           : "..gis.server.model.."
  - "DB Infrastructure": "..gis.server.db.."

Rules:
  1. Service layer must not depend on API adapter or auth adapter layers.
  2. API adapters must not depend on the repository layer directly.
  3. Auth adapters must not depend on the service or repository layers.
  4. Model layer must not depend on any other layer.
  5. No cyclic dependencies between any packages.
```

**Acceptance criteria:**
- The test class exists and contains at least the five rules listed above.
- `mvn test` passes with the corrected structure from Tasks 2.1 and 2.2.
- Introducing a deliberate violation (e.g., a service importing an API class) causes
  the test to fail.

**Dependencies:** Tasks 2.1, 2.2, 2.3

---

## Phase 3: Reverse Proxy Support (ADR-0004)

ADR-0004 requires that backend services not assume direct public exposure and that
both subdomain and context-path proxy configurations work. The GIS Server has all
routes hardcoded to absolute paths and generates tile URLs without a configurable
prefix. These four tasks add the necessary flexibility.

---

### Task 3.1 — Add `GIS_CONTEXT_PATH` environment variable to `GisServerConfig`

**Status:** Not Started

**Description:**
Introduce an optional `GIS_CONTEXT_PATH` environment variable (default: empty string)
that specifies the URL path prefix under which the GIS Server is mounted behind a
reverse proxy. Valid values are either empty or start with `/` and do not end with `/`
(e.g., `/gis`).

**Files to modify:**
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/config/GisServerConfig.java`

**Changes:**
- Add `String contextPath` as a record component (validated to be empty or match
  the pattern `(/[^/]+)+` with no trailing slash).
- Add `ENV_CONTEXT_PATH = "GIS_CONTEXT_PATH"` constant.
- In the `load(ConfigLoader)` factory, read the optional property (default `""`).
- Update the compact constructor to validate the format.

**Acceptance criteria:**
- When `GIS_CONTEXT_PATH` is not set, `contextPath` is `""`.
- When set to `/gis`, `contextPath` is `"/gis"`.
- Values with a trailing slash (e.g., `/gis/`) are rejected with a clear error at startup.
- Values that do not start with `/` (other than the empty string) are rejected.

**Dependencies:** None

---

### Task 3.2 — Apply context path prefix to all route registrations

**Status:** Not Started

**Description:**
All route registrations in the three controllers and `GisServer` must be prefixed
with `config.contextPath()`. When the context path is empty, behavior is unchanged.

**Files to modify:**
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/api/wmts/WmtsController.java`
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/api/geocode/GeocodeController.java`
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/api/health/HealthController.java`
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/GisServer.java`

**Changes:**
- Pass `config.contextPath()` to each controller's `registerRoutes` method (add a
  `String contextPath` parameter to each `registerRoutes` signature).
- In each controller, prepend `contextPath` to all route path strings.
- In `GisServer`, pass `config.contextPath()` when registering the logout handler:
  `javalin.post(config.contextPath() + "/api/v1/auth/logout", logoutHandler)`.
- Add a CORS configuration to Javalin using the `CorsPlugin` provided by the Javalin
  framework. Introduce a `GIS_CORS_ALLOWED_ORIGINS` environment variable
  (comma-separated list, required when not in development mode). Configure the plugin
  to allow the listed origins and to forward the `Authorization` request header.

**Acceptance criteria:**
- With `GIS_CONTEXT_PATH=/gis`, all routes are registered under `/gis/wmts/...`,
  `/gis/api/v1/...`, and `/gis/health`.
- With `GIS_CONTEXT_PATH` unset, all routes remain at their original paths.
- CORS preflight requests (`OPTIONS`) to `/wmts/` and `/api/v1/geocode/` paths return
  the configured `Access-Control-Allow-Origin` header.
- Existing integration tests continue to pass (they use no context path, so behavior
  is unchanged from their perspective).

**Dependencies:** Task 3.1

---

### Task 3.3 — Fix `CapabilitiesGenerator` to use configurable base URL

**Status:** Not Started

**Description:**
The WMTS Capabilities XML document contains `ResourceURL` elements with hardcoded
`/wmts/...` template paths. When the GIS Server is deployed with a context path, these
URLs will point to the wrong location for tile clients. The generator must accept the
context path and prepend it.

**Files to modify:**
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/api/wmts/CapabilitiesGenerator.java`
- `Implementation/servers/gis-server/src/main/java/net/pkhapps/idispatchx/gis/server/GisServer.java`

**Changes:**
- Add a `String contextPath` parameter to the `CapabilitiesGenerator` constructor.
- In `appendLayer`, change the `ResourceURL template` value from
  `"/wmts/..."` to `contextPath + "/wmts/..."`.
- Pass `config.contextPath()` when constructing `CapabilitiesGenerator` in `GisServer`.

**Acceptance criteria:**
- With `GIS_CONTEXT_PATH=/gis`, the `ResourceURL` templates in the capabilities
  document begin with `/gis/wmts/`.
- With no context path, the templates begin with `/wmts/` (unchanged behavior).
- Existing capabilities-related tests are updated to cover both cases.

**Dependencies:** Tasks 3.1, 3.2

---

### Task 3.4 — Document proxy deployment requirements

**Status:** Not Started

**Description:**
The Security NFR states that `/health` must not be exposed through the public reverse
proxy. The ADR also requires that authorization headers be forwarded and that WebSocket
upgrade is supported for the CAD Server. These requirements are enforced through nginx
configuration (Phase 4), but must also be documented.

Create a short operations note that documents:
1. Which paths the reverse proxy must expose (`/api` and `/wmts` under the context path).
2. Which paths must not be proxied (`/health`).
3. The `Authorization` header must be forwarded (`proxy_set_header Authorization $http_authorization`).
4. WebSocket upgrade must be configured for CAD Server connections (not GIS Server,
   but documented for completeness).
5. OIDC redirect URIs must use the proxy's external URL for the CAD Server.

**Files to create:**
- `Implementation/deploy/docker/README.md`

**Acceptance criteria:**
- The README file exists and covers all five points above.
- It references the relevant ADRs and NFRs.

**Dependencies:** None (can be done at any time during Phase 3)

---

## Phase 4: Deployment Infrastructure (ADR-0002, ADR-0003)

The `Implementation/deploy/docker/` directory is empty. ADR-0002 requires a full
Docker Compose setup for development HA, and ADR-0003 requires that partial failure
testing be possible by stopping individual containers. These three tasks create the
necessary configuration.

Note: The CAD Server is not yet implemented. The docker-compose file created here
includes stub service definitions for CAD Server components so the overall structure
conforms to ADR-0002. Those stubs will be filled in when the CAD Server is implemented.

---

### Task 4.1 — Create nginx configuration for reverse proxy

**Status:** Not Started

**Description:**
Create an nginx configuration file that routes requests to the GIS Server replicas.
The configuration must support both subdomain-based and context-path-based routing
by providing two example server blocks (only one needs to be active at a time).
It must not expose the `/health` path to public clients.

**Files to create:**
- `Implementation/deploy/docker/nginx/nginx.conf`

**Configuration requirements:**
- Upstream block `gis_servers` referencing `gis-server-1:8080` and `gis-server-2:8080`.
- Subdomain server block: `server_name gis.localhost;` — proxies all paths except
  `/health`.
- Context-path server block (commented out by default): location `/gis/` proxied to
  the upstream.
- `proxy_set_header Authorization $http_authorization;` on proxy locations.
- `proxy_pass_request_headers on;`
- Do not include a `location /health` block in either server block.
- Stub `upstream cad_server` block (empty, to be filled when CAD Server is implemented).

**Acceptance criteria:**
- nginx starts without errors when the docker-compose file (Task 4.2) is used.
- A request to `http://gis.localhost/wmts/1.0.0/WMTSCapabilities.xml` is proxied to
  one of the two GIS Server replicas.
- A request to `http://gis.localhost/health` returns 404 from nginx (not proxied).

**Dependencies:** Task 3.2 (routes must be finalized before nginx paths are configured)

---

### Task 4.2 — Create `docker-compose.yml`

**Status:** Not Started

**Description:**
Create the main Docker Compose file for the development HA environment. It must
satisfy ADR-0002 (full system with GIS Server replicas) and ADR-0003 (explicit
dependency ordering, no implicit dependencies, individual containers stoppable).

**Files to create:**
- `Implementation/deploy/docker/docker-compose.yml`

**Services to include:**

| Service | Image | Notes |
|---------|-------|-------|
| `gis-db` | `postgis/postgis:17-3.5` | PostGIS database for GIS Server |
| `gis-server-1` | `idispatchx/gis-server` (built locally) | First GIS Server replica |
| `gis-server-2` | `idispatchx/gis-server` (built locally) | Second GIS Server replica |
| `proxy` | `nginx:1.27-alpine` | Shared reverse proxy |
| `cad-server-active` | Stub (`busybox` with `sleep infinity`) | Placeholder for CAD Server active node |
| `cad-server-standby` | Stub (`busybox` with `sleep infinity`) | Placeholder for CAD Server warm standby |

**Dependency and health-check requirements (ADR-0003):**
- `gis-db` must declare a `healthcheck` using `pg_isready`.
- `gis-server-1` and `gis-server-2` must declare `depends_on: gis-db: condition: service_healthy`.
- `gis-server-1` and `gis-server-2` must declare a `healthcheck` calling `GET /health`
  (using `wget` or `curl`).
- `proxy` must declare `depends_on: gis-server-1: condition: service_healthy` and
  `gis-server-2: condition: service_healthy`.
- No container may use `restart: always` or similar policy that masks failures during
  HA testing.

**Volumes:**
- `gis_tiles`: named volume mounted read-only into both GIS Server replicas at the
  path configured by `GIS_TILE_DIR`.
- `gis_db_data`: named volume for the PostGIS database.

**Environment variables for GIS Server replicas:**
- All required `GIS_*` variables must be supplied (via `.env` file or inline).
- `GIS_CONTEXT_PATH` must be set to match the nginx configuration.

**Acceptance criteria:**
- `docker compose up` starts all services without errors (given that a GIS Server
  image has been built and tile data is populated in the volume).
- Stopping `gis-server-1` with `docker compose stop gis-server-1` does not stop any
  other service.
- Stopping `gis-db` causes `gis-server-1` and `gis-server-2` health checks to fail
  (they return unhealthy) without crashing the proxy or other containers.
- Restarting `gis-db` after stopping it allows the GIS Server replicas to recover
  (connection pool retry on next request).

**Dependencies:** Tasks 3.1, 3.2, 4.1

---

### Task 4.3 — Create GIS Server `Dockerfile`

**Status:** Not Started

**Description:**
The docker-compose file references a locally-built `idispatchx/gis-server` image.
A `Dockerfile` for the GIS Server must be created so developers can build this image.

**Files to create:**
- `Implementation/servers/gis-server/Dockerfile`

**Requirements:**
- Multi-stage build: Maven build stage + slim runtime stage (`eclipse-temurin:25-jre`
  or equivalent).
- Pass `--enable-preview` to the Java runtime (consistent with the surefire config).
- Set `java.awt.headless=true` (consistent with the existing surefire argLine).
- Expose port `8080`.
- Entrypoint runs the GIS Server JAR with preview flags enabled.

**Acceptance criteria:**
- `docker build -t idispatchx/gis-server .` succeeds from `Implementation/servers/gis-server/`.
- The resulting container starts the GIS Server and responds to `GET /health` on
  port 8080.

**Dependencies:** None (can be done in parallel with Tasks 4.1 and 4.2)

---

## Execution Notes

### Recommended order

1. Tasks 1.1 → 1.2 (sequential; 1.2 depends on 1.1)
2. Tasks 2.1 + 2.2 together (coordinated; visibility changes and wiring fix must be
   applied in one coherent change to keep the build green)
3. Task 2.3 (independent; can be done any time)
4. Task 2.4 (after 2.1, 2.2, 2.3)
5. Tasks 3.1 → 3.2 → 3.3 (sequential within Phase 3; 3.4 is independent)
6. Tasks 4.1, 4.3 in parallel, then 4.2 (depends on 4.1)

### Phases 1–2 vs 3–4

Phases 1 and 2 (ADR-0007, ADR-0009) touch only Java source files and can be
developed and reviewed independently of Phases 3 and 4. A developer working on
Docker and nginx configuration does not need to wait for the domain primitive or
ArchUnit work to be done.

### Testing strategy

- Phase 1: Existing tile unit tests and WMTS integration tests provide coverage.
  Update their call sites to pass `TileCoordinates` objects.
- Phase 2: ArchUnit test must be the final deliverable of the phase and must pass
  cleanly before the phase is marked Done.
- Phase 3: Add integration test cases covering both empty and non-empty context paths
  for the WMTS capabilities URL generation (Task 3.3) and route resolution (Task 3.2).
- Phase 4: Manual smoke test using `docker compose up` followed by deliberate
  container kills. No automated Docker Compose tests are required.
