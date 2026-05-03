package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.EventId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Minimal {@link DomainEvent} record used in WAL and snapshot adapter tests.
 * Not for production use.
 */
public record TestDomainEvent(
        EventId eventId,
        Instant timestamp,
        @Nullable CommandId causedBy,
        String payload
) implements DomainEvent {

    public TestDomainEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
    }

    public static TestDomainEvent of(String payload) {
        return new TestDomainEvent(EventId.generate(), Instant.now(), null, payload);
    }

    public static TestDomainEvent of(String payload, CommandId causedBy) {
        return new TestDomainEvent(EventId.generate(), Instant.now(), causedBy, payload);
    }
}
