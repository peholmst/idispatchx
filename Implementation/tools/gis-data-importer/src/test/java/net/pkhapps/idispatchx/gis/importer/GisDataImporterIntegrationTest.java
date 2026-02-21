package net.pkhapps.idispatchx.gis.importer;

import net.pkhapps.idispatchx.gis.importer.parser.FeatureVisitor;
import net.pkhapps.idispatchx.gis.importer.parser.MunicipalityJsonParser;
import net.pkhapps.idispatchx.gis.importer.parser.NlsGmlParser;
import net.pkhapps.idispatchx.gis.importer.parser.model.KuntaFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.OsoitepisteFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.PaikannimiFeature;
import net.pkhapps.idispatchx.gis.importer.parser.model.TieviivaFeature;
import net.pkhapps.idispatchx.gis.importer.transform.CoordinateTransformer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests using sample data files.
 * These tests verify coordinate transformation on actual parsed GML features
 * and municipality JSON parsing with the real data file.
 * Tests are skipped if sample data files are not present.
 */
class GisDataImporterIntegrationTest {

    private static final Path SAMPLE_GML = Path.of("../../../SampleData/L3311R.xml");
    private static final Path SAMPLE_JSON = Path.of("../../../SampleData/codelist_kunta_1_20250101.json");

    @Test
    void coordinateTransform_allSampleFeatures_withinFinlandBounds() throws Exception {
        Assumptions.assumeTrue(Files.exists(SAMPLE_GML),
                "Sample GML not found: " + SAMPLE_GML.toAbsolutePath());

        var transformer = new CoordinateTransformer();
        var visitor = new CollectingVisitor();

        try (var input = new FileInputStream(SAMPLE_GML.toFile())) {
            NlsGmlParser.parse(input, visitor);
        }

        // Transform all Kunta polygons
        for (var kunta : visitor.kunnat) {
            var coords = transformer.transformPolygon(kunta.polygonCoordinates());
            for (var point : coords) {
                assertValidFinlandCoordinate(point, "Kunta gid=" + kunta.gid());
            }
        }

        // Transform all Osoitepiste points
        for (var op : visitor.osoitepisteet) {
            var point = transformer.transformPoint(op.pointEasting(), op.pointNorthing());
            assertValidFinlandCoordinate(point, "Osoitepiste gid=" + op.gid());
        }

        // Transform all Tieviiva linestrings
        for (var tv : visitor.tieviivat) {
            var coords = transformer.transformLineString(tv.lineCoordinates());
            for (var point : coords) {
                assertValidFinlandCoordinate(point, "Tieviiva gid=" + tv.gid());
            }
        }

        // Transform all Paikannimi points
        for (var pn : visitor.paikannimet) {
            var point = transformer.transformPoint(pn.pointEasting(), pn.pointNorthing());
            assertValidFinlandCoordinate(point, "Paikannimi gid=" + pn.gid());
        }
    }

    @Test
    void municipalityJson_sampleFile_spotCheckEntries() throws Exception {
        Assumptions.assumeTrue(Files.exists(SAMPLE_JSON),
                "Sample JSON not found: " + SAMPLE_JSON.toAbsolutePath());

        try (var input = new FileInputStream(SAMPLE_JSON.toFile())) {
            var entries = MunicipalityJsonParser.parse(input);

            // Verify both municipalities from the GML sample area exist
            var parainen = entries.stream().filter(e -> "445".equals(e.code())).findFirst().orElseThrow();
            assertEquals("Parainen", parainen.nameFi());
            assertEquals("Pargas", parainen.nameSv());

            var kemionsaari = entries.stream().filter(e -> "322".equals(e.code())).findFirst().orElseThrow();
            assertEquals("Kemiönsaari", kemionsaari.nameFi());
            assertEquals("Kimitoön", kemionsaari.nameSv());

            // All entries should have codes and at least a Finnish name
            for (var entry : entries) {
                assertNotNull(entry.code(), "Municipality code should not be null");
                assertFalse(entry.code().isBlank(), "Municipality code should not be blank");
            }
        }
    }

    private static void assertValidFinlandCoordinate(double[] point, String context) {
        assertTrue(point[0] >= 58.84 && point[0] <= 70.09,
                context + ": latitude " + point[0] + " outside Finland bounds");
        assertTrue(point[1] >= 19.08 && point[1] <= 31.59,
                context + ": longitude " + point[1] + " outside Finland bounds");
    }

    private static class CollectingVisitor implements FeatureVisitor {
        final List<KuntaFeature> kunnat = new ArrayList<>();
        final List<TieviivaFeature> tieviivat = new ArrayList<>();
        final List<OsoitepisteFeature> osoitepisteet = new ArrayList<>();
        final List<PaikannimiFeature> paikannimet = new ArrayList<>();

        @Override
        public void onKunta(KuntaFeature feature) {
            kunnat.add(feature);
        }

        @Override
        public void onTieviiva(TieviivaFeature feature) {
            tieviivat.add(feature);
        }

        @Override
        public void onOsoitepiste(OsoitepisteFeature feature) {
            osoitepisteet.add(feature);
        }

        @Override
        public void onPaikannimi(PaikannimiFeature feature) {
            paikannimet.add(feature);
        }
    }
}
