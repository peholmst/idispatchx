#!/bin/bash
#
# GIS Data Importer - Launch script for Linux
#
# Usage: ./gis-data-importer.sh [options]
#
# Requires: Java 25+ installed and available in PATH
#

set -e

# Resolve the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/lib"

# Check that lib directory exists
if [ ! -d "${LIB_DIR}" ]; then
    echo "Error: lib directory not found at ${LIB_DIR}" >&2
    exit 1
fi

# Build classpath from all JARs in lib/
CLASSPATH=""
for jar in "${LIB_DIR}"/*.jar; do
    if [ -z "${CLASSPATH}" ]; then
        CLASSPATH="${jar}"
    else
        CLASSPATH="${CLASSPATH}:${jar}"
    fi
done

if [ -z "${CLASSPATH}" ]; then
    echo "Error: No JAR files found in ${LIB_DIR}" >&2
    exit 1
fi

# Default JVM options (can be overridden via JAVA_OPTS environment variable)
DEFAULT_JAVA_OPTS="--enable-preview -Djava.awt.headless=true"

# Run the importer
exec java ${DEFAULT_JAVA_OPTS} ${JAVA_OPTS:-} -cp "${CLASSPATH}" net.pkhapps.idispatchx.gis.importer.Main "$@"
