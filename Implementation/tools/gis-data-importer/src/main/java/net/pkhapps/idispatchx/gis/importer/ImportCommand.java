package net.pkhapps.idispatchx.gis.importer;

import net.pkhapps.idispatchx.gis.importer.db.AddressPointImporter;
import net.pkhapps.idispatchx.gis.importer.db.MunicipalityImporter;
import net.pkhapps.idispatchx.gis.importer.db.NamedPlaceImporter;
import net.pkhapps.idispatchx.gis.importer.db.RoadSegmentImporter;
import net.pkhapps.idispatchx.gis.importer.parser.FeatureType;
import net.pkhapps.idispatchx.gis.importer.parser.FeatureVisitor;
import net.pkhapps.idispatchx.gis.importer.parser.MunicipalityJsonParser;
import net.pkhapps.idispatchx.gis.importer.parser.NlsGmlParser;
import net.pkhapps.idispatchx.gis.importer.parser.model.KuntaFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.OsoitepisteFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.PaikannimiFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.TieviivaFeature;
import net.pkhapps.idispatchx.gis.importer.transform.CoordinateTransformer;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static net.pkhapps.idispatchx.gis.database.jooq.tables.ImportLog.IMPORT_LOG;

/**
 * Orchestrates the 3-pass GIS data import pipeline.
 * <ol>
 *   <li>Pass 1: Municipality JSON — names</li>
 *   <li>Pass 2: GML Kunta features — boundaries</li>
 *   <li>Pass 3: GML Tieviiva/Osoitepiste/Paikannimi — features</li>
 * </ol>
 */
public final class ImportCommand {

    private static final Logger LOG = LoggerFactory.getLogger(ImportCommand.class);

    private final DSLContext dsl;
    private final boolean truncate;
    private final Set<FeatureType> featureFilter;

    private final MunicipalityImporter municipalityImporter;
    private final AddressPointImporter addressPointImporter;
    private final RoadSegmentImporter roadSegmentImporter;
    private final NamedPlaceImporter namedPlaceImporter;

    public ImportCommand(DSLContext dsl, CoordinateTransformer transformer, boolean truncate, Set<FeatureType> featureFilter) {
        this.dsl = dsl;
        this.truncate = truncate;
        this.featureFilter = featureFilter;
        this.municipalityImporter = new MunicipalityImporter(dsl, transformer);
        this.addressPointImporter = new AddressPointImporter(dsl, transformer);
        this.roadSegmentImporter = new RoadSegmentImporter(dsl, transformer);
        this.namedPlaceImporter = new NamedPlaceImporter(dsl, transformer);
    }

    /**
     * Pass 1: Import municipality names from JSON.
     */
    public void importMunicipalities(Path jsonFile) throws IOException {
        LOG.info("Pass 1: Importing municipality names from {}", jsonFile.getFileName());
        var startedAt = OffsetDateTime.now();
        try (var input = new FileInputStream(jsonFile.toFile())) {
            var entries = MunicipalityJsonParser.parse(input);
            int count = municipalityImporter.importNames(entries);
            logImport(jsonFile.getFileName().toString(), "municipality_names", count, startedAt);
        }
    }

    /**
     * Passes 2 and 3: Import GML features from all provided files.
     */
    public void importGmlFiles(List<Path> gmlFiles) throws Exception {
        if (truncate) {
            LOG.info("Truncate mode: resetting tables before import");
            if (featureFilter.contains(FeatureType.KUNTA)) {
                municipalityImporter.truncateBoundaries();
            }
            if (featureFilter.contains(FeatureType.OSOITEPISTE)) {
                addressPointImporter.truncate();
            }
            if (featureFilter.contains(FeatureType.TIEVIIVA)) {
                roadSegmentImporter.truncate();
            }
            if (featureFilter.contains(FeatureType.PAIKANNIMI)) {
                namedPlaceImporter.truncate();
            }
        }

        // Pass 2: Kunta boundaries
        if (featureFilter.contains(FeatureType.KUNTA)) {
            LOG.info("Pass 2: Importing Kunta boundaries from {} file(s)", gmlFiles.size());
            for (var file : gmlFiles) {
                var startedAt = OffsetDateTime.now();
                var counter = new int[]{0};
                try (var input = new FileInputStream(file.toFile())) {
                    NlsGmlParser.parse(input, new FeatureVisitor() {
                        @Override
                        public void onKunta(KuntaFeature feature) {
                            if (feature.loppupvm() != null) {
                                municipalityImporter.deleteByCode(feature.kuntatunnus());
                            } else {
                                municipalityImporter.importBoundary(feature);
                            }
                            counter[0]++;
                        }

                        @Override
                        public void onTieviiva(TieviivaFeature feature) {
                        }

                        @Override
                        public void onOsoitepiste(OsoitepisteFeature feature) {
                        }

                        @Override
                        public void onPaikannimi(PaikannimiFeature feature) {
                        }
                    }, EnumSet.of(FeatureType.KUNTA));
                }
                logImport(file.getFileName().toString(), "kunta", counter[0], startedAt);
            }
        }

        // Pass 3: Other features
        var pass3Types = EnumSet.copyOf(featureFilter);
        pass3Types.remove(FeatureType.KUNTA);
        if (!pass3Types.isEmpty()) {
            LOG.info("Pass 3: Importing {} from {} file(s)", pass3Types, gmlFiles.size());
            for (var file : gmlFiles) {
                var startedAt = OffsetDateTime.now();
                var counters = new int[3]; // [tieviiva, osoitepiste, paikannimi]
                try (var input = new FileInputStream(file.toFile())) {
                    NlsGmlParser.parse(input, new FeatureVisitor() {
                        @Override
                        public void onKunta(KuntaFeature feature) {
                        }

                        @Override
                        public void onTieviiva(TieviivaFeature feature) {
                            if (feature.loppupvm() != null) {
                                roadSegmentImporter.delete(feature.gid());
                            } else {
                                roadSegmentImporter.upsert(feature);
                            }
                            counters[0]++;
                        }

                        @Override
                        public void onOsoitepiste(OsoitepisteFeature feature) {
                            if (feature.loppupvm() != null) {
                                addressPointImporter.delete(feature.gid());
                            } else {
                                addressPointImporter.upsert(feature);
                            }
                            counters[1]++;
                        }

                        @Override
                        public void onPaikannimi(PaikannimiFeature feature) {
                            if (feature.loppupvm() != null) {
                                namedPlaceImporter.delete(feature.gid());
                            } else {
                                namedPlaceImporter.upsert(feature);
                            }
                            counters[2]++;
                        }
                    }, pass3Types);
                }

                // Flush remaining batches
                roadSegmentImporter.flush();
                addressPointImporter.flush();
                namedPlaceImporter.flush();

                var fileName = file.getFileName().toString();
                if (counters[0] > 0) logImport(fileName, "tieviiva", counters[0], startedAt);
                if (counters[1] > 0) logImport(fileName, "osoitepiste", counters[1], startedAt);
                if (counters[2] > 0) logImport(fileName, "paikannimi", counters[2], startedAt);
            }
        }

        LOG.info("Import summary: road_segments={}, address_points={}, named_places={}",
                roadSegmentImporter.totalCount(), addressPointImporter.totalCount(), namedPlaceImporter.totalCount());
    }

    private void logImport(String filename, String featureType, int recordCount, OffsetDateTime startedAt) {
        dsl.insertInto(IMPORT_LOG)
                .set(IMPORT_LOG.FILENAME, filename)
                .set(IMPORT_LOG.FEATURE_TYPE, featureType)
                .set(IMPORT_LOG.RECORD_COUNT, recordCount)
                .set(IMPORT_LOG.STARTED_AT, startedAt)
                .set(IMPORT_LOG.COMPLETED_AT, OffsetDateTime.now())
                .execute();
    }
}
