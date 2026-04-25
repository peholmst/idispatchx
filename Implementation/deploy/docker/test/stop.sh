#!/usr/bin/env bash
#
# Stop the iDispatchX test stack and remove all containers and the test DB volume.
# Run from the Implementation/ directory:
#
#   ./deploy/docker/test/stop.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/../docker-compose-test.yml"

echo "==> Stopping test stack and removing volumes..."
docker compose -f "${COMPOSE_FILE}" down -v
echo "Done."
