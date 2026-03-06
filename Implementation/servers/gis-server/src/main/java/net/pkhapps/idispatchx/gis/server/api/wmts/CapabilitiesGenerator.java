package net.pkhapps.idispatchx.gis.server.api.wmts;

import net.pkhapps.idispatchx.gis.server.model.TileCoordinates;
import net.pkhapps.idispatchx.gis.server.model.TileLayer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Generates the WMTS 1.0.0 GetCapabilities XML document.
 * <p>
 * The XML document is generated once at construction time and cached as a string.
 * <p>
 * Uses the ETRS-TM35FIN tile matrix set (JHS 180) with:
 * <ul>
 *   <li>Zoom levels 0-15</li>
 *   <li>Top-left corner: -548576.0 8388608.0</li>
 *   <li>Scale denominators following JHS 180</li>
 *   <li>256×256 pixel tiles</li>
 * </ul>
 */
public final class CapabilitiesGenerator {

    private static final String TILE_MATRIX_SET = "ETRS-TM35FIN";
    private static final double BASE_SCALE_DENOMINATOR = 29257143.0;
    private static final double TOP_LEFT_X = -548576.0;
    private static final double TOP_LEFT_Y = 8388608.0;
    private static final int TILE_SIZE = 256;

    private final String capabilitiesXml;

    /**
     * Creates a new capabilities generator and generates the XML.
     *
     * @param layers the map of available tile layers
     */
    public CapabilitiesGenerator(Map<String, TileLayer> layers) {
        Objects.requireNonNull(layers, "layers must not be null");
        this.capabilitiesXml = generateXml(layers);
    }

    /**
     * Returns the pre-generated WMTS capabilities XML.
     *
     * @return the capabilities XML document as a string
     */
    public String getCapabilitiesXml() {
        return capabilitiesXml;
    }

    private String generateXml(Map<String, TileLayer> layers) {
        var sb = new StringBuilder();

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<Capabilities xmlns=\"http://www.opengis.net/wmts/1.0\"\n");
        sb.append("              xmlns:ows=\"http://www.opengis.net/ows/1.1\"\n");
        sb.append("              xmlns:xlink=\"http://www.w3.org/1999/xlink\"\n");
        sb.append("              version=\"1.0.0\">\n");

        // ServiceIdentification
        sb.append("  <ows:ServiceIdentification>\n");
        sb.append("    <ows:Title>iDispatchX GIS Server</ows:Title>\n");
        sb.append("    <ows:ServiceType>OGC WMTS</ows:ServiceType>\n");
        sb.append("    <ows:ServiceTypeVersion>1.0.0</ows:ServiceTypeVersion>\n");
        sb.append("  </ows:ServiceIdentification>\n");

        // Contents
        sb.append("  <Contents>\n");

        // Layers
        for (var layer : layers.values()) {
            appendLayer(sb, layer);
        }

        // TileMatrixSet
        appendTileMatrixSet(sb);

        sb.append("  </Contents>\n");
        sb.append("</Capabilities>\n");

        return sb.toString();
    }

    private void appendLayer(StringBuilder sb, TileLayer layer) {
        var layerName = escapeXml(layer.name());

        sb.append("    <Layer>\n");
        sb.append("      <ows:Title>").append(layerName).append("</ows:Title>\n");
        sb.append("      <ows:Identifier>").append(layerName).append("</ows:Identifier>\n");
        sb.append("      <Style isDefault=\"true\">\n");
        sb.append("        <ows:Identifier>default</ows:Identifier>\n");
        sb.append("      </Style>\n");
        sb.append("      <Format>image/png</Format>\n");
        sb.append("      <TileMatrixSetLink>\n");
        sb.append("        <TileMatrixSet>").append(TILE_MATRIX_SET).append("</TileMatrixSet>\n");
        sb.append("      </TileMatrixSetLink>\n");
        sb.append("      <ResourceURL format=\"image/png\" resourceType=\"tile\"\n");
        sb.append("                   template=\"/wmts/").append(layerName)
                .append("/").append(TILE_MATRIX_SET)
                .append("/{TileMatrix}/{TileRow}/{TileCol}.png\"/>\n");
        sb.append("    </Layer>\n");
    }

    private void appendTileMatrixSet(StringBuilder sb) {
        sb.append("    <TileMatrixSet>\n");
        sb.append("      <ows:Identifier>").append(TILE_MATRIX_SET).append("</ows:Identifier>\n");
        sb.append("      <ows:SupportedCRS>urn:ogc:def:crs:EPSG::3067</ows:SupportedCRS>\n");

        for (int zoom = TileCoordinates.MIN_ZOOM; zoom <= TileCoordinates.MAX_ZOOM; zoom++) {
            appendTileMatrix(sb, zoom);
        }

        sb.append("    </TileMatrixSet>\n");
    }

    private void appendTileMatrix(StringBuilder sb, int zoom) {
        var scaleDenominator = BigDecimal.valueOf(BASE_SCALE_DENOMINATOR / Math.pow(2, zoom)).toPlainString();
        var matrixDimension = TileCoordinates.matrixDimensionForZoom(zoom);

        sb.append("      <TileMatrix>\n");
        sb.append("        <ows:Identifier>").append(zoom).append("</ows:Identifier>\n");
        sb.append("        <ScaleDenominator>").append(scaleDenominator).append("</ScaleDenominator>\n");
        sb.append("        <TopLeftCorner>").append(TOP_LEFT_X).append(" ").append(TOP_LEFT_Y).append("</TopLeftCorner>\n");
        sb.append("        <TileWidth>").append(TILE_SIZE).append("</TileWidth>\n");
        sb.append("        <TileHeight>").append(TILE_SIZE).append("</TileHeight>\n");
        sb.append("        <MatrixWidth>").append(matrixDimension).append("</MatrixWidth>\n");
        sb.append("        <MatrixHeight>").append(matrixDimension).append("</MatrixHeight>\n");
        sb.append("      </TileMatrix>\n");
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
