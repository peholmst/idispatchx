package net.pkhapps.idispatchx.cad.adapter.secondary.wal;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Serializes and deserializes {@link WalEntry} objects to/from bytes.
 * <p>
 * Two implementations are provided: {@link JsonDomainEventSerializer} (text/JSON) and
 * {@link SmileDomainEventSerializer} (SMILE binary). Both produce identical logical content;
 * only the encoding differs.
 * <p>
 * Framing (newlines for text, length-prefix for binary) is handled by the WAL adapter,
 * not by this interface.
 */
public interface DomainEventSerializer {

    /**
     * Serializes a WAL entry to bytes. No framing bytes are included.
     *
     * @param entry the entry to serialize
     * @return the serialized bytes
     * @throws IOException if serialization fails
     */
    byte[] serialize(WalEntry entry) throws IOException;

    /**
     * Deserializes bytes (previously produced by {@link #serialize}) back to a WAL entry.
     *
     * @param data the raw bytes (no framing)
     * @return the deserialized entry
     * @throws IOException if deserialization fails, including unknown event type
     */
    WalEntry deserialize(byte[] data) throws IOException;

    /**
     * Returns the configured {@link ObjectMapper} used by this serializer.
     * Exposed for use by the snapshot adapter, which shares the same type registry.
     */
    ObjectMapper objectMapper();
}
