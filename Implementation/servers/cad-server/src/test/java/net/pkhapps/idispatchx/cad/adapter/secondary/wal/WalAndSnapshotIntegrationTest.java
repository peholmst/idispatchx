package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import net.pkhapps.idispatchx.cad.adapter.secondary.snapshot.FileBasedSnapshotAdapter;
import net.pkhapps.idispatchx.cad.adapter.secondary.snapshot.SnapshotConfig;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.OperationalState;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test exercising the full startup sequence from Technical Design §11.1:
 * write events → create snapshot → truncate WAL → simulate startup (load snapshot + replay remaining WAL entries).
 */
class WalAndSnapshotIntegrationTest {

    @TempDir
    Path walDir;

    @TempDir
    Path snapshotDir;

    @ParameterizedTest
    @EnumSource(WalFormat.class)
    void startupSequence_snapshotPlusPartialReplay(WalFormat format) throws IOException, InterruptedException {
        var eventTypes = List.of(TestDomainEvent.class);
        DomainEventSerializer serializer = createSerializer(format, eventTypes);

        // Phase 1: Write 20 events to the WAL.
        try (var wal = createWal(format, eventTypes)) {
            for (int i = 1; i <= 20; i++) {
                wal.write(TestDomainEvent.of("event-" + i));
            }
            assertEquals(20L, wal.currentSequence().value());

            // Phase 2: Create a snapshot at seq=15 (representing state after events 1–15).
            var snapshotAdapter = new FileBasedSnapshotAdapter(new SnapshotConfig(snapshotDir, format), serializer);
            snapshotAdapter.createSnapshot(OperationalState.empty(), new SequenceNumber(15));

            // Phase 3: Truncate WAL up to seq=15.
            wal.truncate(new SequenceNumber(15));
            Thread.sleep(200); // let async deletion complete
        }

        // Phase 4: Simulate startup with a new WAL adapter instance.
        try (var wal = createWal(format, eventTypes)) {
            assertEquals(20L, wal.currentSequence().value());

            // 4a. Load snapshot.
            var snapshotAdapter = new FileBasedSnapshotAdapter(new SnapshotConfig(snapshotDir, format), serializer);
            var snapshot = snapshotAdapter.loadLatestSnapshot();
            assertTrue(snapshot.isPresent());
            assertEquals(15L, snapshot.get().sequenceNumber().value());

            // 4b. Replay WAL from snapshot's sequence — should deliver events 16–20.
            var replayed = new ArrayList<DomainEvent>();
            wal.replayFrom(snapshot.get().sequenceNumber(), replayed::add);
            assertEquals(5, replayed.size(), "Expected exactly 5 events (16–20) from partial replay");
            assertEquals("event-16", ((TestDomainEvent) replayed.get(0)).payload());
            assertEquals("event-20", ((TestDomainEvent) replayed.getLast()).payload());
        }
    }

    @ParameterizedTest
    @EnumSource(WalFormat.class)
    void fullReplayEqualsSnapshotPlusPartialReplay(WalFormat format) throws Exception {
        var eventTypes = List.of(TestDomainEvent.class);
        DomainEventSerializer serializer = createSerializer(format, eventTypes);
        var snapshotAdapter = new FileBasedSnapshotAdapter(new SnapshotConfig(snapshotDir, format), serializer);

        // Write 10 events.
        try (var wal = createWal(format, eventTypes)) {
            for (int i = 1; i <= 10; i++) {
                wal.write(TestDomainEvent.of("event-" + i));
            }
            assertEquals(10L, wal.currentSequence().value());

            // Create snapshot at seq=6.
            snapshotAdapter.createSnapshot(OperationalState.empty(), new SequenceNumber(6));
        }

        // Partial replay from seq=6 should deliver events 7–10.
        try (var wal = createWal(format, eventTypes)) {
            var partialReplay = new ArrayList<DomainEvent>();
            wal.replayFrom(new SequenceNumber(6), partialReplay::add);
            assertEquals(4, partialReplay.size());
            assertEquals("event-7", ((TestDomainEvent) partialReplay.getFirst()).payload());
            assertEquals("event-10", ((TestDomainEvent) partialReplay.getLast()).payload());
        }
    }

    private FileBasedWalAdapter createWal(WalFormat format, List<? extends Class<? extends DomainEvent>> eventTypes)
            throws IOException {
        var config = new WalConfig(walDir, format, CorruptionMode.STRICT, 5);
        return FileBasedWalAdapter.create(config, eventTypes);
    }

    private DomainEventSerializer createSerializer(WalFormat format, List<? extends Class<? extends DomainEvent>> eventTypes) {
        return format == WalFormat.TEXT
                ? new JsonDomainEventSerializer(eventTypes)
                : new SmileDomainEventSerializer(eventTypes);
    }
}
