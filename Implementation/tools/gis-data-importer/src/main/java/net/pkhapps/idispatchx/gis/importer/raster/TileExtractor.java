package net.pkhapps.idispatchx.gis.importer.raster;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Extracts 256x256 WMTS tiles from a georeferenced source image.
 * Uses a consumer/callback pattern to avoid holding all tiles in memory.
 */
public final class TileExtractor {

    private TileExtractor() {
    }

    /**
     * Consumer for extracted tiles.
     */
    @FunctionalInterface
    public interface TileConsumer {
        void accept(ExtractedTile tile) throws IOException;
    }

    /**
     * An extracted tile with its coordinate and image data.
     *
     * @param coordinate the tile coordinate in the tile matrix set
     * @param image      the 256x256 ARGB tile image
     */
    public record ExtractedTile(TileMatrixSet.TileCoordinate coordinate, BufferedImage image) {
    }

    /**
     * Extracts tiles from the source image and passes each non-empty tile to the consumer.
     *
     * @param source      the source image
     * @param zoom        the zoom level
     * @param ulX         upper-left corner easting (edge of first pixel)
     * @param ulY         upper-left corner northing (edge of first pixel)
     * @param pixelWidth  pixel width in meters (positive)
     * @param pixelHeight pixel height in meters (negative)
     * @param consumer    consumer for non-empty extracted tiles
     * @return number of tiles passed to the consumer
     * @throws IOException if the consumer throws an IOException
     */
    public static int extract(BufferedImage source, int zoom, double ulX, double ulY,
                              double pixelWidth, double pixelHeight, TileConsumer consumer) throws IOException {
        var srcWidth = source.getWidth();
        var srcHeight = source.getHeight();
        var lrX = ulX + srcWidth * pixelWidth;
        var lrY = ulY + srcHeight * pixelHeight; // pixelHeight is negative, so lrY < ulY
        var absPixelHeight = Math.abs(pixelHeight);

        var tileSpan = TileMatrixSet.tileSpan(zoom);
        var colMin = TileMatrixSet.column(ulX, zoom);
        var colMax = TileMatrixSet.column(lrX, zoom);
        var rowMin = TileMatrixSet.row(ulY, zoom);
        var rowMax = TileMatrixSet.row(lrY, zoom);

        var count = 0;
        for (var row = rowMin; row <= rowMax; row++) {
            for (var col = colMin; col <= colMax; col++) {
                var bounds = TileMatrixSet.tileBounds(zoom, row, col);

                // Compute source pixel region
                var srcX = (bounds.west() - ulX) / pixelWidth;
                var srcY = (ulY - bounds.north()) / absPixelHeight;
                var srcEndX = srcX + tileSpan / pixelWidth;
                var srcEndY = srcY + tileSpan / absPixelHeight;

                // Clamp to source image bounds
                var clampedSrcX = Math.max(0, (int) Math.floor(srcX));
                var clampedSrcY = Math.max(0, (int) Math.floor(srcY));
                var clampedSrcEndX = Math.min(srcWidth, (int) Math.ceil(srcEndX));
                var clampedSrcEndY = Math.min(srcHeight, (int) Math.ceil(srcEndY));

                if (clampedSrcEndX <= clampedSrcX || clampedSrcEndY <= clampedSrcY) {
                    continue;
                }

                // Create 256x256 transparent ARGB tile
                var tile = new BufferedImage(TileMatrixSet.TILE_SIZE, TileMatrixSet.TILE_SIZE,
                        BufferedImage.TYPE_INT_ARGB);
                var g = tile.createGraphics();
                try {
                    // Compute destination pixel positions on the tile
                    var destX = (int) Math.round((clampedSrcX - srcX) * pixelWidth / pixelWidth);
                    var destY = (int) Math.round((clampedSrcY - srcY) * absPixelHeight / absPixelHeight);
                    var destEndX = destX + (clampedSrcEndX - clampedSrcX);
                    var destEndY = destY + (clampedSrcEndY - clampedSrcY);

                    g.drawImage(source,
                            destX, destY, destEndX, destEndY,
                            clampedSrcX, clampedSrcY, clampedSrcEndX, clampedSrcEndY,
                            null);
                } finally {
                    g.dispose();
                }

                if (!isFullyTransparent(tile)) {
                    consumer.accept(new ExtractedTile(new TileMatrixSet.TileCoordinate(zoom, row, col), tile));
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isFullyTransparent(BufferedImage image) {
        for (var y = 0; y < image.getHeight(); y++) {
            for (var x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFF000000) != 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
