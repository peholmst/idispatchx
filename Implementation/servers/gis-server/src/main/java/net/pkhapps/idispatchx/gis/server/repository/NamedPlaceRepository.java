package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.Language;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static net.pkhapps.idispatchx.gis.database.jooq.Tables.MUNICIPALITY;
import static net.pkhapps.idispatchx.gis.database.jooq.Tables.NAMED_PLACE;

/**
 * Repository for querying named place data from the GIS database.
 * <p>
 * Named places are stored with one row per language. The karttanimi_id groups
 * all language versions of the same place. This repository handles the grouping
 * and returns all language versions for each place.
 * <p>
 * Uses PostgreSQL's pg_trgm extension for fuzzy matching on place names.
 */
public final class NamedPlaceRepository {

    private static final Logger log = LoggerFactory.getLogger(NamedPlaceRepository.class);

    private final DSLContext dsl;

    /**
     * Creates a new NamedPlaceRepository with the given DSL context.
     *
     * @param dsl the jOOQ DSL context for database access
     * @throws NullPointerException if dsl is null
     */
    public NamedPlaceRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
    }

    /**
     * Searches for named places by name using fuzzy matching.
     * <p>
     * Uses PostgreSQL's pg_trgm extension for similarity-based matching.
     * Results are grouped by karttanimi_id to merge multilingual entries,
     * and ordered by similarity score (highest first).
     * <p>
     * The search can optionally be filtered to a specific municipality.
     *
     * @param query        the search query string
     * @param limit        the maximum number of results to return
     * @param municipality the municipality code to filter by, or null for all municipalities
     * @return a list of matching named places ordered by similarity
     * @throws NullPointerException     if query is null
     * @throws IllegalArgumentException if query is blank or limit is less than 1
     */
    public List<NamedPlaceSearchResult> search(String query, int limit, @Nullable MunicipalityCode municipality) {
        Objects.requireNonNull(query, "query must not be null");
        if (query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got " + limit);
        }

        log.debug("Searching named places: '{}' (limit: {}, municipality: {})", query, limit, municipality);

        // Create aliases for the tables used in the CTE
        var np = NAMED_PLACE.as("np");
        var m = MUNICIPALITY.as("m");

        // Similarity score field
        Field<Double> similarityScore = DSL.function("similarity", Double.class, np.NAME, DSL.val(query));

        // Build the matched_places CTE to get distinct places by karttanimi_id
        // with the best matching score
        var matchedPlacesQuery = DSL.select(
                        np.KARTTANIMI_ID,
                        np.MUNICIPALITY_CODE,
                        np.PLACE_CLASS,
                        DSL.function("ST_X", Double.class, np.LOCATION).as("lon"),
                        DSL.function("ST_Y", Double.class, np.LOCATION).as("lat"),
                        similarityScore.as("score")
                )
                .from(np)
                .where(DSL.condition("{0} % {1}", np.NAME, DSL.val(query)));

        // Add municipality filter if specified
        if (municipality != null) {
            matchedPlacesQuery = matchedPlacesQuery
                    .and(np.MUNICIPALITY_CODE.eq(municipality.code()));
        }

        // Add karttanimi_id not null condition to ensure grouping works
        matchedPlacesQuery = matchedPlacesQuery.and(np.KARTTANIMI_ID.isNotNull());

        var matchedPlaces = DSL.name("matched_places").as(
                matchedPlacesQuery
        );

        // Main query: join matched_places with named_place to get all language versions
        // and with municipality to get municipality names
        var mp = DSL.table(DSL.name("matched_places")).as("mp");

        // Fields from the CTE
        Field<Long> mpKarttanimiId = DSL.field(DSL.name("mp", "karttanimi_id"), Long.class);
        Field<String> mpMunicipalityCode = DSL.field(DSL.name("mp", "municipality_code"), String.class);
        Field<Integer> mpPlaceClass = DSL.field(DSL.name("mp", "place_class"), Integer.class);
        Field<Double> mpLon = DSL.field(DSL.name("mp", "lon"), Double.class);
        Field<Double> mpLat = DSL.field(DSL.name("mp", "lat"), Double.class);
        Field<Double> mpScore = DSL.field(DSL.name("mp", "score"), Double.class);

        // DISTINCT ON (karttanimi_id, language) requires those expressions to lead the ORDER BY.
        // Ordering by (karttanimi_id, language, score DESC) picks the highest-scoring row per
        // (karttanimi_id, language) pair. Overall ordering by score is applied in Java after grouping.
        //
        // The SQL limit is set to limit * 5 because the query returns one row per language per place,
        // and Finnish GIS data has at most 5 languages (fi, sv, smn, sms, sme). This caps the number
        // of rows fetched to avoid unbounded result sets for broad queries. A more precise approach
        // would select the top N distinct karttanimi_id values in a separate CTE first, but limit * 5
        // is a conservative upper bound that keeps the query simple.
        var records = dsl.with(matchedPlaces)
                .select(
                        mpKarttanimiId,
                        mpMunicipalityCode,
                        mpPlaceClass,
                        mpLon,
                        mpLat,
                        mpScore,
                        NAMED_PLACE.NAME,
                        NAMED_PLACE.LANGUAGE,
                        m.NAME_FI,
                        m.NAME_SV,
                        m.NAME_SMN,
                        m.NAME_SMS,
                        m.NAME_SME
                )
                .distinctOn(mpKarttanimiId, NAMED_PLACE.LANGUAGE)
                .from(mp)
                .join(NAMED_PLACE).on(NAMED_PLACE.KARTTANIMI_ID.eq(mpKarttanimiId))
                .leftJoin(m).on(m.MUNICIPALITY_CODE.eq(mpMunicipalityCode))
                .orderBy(
                        mpKarttanimiId,
                        NAMED_PLACE.LANGUAGE,
                        mpScore.desc()
                )
                .limit(limit * 5)
                .fetch();

        // Group records by karttanimi_id to merge multilingual entries.
        // Use LinkedHashMap to preserve insertion order (sorted by score in Java below).
        Map<Long, NamedPlaceBuilder> builders = new LinkedHashMap<>();
        int placesFound = 0;

        for (var record : records) {
            Long karttanimiId = record.get(mpKarttanimiId);
            if (karttanimiId == null) {
                continue;
            }

            var builder = builders.get(karttanimiId);
            if (builder == null) {
                if (placesFound >= limit) {
                    continue;
                }
                builder = new NamedPlaceBuilder(karttanimiId);
                builder.placeClass = record.get(mpPlaceClass);
                builder.longitude = record.get(mpLon);
                builder.latitude = record.get(mpLat);
                builder.similarityScore = record.get(mpScore);

                // Map municipality if present
                String municipalityCodeStr = record.get(mpMunicipalityCode);
                if (municipalityCodeStr != null) {
                    builder.municipality = mapMunicipality(record, municipalityCodeStr, m);
                }

                builders.put(karttanimiId, builder);
                placesFound++;
            }

            // Add the name for this language
            String name = record.get(NAMED_PLACE.NAME);
            String languageCode = record.get(NAMED_PLACE.LANGUAGE);
            if (name != null && languageCode != null && !languageCode.isBlank()) {
                builder.names.put(Language.of(languageCode), name);
            }
        }

        // Build results, sorted by similarity score descending, skipping places with missing coordinates
        List<NamedPlaceSearchResult> results = new ArrayList<>(builders.size());
        builders.values().stream()
                .sorted((a, b) -> Double.compare(
                        b.similarityScore != null ? b.similarityScore : 0.0,
                        a.similarityScore != null ? a.similarityScore : 0.0))
                .forEach(builder -> {
                    var result = builder.build();
                    if (result != null) {
                        results.add(result);
                    }
                });

        log.debug("Found {} named places matching '{}'", results.size(), query);
        return results;
    }

    private @Nullable Municipality mapMunicipality(Record record, String municipalityCode,
                                                    net.pkhapps.idispatchx.gis.database.jooq.tables.Municipality m) {
        var code = MunicipalityCode.of(municipalityCode);
        var name = MultilingualName.ofFinnishFields(
                record.get(m.NAME_FI),
                record.get(m.NAME_SV),
                record.get(m.NAME_SMN),
                record.get(m.NAME_SMS),
                record.get(m.NAME_SME)
        );
        if (name.isEmpty()) {
            return null;
        }
        return Municipality.of(code, name);
    }

    /**
     * Builder class to accumulate data for a single named place across multiple records.
     */
    private static class NamedPlaceBuilder {
        final long karttanimiId;
        final Map<Language, String> names = new HashMap<>();
        Integer placeClass;
        Municipality municipality;
        Double longitude;
        Double latitude;
        Double similarityScore;

        NamedPlaceBuilder(long karttanimiId) {
            this.karttanimiId = karttanimiId;
        }

        /**
         * Builds the result, or returns null if coordinates are missing.
         */
        @Nullable NamedPlaceSearchResult build() {
            if (latitude == null || longitude == null) {
                return null;
            }
            var multilingualName = names.isEmpty() ? MultilingualName.empty() : MultilingualName.of(names);
            var coordinates = Coordinates.Epsg4326.of(latitude, longitude);

            return new NamedPlaceSearchResult(
                    karttanimiId,
                    multilingualName,
                    placeClass != null ? placeClass : 0,
                    municipality,
                    coordinates,
                    similarityScore != null ? similarityScore : 0.0
            );
        }
    }
}
