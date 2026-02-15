package net.pkhapps.idispatchx.gis.importer.db;

import net.pkhapps.idispatchx.gis.importer.parser.model.PaikannimiFeature;
import net.pkhapps.idispatchx.gis.importer.transform.CoordinateTransformer;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static net.pkhapps.idispatchx.gis.database.jooq.tables.NamedPlace.NAMED_PLACE;

/**
 * Imports Paikannimi (place name) features into the {@code gis.named_place} table.
 * Resolves municipality by point-in-polygon against {@code gis.municipality} boundaries.
 * Accumulates features in a batch and flushes every 1000 rows.
 */
public final class NamedPlaceImporter {

    private static final Logger LOG = LoggerFactory.getLogger(NamedPlaceImporter.class);
    private static final int BATCH_SIZE = 1000;

    private final CoordinateTransformer transformer;
    private final List<PaikannimiFeature> batch = new ArrayList<>(BATCH_SIZE);
    private int totalCount;

    public NamedPlaceImporter(DSLContext dsl, CoordinateTransformer transformer) {
        this.transformer = transformer;
    }

    /**
     * Adds a feature to the batch. Does not perform any database operations.
     * Call {@link #flush(DSLContext)} to write accumulated features.
     */
    public void upsert(PaikannimiFeature feature) {
        batch.add(feature);
    }

    /**
     * Deletes a named place by its GML gid.
     *
     * @param tx the transactional DSLContext
     */
    public void delete(DSLContext tx, long gid) {
        tx.deleteFrom(NAMED_PLACE)
                .where(NAMED_PLACE.ID.eq(gid))
                .execute();
    }

    /**
     * Truncates the named_place table for full import mode.
     *
     * @param tx the transactional DSLContext
     */
    public void truncate(DSLContext tx) {
        tx.truncate(NAMED_PLACE).execute();
        LOG.info("Truncated named_place table");
    }

    /**
     * Flushes any remaining features in the batch to the database.
     *
     * @param tx the transactional DSLContext
     */
    public void flush(DSLContext tx) {
        if (batch.isEmpty()) return;

        for (var feature : batch) {
            var point = transformer.transformPoint(feature.pointEasting(), feature.pointNorthing());
            var pointWkt = "POINT(" + point[1] + " " + point[0] + ")";

            // Resolve municipality via point-in-polygon
            var municipalityCode = tx.fetchOne("""
                            SELECT municipality_code
                            FROM gis.municipality
                            WHERE ST_Contains(boundary, ST_SetSRID(ST_GeomFromText({0}), 4326))
                            LIMIT 1
                            """,
                    DSL.val(pointWkt));
            String resolvedCode = municipalityCode != null ? municipalityCode.get(0, String.class) : null;

            tx.execute("""
                            INSERT INTO gis.named_place (id, name, language, place_class, karttanimi_id, municipality_code, location, imported_at)
                            VALUES ({0}, {1}, {2}, {3}, {4}, {5}, ST_SetSRID(ST_GeomFromText({6}), 4326), NOW())
                            ON CONFLICT (id) DO UPDATE SET
                                name = EXCLUDED.name,
                                language = EXCLUDED.language,
                                place_class = EXCLUDED.place_class,
                                karttanimi_id = EXCLUDED.karttanimi_id,
                                municipality_code = EXCLUDED.municipality_code,
                                location = EXCLUDED.location,
                                imported_at = EXCLUDED.imported_at
                            """,
                    DSL.val(feature.gid()),
                    DSL.val(feature.teksti()),
                    DSL.val(feature.kieli()),
                    DSL.val(feature.kohdeluokka()),
                    DSL.val(feature.karttanimiId()),
                    DSL.val(resolvedCode),
                    DSL.val(pointWkt));
        }

        totalCount += batch.size();
        batch.clear();
    }

    /**
     * Returns the total number of features upserted so far.
     */
    public int totalCount() {
        return totalCount;
    }

    /**
     * Returns the current batch size (for triggering flushes externally).
     */
    public int batchSize() {
        return batch.size();
    }
}
