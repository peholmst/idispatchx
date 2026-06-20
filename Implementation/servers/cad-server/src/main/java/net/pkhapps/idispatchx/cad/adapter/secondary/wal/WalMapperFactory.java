package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.smile.SmileMapper;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntry;
import net.pkhapps.idispatchx.cad.domain.model.shared.location.Location;

import java.util.List;

/**
 * Factory for creating {@link ObjectMapper} instances configured for WAL and snapshot serialization.
 * <p>
 * Uses a mixin to add {@code @JsonTypeInfo} to the {@link DomainEvent} interface without
 * modifying the domain model. The {@code @type} property stores the fully qualified class name,
 * allowing deserialization of any {@link DomainEvent} subtype on the classpath.
 * <p>
 * Separate mixins add polymorphic type handling for the {@link Location} and
 * {@link net.pkhapps.idispatchx.cad.domain.model.incident.IncidentLogEntry} sealed interfaces,
 * using a {@code "@type"} discriminator consistent with the {@link DomainEvent} mixin.
 */
final class WalMapperFactory {

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@type")
    private interface DomainEventMixin {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Location.ExactAddress.class, name = "exact_address"),
            @JsonSubTypes.Type(value = Location.RoadIntersection.class, name = "road_intersection"),
            @JsonSubTypes.Type(value = Location.NamedPlace.class, name = "named_place"),
            @JsonSubTypes.Type(value = Location.RelativeLocation.class, name = "relative_location")
    })
    private interface LocationMixin {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = IncidentLogEntry.AutomaticEntry.class, name = "automatic"),
            @JsonSubTypes.Type(value = IncidentLogEntry.ManualEntry.class, name = "manual")
    })
    private interface IncidentLogEntryMixin {
    }

    /** Document shape stored per WAL entry: {seq, event}. */
    static final class WalEntryDocument {
        public long seq;
        public DomainEvent event;

        @SuppressWarnings("NullAway") // Fields are populated by Jackson after construction
        WalEntryDocument() {
        }

        WalEntryDocument(long seq, DomainEvent event) {
            this.seq = seq;
            this.event = event;
        }
    }

    private WalMapperFactory() {
    }

    /**
     * Builds an {@link ObjectMapper} for the {@link WalFormat#TEXT} (JSON) format.
     *
     * @param eventTypes concrete {@link DomainEvent} subtypes to pre-register
     */
    static ObjectMapper buildJson(List<? extends Class<? extends DomainEvent>> eventTypes) {
        var builder = JsonMapper.builder()
                .addMixIn(DomainEvent.class, DomainEventMixin.class)
                .addMixIn(Location.class, LocationMixin.class)
                .addMixIn(IncidentLogEntry.class, IncidentLogEntryMixin.class);
        if (!eventTypes.isEmpty()) {
            builder.registerSubtypes(eventTypes.toArray(new Class[0]));
        }
        return builder.build();
    }

    /**
     * Builds an {@link ObjectMapper} for the {@link WalFormat#BINARY} (SMILE) format.
     *
     * @param eventTypes concrete {@link DomainEvent} subtypes to pre-register
     */
    static ObjectMapper buildSmile(List<? extends Class<? extends DomainEvent>> eventTypes) {
        var builder = SmileMapper.builder()
                .addMixIn(DomainEvent.class, DomainEventMixin.class)
                .addMixIn(Location.class, LocationMixin.class)
                .addMixIn(IncidentLogEntry.class, IncidentLogEntryMixin.class);
        if (!eventTypes.isEmpty()) {
            builder.registerSubtypes(eventTypes.toArray(new Class[0]));
        }
        return builder.build();
    }
}
