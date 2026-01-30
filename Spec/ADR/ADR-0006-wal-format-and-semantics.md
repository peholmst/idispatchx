# ADR-0006: WAL Format and Semantics

## Status
Accepted

## Context

CAD Server stores operational state in memory, persisted via a Write-Ahead Log
(WAL) as described in C4 Containers (lines 59-61). The WAL supports:

* Development debugging with human-readable formats
* Production efficiency with compact binary encoding
* High-availability failover per NFR Availability (lines 90-94)
* PII purging requirements per NFR Security (line 72)

This ADR documents the format options, data scope, snapshot behavior, replay
semantics, and purging strategy for the WAL.

## Decision

### WAL Format Configuration

CAD Server supports two WAL formats, selected at startup via configuration:

* **Text format**: Newline-delimited JSON for development and debugging
* **Binary format**: Compact encoding for production use

The specific binary encoding is an implementation detail and not specified here.
Both formats must support the same logical operations and replay semantics.

### Operational Data Only

The WAL contains only operational data:

* Calls (including caller information)
* Incidents (status, assignments, timestamps)
* Unit status and location updates

The WAL excludes reference data:

* Unit definitions
* Station configurations
* Incident types
* Other static configuration

Reference data is stored in separate configuration files that can be edited
and reloaded at runtime. The specific reload mechanism is an implementation
detail.

### Snapshots

Periodic snapshots capture the complete operational state:

* Snapshots are written atomically using a temporary file followed by rename
* Each snapshot includes the sequence number of the last WAL entry it contains
* Snapshots use the same format (text or binary) as the WAL

### Startup and Replay

On startup, CAD Server performs the following sequence:

1. Load reference data from configuration files
2. Load the latest valid snapshot (if present)
3. Replay WAL entries starting from the snapshot's sequence number

Corruption handling is configurable:

* **Strict mode**: Halt immediately on the first corrupt WAL entry
* **Lenient mode**: Log a warning, skip corrupt entries, and continue replay

The appropriate mode depends on deployment requirements and operational
procedures.

### Warm Standby Behavior

The warm standby continuously monitors the WAL for new entries and loads
new snapshots as they are created.

On failover:

1. Replay any remaining WAL entries not yet processed
2. Begin accepting client connections

This enables rapid takeover as specified in NFR Availability (line 90).

### Purging Strategy

After a snapshot is successfully written:

* WAL entries preceding the snapshot's sequence number may be purged
* Older snapshots may be purged

Purging is performed asynchronously and must not block normal operations.

PII contained in calls and incidents must be purged from the WAL and snapshots
once the data has been archived to the CAD Archive database, per NFR Security
(line 72).

## Consequences

* Two format implementations are required (text/JSON and binary)
* Snapshot I/O adds overhead during normal operation
* Shared storage remains a single point of failure (per NFR Availability line 18)
* Reference data must be synchronized between primary and standby nodes
* Corruption handling mode choice affects recovery behavior and data integrity guarantees

## Cross-References

* [C4 Containers](../C4/Containers.md) — WAL storage and PostgreSQL archive (lines 59-61)
* [NFR Availability](../NonFunctionalRequirements/Availability.md) — Shared storage assumption (line 18), CAD Server failover (lines 90-94)
* [NFR Security](../NonFunctionalRequirements/Security.md) — PII purging after archival (line 72)
* [ADR-0005](ADR-0005-session-state-during-failover.md) — No authentication state in WAL
