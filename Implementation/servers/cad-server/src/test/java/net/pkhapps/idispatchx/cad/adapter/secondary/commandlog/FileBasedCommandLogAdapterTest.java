package net.pkhapps.idispatchx.cad.adapter.secondary.commandlog;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.port.secondary.commandlog.CommandLogEntry;
import net.pkhapps.idispatchx.common.auth.IPAddress;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FileBasedCommandLogAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void log_writesEntryToFile() throws IOException {
        var config = new CommandLogConfig(tempDir, "commands.log", 100);
        var commandId = CommandId.generate();
        try (var adapter = new FileBasedCommandLogAdapter(config)) {
            var entry = new CommandLogEntry(
                    commandId,
                    UserId.of("user@example.com"),
                    Instant.parse("2026-05-03T12:00:00Z"),
                    IPAddress.of("192.168.1.1"),
                    "CreateIncidentCommand"
            );
            adapter.log(entry);
        }

        var lines = Files.readAllLines(tempDir.resolve("commands.log"));
        assertEquals(1, lines.size());
        var parts = lines.getFirst().split("\t", -1);
        assertEquals(5, parts.length);
        assertEquals("2026-05-03T12:00:00Z", parts[0]);
        assertEquals(commandId.value(), parts[1]);
        assertEquals("user@example.com", parts[2]);
        assertEquals("192.168.1.1", parts[3]);
        assertEquals("CreateIncidentCommand", parts[4]);
    }

    @Test
    void log_writesEntryWithNullIpAddress() throws IOException {
        var config = new CommandLogConfig(tempDir, "commands.log", 100);
        try (var adapter = new FileBasedCommandLogAdapter(config)) {
            var entry = new CommandLogEntry(
                    CommandId.generate(),
                    UserId.SYSTEM,
                    Instant.parse("2026-05-03T12:00:00Z"),
                    null,
                    "CloseIncidentCommand"
            );
            adapter.log(entry);
        }

        var lines = Files.readAllLines(tempDir.resolve("commands.log"));
        assertEquals(1, lines.size());
        var parts = lines.getFirst().split("\t", -1);
        assertEquals(5, parts.length);
        assertEquals("SYSTEM", parts[2]);
        assertEquals("", parts[3]);
        assertEquals("CloseIncidentCommand", parts[4]);
    }

    @Test
    void log_rotatesWhenFileSizeExceeded() throws IOException {
        // Set max to 1 MB, write enough entries to trigger rotation
        var config = new CommandLogConfig(tempDir, "commands.log", 1);
        try (var adapter = new FileBasedCommandLogAdapter(config)) {
            // Write entries until rotation happens (each line ~100 bytes, 1MB = ~10000 entries)
            var entry = new CommandLogEntry(
                    CommandId.generate(),
                    UserId.of("user@example.com"),
                    Instant.now(),
                    IPAddress.of("10.0.0.1"),
                    "SomeCommand"
            );
            int written = 0;
            long maxBytes = 1L * 1024 * 1024;
            while (Files.size(tempDir.resolve("commands.log")) < maxBytes || written < 2) {
                adapter.log(entry);
                written++;
                if (written > 15000) break; // safety valve
            }
            // Write one more to trigger rotation check
            adapter.log(entry);
        }

        // After rotation, there should be at least one rotated file alongside commands.log
        var files = Files.list(tempDir).toList();
        assertTrue(files.size() >= 2,
                "Expected at least 2 files (current + 1 rotated), found: " + files.size());
        assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().equals("commands.log")));
        assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().startsWith("commands.log.")));
    }

    @Test
    void log_appendsToExistingFile() throws IOException {
        var config = new CommandLogConfig(tempDir, "commands.log", 100);
        var entry = new CommandLogEntry(
                CommandId.generate(),
                UserId.SYSTEM,
                Instant.now(),
                null,
                "TestCommand"
        );

        try (var adapter = new FileBasedCommandLogAdapter(config)) {
            adapter.log(entry);
        }
        try (var adapter = new FileBasedCommandLogAdapter(config)) {
            adapter.log(entry);
        }

        var lines = Files.readAllLines(tempDir.resolve("commands.log"));
        assertEquals(2, lines.size());
    }

    @Test
    void log_throwsAfterClose() throws IOException {
        var config = new CommandLogConfig(tempDir, "commands.log", 100);
        var adapter = new FileBasedCommandLogAdapter(config);
        adapter.close();

        var entry = new CommandLogEntry(
                CommandId.generate(),
                UserId.SYSTEM,
                Instant.now(),
                null,
                "TestCommand"
        );
        assertThrows(IllegalStateException.class, () -> adapter.log(entry));
    }

    @Test
    void log_isThreadSafe() throws Exception {
        var config = new CommandLogConfig(tempDir, "commands.log", 100);
        int threadCount = 10;
        int entriesPerThread = 50;

        try (var adapter = new FileBasedCommandLogAdapter(config)) {
            var latch = new CountDownLatch(threadCount);
            List<Thread> threads = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                threads.add(new Thread(() -> {
                    try {
                        for (int j = 0; j < entriesPerThread; j++) {
                            adapter.log(new CommandLogEntry(
                                    CommandId.generate(),
                                    UserId.of("user-" + threadId),
                                    Instant.now(),
                                    null,
                                    "Command" + j
                            ));
                        }
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            threads.forEach(Thread::start);
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        }

        var lines = Files.readAllLines(tempDir.resolve("commands.log"));
        assertEquals(threadCount * entriesPerThread, lines.size());
    }

    // Helper to get a reusable entry for line-format tests
    private CommandLogEntry entry() {
        return new CommandLogEntry(
                CommandId.generate(),
                UserId.of("user@example.com"),
                Instant.parse("2026-05-03T12:00:00Z"),
                IPAddress.of("192.168.1.1"),
                "CreateIncidentCommand"
        );
    }
}
