package net.pkhapps.idispatchx.gis.server.api.wmts;

import net.pkhapps.idispatchx.gis.server.model.TileCoordinates;
import net.pkhapps.idispatchx.gis.server.model.TileLayer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CapabilitiesGeneratorTest {

    @Test
    void generatesValidXmlDocument() {
        var layers = Map.of("terrain", new TileLayer("terrain", Set.of(10, 14)));
        var generator = new CapabilitiesGenerator(layers);
        var xml = generator.getCapabilitiesXml();

        assertNotNull(xml);
        assertTrue(xml.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("<Capabilities"));
        assertTrue(xml.contains("version=\"1.0.0\""));
    }

    @Test
    void containsLayerIdentifier() {
        var layers = Map.of("terrain", new TileLayer("terrain", Set.of(10)));
        var generator = new CapabilitiesGenerator(layers);
        var xml = generator.getCapabilitiesXml();

        assertTrue(xml.contains("terrain"));
    }

    @Test
    void containsMultipleLayers() {
        var layers = Map.of(
                "terrain", new TileLayer("terrain", Set.of(10)),
                "roads", new TileLayer("roads", Set.of(12))
        );
        var generator = new CapabilitiesGenerator(layers);
        var xml = generator.getCapabilitiesXml();

        assertTrue(xml.contains("terrain"));
        assertTrue(xml.contains("roads"));
    }

    @Test
    void contains16ZoomLevels() {
        var layers = Map.of("terrain", new TileLayer("terrain", Set.of(10)));
        var generator = new CapabilitiesGenerator(layers);
        var xml = generator.getCapabilitiesXml();

        // Count occurrences of TileMatrix elements
        int count = 0;
        int idx = 0;
        while ((idx = xml.indexOf("<TileMatrix>", idx)) != -1) {
            count++;
            idx++;
        }
        assertEquals(16, count);
    }

    @Test
    void containsCorrectTileMatrixSet() {
        var layers = Map.of("terrain", new TileLayer("terrain", Set.of(10)));
        var generator = new CapabilitiesGenerator(layers);
        var xml = generator.getCapabilitiesXml();

        assertTrue(xml.contains("ETRS-TM35FIN"));
        assertTrue(xml.contains("EPSG::3067"));
    }

    @Test
    void containsCorrectTopLeftCorner() {
        var layers = Map.of("terrain", new TileLayer("terrain", Set.of(10)));
        var generator = new CapabilitiesGenerator(layers);
        var xml = generator.getCapabilitiesXml();

        assertTrue(xml.contains("-548576.0 8388608.0"), "Should contain correct TopLeftCorner");
    }

    @Test
    void containsCorrectTileSize() {
        var layers = Map.of("terrain", new TileLayer("terrain", Set.of(10)));
        var generator = new CapabilitiesGenerator(layers);
        var xml = generator.getCapabilitiesXml();

        assertTrue(xml.contains("<TileWidth>256</TileWidth>"));
        assertTrue(xml.contains("<TileHeight>256</TileHeight>"));
    }

    @Test
    void scaleDenominatorsFollowJhs180() {
        var layers = Map.of("terrain", new TileLayer("terrain", Set.of(10)));
        var generator = new CapabilitiesGenerator(layers);
        var xml = generator.getCapabilitiesXml();

        // Zoom 0: 29257143.0 / 2^0 = 29257143 (no fractional part)
        assertTrue(xml.contains("29257143"), "Should contain scale denominator for zoom 0");

        // Zoom 1: 29257143.0 / 2^1 = 14628571.5
        assertTrue(xml.contains("14628571.5"), "Should contain scale denominator for zoom 1");
    }

    @Test
    void matrixDimensionsFollowJhs180() {
        var layers = Map.of("terrain", new TileLayer("terrain", Set.of(10)));
        var generator = new CapabilitiesGenerator(layers);
        var xml = generator.getCapabilitiesXml();

        // Zoom 0: 1x1
        assertTrue(xml.contains("<MatrixWidth>1</MatrixWidth>"), "Zoom 0 should have MatrixWidth=1");

        // Zoom 1: 2x2
        assertTrue(xml.contains("<MatrixWidth>2</MatrixWidth>"), "Zoom 1 should have MatrixWidth=2");

        // Zoom 15: 32768x32768
        int zoom15Dim = TileCoordinates.matrixDimensionForZoom(15);
        assertEquals(32768, zoom15Dim);
        assertTrue(xml.contains("<MatrixWidth>32768</MatrixWidth>"), "Zoom 15 should have MatrixWidth=32768");
    }

    @Test
    void xmlEscapingForSpecialCharactersInLayerName() {
        // Layer name with XML special chars — test that it gets escaped
        var layers = Map.of("terrain&layer", new TileLayer("terrain&layer", Set.of(10)));
        var generator = new CapabilitiesGenerator(layers);
        var xml = generator.getCapabilitiesXml();

        assertTrue(xml.contains("terrain&amp;layer"), "Ampersand should be XML-escaped");
        assertFalse(xml.contains("terrain&layer"), "Raw ampersand should not appear in XML content");
    }

    @Test
    void resourceUrlTemplateContainsLayer() {
        var layers = Map.of("terrain", new TileLayer("terrain", Set.of(10)));
        var generator = new CapabilitiesGenerator(layers);
        var xml = generator.getCapabilitiesXml();

        assertTrue(xml.contains("ResourceURL"));
        assertTrue(xml.contains("/wmts/terrain/ETRS-TM35FIN/{TileMatrix}/{TileRow}/{TileCol}.png"));
    }

    @Test
    void capabilitiesXmlIsCached() {
        var layers = Map.of("terrain", new TileLayer("terrain", Set.of(10)));
        var generator = new CapabilitiesGenerator(layers);

        // Should return the same string instance
        assertSame(generator.getCapabilitiesXml(), generator.getCapabilitiesXml());
    }

    @Test
    void emptyLayersProducesValidXml() {
        var generator = new CapabilitiesGenerator(Map.of());
        var xml = generator.getCapabilitiesXml();

        assertNotNull(xml);
        assertTrue(xml.contains("<Capabilities"));
        assertTrue(xml.contains("</Capabilities>"));
    }
}
