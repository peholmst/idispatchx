package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;

import java.util.List;

/**
 * Factory for creating {@link ObjectMapper} instances configured for WAL and snapshot serialization.
 * <p>
 * Uses a mixin to add {@code @JsonTypeInfo} to the {@link DomainEvent} interface without
 * modifying the domain model. The {@code @type} property stores the fully qualified class name,
 * allowing deserialization of any {@link DomainEvent} subtype on the classpath.
 */
final class WalMapperFactory {

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@type")
    private interface DomainEventMixin {
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
        if (!eventTypes.isEmpty()) {
            mapper.registerSubtypes(eventTypes.toArray(new Class[0]));
        }
        return mapper;
    }
}
