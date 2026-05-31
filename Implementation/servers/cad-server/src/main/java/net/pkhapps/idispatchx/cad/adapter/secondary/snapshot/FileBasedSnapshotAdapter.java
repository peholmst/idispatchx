package net.pkhapps.idispatchx.cad.adapter.secondary.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.DomainEventSerializer;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.WalFormat;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.OperationalState;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.Snapshot;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.SnapshotPort;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.SnapshotReadException;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.SnapshotWriteException;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * File-based implementation of {@link SnapshotPort}.
 * <p>
 * Snapshots are written atomically via a temporary file followed by a rename (per ADR-0006).
 * The same {@link ObjectMapper} as the WAL adapter is used to guarantee format consistency.
 *
 * <h3>Snapshot file naming</h3>
 * <ul>
 *   <li>TEXT format: {@code snapshot-{seq:016d}.json}</li>
 *   <li>BINARY format: {@code snapshot-{seq:016d}.bin}</li>
 * </ul>
 * Temporary files use the same name with a {@code .tmp} suffix appended.
 */
public final class FileBasedSnapshotAdapter implements SnapshotPort {

    private static final Logger log = LoggerFactory.getLogger(FileBasedSnapshotAdapter.class);

    private static final String TEXT_EXT = ".json";
    private static final String BINARY_EXT = ".bin";

    private final SnapshotConfig config;
    private final ObjectMapper mapper;
    private final String snapshotExtension;

    /**
     * Creates the adapter.
     *
     * @param config     snapshot configuration
     * @param serializer the WAL serializer whose {@link ObjectMapper} is reused for consistent format
     * @throws IOException if the snapshot directory cannot be created
     */
    public FileBasedSnapshotAdapter(SnapshotConfig config, DomainEventSerializer serializer) throws IOException {
        this.config = config;
        this.mapper = serializer.objectMapper();
        this.snapshotExtension = config.format() == WalFormat.TEXT ? TEXT_EXT : BINARY_EXT;
        Files.createDirectories(config.snapshotDirectory());
        log.info("Snapshot adapter started (format={}, directory={})", config.format(), config.snapshotDirectory());
    }

    // -------------------------------------------------------------------------
    // SnapshotPort implementation
    // -------------------------------------------------------------------------

    @Override
    public void createSnapshot(OperationalState state, SequenceNumber upToSequence) {
        Path finalPath = snapshotPath(upToSequence.value());
        Path tmpPath = config.snapshotDirectory().resolve(finalPath.getFileName() + ".tmp");

        try {
            SnapshotDocument doc = new SnapshotDocument(upToSequence.value(), state);
            byte[] bytes = mapper.writeValueAsBytes(doc);

            // Write to temp file, sync to disk, then rename atomically.
            try (FileChannel channel = FileChannel.open(tmpPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                var buf = java.nio.ByteBuffer.wrap(bytes);
                while (buf.hasRemaining()) {
                    channel.write(buf);
                }
                channel.force(true);
            }

            Files.move(tmpPath, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.info("Snapshot created: {} (seq={})", finalPath.getFileName(), upToSequence.value());

        } catch (IOException e) {
            // Best-effort cleanup of the temp file.
            try {
                Files.deleteIfExists(tmpPath);
            } catch (IOException ignored) {
            }
            throw new SnapshotWriteException("Failed to create snapshot at seq=" + upToSequence.value(), e);
        }
    }

    @Override
    public Optional<Snapshot> loadLatestSnapshot() {
        List<Path> snapshots = listSnapshots();
        if (snapshots.isEmpty()) {
            return Optional.empty();
        }
        // Try newest first; fall back to older if corrupt.
        for (Path path : snapshots) {
            try {
                SnapshotDocument doc = mapper.readValue(path.toFile(), SnapshotDocument.class);
                var snapshot = new Snapshot(doc.state, new SequenceNumber(doc.sequenceNumber));
                log.info("Loaded snapshot: {} (seq={})", path.getFileName(), doc.sequenceNumber);
                return Optional.of(snapshot);
            } catch (IOException e) {
                log.warn("Snapshot {} is corrupt and will be skipped: {}", path.getFileName(), e.getMessage());
            }
        }
        // Snapshot files exist but none could be deserialized — surface the failure rather than
        // silently treating it as "no snapshot", which could cause unsafe startup after WAL truncation.
        throw new SnapshotReadException(
                "All " + snapshots.size() + " snapshot file(s) are corrupt; cannot load any");
    }

    @Override
    public void purgeOlderSnapshots(SequenceNumber keepAfter) {
        List<Path> toDelete = listSnapshots().stream()
                .filter(p -> extractSeq(p) < keepAfter.value())
                .toList();
        if (!toDelete.isEmpty()) {
            CompletableFuture.runAsync(() -> {
                for (Path path : toDelete) {
                    try {
                        Files.deleteIfExists(path);
                        log.info("Purged old snapshot: {}", path.getFileName());
                    } catch (IOException e) {
                        log.warn("Failed to purge old snapshot: {}", path.getFileName(), e);
                    }
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Lists all snapshot files sorted descending by sequence (newest first). */
    private List<Path> listSnapshots() {
        try (var stream = Files.list(config.snapshotDirectory())) {
            return stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("snapshot-") && name.endsWith(snapshotExtension);
                    })
                    .sorted(Comparator.comparingLong(FileBasedSnapshotAdapter::extractSeq).reversed())
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to list snapshots", e);
            return List.of();
        }
    }

    private Path snapshotPath(long seq) {
        return config.snapshotDirectory().resolve(String.format("snapshot-%016d%s", seq, snapshotExtension));
    }

    static long extractSeq(Path path) {
        String name = path.getFileName().toString();
        int dashPos = name.indexOf('-');
        int dotPos = name.lastIndexOf('.');
        return Long.parseLong(name.substring(dashPos + 1, dotPos));
    }

    // -------------------------------------------------------------------------
    // Jackson document type for snapshots
    // -------------------------------------------------------------------------

    /**
     * Jackson serialization document for a snapshot file.
     * Public fields allow Jackson to serialize and deserialize without explicit getters.
     */
    public static final class SnapshotDocument {
        public long sequenceNumber;
        public OperationalState state;

        /** For Jackson deserialization. */
        public SnapshotDocument() {
        }

        SnapshotDocument(long sequenceNumber, OperationalState state) {
            this.sequenceNumber = sequenceNumber;
            this.state = state;
        }
    }
}
