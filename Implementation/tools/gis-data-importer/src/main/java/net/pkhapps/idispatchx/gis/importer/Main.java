package net.pkhapps.idispatchx.gis.importer;

import net.pkhapps.idispatchx.gis.importer.db.DatabaseConnection;
import net.pkhapps.idispatchx.gis.importer.parser.FeatureType;
import net.pkhapps.idispatchx.gis.importer.raster.RasterTileImporter;
import net.pkhapps.idispatchx.gis.importer.transform.CoordinateTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * CLI entry point for the GIS Data Importer.
 * <p>
 * Supports two independent import pipelines that can run in the same invocation:
 * <ul>
 *   <li>Vector data import: GML features and municipality JSON into PostGIS</li>
 *   <li>Raster tile import: PNG + world files into filesystem tile directory</li>
 * </ul>
 *
 * @see ImportCommand
 * @see RasterTileImporter
 */
public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        // Vector import args
        var gmlPaths = new ArrayList<Path>();
        Path gmlInputDir = null;
        Path municipalitiesFile = null;
        String dbUrl = null;
        String dbUser = null;
        String dbPassword = null;
        String featuresArg = null;

        // Tile import args
        var tilePaths = new ArrayList<Path>();
        Path tileInputDir = null;
        Path tileDir = null;
        String tileLayer = null;

        var truncate = false;

        // Parse arguments
        var i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "--input" -> {
                    i++;
                    while (i < args.length && !args[i].startsWith("--")) {
                        gmlPaths.add(Path.of(args[i]));
                        i++;
                    }
                }
                case "--input-dir" -> {
                    i++;
                    if (i < args.length) {
                        gmlInputDir = Path.of(args[i]);
                        i++;
                    } else {
                        LOG.error("--input-dir requires a directory argument");
                        System.exit(1);
                    }
                }
                case "--municipalities" -> {
                    i++;
                    if (i < args.length) {
                        municipalitiesFile = Path.of(args[i]);
                        i++;
                    } else {
                        LOG.error("--municipalities requires a file argument");
                        System.exit(1);
                    }
                }
                case "--db-url" -> {
                    i++;
                    if (i < args.length) {
                        dbUrl = args[i];
                        i++;
                    } else {
                        LOG.error("--db-url requires an argument");
                        System.exit(1);
                    }
                }
                case "--db-user" -> {
                    i++;
                    if (i < args.length) {
                        dbUser = args[i];
                        i++;
                    } else {
                        LOG.error("--db-user requires an argument");
                        System.exit(1);
                    }
                }
                case "--db-password" -> {
                    i++;
                    if (i < args.length) {
                        dbPassword = args[i];
                        i++;
                    } else {
                        LOG.error("--db-password requires an argument");
                        System.exit(1);
                    }
                }
                case "--features" -> {
                    i++;
                    if (i < args.length) {
                        featuresArg = args[i];
                        i++;
                    } else {
                        LOG.error("--features requires a comma-separated list");
                        System.exit(1);
                    }
                }
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
                        LOG.warn("Ignoring unrecognized option: {}", args[i]);
                    }
                    i++;
                }
            }
        }

        // Scan GML input directory for XML files
        if (gmlInputDir != null) {
            if (!Files.isDirectory(gmlInputDir)) {
                LOG.error("GML input directory does not exist: {}", gmlInputDir);
                System.exit(1);
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(gmlInputDir, "*.xml")) {
                for (var path : stream) {
                    gmlPaths.add(path);
                }
            } catch (IOException e) {
                LOG.error("Cannot read GML input directory: {}", e.getMessage());
                System.exit(1);
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

        boolean hasVectorInput = !gmlPaths.isEmpty() || municipalitiesFile != null;
        boolean hasTileInput = !tilePaths.isEmpty();

        if (!hasVectorInput && !hasTileInput) {
            LOG.error("No input files specified. Use --input, --input-dir, --municipalities, --tiles, or --tile-input-dir.");
            System.exit(1);
        }

        // Parse feature filter
        Set<FeatureType> featureFilter = EnumSet.allOf(FeatureType.class);
        if (featuresArg != null) {
            featureFilter = EnumSet.noneOf(FeatureType.class);
            for (var name : featuresArg.split(",")) {
                featureFilter.add(FeatureType.valueOf(name.trim().toUpperCase()));
            }
        }

        // Vector data import
        if (hasVectorInput) {
            if (dbUrl == null || dbUser == null || dbPassword == null) {
                LOG.error("--db-url, --db-user, and --db-password are required for GML/JSON import");
                System.exit(1);
            }
            runVectorImport(dbUrl, dbUser, dbPassword, municipalitiesFile, gmlPaths, truncate, featureFilter);
        }

        // Tile import
        if (hasTileInput) {
            if (tileDir == null) {
                LOG.error("--tile-dir is required when tile input is present");
                System.exit(1);
            }
            if (tileLayer == null) {
                LOG.error("--tile-layer is required when tile input is present");
                System.exit(1);
            }
            runTileImport(tileDir, tileLayer, tilePaths, truncate);
        }

        System.exit(0);
    }

    private static void runVectorImport(String dbUrl, String dbUser, String dbPassword,
                                        Path municipalitiesFile, List<Path> gmlPaths,
                                        boolean truncate, Set<FeatureType> featureFilter) {
        try (var db = new DatabaseConnection(dbUrl, dbUser, dbPassword)) {
            var transformer = new CoordinateTransformer();
            var command = new ImportCommand(db.dsl(), transformer, truncate, featureFilter);

            if (municipalitiesFile != null) {
                command.importMunicipalities(municipalitiesFile);
            }

            if (!gmlPaths.isEmpty()) {
                command.importGmlFiles(gmlPaths);
            }

            LOG.info("Vector data import complete");
        } catch (Exception e) {
            LOG.error("Vector data import failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void runTileImport(Path tileDir, String tileLayer, List<Path> tilePaths, boolean truncate) {
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
        LOG.info("Tile import complete: {} tiles written", totalTiles);
    }
}
