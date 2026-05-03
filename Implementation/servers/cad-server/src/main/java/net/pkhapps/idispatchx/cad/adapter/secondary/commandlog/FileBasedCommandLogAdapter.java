package net.pkhapps.idispatchx.cad.adapter.secondary.commandlog;

import net.pkhapps.idispatchx.cad.port.secondary.commandlog.CommandLogEntry;
import net.pkhapps.idispatchx.cad.port.secondary.commandlog.CommandLogException;
import net.pkhapps.idispatchx.cad.port.secondary.commandlog.CommandLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * File-based implementation of {@link CommandLogPort} that writes commands to an append-only
 * text file with size-based rotation.
 * <p>
 * Each entry is written as a single pipe-delimited line:
 * {@code <ISO-8601 timestamp>|<commandId>|<userId>|<ipAddress or empty>|<commandType>}
 * <p>
 * When the current log file reaches the configured maximum size, it is renamed to
 * {@code <baseFileName>.<timestamp-millis>} and a new file is started. This class is
 * thread-safe and must be closed when no longer needed.
 */
public final class FileBasedCommandLogAdapter implements CommandLogPort, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FileBasedCommandLogAdapter.class);

    private final CommandLogConfig config;
    private final long maxFileSizeBytes;
    private BufferedWriter writer;
    private Path currentFile;
    private boolean closed = false;

    /**
     * Creates the adapter and opens the log file for writing.
     *
     * @param config the command log configuration
     * @throws IOException if the log directory cannot be created or the file cannot be opened
     */
    public FileBasedCommandLogAdapter(CommandLogConfig config) throws IOException {
        this.config = config;
        this.maxFileSizeBytes = config.maxFileSizeMb() * 1024L * 1024L;
        Files.createDirectories(config.logDirectory());
        this.currentFile = config.logDirectory().resolve(config.baseFileName());
        this.writer = openForAppend(currentFile);
        log.info("Command log opened: {}", currentFile);
    }

    @Override
    public synchronized void log(CommandLogEntry entry) {
        if (closed) {
            throw new IllegalStateException("Command log has been closed");
        }
        try {
            String line = formatEntry(entry);
            writer.write(line);
            writer.newLine();
            writer.flush();
            rotateIfNeeded();
        } catch (IOException e) {
            throw new CommandLogException("Failed to write command log entry for command " + entry.commandId(), e);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            writer.flush();
            writer.close();
            log.info("Command log closed: {}", currentFile);
        }
    }

    private void rotateIfNeeded() throws IOException {
        long fileSize;
        try {
            fileSize = Files.size(currentFile);
        } catch (IOException e) {
            log.warn("Could not determine size of {}, skipping rotation check", currentFile, e);
            return;
        }
        if (fileSize >= maxFileSizeBytes) {
            rotate();
        }
    }

    private void rotate() throws IOException {
        writer.flush();
        writer.close();

        Path rotated = config.logDirectory().resolve(config.baseFileName() + "." + Instant.now().toEpochMilli());
        try {
            Files.move(currentFile, rotated);
            log.info("Command log rotated: {} -> {}", currentFile, rotated);
        } catch (IOException e) {
            log.error("Failed to rename command log during rotation; continuing to write to existing file", e);
        } finally {
            writer = openForAppend(currentFile);
        }
    }

    private static String formatEntry(CommandLogEntry entry) {
        String ip = entry.ipAddress() != null ? entry.ipAddress().value() : "";
        return entry.timestamp().toString()
                + "|" + entry.commandId().value()
                + "|" + entry.userId().value()
                + "|" + ip
                + "|" + entry.commandType();
    }

    private static BufferedWriter openForAppend(Path path) throws IOException {
        return Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
