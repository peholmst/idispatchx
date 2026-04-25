# GIS Server Test Stack

Manual testing environment for the GIS Server using real NLS sample data.
This stack is **not** used by E2E tests — those use a mock GIS Server.

## Purpose

Provides a single-host Docker environment with:
- GIS Server (single replica)
- Keycloak (OIDC provider)
- PostGIS database (seeded with NLS GML geocoding data)
- Map tiles bind-mounted from `SampleData/tiles/` (generated locally, not committed to git)

## Prerequisites

- Docker with Compose v2 (`docker compose`)
- Java 25 JDK and Maven (for building the GIS Server and Data Importer)

## Quick Start

Run from the `Implementation/` directory:

```bash
# Start all services (generates tiles if needed, builds images, seeds data, waits for healthy)
./deploy/docker/test/start.sh

# Stop all services and remove the test DB volume
./deploy/docker/test/stop.sh
```

`start.sh` automatically generates tiles the first time it runs.
To regenerate tiles manually (e.g. after adding new rasters):

```bash
./deploy/docker/test/generate-tiles.sh
```

## Tile Fixtures

Tiles are generated locally from PNG rasters in `SampleData/rasters/` and written to
`SampleData/tiles/` (which is gitignored due to its size — ~500 MB).

`start.sh` detects if tiles are missing and runs `generate-tiles.sh` automatically.
Generation takes a few minutes and requires ~4 GB of heap for the taustakartta layer.

**Tile layers:**

| Layer           | Zoom levels    | Resolution        |
|-----------------|----------------|-------------------|
| `maastokartta`  | 6–8, 10–11, 13 | 128m → 1m/px      |
| `taustakartta`  | 7–12, 14       | 64m → 0.5m/px     |

## Services and Ports

| Service         | Port  | Purpose                              |
|-----------------|-------|--------------------------------------|
| GIS Server      | 8080  | REST API + WMTS tiles                |
| Keycloak        | 8180  | OIDC provider (`idispatchx` realm)   |
| PostGIS         | 5432  | GIS database (optional, for debug)   |

PostGIS credentials (test-only): `postgres` / `testpassword`, database `gisdb`.

Keycloak admin console: http://localhost:8180 (admin / admin).

## Sample Data Coverage

The NLS L33xx GML sheets cover south-west Finland — a part of the city of **Pargas** (Parainen).

## Running the Dispatcher Client Against the Test Stack

From `Implementation/clients/dispatcher-client/`:

```bash
VITE_CONFIG_URL=/config.test-gis.json npm run dev
```

This uses `src/public/config.test-gis.json`, which points to the Keycloak and GIS Server
instances started by the test stack.

## Manual API Testing

**1. Get a token from Keycloak** (uses the `dispatcher` user with `Dispatcher` role):

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/idispatchx/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=dispatcher-client&username=dispatcher&password=Test1234!" \
  | jq -r '.access_token')
```

**2. Call a GIS Server endpoint:**

```bash
# Geocoding search
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/geocode/search?q=Parainen"

# List tile layers
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/layers"

# Health (no auth required)
curl http://localhost:8080/health
```

## Adding More Sample Data

### Additional GML ZIP files

1. Place new NLS GML `.zip` files in `SampleData/maastotietokanta/avoin/etrs89/gml/L3/L33/`.
2. Re-run `stop.sh` and `start.sh` — the data seeder re-imports all ZIPs on every fresh start.

### Additional rasters

1. Place new PNG + PGW pairs under `SampleData/rasters/<layer>/`.
2. Re-run tile generation (from `Implementation/`):
   ```bash
   ./deploy/docker/test/generate-tiles.sh
   ```
3. Restart the stack: `./deploy/docker/test/stop.sh && ./deploy/docker/test/start.sh`
