# GIS Data Importer

CLI tool for importing GIS data from the National Land Survey of Finland (Maanmittauslaitos) into iDispatchX.

## Features

- **Vector data import**: Parses GML files (municipality boundaries, road segments, address points, place names) and imports them into PostGIS
- **Raster tile import**: Converts georeferenced PNG images into a tile directory structure

## Building

From the `Implementation` directory:

```bash
./mvnw package -pl tools/gis-data-importer -am -DskipTests
```

This creates a distributable archive at:
```
tools/gis-data-importer/target/gis-data-importer-1.0.0-SNAPSHOT-dist.tar.gz
```

Extract and run using the included shell script:
```bash
tar -xzf gis-data-importer-1.0.0-SNAPSHOT-dist.tar.gz
cd gis-data-importer-1.0.0-SNAPSHOT
./gis-data-importer.sh [options]
```

**Requires:** Java 25+

## Usage

### Vector Data Import

Import municipality names from JSON and GML features into PostGIS:

```bash
./gis-data-importer.sh \
  --db-url jdbc:postgresql://localhost:5432/idispatchx \
  --db-user postgres \
  --db-password secret \
  --municipalities /path/to/codelist_kunta.json \
  --input-dir /path/to/gml-files/
```

Options:
- `--db-url`, `--db-user`, `--db-password` - PostgreSQL connection (required)
- `--municipalities <file>` - Municipality reference JSON from koodistot.suomi.fi
- `--input <file...>` - One or more GML files
- `--input-dir <dir>` - Directory containing GML files (*.xml)
- `--features <list>` - Comma-separated feature types to import: `KUNTA`, `TIEVIIVA`, `OSOITEPISTE`, `PAIKANNIMI` (default: all)
- `--truncate` - Clear existing data before import

### Raster Tile Import

Import georeferenced PNG images as map tiles:

```bash
./gis-data-importer.sh \
  --tile-input-dir /path/to/images/ \
  --tile-dir /path/to/tiles/ \
  --tile-layer orthophoto
```

Options:
- `--tiles <file...>` - One or more PNG files with accompanying world files (.pgw)
- `--tile-input-dir <dir>` - Directory containing PNG files
- `--tile-dir <dir>` - Output directory for tiles (required)
- `--tile-layer <name>` - Layer name (required)
- `--truncate` - Clear existing layer before import

## Data Sources

- **Municipality boundaries & names**: [koodistot.suomi.fi](https://koodistot.suomi.fi) (JSON) and NLS topographic GML
- **Road segments, address points, place names**: [National Land Survey of Finland](https://www.maanmittauslaitos.fi/en/maps-and-spatial-data/datasets-and-interfaces/product-descriptions/topographic-database) topographic database GML files
- **Raster imagery**: Georeferenced PNG with ESRI world files (.pgw)
