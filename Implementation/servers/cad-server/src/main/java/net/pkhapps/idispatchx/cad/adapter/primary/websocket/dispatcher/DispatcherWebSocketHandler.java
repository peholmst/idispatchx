package net.pkhapps.idispatchx.cad.adapter.primary.websocket.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import net.pkhapps.idispatchx.cad.adapter.broadcast.DispatcherBroadcastService;
import net.pkhapps.idispatchx.cad.adapter.broadcast.SessionRegistry;
import net.pkhapps.idispatchx.common.auth.Role;
import net.pkhapps.idispatchx.common.auth.SessionStore;
import net.pkhapps.idispatchx.common.auth.TokenClaims;
import net.pkhapps.idispatchx.common.auth.TokenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Javalin WebSocket handler for dispatcher client connections.
 * <p>
 * Connection URL: {@code ws://<host>/api/v1/ws/dispatcher?token=<jwt>}
 * <p>
 * Required role: {@code Dispatcher} or {@code Observer}.
 * The JWT is passed as a {@code ?token=} query parameter on the HTTP upgrade request.
 * <p>
 * On successful connect, a {@code connected} message is sent to the client. The session is
 * registered in {@link SessionRegistry} so that {@link DispatcherBroadcastService} can deliver
 * future events. On close or error, the session is unregistered.
 * <p>
 * Per ADR-0005: WebSocket sessions are not preserved across failover. On reconnect the client
 * must re-authenticate and re-fetch state via REST.
 */
public final class DispatcherWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DispatcherWebSocketHandler.class);
    private static final String SESSION_ID_ATTR = "dispatcer.sessionId";

    private final TokenValidator tokenValidator;
    private final SessionStore sessionStore;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public DispatcherWebSocketHandler(
            TokenValidator tokenValidator,
            SessionStore sessionStore,
            SessionRegistry sessionRegistry,
            ObjectMapper objectMapper) {
        this.tokenValidator = Objects.requireNonNull(tokenValidator, "tokenValidator must not be null");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore must not be null");
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Registers the dispatcher WebSocket endpoint.
     *
     * @param app         the Javalin application
     * @param contextPath configurable URL prefix (empty or starts with {@code /})
     */
    public void registerRoutes(Javalin app, String contextPath) {
        app.ws(contextPath + "/api/v1/ws/dispatcher", ws -> {
            ws.onConnect(ctx -> {
                var token = ctx.queryParam("token");
                if (token == null || token.isBlank()) {
                    log.debug("Dispatcher WebSocket connection rejected: missing token");
                    ctx.closeSession(4001, "Missing token");
                    return;
                }

                TokenClaims claims;
                try {
                    claims = tokenValidator.validate(token);
                } catch (Exception e) {
                    log.debug("Dispatcher WebSocket rejected: invalid token: {}", e.getMessage());
                    ctx.closeSession(4001, "Invalid token");
                    return;
                }

                if (claims.sessionId() != null && sessionStore.isRevoked(claims.sessionId())) {
                    log.debug("Dispatcher WebSocket rejected: revoked session for user {}", claims.subject());
                    ctx.closeSession(4001, "Session revoked");
                    return;
                }

                if (!claims.hasAnyRole(Role.DISPATCHER, Role.OBSERVER)) {
                    log.debug("Dispatcher WebSocket rejected: insufficient role for user {}", claims.subject());
                    ctx.closeSession(4003, "Insufficient permissions");
                    return;
                }

                var sessionId = UUID.randomUUID().toString();
                ctx.attribute(SESSION_ID_ATTR, sessionId);

                var session = new DispatcherSession(ctx, sessionId, claims.subject(), objectMapper);
                sessionRegistry.registerDispatcherSession(session);
                log.info("Dispatcher WebSocket connected: sessionId={}, userId={}", sessionId, claims.subject());

                // Send connected event per spec section 6.1
                session.send(Map.of(
                        "type", "connected",
                        "sequenceNumber", 0,
                        "timestamp", Instant.now().toString(),
                        "payload", Map.of("serverTime", Instant.now().toString())
                ));
            });

            ws.onClose(ctx -> {
                var sessionId = ctx.<String>attribute(SESSION_ID_ATTR);
                if (sessionId != null) {
                    sessionRegistry.unregisterDispatcherSession(sessionId);
                    log.info("Dispatcher WebSocket disconnected: sessionId={}", sessionId);
                }
            });

            ws.onError(ctx -> {
                var sessionId = ctx.<String>attribute(SESSION_ID_ATTR);
                if (sessionId != null) {
                    sessionRegistry.unregisterDispatcherSession(sessionId);
                    log.warn("Dispatcher WebSocket error: sessionId={}", sessionId);
                }
            });
        });
    }
}
