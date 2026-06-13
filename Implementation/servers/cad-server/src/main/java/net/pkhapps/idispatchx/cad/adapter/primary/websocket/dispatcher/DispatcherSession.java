package net.pkhapps.idispatchx.cad.adapter.primary.websocket.dispatcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Holds the per-connection state for a single dispatcher WebSocket session.
 * <p>
 * A new {@code DispatcherSession} is created for each successful WebSocket upgrade.
 * It wraps the Javalin {@link WsContext} and serializes/sends WebSocket messages as JSON.
 */
public final class DispatcherSession {

    private static final Logger log = LoggerFactory.getLogger(DispatcherSession.class);

    private final WsContext ctx;
    private final String sessionId;
    private final String userId;
    private final ObjectMapper objectMapper;

    public DispatcherSession(WsContext ctx, String sessionId, String userId, ObjectMapper objectMapper) {
        this.ctx = Objects.requireNonNull(ctx, "ctx must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Returns the unique session ID (Javalin context session ID).
     */
    public String sessionId() {
        return sessionId;
    }

    /**
     * Returns the user ID (JWT {@code sub} claim) of the authenticated dispatcher.
     */
    public String userId() {
        return userId;
    }

    /**
     * Sends a WebSocket message to the client. Serializes the message to JSON.
     * If sending fails (e.g. the connection has closed), the error is logged and ignored.
     *
     * @param message the message to send
     */
    public void send(Object message) {
        try {
            var json = objectMapper.writeValueAsString(message);
            ctx.send(json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize WebSocket message for session {}: {}", sessionId, e.getMessage(), e);
        } catch (Exception e) {
            log.warn("Failed to send WebSocket message to session {}: {}", sessionId, e.getMessage());
        }
    }
}
