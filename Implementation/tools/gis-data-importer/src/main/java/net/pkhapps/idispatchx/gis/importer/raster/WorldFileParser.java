package net.pkhapps.idispatchx.gis.importer.raster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parses NLS world files (.pgw) that provide georeferencing for raster map images.
 * <p>
 * A world file contains 6 lines defining an affine transformation from pixel coordinates
 * to map coordinates in EPSG:3067. For NLS data, rotation is always zero and pixels are square.
 */
public final class WorldFileParser {

    private static final double MIN_EASTING = 43_547.79;
    private static final double MAX_EASTING = 764_796.72;
    private static final double MIN_NORTHING = 6_522_236.87;
    private static final double MAX_NORTHING = 7_795_461.19;

    private WorldFileParser() {
    }

    /**
     * Parsed world file data with upper-left corner coordinates (edge of first pixel, not center).
     *
     * @param pixelWidth  pixel width in meters (positive)
     * @param pixelHeight pixel height in meters (negative, north-up)
     * @param ulCornerX   easting of upper-left corner (edge of first pixel) in EPSG:3067
     * @param ulCornerY   northing of upper-left corner (edge of first pixel) in EPSG:3067
     */
    public record WorldFileData(double pixelWidth, double pixelHeight, double ulCornerX, double ulCornerY) {
    }

    /**
     * Parses a world file and returns the georeferencing data.
     *
     * @param worldFile path to the .pgw world file
     * @return parsed world file data
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the file content is invalid
     */
    public static WorldFileData parse(Path worldFile) throws IOException {
        var lines = Files.readAllLines(worldFile);
        if (lines.size() < 6) {
            throw new IllegalArgumentException("World file must have at least 6 lines, got " + lines.size());
        }

        var pixelWidth = parseDouble(lines.get(0), "pixel width (line 1)");
        var rotationY = parseDouble(lines.get(1), "rotation Y (line 2)");
        var rotationX = parseDouble(lines.get(2), "rotation X (line 3)");
        var pixelHeight = parseDouble(lines.get(3), "pixel height (line 4)");
        var ulCenterX = parseDouble(lines.get(4), "upper-left center X (line 5)");
        var ulCenterY = parseDouble(lines.get(5), "upper-left center Y (line 6)");

        if (rotationY != 0.0) {
            throw new IllegalArgumentException("Rotation Y must be 0.0, got " + rotationY);
        }
        if (rotationX != 0.0) {
            throw new IllegalArgumentException("Rotation X must be 0.0, got " + rotationX);
        }
        if (pixelWidth <= 0.0) {
            throw new IllegalArgumentException("Pixel width must be positive, got " + pixelWidth);
        }
        if (pixelHeight >= 0.0) {
            throw new IllegalArgumentException("Pixel height must be negative, got " + pixelHeight);
        }
        if (pixelWidth != Math.abs(pixelHeight)) {
            throw new IllegalArgumentException(
                    "Pixels must be square: width=" + pixelWidth + ", |height|=" + Math.abs(pixelHeight));
        }

        var ulCornerX = ulCenterX - pixelWidth / 2.0;
        var ulCornerY = ulCenterY - pixelHeight / 2.0;

        if (ulCornerX < MIN_EASTING || ulCornerX > MAX_EASTING) {
            throw new IllegalArgumentException(
                    "Upper-left corner easting " + ulCornerX + " outside EPSG:3067 bounds ["
                            + MIN_EASTING + ", " + MAX_EASTING + "]");
        }
        if (ulCornerY < MIN_NORTHING || ulCornerY > MAX_NORTHING) {
            throw new IllegalArgumentException(
                    "Upper-left corner northing " + ulCornerY + " outside EPSG:3067 bounds ["
                            + MIN_NORTHING + ", " + MAX_NORTHING + "]");
        }

        return new WorldFileData(pixelWidth, pixelHeight, ulCornerX, ulCornerY);
    }

    private static double parseDouble(String value, String description) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot parse " + description + ": '" + value.trim() + "'");
        }
    }
}
