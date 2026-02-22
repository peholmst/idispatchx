package net.pkhapps.idispatchx.gis.server.repository;

import net.pkhapps.idispatchx.common.domain.model.Language;
import net.pkhapps.idispatchx.common.domain.model.MultilingualName;
import net.pkhapps.idispatchx.common.domain.model.Municipality;
import net.pkhapps.idispatchx.common.domain.model.MunicipalityCode;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static net.pkhapps.idispatchx.gis.database.jooq.Tables.MUNICIPALITY;

/**
 * Repository for querying municipality data from the GIS database.
 * <p>
 * Provides lookup by municipality code and fuzzy search by name using
 * PostgreSQL's pg_trgm extension for similarity matching.
 */
public final class MunicipalityRepository {

    private static final Logger log = LoggerFactory.getLogger(MunicipalityRepository.class);

    private final DSLContext dsl;

    /**
     * Creates a new MunicipalityRepository with the given DSL context.
     *
     * @param dsl the jOOQ DSL context for database access
     * @throws NullPointerException if dsl is null
     */
    public MunicipalityRepository(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl must not be null");
    }

    /**
     * Finds a municipality by its code.
     *
     * @param code the municipality code to look up
     * @return the municipality if found, empty otherwise
     * @throws NullPointerException if code is null
     */
    public Optional<Municipality> findByCode(MunicipalityCode code) {
        Objects.requireNonNull(code, "code must not be null");

        log.debug("Looking up municipality by code: {}", code);

        var record = dsl.select()
                .from(MUNICIPALITY)
                .where(MUNICIPALITY.MUNICIPALITY_CODE.eq(code.code()))
                .fetchOne();

        if (record == null) {
            log.debug("Municipality not found for code: {}", code);
            return Optional.empty();
        }

        var municipality = mapToMunicipality(record);
        log.debug("Found municipality: {}", municipality);
        return Optional.of(municipality);
    }

    /**
     * Searches for municipalities by name using fuzzy matching.
     * <p>
     * Uses PostgreSQL's pg_trgm extension for similarity-based matching.
     * Results are ordered by similarity score (highest first).
     *
     * @param query the search query string
     * @param limit the maximum number of results to return
     * @return a list of matching municipalities ordered by similarity
     * @throws NullPointerException     if query is null
     * @throws IllegalArgumentException if query is blank or limit is less than 1
     */
    public List<Municipality> searchByName(String query, int limit) {
        Objects.requireNonNull(query, "query must not be null");
        if (query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got " + limit);
        }

        log.debug("Searching municipalities by name: '{}' (limit: {})", query, limit);

        // Build a condition that matches any of the name columns using pg_trgm
        var nameCondition = DSL.condition("{0} % {1}", MUNICIPALITY.NAME_FI, DSL.val(query))
                .or(DSL.condition("{0} % {1}", MUNICIPALITY.NAME_SV, DSL.val(query)))
                .or(DSL.condition("{0} % {1}", MUNICIPALITY.NAME_SMN, DSL.val(query)))
                .or(DSL.condition("{0} % {1}", MUNICIPALITY.NAME_SMS, DSL.val(query)))
                .or(DSL.condition("{0} % {1}", MUNICIPALITY.NAME_SME, DSL.val(query)));

        // Calculate max similarity across all name columns
        var maxSimilarity = DSL.greatest(
                DSL.coalesce(DSL.function("similarity", Double.class, MUNICIPALITY.NAME_FI, DSL.val(query)), 0.0),
                DSL.coalesce(DSL.function("similarity", Double.class, MUNICIPALITY.NAME_SV, DSL.val(query)), 0.0),
                DSL.coalesce(DSL.function("similarity", Double.class, MUNICIPALITY.NAME_SMN, DSL.val(query)), 0.0),
                DSL.coalesce(DSL.function("similarity", Double.class, MUNICIPALITY.NAME_SMS, DSL.val(query)), 0.0),
                DSL.coalesce(DSL.function("similarity", Double.class, MUNICIPALITY.NAME_SME, DSL.val(query)), 0.0)
        );

        var records = dsl.select(MUNICIPALITY.fields())
                .select(maxSimilarity.as("score"))
                .from(MUNICIPALITY)
                .where(nameCondition)
                .orderBy(DSL.field("score").desc())
                .limit(limit)
                .fetch();

        var municipalities = records.stream()
                .map(this::mapToMunicipality)
                .toList();

        log.debug("Found {} municipalities matching '{}'", municipalities.size(), query);
        return municipalities;
    }

    private Municipality mapToMunicipality(Record record) {
        var code = MunicipalityCode.of(record.get(MUNICIPALITY.MUNICIPALITY_CODE));

        var nameValues = new HashMap<Language, String>();
        addNameIfPresent(nameValues, Language.of("fi"), record.get(MUNICIPALITY.NAME_FI));
        addNameIfPresent(nameValues, Language.of("sv"), record.get(MUNICIPALITY.NAME_SV));
        addNameIfPresent(nameValues, Language.of("smn"), record.get(MUNICIPALITY.NAME_SMN));
        addNameIfPresent(nameValues, Language.of("sms"), record.get(MUNICIPALITY.NAME_SMS));
        addNameIfPresent(nameValues, Language.of("sme"), record.get(MUNICIPALITY.NAME_SME));

        var name = MultilingualName.of(nameValues);

        return Municipality.of(code, name);
    }

    private void addNameIfPresent(HashMap<Language, String> nameValues, Language language, String value) {
        if (value != null && !value.isBlank()) {
            nameValues.put(language, value);
        }
    }
}
