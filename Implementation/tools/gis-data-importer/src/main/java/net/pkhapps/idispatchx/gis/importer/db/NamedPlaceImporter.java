package net.pkhapps.idispatchx.gis.importer.db;

import net.pkhapps.idispatchx.gis.importer.parser.model.PaikannimiFeature;
import net.pkhapps.idispatchx.gis.importer.transform.CoordinateTransformer;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static net.pkhapps.idispatchx.gis.database.jooq.tables.Municipality.MUNICIPALITY;
import static net.pkhapps.idispatchx.gis.database.jooq.tables.NamedPlace.NAMED_PLACE;
import static net.pkhapps.idispatchx.gis.importer.db.PostGisDsl.*;

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
            var location = stGeomFromText(pointWkt(point[0], point[1]), 4326);

            // Resolve municipality via point-in-polygon
            var resolvedCode = tx.select(MUNICIPALITY.MUNICIPALITY_CODE)
                    .from(MUNICIPALITY)
                    .where(stContains(MUNICIPALITY.BOUNDARY, location))
                    .limit(1)
                    .fetchOne(MUNICIPALITY.MUNICIPALITY_CODE);

            tx.insertInto(NAMED_PLACE)
                    .set(NAMED_PLACE.ID, feature.gid())
                    .set(NAMED_PLACE.NAME, feature.teksti())
                    .set(NAMED_PLACE.LANGUAGE, feature.kieli())
                    .set(NAMED_PLACE.PLACE_CLASS, feature.kohdeluokka())
                    .set(NAMED_PLACE.KARTTANIMI_ID, feature.karttanimiId())
                    .set(NAMED_PLACE.MUNICIPALITY_CODE, resolvedCode)
                    .set(NAMED_PLACE.LOCATION, location)
                    .onConflict(NAMED_PLACE.ID)
                    .doUpdate()
                    .set(NAMED_PLACE.NAME, feature.teksti())
                    .set(NAMED_PLACE.LANGUAGE, feature.kieli())
                    .set(NAMED_PLACE.PLACE_CLASS, feature.kohdeluokka())
                    .set(NAMED_PLACE.KARTTANIMI_ID, feature.karttanimiId())
                    .set(NAMED_PLACE.MUNICIPALITY_CODE, resolvedCode)
                    .set(NAMED_PLACE.LOCATION, location)
                    .execute();
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
