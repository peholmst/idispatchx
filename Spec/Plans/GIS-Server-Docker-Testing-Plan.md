# GIS Server Docker Testing Plan

This plan addresses [issue #38](https://github.com/peholmst/iDispatchX/issues/38): make it possible to run the GIS Server as a Docker container for manual testing, using real NLS sample data for address lookups and map tiles.

## References

- [C4: Containers](../C4/Containers.md)
- [Technical Design: GIS Server REST API](../TechnicalDesigns/GIS-Server-REST-API.md)
- [Technical Design: GIS Data Import and Schema](../TechnicalDesigns/GIS-Data-Import-and-Schema.md)
- [NFR: Security](../NonFunctionalRequirements/Security.md)
- [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md)
- [ADR-0002: Docker Compose for Development HA Mode](../ADR/ADR-0002-docker-compose-development-ha.md)

---

## Goals

- Run GIS Server and all its dependencies in Docker for manual testing
- Use real NLS data (municipalities, geocoding, WMTS tiles) committed to `SampleData/`
- E2E tests continue to use mocks — no changes to that setup
- Simple commands to start and stop the test stack

## Non-goals

- Dockerizing the GIS Data Importer (a separate concern, separate issue)
- CI/CD integration of the test stack
- Changing E2E test setup (E2E tests keep their mock GIS Server)

---

## Sample Data Inventory

The following NLS data is already committed to `SampleData/`:

| Directory | Contents |
|-----------|----------|
| `SampleData/municipalities/codelist_kunta_1_20260101.json` | Finnish municipality codes and names (2026 edition) |
| `SampleData/maastotietokanta/avoin/etrs89/gml/L3/L33/*.zip` | NLS GML data (L3312R, L3313L, L3314L, L3314R, L3321R, L3323L, L3323R) |
| `SampleData/rasters/maastokartta/**/*.png+pgw` | Topographic map rasters at zoom levels 6–13 |
| `SampleData/rasters/taustakartta/**/*.png+pgw` | Background map rasters at zoom levels 7–14 |

**Tile layer names** derived from the raster directories:
- `maastokartta` — topographic map (NLS maastokartta), 8 zoom levels (6–7 + 8 + 10–11 + 13)
- `taustakartta` — background map (NLS taustakartta), 7 zoom levels (7–12 + 14)

**GML coverage**: the L33xx map sheets cover a region in northern-central Finland (same area as the rasters).

---

## Plan Overview

| Phase | Description | Tasks | Status |
|-------|-------------|-------|--------|
| 1 | Tile Fixtures | 2 | Not Started |
| 2 | Keycloak Configuration | 1 | Not Started |
| 3 | Test Docker Compose | 2 | Not Started |
| 4 | Convenience Scripts | 2 | Not Started |
| 5 | Documentation | 1 | Not Started |
| **Total** | | **8** | |

---

## Phase 1: Tile Fixtures

Generate the WMTS tile directory from the NLS rasters and commit it so the test container can bind-mount it without running the importer at startup.

### Task 1.1: Generate Tile Fixtures for `maastokartta`

**Status:** Not Started

**Description:**
Run the GIS Data Importer raster tile pipeline against all PNG files under `SampleData/rasters/maastokartta/`. Write output to `SampleData/tiles/` with layer name `maastokartta`. Commit the generated tile files.

Command (from `Implementation/`):
```bash
java --enable-preview -Djava.awt.headless=true \
  -cp 'tools/gis-data-importer/target/gis-data-importer-1.0.0-SNAPSHOT-dist.tar.gz!/lib/*' \
  net.pkhapps.idispatchx.gis.importer.Main \
  --tile-dir ../SampleData/tiles \
  --tile-layer maastokartta \
  --tiles $(find ../SampleData/rasters/maastokartta -name "*.png" | tr '\n' ' ')
```

Or use the unpacked distribution launcher:
```bash
tools/gis-data-importer/target/gis-data-importer-1.0.0-SNAPSHOT/gis-data-importer.sh \
  --tile-dir ../SampleData/tiles \
  --tile-layer maastokartta \
  --tiles $(find ../SampleData/rasters/maastokartta -name "*.png" | tr '\n' ' ')
```

Expected zoom levels: 6 (1000k overview), 7 (500k), 8 (250k), 10 (100k), 11 (50k), 13 (10k).

**Files to create/modify:**
- `SampleData/tiles/maastokartta/ETRS-TM35FIN/<zoom>/<row>/<col>.png` (generated, committed)

**Acceptance Criteria:**
- [ ] Tiles generated for all maastokartta source PNGs without skips or errors
- [ ] GIS Server discovers `maastokartta` layer and reports it in the health endpoint under `tileDirectory.layers`

**Dependencies:** None

---

### Task 1.2: Generate Tile Fixtures for `taustakartta`

**Status:** Not Started

**Description:**
Run the GIS Data Importer raster tile pipeline against all PNG files under `SampleData/rasters/taustakartta/`. Write output to `SampleData/tiles/` with layer name `taustakartta`. Commit the generated tile files.

Command (from `Implementation/`):
```bash
<launcher> \
  --tile-dir ../SampleData/tiles \
  --tile-layer taustakartta \
  --tiles $(find ../SampleData/rasters/taustakartta -name "*.png" | tr '\n' ' ')
```

Expected zoom levels: 7 (320k), 8 (160k), 9 (80k), 10 (40k), 11 (20k), 12 (10k), 14 (5k).

**Files to create/modify:**
- `SampleData/tiles/taustakartta/ETRS-TM35FIN/<zoom>/<row>/<col>.png` (generated, committed)

**Acceptance Criteria:**
- [ ] Tiles generated for all taustakartta source PNGs without skips or errors
- [ ] GIS Server discovers `taustakartta` layer and reports it in the health endpoint under `tileDirectory.layers`

**Dependencies:** None

---

## Phase 2: Keycloak Configuration

### Task 2.1: Create Test Keycloak Realm JSON

**Status:** Not Started

**Description:**
Create `Implementation/deploy/docker/test/keycloak-test-realm.json` with the `idispatchx` realm for combined GIS Server + Dispatcher Client testing. Base it on the existing `Implementation/clients/dispatcher-client/docker/keycloak-realm.json` and add:

1. **`gis-server` OIDC client**: A client with `clientId: "gis-server"` for JWT audience validation by the GIS Server. The GIS Server validates bearer tokens; it does not initiate redirect flows. The client type (public vs. confidential) should match what `GisServerConfig` expects.

2. **`Dispatcher` realm role**: A realm-level role named `Dispatcher` that appears in the `roles` token claim, per the Security NFR.

3. **Test dispatcher user**: A user (e.g., `dispatcher`) with a known password and the `Dispatcher` role, for manual API testing with `curl` or the Dispatcher Client.

The existing `dispatcher-client` registration from the base realm JSON is preserved unchanged.

**Files to create/modify:**
- `Implementation/deploy/docker/test/keycloak-test-realm.json` (new)

**Acceptance Criteria:**
- [ ] Keycloak imports the realm without errors
- [ ] GIS Server successfully fetches JWKS from Keycloak
- [ ] A token for the test dispatcher user is accepted by the GIS Server (HTTP 200 on an authenticated endpoint)
- [ ] JWT `roles` claim contains `Dispatcher`

**Dependencies:** None

---

## Phase 3: Test Docker Compose

### Task 3.1: Create `docker-compose-test.yml`

**Status:** Not Started

**Description:**
Create `Implementation/deploy/docker/docker-compose-test.yml` with a lightweight, single-instance testing stack. Differences from the production `docker-compose.yml`:

| Aspect | Production | Test |
|--------|-----------|------|
| GIS Server instances | 2 (HA) | 1 |
| Reverse proxy | nginx | None (ports exposed directly) |
| Keycloak | Not included | Included |
| Tile data | Docker volume | Bind-mount from `SampleData/tiles/` |
| Geocoding data | Must be imported separately | Loaded by data-seeder at startup |
| DB credentials | `.env` file | Hardcoded constants (test-only) |

**Services:**

| Service | Image | Purpose |
|---------|-------|---------|
| `keycloak` | `quay.io/keycloak/keycloak:26.0.0` | OIDC provider for test stack |
| `gis-db` | `postgis/postgis:17-3.5` | PostGIS database (postgres superuser for Flyway compat) |
| `gis-server` | `idispatchx/gis-server` | GIS Server (single replica) |
| `gis-data-seeder` | `eclipse-temurin:25-jre` | One-shot container; runs GIS Data Importer to seed geocoding data after GIS Server is healthy |

**Ports exposed on localhost:**
- `8080` → GIS Server HTTP
- `8180` → Keycloak HTTP
- `5432` → PostGIS (optional; useful for debugging with a DB client)

**Key configuration:**
- `GIS_OIDC_ISSUER=http://keycloak:8080/realms/idispatchx`
- `GIS_OIDC_CLIENT_ID=gis-server`
- `GIS_TILE_DIR=/tiles` (bind-mounted from `../../SampleData/tiles` relative to compose file)
- `GIS_CORS_ALLOWED_ORIGINS=http://localhost:5173`
- PostgreSQL superuser used for both DB init and GIS Server connection (Flyway can then CREATE EXTENSION as needed)

**Data seeder details:**
The `gis-data-seeder` container runs after `gis-server` reports healthy. It uses the GIS Data Importer JAR to import:
1. Municipality names: `SampleData/municipalities/codelist_kunta_1_20260101.json`
2. GML features: all `.zip` files under `SampleData/maastotietokanta/`

The importer JAR and sample data are bind-mounted into the container.

**Files to create/modify:**
- `Implementation/deploy/docker/docker-compose-test.yml` (new)

**Acceptance Criteria:**
- [ ] `docker compose -f docker-compose-test.yml up` starts all services without errors
- [ ] GIS Server health endpoint returns `{ "status": "UP" }` with database and tileDirectory components healthy
- [ ] Both `maastokartta` and `taustakartta` layers appear in the health response
- [ ] `GET /api/v1/geocode/search?q=<road-name>` returns results from the seeded GML data after the data-seeder completes
- [ ] `gis-data-seeder` exits with code 0

**Dependencies:** Tasks 1.1, 1.2, 2.1

---

### Task 3.2: Dispatcher Client Config for the Test Stack (Optional)

**Status:** Not Started

**Description:**
Create `Implementation/clients/dispatcher-client/public/config.test-gis.json` so developers can run the Dispatcher Client against the test stack:
- OIDC issuer: `http://localhost:8180/realms/idispatchx`
- GIS Server base URL: `http://localhost:8080`

Start the client with: `VITE_CONFIG_URL=/config.test-gis.json npm run dev`

**Files to create/modify:**
- `Implementation/clients/dispatcher-client/src/public/config.test-gis.json` (new — must be under `src/` because Vite's `root` is set to `src`)

**Acceptance Criteria:**
- [ ] Dispatcher Client starts against the test stack and authenticates via the test Keycloak
- [ ] Map tiles load from the test GIS Server
- [ ] Address lookup returns results from the seeded data

**Dependencies:** Tasks 2.1, 3.1

---

## Phase 4: Convenience Scripts

### Task 4.1: Create `start.sh`

**Status:** Not Started

**Description:**
Create `Implementation/deploy/docker/test/start.sh`. Run from the `Implementation/` directory. Steps:

1. Build the GIS Server JAR if not already built:
   ```bash
   ./mvnw package -pl servers/gis-server -am -DskipTests
   ```
2. Build the GIS Server Docker image:
   ```bash
   docker build -t idispatchx/gis-server -f servers/gis-server/Dockerfile .
   ```
3. Start the test compose stack:
   ```bash
   docker compose -f deploy/docker/docker-compose-test.yml up -d
   ```
4. Poll `http://localhost:8080/health` until `status` is `UP` (or time out after 120 s)
5. Print service URLs on success

Exit non-zero if the build or any service fails to start within the timeout.

**Files to create/modify:**
- `Implementation/deploy/docker/test/start.sh` (new, executable)

**Acceptance Criteria:**
- [ ] `./deploy/docker/test/start.sh` (from `Implementation/`) brings up the full stack from a clean state
- [ ] Script exits non-zero on failure
- [ ] URLs printed on success

**Dependencies:** Task 3.1

---

### Task 4.2: Create `stop.sh`

**Status:** Not Started

**Description:**
Create `Implementation/deploy/docker/test/stop.sh`. Stops and removes all containers and the test database volume:

```bash
docker compose -f deploy/docker/docker-compose-test.yml down -v
```

Run from `Implementation/`. Re-runnable when the stack is already stopped.

**Files to create/modify:**
- `Implementation/deploy/docker/test/stop.sh` (new, executable)

**Acceptance Criteria:**
- [ ] `./deploy/docker/test/stop.sh` stops all containers and removes the test DB volume
- [ ] Running when already stopped exits cleanly

**Dependencies:** Task 3.1

---

## Phase 5: Documentation

### Task 5.1: Create Test Stack README

**Status:** Not Started

**Description:**
Create `Implementation/deploy/docker/test/README.md` covering:

1. **Purpose** — manual testing with the real GIS Server; not for E2E tests (those use mocks)
2. **Prerequisites** — Docker + Compose v2, Java 25 JDK + Maven
3. **Quick start** — `./deploy/docker/test/start.sh` / `./deploy/docker/test/stop.sh`
4. **Services and ports** — table of services, ports, and what they serve
5. **Sample data coverage** — geographic area of the NLS L33xx sheets and included tile layers
6. **Manual API testing** — example: get a token via `curl` from Keycloak, then call a GIS Server endpoint
7. **Adding more sample data** — how to add NLS GML zips and rasters to `SampleData/`, re-run tile import, add new zips to the data-seeder configuration

**Files to create/modify:**
- `Implementation/deploy/docker/test/README.md` (new)

**Acceptance Criteria:**
- [ ] A developer unfamiliar with the project can follow the README to start the stack and make a successful geocoding request

**Dependencies:** Tasks 3.1, 4.1, 4.2

---

## Execution Notes

**Recommended order:**

1. **In parallel**: Tasks 1.1, 1.2, 2.1 — no dependencies between them
2. **Task 3.1** — depends on 1.1, 1.2, 2.1 (needs tiles, Keycloak config; data seeder uses importer)
3. **In parallel**: Tasks 4.1, 4.2 — both depend on 3.1
4. **Task 5.1** — documents the finished system
5. **Optional**: Task 3.2 — can be done at any time

**Note on postgres superuser in test compose:**
Using the PostgreSQL superuser (`POSTGRES_USER=postgres`) for both the DB and the GIS Server connection lets Flyway's `CREATE EXTENSION` statements in V1 succeed without a separate init script. Test-only; not permitted in production.

**Note on GML ZIP files:**
The GIS Data Importer now supports ZIP-compressed GML files (both via `--input <file>.zip` and via `--input-dir` scanning). The data-seeder passes the zip files directly.

**Note on tile directory size:**
The 5k taustakartta tiles (zoom 14, 0.5 m/px) will produce the most tiles due to their fine resolution. The commit may be large; consider using Git LFS for `SampleData/tiles/` if repository size becomes a concern.
