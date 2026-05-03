package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalReplayException;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalWriteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * File-based implementation of {@link WalPort}.
 * <p>
 * WAL entries are stored in segmented files. Each segment holds at most
 * {@link WalConfig#maxEntriesPerSegment()} entries, enabling O(1) truncation by
 * deleting whole segment files. The current open segment is never truncated.
 *
 * <h3>Segment file naming</h3>
 * <ul>
 *   <li>TEXT format: {@code wal-{firstSeq:016d}.ndjson}</li>
 *   <li>BINARY format: {@code wal-{firstSeq:016d}.bin}</li>
 * </ul>
 * where {@code firstSeq} is the sequence number of the first entry in that segment.
 *
 * <h3>Entry encoding</h3>
 * <ul>
 *   <li>TEXT: one UTF-8 JSON line per entry (newline-delimited)</li>
 *   <li>BINARY: 4-byte big-endian length prefix followed by SMILE bytes</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * Write methods ({@code write}, {@code writeBatch}) are {@code synchronized}.
 * Read methods ({@code replay}, {@code replayFrom}) may run concurrently with writes
 * because they read immutable closed segments and the already-written portion of the
 * current segment sequentially.
 */
public final class FileBasedWalAdapter implements WalPort, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FileBasedWalAdapter.class);

    private static final String TEXT_EXT = ".ndjson";
    private static final String BINARY_EXT = ".bin";

    private final WalConfig config;
    private final DomainEventSerializer serializer;
    private final String segmentExtension;

    private final AtomicLong currentSeq = new AtomicLong(0);
    private Path currentSegmentPath;
    private FileChannel currentChannel;
    private int currentSegmentEntries;
    private boolean needsRollover = false;
    private boolean closed = false;

    /**
     * Creates the adapter, scanning the WAL directory for existing segments and resuming
     * from the latest state.
     *
     * @param config     WAL configuration
     * @param serializer the event serializer matching the configured {@link WalFormat}
     * @throws IOException if the WAL directory cannot be created or the last segment cannot be read
     */
    public FileBasedWalAdapter(WalConfig config, DomainEventSerializer serializer) throws IOException {
        this.config = config;
        this.serializer = serializer;
        this.segmentExtension = config.format() == WalFormat.TEXT ? TEXT_EXT : BINARY_EXT;
        Files.createDirectories(config.walDirectory());
        initialize();
        log.info("WAL adapter started (format={}, corruptionMode={}, maxEntriesPerSegment={}, currentSeq={})",
                config.format(), config.corruptionMode(), config.maxEntriesPerSegment(), currentSeq.get());
    }

    /**
     * Convenience factory that creates the correct {@link DomainEventSerializer} for the config format.
     *
     * @param config     WAL configuration
     * @param eventTypes concrete {@link DomainEvent} subtypes to register
     * @throws IOException if initialization fails
     */
    public static FileBasedWalAdapter create(WalConfig config, List<? extends Class<? extends DomainEvent>> eventTypes)
            throws IOException {
        DomainEventSerializer serializer = config.format() == WalFormat.TEXT
                ? new JsonDomainEventSerializer(eventTypes)
                : new SmileDomainEventSerializer(eventTypes);
        return new FileBasedWalAdapter(config, serializer);
    }

    // -------------------------------------------------------------------------
    // WalPort implementation
    // -------------------------------------------------------------------------

    @Override
    public synchronized SequenceNumber write(DomainEvent event) {
        ensureOpen();
        try {
            maybeRolloverBeforeWrite();
            long seq = currentSeq.incrementAndGet();
            var entry = new WalEntry(new SequenceNumber(seq), event);
            appendEntry(entry);
            currentChannel.force(true);
            currentSegmentEntries++;
            checkRolloverAfterWrite();
            return new SequenceNumber(seq);
        } catch (IOException e) {
            throw new WalWriteException("Failed to write WAL entry seq=" + currentSeq.get(), e);
        }
    }

    @Override
    public synchronized SequenceNumber writeBatch(List<? extends DomainEvent> events) {
        ensureOpen();
        if (events.isEmpty()) {
            return currentSequence();
        }

        long firstSeq = currentSeq.get() + 1;
        long lastSeq = firstSeq + events.size() - 1;
        long positionBefore;
        try {
            positionBefore = currentChannel.position();
        } catch (IOException e) {
            throw new WalWriteException("Failed to determine WAL position for batch starting at seq=" + firstSeq, e);
        }

        try {
            maybeRolloverBeforeWrite();
            for (DomainEvent event : events) {
                long seq = currentSeq.incrementAndGet();
                appendEntry(new WalEntry(new SequenceNumber(seq), event));
            }
            currentChannel.force(true);
        } catch (IOException e) {
            // Rollback: truncate the channel back to the pre-batch position.
            currentSeq.set(firstSeq - 1);
            try {
                currentChannel.truncate(positionBefore);
                currentChannel.position(positionBefore);
            } catch (IOException truncateEx) {
                log.error("Failed to roll back WAL batch starting at seq={}", firstSeq, truncateEx);
            }
            throw new WalWriteException("Failed to write WAL batch starting at seq=" + firstSeq, e);
        }

        currentSegmentEntries += events.size();
        checkRolloverAfterWrite();
        return new SequenceNumber(lastSeq);
    }

    @Override
    public void replayFrom(SequenceNumber from, Consumer<DomainEvent> consumer) {
        replayInternal(from.value(), consumer);
    }

    @Override
    public void replay(Consumer<DomainEvent> consumer) {
        // skipUpToSeq=0 means "include all entries" since all valid seq numbers are >= 1.
        replayInternal(0L, consumer);
    }

    private void replayInternal(long skipUpToSeq, Consumer<DomainEvent> consumer) {
        var segments = listSegments();
        for (int i = 0; i < segments.size(); i++) {
            // Skip this segment entirely if we know all its entries are <= skipUpToSeq.
            if (i < segments.size() - 1) {
                long nextFirstSeq = extractFirstSeq(segments.get(i + 1));
                // All entries in segment i have seq <= nextFirstSeq-1.
                if (nextFirstSeq - 1 <= skipUpToSeq) {
                    continue;
                }
            } else {
                // Last (current) segment: skip if all written entries are <= skipUpToSeq.
                long segFirstSeq = extractFirstSeq(segments.get(i));
                if (segFirstSeq <= skipUpToSeq && currentSeq.get() <= skipUpToSeq) {
                    continue;
                }
            }
            readSegment(segments.get(i), skipUpToSeq, consumer);
        }
    }

    @Override
    public void truncate(SequenceNumber upTo) {
        var segments = listSegments();
        var toDelete = new ArrayList<Path>();
        for (int i = 0; i < segments.size() - 1; i++) {
            long nextFirstSeq = extractFirstSeq(segments.get(i + 1));
            // The segment at i covers [firstSeq, nextFirstSeq-1]. Delete if nextFirstSeq-1 <= upTo.
            if (nextFirstSeq - 1 <= upTo.value()) {
                toDelete.add(segments.get(i));
            }
        }
        if (!toDelete.isEmpty()) {
            CompletableFuture.runAsync(() -> {
                for (Path path : toDelete) {
                    try {
                        Files.deleteIfExists(path);
                        log.info("WAL truncation: deleted segment {}", path.getFileName());
                    } catch (IOException e) {
                        log.warn("WAL truncation: failed to delete segment {}", path.getFileName(), e);
                    }
                }
            });
        }
    }

    @Override
    public SequenceNumber currentSequence() {
        long seq = currentSeq.get();
        return seq > 0 ? new SequenceNumber(seq) : SequenceNumber.start();
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            if (currentChannel != null && currentChannel.isOpen()) {
                currentChannel.force(true);
                currentChannel.close();
            }
            log.info("WAL adapter closed (currentSeq={})", currentSeq.get());
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void initialize() throws IOException {
        // Use a mutable list so we can remove empty trailing segments.
        var segments = new ArrayList<>(listSegments());
        if (segments.isEmpty()) {
            currentSeq.set(0);
            currentSegmentEntries = 0;
            openNewSegment(1);
            return;
        }

        // Find the newest non-empty segment; delete empty trailing ones.
        while (!segments.isEmpty()) {
            Path last = segments.getLast();
            long lastFirstSeq = extractFirstSeq(last);
            long entryCount = countEntriesInSegment(last);
            if (entryCount > 0) {
                currentSeq.set(lastFirstSeq + entryCount - 1);
                currentSegmentEntries = (int) entryCount;
                currentSegmentPath = last;
                currentChannel = FileChannel.open(last, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                // If the segment is full, the next write will trigger a lazy rollover.
                needsRollover = (currentSegmentEntries >= config.maxEntriesPerSegment());
                return;
            }
            log.warn("Deleting empty WAL segment: {}", last.getFileName());
            Files.deleteIfExists(last);
            segments.removeLast();
        }

        // All segments were empty.
        currentSeq.set(0);
        currentSegmentEntries = 0;
        openNewSegment(1);
    }

    private void openNewSegment(long firstSeq) throws IOException {
        currentSegmentPath = segmentPath(firstSeq);
        currentChannel = FileChannel.open(currentSegmentPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        currentSegmentEntries = 0;
        log.debug("Opened new WAL segment: {}", currentSegmentPath.getFileName());
    }

    /** Lazily rolls over to a new segment before a write when the current segment is full. */
    private void maybeRolloverBeforeWrite() throws IOException {
        if (needsRollover) {
            needsRollover = false;
            currentChannel.force(true);
            currentChannel.close();
            long newFirstSeq = currentSeq.get() + 1;
            openNewSegment(newFirstSeq);
            log.debug("WAL segment rolled over; new segment starts at seq={}", newFirstSeq);
        }
    }

    private void checkRolloverAfterWrite() {
        needsRollover = (currentSegmentEntries >= config.maxEntriesPerSegment());
    }

    private void appendEntry(WalEntry entry) throws IOException {
        byte[] payload = serializer.serialize(entry);
        if (config.format() == WalFormat.TEXT) {
            // Append JSON bytes + newline
            byte[] newline = "\n".getBytes(StandardCharsets.UTF_8);
            writeAll(ByteBuffer.wrap(payload));
            writeAll(ByteBuffer.wrap(newline));
        } else {
            // 4-byte big-endian length prefix + SMILE bytes
            ByteBuffer lenBuf = ByteBuffer.allocate(4);
            lenBuf.putInt(payload.length);
            lenBuf.flip();
            writeAll(lenBuf);
            writeAll(ByteBuffer.wrap(payload));
        }
    }

    private void writeAll(ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            currentChannel.write(buf);
        }
    }

    private void readSegment(Path segment, long skipUpToSeq, Consumer<DomainEvent> consumer) {
        try {
            if (config.format() == WalFormat.TEXT) {
                readTextSegment(segment, skipUpToSeq, consumer);
            } else {
                readBinarySegment(segment, skipUpToSeq, consumer);
            }
        } catch (IOException e) {
            handleReplayError("Failed to open segment " + segment.getFileName(), e, null);
        }
    }

    private void readTextSegment(Path segment, long skipUpToSeq, Consumer<DomainEvent> consumer) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(segment, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    WalEntry entry = serializer.deserialize(line.getBytes(StandardCharsets.UTF_8));
                    if (entry.sequenceNumber().value() > skipUpToSeq) {
                        consumer.accept(entry.event());
                    }
                } catch (IOException e) {
                    handleReplayError("Failed to deserialize WAL entry in segment "
                            + segment.getFileName(), e, null);
                }
            }
        }
    }

    private void readBinarySegment(Path segment, long skipUpToSeq, Consumer<DomainEvent> consumer) throws IOException {
        try (var channel = FileChannel.open(segment, StandardOpenOption.READ)) {
            ByteBuffer lenBuf = ByteBuffer.allocate(4);
            long entryIndex = 0;
            while (channel.position() < channel.size()) {
                lenBuf.clear();
                int read = readFully(channel, lenBuf);
                if (read < 4) {
                    // Truncated length prefix — treat as corrupt
                    handleReplayError("Truncated length prefix in binary WAL segment "
                            + segment.getFileName() + " at entry " + entryIndex, null, null);
                    break;
                }
                lenBuf.flip();
                int payloadLen = lenBuf.getInt();
                if (payloadLen <= 0 || channel.position() + payloadLen > channel.size()) {
                    handleReplayError("Invalid payload length " + payloadLen + " in binary WAL segment "
                            + segment.getFileName() + " at entry " + entryIndex, null, null);
                    break;
                }
                ByteBuffer payloadBuf = ByteBuffer.allocate(payloadLen);
                readFully(channel, payloadBuf);
                payloadBuf.flip();
                byte[] payload = new byte[payloadLen];
                payloadBuf.get(payload);
                try {
                    WalEntry entry = serializer.deserialize(payload);
                    if (entry.sequenceNumber().value() > skipUpToSeq) {
                        consumer.accept(entry.event());
                    }
                } catch (IOException e) {
                    handleReplayError("Failed to deserialize WAL entry in binary segment "
                            + segment.getFileName() + " at entry " + entryIndex, e, null);
                }
                entryIndex++;
            }
        }
    }

    private int readFully(FileChannel channel, ByteBuffer buf) throws IOException {
        int total = 0;
        while (buf.hasRemaining()) {
            int read = channel.read(buf);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    private void handleReplayError(String message, Exception cause, Long seq) {
        String fullMessage = seq != null ? message + " (seq=" + seq + ")" : message;
        if (config.corruptionMode() == CorruptionMode.STRICT) {
            if (cause != null) {
                throw new WalReplayException(fullMessage, cause);
            } else {
                throw new WalReplayException(fullMessage);
            }
        } else {
            if (cause != null) {
                log.warn("WAL replay (lenient): skipping corrupt entry — {}", message, cause);
            } else {
                log.warn("WAL replay (lenient): skipping corrupt entry — {}", message);
            }
        }
    }

    /** Lists all WAL segment files in the WAL directory, sorted ascending by first sequence. */
    List<Path> listSegments() {
        try {
            var prefix = "wal-";
            var ext = segmentExtension;
            try (var stream = Files.list(config.walDirectory())) {
                return stream
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return name.startsWith(prefix) && name.endsWith(ext);
                        })
                        .sorted(Comparator.comparing(p -> extractFirstSeq(p)))
                        .toList();
            }
        } catch (IOException e) {
            log.warn("Failed to list WAL segments", e);
            return List.of();
        }
    }

    private Path segmentPath(long firstSeq) {
        return config.walDirectory().resolve(String.format("wal-%016d%s", firstSeq, segmentExtension));
    }

    static long extractFirstSeq(Path segmentPath) {
        String name = segmentPath.getFileName().toString();
        // Name format: wal-{16 digits}.ext
        int dashPos = name.indexOf('-');
        int dotPos = name.lastIndexOf('.');
        return Long.parseLong(name.substring(dashPos + 1, dotPos));
    }

    private long countEntriesInSegment(Path segment) throws IOException {
        if (config.format() == WalFormat.TEXT) {
            return countLines(segment);
        } else {
            return countBinaryEntries(segment);
        }
    }

    private static long countLines(Path path) throws IOException {
        long count = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            while (reader.readLine() != null) {
                count++;
            }
        }
        return count;
    }

    private static long countBinaryEntries(Path path) throws IOException {
        long count = 0;
        try (var channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer lenBuf = ByteBuffer.allocate(4);
            while (channel.position() < channel.size()) {
                lenBuf.clear();
                int read = 0;
                while (lenBuf.hasRemaining()) {
                    int r = channel.read(lenBuf);
                    if (r < 0) {
                        return count; // truncated
                    }
                    read += r;
                }
                if (read < 4) {
                    return count;
                }
                lenBuf.flip();
                int payloadLen = lenBuf.getInt();
                if (payloadLen <= 0 || channel.position() + payloadLen > channel.size()) {
                    return count; // corrupt length
                }
                channel.position(channel.position() + payloadLen);
                count++;
            }
        }
        return count;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("WAL adapter has been closed");
        }
    }
}
