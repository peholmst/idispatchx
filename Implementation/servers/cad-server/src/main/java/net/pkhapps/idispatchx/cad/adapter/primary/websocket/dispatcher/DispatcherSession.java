package net.pkhapps.idispatchx.cad.adapter.primary.websocket.dispatcher;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.javalin.websocket.WsContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Holds the per-connection state for a single dispatcher WebSocket session.
 * <p>
 * A new {@code DispatcherSession} is created for each successful WebSocket upgrade.
 * It wraps the Javalin {@link WsContext} and serializes/sends WebSocket messages as JSON.
 * <p>
 * The {@code oidcSessionId} is the OIDC {@code sid} claim from the token; it is used by
 * {@link net.pkhapps.idispatchx.cad.adapter.broadcast.SessionRegistry} to find and close
 * all connections belonging to a revoked OIDC session.
 */
public final class DispatcherSession {

    private static final Logger log = LoggerFactory.getLogger(DispatcherSession.class);

    private final WsContext ctx;
    private final String sessionId;
    private final @Nullable String oidcSessionId;
    private final String userId;
    private final ObjectMapper objectMapper;

    public DispatcherSession(WsContext ctx, String sessionId, @Nullable String oidcSessionId,
                             String userId, ObjectMapper objectMapper) {
        this.ctx = Objects.requireNonNull(ctx, "ctx must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.oidcSessionId = oidcSessionId;
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Returns the unique connection ID generated at upgrade time.
     */
    public String sessionId() {
        return sessionId;
    }

    /**
     * Returns the OIDC session ID ({@code sid} claim) from the token, or {@code null} if the
     * token did not carry a {@code sid} claim.
     */
    public @Nullable String oidcSessionId() {
        return oidcSessionId;
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
        } catch (JacksonException e) {
            log.error("Failed to serialize WebSocket message for session {}: {}", sessionId, e.getMessage(), e);
        } catch (Exception e) {
            log.warn("Failed to send WebSocket message to session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Closes the WebSocket connection with the given status code and reason.
     *
     * @param code   the WebSocket close code
     * @param reason a human-readable close reason
     */
    public void close(int code, String reason) {
        try {
            ctx.closeSession(code, reason);
        } catch (Exception e) {
            log.warn("Failed to close session {}: {}", sessionId, e.getMessage());
        }
    }
}
