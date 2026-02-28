package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static net.pkhapps.idispatchx.gis.database.jooq.Tables.MUNICIPALITY;
import static net.pkhapps.idispatchx.gis.database.jooq.Tables.ROAD_SEGMENT;

/**
 * Repository for querying road segments and computing interpolated addresses.
 * <p>
 * This repository uses PostgreSQL's pg_trgm extension for fuzzy name matching
 * and PostGIS functions for geometry operations such as address interpolation
 * along road segments and finding road intersections.
 * <p>
 * Address interpolation uses odd/even parity to select the appropriate side
 * of the road:
 * <ul>
 *   <li>Odd address numbers are assigned to the right side of the road</li>
 *   <li>Even address numbers are assigned to the left side of the road</li>
 * </ul>
 */
public final class RoadSegmentRepository {

    private static final Logger log = LoggerFactory.getLogger(RoadSegmentRepository.class);

    /**
     * Maximum decimal places for coordinate precision (from NFR).
     */
    private static final int COORDINATE_DECIMAL_PLACES = 6;

    private final DSLContext dsl;

    /**
     * Creates a new road segment repository.
     *
     * @param dsl the jOOQ DSL context for database operations
     * @throws NullPointerException if dsl is null
     */
    public RoadSegmentRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
    }

    /**
     * Searches for road segments by fuzzy name matching using pg_trgm.
     * <p>
     * The search matches against Finnish and Swedish road names using trigram
     * similarity. Results are ordered by similarity score (highest first).
     *
     * @param query        the search query string
     * @param limit        maximum number of results to return
     * @param municipality optional municipality filter
     * @return list of matching road segments, ordered by similarity score
     * @throws NullPointerException     if query is null
     * @throws IllegalArgumentException if limit is less than 1
     */
    public List<RoadSegmentSearchResult> searchByName(String query, int limit,
                                                       @Nullable MunicipalityCode municipality) {
        Objects.requireNonNull(query, "query must not be null");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got " + limit);
        }

        if (query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }

        log.debug("Searching road segments by name: query='{}', limit={}, municipality={}",
                query, limit, municipality);

        // pg_trgm similarity function
        Field<Double> similarityFi = DSL.function("similarity",
                Double.class, ROAD_SEGMENT.NAME_FI, DSL.val(query));
        Field<Double> similaritySv = DSL.function("similarity",
                Double.class, ROAD_SEGMENT.NAME_SV, DSL.val(query));
        Field<Double> maxSimilarity = DSL.greatest(
                DSL.coalesce(similarityFi, DSL.val(0.0)),
                DSL.coalesce(similaritySv, DSL.val(0.0))
        );

        // pg_trgm % operator for fuzzy matching
        Condition nameMatchesFi = DSL.condition("{0} % {1}", ROAD_SEGMENT.NAME_FI, DSL.val(query));
        Condition nameMatchesSv = DSL.condition("{0} % {1}", ROAD_SEGMENT.NAME_SV, DSL.val(query));
        Condition nameMatches = nameMatchesFi.or(nameMatchesSv);

        // Build query with optional municipality filter
        Condition whereCondition = nameMatches;
        if (municipality != null) {
            whereCondition = whereCondition.and(
                    ROAD_SEGMENT.MUNICIPALITY_CODE.eq(municipality.code()));
        }

        var results = dsl.select(
                        ROAD_SEGMENT.ID,
                        ROAD_SEGMENT.NAME_FI,
                        ROAD_SEGMENT.NAME_SV,
                        ROAD_SEGMENT.NAME_SMN,
                        ROAD_SEGMENT.NAME_SMS,
                        ROAD_SEGMENT.NAME_SME,
                        ROAD_SEGMENT.MUNICIPALITY_CODE,
                        ROAD_SEGMENT.MIN_ADDRESS_LEFT,
                        ROAD_SEGMENT.MAX_ADDRESS_LEFT,
                        ROAD_SEGMENT.MIN_ADDRESS_RIGHT,
                        ROAD_SEGMENT.MAX_ADDRESS_RIGHT,
                        MUNICIPALITY.NAME_FI.as("m_name_fi"),
                        MUNICIPALITY.NAME_SV.as("m_name_sv"),
                        MUNICIPALITY.NAME_SMN.as("m_name_smn"),
                        MUNICIPALITY.NAME_SMS.as("m_name_sms"),
                        MUNICIPALITY.NAME_SME.as("m_name_sme"),
                        maxSimilarity.as("similarity")
                )
                .from(ROAD_SEGMENT)
                .leftJoin(MUNICIPALITY)
                .on(ROAD_SEGMENT.MUNICIPALITY_CODE.eq(MUNICIPALITY.MUNICIPALITY_CODE))
                .where(whereCondition)
                .orderBy(maxSimilarity.desc())
                .limit(limit)
                .fetch();

        List<RoadSegmentSearchResult> searchResults = new ArrayList<>();
        for (var record : results) {
            var roadName = MultilingualName.ofFinnishFields(
                    record.get(ROAD_SEGMENT.NAME_FI),
                    record.get(ROAD_SEGMENT.NAME_SV),
                    record.get(ROAD_SEGMENT.NAME_SMN),
                    record.get(ROAD_SEGMENT.NAME_SMS),
                    record.get(ROAD_SEGMENT.NAME_SME)
            );

            var muni = buildMunicipality(
                    record.get(ROAD_SEGMENT.MUNICIPALITY_CODE),
                    record.get("m_name_fi", String.class),
                    record.get("m_name_sv", String.class),
                    record.get("m_name_smn", String.class),
                    record.get("m_name_sms", String.class),
                    record.get("m_name_sme", String.class)
            );

            searchResults.add(new RoadSegmentSearchResult(
                    record.get(ROAD_SEGMENT.ID),
                    roadName,
                    muni,
                    record.get(ROAD_SEGMENT.MIN_ADDRESS_LEFT),
                    record.get(ROAD_SEGMENT.MAX_ADDRESS_LEFT),
                    record.get(ROAD_SEGMENT.MIN_ADDRESS_RIGHT),
                    record.get(ROAD_SEGMENT.MAX_ADDRESS_RIGHT),
                    record.get("similarity", Double.class)
            ));
        }

        log.debug("Found {} road segments matching query '{}'", searchResults.size(), query);
        return searchResults;
    }

    /**
     * Interpolates the geographic coordinates of a street address along a road segment.
     * <p>
     * The method finds road segments matching the given name that contain the requested
     * address number within their address range. The position is calculated by linear
     * interpolation along the segment geometry.
     * <p>
     * Odd/even parity determines which side of the road is used:
     * <ul>
     *   <li>Odd numbers use the right side (min/max_address_right)</li>
     *   <li>Even numbers use the left side (min/max_address_left)</li>
     * </ul>
     *
     * @param roadName     the road name to search for
     * @param number       the address number to interpolate
     * @param municipality optional municipality filter
     * @return the interpolated address result, or empty if no matching segment found
     * @throws NullPointerException     if roadName is null
     * @throws IllegalArgumentException if number is less than 1
     */
    public Optional<InterpolatedAddressResult> interpolateAddress(String roadName, int number,
                                                                   @Nullable MunicipalityCode municipality) {
        Objects.requireNonNull(roadName, "roadName must not be null");
        if (number < 1) {
            throw new IllegalArgumentException("number must be at least 1, got " + number);
        }

        if (roadName.isBlank()) {
            throw new IllegalArgumentException("roadName must not be blank");
        }

        log.debug("Interpolating address: roadName='{}', number={}, municipality={}",
                roadName, number, municipality);

        boolean isOdd = (number % 2) == 1;

        // Build address range condition based on odd/even parity
        Condition addressInRange;
        Field<Integer> minAddress;
        Field<Integer> maxAddress;

        if (isOdd) {
            // Odd numbers on right side
            minAddress = ROAD_SEGMENT.MIN_ADDRESS_RIGHT;
            maxAddress = ROAD_SEGMENT.MAX_ADDRESS_RIGHT;
            addressInRange = ROAD_SEGMENT.MIN_ADDRESS_RIGHT.isNotNull()
                    .and(ROAD_SEGMENT.MAX_ADDRESS_RIGHT.isNotNull())
                    .and(DSL.val(number).between(ROAD_SEGMENT.MIN_ADDRESS_RIGHT, ROAD_SEGMENT.MAX_ADDRESS_RIGHT));
        } else {
            // Even numbers on left side
            minAddress = ROAD_SEGMENT.MIN_ADDRESS_LEFT;
            maxAddress = ROAD_SEGMENT.MAX_ADDRESS_LEFT;
            addressInRange = ROAD_SEGMENT.MIN_ADDRESS_LEFT.isNotNull()
                    .and(ROAD_SEGMENT.MAX_ADDRESS_LEFT.isNotNull())
                    .and(DSL.val(number).between(ROAD_SEGMENT.MIN_ADDRESS_LEFT, ROAD_SEGMENT.MAX_ADDRESS_LEFT));
        }

        // Name matching condition (exact or fuzzy)
        Condition nameMatches = ROAD_SEGMENT.NAME_FI.equalIgnoreCase(roadName)
                .or(ROAD_SEGMENT.NAME_SV.equalIgnoreCase(roadName))
                .or(DSL.condition("{0} % {1}", ROAD_SEGMENT.NAME_FI, DSL.val(roadName)))
                .or(DSL.condition("{0} % {1}", ROAD_SEGMENT.NAME_SV, DSL.val(roadName)));

        Condition whereCondition = nameMatches.and(addressInRange);
        if (municipality != null) {
            whereCondition = whereCondition.and(
                    ROAD_SEGMENT.MUNICIPALITY_CODE.eq(municipality.code()));
        }

        // Similarity for ordering by best match
        Field<Double> similarityFi = DSL.function("similarity",
                Double.class, ROAD_SEGMENT.NAME_FI, DSL.val(roadName));
        Field<Double> similaritySv = DSL.function("similarity",
                Double.class, ROAD_SEGMENT.NAME_SV, DSL.val(roadName));
        Field<Double> maxSimilarity = DSL.greatest(
                DSL.coalesce(similarityFi, DSL.val(0.0)),
                DSL.coalesce(similaritySv, DSL.val(0.0))
        );

        var record = dsl.select(
                        ROAD_SEGMENT.ID,
                        ROAD_SEGMENT.NAME_FI,
                        ROAD_SEGMENT.NAME_SV,
                        ROAD_SEGMENT.NAME_SMN,
                        ROAD_SEGMENT.NAME_SMS,
                        ROAD_SEGMENT.NAME_SME,
                        ROAD_SEGMENT.MUNICIPALITY_CODE,
                        minAddress.as("min_addr"),
                        maxAddress.as("max_addr"),
                        ROAD_SEGMENT.GEOMETRY,
                        MUNICIPALITY.NAME_FI.as("m_name_fi"),
                        MUNICIPALITY.NAME_SV.as("m_name_sv"),
                        MUNICIPALITY.NAME_SMN.as("m_name_smn"),
                        MUNICIPALITY.NAME_SMS.as("m_name_sms"),
                        MUNICIPALITY.NAME_SME.as("m_name_sme")
                )
                .from(ROAD_SEGMENT)
                .leftJoin(MUNICIPALITY)
                .on(ROAD_SEGMENT.MUNICIPALITY_CODE.eq(MUNICIPALITY.MUNICIPALITY_CODE))
                .where(whereCondition)
                .orderBy(maxSimilarity.desc())
                .limit(1)
                .fetchOne();

        if (record == null) {
            log.debug("No road segment found for address: roadName='{}', number={}", roadName, number);
            return Optional.empty();
        }

        // Calculate interpolation fraction
        Integer minAddr = record.get("min_addr", Integer.class);
        Integer maxAddr = record.get("max_addr", Integer.class);

        if (minAddr == null || maxAddr == null) {
            log.warn("Address range is null for segment ID {}", record.get(ROAD_SEGMENT.ID));
            return Optional.empty();
        }

        double fraction;
        if (maxAddr.equals(minAddr)) {
            fraction = 0.5; // Single address, use midpoint
        } else {
            fraction = (double) (number - minAddr) / (maxAddr - minAddr);
        }

        // Clamp fraction to [0, 1]
        fraction = Math.max(0.0, Math.min(1.0, fraction));

        // Interpolate coordinates using PostGIS
        var interpolatedRecord = dsl.select(
                        DSL.field("ST_X(ST_LineInterpolatePoint({0}, {1}))",
                                Double.class, ROAD_SEGMENT.GEOMETRY, DSL.val(fraction)).as("lon"),
                        DSL.field("ST_Y(ST_LineInterpolatePoint({0}, {1}))",
                                Double.class, ROAD_SEGMENT.GEOMETRY, DSL.val(fraction)).as("lat")
                )
                .from(ROAD_SEGMENT)
                .where(ROAD_SEGMENT.ID.eq(record.get(ROAD_SEGMENT.ID)))
                .fetchOne();

        if (interpolatedRecord == null) {
            log.warn("Failed to interpolate coordinates for segment ID {}", record.get(ROAD_SEGMENT.ID));
            return Optional.empty();
        }

        Double longitude = interpolatedRecord.get("lon", Double.class);
        Double latitude = interpolatedRecord.get("lat", Double.class);

        if (longitude == null || latitude == null) {
            log.warn("Interpolated coordinates are null for segment ID {}", record.get(ROAD_SEGMENT.ID));
            return Optional.empty();
        }

        // Round coordinates to the allowed precision
        double roundedLat = roundToDecimalPlaces(latitude, COORDINATE_DECIMAL_PLACES);
        double roundedLon = roundToDecimalPlaces(longitude, COORDINATE_DECIMAL_PLACES);

        var streetName = MultilingualName.ofFinnishFields(
                record.get(ROAD_SEGMENT.NAME_FI),
                record.get(ROAD_SEGMENT.NAME_SV),
                record.get(ROAD_SEGMENT.NAME_SMN),
                record.get(ROAD_SEGMENT.NAME_SMS),
                record.get(ROAD_SEGMENT.NAME_SME)
        );

        var muni = buildMunicipality(
                record.get(ROAD_SEGMENT.MUNICIPALITY_CODE),
                record.get("m_name_fi", String.class),
                record.get("m_name_sv", String.class),
                record.get("m_name_smn", String.class),
                record.get("m_name_sms", String.class),
                record.get("m_name_sme", String.class)
        );

        var coordinates = Coordinates.Epsg4326.of(roundedLat, roundedLon);

        var result = new InterpolatedAddressResult(
                streetName,
                String.valueOf(number),
                muni,
                coordinates
        );

        log.debug("Interpolated address: {} {} -> {}", streetName.anyValue().orElse(""), number, coordinates);
        return Optional.of(result);
    }

    /**
     * Searches for road intersections by fuzzy name matching.
     * <p>
     * Finds pairs of road segments that geometrically intersect and match
     * the search query. Results include the intersection point coordinates
     * and similarity scores.
     *
     * @param query        the search query string
     * @param limit        maximum number of results to return
     * @param municipality optional municipality filter
     * @return list of matching intersections, ordered by similarity score
     * @throws NullPointerException     if query is null
     * @throws IllegalArgumentException if limit is less than 1
     */
    public List<IntersectionSearchResult> searchIntersections(String query, int limit,
                                                               @Nullable MunicipalityCode municipality) {
        Objects.requireNonNull(query, "query must not be null");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got " + limit);
        }

        if (query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }

        log.debug("Searching intersections: query='{}', limit={}, municipality={}",
                query, limit, municipality);

        // Create table aliases
        var rs1 = ROAD_SEGMENT.as("rs1");
        var rs2 = ROAD_SEGMENT.as("rs2");

        // Similarity functions for both roads
        Field<Double> similarity1Fi = DSL.function("similarity",
                Double.class, rs1.NAME_FI, DSL.val(query));
        Field<Double> similarity1Sv = DSL.function("similarity",
                Double.class, rs1.NAME_SV, DSL.val(query));
        Field<Double> similarity2Fi = DSL.function("similarity",
                Double.class, rs2.NAME_FI, DSL.val(query));
        Field<Double> similarity2Sv = DSL.function("similarity",
                Double.class, rs2.NAME_SV, DSL.val(query));

        Field<Double> maxSimilarity = DSL.greatest(
                DSL.coalesce(similarity1Fi, DSL.val(0.0)),
                DSL.coalesce(similarity1Sv, DSL.val(0.0)),
                DSL.coalesce(similarity2Fi, DSL.val(0.0)),
                DSL.coalesce(similarity2Sv, DSL.val(0.0))
        );

        // Intersection point
        Field<Double> intersectionX = DSL.field(
                "ST_X(ST_PointOnSurface(ST_Intersection({0}, {1})))",
                Double.class, rs1.GEOMETRY, rs2.GEOMETRY);
        Field<Double> intersectionY = DSL.field(
                "ST_Y(ST_PointOnSurface(ST_Intersection({0}, {1})))",
                Double.class, rs1.GEOMETRY, rs2.GEOMETRY);

        // Name match conditions using pg_trgm %
        Condition name1MatchesFi = DSL.condition("{0} % {1}", rs1.NAME_FI, DSL.val(query));
        Condition name1MatchesSv = DSL.condition("{0} % {1}", rs1.NAME_SV, DSL.val(query));
        Condition name2MatchesFi = DSL.condition("{0} % {1}", rs2.NAME_FI, DSL.val(query));
        Condition name2MatchesSv = DSL.condition("{0} % {1}", rs2.NAME_SV, DSL.val(query));
        Condition nameMatches = name1MatchesFi.or(name1MatchesSv).or(name2MatchesFi).or(name2MatchesSv);

        // ST_Intersects condition and ensure different segments
        Condition intersects = DSL.condition("ST_Intersects({0}, {1})", rs1.GEOMETRY, rs2.GEOMETRY);
        Condition differentSegments = rs1.ID.lt(rs2.ID);

        // Ensure segments have names (not just unnamed paths)
        Condition hasNames = rs1.NAME_FI.isNotNull().or(rs1.NAME_SV.isNotNull())
                .and(rs2.NAME_FI.isNotNull().or(rs2.NAME_SV.isNotNull()));

        // Build where condition
        Condition whereCondition = intersects
                .and(differentSegments)
                .and(hasNames)
                .and(nameMatches);

        if (municipality != null) {
            whereCondition = whereCondition.and(
                    rs1.MUNICIPALITY_CODE.eq(municipality.code())
                            .or(rs2.MUNICIPALITY_CODE.eq(municipality.code())));
        }

        var results = dsl.select(
                        rs1.NAME_FI.as("rs1_name_fi"),
                        rs1.NAME_SV.as("rs1_name_sv"),
                        rs1.NAME_SMN.as("rs1_name_smn"),
                        rs1.NAME_SMS.as("rs1_name_sms"),
                        rs1.NAME_SME.as("rs1_name_sme"),
                        rs2.NAME_FI.as("rs2_name_fi"),
                        rs2.NAME_SV.as("rs2_name_sv"),
                        rs2.NAME_SMN.as("rs2_name_smn"),
                        rs2.NAME_SMS.as("rs2_name_sms"),
                        rs2.NAME_SME.as("rs2_name_sme"),
                        rs1.MUNICIPALITY_CODE.as("muni_code"),
                        MUNICIPALITY.NAME_FI.as("m_name_fi"),
                        MUNICIPALITY.NAME_SV.as("m_name_sv"),
                        MUNICIPALITY.NAME_SMN.as("m_name_smn"),
                        MUNICIPALITY.NAME_SMS.as("m_name_sms"),
                        MUNICIPALITY.NAME_SME.as("m_name_sme"),
                        intersectionX.as("int_x"),
                        intersectionY.as("int_y"),
                        maxSimilarity.as("similarity")
                )
                .from(rs1)
                .join(rs2).on(intersects.and(differentSegments))
                .leftJoin(MUNICIPALITY).on(rs1.MUNICIPALITY_CODE.eq(MUNICIPALITY.MUNICIPALITY_CODE))
                .where(hasNames.and(nameMatches)
                        .and(municipality != null
                                ? rs1.MUNICIPALITY_CODE.eq(municipality.code())
                                        .or(rs2.MUNICIPALITY_CODE.eq(municipality.code()))
                                : DSL.trueCondition()))
                .orderBy(maxSimilarity.desc())
                .limit(limit)
                .fetch();

        List<IntersectionSearchResult> searchResults = new ArrayList<>();
        for (var record : results) {
            var roadA = MultilingualName.ofFinnishFields(
                    record.get("rs1_name_fi", String.class),
                    record.get("rs1_name_sv", String.class),
                    record.get("rs1_name_smn", String.class),
                    record.get("rs1_name_sms", String.class),
                    record.get("rs1_name_sme", String.class)
            );

            var roadB = MultilingualName.ofFinnishFields(
                    record.get("rs2_name_fi", String.class),
                    record.get("rs2_name_sv", String.class),
                    record.get("rs2_name_smn", String.class),
                    record.get("rs2_name_sms", String.class),
                    record.get("rs2_name_sme", String.class)
            );

            var muni = buildMunicipality(
                    record.get("muni_code", String.class),
                    record.get("m_name_fi", String.class),
                    record.get("m_name_sv", String.class),
                    record.get("m_name_smn", String.class),
                    record.get("m_name_sms", String.class),
                    record.get("m_name_sme", String.class)
            );

            Double lon = record.get("int_x", Double.class);
            Double lat = record.get("int_y", Double.class);

            if (lon == null || lat == null) {
                log.warn("Intersection coordinates are null, skipping");
                continue;
            }

            // Round coordinates to the allowed precision
            double roundedLat = roundToDecimalPlaces(lat, COORDINATE_DECIMAL_PLACES);
            double roundedLon = roundToDecimalPlaces(lon, COORDINATE_DECIMAL_PLACES);

            var coordinates = Coordinates.Epsg4326.of(roundedLat, roundedLon);

            searchResults.add(new IntersectionSearchResult(
                    roadA,
                    roadB,
                    muni,
                    coordinates,
                    record.get("similarity", Double.class)
            ));
        }

        log.debug("Found {} intersections matching query '{}'", searchResults.size(), query);
        return searchResults;
    }

    /**
     * Builds a Municipality from the code and name fields.
     */
    private @Nullable Municipality buildMunicipality(@Nullable String code,
                                                      @Nullable String nameFi, @Nullable String nameSv,
                                                      @Nullable String nameSmn, @Nullable String nameSms,
                                                      @Nullable String nameSme) {
        var name = MultilingualName.ofFinnishFields(nameFi, nameSv, nameSmn, nameSms, nameSme);
        if (name.isEmpty()) {
            return null;
        }

        MunicipalityCode municipalityCode = null;
        if (code != null && !code.isBlank()) {
            try {
                municipalityCode = MunicipalityCode.of(code);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid municipality code: {}", code);
            }
        }

        return Municipality.of(municipalityCode, name);
    }

    /**
     * Rounds a double value to the specified number of decimal places.
     */
    private double roundToDecimalPlaces(double value, int decimalPlaces) {
        return BigDecimal.valueOf(value)
                .setScale(decimalPlaces, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
