# CAD Server WAL and Snapshot Ports — Implementation Plan (Issue #72)

## References

- [Issue #72](https://github.com/peholmst/iDispatchX/issues/72) — Implement WalPort and SnapshotPort
- [ADR-0006: WAL Format and Semantics](../ADR/ADR-0006-wal-format-and-semantics.md) — Primary specification; read before implementing
- [ADR-0008: CAD Server Ports-and-Adapters Architecture](../ADR/ADR-0008-cad-server-ports-and-adapters.md) — Hexagonal architecture rules
- [Technical Design: CAD Server Domain Core](../TechnicalDesigns/CAD-Server-Domain-Core.md) — Port interfaces (§7.1, §7.5), startup sequence (§11)
- [NFR: Performance](../NonFunctionalRequirements/Performance.md) — WAL sync must precede state update
- [NFR: Availability](../NonFunctionalRequirements/Availability.md) — Warm standby and failover (lines 90–94)
- [NFR: Security](../NonFunctionalRequirements/Security.md) — PII purging after archival (line 72)

## Overview

This plan implements file-based adapters for `WalPort` and `SnapshotPort` in the CAD Server's secondary adapter layer. Both adapters share a common serialization layer that supports two formats: newline-delimited JSON (text, for development) and Jackson SMILE (binary, for production). The WAL uses segmented files for O(1) truncation. Snapshots are written atomically via a temporary file followed by a rename.

| Phase | Description | Tasks |
|-------|-------------|-------|
| 1 | Serialization layer | 3 |
| 2 | WAL adapter | 4 |
| 3 | Snapshot adapter | 3 |
| 4 | End-to-end verification | 1 |

> **Domain model dependency:** `Incident`, `Call`, and `UnitStatus` are currently placeholders. Full snapshot serialization of `OperationalState` depends on their implementation (future issue). Phase 3 implements the infrastructure skeleton and tests with empty collections. No tasks in this plan are blocked by this — Phase 3 proceeds with the current placeholders.

---

## Phase 1 — Serialization Layer

The serialization layer translates `DomainEvent` objects to and from bytes. Both the WAL adapter and snapshot adapter share this layer to guarantee format consistency (per ADR-0006).

### Task 1.1 — Add Jackson dependencies

**Status:** Not Started

**Description:** Add `jackson-datatype-jsr310` (required for `Instant` serialization) and `jackson-dataformat-smile` (required for binary format) to the build.

**Files to modify:**
- `Implementation/pom.xml` — add `jackson-dataformat-smile` to `<dependencyManagement>` at version `${jackson.version}`
- `Implementation/servers/cad-server/pom.xml` — add `jackson-datatype-jsr310` and `jackson-dataformat-smile` as dependencies (no version, managed by parent)

**Acceptance criteria:**
- Both dependencies compile and resolve without errors.
- `SmileFactory` from `jackson-dataformat-smile` is accessible in the adapter package.
- `JavaTimeModule` from `jackson-datatype-jsr310` serializes `Instant` as ISO-8601 strings.

**Dependencies:** None.

---

### Task 1.2 — Implement `DomainEventSerializer`

**Status:** Not Started

**Description:** Define the serialization contract and its two implementations. The interface handles single WAL entries (sequence number + event). To keep Jackson annotations out of the domain model, use Jackson mixin annotations applied at the adapter level.

**New files:**
- `Implementation/servers/cad-server/src/main/java/net/pkhapps/idispatchx/cad/adapter/secondary/wal/WalEntry.java`
- `Implementation/servers/cad-server/src/main/java/net/pkhapps/idispatchx/cad/adapter/secondary/wal/DomainEventSerializer.java`
- `Implementation/servers/cad-server/src/main/java/net/pkhapps/idispatchx/cad/adapter/secondary/wal/JsonDomainEventSerializer.java`
- `Implementation/servers/cad-server/src/main/java/net/pkhapps/idispatchx/cad/adapter/secondary/wal/SmileDomainEventSerializer.java`
- `Implementation/servers/cad-server/src/main/java/net/pkhapps/idispatchx/cad/adapter/secondary/wal/package-info.java`

**`WalEntry`** — internal record, not part of the port API:
```java
record WalEntry(SequenceNumber sequenceNumber, DomainEvent event) {}
```

**`DomainEventSerializer`** interface:
```java
interface DomainEventSerializer {
    /**
     * Serializes a WAL entry to bytes.
     * For JSON: returns UTF-8 encoded JSON object.
     * For SMILE: returns SMILE-encoded equivalent.
     */
    byte[] serialize(WalEntry entry) throws IOException;

    /**
     * Deserializes bytes back to a WAL entry.
     */
    WalEntry deserialize(byte[] data) throws IOException;

    /**
     * Exposes the configured ObjectMapper for use by the snapshot adapter.
     */
    ObjectMapper objectMapper();
}
```

**Serialized JSON shape per entry:**
```json
{"seq": 1, "type": "net.pkhapps.idispatchx.cad.domain.event.SomeEvent", "data": {...}}
```

The `type` field holds the fully qualified class name. The `data` field holds the serialized event.

**Polymorphic type handling:** Since `DomainEvent` is an interface, Jackson needs help. Apply a mixin in the adapter package:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@type")
interface DomainEventMixin {}
```

Register it with `mapper.addMixIn(DomainEvent.class, DomainEventMixin.class)`.

Concrete event classes are registered with `mapper.registerSubtypes(Class<?>...)` at adapter construction time. The serializer accepts a `List<Class<? extends DomainEvent>>` in its constructor for this purpose. This list will grow as domain events are implemented.

**`JsonDomainEventSerializer`**: Uses `ObjectMapper` with `JavaTimeModule`, the mixin above, and the registered event subtypes. Serializes to UTF-8 JSON bytes; deserializes from UTF-8 JSON bytes.

**`SmileDomainEventSerializer`**: Uses `ObjectMapper` constructed with `SmileFactory` — otherwise identical configuration to the JSON variant. Output is SMILE-encoded bytes; no additional framing is needed in this class (framing is handled by the WAL segment writer).

**Acceptance criteria:**
- A concrete `DomainEvent` implementation can be round-tripped through both serializers: `deserialize(serialize(entry)).equals(entry)`.
- `Instant` fields serialize to ISO-8601 strings in JSON; deserialized correctly.
- Deserializing bytes with an unknown type name throws `IOException` with a descriptive message.
- `SmileDomainEventSerializer` output is measurably smaller than `JsonDomainEventSerializer` output for a representative event.
- `objectMapper()` returns the same instance used for serialization (no copy).

**Dependencies:** Task 1.1.

---

### Task 1.3 — Unit tests for `DomainEventSerializer`

**Status:** Not Started

**Description:** Test both implementations with a test-only `DomainEvent` record defined inside the test class. This avoids depending on domain model implementation.

**New files:**
- `Implementation/servers/cad-server/src/test/java/net/pkhapps/idispatchx/cad/adapter/secondary/wal/JsonDomainEventSerializerTest.java`
- `Implementation/servers/cad-server/src/test/java/net/pkhapps/idispatchx/cad/adapter/secondary/wal/SmileDomainEventSerializerTest.java`

**Test cases (both serializers):**
- Round-trip with a test event: serialize then deserialize produces equal `WalEntry`.
- `Instant` fields survive round-trip without precision loss.
- Corrupted byte array throws `IOException`.
- Unknown type name in JSON throws `IOException` with the type name in the message.
- SMILE output is shorter than JSON output for same entry (SMILE test only).

**Dependencies:** Task 1.2.

---

## Phase 2 — WAL Adapter

### Task 2.1 — Define `WalFormat` and `CorruptionMode` enums, and `WalConfig`

**Status:** Not Started

**Description:** Create the configuration types for the WAL adapter.

**New files:**
- `Implementation/servers/cad-server/src/main/java/net/pkhapps/idispatchx/cad/adapter/secondary/wal/WalFormat.java`
- `Implementation/servers/cad-server/src/main/java/net/pkhapps/idispatchx/cad/adapter/secondary/wal/CorruptionMode.java`
- `Implementation/servers/cad-server/src/main/java/net/pkhapps/idispatchx/cad/adapter/secondary/wal/WalConfig.java`

**`WalFormat`** enum:
```java
public enum WalFormat {
    /** Newline-delimited JSON. For development and debugging. */
    TEXT,
    /** Jackson SMILE. Compact encoding for production. */
    BINARY
}
```

**`CorruptionMode`** enum:
```java
public enum CorruptionMode {
    /** Halt immediately on the first corrupt WAL entry. */
    STRICT,
    /** Log a warning, skip corrupt entries, and continue replay. */
    LENIENT
}
```

**`WalConfig`** record fields:
- `walDirectory` (`Path`, required) — directory for WAL segment files.
- `format` (`WalFormat`, optional, default `TEXT`) — encoding format.
- `corruptionMode` (`CorruptionMode`, optional, default `STRICT`) — replay corruption handling.
- `maxEntriesPerSegment` (`int`, optional, default `10_000`) — entries per segment file before rollover.

Follow the `ConfigLoader`-based builder pattern used by `CommandLogConfig`.

**Acceptance criteria:**
- Missing required `walDirectory` env var causes `ConfigurationException` at construction.
- Optional fields use the documented defaults when their env var is absent.
- `maxEntriesPerSegment` must be ≥ 1; construction fails with `IllegalArgumentException` otherwise.

**Dependencies:** None.

---

### Task 2.2 — Define `WalReplayException`

**Status:** Not Started

**Description:** Add the exception thrown on corrupt WAL entries in STRICT mode.

**New file:**
- `Implementation/servers/cad-server/src/main/java/net/pkhapps/idispatchx/cad/port/secondary/wal/WalReplayException.java`

Match the structure of `WalWriteException`: constructors for `(String message)` and `(String message, Throwable cause)`.

**Acceptance criteria:**
- Is a `RuntimeException`.
- Has message-only and message+cause constructors.

**Dependencies:** None.

---

### Task 2.3 — Implement `FileBasedWalAdapter`

**Status:** Not Started

**Description:** Implement `WalPort` using segmented files on disk. Segmented files enable O(1) truncation: deleting an entire segment file removes all its entries without rewriting the remaining log.

**New file:**
- `Implementation/servers/cad-server/src/main/java/net/pkhapps/idispatchx/cad/adapter/secondary/wal/FileBasedWalAdapter.java`

**Segment file naming convention:**

| Format | Pattern | Example |
|--------|---------|---------|
| TEXT | `wal-{firstSeq:016d}.ndjson` | `wal-0000000000000001.ndjson` |
| BINARY | `wal-{firstSeq:016d}.bin` | `wal-0000000000000001.bin` |

`{firstSeq}` is the sequence number of the first entry written to that segment. Sorting segment files lexicographically gives them in sequence order.

**Entry encoding within a segment:**

- TEXT: One UTF-8 JSON line per entry (newline-delimited). Each line is the output of `DomainEventSerializer.serialize()`.
- BINARY: One SMILE entry per record, preceded by a 4-byte big-endian length prefix encoding the byte count of the SMILE payload. Frame: `[int length][SMILE bytes]`.

**Write path (`write` and `writeBatch`):**
1. Acquire the instance write lock (`synchronized`).
2. Assign sequence number(s): increment `AtomicLong nextSequence` for each event.
3. Serialize all entries via `DomainEventSerializer`.
4. Record the current file position (for rollback on `writeBatch` failure).
5. Append all serialized entries to the current segment's `FileChannel`.
6. Call `FileChannel.force(true)` to flush data and metadata to disk.
7. On `force` failure in `writeBatch`: truncate the `FileChannel` back to the pre-batch position; throw `WalWriteException`.
8. Update `currentSeq` `AtomicLong` to the last assigned sequence.
9. If current segment has reached `maxEntriesPerSegment` after the write, close the segment and record the rollover state so the next write opens a new segment.
10. Release the lock.

**Replay path (`replay` and `replayFrom`):**
1. List all segment files in `walDirectory`, sorted by `firstSeq` (ascending). Segment files with unrecognized extensions are ignored.
2. For `replayFrom(from, consumer)`: skip segments whose entries are entirely ≤ `from` (i.e., the segment's last possible sequence is ≤ `from.value`). Since segments have `maxEntriesPerSegment` entries, the last sequence in segment starting at `S` is at most `S + maxEntriesPerSegment - 1`. Skip if `S + maxEntriesPerSegment - 1 <= from.value`.
   - To avoid reading a segment unnecessarily, also check the first sequence of the _next_ segment: if `nextSegmentFirstSeq <= from.value`, the current segment contains only entries ≤ `from`.
3. For each relevant segment file, open a reader and iterate entries in order.
4. For each entry:
   - TEXT: read one line; attempt `DomainEventSerializer.deserialize(line.getBytes(UTF_8))`.
   - BINARY: read 4-byte length; read that many bytes; attempt `DomainEventSerializer.deserialize(payload)`.
   - For `replayFrom`: skip entries with `sequenceNumber <= from`.
   - On deserialization failure: STRICT → throw `WalReplayException`; LENIENT → log WARN with sequence number and skip.
5. Deliver valid events to `consumer` in sequence order.
6. Replay is not synchronized with the write lock; it reads immutable closed segments and the tail of the current open segment sequentially. Concurrent writes append to the current segment and do not disturb replay of already-written entries.

**`truncate(upTo)`:**
1. Collect all segment files whose entire content is ≤ `upTo`. A segment starting at `S` with next segment starting at `S'` is fully truncatable if `S' - 1 <= upTo.value`. The current (open) segment is never truncated.
2. Schedule deletion of collected files via a `ScheduledExecutorService` or `CompletableFuture.runAsync()`. Return immediately; do not block.
3. Log INFO for each deleted file.

**`currentSequence()`:**
- Return `SequenceNumber` from the `currentSeq` `AtomicLong`.
- If the WAL is empty (no writes yet and no prior state loaded), return `SequenceNumber.start()`.

**Initialisation:**
- On construction, scan `walDirectory` for existing segment files.
- If any exist, set `currentSeq` to the highest sequence number found (by reading the last entry of the newest segment, or by counting entries via segment naming heuristic).
- Open the newest segment for append, or create the initial segment.

**Closing:**
- Implement `AutoCloseable`. `close()` flushes and closes the current open `FileChannel`. Truncation tasks in flight are not cancelled (they are harmless after close).

**Thread safety:**
- `write` and `writeBatch` are `synchronized`.
- `replay` and `replayFrom` are not synchronized; they read closed segments and scan the current segment's already-written bytes.
- `truncate` schedules deletion without holding a lock.

**Acceptance criteria:**
- Empty WAL: `currentSequence()` returns `SequenceNumber.start()`, `replay()` delivers no events, `replayFrom(start, c)` delivers no events.
- After writing N events: `currentSequence()` returns `SequenceNumber(N)`.
- `write(event)` blocks until `FileChannel.force(true)` returns.
- `writeBatch(events)` is atomic: if `force` throws, no events from the batch appear in subsequent `replay`.
- `replay()` delivers all events in ascending sequence order.
- `replayFrom(seq, consumer)` delivers only events with sequence number > `seq`.
- After `maxEntriesPerSegment` writes, a second segment file exists in `walDirectory`.
- `truncate(upTo)` eventually deletes segment files that are fully within `[1..upTo]`, without deleting the current open segment.
- STRICT mode: a corrupt entry causes `WalReplayException` during `replay` or `replayFrom`.
- LENIENT mode: a corrupt entry is skipped; a WARN is logged; subsequent valid entries are delivered.
- TEXT and BINARY configurations produce identical replay behaviour (same events, same order).
- Concurrent `write()` calls do not produce duplicate sequence numbers; all written events appear in replay.
- Re-opening the adapter (new instance, same directory) restores `currentSequence()` to the previously highest written value and resumes appending to the correct segment.

**Dependencies:** Tasks 1.2, 2.1, 2.2.

---

### Task 2.4 — Tests for `FileBasedWalAdapter`

**Status:** Not Started

**Description:** Unit and integration tests using JUnit 5 `@TempDir`.

**New file:**
- `Implementation/servers/cad-server/src/test/java/net/pkhapps/idispatchx/cad/adapter/secondary/wal/FileBasedWalAdapterTest.java`

**Test cases:**
1. Empty WAL — `currentSequence()`, `replay()`, `replayFrom()` behaviour.
2. Single event write, `replay()` receives it.
3. Batch write, `replay()` receives all in order.
4. `replayFrom(seq, c)` skips events ≤ `seq`, delivers events after `seq`.
5. Segment rollover: write `maxEntriesPerSegment + 1` events; verify two segment files exist; verify all events replayed.
6. Truncate: write events 1–20, truncate to 10, replay delivers 11–20.
7. STRICT corruption: inject corrupt bytes into the segment file; `replay()` throws `WalReplayException`.
8. LENIENT corruption: inject corrupt bytes; `replay()` delivers valid entries, skips corrupted ones.
9. Re-open: write events, close adapter, open new adapter on same directory; `currentSequence()` correct, `replay()` delivers all events.
10. Concurrent writes: multiple threads calling `write()` simultaneously; no duplicate sequences; all events present in final `replay()`.
11. TEXT and BINARY formats both pass tests 2–5.

**Dependencies:** Task 2.3.

---

## Phase 3 — Snapshot Adapter

### Task 3.1 — Define `SnapshotConfig`

**Status:** Not Started

**Description:** Configuration record for the snapshot adapter. Format must be consistent with the WAL.

**New file:**
- `Implementation/servers/cad-server/src/main/java/net/pkhapps/idispatchx/cad/adapter/secondary/snapshot/SnapshotConfig.java`

**Fields:**
- `snapshotDirectory` (`Path`, required) — directory for snapshot files.
- `format` (`WalFormat`, required) — must match the WAL format per ADR-0006.

Follow the `ConfigLoader`-based builder pattern.

**Acceptance criteria:**
- Missing required env vars cause `ConfigurationException`.
- Builder consistent with `CommandLogConfig`.

**Dependencies:** Task 2.1 (for `WalFormat`).

---

### Task 3.2 — Implement `FileBasedSnapshotAdapter`

**Status:** Not Started

**Description:** Implement `SnapshotPort` using the same `ObjectMapper` as the WAL serializer, ensuring format consistency.

**New files:**
- `Implementation/servers/cad-server/src/main/java/net/pkhapps/idispatchx/cad/adapter/secondary/snapshot/FileBasedSnapshotAdapter.java`
- `Implementation/servers/cad-server/src/main/java/net/pkhapps/idispatchx/cad/adapter/secondary/snapshot/package-info.java`

**Snapshot file naming convention:**

| Format | Pattern | Example |
|--------|---------|---------|
| TEXT | `snapshot-{seq:016d}.json` | `snapshot-0000000000000500.json` |
| BINARY | `snapshot-{seq:016d}.bin` | `snapshot-0000000000000500.bin` |

Temporary files during write: append `.tmp` suffix before rename (e.g. `snapshot-0000000000000500.json.tmp`).

**Serialized snapshot shape (JSON text format):**
```json
{
  "sequenceNumber": 500,
  "incidents": [...],
  "calls": [...],
  "unitStatuses": [...]
}
```

**`createSnapshot(state, upToSequence)`:**
1. Determine the temp filename and final filename.
2. Serialize `OperationalState` plus `upToSequence` to a `SnapshotDocument` POJO using the `ObjectMapper` from `DomainEventSerializer`. For TEXT: write UTF-8 JSON. For BINARY: write SMILE.
3. Write to temp file, calling `FileChannel.force(true)` before closing.
4. Atomically rename temp file to final filename using `Files.move(..., ATOMIC_MOVE)`.
5. On any failure: delete temp file (best-effort) and throw `SnapshotWriteException`.

**`loadLatestSnapshot()`:**
1. List all snapshot files in `snapshotDirectory` with the correct extension, sorted by sequence number descending.
2. For each snapshot file (newest first):
   - Attempt to deserialize into `Snapshot(state, sequenceNumber)`.
   - If successful, return `Optional.of(snapshot)`.
   - If deserialization fails (corrupt file): log WARN with filename, try next.
3. Return `Optional.empty()` if no valid snapshot found.

**`purgeOlderSnapshots(keepAfter)`:**
1. List snapshot files with sequence number < `keepAfter.value`.
2. Delete each file asynchronously via `CompletableFuture.runAsync()`.
3. Return immediately; do not block.

**Domain object serialization note:** `Incident`, `Call`, and `UnitStatus` are currently placeholders with private constructors and no fields. The adapter must be structured so that their serialization is delegated to the same `ObjectMapper`. When these classes are fully implemented, mixins can be added in the snapshot adapter package (e.g. `IncidentMixin`) to keep Jackson annotations out of the domain model. For now, empty collections are all that exists in `OperationalState`, so the serialization round-trips correctly.

**Acceptance criteria:**
- `createSnapshot()` writes a `.tmp` file, then renames it atomically; no partial snapshot file is left if the rename fails.
- The temp file is cleaned up on failure.
- `loadLatestSnapshot()` returns the snapshot with the highest sequence number that deserializes correctly.
- On a corrupt snapshot file: `loadLatestSnapshot()` logs WARN and tries the next oldest.
- `purgeOlderSnapshots(keepAfter)` eventually deletes snapshot files with sequence < `keepAfter.value` without deleting the snapshot at `keepAfter`.
- A snapshot created with TEXT format cannot be loaded when the adapter is configured for BINARY (and vice versa): file extension mismatch means it is not listed.
- Round-trip test: create snapshot from `OperationalState.empty()`, load it, verify state equals `OperationalState.empty()` and sequence matches.

**Dependencies:** Tasks 1.2, 3.1.

---

### Task 3.3 — Tests for `FileBasedSnapshotAdapter`

**Status:** Not Started

**Description:** Integration tests using `@TempDir`.

**New file:**
- `Implementation/servers/cad-server/src/test/java/net/pkhapps/idispatchx/cad/adapter/secondary/snapshot/FileBasedSnapshotAdapterTest.java`

**Test cases:**
1. No snapshots: `loadLatestSnapshot()` returns empty.
2. Create snapshot, reload it: state and sequence match.
3. Create two snapshots; `loadLatestSnapshot()` returns the newer one.
4. Corrupt the newest snapshot file; `loadLatestSnapshot()` falls back to the older one.
5. `purgeOlderSnapshots(seq)`: files with sequence < `seq` deleted; file at `seq` retained.
6. Atomic write: simulate failure by making `snapshotDirectory` read-only after write but before rename; verify no final snapshot file exists (only `.tmp` which is cleaned up).
7. TEXT and BINARY formats both pass tests 2–5.

**Dependencies:** Task 3.2.

---

## Phase 4 — End-to-End Verification

### Task 4.1 — WAL + Snapshot startup/replay integration test

**Status:** Not Started

**Description:** Exercise the full startup sequence from Technical Design §11.1: write events to WAL → create snapshot → truncate WAL → simulate startup (load snapshot + replay remaining WAL entries) → verify reconstructed state.

**New file:**
- `Implementation/servers/cad-server/src/test/java/net/pkhapps/idispatchx/cad/adapter/secondary/WalAndSnapshotIntegrationTest.java`

**Scenario (using test-only `DomainEvent` implementations and a test-only replay handler):**
1. Write events 1–20 to `FileBasedWalAdapter`.
2. Record that a snapshot was taken at sequence 15 (representing state after events 1–15).
3. Create snapshot via `FileBasedSnapshotAdapter.createSnapshot(state, SequenceNumber(15))`.
4. Truncate WAL via `FileBasedWalAdapter.truncate(SequenceNumber(15))`.
5. Simulate startup:
   a. `snapshotAdapter.loadLatestSnapshot()` → returns snapshot at seq 15.
   b. `walAdapter.replayFrom(SequenceNumber(15), consumer)` → delivers events 16–20.
6. Verify exactly 5 events (16–20) delivered to consumer.
7. Verify `walAdapter.currentSequence()` = `SequenceNumber(20)`.

**Acceptance criteria:**
- Post-truncation replay delivers only events 16–20.
- No events 1–15 appear in post-truncation replay.
- Snapshot round-trips correctly.
- Both TEXT and BINARY configurations pass.

**Dependencies:** Tasks 2.3, 3.2.

---

## Execution Notes

**Critical path:** Phase 1 (serialization) must be complete before Phase 2 Task 2.3 and Phase 3 Task 3.2.

**Parallelisable tasks:**
- Tasks 1.1, 2.1, 2.2 have no dependencies and can be started immediately, in parallel.
- Tasks 2.1 and 3.1 can be written simultaneously once `WalFormat` exists.
- Task 1.3 (serializer tests) and Task 2.1 (WAL config) can proceed in parallel after Task 1.2.

**Recommended order for a single implementor:**
1. Tasks 1.1, 2.1, 2.2 (no-deps setup)
2. Task 1.2 (serialization implementation)
3. Task 1.3 (serializer tests)
4. Tasks 2.3, 3.1 (WAL adapter + snapshot config)
5. Tasks 2.4, 3.2 (WAL tests + snapshot adapter)
6. Task 3.3 (snapshot tests)
7. Task 4.1 (end-to-end)

**No concrete domain events exist yet.** All serializer tests and WAL tests use a test-only `DomainEvent` record defined within the test class. The production adapters are wired to accept an injectable list of event classes to register with Jackson; this list starts empty and grows as domain events are implemented in future issues.
