package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Coordinates;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import static net.pkhapps.idispatchx.gis.database.jooq.Tables.ADDRESS_POINT;
import static net.pkhapps.idispatchx.gis.database.jooq.Tables.MUNICIPALITY;

/**
 * Repository for querying address points from the GIS database.
 * <p>
 * Uses PostgreSQL pg_trgm extension for fuzzy text matching, enabling
 * search queries to find addresses even with minor typos or variations.
 * Results are ranked by similarity score.
 */
public final class AddressPointRepository {

    private static final Logger log = LoggerFactory.getLogger(AddressPointRepository.class);

    private static final Field<String> MUNICIPALITY_NAME_FI = MUNICIPALITY.NAME_FI.as("m_name_fi");
    private static final Field<String> MUNICIPALITY_NAME_SV = MUNICIPALITY.NAME_SV.as("m_name_sv");
    private static final Field<String> MUNICIPALITY_NAME_SMN = MUNICIPALITY.NAME_SMN.as("m_name_smn");
    private static final Field<String> MUNICIPALITY_NAME_SMS = MUNICIPALITY.NAME_SMS.as("m_name_sms");
    private static final Field<String> MUNICIPALITY_NAME_SME = MUNICIPALITY.NAME_SME.as("m_name_sme");

    private final DSLContext dsl;

    /**
     * Creates a new AddressPointRepository with the given DSL context.
     *
     * @param dslContext the jOOQ DSL context for database operations
     * @throws NullPointerException if dslContext is null
     */
    public AddressPointRepository(DSLContext dslContext) {
        this.dsl = Objects.requireNonNull(dslContext, "dslContext must not be null");
    }

    /**
     * Searches for address points matching the given query.
     * <p>
     * The search uses pg_trgm fuzzy matching against Finnish and Swedish street names,
     * and exact matching against the address number. Results are ordered by similarity
     * score in descending order.
     *
     * @param query        the search query (street name or number)
     * @param limit        the maximum number of results to return (must be positive)
     * @param municipality optional municipality code to filter results, may be null
     * @return a list of matching address points, ordered by similarity score
     * @throws NullPointerException     if query is null
     * @throws IllegalArgumentException if query is blank or limit is not positive
     */
    public List<AddressSearchResult> search(String query, int limit,
                                            @Nullable MunicipalityCode municipality) {
        Objects.requireNonNull(query, "query must not be null");
        if (query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, got " + limit);
        }

        log.debug("Searching for addresses with query='{}', limit={}, municipality={}",
                query, limit, municipality);

        // pg_trgm similarity function for ranking
        Field<Double> similarityFi = similarity(ADDRESS_POINT.NAME_FI, query);
        Field<Double> similaritySv = similarity(ADDRESS_POINT.NAME_SV, query);
        Field<Double> maxSimilarity = DSL.greatest(similarityFi, similaritySv).as("similarity_score");

        // ST_X and ST_Y for coordinate extraction
        Field<Double> longitude = DSL.field("ST_X({0})", Double.class, ADDRESS_POINT.LOCATION).as("lon");
        Field<Double> latitude = DSL.field("ST_Y({0})", Double.class, ADDRESS_POINT.LOCATION).as("lat");

        // pg_trgm similarity operator (%) for fuzzy matching
        Condition fuzzyMatchFi = similarityOperator(ADDRESS_POINT.NAME_FI, query);
        Condition fuzzyMatchSv = similarityOperator(ADDRESS_POINT.NAME_SV, query);
        Condition exactNumberMatch = ADDRESS_POINT.NUMBER.eq(query);

        Condition searchCondition = fuzzyMatchFi.or(fuzzyMatchSv).or(exactNumberMatch);

        // Optional municipality filter
        if (municipality != null) {
            searchCondition = searchCondition.and(
                    ADDRESS_POINT.MUNICIPALITY_CODE.eq(municipality.code()));
        }

        var results = dsl.select(
                        ADDRESS_POINT.ID,
                        ADDRESS_POINT.NUMBER,
                        ADDRESS_POINT.NAME_FI,
                        ADDRESS_POINT.NAME_SV,
                        ADDRESS_POINT.NAME_SMN,
                        ADDRESS_POINT.NAME_SMS,
                        ADDRESS_POINT.NAME_SME,
                        ADDRESS_POINT.MUNICIPALITY_CODE,
                        MUNICIPALITY_NAME_FI,
                        MUNICIPALITY_NAME_SV,
                        MUNICIPALITY_NAME_SMN,
                        MUNICIPALITY_NAME_SMS,
                        MUNICIPALITY_NAME_SME,
                        latitude,
                        longitude,
                        maxSimilarity
                )
                .from(ADDRESS_POINT)
                .leftJoin(MUNICIPALITY)
                .on(ADDRESS_POINT.MUNICIPALITY_CODE.eq(MUNICIPALITY.MUNICIPALITY_CODE))
                .where(searchCondition)
                .orderBy(maxSimilarity.desc())
                .limit(limit)
                .fetch();

        log.debug("Found {} address results for query='{}'", results.size(), query);

        return results.stream()
                .map(this::mapToAddressSearchResult)
                .toList();
    }

    /**
     * Creates a pg_trgm similarity function call.
     */
    private Field<Double> similarity(Field<String> field, String query) {
        return DSL.field("similarity({0}, {1})", Double.class, field, DSL.val(query));
    }

    /**
     * Creates a pg_trgm similarity operator condition (%).
     */
    private Condition similarityOperator(Field<String> field, String query) {
        return DSL.condition("{0} % {1}", field, DSL.val(query));
    }

    /**
     * Maps a database record to an AddressSearchResult.
     */
    private AddressSearchResult mapToAddressSearchResult(Record record) {
        var id = record.get(ADDRESS_POINT.ID);
        var number = record.get(ADDRESS_POINT.NUMBER);

        // Build multilingual street name
        var streetName = MultilingualName.ofFinnishFields(
                record.get(ADDRESS_POINT.NAME_FI),
                record.get(ADDRESS_POINT.NAME_SV),
                record.get(ADDRESS_POINT.NAME_SMN),
                record.get(ADDRESS_POINT.NAME_SMS),
                record.get(ADDRESS_POINT.NAME_SME)
        );

        // Build municipality if code is present
        Municipality municipality = null;
        var municipalityCode = record.get(ADDRESS_POINT.MUNICIPALITY_CODE);
        if (municipalityCode != null) {
            var municipalityName = MultilingualName.ofFinnishFields(
                    record.get("m_name_fi", String.class),
                    record.get("m_name_sv", String.class),
                    record.get("m_name_smn", String.class),
                    record.get("m_name_sms", String.class),
                    record.get("m_name_sme", String.class)
            );
            if (!municipalityName.isEmpty()) {
                municipality = Municipality.of(MunicipalityCode.of(municipalityCode), municipalityName);
            }
        }

        // Extract coordinates
        var latitude = record.get("lat", Double.class);
        var longitude = record.get("lon", Double.class);
        var coordinates = Coordinates.Epsg4326.of(latitude, longitude);

        // Get similarity score
        var similarityScore = record.get("similarity_score", Double.class);
        if (similarityScore == null) {
            similarityScore = 0.0;
        }

        return new AddressSearchResult(id, number, streetName, municipality, coordinates, similarityScore);
    }
}
