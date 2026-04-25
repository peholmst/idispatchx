# GIS Server Test Stack

Manual testing environment for the GIS Server using real NLS sample data.
This stack is **not** used by E2E tests — those use a mock GIS Server.

## Purpose

Provides a single-host Docker environment with:
- GIS Server (single replica)
- Keycloak (OIDC provider)
- PostGIS database (seeded with NLS GML geocoding data)
- Map tiles bind-mounted from `SampleData/tiles/`

## Prerequisites

- Docker with Compose v2 (`docker compose`)
- Java 25 JDK and Maven (for building the GIS Server and Data Importer)

## Quick Start

Run from the `Implementation/` directory:

```bash
# Start all services (builds images, seeds data, waits for healthy)
./deploy/docker/test/start.sh

# Stop all services and remove the test DB volume
./deploy/docker/test/stop.sh
```

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

**Tile layers:**

| Layer           | Zoom levels | Resolution        |
|-----------------|-------------|-------------------|
| `maastokartta`  | 6–8, 10–11, 13 | 128m → 1m/px  |
| `taustakartta`  | 7–12, 14    | 64m → 0.5m/px     |

Tiles are pre-generated from the PNG rasters in `SampleData/rasters/` and committed to `SampleData/tiles/`.

## Running the Dispatcher Client Against the Test Stack

From `Implementation/clients/dispatcher-client/`:

```bash
VITE_CONFIG_URL=/config.test-gis.json npm run dev
```

This uses `src/public/config.test-gis.json`, which points to the Keycloak and GIS Server instances started by the test stack.

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
2. Re-run the tile importer (from `Implementation/`):
   ```bash
   JAVA_OPTS="-Xmx4g" bash tools/gis-data-importer/target/gis-data-importer-1.0.0-SNAPSHOT/gis-data-importer.sh \
     --tile-dir ../SampleData/tiles \
     --tile-layer <layer-name> \
     --tiles $(find ../SampleData/rasters/<layer-name> -name "*.png" | tr '\n' ' ')
   ```
3. Commit the new tiles in `SampleData/tiles/`.

> **Note on repository size:** The zoom-14 taustakartta tiles are numerous (0.5 m/px).
> Consider Git LFS for `SampleData/tiles/` if the repository grows too large.
