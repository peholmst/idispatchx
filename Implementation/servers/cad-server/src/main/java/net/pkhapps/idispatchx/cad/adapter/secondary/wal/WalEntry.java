package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;

import java.util.Objects;

/**
 * An internal record pairing a WAL sequence number with its domain event.
 * Not part of the public port API.
 */
record WalEntry(SequenceNumber sequenceNumber, DomainEvent event) {

    WalEntry {
        Objects.requireNonNull(sequenceNumber, "sequenceNumber must not be null");
        Objects.requireNonNull(event, "event must not be null");
    }
}
