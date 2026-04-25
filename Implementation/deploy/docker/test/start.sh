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
COMPOSE_FILE="${SCRIPT_DIR}/../docker-compose-test.yml"

cd "${IMPL_DIR}"

echo "==> Building GIS Data Importer..."
./mvnw package -pl tools/gis-data-importer -am -DskipTests -q

echo "==> Building GIS Server JAR..."
./mvnw package -pl servers/gis-server -am -DskipTests -q

echo "==> Building GIS Server Docker image..."
docker build -t idispatchx/gis-server -f servers/gis-server/Dockerfile . --quiet

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
echo "Dispatcher Client (run separately from Implementation/):"
echo "  VITE_CONFIG_URL=/config.test-gis.json npm run dev"
echo ""
echo "To stop: ./deploy/docker/test/stop.sh"
