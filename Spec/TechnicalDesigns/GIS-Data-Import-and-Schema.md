# Technical Design: GIS Data Import and Database Schema

## Overview

This document describes the PostGIS database schema used by the GIS Server for geocoding, and the design of the GIS Data Importer CLI tool that populates it from two data sources:

1. **NLS topographic GML files** — municipality boundaries, road segments, address points, and place names from the National Land Survey of Finland (Maastotietojärjestelmä).
2. **Municipality reference JSON** — municipality codes and multilingual names from the Finnish interoperability platform (koodistot.suomi.fi).

The GIS Server provides geocoding services to dispatchers (address/place name lookup), and map tiles via WMTS. This design covers the geocoding data layer only — raster tile storage is a filesystem concern handled separately.

The importer converts GML coordinates from EPSG:3067 to EPSG:4326 and writes all data to the shared PostGIS database. It supports both full imports and incremental patch imports.

## References

- [C4: Containers](../C4/Containers.md) — GIS Server and GIS Data Importer container definitions, tech stacks, storage
- [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md) — EPSG:4326/3067 coordinate systems, precision bounds, multilingual names
- [NFR: Availability](../NonFunctionalRequirements/Availability.md) — Degraded operation without GIS Server
- [NFR: Performance](../NonFunctionalRequirements/Performance.md) — Geocoding response time targets
- [NFR: Security](../NonFunctionalRequirements/Security.md) — No authentication for CLI importer
- [NFR: Maintainability](../NonFunctionalRequirements/Maintainability.md) — Minimal dependencies, prefer standard libraries
- [UC: Lookup Address](../UseCases/Dispatcher/UC-Lookup-Address.md) — Primary geocoding use case
- [Domain: Location](../Domain/Location.md) — ExactAddress, RoadIntersection, NamedPlace variants
- [Domain: Municipality](../Domain/Municipality.md) — Municipality code and multilingual name
- [Domain: MultilingualName](../Domain/MultilingualName.md) — Language-keyed name values
- NLS Topographic Database GML Schema: `https://xml.nls.fi/XML/Schema/Maastotietojarjestelma/MTK/201405/Maastotiedot.xsd`

---

## 1. NLS GML Data Model

### 1.1 Source Format

NLS distributes topographic data as GML files organized by map sheet (e.g., `L3311R.xml`). Each file is a `<Maastotiedot>` document in namespace `http://xml.nls.fi/XML/Namespace/Maastotietojarjestelma/SiirtotiedostonMalli/2011-02`, with `srsName="EPSG:3067, EPSG:5717"`.

The document contains 140+ feature type collections. Only four are relevant to geocoding:

| GML Collection | Feature Element | Geometry Type | Purpose |
|----------------|-----------------|---------------|---------|
| `<kunnat>` | `<Kunta>` | Polygon (Alue) | Municipality boundaries |
| `<tieviivat>` | `<Tieviiva>` | LineString (Murtoviiva) | Road segments with names and address ranges |
| `<osoitepisteet>` | `<Osoitepiste>` | Point (Piste) | Address points with house numbers |
| `<paikannnimet>` | `<Paikannimi>` | Point (Piste) | Named places (islands, villages, landmarks) |

### 1.2 Common Feature Attributes

All features share a base set of attributes inherited from their respective XSD base types:

| Attribute | Type | Description |
|-----------|------|-------------|
| `@gid` | unsigned int | Globally unique NLS feature identifier. Stable across data releases. |
| `@dimension` | unsigned int | Coordinate dimension (2 or 3) |
| `sijaintitarkkuus` | unsigned int | Positional accuracy class |
| `aineistolahde` | unsigned int | Data source identifier |
| `alkupvm` | date | Feature creation/modification date |
| `loppupvm` | date (optional) | Feature end/retirement date. When set, indicates the feature has been removed. |
| `sijainti` | geometry | Feature geometry (type varies by feature) |
| `kohderyhma` | unsigned int | Feature group code (fixed per type) |
| `kohdeluokka` | unsigned int | Feature class code |

The `gid` is the primary key for all features. The `loppupvm` field is critical for incremental imports: when set, it signals that the feature should be removed from the database.

### 1.3 Kunta (Municipality)

**XSD base type**: `AluekohdeType`

| Element | Type | Required | Description |
|---------|------|----------|-------------|
| `kohderyhma` | unsigned int | Yes | Always 71 |
| `kohdeluokka` | unsigned int | Yes | Always 84200 |
| `kuntatunnus` | string | Yes | 3-digit municipality code (Statistics Finland) |
| Geometry | Polygon (`Alue`) | Yes | Municipality boundary in EPSG:3067 |

**Example**:
```xml
<Kunta gid="27696440" dimension="3">
  <sijaintitarkkuus>0</sijaintitarkkuus>
  <aineistolahde>1</aineistolahde>
  <alkupvm>2009-01-01</alkupvm>
  <sijainti>
    <Piste><gml:pos srsDimension="3">230000.000 6672000.001 0.000</gml:pos></Piste>
    <Alue>
      <gml:exterior>
        <gml:LinearRing>
          <gml:posList srsDimension="3">234265.396 6669060.097 0.000 ...</gml:posList>
        </gml:LinearRing>
      </gml:exterior>
    </Alue>
  </sijainti>
  <kohderyhma>71</kohderyhma>
  <kohdeluokka>84200</kohdeluokka>
  <kuntatunnus>445</kuntatunnus>
</Kunta>
```

**Notes**:
- A municipality's boundary may be split across multiple map sheets. The importer must merge polygons from different files into a MULTIPOLYGON.
- The `<Piste>` element contains a representative point (centroid); the `<Alue>` contains the actual boundary polygon.

### 1.4 Tieviiva (Road Segment)

**XSD base type**: `ViivakohdeType` → `TieviivaType`

| Element | Type | Required | Description |
|---------|------|----------|-------------|
| `kohderyhma` | unsigned int | Yes | Always 25 |
| `kohdeluokka` | TieluokkaType | Yes | Road class (see code list below) |
| `kulkutapa` | unsigned int | Yes | Travel mode |
| `tasosijainti` | int | Yes | Vertical relationship (0=ground, 1=bridge, -1=tunnel) |
| `valmiusaste` | int | Yes | Completion status (0=in use) |
| `paallyste` | int | Yes | Surface type: 0=unknown, 1=unpaved, 2=paved |
| `yksisuuntaisuus` | int | Yes | One-way: 0=bidirectional, 1=forward, 2=backward |
| `tienumero` | unsigned int | No | Road number (for numbered highways) |
| `tieosanumero` | unsigned int | No | Road section number |
| `hallinnollinenLuokka` | int | No | Administrative class: 1=state, 2=municipal, 3=private |
| `minOsoitenumeroVasen` | int | No | Min address number, left side (0=none) |
| `maxOsoitenumeroVasen` | int | No | Max address number, left side (0=none) |
| `minOsoitenumeroOikea` | int | No | Min address number, right side (0=none) |
| `maxOsoitenumeroOikea` | int | No | Max address number, right side (0=none) |
| `nimi_suomi` | string | No | Finnish street name (attribute `kieli="fin"`) |
| `nimi_ruotsi` | string | No | Swedish street name (attribute `kieli="swe"`) |
| `nimi_inarinsaame` | string | No | Inari Sami name (attribute `kieli="smn"`) |
| `nimi_koltansaame` | string | No | Skolt Sami name (attribute `kieli="sms"`) |
| `nimi_pohjoissaame` | string | No | Northern Sami name (attribute `kieli="sme"`) |
| `kuntatunnus` | string | No | Municipality code |
| Geometry | LineString (`Murtoviiva`) | Yes | Road centerline in EPSG:3067 |

**Road Class Code List** (TieluokkaType):

| Code | Finnish | Description |
|------|---------|-------------|
| 12111 | Autotie Ia | Motorway class Ia |
| 12112 | Autotie Ib | Motorway class Ib |
| 12121 | Autotie IIa | Main road class IIa |
| 12122 | Autotie IIb | Main road class IIb |
| 12131 | Autotie IIIa | Regional road class IIIa |
| 12132 | Autotie IIIb | Regional road class IIIb |
| 12141 | Ajotie | Drivable road |
| 12151 | Lautta | Ferry |
| 12152 | Lossi | Cable ferry |
| 12311 | Vanha ajopolku | Old cart track |
| 12312 | Talvitie | Winter road |
| 12313 | Polku | Footpath |
| 12314 | Kävely/pyörätie | Walking/cycling path |
| 12316 | Ajopolku | Cart track |

**Example**:
```xml
<Tieviiva gid="1186733682" dimension="3">
  <sijaintitarkkuus>3000</sijaintitarkkuus>
  <korkeustarkkuus>201</korkeustarkkuus>
  <aineistolahde>1</aineistolahde>
  <alkupvm>2018-06-05</alkupvm>
  <kulkutapa>2</kulkutapa>
  <sijainti>
    <Murtoviiva>
      <gml:posList srsDimension="3">232056.555 6678000.000 7.480 ...</gml:posList>
    </Murtoviiva>
  </sijainti>
  <kohderyhma>25</kohderyhma>
  <kohdeluokka>12141</kohdeluokka>
  <tasosijainti>0</tasosijainti>
  <valmiusaste>0</valmiusaste>
  <paallyste>1</paallyste>
  <yksisuuntaisuus>0</yksisuuntaisuus>
  <hallinnollinenLuokka>3</hallinnollinenLuokka>
  <nimi_suomi kieli="fin">Kuggöntie</nimi_suomi>
  <nimi_ruotsi kieli="swe">Kuggövägen</nimi_ruotsi>
  <minOsoitenumeroVasen>0</minOsoitenumeroVasen>
  <maxOsoitenumeroVasen>0</maxOsoitenumeroVasen>
  <minOsoitenumeroOikea>0</minOsoitenumeroOikea>
  <maxOsoitenumeroOikea>0</maxOsoitenumeroOikea>
  <kuntatunnus>445</kuntatunnus>
</Tieviiva>
```

**Notes**:
- Road segments are a primary source for address geocoding. Not all addresses have a corresponding Osoitepiste record. When a dispatcher searches for e.g. "Strandvägen 1", the GIS Server finds the road segment whose name matches and whose address range includes number 1, then interpolates an approximate coordinate along the road geometry. The dispatcher can then refine the position on the map.
- Address range values of `0` mean "no addresses on this side" — imported as NULL.
- Many road segments lack street names (unnamed forest roads, paths). These are still imported for road intersection computation but are not useful for name-based geocoding.
- Multiple Tieviiva segments may share the same street name — they represent consecutive sections of the same road. When geocoding, all matching segments must be considered to find the one whose address range contains the requested number.
- Some streets have only a Swedish name (no Finnish name), common in bilingual municipalities like Parainen (code 445).

### 1.5 Osoitepiste (Address Point)

**XSD base type**: `SymbolikohdeType` → `OsoitepisteType`

| Element | Type | Required | Description |
|---------|------|----------|-------------|
| `kohderyhma` | unsigned int | Yes | Always 2 |
| `kohdeluokka` | unsigned int | Yes | Address symbol class (96001) |
| `numero` | string | No | Address number (may contain letters, e.g. "427s", "290 s") |
| `nimi_suomi` | string | No | Finnish street/address name |
| `nimi_ruotsi` | string | No | Swedish street/address name |
| `nimi_inarinsaame` | string | No | Inari Sami name |
| `nimi_koltansaame` | string | No | Skolt Sami name |
| `nimi_pohjoissaame` | string | No | Northern Sami name |
| `kuntatunnus` | string | No | Municipality code |
| Geometry | Point (`Piste`) | Yes | Address location in EPSG:3067 |

**Example**:
```xml
<Osoitepiste gid="1754319417" dimension="2">
  <sijaintitarkkuus>12500</sijaintitarkkuus>
  <korkeustarkkuus>1</korkeustarkkuus>
  <aineistolahde>1</aineistolahde>
  <alkupvm>2016-10-11</alkupvm>
  <suunta>0</suunta>
  <sijainti>
    <Piste><gml:pos srsDimension="2">231221.828 6677931.943</gml:pos></Piste>
  </sijainti>
  <kohderyhma>2</kohderyhma>
  <kohdeluokka>96001</kohdeluokka>
  <numero>427s</numero>
  <nimi_suomi kieli="fin">Kuggö</nimi_suomi>
  <nimi_ruotsi kieli="swe">Kuggö</nimi_ruotsi>
  <kuntatunnus>445</kuntatunnus>
</Osoitepiste>
```

**Notes**:
- Osoitepiste provides precise coordinates for specific addresses and maps to the `ExactAddress` domain variant. However, not all addresses have a corresponding Osoitepiste record — many addresses are only covered by Tieviiva road segments with address ranges (see section 1.4).
- The `numero` field is a string, not an integer — it can contain suffixes like "s" (staircase) or spaces.
- An Osoitepiste represents an "address point" which may be on a road or a standalone named location (e.g., an island with a postal address but no road).

### 1.6 Paikannimi (Place Name)

**XSD base type**: `TekstikohdeType` → `PaikannimiType`

| Element | Type | Required | Description |
|---------|------|----------|-------------|
| `kohderyhma` | unsigned int | Yes | Feature group |
| `kohdeluokka` | PaikannimiluokkaType | Yes | Place name class (56 values, see below) |
| `teksti` | string | Yes | The place name text |
| `teksti@kieli` | string | Yes | Language code (ISO 639: "fin", "swe", "smn", "sms", "sme") |
| `suunta` | int | Yes | Text rotation (1/10000 radian) |
| `dx`, `dy` | int | Yes | Text offset for map rendering |
| `ladontatunnus` | int | Yes | Typesetting code |
| `versaalitieto` | int | Yes | Capitalization code |
| `nrKarttanimiId` | unsigned int | No | Map name register ID — links multilingual entries for the same place |
| Geometry | Point (`Piste`) | Yes | Name anchor point in EPSG:3067 |

**Key Place Name Classes** (PaikannimiluokkaType, selected):

| Code | Description |
|------|-------------|
| 12101 | Road name |
| 35010 | Field/open area name |
| 35020 | Forest name |
| 35040 | Elevation name (hill, mountain) |
| 35050 | Peninsula/cape name |
| 35060 | Island name |
| 35070 | Islet/reef name |
| 36201 | Lake name |
| 36301 | River name |
| 42101 | Building name |
| 48111–48190 | Settlement names (city, village, hamlet) |
| 72201–72801 | Protected area names |

**Example**:
```xml
<Paikannimi gid="70304994" dimension="2">
  <sijaintitarkkuus>0</sijaintitarkkuus>
  <aineistolahde>1</aineistolahde>
  <alkupvm>2016-05-04</alkupvm>
  <teksti kieli="swe">Lilla Gulskär</teksti>
  <suunta>0</suunta>
  <dx>-33801</dx>
  <dy>-95370</dy>
  <sijainti>
    <Piste><gml:pos srsDimension="2">228723.269 6674958.712</gml:pos></Piste>
  </sijainti>
  <kohderyhma>16</kohderyhma>
  <kohdeluokka>35070</kohdeluokka>
  <ladontatunnus>6111</ladontatunnus>
  <versaalitieto>0</versaalitieto>
  <nrKarttanimiId>70304994</nrKarttanimiId>
</Paikannimi>
```

**Notes**:
- Each Paikannimi record has exactly one name in one language. A place with names in multiple languages has multiple Paikannimi records sharing the same `nrKarttanimiId`.
- Paikannimi does NOT contain a `kuntatunnus` field. The municipality must be resolved at import time via point-in-polygon against Kunta boundaries.
- The `teksti@kieli` attribute provides the ISO 639 language code directly.
- The `dx`, `dy`, `suunta`, `ladontatunnus`, and `versaalitieto` fields are map rendering hints — not relevant for geocoding, but stored for completeness.

### 1.7 Municipality Reference JSON

**Source**: Finnish interoperability platform (koodistot.suomi.fi), codelist `kunta_1_YYYYMMDD`.

The JSON file is a codescheme export containing municipality codes and multilingual names. The file has a complex nested structure with extension mappings (e.g., welfare area cross-references). Only the `codes` array at the top level is relevant.

**File structure** (simplified):
```json
{
  "codeValue": "kunta_1_20250101",
  "extensions": [ ... ],   // Welfare area mappings — ignored
  "codes": [               // Municipality entries — this is what we import
    {
      "codeValue": "445",
      "status": "VALID",
      "prefLabel": {
        "fi": "Parainen",
        "sv": "Pargas",
        "se": "Parainen",
        "smn": "Parainen",
        "sms": "Parainen",
        "en": "Pargas"
      }
    },
    ...
  ]
}
```

**Relevant fields per code entry**:

| Field | Description |
|-------|-------------|
| `codeValue` | 3-digit municipality code (matches GML `kuntatunnus` and Statistics Finland code) |
| `status` | Entry status. Only `"VALID"` entries are imported. |
| `prefLabel.fi` | Finnish name |
| `prefLabel.sv` | Swedish name |
| `prefLabel.se` | Northern Sami name (ISO 639-1 `se`; mapped to `sme` in the system) |
| `prefLabel.smn` | Inari Sami name (ISO 639-2 `smn`) |
| `prefLabel.sms` | Skolt Sami name (ISO 639-2 `sms`) |
| `prefLabel.en` | English name |

**Notes**:
- The JSON file may contain entries that are not municipalities (other area types). Only entries from the `codes` array with `status: "VALID"` are imported.
- The file is large (~20MB) because the `extensions` section repeats municipality data in cross-reference mappings. The importer navigates directly to the `codes` array and ignores everything else.
- Language code mapping: the JSON uses ISO 639-1 `"se"` for Northern Sami, while the GML and the system use ISO 639-3 `"sme"`. The importer normalizes to ISO 639-3.
- All languages are always present in the JSON `prefLabel`, but for many municipalities the Sami names are identical to the Finnish name (no distinct Sami name exists). These are stored as-is.

---

## 2. Database Schema

### 2.1 Prerequisites

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

PostGIS provides spatial types and functions. pg_trgm provides trigram-based fuzzy text matching for geocoding queries.

All tables are in the `gis` schema:

```sql
CREATE SCHEMA IF NOT EXISTS gis;
```

### 2.2 Table: `gis.municipality`

Stores municipality reference data: codes and multilingual names from the municipality JSON file, and boundary polygons from GML Kunta features.

```sql
CREATE TABLE gis.municipality (
    municipality_code  VARCHAR(3)                  PRIMARY KEY,
    name_fi            VARCHAR(200),
    name_sv            VARCHAR(200),
    name_smn           VARCHAR(200),
    name_sms           VARCHAR(200),
    name_sme           VARCHAR(200),
    boundary           GEOMETRY(MULTIPOLYGON, 4326),
    imported_at        TIMESTAMPTZ                 NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_municipality_boundary ON gis.municipality USING GIST (boundary);
```

**Design notes**:
- Primary key is `municipality_code` (3-digit Statistics Finland code), not the NLS `gid`.
- `MULTIPOLYGON` because a municipality boundary may be assembled from polygons in multiple GML map sheet files.
- Name columns (`name_fi`, `name_sv`, `name_smn`, `name_sms`, `name_sme`) are populated from the municipality JSON reference file. They are nullable to allow importing boundary data before names (or vice versa).
- The GML Kunta `gid` is not stored because the municipality code is the stable domain identifier and multiple GML records (from different map sheets) may represent the same municipality.
- For many municipalities, the Sami names are identical to the Finnish name. They are stored as-is — the system does not deduplicate names across languages.

### 2.3 Table: `gis.address_point`

Stores Osoitepiste features. Provides precise coordinates for specific addresses where available.

```sql
CREATE TABLE gis.address_point (
    id                 BIGINT                      PRIMARY KEY,
    number             VARCHAR(20),
    name_fi            VARCHAR(200),
    name_sv            VARCHAR(200),
    name_smn           VARCHAR(200),
    name_sms           VARCHAR(200),
    name_sme           VARCHAR(200),
    municipality_code  VARCHAR(3),
    location           GEOMETRY(POINT, 4326)       NOT NULL,
    imported_at        TIMESTAMPTZ                 NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_address_point_location       ON gis.address_point USING GIST (location);
CREATE INDEX idx_address_point_name_fi        ON gis.address_point USING GIN  (name_fi  gin_trgm_ops);
CREATE INDEX idx_address_point_name_sv        ON gis.address_point USING GIN  (name_sv  gin_trgm_ops);
CREATE INDEX idx_address_point_municipality   ON gis.address_point (municipality_code);
```

**Design notes**:
- `id` is the NLS `gid` attribute — globally unique and stable across data releases.
- `number` is VARCHAR because it may contain letters (e.g., "427s", "12 a").
- Five name columns for the five supported languages (Finnish, Swedish, Inari Sami, Skolt Sami, Northern Sami). Sami names are rare but must be stored per the Internationalization NFR.
- GIN trigram indexes on `name_fi` and `name_sv` enable efficient fuzzy substring matching for geocoding. Sami name indexes are omitted for now (low cardinality) but can be added if needed.
- No foreign key to `gis.municipality` — the importer may process files in any order, and the municipality JSON may not be loaded yet.

### 2.4 Table: `gis.road_segment`

Stores Tieviiva features. Primary source for address geocoding via address range interpolation — most addresses are geocoded by finding the matching road segment and interpolating a position along its geometry. Also used for road intersection computation.

```sql
CREATE TABLE gis.road_segment (
    id                    BIGINT                      PRIMARY KEY,
    road_class            INT                         NOT NULL,
    surface_type          SMALLINT                    NOT NULL,
    administrative_class  SMALLINT,
    one_way               SMALLINT                    NOT NULL,
    name_fi               VARCHAR(200),
    name_sv               VARCHAR(200),
    name_smn              VARCHAR(200),
    name_sms              VARCHAR(200),
    name_sme              VARCHAR(200),
    min_address_left      INT,
    max_address_left      INT,
    min_address_right     INT,
    max_address_right     INT,
    municipality_code     VARCHAR(3),
    geometry              GEOMETRY(LINESTRING, 4326)  NOT NULL,
    imported_at           TIMESTAMPTZ                 NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_road_segment_geometry       ON gis.road_segment USING GIST (geometry);
CREATE INDEX idx_road_segment_name_fi        ON gis.road_segment USING GIN  (name_fi gin_trgm_ops);
CREATE INDEX idx_road_segment_name_sv        ON gis.road_segment USING GIN  (name_sv gin_trgm_ops);
CREATE INDEX idx_road_segment_municipality   ON gis.road_segment (municipality_code);
```

**Design notes**:
- Address range values of `0` in the GML are imported as NULL (0 means "no addresses on this side").
- Road segments without any street name are still imported — their geometry is needed for computing road intersections.
- The `road_class` code doubles as a functional classification useful for filtering (e.g., only search named vehicular roads, exclude footpaths).

### 2.5 Table: `gis.named_place`

Stores Paikannimi features. Each row is one name in one language for a place.

```sql
CREATE TABLE gis.named_place (
    id                 BIGINT                      PRIMARY KEY,
    name               VARCHAR(200)                NOT NULL,
    language           VARCHAR(3)                  NOT NULL,
    place_class        INT                         NOT NULL,
    kartanimi_id       BIGINT,
    municipality_code  VARCHAR(3),
    location           GEOMETRY(POINT, 4326)       NOT NULL,
    imported_at        TIMESTAMPTZ                 NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_named_place_location       ON gis.named_place USING GIST (location);
CREATE INDEX idx_named_place_name           ON gis.named_place USING GIN  (name gin_trgm_ops);
CREATE INDEX idx_named_place_kartanimi_id   ON gis.named_place (kartanimi_id);
CREATE INDEX idx_named_place_municipality   ON gis.named_place (municipality_code);
```

**Design notes**:
- Multiple rows may share the same `kartanimi_id` — these are the same place in different languages. The GIS Server groups by `kartanimi_id` when building multilingual geocoding results.
- `municipality_code` is resolved during import by checking which `gis.municipality` boundary polygon contains the place's point geometry (`ST_Contains`).
- `language` stores the ISO 639 code from the `teksti@kieli` attribute (e.g., "fin", "swe", "sme").
- Map rendering attributes (`suunta`, `dx`, `dy`, `ladontatunnus`, `versaalitieto`) are not stored — they have no geocoding value.

### 2.6 Table: `gis.import_log`

Tracks import runs for auditing and troubleshooting.

```sql
CREATE TABLE gis.import_log (
    id             BIGSERIAL                    PRIMARY KEY,
    filename       VARCHAR(500)                 NOT NULL,
    feature_type   VARCHAR(50)                  NOT NULL,
    record_count   INT                          NOT NULL,
    started_at     TIMESTAMPTZ                  NOT NULL,
    completed_at   TIMESTAMPTZ
);
```

### 2.7 Flyway Migration

The schema DDL is managed as a Flyway migration in the GIS Server module:

```
Implementation/servers/gis-server/src/main/resources/db/migration/
  V1__create_gis_schema.sql
```

The GIS Server owns the schema because it is the primary long-running consumer. The GIS Data Importer also runs Flyway on startup to ensure the schema is current before importing data.

---

## 3. Import Strategy

### 3.1 Import Modes

The importer supports two modes:

**Full import** (`--truncate` flag):
1. Truncate all data tables (`address_point`, `road_segment`, `named_place`; municipality boundaries reset to NULL).
2. Parse each input GML file and INSERT all features.
3. Best used for initial data load or when replacing the entire dataset.

**Incremental import** (default, no flag):
1. Parse each input GML file.
2. For each feature:
   - If `loppupvm` (end date) is set: DELETE the feature from the database by `gid`.
   - Otherwise: UPSERT — INSERT the feature, or UPDATE if a record with the same `gid` already exists.
3. This supports NLS patch releases that contain only changed features.

The `gid` attribute is the stable NLS feature identifier. It persists across data releases, making it reliable as the UPSERT key.

### 3.2 UPSERT Pattern

Using jOOQ's `INSERT ... ON CONFLICT (id) DO UPDATE SET ...`:

```sql
INSERT INTO gis.address_point (id, number, name_fi, name_sv, ..., location, imported_at)
VALUES (?, ?, ?, ?, ..., ST_SetSRID(ST_MakePoint(?, ?), 4326), NOW())
ON CONFLICT (id) DO UPDATE SET
    number = EXCLUDED.number,
    name_fi = EXCLUDED.name_fi,
    name_sv = EXCLUDED.name_sv,
    ...
    location = EXCLUDED.location,
    imported_at = EXCLUDED.imported_at;
```

### 3.3 Municipality Boundary Merging

Kunta features require special handling because a municipality's boundary may be split across multiple GML map sheet files:

1. For each Kunta feature, extract the polygon and convert to EPSG:4326.
2. UPSERT by `municipality_code` (not `gid`):
   - If the municipality already exists, merge the new polygon with the existing boundary using `ST_Union`.
   - If it does not exist, insert a new record with the polygon wrapped as a MULTIPOLYGON.

```sql
INSERT INTO gis.municipality (municipality_code, boundary, imported_at)
VALUES (?, ST_Multi(?), NOW())
ON CONFLICT (municipality_code) DO UPDATE SET
    boundary = ST_Multi(ST_Union(gis.municipality.boundary, EXCLUDED.boundary)),
    imported_at = EXCLUDED.imported_at;
```

In full import mode (`--truncate`), boundaries are reset to NULL before import, so no merging is needed — each polygon is simply set.

### 3.4 Import Order

The importer processes data in three passes:

1. **Pass 1 — Municipality JSON**: If a `--municipalities` JSON file is provided, parse it and UPSERT municipality codes and names into `gis.municipality`. This populates the name columns.
2. **Pass 2 — GML Kunta**: Import all Kunta features from all GML input files. This populates (or merges) boundary polygons into `gis.municipality`, using the UPSERT pattern from section 3.3 to preserve names already loaded in pass 1.
3. **Pass 3 — GML features**: Import Tieviiva, Osoitepiste, and Paikannimi from all GML files. Paikannimi municipality resolution uses the boundary polygons loaded in pass 2.

Passes 1 and 2 can be run independently (e.g., JSON-only import or GML-only import). When both are provided in the same run, the order above is enforced automatically.

### 3.5 Municipality JSON Import

The municipality JSON file is parsed using Jackson (already a project dependency). The importer:

1. Reads the top-level JSON object.
2. Navigates to the `codes` array (ignoring `extensions` and other metadata).
3. For each entry in `codes` where `status` is `"VALID"`:
   - Extracts `codeValue` as the municipality code.
   - Extracts `prefLabel` names for `fi`, `sv`, `smn`, `sms`, and `se` (mapped to `sme`).
   - UPSERTs into `gis.municipality`:

```sql
INSERT INTO gis.municipality (municipality_code, name_fi, name_sv, name_smn, name_sms, name_sme, imported_at)
VALUES (?, ?, ?, ?, ?, ?, NOW())
ON CONFLICT (municipality_code) DO UPDATE SET
    name_fi  = EXCLUDED.name_fi,
    name_sv  = EXCLUDED.name_sv,
    name_smn = EXCLUDED.name_smn,
    name_sms = EXCLUDED.name_sms,
    name_sme = EXCLUDED.name_sme,
    imported_at = EXCLUDED.imported_at;
```

This preserves any existing `boundary` data (the UPSERT only touches name columns). Similarly, the GML Kunta import (section 3.3) only touches the `boundary` column, preserving names.

### 3.6 Municipality Resolution for Paikannimi

Paikannimi features do not include a `kuntatunnus`. The municipality is resolved at import time:

```sql
SELECT municipality_code
FROM gis.municipality
WHERE ST_Contains(boundary, ST_SetSRID(ST_MakePoint(?, ?), 4326))
LIMIT 1;
```

If no municipality boundary contains the point (e.g., a place name in the open sea near the coast), `municipality_code` is set to NULL. This is acceptable — the geocoding query can still return the result without a municipality.

### 3.7 Coordinate Transformation

All NLS GML coordinates are in EPSG:3067 (EUREF-FIN / TM35FIN). The importer converts to EPSG:4326 (WGS 84) during import using Geotools:

```java
CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:3067");
CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:4326");
MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
```

The GML data is 3D (easting, northing, elevation). The elevation dimension is dropped during conversion because EPSG:4326 as used by the system is 2D (latitude, longitude).

Transformed coordinates are validated against the NFR bounds:
- Latitude: 58.84° to 70.09°
- Longitude: 19.08° to 31.59°

Features with coordinates outside these bounds after transformation are logged as warnings and skipped.

### 3.8 Batch Processing

Features are accumulated in memory and flushed to the database in batches of 1000 rows using jOOQ batch insert/upsert operations. This balances memory usage against database round-trip overhead.

---

## 4. GML Parser Design

### 4.1 Parsing Approach: StAX

The importer uses Java's built-in StAX (Streaming API for XML) parser. Rationale:

- **Efficiency**: GML files can be tens of megabytes with 150,000+ lines. StAX processes as a stream without loading the full DOM.
- **Selectivity**: Only 4 of 140+ feature types are relevant. StAX can skip irrelevant sections by ignoring unrecognized element names.
- **Simplicity**: The NLS GML uses a custom namespace wrapper around standard GML geometry types. A targeted StAX parser is simpler and more maintainable than JAXB code generation from the complex 140+ type XSD.
- **No additional dependencies**: StAX is part of the Java standard library.

### 4.2 Parser Architecture

The parser uses a visitor/callback pattern:

```
NlsGmlParser
  - Reads XML stream
  - Detects feature type by element name (Kunta, Tieviiva, Osoitepiste, Paikannimi)
  - Delegates to element-specific parsing logic
  - Calls FeatureVisitor methods with parsed model objects

FeatureVisitor (interface)
  - onKunta(KuntaFeature)
  - onTieviiva(TieviivaFeature)
  - onOsoitepiste(OsoitepisteFeature)
  - onPaikannimi(PaikannimiFeature)
```

The `ImportCommand` implements `FeatureVisitor` and routes parsed features to the appropriate database importer.

### 4.3 Parsed Feature Models

Simple Java records holding the extracted data before database insertion:

**MunicipalityEntry** (from JSON): `code`, `nameFi`, `nameSv`, `nameSmn`, `nameSms`, `nameSme`

**KuntaFeature** (from GML): `gid`, `alkupvm`, `loppupvm`, `kuntatunnus`, `polygonCoordinates`

**TieviivaFeature** (from GML): `gid`, `alkupvm`, `loppupvm`, `kohdeluokka`, `paallyste`, `hallinnollinenLuokka`, `yksisuuntaisuus`, `nameFi`, `nameSv`, `nameSmn`, `nameSms`, `nameSme`, `minAddressLeft`, `maxAddressLeft`, `minAddressRight`, `maxAddressRight`, `kuntatunnus`, `lineCoordinates`

**OsoitepisteFeature** (from GML): `gid`, `alkupvm`, `loppupvm`, `numero`, `nameFi`, `nameSv`, `nameSmn`, `nameSms`, `nameSme`, `kuntatunnus`, `pointEasting`, `pointNorthing`

**PaikannimiFeature** (from GML): `gid`, `alkupvm`, `loppupvm`, `teksti`, `kieli`, `kohdeluokka`, `kartanimiId`, `pointEasting`, `pointNorthing`

### 4.4 GML Geometry Parsing

The parser handles three geometry patterns found in the data:

| GML Element | Geometry | Parsing |
|-------------|----------|---------|
| `<Piste><gml:pos>` | Point | Split on whitespace: `easting northing [elevation]` |
| `<Murtoviiva><gml:posList>` | LineString | Split on whitespace, take coordinate triples: `e1 n1 z1 e2 n2 z2 ...` |
| `<Alue><gml:exterior><gml:LinearRing><gml:posList>` | Polygon | Same as LineString, close ring |

The `srsDimension` attribute indicates whether coordinates are 2D or 3D. The parser reads this to determine the stride (2 or 3 values per coordinate).

---

## 5. Package Structure

```
net.pkhapps.idispatchx.gis.importer/
├── Main.java                          CLI entry point (argument parsing, logging setup)
├── ImportCommand.java                 Orchestrates the import pipeline, implements FeatureVisitor
├── parser/
│   ├── NlsGmlParser.java             StAX-based GML parser for topographic data
│   ├── MunicipalityJsonParser.java    Jackson-based parser for municipality reference JSON
│   ├── FeatureVisitor.java            Callback interface for parsed GML features
│   └── model/
│       ├── KuntaFeature.java          Parsed Kunta data (Java record)
│       ├── TieviivaFeature.java       Parsed Tieviiva data (Java record)
│       ├── OsoitepisteFeature.java    Parsed Osoitepiste data (Java record)
│       ├── PaikannimiFeature.java     Parsed Paikannimi data (Java record)
│       └── MunicipalityEntry.java     Parsed municipality JSON entry (Java record)
├── transform/
│   └── CoordinateTransformer.java     EPSG:3067 → EPSG:4326 via Geotools
└── db/
    ├── DatabaseConnection.java        PostgreSQL connection and Flyway setup via jOOQ
    ├── MunicipalityImporter.java      Upserts municipality names (JSON) and boundaries (GML Kunta)
    ├── AddressPointImporter.java      Upserts/deletes Osoitepiste features
    ├── RoadSegmentImporter.java       Upserts/deletes Tieviiva features
    └── NamedPlaceImporter.java        Upserts/deletes Paikannimi features, resolves municipality
```

---

## 6. CLI Interface

```
java -jar gis-data-importer.jar \
    --db-url jdbc:postgresql://localhost:5432/idispatchx_gis \
    --db-user gis_importer \
    --db-password <password> \
    --input /path/to/L3311R.xml [/path/to/L3312R.xml ...] \
    [--input-dir /path/to/gml/directory] \
    [--municipalities /path/to/codelist_kunta_1_20250101.json] \
    [--truncate]                   # Truncate all tables before import (full import mode)
    [--features kunta,tieviiva,osoitepiste,paikannimi]  # Import only specific GML feature types
```

| Argument | Required | Description |
|----------|----------|-------------|
| `--db-url` | Yes | JDBC URL for the PostgreSQL database |
| `--db-user` | Yes | Database username |
| `--db-password` | Yes | Database password |
| `--input` | Yes* | One or more GML file paths |
| `--input-dir` | Yes* | Directory containing GML files. All `*.xml` files in the directory are imported. Not recursive. |
| `--municipalities` | No | Path to municipality reference JSON from koodistot.suomi.fi |
| `--truncate` | No | Enable full import mode (truncate before insert) |
| `--features` | No | Comma-separated list of GML feature types to import (default: all four) |

*At least one of `--input`, `--input-dir`, or `--municipalities` must be provided. `--input` and `--input-dir` can be combined (files are merged into a single set, duplicates ignored). Both file arguments can be used together with `--municipalities`.

The importer exits with code 0 on success and non-zero on failure, printing a summary of imported/updated/deleted record counts per feature type.

---

## 7. Geocoding Query Patterns

This section briefly describes how the GIS Server will use the schema for `UC-Lookup-Address`. The geocoding API design is out of scope for this document.

### 7.1 Address Search Strategy

Address geocoding uses two data sources, queried in priority order:

1. **Address points** (`gis.address_point`) — precise coordinates for specific addresses where available.
2. **Road segments** (`gis.road_segment`) — address range interpolation for addresses not covered by address points.

The GIS Server first searches address points for an exact match. If none is found (or to supplement results), it falls back to road segment interpolation. Both result types map to the `ExactAddress` domain variant.

### 7.2 Address Point Lookup

When the dispatcher enters a street name and optional number (e.g., "Kuggö 427"):

```sql
SELECT ap.id, ap.number, ap.name_fi, ap.name_sv, ap.municipality_code,
       m.name_fi AS municipality_name_fi, m.name_sv AS municipality_name_sv,
       ST_Y(ap.location) AS latitude, ST_X(ap.location) AS longitude
FROM gis.address_point ap
LEFT JOIN gis.municipality m ON m.municipality_code = ap.municipality_code
WHERE ap.name_fi % :street_name OR ap.name_sv % :street_name
ORDER BY similarity(COALESCE(ap.name_fi, ap.name_sv), :street_name) DESC
LIMIT 20;
```

The `%` operator uses pg_trgm for fuzzy matching. Results are ranked by similarity. If an address number was provided, results can be further filtered.

### 7.3 Road Segment Address Interpolation

For addresses without a dedicated address point, the GIS Server finds matching road segments and interpolates a position along the geometry. For example, searching "Strandvägen 1":

**Step 1 — Find matching road segments whose address range includes the number:**

```sql
SELECT rs.id, rs.name_fi, rs.name_sv, rs.municipality_code,
       rs.min_address_left, rs.max_address_left,
       rs.min_address_right, rs.max_address_right,
       rs.geometry,
       m.name_fi AS municipality_name_fi, m.name_sv AS municipality_name_sv
FROM gis.road_segment rs
LEFT JOIN gis.municipality m ON m.municipality_code = rs.municipality_code
WHERE (rs.name_fi % :street_name OR rs.name_sv % :street_name)
  AND (
    (:number BETWEEN rs.min_address_left  AND rs.max_address_left) OR
    (:number BETWEEN rs.min_address_right AND rs.max_address_right)
  )
ORDER BY similarity(COALESCE(rs.name_fi, rs.name_sv), :street_name) DESC
LIMIT 10;
```

**Step 2 — Interpolate position along the road geometry:**

Given a matching segment with address range [min, max] and requested number N, the fraction along the road is:

```
fraction = (N - min) / (max - min)
```

The interpolated coordinate is computed using PostGIS:

```sql
ST_LineInterpolatePoint(geometry, fraction)
```

This produces an approximate coordinate along the road centerline. The dispatcher sees this point on the map and can manually move the marker to the correct building. This behavior is by design — the geocoded coordinate is a starting point, not a precise building location.

**Note on odd/even address numbering:** In Finnish addressing, odd numbers are typically on one side of the road and even numbers on the other. The address ranges (`min_address_left`, `max_address_left`, `min_address_right`, `max_address_right`) reflect this. The GIS Server should match the requested number against the correct side (left or right) based on odd/even parity where applicable.

### 7.4 Place Name Search

When the dispatcher enters a place name (e.g., "Gulskär"):

```sql
SELECT np.kartanimi_id, np.name, np.language, np.place_class,
       np.municipality_code,
       m.name_fi AS municipality_name_fi, m.name_sv AS municipality_name_sv,
       ST_Y(np.location) AS latitude, ST_X(np.location) AS longitude
FROM gis.named_place np
LEFT JOIN gis.municipality m ON m.municipality_code = np.municipality_code
WHERE np.name % :query
ORDER BY similarity(np.name, :query) DESC
LIMIT 20;
```

The GIS Server groups results by `kartanimi_id` to merge multilingual name variants into a single result.

### 7.5 Road Intersection Search

When the dispatcher enters two road names (e.g., "Kuggöntie / Pensarintie"):

```sql
SELECT ST_Y(ST_Centroid(ST_Intersection(a.geometry, b.geometry))) AS latitude,
       ST_X(ST_Centroid(ST_Intersection(a.geometry, b.geometry))) AS longitude,
       a.name_fi AS road_a_fi, a.name_sv AS road_a_sv,
       b.name_fi AS road_b_fi, b.name_sv AS road_b_sv,
       a.municipality_code
FROM gis.road_segment a
JOIN gis.road_segment b ON ST_Intersects(a.geometry, b.geometry) AND a.id < b.id
WHERE (a.name_fi % :road_a OR a.name_sv % :road_a)
  AND (b.name_fi % :road_b OR b.name_sv % :road_b)
LIMIT 10;
```

### 7.6 Result Type Mapping

Geocoding results map to domain Location variants:

| Source | Location Variant | Type Label | Coordinate Precision |
|--------|-----------------|------------|---------------------|
| `gis.address_point` | ExactAddress | "address" | Precise point from NLS |
| `gis.road_segment` (interpolated) | ExactAddress | "address" | Approximate — interpolated along road centerline |
| `gis.named_place` | NamedPlace | "place" | Anchor point from NLS |
| `gis.road_segment` (intersection) | RoadIntersection | "intersection" | Computed from geometry intersection |

Both address point and road segment interpolation results map to `ExactAddress`. The dispatcher can always adjust the marker position on the map after the initial geocoding result is displayed.

---

## 8. Verification Strategy

### 8.1 Import Verification

After importing `L3311R.xml`, verify:

| Check | Expected |
|-------|----------|
| Municipality count | 2 records (codes 322 and 445) |
| Address point count | ~12 records (all with municipality code 445) |
| Road segment count | ~80 records |
| Named place count | ~250 records |
| All coordinates within Finland bounds | No latitude < 58.84 or > 70.09, no longitude < 19.08 or > 31.59 |

### 8.2 Spatial Integrity

```sql
-- Verify address points fall within municipality boundaries
SELECT ap.id, ap.municipality_code
FROM gis.address_point ap
JOIN gis.municipality m ON m.municipality_code = ap.municipality_code
WHERE NOT ST_Contains(m.boundary, ap.location);
-- Expected: 0 rows (all address points inside their declared municipality)
```

### 8.3 Geocoding Smoke Tests

```sql
-- Address point search for "Kuggö"
SELECT name_fi, name_sv, number, municipality_code,
       ST_Y(location) AS lat, ST_X(location) AS lon
FROM gis.address_point
WHERE name_fi % 'Kuggö' OR name_sv % 'Kuggö'
ORDER BY similarity(COALESCE(name_fi, name_sv), 'Kuggö') DESC;
-- Expected: Multiple address points on Kuggö with coordinates near lat ~60.1, lon ~21.8

-- Road segment address interpolation for "Kuggöntie 5"
SELECT rs.name_fi, rs.name_sv,
       rs.min_address_left, rs.max_address_left,
       rs.min_address_right, rs.max_address_right,
       ST_Y(ST_LineInterpolatePoint(rs.geometry, 0.5)) AS approx_lat,
       ST_X(ST_LineInterpolatePoint(rs.geometry, 0.5)) AS approx_lon
FROM gis.road_segment rs
WHERE (rs.name_fi = 'Kuggöntie' OR rs.name_sv = 'Kuggövägen')
  AND (5 BETWEEN rs.min_address_left AND rs.max_address_left
    OR 5 BETWEEN rs.min_address_right AND rs.max_address_right);
-- Expected: Road segment(s) with address ranges covering number 5, with interpolated coordinate along the road
```

### 8.4 Incremental Import Test

1. Import `L3311R.xml` (full import with `--truncate`).
2. Verify record counts.
3. Re-import the same file without `--truncate` (incremental mode).
4. Verify record counts unchanged (UPSERT updated existing records, no duplicates).
5. Create a test patch file with one feature having `loppupvm` set.
6. Import the patch file.
7. Verify the feature was deleted.
