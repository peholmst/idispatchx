package net.pkhapps.idispatchx.gis.importer.db;

import net.pkhapps.idispatchx.gis.importer.parser.model.OsoitepisteFeature;
import net.pkhapps.idispatchx.gis.importer.transform.CoordinateTransformer;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static net.pkhapps.idispatchx.gis.database.jooq.tables.AddressPoint.ADDRESS_POINT;
import static net.pkhapps.idispatchx.gis.importer.db.PostGisDsl.*;

/**
 * Imports Osoitepiste (address point) features into the {@code gis.address_point} table.
 * Accumulates features in a batch and flushes every 1000 rows.
 */
public final class AddressPointImporter {

    private static final Logger LOG = LoggerFactory.getLogger(AddressPointImporter.class);
    private static final int BATCH_SIZE = 1000;

    private final CoordinateTransformer transformer;
    private final List<OsoitepisteFeature> batch = new ArrayList<>(BATCH_SIZE);
    private int totalCount;

    public AddressPointImporter(DSLContext dsl, CoordinateTransformer transformer) {
        this.transformer = transformer;
    }

    /**
     * Adds a feature to the batch. Does not perform any database operations.
     * Call {@link #flush(DSLContext)} to write accumulated features.
     */
    public void upsert(OsoitepisteFeature feature) {
        batch.add(feature);
    }

    /**
     * Deletes an address point by its GML gid.
     *
     * @param tx the transactional DSLContext
     */
    public void delete(DSLContext tx, long gid) {
        tx.deleteFrom(ADDRESS_POINT)
                .where(ADDRESS_POINT.ID.eq(gid))
                .execute();
    }

    /**
     * Truncates the address_point table for full import mode.
     *
     * @param tx the transactional DSLContext
     */
    public void truncate(DSLContext tx) {
        tx.truncate(ADDRESS_POINT).execute();
        LOG.info("Truncated address_point table");
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

            tx.insertInto(ADDRESS_POINT)
                    .set(ADDRESS_POINT.ID, feature.gid())
                    .set(ADDRESS_POINT.NUMBER, feature.numero())
                    .set(ADDRESS_POINT.NAME_FI, feature.nameFi())
                    .set(ADDRESS_POINT.NAME_SV, feature.nameSv())
                    .set(ADDRESS_POINT.NAME_SMN, feature.nameSmn())
                    .set(ADDRESS_POINT.NAME_SMS, feature.nameSms())
                    .set(ADDRESS_POINT.NAME_SME, feature.nameSme())
                    .set(ADDRESS_POINT.MUNICIPALITY_CODE, feature.kuntatunnus())
                    .set(ADDRESS_POINT.LOCATION, location)
                    .onConflict(ADDRESS_POINT.ID)
                    .doUpdate()
                    .set(ADDRESS_POINT.NUMBER, feature.numero())
                    .set(ADDRESS_POINT.NAME_FI, feature.nameFi())
                    .set(ADDRESS_POINT.NAME_SV, feature.nameSv())
                    .set(ADDRESS_POINT.NAME_SMN, feature.nameSmn())
                    .set(ADDRESS_POINT.NAME_SMS, feature.nameSms())
                    .set(ADDRESS_POINT.NAME_SME, feature.nameSme())
                    .set(ADDRESS_POINT.MUNICIPALITY_CODE, feature.kuntatunnus())
                    .set(ADDRESS_POINT.LOCATION, location)
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
