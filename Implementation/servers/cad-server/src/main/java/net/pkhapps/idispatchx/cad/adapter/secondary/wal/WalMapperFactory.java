package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.shared.location.Location;

import java.util.List;

/**
 * Factory for creating {@link ObjectMapper} instances configured for WAL and snapshot serialization.
 * <p>
 * Uses a mixin to add {@code @JsonTypeInfo} to the {@link DomainEvent} interface without
 * modifying the domain model. The {@code @type} property stores the fully qualified class name,
 * allowing deserialization of any {@link DomainEvent} subtype on the classpath.
 * <p>
 * A separate mixin adds polymorphic type handling for the {@link Location} sealed interface,
 * using a {@code "type"} discriminator matching the REST API format.
 */
final class WalMapperFactory {

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@type")
    private interface DomainEventMixin {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Location.ExactAddress.class, name = "exact_address"),
            @JsonSubTypes.Type(value = Location.RoadIntersection.class, name = "road_intersection"),
            @JsonSubTypes.Type(value = Location.NamedPlace.class, name = "named_place"),
            @JsonSubTypes.Type(value = Location.RelativeLocation.class, name = "relative_location")
    })
    private interface LocationMixin {
    }

    /** Document shape stored per WAL entry: {seq, event}. */
    static final class WalEntryDocument {
        public long seq;
        public DomainEvent event;

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
        return configure(new ObjectMapper(), eventTypes);
    }

    /**
     * Builds an {@link ObjectMapper} for the {@link WalFormat#BINARY} (SMILE) format.
     *
     * @param eventTypes concrete {@link DomainEvent} subtypes to pre-register
     */
    static ObjectMapper buildSmile(List<? extends Class<? extends DomainEvent>> eventTypes) {
        return configure(new ObjectMapper(new SmileFactory()), eventTypes);
    }

    private static ObjectMapper configure(ObjectMapper mapper, List<? extends Class<? extends DomainEvent>> eventTypes) {
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.addMixIn(DomainEvent.class, DomainEventMixin.class);
        mapper.addMixIn(Location.class, LocationMixin.class);
        if (!eventTypes.isEmpty()) {
            mapper.registerSubtypes(eventTypes.toArray(new Class[0]));
        }
        return mapper;
    }
}
