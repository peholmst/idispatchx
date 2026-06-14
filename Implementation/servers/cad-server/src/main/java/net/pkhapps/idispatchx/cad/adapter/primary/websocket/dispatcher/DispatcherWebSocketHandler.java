package net.pkhapps.idispatchx.cad.adapter.primary.websocket.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import net.pkhapps.idispatchx.cad.adapter.broadcast.DispatcherBroadcastService;
import net.pkhapps.idispatchx.cad.adapter.broadcast.SessionRegistry;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;
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
 * On successful connect, a {@code connected} message is sent to the client with the
 * current WAL sequence number. The session is registered in {@link SessionRegistry}
 * — keyed by a generated connection ID and also indexed by the OIDC {@code sid} claim
 * so that back-channel logout can close active connections immediately.
 * On close or error, the session is unregistered.
 * <p>
 * Per ADR-0005: WebSocket sessions are not preserved across failover. On reconnect the client
 * must re-authenticate and re-fetch state via REST.
 */
public final class DispatcherWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DispatcherWebSocketHandler.class);
    private static final String SESSION_ID_ATTR = "dispatcher.connectionId";

    private final TokenValidator tokenValidator;
    private final SessionStore sessionStore;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final WalPort walPort;

    public DispatcherWebSocketHandler(
            TokenValidator tokenValidator,
            SessionStore sessionStore,
            SessionRegistry sessionRegistry,
            ObjectMapper objectMapper,
            WalPort walPort) {
        this.tokenValidator = Objects.requireNonNull(tokenValidator, "tokenValidator must not be null");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore must not be null");
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.walPort = Objects.requireNonNull(walPort, "walPort must not be null");
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

                var connectionId = UUID.randomUUID().toString();
                ctx.attribute(SESSION_ID_ATTR, connectionId);

                var session = new DispatcherSession(ctx, connectionId, claims.sessionId(),
                        claims.subject(), objectMapper);
                sessionRegistry.registerDispatcherSession(session);
                log.info("Dispatcher WebSocket connected: connectionId={}, userId={}, oidcSessionId={}",
                        connectionId, claims.subject(), claims.sessionId());

                // Send connected event per spec section 6.1
                var currentSeq = walPort.currentSequence().value();
                session.send(Map.of(
                        "type", "connected",
                        "sequenceNumber", currentSeq,
                        "timestamp", Instant.now().toString(),
                        "payload", Map.of("serverTime", Instant.now().toString())
                ));
            });

            ws.onClose(ctx -> {
                var connectionId = ctx.<String>attribute(SESSION_ID_ATTR);
                if (connectionId != null) {
                    sessionRegistry.unregisterDispatcherSession(connectionId);
                    log.info("Dispatcher WebSocket disconnected: connectionId={}", connectionId);
                }
            });

            ws.onError(ctx -> {
                var connectionId = ctx.<String>attribute(SESSION_ID_ATTR);
                if (connectionId != null) {
                    sessionRegistry.unregisterDispatcherSession(connectionId);
                    log.warn("Dispatcher WebSocket error: connectionId={}", connectionId);
                }
            });
        });
    }
}
