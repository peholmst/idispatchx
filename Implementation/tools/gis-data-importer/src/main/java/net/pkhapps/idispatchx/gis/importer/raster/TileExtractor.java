package net.pkhapps.idispatchx.gis.importer.raster;

import javax.imageio.ImageReader;
import java.awt.Rectangle;
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
        public ExtractedTile {
            image = copy(image);
        }

        @Override
        public BufferedImage image() {
            return copy(image);
        }

        private static BufferedImage copy(BufferedImage image) {
            return new BufferedImage(
                    image.getColorModel(),
                    image.copyData(null),
                    image.isAlphaPremultiplied(),
                    null);
        }
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
        return extract(zoom, ulX, ulY, pixelWidth, pixelHeight, srcWidth, srcHeight,
                (clampedSrcX, clampedSrcY, clampedSrcEndX, clampedSrcEndY, destX, destY, destEndX, destEndY) -> {
                    var tile = new BufferedImage(TileMatrixSet.TILE_SIZE, TileMatrixSet.TILE_SIZE,
                            BufferedImage.TYPE_INT_ARGB);
                    var g = tile.createGraphics();
                    try {
                        g.drawImage(source,
                                destX, destY, destEndX, destEndY,
                                clampedSrcX, clampedSrcY, clampedSrcEndX, clampedSrcEndY,
                                null);
                    } finally {
                        g.dispose();
                    }
                    return tile;
                }, consumer);
    }

    /**
     * Extracts tiles from an image reader and passes each non-empty tile to the consumer.
     * The reader is asked for only the source region needed for each output tile, which keeps
     * peak heap usage bounded for large source rasters.
     *
     * @param reader      image reader positioned at the PNG input
     * @param imageIndex  image index to read, usually 0
     * @param zoom        the zoom level
     * @param ulX         upper-left corner easting (edge of first pixel)
     * @param ulY         upper-left corner northing (edge of first pixel)
     * @param pixelWidth  pixel width in meters (positive)
     * @param pixelHeight pixel height in meters (negative)
     * @param consumer    consumer for non-empty extracted tiles
     * @return number of tiles passed to the consumer
     * @throws IOException if image reading or the consumer throws an IOException
     */
    public static int extract(ImageReader reader, int imageIndex, int zoom, double ulX, double ulY,
                              double pixelWidth, double pixelHeight, TileConsumer consumer) throws IOException {
        var srcWidth = reader.getWidth(imageIndex);
        var srcHeight = reader.getHeight(imageIndex);
        return extract(zoom, ulX, ulY, pixelWidth, pixelHeight, srcWidth, srcHeight,
                (clampedSrcX, clampedSrcY, clampedSrcEndX, clampedSrcEndY, destX, destY, destEndX, destEndY) -> {
                    var param = reader.getDefaultReadParam();
                    var width = clampedSrcEndX - clampedSrcX;
                    var height = clampedSrcEndY - clampedSrcY;
                    param.setSourceRegion(new Rectangle(clampedSrcX, clampedSrcY, width, height));
                    var region = reader.read(imageIndex, param);

                    var tile = new BufferedImage(TileMatrixSet.TILE_SIZE, TileMatrixSet.TILE_SIZE,
                            BufferedImage.TYPE_INT_ARGB);
                    var g = tile.createGraphics();
                    try {
                        g.drawImage(region, destX, destY, destEndX, destEndY, 0, 0, width, height, null);
                    } finally {
                        g.dispose();
                        region.flush();
                    }
                    return tile;
                }, consumer);
    }

    private static int extract(int zoom, double ulX, double ulY, double pixelWidth, double pixelHeight,
                               int srcWidth, int srcHeight, TileRenderer tileRenderer,
                               TileConsumer consumer) throws IOException {
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

                // Compute destination pixel positions on the tile
                var destX = (int) Math.round((clampedSrcX - srcX) * pixelWidth / pixelWidth);
                var destY = (int) Math.round((clampedSrcY - srcY) * absPixelHeight / absPixelHeight);
                var destEndX = destX + (clampedSrcEndX - clampedSrcX);
                var destEndY = destY + (clampedSrcEndY - clampedSrcY);

                var tile = tileRenderer.render(clampedSrcX, clampedSrcY, clampedSrcEndX, clampedSrcEndY,
                        destX, destY, destEndX, destEndY);

                if (!isFullyTransparent(tile)) {
                    consumer.accept(new ExtractedTile(new TileMatrixSet.TileCoordinate(zoom, row, col), tile));
                    count++;
                } else {
                    tile.flush();
                }
            }
        }
        return count;
    }

    @FunctionalInterface
    private interface TileRenderer {
        BufferedImage render(int clampedSrcX, int clampedSrcY, int clampedSrcEndX, int clampedSrcEndY,
                             int destX, int destY, int destEndX, int destEndY) throws IOException;
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
