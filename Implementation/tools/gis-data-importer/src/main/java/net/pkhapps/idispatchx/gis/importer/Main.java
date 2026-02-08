package net.pkhapps.idispatchx.gis.importer;

import net.pkhapps.idispatchx.gis.importer.raster.RasterTileImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * CLI entry point for the GIS Data Importer.
 * <p>
 * Tile import arguments:
 * <ul>
 *   <li>{@code --tiles <file>...} — PNG files to import as raster tiles</li>
 *   <li>{@code --tile-input-dir <dir>} — directory containing PNG files to import</li>
 *   <li>{@code --tile-dir <dir>} — base output directory for tiles (required with tile input)</li>
 *   <li>{@code --tile-layer <name>} — layer name (required with tile input)</li>
 *   <li>{@code --truncate} — delete existing layer data before import</li>
 * </ul>
 */
public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        var tilePaths = new ArrayList<Path>();
        Path tileInputDir = null;
        Path tileDir = null;
        String tileLayer = null;
        var truncate = false;

        var i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "--tiles" -> {
                    i++;
                    while (i < args.length && !args[i].startsWith("--")) {
                        tilePaths.add(Path.of(args[i]));
                        i++;
                    }
                }
                case "--tile-input-dir" -> {
                    i++;
                    if (i < args.length) {
                        tileInputDir = Path.of(args[i]);
                        i++;
                    } else {
                        LOG.error("--tile-input-dir requires a directory argument");
                        System.exit(1);
                    }
                }
                case "--tile-dir" -> {
                    i++;
                    if (i < args.length) {
                        tileDir = Path.of(args[i]);
                        i++;
                    } else {
                        LOG.error("--tile-dir requires a directory argument");
                        System.exit(1);
                    }
                }
                case "--tile-layer" -> {
                    i++;
                    if (i < args.length) {
                        tileLayer = args[i];
                        i++;
                    } else {
                        LOG.error("--tile-layer requires a name argument");
                        System.exit(1);
                    }
                }
                case "--truncate" -> {
                    truncate = true;
                    i++;
                }
                default -> {
                    if (args[i].startsWith("--")) {
                        LOG.info("Ignoring unrecognized option: {}", args[i]);
                    }
                    i++;
                }
            }
        }

        // Scan tile input directory for PNG files
        if (tileInputDir != null) {
            if (!Files.isDirectory(tileInputDir)) {
                LOG.error("Tile input directory does not exist: {}", tileInputDir);
                System.exit(1);
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(tileInputDir, "*.png")) {
                for (var path : stream) {
                    tilePaths.add(path);
                }
            } catch (IOException e) {
                LOG.error("Cannot read tile input directory: {}", e.getMessage());
                System.exit(1);
            }
        }

        // Validate
        if (tilePaths.isEmpty()) {
            LOG.error("No input files specified. Use --tiles or --tile-input-dir.");
            System.exit(1);
        }
        if (tileDir == null) {
            LOG.error("--tile-dir is required when tile input is present");
            System.exit(1);
        }
        if (tileLayer == null) {
            LOG.error("--tile-layer is required when tile input is present");
            System.exit(1);
        }

        // Execute
        var importer = new RasterTileImporter(tileDir, tileLayer);

        if (truncate) {
            try {
                LOG.info("Truncating layer '{}'", tileLayer);
                importer.truncateLayer();
            } catch (IOException e) {
                LOG.error("Failed to truncate layer: {}", e.getMessage());
                System.exit(1);
            }
        }

        LOG.info("Importing {} tile source file(s) to {}/{}", tilePaths.size(), tileDir, tileLayer);
        var totalTiles = importer.importFiles(tilePaths);
        LOG.info("Import complete: {} tiles written", totalTiles);
        System.exit(0);
    }
}
