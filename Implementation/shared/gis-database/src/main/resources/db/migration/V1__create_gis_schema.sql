-- Extensions
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Schema
CREATE SCHEMA IF NOT EXISTS gis;

-- Municipality reference data: codes, multilingual names, and boundary polygons
CREATE TABLE gis.municipality (
    municipality_code  VARCHAR(3)                          PRIMARY KEY,
    name_fi            VARCHAR(200),
    name_sv            VARCHAR(200),
    name_smn           VARCHAR(200),
    name_sms           VARCHAR(200),
    name_sme           VARCHAR(200),
    boundary           GEOMETRY(MULTIPOLYGON, 4326),
    imported_at        TIMESTAMPTZ                         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_municipality_boundary ON gis.municipality USING GIST (boundary);

-- Address points (Osoitepiste features)
CREATE TABLE gis.address_point (
    id                 BIGINT                              PRIMARY KEY,
    number             VARCHAR(20),
    name_fi            VARCHAR(200),
    name_sv            VARCHAR(200),
    name_smn           VARCHAR(200),
    name_sms           VARCHAR(200),
    name_sme           VARCHAR(200),
    municipality_code  VARCHAR(3),
    location           GEOMETRY(POINT, 4326)               NOT NULL,
    imported_at        TIMESTAMPTZ                         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_address_point_location     ON gis.address_point USING GIST (location);
CREATE INDEX idx_address_point_name_fi      ON gis.address_point USING GIN  (name_fi  gin_trgm_ops);
CREATE INDEX idx_address_point_name_sv      ON gis.address_point USING GIN  (name_sv  gin_trgm_ops);
CREATE INDEX idx_address_point_municipality ON gis.address_point (municipality_code);

-- Road segments (Tieviiva features)
CREATE TABLE gis.road_segment (
    id                    BIGINT                            PRIMARY KEY,
    road_class            INT                               NOT NULL,
    surface_type          SMALLINT                          NOT NULL,
    administrative_class  SMALLINT,
    one_way               SMALLINT                          NOT NULL,
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
    geometry              GEOMETRY(LINESTRING, 4326)        NOT NULL,
    imported_at           TIMESTAMPTZ                       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_road_segment_geometry     ON gis.road_segment USING GIST (geometry);
CREATE INDEX idx_road_segment_name_fi      ON gis.road_segment USING GIN  (name_fi gin_trgm_ops);
CREATE INDEX idx_road_segment_name_sv      ON gis.road_segment USING GIN  (name_sv gin_trgm_ops);
CREATE INDEX idx_road_segment_municipality ON gis.road_segment (municipality_code);

-- Named places (Paikannimi features)
CREATE TABLE gis.named_place (
    id                 BIGINT                              PRIMARY KEY,
    name               VARCHAR(200)                        NOT NULL,
    language           VARCHAR(3)                          NOT NULL,
    place_class        INT                                 NOT NULL,
    kartanimi_id       BIGINT,
    municipality_code  VARCHAR(3),
    location           GEOMETRY(POINT, 4326)               NOT NULL,
    imported_at        TIMESTAMPTZ                         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_named_place_location     ON gis.named_place USING GIST (location);
CREATE INDEX idx_named_place_name         ON gis.named_place USING GIN  (name gin_trgm_ops);
CREATE INDEX idx_named_place_kartanimi_id ON gis.named_place (kartanimi_id);
CREATE INDEX idx_named_place_municipality ON gis.named_place (municipality_code);

-- Import audit log
CREATE TABLE gis.import_log (
    id             BIGSERIAL                              PRIMARY KEY,
    filename       VARCHAR(500)                           NOT NULL,
    feature_type   VARCHAR(50)                            NOT NULL,
    record_count   INT                                    NOT NULL,
    started_at     TIMESTAMPTZ                            NOT NULL,
    completed_at   TIMESTAMPTZ
);
