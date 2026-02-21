# GIS Server Implementation Plan

This document contains the implementation plan for the GIS Server. It is organized into phases with detailed tasks, dependencies, and success criteria.

## References

- [Technical Design: GIS Server REST API](../TechnicalDesigns/GIS-Server-REST-API.md)
- [Technical Design: GIS Data Import and Schema](../TechnicalDesigns/GIS-Data-Import-and-Schema.md)
- [C4: Containers](../C4/Containers.md)
- [NFR: Security](../NonFunctionalRequirements/Security.md)
- [NFR: Performance](../NonFunctionalRequirements/Performance.md)
- [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md)
- [NFR: Maintainability](../NonFunctionalRequirements/Maintainability.md)

---

## Plan Overview

| Phase | Description | Tasks | Status |
|-------|-------------|-------|--------|
| 0 | Prerequisites | 2 | Not Started |
| 1 | Foundation & Infrastructure | 5 | Not Started |
| 2 | Authentication & Security | 5 | Not Started |
| 3 | Model & Repository Layer | 5 | Not Started |
| 4 | WMTS Tile Service | 6 | Not Started |
| 5 | Geocoding Service | 8 | Not Started |
| 6 | Health & Error Handling | 3 | Not Started |
| 7 | Testing & Documentation | 4 | Not Started |
| **Total** | | **38** | |

---

## Phase 0: Prerequisites

Tasks that must be completed before GIS Server implementation begins.

### Task 0.1: Update C4 Containers Specification

**Status:** Not Started

**Description:**
Add HikariCP to the GIS Server tech stack in `Spec/C4/Containers.md`.

**Acceptance Criteria:**
- [ ] HikariCP added to GIS Server tech stack list
- [ ] Consistent with other server containers

**Dependencies:** None

---

### Task 0.2: Verify gis-database Module

**Status:** Not Started

**Description:**
Verify that the `shared/gis-database` module is complete with all necessary jOOQ generated classes and Flyway migrations.

**Acceptance Criteria:**
- [ ] Flyway migration `V1__create_gis_schema.sql` creates all required tables
- [ ] jOOQ generated classes exist for all GIS tables
- [ ] Module compiles successfully

**Dependencies:** None

---

## Phase 1: Foundation & Infrastructure

Core application setup including configuration, database connectivity, and Javalin server initialization.

### Task 1.1: Create Configuration System

**Status:** Not Started

**Description:**
Implement a configuration system that supports:
1. Environment variables
2. Properties file
3. File-based secrets (for container secrets)

Environment variables take precedence over properties file values.

The generic configuration loading infrastructure belongs in the shared module for reuse by CAD Server. GIS Server-specific configuration classes remain in the server module.

**Shared Module Package:** `net.pkhapps.idispatchx.common.config`

**Shared Module Files to Create:**
- `ConfigLoader.java` - Generic configuration loading logic (env vars, properties, secret files)
- `ConfigProperty.java` - Property definition with name, type, default, and secret file support
- `DatabaseConfig.java` - Common database connection settings (URL, user, password)
- `OidcConfig.java` - Common OIDC provider configuration (issuer, JWKS URL)

**GIS Server Package:** `net.pkhapps.idispatchx.gis.server.config`

**GIS Server Files to Create:**
- `GisServerConfig.java` - GIS-specific configuration (HTTP port, tile directory path)

**Configuration Parameters:**

| Parameter | Env Variable | Description |
|-----------|--------------|-------------|
| HTTP port | `GIS_SERVER_PORT` | Server port (default: 8080) |
| Tile directory | `GIS_TILE_DIR` | Base path for tile storage |
| DB URL | `GIS_DB_URL` | JDBC connection URL |
| DB username | `GIS_DB_USER` | Database username |
| DB password | `GIS_DB_PASSWORD` | Database password |
| DB password file | `GIS_DB_PASSWORD_FILE` | Path to file containing DB password |
| OIDC issuer | `GIS_OIDC_ISSUER` | OIDC provider issuer URL |
| OIDC JWKS URL | `GIS_OIDC_JWKS_URL` | JWKS endpoint (defaults to well-known) |

**Acceptance Criteria:**
- [ ] Configuration loads from environment variables
- [ ] Configuration loads from properties file when env vars not set
- [ ] File-based secrets are supported (read password from file)
- [ ] Missing required configuration throws clear error message
- [ ] Secrets are not logged
- [ ] Shared classes are in `java-common` module
- [ ] GIS-specific classes are in `gis-server` module
- [ ] Unit tests cover configuration loading logic

**Dependencies:** None

---

### Task 1.2: Set Up Database Connection with HikariCP

**Note:** The DataSourceProvider may also be a candidate for the shared module if CAD Server uses the same pattern. For now, implement in GIS Server; refactor to shared if needed when implementing CAD Server.

**Status:** Not Started

**Description:**
Implement database connection management using HikariCP connection pool.

**Package:** `net.pkhapps.idispatchx.gis.server.db`

**Files to Create:**
- `DataSourceProvider.java` - Creates and configures HikariDataSource
- `JooqContextProvider.java` - Creates jOOQ DSLContext from DataSource

**pom.xml Changes:**
- Add HikariCP dependency

**Acceptance Criteria:**
- [ ] HikariCP connection pool is configured with sensible defaults
- [ ] Pool size is configurable via environment variable
- [ ] Connection validation query uses PostGIS function
- [ ] jOOQ DSLContext is properly configured for PostgreSQL
- [ ] Connection pool shuts down cleanly on application stop
- [ ] Unit tests verify configuration

**Dependencies:** Task 1.1

---

### Task 1.3: Set Up Javalin Server

**Status:** Not Started

**Description:**
Initialize Javalin HTTP server with base configuration.

**Package:** `net.pkhapps.idispatchx.gis.server`

**Files to Create:**
- `Main.java` - Application entry point
- `GisServer.java` - Server initialization and lifecycle

**Acceptance Criteria:**
- [ ] Javalin server starts on configured port
- [ ] Server logs startup message with port number
- [ ] Graceful shutdown on SIGTERM
- [ ] Jackson configured for JSON serialization
- [ ] SLF4J/Logback logging configured
- [ ] Server can be started from command line

**Dependencies:** Task 1.1

---

### Task 1.4: Implement Flyway Migration on Startup

**Status:** Not Started

**Description:**
Run Flyway migrations on server startup to ensure database schema is current.

**Package:** `net.pkhapps.idispatchx.gis.server.db`

**Files to Create:**
- `FlywayMigrator.java` - Runs Flyway migrations

**Acceptance Criteria:**
- [ ] Flyway runs migrations from `gis-database` module on startup
- [ ] Migration failures prevent server startup
- [ ] Migration status is logged
- [ ] Existing data is preserved during migrations

**Dependencies:** Task 1.2

---

### Task 1.5: Create Logback Configuration

**Status:** Not Started

**Description:**
Configure Logback for structured logging.

**Files to Create:**
- `src/main/resources/logback.xml`

**Acceptance Criteria:**
- [ ] Console appender for local development
- [ ] JSON format option for production (configurable)
- [ ] Appropriate log levels for different packages
- [ ] No PII in log output (per Security NFR)
- [ ] Request logging for audit trail

**Dependencies:** None

---

## Phase 2: Authentication & Security

JWT validation, JWKS handling, role-based authorization, and back channel logout.

**Note:** Authentication infrastructure (JWKS client, token validation, session management) is shared between GIS Server and CAD Server. These components belong in the shared module. Server-specific Javalin handlers remain in each server module.

**Shared Module pom.xml Changes:**
- Add `nimbus-jose-jwt` dependency to `java-common`

### Task 2.1: Implement JWKS Client

**Status:** Not Started

**Description:**
Fetch and cache JWKS from the OIDC provider.

**Shared Module Package:** `net.pkhapps.idispatchx.common.auth`

**Shared Module Files to Create:**
- `JwksClient.java` - Fetches and caches JWKS

**Acceptance Criteria:**
- [ ] Fetches JWKS from configured URL on startup
- [ ] Caches JWKS with configurable TTL
- [ ] Refreshes JWKS when key not found (key rotation support)
- [ ] Handles network errors gracefully
- [ ] Uses Nimbus JOSE + JWT library
- [ ] Shared module classes are in `java-common`
- [ ] Unit tests with mocked HTTP responses

**Dependencies:** Task 1.1 (for OidcConfig)

---

### Task 2.2: Implement Token Validator

**Status:** Not Started

**Description:**
Validate JWT tokens against JWKS.

**Shared Module Package:** `net.pkhapps.idispatchx.common.auth`

**Shared Module Files to Create:**
- `TokenValidator.java` - JWT signature and claims validation
- `TokenClaims.java` - Parsed token claims record

**Validation Steps:**
1. Verify JWT signature against JWKS
2. Check token expiration (`exp` claim)
3. Verify issuer (`iss` claim) matches configured OIDC provider
4. Extract subject (`sub`) and roles claims
5. Validate required role is present

**Acceptance Criteria:**
- [ ] Validates JWT signature using JWKS
- [ ] Rejects expired tokens
- [ ] Rejects tokens from wrong issuer
- [ ] Extracts user identifier and roles
- [ ] Returns clear validation error messages
- [ ] Unit tests cover all validation scenarios

**Dependencies:** Task 2.1

---

### Task 2.3: Implement JWT Authentication Handler

**Status:** Not Started

**Description:**
Javalin before-handler that validates JWT on protected endpoints. Uses the shared TokenValidator.

**GIS Server Package:** `net.pkhapps.idispatchx.gis.server.auth`

**GIS Server Files to Create:**
- `JwtAuthHandler.java` - Javalin before-handler (uses shared TokenValidator)

**Acceptance Criteria:**
- [ ] Extracts Bearer token from Authorization header
- [ ] Returns 401 if Authorization header missing
- [ ] Returns 401 if token is invalid or expired
- [ ] Stores validated claims in Javalin context for downstream handlers
- [ ] Does not apply to health check endpoint
- [ ] Unit tests verify authentication logic

**Dependencies:** Task 2.2, Task 1.3

---

### Task 2.4: Implement Role Authorization

**Status:** Not Started

**Description:**
Verify user has required role (`Dispatcher` or `Observer`) for GIS Server access.

**Shared Module Package:** `net.pkhapps.idispatchx.common.auth`

**Shared Module Files to Create:**
- `Role.java` - Enum of system roles (Dispatcher, Observer, Admin, Station, Unit)

**GIS Server Package:** `net.pkhapps.idispatchx.gis.server.auth`

**GIS Server Files to Create:**
- `RoleAuthHandler.java` - Javalin handler that checks role claims

**Acceptance Criteria:**
- [ ] Allows access if user has `Dispatcher` role
- [ ] Allows access if user has `Observer` role
- [ ] Returns 403 Forbidden if user lacks required role
- [ ] Error response follows standard format
- [ ] Unit tests cover role validation

**Dependencies:** Task 2.3

---

### Task 2.5: Implement Back Channel Logout Handler

**Status:** Not Started

**Description:**
Handle OIDC back channel logout events to invalidate sessions.

**Shared Module Package:** `net.pkhapps.idispatchx.common.auth`

**Shared Module Files to Create:**
- `SessionStore.java` - In-memory session tracking (thread-safe)
- `LogoutTokenValidator.java` - Validates OIDC logout tokens

**GIS Server Package:** `net.pkhapps.idispatchx.gis.server.auth`

**GIS Server Files to Create:**
- `BackChannelLogoutHandler.java` - Javalin endpoint handler for logout events

**Acceptance Criteria:**
- [ ] Receives logout token from OIDC provider
- [ ] Validates logout token signature
- [ ] Extracts `sid` (session ID) claim
- [ ] Invalidates session in SessionStore
- [ ] Subsequent requests with invalidated session return 401
- [ ] Unit tests verify logout flow

**Dependencies:** Task 2.2

---

## Phase 3: Model & Repository Layer

Domain models for geocoding results and repository implementations using jOOQ.

### Task 3.1: Create API Model Classes

**Status:** Not Started

**Description:**
Create DTOs for geocoding API responses.

**Package:** `net.pkhapps.idispatchx.gis.server.api.geocode`

**Files to Create:**
- `SearchRequest.java` - Request DTO with validation
- `SearchResponse.java` - Response DTO
- `LocationResult.java` - Individual result DTO (sealed interface with variants)
- `AddressResult.java` - Address result record
- `PlaceResult.java` - Named place result record
- `IntersectionResult.java` - Road intersection result record

**Acceptance Criteria:**
- [ ] Self-validating request DTO (query length, limit range, municipality code format)
- [ ] Response DTO includes all fields from technical design
- [ ] LocationResult is a sealed interface with three implementations
- [ ] All fields use shared domain types (MultilingualName, MunicipalityCode, Coordinates.Epsg4326)
- [ ] Jackson serialization produces expected JSON format
- [ ] Unit tests verify validation and serialization

**Dependencies:** None

---

### Task 3.2: Create Tile Model Classes

**Status:** Not Started

**Description:**
Create model classes for WMTS tile handling.

**Package:** `net.pkhapps.idispatchx.gis.server.model`

**Files to Create:**
- `TileCoordinates.java` - Zoom/row/col value object with validation
- `TileLayer.java` - Layer metadata (name, available zoom levels)

**Acceptance Criteria:**
- [ ] TileCoordinates validates zoom level (0-15) and non-negative row/col
- [ ] TileCoordinates validates coordinates are within matrix bounds for zoom level
- [ ] TileLayer tracks which zoom levels have pre-rendered tiles
- [ ] Unit tests cover validation

**Dependencies:** None

---

### Task 3.3: Implement AddressPointRepository

**Status:** Not Started

**Description:**
Repository for querying address points using jOOQ.

**Package:** `net.pkhapps.idispatchx.gis.server.repository`

**Files to Create:**
- `AddressPointRepository.java`

**Query Pattern:**
```sql
SELECT ... FROM gis.address_point ap
LEFT JOIN gis.municipality m ON ...
WHERE ap.name_fi % :query OR ap.name_sv % :query
ORDER BY similarity(...) DESC
LIMIT :limit
```

**Acceptance Criteria:**
- [ ] Uses pg_trgm fuzzy matching (`%` operator)
- [ ] Ranks results by similarity score
- [ ] Joins municipality for name lookup
- [ ] Extracts coordinates using ST_X/ST_Y
- [ ] Optional municipality code filter
- [ ] Integration tests with Testcontainers verify queries

**Dependencies:** Task 1.2

---

### Task 3.4: Implement RoadSegmentRepository

**Status:** Not Started

**Description:**
Repository for querying road segments and computing interpolated addresses.

**Package:** `net.pkhapps.idispatchx.gis.server.repository`

**Files to Create:**
- `RoadSegmentRepository.java`

**Query Patterns:**
1. Find road segments by name with address range filtering
2. Interpolate position along segment geometry using ST_LineInterpolatePoint

**Acceptance Criteria:**
- [ ] Finds segments by fuzzy name matching
- [ ] Filters by address range containing requested number
- [ ] Handles odd/even address parity for left/right side selection
- [ ] Interpolates coordinates along segment geometry
- [ ] Finds road intersections using ST_Intersects
- [ ] Integration tests verify queries and interpolation

**Dependencies:** Task 1.2

---

### Task 3.5: Implement NamedPlaceRepository

**Status:** Not Started

**Description:**
Repository for querying named places.

**Package:** `net.pkhapps.idispatchx.gis.server.repository`

**Files to Create:**
- `NamedPlaceRepository.java`
- `MunicipalityRepository.java` - For municipality lookups

**Query Pattern:**
- Fuzzy search on name column
- Group by karttanimi_id to merge multilingual entries
- Join municipality for name lookup

**Acceptance Criteria:**
- [ ] Uses pg_trgm fuzzy matching
- [ ] Groups results by karttanimi_id for multilingual merging
- [ ] Returns all language versions for each place
- [ ] Ranks results by similarity
- [ ] Integration tests verify queries

**Dependencies:** Task 1.2

---

## Phase 4: WMTS Tile Service

WMTS REST endpoint for serving map tiles.

### Task 4.1: Implement Layer Discovery

**Status:** Not Started

**Description:**
Scan tile directory to discover available layers and their zoom levels.

**Package:** `net.pkhapps.idispatchx.gis.server.service.tile`

**Files to Create:**
- `LayerDiscovery.java` - Filesystem scanning

**Acceptance Criteria:**
- [ ] Scans `{base-dir}/` for layer subdirectories
- [ ] For each layer, scans `{layer}/ETRS-TM35FIN/` for zoom level directories
- [ ] Returns list of TileLayer objects with available zoom levels
- [ ] Logs discovered layers at startup
- [ ] Unit tests with temp directory structure

**Dependencies:** Task 1.1

---

### Task 4.2: Implement Tile Service

**Status:** Not Started

**Description:**
Core service for retrieving tiles from filesystem.

**Package:** `net.pkhapps.idispatchx.gis.server.service.tile`

**Files to Create:**
- `TileService.java` - Tile retrieval orchestration

**Acceptance Criteria:**
- [ ] Constructs file path from layer/zoom/row/col
- [ ] Returns tile bytes if file exists
- [ ] Returns empty Optional if file does not exist
- [ ] Validates tile coordinates before lookup
- [ ] Returns appropriate result for runtime resampling delegation
- [ ] Unit tests verify path construction and file reading

**Dependencies:** Task 4.1, Task 3.2

---

### Task 4.3: Implement Tile Resampler

**Status:** Not Started

**Description:**
Resample tiles at runtime for zoom levels without pre-rendered tiles.

**Package:** `net.pkhapps.idispatchx.gis.server.service.tile`

**Files to Create:**
- `TileResampler.java` - Runtime tile resampling using bilinear interpolation

**Algorithm:**
1. Find nearest coarser zoom level with pre-rendered tiles (within 3 levels)
2. Calculate which source tile contains the requested area
3. Extract relevant quadrant/region from source tile
4. Scale up to 256x256 using bilinear interpolation

**Acceptance Criteria:**
- [ ] Upsamples from coarser levels (not downsamples from finer)
- [ ] Uses java.awt.image.BufferedImage and Graphics2D
- [ ] Applies bilinear interpolation (RenderingHints.VALUE_INTERPOLATION_BILINEAR)
- [ ] Maximum resampling depth of 3 levels
- [ ] Returns empty if no source data within 3 levels
- [ ] Unit tests verify resampling produces correct dimensions

**Dependencies:** Task 4.1

---

### Task 4.4: Implement Tile Cache

**Status:** Not Started

**Description:**
LRU cache for resampled tiles.

**Package:** `net.pkhapps.idispatchx.gis.server.service.tile`

**Files to Create:**
- `TileCache.java` - LRU cache for resampled tiles

**Acceptance Criteria:**
- [ ] Cache key is (layer, zoom, row, col) tuple
- [ ] Configurable maximum size (default 1000 tiles)
- [ ] LRU eviction when cache is full
- [ ] Thread-safe for concurrent access
- [ ] Provides cache statistics (hits, misses)
- [ ] Unit tests verify eviction behavior

**Dependencies:** None

---

### Task 4.5: Implement GetCapabilities Generator

**Status:** Not Started

**Description:**
Generate WMTS Capabilities XML document.

**Package:** `net.pkhapps.idispatchx.gis.server.api.wmts`

**Files to Create:**
- `CapabilitiesGenerator.java` - Generates WMTS Capabilities XML

**Output:**
- OGC WMTS 1.0.0 compliant XML
- Lists all discovered layers
- Defines ETRS-TM35FIN tile matrix set with all 16 zoom levels
- Includes ResourceURL templates

**Acceptance Criteria:**
- [ ] Generates valid OGC WMTS 1.0.0 Capabilities XML
- [ ] Includes all discovered layers from LayerDiscovery
- [ ] Defines complete ETRS-TM35FIN tile matrix set (levels 0-15)
- [ ] ResourceURL template matches actual endpoint pattern
- [ ] Unit tests verify XML structure

**Dependencies:** Task 4.1

---

### Task 4.6: Implement WMTS Controller

**Status:** Not Started

**Description:**
Javalin endpoints for WMTS operations.

**Package:** `net.pkhapps.idispatchx.gis.server.api.wmts`

**Files to Create:**
- `WmtsController.java` - Endpoint handlers

**Endpoints:**
- `GET /wmts/1.0.0/WMTSCapabilities.xml` - GetCapabilities
- `GET /wmts/{layer}/ETRS-TM35FIN/{zoom}/{row}/{col}.png` - GetTile

**Acceptance Criteria:**
- [ ] GetCapabilities returns XML with Content-Type application/xml
- [ ] GetTile returns PNG with Content-Type image/png
- [ ] Returns 204 No Content for missing tiles
- [ ] Returns 404 Not Found for unknown layers
- [ ] Returns 400 Bad Request for invalid zoom/row/col
- [ ] Sets Cache-Control headers (24h for pre-rendered, 1h for resampled)
- [ ] Supports conditional requests (ETag, If-None-Match)
- [ ] Metrics/logging for tile requests
- [ ] Integration tests verify endpoints

**Dependencies:** Task 4.2, Task 4.3, Task 4.4, Task 4.5, Task 2.4

---

## Phase 5: Geocoding Service

Geocoding REST endpoint for address, place, and intersection lookups.

### Task 5.1: Implement Query Parser

**Status:** Not Started

**Description:**
Parse search queries to detect patterns (address with number, intersection, place name).

**Package:** `net.pkhapps.idispatchx.gis.server.service.geocode`

**Files to Create:**
- `QueryParser.java` - Pattern detection
- `ParsedQuery.java` - Sealed interface with query type variants

**Patterns to Detect:**
| Pattern | Example | Result Type |
|---------|---------|-------------|
| Name + number | "Mannerheimintie 5" | AddressQuery |
| Name only | "Mannerheimintie" | StreetQuery |
| Two names with separator | "Kaivokatu / Keskuskatu" | IntersectionQuery |
| Single word | "Kallio" | PlaceQuery |

**Separators:** `/`, `&`, `and`, `ja`, `och`

**Acceptance Criteria:**
- [ ] Detects address pattern (name + number)
- [ ] Detects intersection pattern (name + separator + name)
- [ ] Defaults to street/place search for other patterns
- [ ] Handles leading/trailing whitespace
- [ ] Unit tests cover all pattern types

**Dependencies:** None

---

### Task 5.2: Implement Address Point Searcher

**Status:** Not Started

**Description:**
Search address points for matching addresses.

**Package:** `net.pkhapps.idispatchx.gis.server.service.geocode`

**Files to Create:**
- `AddressPointSearcher.java`

**Acceptance Criteria:**
- [ ] Uses AddressPointRepository for queries
- [ ] Converts results to AddressResult DTOs
- [ ] Includes source="address_point" indicator
- [ ] Filters by municipality code if provided
- [ ] Returns results ranked by similarity
- [ ] Unit tests verify search logic

**Dependencies:** Task 3.3, Task 3.1

---

### Task 5.3: Implement Road Segment Searcher

**Status:** Not Started

**Description:**
Search road segments and interpolate addresses.

**Package:** `net.pkhapps.idispatchx.gis.server.service.geocode`

**Files to Create:**
- `RoadSegmentSearcher.java`

**Acceptance Criteria:**
- [ ] Uses RoadSegmentRepository for queries
- [ ] Interpolates coordinates for specific address numbers
- [ ] Handles odd/even parity for left/right side selection
- [ ] Includes source="road_segment" indicator
- [ ] Returns results ranked by similarity
- [ ] Unit tests verify interpolation logic

**Dependencies:** Task 3.4, Task 3.1

---

### Task 5.4: Implement Named Place Searcher

**Status:** Not Started

**Description:**
Search named places.

**Package:** `net.pkhapps.idispatchx.gis.server.service.geocode`

**Files to Create:**
- `NamedPlaceSearcher.java`

**Acceptance Criteria:**
- [ ] Uses NamedPlaceRepository for queries
- [ ] Merges multilingual entries by karttanimi_id
- [ ] Converts results to PlaceResult DTOs
- [ ] Includes place class code
- [ ] Returns results ranked by similarity
- [ ] Unit tests verify search and merging logic

**Dependencies:** Task 3.5, Task 3.1

---

### Task 5.5: Implement Intersection Searcher

**Status:** Not Started

**Description:**
Search for road intersections.

**Package:** `net.pkhapps.idispatchx.gis.server.service.geocode`

**Files to Create:**
- `IntersectionSearcher.java`

**Acceptance Criteria:**
- [ ] Uses RoadSegmentRepository intersection query
- [ ] Computes intersection point using ST_Intersection/ST_Centroid
- [ ] Converts results to IntersectionResult DTOs
- [ ] Returns both road names in multilingual format
- [ ] Unit tests verify intersection detection

**Dependencies:** Task 3.4, Task 3.1

---

### Task 5.6: Implement Result Merger

**Status:** Not Started

**Description:**
Combine and rank results from multiple searchers.

**Package:** `net.pkhapps.idispatchx.gis.server.service.geocode`

**Files to Create:**
- `ResultMerger.java`

**Ranking Rules:**
1. Higher similarity scores rank first
2. Address points rank before road segment interpolations for same address
3. Results are deduplicated based on coordinates and name

**Acceptance Criteria:**
- [ ] Merges results from multiple sources
- [ ] Ranks by similarity score
- [ ] Prioritizes address_point over road_segment for same address
- [ ] Deduplicates based on location proximity
- [ ] Limits final result count
- [ ] Unit tests verify ranking and deduplication

**Dependencies:** Task 3.1

---

### Task 5.7: Implement Geocode Service

**Status:** Not Started

**Description:**
Orchestrate geocoding searches based on query type.

**Package:** `net.pkhapps.idispatchx.gis.server.service.geocode`

**Files to Create:**
- `GeocodeService.java` - Orchestration service

**Flow:**
1. Parse query using QueryParser
2. Based on query type, invoke appropriate searchers in parallel
3. Merge and rank results using ResultMerger
4. Return combined results

**Acceptance Criteria:**
- [ ] Routes queries to appropriate searchers based on parsed type
- [ ] Executes independent searches in parallel (CompletableFuture)
- [ ] Merges results from all sources
- [ ] Handles database errors gracefully
- [ ] Logs search queries for audit trail (no PII)
- [ ] Unit tests verify orchestration logic

**Dependencies:** Task 5.1, Task 5.2, Task 5.3, Task 5.4, Task 5.5, Task 5.6

---

### Task 5.8: Implement Geocode Controller

**Status:** Not Started

**Description:**
Javalin endpoint for geocoding.

**Package:** `net.pkhapps.idispatchx.gis.server.api.geocode`

**Files to Create:**
- `GeocodeController.java` - Endpoint handler

**Endpoint:**
- `GET /api/v1/geocode/search?q=...&limit=...&municipality=...`

**Acceptance Criteria:**
- [ ] Validates query parameter (min 3 chars, max 200 chars)
- [ ] Validates limit parameter (1-50, default 20)
- [ ] Validates municipality code format (3 digits)
- [ ] Returns 400 for validation errors
- [ ] Returns 200 with results array (may be empty)
- [ ] Returns 503 for database errors
- [ ] Content-Type is application/json
- [ ] Integration tests verify endpoint

**Dependencies:** Task 5.7, Task 2.4

---

## Phase 6: Health & Error Handling

Health check endpoint and centralized error handling.

### Task 6.1: Implement Health Controller

**Status:** Not Started

**Description:**
Health check endpoint for infrastructure monitoring. This is an internal endpoint not exposed through the public reverse proxy (per Security NFR).

**Package:** `net.pkhapps.idispatchx.gis.server.api.health`

**Files to Create:**
- `HealthController.java`

**Endpoint:**
- `GET /health` (no authentication required - internal only, not exposed via reverse proxy)

**Health Checks:**
1. Database connectivity (execute simple query)
2. Tile directory accessibility (check exists and readable)
3. Discovered layers list

**Acceptance Criteria:**
- [ ] Returns 200 OK with status "UP" when all components healthy
- [ ] Returns 503 Service Unavailable with status "DOWN" when any component unhealthy
- [ ] Includes component-level status in response
- [ ] Does not require authentication
- [ ] Unit tests verify health check logic

**Dependencies:** Task 1.2, Task 4.1

---

### Task 6.2: Implement Error Response Format

**Status:** Not Started

**Description:**
Standardized error response DTOs. Common error codes are shared; server-specific codes remain in each server module.

**Shared Module Package:** `net.pkhapps.idispatchx.common.api`

**Shared Module Files to Create:**
- `ErrorResponse.java` - Error response DTO (code, message, details, timestamp, path)
- `CommonErrorCode.java` - Shared error codes (UNAUTHORIZED, FORBIDDEN, DATABASE_ERROR, INTERNAL_ERROR)

**GIS Server Package:** `net.pkhapps.idispatchx.gis.server.api.error`

**GIS Server Files to Create:**
- `GisErrorCode.java` - GIS-specific error codes

**GIS-Specific Error Codes:**
- INVALID_QUERY - Query validation failed
- INVALID_PARAMETER - Parameter validation failed
- INVALID_TILE_COORDINATES - Tile coordinates out of bounds
- LAYER_NOT_FOUND - Tile layer does not exist

**Acceptance Criteria:**
- [ ] All error codes from technical design are implemented
- [ ] Error response includes timestamp and request path
- [ ] Optional details field for validation errors
- [ ] Jackson serialization produces expected format
- [ ] Unit tests verify serialization

**Dependencies:** None

---

### Task 6.3: Implement Global Exception Handler

**Status:** Not Started

**Description:**
Centralized exception handling for all endpoints. Uses shared ErrorResponse format.

**Shared Module Package:** `net.pkhapps.idispatchx.common.api`

**Shared Module Files to Create:**
- `ValidationException.java` - Custom exception for validation errors

**GIS Server Package:** `net.pkhapps.idispatchx.gis.server.api.error`

**GIS Server Files to Create:**
- `GlobalExceptionHandler.java` - Javalin exception handler

**Acceptance Criteria:**
- [ ] Catches ValidationException and returns 400
- [ ] Catches authentication exceptions and returns 401
- [ ] Catches authorization exceptions and returns 403
- [ ] Catches database exceptions and returns 503
- [ ] Catches unexpected exceptions and returns 500
- [ ] Logs exceptions with appropriate levels (no PII)
- [ ] All responses use ErrorResponse format
- [ ] Unit tests verify exception mapping

**Dependencies:** Task 6.2

---

## Phase 7: Testing & Documentation

Comprehensive testing and API documentation.

### Task 7.1: Set Up Testcontainers Infrastructure

**Status:** Not Started

**Description:**
Configure Testcontainers for PostgreSQL/PostGIS integration tests.

**Files to Create:**
- `src/test/java/.../IntegrationTestBase.java` - Base class with container setup
- Test resource files for sample data

**Acceptance Criteria:**
- [ ] PostgreSQL container with PostGIS extension starts for tests
- [ ] Flyway migrations run against test container
- [ ] Sample test data can be loaded
- [ ] Container is reused across test classes for speed
- [ ] Tests work in CI environment

**Dependencies:** Task 1.2

---

### Task 7.2: Write Repository Integration Tests

**Status:** Not Started

**Description:**
Integration tests for all repository classes.

**Files to Create:**
- `AddressPointRepositoryTest.java`
- `RoadSegmentRepositoryTest.java`
- `NamedPlaceRepositoryTest.java`
- `MunicipalityRepositoryTest.java`

**Test Scenarios:**
- Fuzzy search returns expected results
- Similarity ranking works correctly
- Municipality filtering works
- Address interpolation produces valid coordinates
- Road intersection detection works
- Empty results handled correctly

**Acceptance Criteria:**
- [ ] Each repository has integration tests
- [ ] Tests use Testcontainers
- [ ] Tests verify actual SQL queries work
- [ ] Tests include edge cases (no results, many results)

**Dependencies:** Task 7.1, Task 3.3, Task 3.4, Task 3.5

---

### Task 7.3: Write API Integration Tests

**Status:** Not Started

**Description:**
End-to-end integration tests for all API endpoints.

**Files to Create:**
- `WmtsControllerTest.java`
- `GeocodeControllerTest.java`
- `HealthControllerTest.java`
- `AuthenticationTest.java`

**Test Scenarios:**
- WMTS GetCapabilities returns valid XML
- WMTS GetTile returns tiles / 204 / 404 appropriately
- Geocoding search returns results
- Authentication rejects invalid tokens
- Authorization rejects wrong roles
- Error responses have correct format

**Acceptance Criteria:**
- [ ] Each endpoint has integration tests
- [ ] Tests use Testcontainers and real HTTP requests
- [ ] Tests verify response status codes and content
- [ ] Tests verify authentication and authorization
- [ ] Tests include error scenarios

**Dependencies:** Task 7.1, Task 4.6, Task 5.8, Task 6.1

---

### Task 7.4: Create OpenAPI Specification

**Status:** Not Started

**Description:**
Create OpenAPI 3.0 specification for the geocoding API. The specification is maintained as a static file in the repository for documentation purposes. It is not served at runtime since the only client (Dispatcher Client) is in the same repository.

**Files to Create:**
- `src/main/resources/openapi/gis-api-v1.yaml` - OpenAPI specification

**Acceptance Criteria:**
- [ ] OpenAPI 3.0 specification covers geocoding endpoint
- [ ] All request parameters documented
- [ ] All response schemas documented
- [ ] Error responses documented
- [ ] Specification validates against OpenAPI 3.0 schema
- [ ] Specification is accessible in the repository for reference

**Dependencies:** Task 5.8

---

## Task Status Legend

| Status | Meaning |
|--------|---------|
| Not Started | Work has not begun |
| In Progress | Currently being implemented |
| Blocked | Waiting on dependency or decision |
| Review | Code complete, awaiting review |
| Done | Implemented and verified |

---

## Execution Notes

### Recommended Execution Order

1. Complete Phase 0 (prerequisites)
2. Complete Phase 1 (foundation) - required by all other phases
3. Complete Phase 2 (authentication) - required for protected endpoints
4. Phases 3-5 can proceed in parallel after Phase 2
5. Phase 6 (error handling) should be completed early to establish patterns
6. Phase 7 (testing) tasks should be done alongside feature implementation

### Parallel Execution Opportunities

Within phases, some tasks can be parallelized:

**Phase 3:** Tasks 3.1 and 3.2 (models) can run in parallel
**Phase 4:** Tasks 4.1-4.4 can run mostly in parallel, then 4.5-4.6
**Phase 5:** Tasks 5.1-5.5 can run in parallel, then 5.6-5.8

### Verification Checkpoints

After each phase, verify:
1. All unit tests pass
2. Code compiles without warnings
3. No security vulnerabilities introduced
4. Documentation updated as needed
