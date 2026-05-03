package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;

import java.io.IOException;
import java.util.List;

/**
 * {@link DomainEventSerializer} using Jackson SMILE compact binary encoding.
 * Intended for production use (WAL format {@link WalFormat#BINARY}).
 */
public final class SmileDomainEventSerializer implements DomainEventSerializer {

    private final ObjectMapper mapper;

    /**
     * @param eventTypes concrete {@link DomainEvent} subtypes to pre-register for deserialization
     */
    public SmileDomainEventSerializer(List<? extends Class<? extends DomainEvent>> eventTypes) {
        this.mapper = WalMapperFactory.buildSmile(eventTypes);
    }

    @Override
    public byte[] serialize(WalEntry entry) throws IOException {
        return mapper.writeValueAsBytes(
                new WalMapperFactory.WalEntryDocument(entry.sequenceNumber().value(), entry.event()));
    }

    @Override
    public WalEntry deserialize(byte[] data) throws IOException {
        var doc = mapper.readValue(data, WalMapperFactory.WalEntryDocument.class);
        return new WalEntry(new SequenceNumber(doc.seq), doc.event);
    }

    @Override
    public ObjectMapper objectMapper() {
        return mapper;
    }
}
