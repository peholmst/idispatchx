package net.pkhapps.idispatchx.gis.importer.db;

import net.pkhapps.idispatchx.gis.importer.parser.model.TieviivaFeature;
import net.pkhapps.idispatchx.gis.importer.transform.CoordinateTransformer;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static net.pkhapps.idispatchx.gis.database.jooq.tables.RoadSegment.ROAD_SEGMENT;

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

    public RoadSegmentImporter(DSLContext dsl, CoordinateTransformer transformer) {
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
            var lineWkt = buildLineStringWkt(coords);

            tx.execute("""
                            INSERT INTO gis.road_segment (id, road_class, surface_type, administrative_class, one_way, name_fi, name_sv, name_smn, name_sms, name_sme, min_address_left, max_address_left, min_address_right, max_address_right, municipality_code, geometry, imported_at)
                            VALUES ({0}, {1}, {2}, {3}, {4}, {5}, {6}, {7}, {8}, {9}, {10}, {11}, {12}, {13}, {14}, ST_SetSRID(ST_GeomFromText({15}), 4326), NOW())
                            ON CONFLICT (id) DO UPDATE SET
                                road_class = EXCLUDED.road_class,
                                surface_type = EXCLUDED.surface_type,
                                administrative_class = EXCLUDED.administrative_class,
                                one_way = EXCLUDED.one_way,
                                name_fi = EXCLUDED.name_fi,
                                name_sv = EXCLUDED.name_sv,
                                name_smn = EXCLUDED.name_smn,
                                name_sms = EXCLUDED.name_sms,
                                name_sme = EXCLUDED.name_sme,
                                min_address_left = EXCLUDED.min_address_left,
                                max_address_left = EXCLUDED.max_address_left,
                                min_address_right = EXCLUDED.min_address_right,
                                max_address_right = EXCLUDED.max_address_right,
                                municipality_code = EXCLUDED.municipality_code,
                                geometry = EXCLUDED.geometry,
                                imported_at = EXCLUDED.imported_at
                            """,
                    DSL.val(feature.gid()),
                    DSL.val(feature.kohdeluokka()),
                    DSL.val((short) feature.paallyste()),
                    DSL.val(feature.hallinnollinenLuokka() != null ? feature.hallinnollinenLuokka().shortValue() : null),
                    DSL.val((short) feature.yksisuuntaisuus()),
                    DSL.val(feature.nameFi()),
                    DSL.val(feature.nameSv()),
                    DSL.val(feature.nameSmn()),
                    DSL.val(feature.nameSms()),
                    DSL.val(feature.nameSme()),
                    DSL.val(feature.minAddressLeft()),
                    DSL.val(feature.maxAddressLeft()),
                    DSL.val(feature.minAddressRight()),
                    DSL.val(feature.maxAddressRight()),
                    DSL.val(feature.kuntatunnus()),
                    DSL.val(lineWkt));
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

    private static String buildLineStringWkt(double[][] coords) {
        var sb = new StringBuilder("LINESTRING(");
        for (int i = 0; i < coords.length; i++) {
            if (i > 0) sb.append(", ");
            // WKT uses longitude latitude order
            sb.append(coords[i][1]).append(' ').append(coords[i][0]);
        }
        sb.append(')');
        return sb.toString();
    }
}
