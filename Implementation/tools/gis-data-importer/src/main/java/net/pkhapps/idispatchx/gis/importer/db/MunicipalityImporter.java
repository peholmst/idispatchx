package net.pkhapps.idispatchx.gis.importer.db;

import net.pkhapps.idispatchx.gis.importer.parser.model.KuntaFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.MunicipalityEntry;
import net.pkhapps.idispatchx.gis.importer.transform.CoordinateTransformer;
import org.jooq.DSLContext;
import org.jooq.Geometry;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static net.pkhapps.idispatchx.gis.database.jooq.tables.Municipality.MUNICIPALITY;
import static net.pkhapps.idispatchx.gis.importer.db.PostGisDsl.*;

/**
 * Imports municipality names from JSON and boundary polygons from GML Kunta features
 * into the {@code gis.municipality} table.
 */
public final class MunicipalityImporter {

    private static final Logger LOG = LoggerFactory.getLogger(MunicipalityImporter.class);

    private final DSLContext dsl;
    private final CoordinateTransformer transformer;

    public MunicipalityImporter(DSLContext dsl, CoordinateTransformer transformer) {
        this.dsl = dsl;
        this.transformer = transformer;
    }

    /**
     * Imports municipality names from parsed JSON entries. Uses UPSERT to preserve existing boundaries.
     *
     * @return the number of entries imported
     */
    public int importNames(List<MunicipalityEntry> entries) {
        int count = 0;
        for (var entry : entries) {
            dsl.insertInto(MUNICIPALITY)
                    .set(MUNICIPALITY.MUNICIPALITY_CODE, entry.code())
                    .set(MUNICIPALITY.NAME_FI, entry.nameFi())
                    .set(MUNICIPALITY.NAME_SV, entry.nameSv())
                    .set(MUNICIPALITY.NAME_SMN, entry.nameSmn())
                    .set(MUNICIPALITY.NAME_SMS, entry.nameSms())
                    .set(MUNICIPALITY.NAME_SME, entry.nameSme())
                    .onConflict(MUNICIPALITY.MUNICIPALITY_CODE)
                    .doUpdate()
                    .set(MUNICIPALITY.NAME_FI, entry.nameFi())
                    .set(MUNICIPALITY.NAME_SV, entry.nameSv())
                    .set(MUNICIPALITY.NAME_SMN, entry.nameSmn())
                    .set(MUNICIPALITY.NAME_SMS, entry.nameSms())
                    .set(MUNICIPALITY.NAME_SME, entry.nameSme())
                    .execute();
            count++;
        }
        LOG.info("Imported {} municipality names", count);
        return count;
    }

    /**
     * Imports a municipality boundary polygon from a GML Kunta feature.
     * Merges with existing boundary using ST_Union for multi-sheet support.
     *
     * @param tx the transactional DSLContext
     */
    public void importBoundary(DSLContext tx, KuntaFeature feature) {
        var coords = transformer.transformPolygon(feature.polygonCoordinates());
        var wkt = polygonWkt(coords);
        var geom = stMulti(stGeomFromText(wkt, 4326));

        // Reference the EXCLUDED row's boundary for the ST_Union merge
        var excludedBoundary = DSL.field(DSL.name("excluded", "boundary"), SQLDataType.GEOMETRY);

        tx.insertInto(MUNICIPALITY)
                .set(MUNICIPALITY.MUNICIPALITY_CODE, feature.kuntatunnus())
                .set(MUNICIPALITY.BOUNDARY, geom)
                .onConflict(MUNICIPALITY.MUNICIPALITY_CODE)
                .doUpdate()
                .set(MUNICIPALITY.BOUNDARY,
                        stMulti(DSL.coalesce(
                                stUnion(MUNICIPALITY.BOUNDARY, excludedBoundary),
                                excludedBoundary)))
                .execute();
    }

    /**
     * Deletes a municipality by its municipality code (for Kunta features with loppupvm).
     *
     * @param tx the transactional DSLContext
     */
    public void deleteByCode(DSLContext tx, String municipalityCode) {
        tx.deleteFrom(MUNICIPALITY)
                .where(MUNICIPALITY.MUNICIPALITY_CODE.eq(municipalityCode))
                .execute();
    }

    /**
     * Resets all municipality boundaries to NULL for full import mode.
     *
     * @param tx the transactional DSLContext
     */
    public void truncateBoundaries(DSLContext tx) {
        tx.update(MUNICIPALITY)
                .setNull(MUNICIPALITY.BOUNDARY)
                .execute();
        LOG.info("Reset all municipality boundaries to NULL");
    }
}
