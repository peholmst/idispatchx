#!/usr/bin/env bash
#
# Generate WMTS tile fixtures from the NLS raster PNGs in SampleData/rasters/.
# Output goes to SampleData/tiles/ (gitignored — run this once before starting
# the test stack for the first time, or whenever sample rasters change).
#
# Run from the Implementation/ directory:
#
#   ./deploy/docker/test/generate-tiles.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMPL_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
REPO_DIR="$(cd "${IMPL_DIR}/.." && pwd)"
SAMPLE_DATA="${REPO_DIR}/SampleData"
TILE_DIR="${SAMPLE_DATA}/tiles"

cd "${IMPL_DIR}"

echo "==> Building GIS Data Importer..."
./mvnw package -pl tools/gis-data-importer -am -DskipTests -q

# Locate the unpacked launcher; extract dist if needed
DIST_TAR="tools/gis-data-importer/target/gis-data-importer-1.0.0-SNAPSHOT-dist.tar.gz"
LAUNCHER_DIR="tools/gis-data-importer/target/gis-data-importer-1.0.0-SNAPSHOT"
if [ ! -f "${LAUNCHER_DIR}/gis-data-importer.sh" ]; then
    echo "==> Extracting importer distribution..."
    tar -xzf "${DIST_TAR}" -C "tools/gis-data-importer/target/"
fi

LAUNCHER="${LAUNCHER_DIR}/gis-data-importer.sh"

# Generate maastokartta tiles (zoom levels 6, 7, 8, 10, 11, 13)
echo "==> Generating maastokartta tiles..."
MAASTO_PNGS=$(find "${SAMPLE_DATA}/rasters/maastokartta" -name "*.png" | tr '\n' ' ')
if [ -z "${MAASTO_PNGS}" ]; then
    echo "WARNING: No PNG files found under SampleData/rasters/maastokartta/ — skipping"
else
    # shellcheck disable=SC2086
    JAVA_OPTS="-Xmx2g" bash "${LAUNCHER}" \
        --tile-dir "${TILE_DIR}" \
        --tile-layer maastokartta \
        --tiles ${MAASTO_PNGS}
fi

# Generate taustakartta tiles (zoom levels 7–12, 14)
echo "==> Generating taustakartta tiles..."
TAUSTA_PNGS=$(find "${SAMPLE_DATA}/rasters/taustakartta" -name "*.png" | tr '\n' ' ')
if [ -z "${TAUSTA_PNGS}" ]; then
    echo "WARNING: No PNG files found under SampleData/rasters/taustakartta/ — skipping"
else
    # shellcheck disable=SC2086
    JAVA_OPTS="-Xmx4g" bash "${LAUNCHER}" \
        --tile-dir "${TILE_DIR}" \
        --tile-layer taustakartta \
        --tiles ${TAUSTA_PNGS}
fi

echo ""
echo "Tiles written to: ${TILE_DIR}"
echo "Run ./deploy/docker/test/start.sh to start the test stack."
