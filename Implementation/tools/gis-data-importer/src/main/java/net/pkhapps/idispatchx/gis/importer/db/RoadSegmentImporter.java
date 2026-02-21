package net.pkhapps.idispatchx.gis.importer.db;

import net.pkhapps.idispatchx.gis.importer.parser.model.TieviivaFeature;
import net.pkhapps.idispatchx.gis.importer.transform.CoordinateTransformer;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static net.pkhapps.idispatchx.gis.database.jooq.tables.RoadSegment.ROAD_SEGMENT;
import static net.pkhapps.idispatchx.gis.importer.db.PostGisDsl.*;

/**
 * Imports Tieviiva (road segment) features into the {@code gis.road_segment} table.
 * Accumulates features in a batch and flushes every 1000 rows.
 */
public final class RoadSegmentImporter {

    private static final Logger LOG = LoggerFactory.getLogger(RoadSegmentImporter.class);
    private static final int BATCH_SIZE = 1000;

    private final CoordinateTransformer transformer;
    private final List<TieviivaFeature> batch = new ArrayList<>(BATCH_SIZE);
    private int totalCount;

    public RoadSegmentImporter(CoordinateTransformer transformer) {
        this.transformer = transformer;
    }

    /**
     * Adds a feature to the batch. Does not perform any database operations.
     * Call {@link #flush(DSLContext)} to write accumulated features.
     */
    public void upsert(TieviivaFeature feature) {
        batch.add(feature);
    }

    /**
     * Deletes a road segment by its GML gid.
     *
     * @param tx the transactional DSLContext
     */
    public void delete(DSLContext tx, long gid) {
        tx.deleteFrom(ROAD_SEGMENT)
                .where(ROAD_SEGMENT.ID.eq(gid))
                .execute();
    }

    /**
     * Truncates the road_segment table for full import mode.
     *
     * @param tx the transactional DSLContext
     */
    public void truncate(DSLContext tx) {
        tx.truncate(ROAD_SEGMENT).execute();
        LOG.info("Truncated road_segment table");
    }

    /**
     * Flushes any remaining features in the batch to the database.
     *
     * @param tx the transactional DSLContext
     */
    public void flush(DSLContext tx) {
        if (batch.isEmpty()) return;

        for (var feature : batch) {
            var coords = transformer.transformLineString(feature.lineCoordinates());
            var geometry = stGeomFromText(lineStringWkt(coords), 4326);

            tx.insertInto(ROAD_SEGMENT)
                    .set(ROAD_SEGMENT.ID, feature.gid())
                    .set(ROAD_SEGMENT.ROAD_CLASS, feature.kohdeluokka())
                    .set(ROAD_SEGMENT.SURFACE_TYPE, (short) feature.paallyste())
                    .set(ROAD_SEGMENT.ADMINISTRATIVE_CLASS,
                            feature.hallinnollinenLuokka() != null ? feature.hallinnollinenLuokka().shortValue() : null)
                    .set(ROAD_SEGMENT.ONE_WAY, (short) feature.yksisuuntaisuus())
                    .set(ROAD_SEGMENT.NAME_FI, feature.nameFi())
                    .set(ROAD_SEGMENT.NAME_SV, feature.nameSv())
                    .set(ROAD_SEGMENT.NAME_SMN, feature.nameSmn())
                    .set(ROAD_SEGMENT.NAME_SMS, feature.nameSms())
                    .set(ROAD_SEGMENT.NAME_SME, feature.nameSme())
                    .set(ROAD_SEGMENT.MIN_ADDRESS_LEFT, feature.minAddressLeft())
                    .set(ROAD_SEGMENT.MAX_ADDRESS_LEFT, feature.maxAddressLeft())
                    .set(ROAD_SEGMENT.MIN_ADDRESS_RIGHT, feature.minAddressRight())
                    .set(ROAD_SEGMENT.MAX_ADDRESS_RIGHT, feature.maxAddressRight())
                    .set(ROAD_SEGMENT.MUNICIPALITY_CODE, feature.kuntatunnus())
                    .set(ROAD_SEGMENT.GEOMETRY, geometry)
                    .onConflict(ROAD_SEGMENT.ID)
                    .doUpdate()
                    .set(ROAD_SEGMENT.ROAD_CLASS, feature.kohdeluokka())
                    .set(ROAD_SEGMENT.SURFACE_TYPE, (short) feature.paallyste())
                    .set(ROAD_SEGMENT.ADMINISTRATIVE_CLASS,
                            feature.hallinnollinenLuokka() != null ? feature.hallinnollinenLuokka().shortValue() : null)
                    .set(ROAD_SEGMENT.ONE_WAY, (short) feature.yksisuuntaisuus())
                    .set(ROAD_SEGMENT.NAME_FI, feature.nameFi())
                    .set(ROAD_SEGMENT.NAME_SV, feature.nameSv())
                    .set(ROAD_SEGMENT.NAME_SMN, feature.nameSmn())
                    .set(ROAD_SEGMENT.NAME_SMS, feature.nameSms())
                    .set(ROAD_SEGMENT.NAME_SME, feature.nameSme())
                    .set(ROAD_SEGMENT.MIN_ADDRESS_LEFT, feature.minAddressLeft())
                    .set(ROAD_SEGMENT.MAX_ADDRESS_LEFT, feature.maxAddressLeft())
                    .set(ROAD_SEGMENT.MIN_ADDRESS_RIGHT, feature.minAddressRight())
                    .set(ROAD_SEGMENT.MAX_ADDRESS_RIGHT, feature.maxAddressRight())
                    .set(ROAD_SEGMENT.MUNICIPALITY_CODE, feature.kuntatunnus())
                    .set(ROAD_SEGMENT.GEOMETRY, geometry)
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
