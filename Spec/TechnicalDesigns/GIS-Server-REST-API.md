# Technical Design: GIS Server REST API

## Overview

This document describes the REST API design for the GIS Server. The GIS Server provides two primary services to the Dispatcher Client:

1. **WMTS REST endpoint** for serving pre-rendered raster map tiles
2. **Geocoding REST endpoint** for address, place name, and road intersection lookups

The API follows RESTful conventions with JSON payloads and JWT bearer authentication.

## References

- [C4: Containers](../C4/Containers.md) - GIS Server container definition, tech stack, API endpoints
- [Technical Design: GIS Data Import and Schema](GIS-Data-Import-and-Schema.md) - Database schema, geocoding query patterns
- [UC: Lookup Address](../UseCases/Dispatcher/UC-Lookup-Address.md) - Primary geocoding use case
- [Domain: Location](../Domain/Location.md) - ExactAddress, RoadIntersection, NamedPlace variants
- [Domain: Municipality](../Domain/Municipality.md) - Municipality code and multilingual name
- [Domain: MultilingualName](../Domain/MultilingualName.md) - Language-keyed name values
- [NFR: Security](../NonFunctionalRequirements/Security.md) - JWT bearer authentication, role requirements
- [NFR: Performance](../NonFunctionalRequirements/Performance.md) - Response time targets
- [NFR: Internationalization](../NonFunctionalRequirements/Internationalization.md) - Coordinate systems, language support
- JHS 180: ETRS-TM35FIN tile matrix set standard for Finnish WMTS services

---

## 1. Authentication

### 1.1 JWT Bearer Authentication

All GIS Server endpoints require JWT bearer authentication per the Security NFR.

**Request header:**

```
Authorization: Bearer <jwt-token>
```

The JWT is obtained from the OIDC Provider and contains:

- `sub`: User identifier
- `roles`: Array of role claims
- `exp`: Token expiration timestamp

### 1.2 Role Authorization

Per the Security NFR, users must have one of the following roles to access GIS Server:

| Role | Access |
|------|--------|
| `Dispatcher` | Full access to tiles and geocoding |
| `Observer` | Full access to tiles and geocoding (read-only system) |

The GIS Server rejects requests from users without these roles with HTTP 403 Forbidden.

### 1.3 Token Validation

The GIS Server validates JWTs by:

1. Fetching the OIDC provider's JWKS (JSON Web Key Set) at startup and caching it
2. Verifying the JWT signature against the JWKS
3. Checking token expiration (`exp` claim)
4. Verifying the issuer (`iss` claim) matches the configured OIDC provider
5. Extracting and validating the required role claim

### 1.4 Back Channel Logout

Per the C4 specification, the GIS Server supports OIDC back channel logout. When a logout event is received:

1. The session associated with the `sid` claim is invalidated
2. Subsequent requests with tokens from that session are rejected with HTTP 401 Unauthorized

---

## 2. WMTS REST Endpoint

The GIS Server provides a RESTful WMTS endpoint following the OGC WMTS 1.0.0 RESTful pattern. This serves pre-rendered map tiles from the filesystem.

### 2.1 GetCapabilities

Returns WMTS service metadata including available layers, tile matrix sets, and supported formats.

**Request:**

```
GET /wmts/1.0.0/WMTSCapabilities.xml
Authorization: Bearer <jwt-token>
```

**Response:**

- Content-Type: `application/xml`
- HTTP 200 OK with WMTS Capabilities XML document

**Capabilities Document Structure:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Capabilities xmlns="http://www.opengis.net/wmts/1.0"
              xmlns:ows="http://www.opengis.net/ows/1.1"
              version="1.0.0">
  <ows:ServiceIdentification>
    <ows:Title>iDispatchX GIS Server</ows:Title>
    <ows:ServiceType>OGC WMTS</ows:ServiceType>
    <ows:ServiceTypeVersion>1.0.0</ows:ServiceTypeVersion>
  </ows:ServiceIdentification>
  <Contents>
    <Layer>
      <ows:Title>Terrain</ows:Title>
      <ows:Identifier>terrain</ows:Identifier>
      <Style isDefault="true">
        <ows:Identifier>default</ows:Identifier>
      </Style>
      <Format>image/png</Format>
      <TileMatrixSetLink>
        <TileMatrixSet>ETRS-TM35FIN</TileMatrixSet>
      </TileMatrixSetLink>
      <ResourceURL format="image/png" resourceType="tile"
        template="/wmts/{Layer}/ETRS-TM35FIN/{TileMatrix}/{TileRow}/{TileCol}.png"/>
    </Layer>
    <!-- Additional layers discovered at startup -->
  </Contents>
  <TileMatrixSet>
    <ows:Identifier>ETRS-TM35FIN</ows:Identifier>
    <ows:SupportedCRS>urn:ogc:def:crs:EPSG::3067</ows:SupportedCRS>
    <TileMatrix>
      <ows:Identifier>0</ows:Identifier>
      <ScaleDenominator>29257143</ScaleDenominator>
      <TopLeftCorner>-548576.0 8388608.0</TopLeftCorner>
      <TileWidth>256</TileWidth>
      <TileHeight>256</TileHeight>
      <MatrixWidth>1</MatrixWidth>
      <MatrixHeight>1</MatrixHeight>
    </TileMatrix>
    <!-- TileMatrix entries for levels 0-15 -->
  </TileMatrixSet>
</Capabilities>
```

### 2.2 GetTile

Retrieves a single map tile.

**Request:**

```
GET /wmts/{layer}/ETRS-TM35FIN/{zoom}/{row}/{col}.png
Authorization: Bearer <jwt-token>
```

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `layer` | string | Layer identifier (e.g., `terrain`, `buildings`) |
| `zoom` | integer | Zoom level (0-15) |
| `row` | integer | Tile row index |
| `col` | integer | Tile column index |

**Responses:**

| Status | Condition | Body |
|--------|-----------|------|
| 200 OK | Tile exists | PNG image (256x256 pixels) |
| 204 No Content | Tile does not exist (empty area) | Empty |
| 400 Bad Request | Invalid zoom level or coordinates | JSON error |
| 401 Unauthorized | Invalid or missing JWT | JSON error |
| 403 Forbidden | User lacks required role | JSON error |
| 404 Not Found | Unknown layer | JSON error |

**Response Headers (200 OK):**

```
Content-Type: image/png
Cache-Control: public, max-age=86400
ETag: "<hash-of-tile>"
```

**Caching:**

- Tiles are immutable once imported; aggressive caching is safe
- `Cache-Control: public, max-age=86400` (24 hours)
- ETag based on file content hash for conditional requests
- 304 Not Modified returned for matching `If-None-Match` requests

### 2.3 Runtime Resampling

When a tile is requested at a zoom level without pre-rendered tiles, the GIS Server resamples from the nearest available coarser level (see GIS-Data-Import-and-Schema.md section 13).

Resampled tiles:

- Are served with the same Content-Type and format as pre-rendered tiles
- Are cached in memory (LRU cache) to avoid repeated resampling
- Use `Cache-Control: public, max-age=3600` (1 hour) to allow re-generation if source tiles change

---

## 3. Geocoding REST Endpoint

The geocoding endpoint provides address, place name, and road intersection lookups.

### 3.1 Search Endpoint

Performs a geocoding search against the GIS database.

**Request:**

```
GET /api/v1/geocode/search
Authorization: Bearer <jwt-token>
```

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `q` | string | Yes | Search query (minimum 3 characters) |
| `limit` | integer | No | Maximum results to return (1-50). Default: 20 |
| `municipality` | string | No | Filter by municipality code (3-digit) |

**Response (200 OK):**

```json
{
  "results": [
    {
      "type": "address",
      "name": {
        "fi": "Mannerheimintie",
        "sv": "Mannerheimv\u00e4gen"
      },
      "number": "1",
      "municipality": {
        "code": "091",
        "name": {
          "fi": "Helsinki",
          "sv": "Helsingfors"
        }
      },
      "coordinates": {
        "latitude": 60.169857,
        "longitude": 24.938379
      },
      "source": "address_point"
    },
    {
      "type": "address",
      "name": {
        "fi": "Mannerheimintie",
        "sv": "Mannerheimv\u00e4gen"
      },
      "number": "5",
      "municipality": {
        "code": "091",
        "name": {
          "fi": "Helsinki",
          "sv": "Helsingfors"
        }
      },
      "coordinates": {
        "latitude": 60.170123,
        "longitude": 24.939012
      },
      "source": "road_segment"
    },
    {
      "type": "place",
      "name": {
        "fi": "Mannerheiminaukio",
        "sv": "Mannerheimplatsen"
      },
      "placeClass": 48111,
      "municipality": {
        "code": "091",
        "name": {
          "fi": "Helsinki",
          "sv": "Helsingfors"
        }
      },
      "coordinates": {
        "latitude": 60.170500,
        "longitude": 24.939500
      }
    },
    {
      "type": "intersection",
      "roadA": {
        "fi": "Mannerheimintie",
        "sv": "Mannerheimv\u00e4gen"
      },
      "roadB": {
        "fi": "Kaivokatu",
        "sv": "Brunnsgatan"
      },
      "municipality": {
        "code": "091",
        "name": {
          "fi": "Helsinki",
          "sv": "Helsingfors"
        }
      },
      "coordinates": {
        "latitude": 60.170800,
        "longitude": 24.940200
      }
    }
  ],
  "query": "Mannerheimintie 1",
  "resultCount": 4
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `results` | array | Array of location results |
| `results[].type` | string | Location type: `address`, `place`, or `intersection` |
| `results[].name` | object | Multilingual name containing all available language versions (language code to value) |
| `results[].number` | string | Address number (only for type `address`) |
| `results[].placeClass` | integer | NLS place class code (only for type `place`) |
| `results[].roadA` | object | First road multilingual name (only for type `intersection`) |
| `results[].roadB` | object | Second road multilingual name (only for type `intersection`) |
| `results[].municipality` | object | Municipality with `code` and multilingual `name` |
| `results[].coordinates` | object | Location with `latitude` and `longitude` in EPSG:4326 |
| `results[].source` | string | Data source: `address_point` or `road_segment` (only for type `address`) |
| `query` | string | Original search query |
| `resultCount` | integer | Number of results returned |

**Multilingual Names:**

All name fields include every available language version. The caller (person reporting an emergency) may speak a different language than the dispatcher, so all translations are returned for display flexibility. Language codes follow ISO 639: `fi` (Finnish), `sv` (Swedish), `en` (English), `sme` (Northern Sami), `smn` (Inari Sami), `sms` (Skolt Sami).

**Result Ordering:**

Results are ordered by relevance using pg_trgm similarity scoring:

1. Higher similarity scores rank first
2. Within equal similarity, exact matches rank before partial matches
3. Address points rank before road segment interpolations for the same address

**Error Responses:**

| Status | Condition | Body |
|--------|-----------|------|
| 400 Bad Request | Query too short (< 3 chars) or invalid parameters | JSON error |
| 401 Unauthorized | Invalid or missing JWT | JSON error |
| 403 Forbidden | User lacks required role | JSON error |
| 503 Service Unavailable | Database connection failure | JSON error |

### 3.2 Search Strategy

The geocoding search combines multiple data sources in priority order:

1. **Address points** (`gis.address_point`) - Exact coordinates for specific addresses
2. **Road segments** (`gis.road_segment`) - Address range interpolation
3. **Named places** (`gis.named_place`) - Named locations (islands, villages, landmarks)
4. **Road intersections** - Computed from intersecting road segment geometries

**Query Parsing:**

The search query is parsed to detect patterns:

| Pattern | Example | Search Strategy |
|---------|---------|-----------------|
| Name + number | "Mannerheimintie 5" | Address point lookup, then road segment interpolation |
| Name only | "Mannerheimintie" | Street name search, place name search |
| Two names with separator | "Kaivokatu / Keskuskatu" | Road intersection search |
| Single word | "Kallio" | Place name search, street name search |

**Separators for intersection queries:** `/`, `&`, `and`, `ja` (Finnish), `och` (Swedish)

### 3.3 Coordinate Conversion

Per the Internationalization NFR:

- Database stores coordinates in EPSG:4326 (converted from EPSG:3067 during import)
- API returns coordinates in EPSG:4326
- Coordinates are limited to 6 decimal places (approximately 0.1 meter precision)

---

## 4. Error Response Format

All error responses use a consistent JSON format:

```json
{
  "error": {
    "code": "INVALID_QUERY",
    "message": "Search query must be at least 3 characters",
    "details": {
      "minLength": 3,
      "actualLength": 2
    }
  },
  "timestamp": "2026-02-21T10:30:00Z",
  "path": "/api/v1/geocode/search"
}
```

**Error Codes:**

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `INVALID_QUERY` | 400 | Search query validation failed |
| `INVALID_PARAMETER` | 400 | Request parameter validation failed |
| `INVALID_TILE_COORDINATES` | 400 | Tile row/col outside valid range for zoom level |
| `UNAUTHORIZED` | 401 | Missing or invalid JWT token |
| `FORBIDDEN` | 403 | Valid JWT but insufficient role |
| `LAYER_NOT_FOUND` | 404 | Requested tile layer does not exist |
| `DATABASE_ERROR` | 503 | Database connection or query failure |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

---

## 5. Health Check Endpoint

For infrastructure monitoring and load balancer health checks.

**Request:**

```
GET /health
```

**Note:** This endpoint does not require authentication.

**Response (200 OK):**

```json
{
  "status": "UP",
  "components": {
    "database": {
      "status": "UP"
    },
    "tileDirectory": {
      "status": "UP",
      "layers": ["terrain", "buildings"]
    }
  }
}
```

**Response (503 Service Unavailable):**

```json
{
  "status": "DOWN",
  "components": {
    "database": {
      "status": "DOWN",
      "error": "Connection refused"
    },
    "tileDirectory": {
      "status": "UP",
      "layers": ["terrain"]
    }
  }
}
```

---

## 6. Package Structure

```
net.pkhapps.idispatchx.gis.server/
├── Main.java                           # Application entry point
├── config/
│   ├── ServerConfig.java               # Server configuration (ports, paths)
│   ├── OidcConfig.java                 # OIDC provider configuration
│   └── DatabaseConfig.java             # Database connection configuration
├── api/
│   ├── wmts/
│   │   ├── WmtsController.java         # WMTS endpoint handlers
│   │   ├── CapabilitiesGenerator.java  # GetCapabilities XML generation
│   │   └── TileResponse.java           # Tile response helpers
│   ├── geocode/
│   │   ├── GeocodeController.java      # Geocoding endpoint handlers
│   │   ├── SearchRequest.java          # Request DTO with validation
│   │   ├── SearchResponse.java         # Response DTO
│   │   ├── LocationResult.java         # Individual result DTO
│   │   └── QueryParser.java            # Search query pattern detection
│   ├── health/
│   │   └── HealthController.java       # Health check endpoint
│   └── error/
│       ├── ErrorResponse.java          # Error response DTO
│       └── GlobalExceptionHandler.java # Centralized error handling
├── auth/
│   ├── JwtAuthHandler.java             # Javalin before-handler for JWT validation
│   ├── JwksClient.java                 # OIDC JWKS fetching and caching
│   ├── TokenValidator.java             # JWT signature and claims validation
│   └── BackChannelLogoutHandler.java   # OIDC back channel logout endpoint
├── service/
│   ├── tile/
│   │   ├── TileService.java            # Tile retrieval and caching
│   │   ├── TileResampler.java          # Runtime tile resampling
│   │   ├── TileCache.java              # LRU cache for resampled tiles
│   │   └── LayerDiscovery.java         # Filesystem layer scanning
│   └── geocode/
│       ├── GeocodeService.java         # Geocoding orchestration
│       ├── AddressPointSearcher.java   # Address point queries
│       ├── RoadSegmentSearcher.java    # Road segment queries with interpolation
│       ├── NamedPlaceSearcher.java     # Named place queries
│       ├── IntersectionSearcher.java   # Road intersection queries
│       └── ResultMerger.java           # Combine and rank results
├── repository/
│   ├── AddressPointRepository.java     # jOOQ queries for gis.address_point
│   ├── RoadSegmentRepository.java      # jOOQ queries for gis.road_segment
│   ├── NamedPlaceRepository.java       # jOOQ queries for gis.named_place
│   └── MunicipalityRepository.java     # jOOQ queries for gis.municipality
└── model/
    ├── MultilingualName.java           # Language-keyed name value object
    ├── Municipality.java               # Municipality value object
    ├── Coordinates.java                # EPSG:4326 coordinate pair
    └── TileCoordinates.java            # Zoom/row/col value object
```

---

## 7. Request Validation

### 7.1 Geocoding Query Validation

| Rule | Constraint |
|------|------------|
| Minimum query length | 3 characters |
| Maximum query length | 200 characters |
| Query characters | UTF-8, printable characters |
| Limit range | 1 to 50 |
| Municipality code | 3-digit numeric string |

### 7.2 Tile Coordinate Validation

| Rule | Constraint |
|------|------------|
| Zoom level | 0 to 15 |
| Row index | Non-negative, within matrix bounds for zoom level |
| Column index | Non-negative, within matrix bounds for zoom level |
| Layer name | Alphanumeric plus hyphens, max 50 characters |

### 7.3 Validation Implementation

Validation is performed at the controller level using self-validating DTOs:

```java
public record SearchRequest(
    String query,
    Integer limit,
    String municipality
) {
    public SearchRequest {
        Objects.requireNonNull(query, "query is required");
        if (query.length() < 3) {
            throw new ValidationException("INVALID_QUERY",
                "Search query must be at least 3 characters");
        }
        if (query.length() > 200) {
            throw new ValidationException("INVALID_QUERY",
                "Search query must not exceed 200 characters");
        }
        if (limit != null && (limit < 1 || limit > 50)) {
            throw new ValidationException("INVALID_PARAMETER",
                "Limit must be between 1 and 50");
        }
        if (municipality != null && !MUNICIPALITY_PATTERN.matcher(municipality).matches()) {
            throw new ValidationException("INVALID_PARAMETER",
                "Municipality code must be 3 digits");
        }
        // Apply defaults
        limit = limit != null ? limit : 20;
    }

    private static final Pattern MUNICIPALITY_PATTERN = Pattern.compile("^\\d{3}$");
}
```

---

## 8. Performance Considerations

### 8.1 Response Time Targets

Per the Performance NFR:

| Operation | Target |
|-----------|--------|
| Tile retrieval (pre-rendered) | < 1 second |
| Tile retrieval (resampled) | < 1 second |
| Geocoding search | Within seconds |

### 8.2 Tile Serving Optimizations

- **Filesystem direct read**: Tiles are served directly from the filesystem without database involvement
- **OS page cache**: Frequently accessed tiles are cached in OS memory
- **HTTP caching**: `Cache-Control` and `ETag` headers enable client and proxy caching
- **Conditional requests**: `If-None-Match` support reduces bandwidth for unchanged tiles

### 8.3 Geocoding Optimizations

- **pg_trgm indexes**: GIN indexes on name columns enable efficient fuzzy matching
- **Connection pooling**: jOOQ with HikariCP for database connection management
- **Query limiting**: Results are limited at the database level, not in application code
- **Parallel queries**: Address point, road segment, and named place searches can execute in parallel

### 8.4 Resampled Tile Cache

- **LRU eviction**: Least-recently-used tiles evicted when cache is full
- **Configurable size**: Default 1,000 tiles (~50 MB)
- **Cache key**: `(layer, zoom, row, col)` tuple
- **Invalidation**: Cache cleared on application restart; no runtime invalidation needed since source tiles are immutable until next import

---

## 9. API Versioning

The geocoding API uses URL path versioning (`/api/v1/`). The WMTS endpoint does not use versioning as it follows the OGC WMTS 1.0.0 standard.

### 9.1 Version Compatibility

- Breaking changes require a new version (`/api/v2/`)
- Additive changes (new optional fields) are backward compatible
- Old versions may be deprecated but should remain functional for a reasonable period

### 9.2 Content Negotiation

- Geocoding API: Always returns `application/json`
- WMTS GetCapabilities: Always returns `application/xml`
- WMTS GetTile: Always returns `image/png`

---

## 10. Verification Strategy

### 10.1 Unit Tests

- **Query parsing**: Verify pattern detection for addresses, intersections, place names
- **Coordinate validation**: Verify Finland bounds enforcement
- **Search request validation**: Verify all validation rules
- **Result merging**: Verify deduplication and ranking logic

### 10.2 Integration Tests

**Authentication:**
- Valid JWT with Dispatcher role: 200 OK
- Valid JWT with Observer role: 200 OK
- Valid JWT without required role: 403 Forbidden
- Invalid JWT signature: 401 Unauthorized
- Expired JWT: 401 Unauthorized
- Missing Authorization header: 401 Unauthorized

**WMTS:**
- GetCapabilities returns valid XML with all discovered layers
- GetTile for existing tile returns PNG image
- GetTile for missing tile returns 204 No Content
- GetTile for non-existent layer returns 404 Not Found
- GetTile with invalid zoom level returns 400 Bad Request
- Resampled tiles match expected dimensions and format

**Geocoding:**
- Address search returns address_point and road_segment results
- Place name search returns named_place results
- Intersection search (with `/` separator) returns intersection results
- Municipality filter restricts results correctly
- All language versions included in multilingual name fields
- Limit parameter constrains result count
- Query too short returns 400 Bad Request
- Empty results return 200 OK with empty array

### 10.3 Performance Tests

- Tile retrieval under concurrent load meets < 1 second target
- Geocoding search under concurrent load meets "within seconds" target
- Resampled tile cache prevents repeated resampling

### 10.4 Smoke Tests After Deployment

```bash
# Health check
curl http://localhost:8080/health

# GetCapabilities (requires valid JWT)
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/wmts/1.0.0/WMTSCapabilities.xml

# GetTile
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/wmts/terrain/ETRS-TM35FIN/14/13388/6058.png \
  -o tile.png

# Geocoding search
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/geocode/search?q=Mannerheimintie%201"
```

---

## 11. OpenAPI Specification

An OpenAPI 3.0 specification will be generated for the geocoding API (`/api/v1/geocode/*`). The WMTS endpoint is not included in OpenAPI as it follows the OGC WMTS standard.

The OpenAPI specification will be available at:

```
GET /api/v1/openapi.json
GET /api/v1/openapi.yaml
```

This endpoint does not require authentication to enable API documentation tools.
