#!/usr/bin/env bash
#
# Start the iDispatchX test stack for manual GIS Server testing.
# Run from the Implementation/ directory:
#
#   ./deploy/docker/test/start.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMPL_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
REPO_DIR="$(cd "${IMPL_DIR}/.." && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/../docker-compose-test.yml"
TILE_DIR="${REPO_DIR}/SampleData/tiles"

cd "${IMPL_DIR}"

# --------------------------------------------------------------------------
# Build GIS Data Importer (needed for tile generation and seeder image)
# --------------------------------------------------------------------------
echo "==> Building GIS Data Importer..."
./mvnw package -pl tools/gis-data-importer -am -DskipTests -q

IMPORTER_DIST="tools/gis-data-importer/target/gis-data-importer-1.0.0-SNAPSHOT-dist.tar.gz"
IMPORTER_UNPACKED="tools/gis-data-importer/target/gis-data-importer-1.0.0-SNAPSHOT"
if [ ! -f "${IMPORTER_UNPACKED}/gis-data-importer.sh" ]; then
    tar -xzf "${IMPORTER_DIST}" -C "tools/gis-data-importer/target/"
fi

# --------------------------------------------------------------------------
# Generate tile fixtures if not present (SampleData/tiles/ is gitignored)
# --------------------------------------------------------------------------
if [ ! -d "${TILE_DIR}/maastokartta" ] || [ ! -d "${TILE_DIR}/taustakartta" ]; then
    echo "==> Tile fixtures not found — generating (this may take a few minutes)..."
    bash "${SCRIPT_DIR}/generate-tiles.sh"
fi

# --------------------------------------------------------------------------
# Build GIS Server JAR and base image
# --------------------------------------------------------------------------
echo "==> Building GIS Server JAR..."
./mvnw package -pl servers/gis-server -am -DskipTests -q

echo "==> Building GIS Server base image (idispatchx/gis-server)..."
docker build -t idispatchx/gis-server -f servers/gis-server/Dockerfile . --quiet

# --------------------------------------------------------------------------
# Build idispatchx/gis-server-test — base image + tile fixtures
# Tiles are baked in because Docker bind mounts are not available here.
# --------------------------------------------------------------------------
echo "==> Building GIS Server test image (idispatchx/gis-server-test) with tiles..."
docker build \
    -t idispatchx/gis-server-test \
    -f "${SCRIPT_DIR}/Dockerfile.gis-server-test" \
    "${TILE_DIR}" \
    --quiet

# --------------------------------------------------------------------------
# Build idispatchx/keycloak-test — Keycloak with realm JSON baked in
# --------------------------------------------------------------------------
echo "==> Building Keycloak test image (idispatchx/keycloak-test)..."
docker build \
    -t idispatchx/keycloak-test \
    -f "${SCRIPT_DIR}/Dockerfile.keycloak" \
    "${SCRIPT_DIR}" \
    --quiet

# --------------------------------------------------------------------------
# Build idispatchx/gis-data-seeder — importer + NLS sample data baked in
# A staging directory is used to keep the build context small.
# --------------------------------------------------------------------------
echo "==> Staging GIS Data Seeder build context..."
SEEDER_CTX=$(mktemp -d)
trap 'rm -rf "${SEEDER_CTX}"' EXIT

cp "${IMPORTER_DIST}" "${SEEDER_CTX}/importer.tar.gz"
cp -r "${REPO_DIR}/SampleData/municipalities" "${SEEDER_CTX}/municipalities"
mkdir -p "${SEEDER_CTX}/gml"
cp "${REPO_DIR}/SampleData/maastotietokanta/avoin/etrs89/gml/L3/L33/"*.zip "${SEEDER_CTX}/gml/"

echo "==> Building GIS Data Seeder image (idispatchx/gis-data-seeder)..."
docker build \
    -t idispatchx/gis-data-seeder \
    -f "${SCRIPT_DIR}/Dockerfile.gis-data-seeder" \
    "${SEEDER_CTX}" \
    --quiet

# --------------------------------------------------------------------------
# Start the test stack
# --------------------------------------------------------------------------
echo "==> Starting test stack..."
docker compose -f "${COMPOSE_FILE}" up -d

echo "==> Waiting for GIS Server to become healthy (up to 120s)..."
TIMEOUT=120
ELAPSED=0
until curl -sf http://localhost:8080/health | grep -q '"status":"UP"' 2>/dev/null; do
    if [ "${ELAPSED}" -ge "${TIMEOUT}" ]; then
        echo "ERROR: GIS Server did not become healthy within ${TIMEOUT}s" >&2
        docker compose -f "${COMPOSE_FILE}" logs gis-server >&2
        exit 1
    fi
    sleep 5
    ELAPSED=$((ELAPSED + 5))
done

echo ""
echo "Test stack is running:"
echo "  GIS Server:  http://localhost:8080"
echo "  Keycloak:    http://localhost:8180"
echo "  PostGIS:     localhost:5432 (db=gisdb, user=postgres, password=testpassword)"
echo ""
echo "Dispatcher Client (run separately from clients/dispatcher-client/):"
echo "  VITE_CONFIG_URL=/config.test-gis.json npm run dev"
echo ""
echo "To stop: ./deploy/docker/test/stop.sh"
