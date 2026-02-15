# Technical Design: GIS Data Import and Database Schema

## Overview

This document describes the PostGIS database schema used by the GIS Server for geocoding, and the design of the GIS Data Importer CLI tool that populates it from two data sources:

1. **NLS topographic GML files** — municipality boundaries, road segments, address points, and place names from the National Land Survey of Finland (Maastotietojärjestelmä).
2. **Municipality reference JSON** — municipality codes and multilingual names from the Finnish interoperability platform (koodistot.suomi.fi).

The GIS Server provides geocoding services to dispatchers (address/place name lookup), and map tiles via WMTS. Sections 1–8 cover the geocoding data layer (GML and JSON to PostGIS). Sections 9–13 cover raster tile import from NLS source images to the filesystem tile directory served by the GIS Server's WMTS endpoint.

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
- [UX: Dispatcher Client Guidelines](../UXDesigns/Dispatcher-Client-UX-Guidelines.md) — Background layer selection, map component
- JHS 180: ETRS-TM35FIN tile matrix set standard for Finnish WMTS services
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
| `<paikannimet>` | `<Paikannimi>` | Point (Piste) | Named places (islands, villages, landmarks) |

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
    karttanimi_id       BIGINT,
    municipality_code  VARCHAR(3),
    location           GEOMETRY(POINT, 4326)       NOT NULL,
    imported_at        TIMESTAMPTZ                 NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_named_place_location       ON gis.named_place USING GIST (location);
CREATE INDEX idx_named_place_name           ON gis.named_place USING GIN  (name gin_trgm_ops);
CREATE INDEX idx_named_place_karttanimi_id   ON gis.named_place (karttanimi_id);
CREATE INDEX idx_named_place_municipality   ON gis.named_place (municipality_code);
```

**Design notes**:
- Multiple rows may share the same `karttanimi_id` — these are the same place in different languages. The GIS Server groups by `karttanimi_id` when building multilingual geocoding results.
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

**PaikannimiFeature** (from GML): `gid`, `alkupvm`, `loppupvm`, `teksti`, `kieli`, `kohdeluokka`, `karttanimiId`, `pointEasting`, `pointNorthing`

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
├── raster/
│   ├── WorldFileParser.java           Parses .pgw world files into affine transform parameters
│   ├── TileMatrixSet.java             ETRS-TM35FIN tile matrix set constants and tile coordinate math
│   ├── TileExtractor.java             Extracts 256×256 tile images from a source BufferedImage
│   ├── TileWriter.java                Writes tile PNGs to the filesystem, composites with existing tiles
│   └── RasterTileImporter.java        Orchestrates the raster tile import pipeline
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
    [--tiles /path/to/L3311F.png ...] \
    [--tile-input-dir /path/to/raster/directory] \
    [--tile-dir /data/tiles] \
    [--tile-layer terrain] \
    [--truncate]                   # Truncate before import (full import mode)
    [--features kunta,tieviiva,osoitepiste,paikannimi]  # Import only specific GML feature types
```

| Argument | Required | Description |
|----------|----------|-------------|
| `--db-url` | Yes† | JDBC URL for the PostgreSQL database |
| `--db-user` | Yes† | Database username |
| `--db-password` | Yes† | Database password |
| `--input` | Yes* | One or more GML file paths |
| `--input-dir` | Yes* | Directory containing GML files. All `*.xml` files in the directory are imported. Not recursive. |
| `--municipalities` | No | Path to municipality reference JSON from koodistot.suomi.fi |
| `--tiles` | Yes* | One or more source raster PNG file paths. World files (`.pgw`) must be co-located (same directory, same base name). |
| `--tile-input-dir` | Yes* | Directory containing source raster PNG files. All `*.png` files with co-located `.pgw` world files are imported. Not recursive. |
| `--tile-dir` | Yes‡ | Base directory for tile output. Must exist and be writable. |
| `--tile-layer` | Yes‡ | Layer name for the imported tiles (e.g., `terrain`, `buildings`). |
| `--truncate` | No | Enable full import mode. For GML data: truncates database tables. For tiles: removes the specified layer's tile directory before import. |
| `--features` | No | Comma-separated list of GML feature types to import (default: all four) |

\*At least one of `--input`, `--input-dir`, `--municipalities`, `--tiles`, or `--tile-input-dir` must be provided. File arguments can be combined. †Database arguments are required when importing GML/JSON data (`--input`, `--input-dir`, or `--municipalities`). They are not required for tile-only imports. ‡Required when `--tiles` or `--tile-input-dir` is provided.

Tile import and GML/JSON import are independent and can run in the same invocation or separately.

The importer exits with code 0 on success and non-zero on failure, printing a summary of imported/updated/deleted record counts per feature type and tile counts per layer/zoom level.

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
SELECT np.karttanimi_id, np.name, np.language, np.place_class,
       np.municipality_code,
       m.name_fi AS municipality_name_fi, m.name_sv AS municipality_name_sv,
       ST_Y(np.location) AS latitude, ST_X(np.location) AS longitude
FROM gis.named_place np
LEFT JOIN gis.municipality m ON m.municipality_code = np.municipality_code
WHERE np.name % :query
ORDER BY similarity(np.name, :query) DESC
LIMIT 20;
```

The GIS Server groups results by `karttanimi_id` to merge multilingual name variants into a single result.

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

### 8.5 Tile Import Verification

After importing `L3311F.png` with `--tile-layer terrain --tile-dir /data/tiles`:

| Check | Expected |
|-------|----------|
| Zoom level detected | 14 (pixel size 0.5m) |
| Tile directory created | `/data/tiles/terrain/ETRS-TM35FIN/14/` |
| Tile row directories | 48 directories (13364 through 13411) |
| Total tile files | Up to 2,304 (48 × 48; some edge tiles may be skipped if fully outside source coverage) |
| Tile dimensions | All tiles are 256 × 256 pixels |
| Tile format | PNG with alpha channel (RGBA) |
| Edge tiles | Tiles at boundaries have transparent pixels where no source data exists |
| Interior tiles | Fully opaque, contain map imagery |
| Visual spot check | Open tile at row 13388, col 6058 (approximately center of source image). Should show archipelago map imagery consistent with the source. |

### 8.6 Tile Serving Verification

After importing tiles and starting the GIS Server:

```
# Request a known tile
GET /wmts/terrain/ETRS-TM35FIN/14/13388/6058.png
# Expected: 200 OK, Content-Type: image/png, 256×256 pixel image

# Request a tile outside coverage
GET /wmts/terrain/ETRS-TM35FIN/14/0/0.png
# Expected: 204 No Content (or transparent tile)

# Request a tile at a non-imported zoom level (runtime resampling)
GET /wmts/terrain/ETRS-TM35FIN/13/6694/3029.png
# Expected: 200 OK, resampled from level 14 tiles
```

---

## 9. NLS Raster Source Format

NLS (National Land Survey of Finland, Maanmittauslaitos) distributes pre-rendered raster map images as PNG files with accompanying world files providing georeferencing. Each map sheet covers a fixed geographic area and is available at one or more product scales.

### 9.1 Map Sheet Naming

NLS raster files follow the Finnish national grid map sheet naming convention. For example, `L3311F`:

- `L`: major grid zone letter
- `3311`: numeric grid subdivision
- `F`: sub-sheet partition (A through H at finer divisions)

The same geographic area may be covered by multiple NLS products at different scales (e.g., terrain maps at 0.5 m/pixel, overview maps at 2 m/pixel). Each product scale is a separate download set.

### 9.2 Source Files

Each map sheet consists of two files:

| File | Example | Description |
|------|---------|-------------|
| PNG image | `L3311F.png` | Pre-rendered map image |
| World file | `L3311F.pgw` | Georeferencing via 6 affine transform coefficients |

The world file must share the same base name as the PNG file, with the `.pgw` extension, and be co-located in the same directory.

### 9.3 World File Format

The world file is a 6-line text file defining an affine transformation from pixel coordinates to map coordinates in EPSG:3067:

```
Line 1: pixel width in meters (easting direction)
Line 2: rotation about Y axis (always 0.0 for NLS data)
Line 3: rotation about X axis (always 0.0 for NLS data)
Line 4: pixel height in meters (negative = north-up)
Line 5: X coordinate (easting) of center of upper-left pixel
Line 6: Y coordinate (northing) of center of upper-left pixel
```

**Example** (`L3311F.pgw`):

```
0.5
0.0
0.0
-0.5
224000.25
6677999.75
```

This means:
- Each pixel is 0.5 × 0.5 meters on the ground.
- The **center** of the upper-left pixel is at easting 224000.25, northing 6677999.75 in EPSG:3067.
- The upper-left **corner** of the image raster (edge of first pixel) is at `(224000.25 − 0.5/2, 6677999.75 + 0.5/2)` = `(224000.0, 6678000.0)`.

All coordinates are in EPSG:3067 (EUREF-FIN / TM35FIN). Per the Internationalization NFR, the system uses EPSG:3067 for rasters — no CRS conversion is needed between the source data and the WMTS tile grid.

### 9.4 PNG Image Characteristics

Based on the sample data (`L3311F.png`):

| Property | Value |
|----------|-------|
| Dimensions | 12,000 × 12,000 pixels |
| Bit depth | 8-bit |
| Color type | Indexed color (palette PNG) |
| File size | ~1.3 MB |
| Content | Pre-rendered topographic map: terrain coloring, water, roads, place names |

The image dimensions and pixel size determine the geographic coverage: 12,000 pixels × 0.5 m/pixel = 6,000 m in each direction. The sample image covers the area from (224000, 6672000) to (230000, 6678000) in EPSG:3067 — part of the Turku archipelago (municipalities 322 Kemiönsaari and 445 Parainen).

### 9.5 Pixel Size and Zoom Level

The absolute value of the pixel width (world file line 1) determines which WMTS zoom level the source image matches. In the ETRS-TM35FIN tile matrix set (section 10), each zoom level has a defined pixel size. A source image with 0.5 m/pixel corresponds to zoom level 14.

NLS provides separate raster products at different scales. Each is imported independently, producing tiles at the corresponding zoom level:

| NLS Product Scale | Pixel Size | Zoom Level |
|-------------------|------------|------------|
| 1:5,000 | 0.5 m | 14 |
| 1:10,000 | 1 m | 13 |
| 1:20,000 | 2 m | 12 |
| 1:40,000 | 4 m | 11 |
| 1:80,000 | 8 m | 10 |
| 1:160,000 | 16 m | 9 |
| 1:320,000 | 32 m | 8 |

Not all NLS products are available at every scale. The administrator imports whichever scales are available. Zoom levels without pre-rendered tiles are handled by runtime resampling in the GIS Server (section 13).

---

## 10. ETRS-TM35FIN Tile Matrix Set

The WMTS tile matrix set follows the Finnish JHS 180 standard. Both NLS source data and the tile grid use EPSG:3067, so no coordinate reprojection is needed during tile import.

### 10.1 Tile Matrix Set Parameters

| Parameter | Value |
|-----------|-------|
| Identifier | `ETRS-TM35FIN` |
| CRS | EPSG:3067 (EUREF-FIN / TM35FIN) |
| Tile size | 256 × 256 pixels |
| Top-left origin | (−548576.0, 8388608.0) in EPSG:3067 |
| Zoom levels | 0 through 15 |
| Pixel size progression | Halves at each level, starting at 8192 m at level 0 |

### 10.2 Tile Matrix Table

| Level | Pixel Size (m) | Tile Span (m) | Scale Denominator |
|-------|----------------|---------------|-------------------|
| 0 | 8192 | 2,097,152 | 29,257,143 |
| 1 | 4096 | 1,048,576 | 14,628,571 |
| 2 | 2048 | 524,288 | 7,314,286 |
| 3 | 1024 | 262,144 | 3,657,143 |
| 4 | 512 | 131,072 | 1,828,571 |
| 5 | 256 | 65,536 | 914,286 |
| 6 | 128 | 32,768 | 457,143 |
| 7 | 64 | 16,384 | 228,571 |
| 8 | 32 | 8,192 | 114,286 |
| 9 | 16 | 4,096 | 57,143 |
| 10 | 8 | 2,048 | 28,571 |
| 11 | 4 | 1,024 | 14,286 |
| 12 | 2 | 512 | 7,143 |
| 13 | 1 | 256 | 3,571 |
| 14 | 0.5 | 128 | 1,786 |
| 15 | 0.25 | 64 | 893 |

**Formulas**:
- Pixel size at level `z`: `8192 / 2^z` meters
- Tile span at level `z`: `pixel_size × 256` meters
- Scale denominator: `pixel_size / 0.00028` (OGC standard pixel size of 0.28 mm)

### 10.3 Tile Coordinate System

Column index increases eastward and row index increases southward from the top-left origin:

```
col = floor((easting  − origin_x) / tile_span)
row = floor((origin_y − northing) / tile_span)
```

Where `origin_x = −548576.0`, `origin_y = 8388608.0`, and `tile_span = pixel_size × 256`.

**Inverse** (tile bounds in EPSG:3067 meters):

```
tile_west  = origin_x + col × tile_span
tile_north = origin_y − row × tile_span
tile_east  = tile_west  + tile_span
tile_south = tile_north − tile_span
```

### 10.4 Sample Data Tile Coverage

For `L3311F.png` at zoom level 14 (tile span = 128 m):

| Property | Value |
|----------|-------|
| Source upper-left | (224000, 6678000) |
| Source lower-right | (230000, 6672000) |
| First column | floor((224000 − (−548576)) / 128) = floor(6035.75) = 6035 |
| Last column | floor((230000 − (−548576)) / 128 − ε) = 6082 |
| First row | floor((8388608 − 6678000) / 128) = floor(13364.125) = 13364 |
| Last row | floor((8388608 − 6672000) / 128 − ε) = 13411 |
| Columns | 48 (6035 through 6082) |
| Rows | 48 (13364 through 13411) |
| Total tiles | up to 2,304 |

The source image does not align to tile boundaries (fractional offsets of 0.75 tiles in column direction and 0.125 tiles in row direction). Tiles at the edges of the source image are only partially covered; uncovered pixels are transparent.

---

## 11. Tile Directory Structure

Pre-rendered tiles are stored on the filesystem in a hierarchy that maps directly to the WMTS RESTful URL pattern. The GIS Server reads tiles from this directory without any database involvement.

### 11.1 Directory Layout

```
{base-dir}/
└── {layer}/
    └── ETRS-TM35FIN/
        └── {zoom}/
            └── {row}/
                └── {col}.png
```

**Example** (after importing `L3311F.png` into the `terrain` layer):

```
/data/tiles/
└── terrain/
    └── ETRS-TM35FIN/
        └── 14/
            ├── 13364/
            │   ├── 6035.png
            │   ├── 6036.png
            │   └── ...
            ├── 13365/
            │   └── ...
            └── 13411/
                └── ...
```

### 11.2 Path Components

| Component | Description |
|-----------|-------------|
| `{base-dir}` | Root tile directory. Configured in the GIS Server and specified via `--tile-dir` in the importer. |
| `{layer}` | Layer identifier (e.g., `terrain`, `buildings`). One subdirectory per tile layer. Matches the UX Guidelines: "Select background layer (GIS Server may provide different tile sets, one for terrain, another for navigation and buildings, etc)". |
| `ETRS-TM35FIN` | Tile matrix set identifier. Fixed for this system. Exists for WMTS standard compliance. |
| `{zoom}` | Zoom level (0–15). Integer directory name. |
| `{row}` | Tile row index. Integer directory name. |
| `{col}.png` | Tile column index as filename, with `.png` extension. |

### 11.3 WMTS URL Mapping

The directory structure maps directly to the WMTS RESTful URL:

```
GET /wmts/{layer}/ETRS-TM35FIN/{zoom}/{row}/{col}.png
```

The GIS Server resolves a tile request by constructing the filesystem path `{base-dir}/{layer}/ETRS-TM35FIN/{zoom}/{row}/{col}.png` and streaming the file. Missing files result in HTTP 204 No Content (empty tile).

### 11.4 Tile Image Format

| Property | Value |
|----------|-------|
| Format | PNG |
| Dimensions | 256 × 256 pixels |
| Color mode | RGBA (RGB with alpha channel) |
| Partial coverage | Transparent pixels (alpha = 0) where no source data exists |

RGBA is used instead of indexed color to support alpha transparency for partial-coverage edge tiles and to simplify compositing when multiple source images contribute to the same tile.

### 11.5 Layer Discovery

The GIS Server discovers available layers at startup by listing subdirectories of `{base-dir}/`. Each subdirectory name is registered as an available tile layer and exposed in the WMTS GetCapabilities response. No configuration file is needed — the filesystem is the source of truth for available layers.

### 11.6 Storage Estimates

For a typical municipal dispatch area (~50 km × 50 km) at zoom level 14 (0.5 m/pixel):

| Metric | Value |
|--------|-------|
| Tiles at level 14 | ~150,000 |
| Average tile size | 10–50 KB (map imagery compresses well as PNG) |
| Total storage at level 14 | ~2–5 GB |
| All zoom levels combined | ~3–7 GB |

Well within modern filesystem and disk capacity.

---

## 12. Raster Tile Import Strategy

The importer reads source PNG + world file pairs, determines the matching WMTS zoom level, calculates which tiles are covered, extracts each 256 × 256 tile region from the source image, and writes it to the tile directory.

### 12.1 Import Pipeline

**Step 1 — Parse world file:**

Read the 6-line `.pgw` file and extract:
- `pixel_width` (line 1): pixel size in meters
- `pixel_height` (line 4): pixel size in meters (negative for north-up)
- `ul_center_x` (line 5): easting of center of upper-left pixel
- `ul_center_y` (line 6): northing of center of upper-left pixel

Compute the upper-left corner (edge of first pixel):
```
ul_x = ul_center_x − pixel_width / 2
ul_y = ul_center_y − pixel_height / 2     (pixel_height is negative, so this adds)
```

Validations:
- Rotation coefficients (lines 2 and 3) must be 0.0. Non-zero rotation is not supported.
- `pixel_width` must equal `abs(pixel_height)` (square pixels).
- Source bounds must fall within EPSG:3067 coverage area (easting 43,547.79 to 764,796.72; northing 6,522,236.87 to 7,795,461.19).

Files that fail validation are logged as errors and skipped.

**Step 2 — Read source image:**

Load the PNG using `javax.imageio.ImageIO.read()`. If the image is indexed color (palette PNG, `BufferedImage.TYPE_BYTE_INDEXED`), convert to `BufferedImage.TYPE_INT_ARGB`:

```java
BufferedImage source = ImageIO.read(pngFile);
if (source.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
    BufferedImage converted = new BufferedImage(
        source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = converted.createGraphics();
    g.drawImage(source, 0, 0, null);
    g.dispose();
    source = converted;
}
```

ARGB conversion is needed because output tiles require an alpha channel for partial coverage, and `Graphics2D` compositing operates reliably on ARGB images. The conversion happens once per source image.

**Step 3 — Determine zoom level:**

Match the pixel size to the tile matrix set:

```java
int zoomLevel = (int) Math.round(Math.log(8192.0 / pixelWidth) / Math.log(2));
double expectedPixelSize = 8192.0 / Math.pow(2, zoomLevel);
if (Math.abs(pixelWidth - expectedPixelSize) > 0.001) {
    // Pixel size does not match any zoom level — log error and skip
}
```

For the sample data (0.5 m/pixel): `log2(8192 / 0.5) = log2(16384) = 14`.

**Step 4 — Calculate overlapping tiles:**

```
tile_span = pixel_width × 256

col_min = floor((ul_x − origin_x) / tile_span)
col_max = floor((lr_x − origin_x) / tile_span)
row_min = floor((origin_y − ul_y) / tile_span)
row_max = floor((origin_y − lr_y) / tile_span)
```

Where `lr_x = ul_x + image_width × pixel_width` and `lr_y = ul_y − image_height × abs(pixel_height)`.

The last column/row is calculated from the lower-right corner of the last pixel, not from the center. Careful handling of the boundary prevents off-by-one errors.

**Step 5 — Extract and write tiles:**

For each tile `(row, col)` in the range `[row_min..row_max] × [col_min..col_max]`:

1. Compute the tile's geographic bounds using the inverse formula from section 10.3.

2. Compute the corresponding pixel region in the source image:
   ```
   src_x = (tile_west − ul_x) / pixel_width
   src_y = (ul_y − tile_north) / abs(pixel_height)
   ```

3. Create a 256 × 256 ARGB `BufferedImage` initialized to fully transparent (all zeros).

4. Draw the relevant portion of the source image onto the tile, clamping to source image bounds:
   ```java
   int srcX = (int) Math.round(src_x);
   int srcY = (int) Math.round(src_y);
   int clipX = Math.max(0, srcX);
   int clipY = Math.max(0, srcY);
   int clipW = Math.min(source.getWidth(), srcX + 256) - clipX;
   int clipH = Math.min(source.getHeight(), srcY + 256) - clipY;
   if (clipW > 0 && clipH > 0) {
       int destX = clipX - srcX;
       int destY = clipY - srcY;
       g.drawImage(source,
           destX, destY, destX + clipW, destY + clipH,
           clipX, clipY, clipX + clipW, clipY + clipH,
           null);
   }
   ```

5. If the tile is fully transparent (no source pixels covered this tile), skip writing it.

6. Write the tile PNG to the filesystem:
   ```java
   Path tilePath = tileDir
       .resolve(layer)
       .resolve("ETRS-TM35FIN")
       .resolve(String.valueOf(zoomLevel))
       .resolve(String.valueOf(row))
       .resolve(col + ".png");
   Files.createDirectories(tilePath.getParent());
   ImageIO.write(tile, "PNG", tilePath.toFile());
   ```

### 12.2 Compositing Multiple Source Images

When multiple source images cover the same geographic area (common at map sheet boundaries), a tile may receive data from more than one source file. The importer handles this by compositing:

1. Before writing, check if the tile file already exists on disk.
2. If it exists, read the existing tile, draw the new tile content on top using `AlphaComposite.SRC_OVER`:

```java
if (Files.exists(tilePath)) {
    BufferedImage existing = ImageIO.read(tilePath.toFile());
    Graphics2D g = existing.createGraphics();
    g.setComposite(AlphaComposite.SRC_OVER);
    g.drawImage(newTile, 0, 0, null);
    g.dispose();
    tile = existing;
}
```

This compositing is order-independent for non-overlapping source images (different source images cover different pixels in the same tile — common at sheet boundaries). For truly overlapping source images (same pixel covered by multiple sources), the last import wins for those pixels.

**Note:** The importer is not idempotent for tiles — running the same import twice layers data on itself. Use `--truncate` for a clean rebuild.

### 12.3 No Overview Generation

The importer generates tiles only at the zoom level matching the source pixel size. It does **not** generate overview tiles at lower zoom levels by downsampling.

Rationale:
- NLS provides pre-rendered images at multiple scales. Each scale has purpose-designed cartographic rendering (different label placement, feature generalization, line widths). Downsampling from a higher-detail scale produces inferior results compared to importing the NLS product at the target scale.
- For zoom levels between NLS-provided scales, the GIS Server performs runtime resampling (section 13).
- This keeps the importer simple: one source pixel size maps to exactly one zoom level.

To populate multiple zoom levels, the administrator imports separate NLS product sets at different scales. Each import invocation targets the zoom level matching that product's pixel size.

### 12.4 Image Processing Library

The importer uses `javax.imageio.ImageIO` and `java.awt.image.BufferedImage` from the Java standard library for all raster tile operations. Geotools raster support (`GridCoverage2D`, etc.) is not used.

Rationale:
- Per the Maintainability NFR, the system prefers standard libraries over third-party dependencies.
- Tile extraction is geometrically simple: read sub-rectangles of a source image and write them as separate PNGs. No reprojection is needed (source data and tile matrix set are both EPSG:3067). No band math, resampling, or other complex raster operations are needed at import time.
- `javax.imageio` handles PNG reading and writing. `BufferedImage` and `Graphics2D` handle sub-image extraction and alpha compositing. Both are part of the Java standard library.

### 12.5 Import Logging

Each source file import is logged to stdout with:
- Source filename
- Detected zoom level and pixel size
- Geographic bounds (EPSG:3067)
- Tile range (columns and rows)
- Number of tiles written, composited, and skipped (empty)
- Duration

Tile imports are not logged to the `gis.import_log` database table (that table tracks GML/JSON imports). Tile import progress is stdout-only.

---

## 13. GIS Server Tile Serving

This section describes how the GIS Server uses the tile directory for WMTS responses, including runtime resampling for zoom levels without pre-rendered tiles. The full WMTS endpoint API design is out of scope for this document.

### 13.1 Direct File Serving

For a WMTS tile request at a zoom level that has pre-rendered tiles:

1. Construct the filesystem path: `{base-dir}/{layer}/ETRS-TM35FIN/{z}/{row}/{col}.png`
2. If the file exists, stream it as `image/png` with appropriate cache headers.
3. If the file does not exist, return HTTP 204 No Content.

This handles the common case and trivially meets the Performance NFR (< 1 second) — it is a filesystem read of a small file (10–50 KB). The OS page cache ensures frequently accessed tiles are served from memory.

### 13.2 Runtime Resampling

When a tile is requested at a zoom level for which no pre-rendered tiles exist, the GIS Server resamples from the nearest available **coarser** (lower zoom number, smaller scale) level that has tile data. NLS provides purpose-rendered map images at specific scales with appropriate cartographic generalization for each. A tile upsampled from a coarser NLS product preserves the visual style intended for that scale range, rather than producing a downsampled version of a finer product whose labels and features are too dense for the requested scale.

For example, if pre-rendered tiles exist at levels 8, 10, 12, and 14:
- A request at level 9 is **upsampled** from level 8.
- A request at level 11 is **upsampled** from level 10.
- A request at level 13 is **upsampled** from level 12.
- A request at level 15 is **upsampled** from level 14.

**Upsampling process** (e.g., level 8 → level 9):

1. One tile at level 9 covers the same area as one **quarter** of a tile at level 8 (each zoom level doubles the resolution).
2. Determine which level-8 tile contains the requested level-9 tile's geographic area.
3. Read the level-8 tile from the filesystem.
4. Extract the relevant 128 × 128 pixel quadrant.
5. Scale up to 256 × 256 pixels using bilinear interpolation (`RenderingHints.VALUE_INTERPOLATION_BILINEAR`).
6. Serve the result.

For a gap of `n` levels (e.g., level 8 → level 10, n = 2):
- The requested tile corresponds to a `256 / 2^n` × `256 / 2^n` pixel region in the source tile (e.g., 64 × 64 for n = 2).
- That region is scaled up to 256 × 256.

### 13.3 Resampling Limits

- **Maximum resampling depth**: 3 levels (source region as small as 32 × 32 pixels scaled to 256 × 256). Beyond this, quality degrades to the point where the map is not usably readable.
- If no source data is available within 3 levels, return HTTP 204 No Content (empty tile).

### 13.4 Resampled Tile Cache

To avoid repeatedly resampling the same tile:

- Resampled tiles are cached in an **in-memory LRU cache** (e.g., `LinkedHashMap` with access-order eviction).
- Cache key: `(layer, zoom, row, col)`.
- Default cache size: ~1,000 tiles (~50 MB at ~50 KB per tile). Configurable.
- Cache is invalidated when the tile directory is modified (e.g., after a new import run).

The cache avoids writing resampled tiles to the filesystem, keeping the tile directory as the single source of truth for pre-rendered tiles only.

### 13.5 Layer Discovery

At startup, the GIS Server scans `{base-dir}/` for subdirectories. Each subdirectory name becomes an available tile layer. For each layer, the server scans `{layer}/ETRS-TM35FIN/` for zoom level subdirectories to determine which zoom levels have pre-rendered tiles. This information is used in the WMTS GetCapabilities response and for resampling source level selection.
