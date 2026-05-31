package net.pkhapps.idispatchx.cad.adapter.secondary.snapshot;

import net.pkhapps.idispatchx.cad.adapter.secondary.wal.DomainEventSerializer;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.JsonDomainEventSerializer;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.SmileDomainEventSerializer;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.WalFormat;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.OperationalState;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.SnapshotReadException;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.SnapshotWriteException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileBasedSnapshotAdapterTest {

    @TempDir
    Path tempDir;

    private FileBasedSnapshotAdapter createAdapter(WalFormat format) throws IOException {
        var config = new SnapshotConfig(tempDir, format);
        var serializer = format == WalFormat.TEXT
                ? new JsonDomainEventSerializer(List.of())
                : new SmileDomainEventSerializer(List.of());
        return new FileBasedSnapshotAdapter(config, serializer);
    }

    // -------------------------------------------------------------------------
    // No snapshots
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(WalFormat.class)
    void noSnapshots_loadLatestReturnsEmpty(WalFormat format) throws IOException {
        var adapter = createAdapter(format);
        assertTrue(adapter.loadLatestSnapshot().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Create and load
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(WalFormat.class)
    void createAndLoad_roundTripsCorrectly(WalFormat format) throws IOException {
        var adapter = createAdapter(format);
        var state = OperationalState.empty();
        var seq = new SequenceNumber(100);

        adapter.createSnapshot(state, seq);

        var loaded = adapter.loadLatestSnapshot();
        assertTrue(loaded.isPresent());
        assertEquals(seq, loaded.get().sequenceNumber());
        // Collections should all be empty
        assertTrue(loaded.get().state().incidents().isEmpty());
        assertTrue(loaded.get().state().calls().isEmpty());
        assertTrue(loaded.get().state().unitStatuses().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(WalFormat.class)
    void createTwoSnapshots_loadLatestReturnsNewest(WalFormat format) throws IOException {
        var adapter = createAdapter(format);
        adapter.createSnapshot(OperationalState.empty(), new SequenceNumber(50));
        adapter.createSnapshot(OperationalState.empty(), new SequenceNumber(100));

        var loaded = adapter.loadLatestSnapshot();
        assertTrue(loaded.isPresent());
        assertEquals(100L, loaded.get().sequenceNumber().value());
    }

    // -------------------------------------------------------------------------
    // Corruption fallback
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(WalFormat.class)
    void corruptNewestSnapshot_fallsBackToOlder(WalFormat format) throws IOException {
        var adapter = createAdapter(format);
        adapter.createSnapshot(OperationalState.empty(), new SequenceNumber(50));
        adapter.createSnapshot(OperationalState.empty(), new SequenceNumber(100));

        // Corrupt the newest snapshot file
        String ext = format == WalFormat.TEXT ? ".json" : ".bin";
        Path newestFile = tempDir.resolve(String.format("snapshot-%016d%s", 100, ext));
        Files.writeString(newestFile, "CORRUPT DATA", java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

        var loaded = adapter.loadLatestSnapshot();
        assertTrue(loaded.isPresent());
        assertEquals(50L, loaded.get().sequenceNumber().value());
    }

    @ParameterizedTest
    @EnumSource(WalFormat.class)
    void allSnapshotsCorrupt_throwsSnapshotReadException(WalFormat format) throws IOException {
        var adapter = createAdapter(format);
        adapter.createSnapshot(OperationalState.empty(), new SequenceNumber(10));

        String ext = format == WalFormat.TEXT ? ".json" : ".bin";
        Path file = tempDir.resolve(String.format("snapshot-%016d%s", 10, ext));
        Files.writeString(file, "CORRUPT", java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

        assertThrows(SnapshotReadException.class, () -> adapter.loadLatestSnapshot());
    }

    // -------------------------------------------------------------------------
    // Atomic write: no partial snapshot on failure
    // -------------------------------------------------------------------------

    @Test
    void atomicWrite_noTmpFileLeftAfterSuccess() throws IOException {
        var adapter = createAdapter(WalFormat.TEXT);
        adapter.createSnapshot(OperationalState.empty(), new SequenceNumber(1));

        // No .tmp files should remain after a successful write
        try (var stream = Files.list(tempDir)) {
            boolean hasTmp = stream.anyMatch(p -> p.getFileName().toString().endsWith(".tmp"));
            assertFalse(hasTmp, "No .tmp files should remain after successful snapshot");
        }
    }

    // -------------------------------------------------------------------------
    // Purge old snapshots
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(WalFormat.class)
    void purge_deletesOlderSnapshots_keepsNewest(WalFormat format) throws IOException, InterruptedException {
        var adapter = createAdapter(format);
        adapter.createSnapshot(OperationalState.empty(), new SequenceNumber(10));
        adapter.createSnapshot(OperationalState.empty(), new SequenceNumber(20));
        adapter.createSnapshot(OperationalState.empty(), new SequenceNumber(30));

        adapter.purgeOlderSnapshots(new SequenceNumber(30));
        Thread.sleep(200);

        var remaining = adapter.loadLatestSnapshot();
        assertTrue(remaining.isPresent());
        assertEquals(30L, remaining.get().sequenceNumber().value());

        // seq=10 and seq=20 should be gone
        String ext = format == WalFormat.TEXT ? ".json" : ".bin";
        assertFalse(Files.exists(tempDir.resolve(String.format("snapshot-%016d%s", 10, ext))));
        assertFalse(Files.exists(tempDir.resolve(String.format("snapshot-%016d%s", 20, ext))));
        assertTrue(Files.exists(tempDir.resolve(String.format("snapshot-%016d%s", 30, ext))));
    }
}
