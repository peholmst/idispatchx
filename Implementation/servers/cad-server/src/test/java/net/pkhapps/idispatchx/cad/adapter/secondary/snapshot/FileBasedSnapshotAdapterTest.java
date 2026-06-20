package net.pkhapps.idispatchx.cad.adapter.secondary.snapshot;

import tools.jackson.databind.node.JsonNodeFactory;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.DomainEventSerializer;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.JsonDomainEventSerializer;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.SmileDomainEventSerializer;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.WalFormat;
import net.pkhapps.idispatchx.cad.domain.model.call.CallOutcome;
import net.pkhapps.idispatchx.cad.domain.model.call.CallState;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntry;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntryId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentPriority;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentState;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentType;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallerName;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.CallSnapshot;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.IncidentSnapshot;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.OperationalState;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.SnapshotReadException;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.SnapshotWriteException;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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

    // -------------------------------------------------------------------------
    // Round-trip with actual calls and incidents
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(WalFormat.class)
    void createAndLoad_withCallsAndIncidents_roundTripsCorrectly(WalFormat format) throws IOException {
        var adapter = createAdapter(format);

        var callId = new CallId("abc123defghijklmnopqr".substring(0, 21));
        var incidentId = new IncidentId("xyz987fedcbazyxwvutsr".substring(0, 21));
        var logEntryId = new IncidentLogEntryId("logentryid0000000abcd".substring(0, 21));
        var now = Instant.parse("2026-01-15T12:00:00Z");
        var dispatcher = new UserId("dispatcher@example.com");

        var callSnapshot = new CallSnapshot(
                callId,
                CallState.ACTIVE,
                dispatcher,
                now,
                null,
                new CallerName("John Doe"),
                null,
                null,
                new Description("House fire"),
                CallOutcome.INCIDENT_CREATED,
                null,
                incidentId
        );

        var logEntry = new IncidentLogEntry.AutomaticEntry(
                logEntryId,
                now,
                dispatcher,
                JsonNodeFactory.instance.objectNode().put("type", "call_linked")
        );
        var incidentSnapshot = new IncidentSnapshot(
                incidentId,
                IncidentState.NEW,
                now,
                new IncidentType("FIRE"),
                IncidentPriority.A,
                null,
                new Description("House fire on Main Street"),
                List.of(logEntry)
        );

        var state = new OperationalState(List.of(incidentSnapshot), List.of(callSnapshot), List.of());
        adapter.createSnapshot(state, new SequenceNumber(42));

        var loaded = adapter.loadLatestSnapshot().orElseThrow();
        assertEquals(42L, loaded.sequenceNumber().value());

        // Verify call round-trips correctly
        assertEquals(1, loaded.state().calls().size());
        var loadedCall = loaded.state().calls().iterator().next().toCall();
        assertEquals(callId, loadedCall.id());
        assertEquals(CallState.ACTIVE, loadedCall.state());
        assertEquals(dispatcher, loadedCall.receivingDispatcher());
        assertEquals(now, loadedCall.callStarted());
        assertNull(loadedCall.callEnded());
        assertEquals("John Doe", loadedCall.callerName().value());
        assertNull(loadedCall.callerPhoneNumber());
        assertNull(loadedCall.location());
        assertEquals("House fire", loadedCall.description().value());
        assertEquals(CallOutcome.INCIDENT_CREATED, loadedCall.outcome());
        assertNull(loadedCall.outcomeRationale());
        assertEquals(incidentId, loadedCall.incidentId());

        // Verify incident round-trips correctly
        assertEquals(1, loaded.state().incidents().size());
        var loadedIncident = loaded.state().incidents().iterator().next().toIncident();
        assertEquals(incidentId, loadedIncident.id());
        assertEquals(IncidentState.NEW, loadedIncident.state());
        assertEquals(now, loadedIncident.incidentCreated());
        assertEquals("FIRE", loadedIncident.incidentType().code());
        assertEquals(IncidentPriority.A, loadedIncident.incidentPriority());
        assertNull(loadedIncident.location());
        assertEquals("House fire on Main Street", loadedIncident.description().value());
        assertEquals(1, loadedIncident.logEntries().size());
        var loadedEntry = (IncidentLogEntry.AutomaticEntry) loadedIncident.logEntries().getFirst();
        assertEquals(logEntryId, loadedEntry.id());
        assertEquals("call_linked", loadedEntry.changeData().get("type").asText());
    }
}
