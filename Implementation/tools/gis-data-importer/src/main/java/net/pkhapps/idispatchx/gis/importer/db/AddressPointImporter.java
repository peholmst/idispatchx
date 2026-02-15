package net.pkhapps.idispatchx.gis.importer.db;

import net.pkhapps.idispatchx.gis.importer.parser.model.OsoitepisteFeature;
import net.pkhapps.idispatchx.gis.importer.transform.CoordinateTransformer;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static net.pkhapps.idispatchx.gis.database.jooq.tables.AddressPoint.ADDRESS_POINT;

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
            var pointWkt = "POINT(" + point[1] + " " + point[0] + ")";

            tx.execute("""
                            INSERT INTO gis.address_point (id, number, name_fi, name_sv, name_smn, name_sms, name_sme, municipality_code, location, imported_at)
                            VALUES ({0}, {1}, {2}, {3}, {4}, {5}, {6}, {7}, ST_SetSRID(ST_GeomFromText({8}), 4326), NOW())
                            ON CONFLICT (id) DO UPDATE SET
                                number = EXCLUDED.number,
                                name_fi = EXCLUDED.name_fi,
                                name_sv = EXCLUDED.name_sv,
                                name_smn = EXCLUDED.name_smn,
                                name_sms = EXCLUDED.name_sms,
                                name_sme = EXCLUDED.name_sme,
                                municipality_code = EXCLUDED.municipality_code,
                                location = EXCLUDED.location,
                                imported_at = EXCLUDED.imported_at
                            """,
                    DSL.val(feature.gid()),
                    DSL.val(feature.numero()),
                    DSL.val(feature.nameFi()),
                    DSL.val(feature.nameSv()),
                    DSL.val(feature.nameSmn()),
                    DSL.val(feature.nameSms()),
                    DSL.val(feature.nameSme()),
                    DSL.val(feature.kuntatunnus()),
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
