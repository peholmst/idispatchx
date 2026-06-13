package net.pkhapps.idispatchx.cad.adapter.primary.websocket.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.pkhapps.idispatchx.cad.adapter.broadcast.DispatcherBroadcastService;
import net.pkhapps.idispatchx.cad.adapter.broadcast.SessionRegistry;
import net.pkhapps.idispatchx.common.auth.SessionStore;
import net.pkhapps.idispatchx.common.auth.TokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DispatcherWebSocketHandler} focused on the session registry and
 * broadcast service behaviour rather than requiring a live WebSocket connection.
 * <p>
 * Full WebSocket integration is verified by {@link TaskNineIntegrationTest} in
 * the acceptance-test suite.
 */
class DispatcherWebSocketHandlerTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-06-13T10:00:00Z");

    private SessionRegistry sessionRegistry;
    private DispatcherBroadcastService broadcastService;

    @BeforeEach
    void setUp() {
        sessionRegistry = new SessionRegistry();
        broadcastService = new DispatcherBroadcastService(sessionRegistry);
    }

    // -----------------------------------------------------------------------
    // SessionRegistry unit tests (Task 9.2 coverage)
    // -----------------------------------------------------------------------

    @Test
    void sessionRegistry_initiallyEmpty() {
        assertTrue(sessionRegistry.getDispatcherSessions().isEmpty());
    }

    @Test
    void sessionRegistry_collectionIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> sessionRegistry.getDispatcherSessions().clear());
    }

    @Test
    void sessionRegistry_unregisterNonExistent_doesNotThrow() {
        assertDoesNotThrow(() -> sessionRegistry.unregisterDispatcherSession("nonexistent-session"));
    }

    // -----------------------------------------------------------------------
    // DispatcherBroadcastService unit tests (Task 9.1 coverage)
    // -----------------------------------------------------------------------

    @Test
    void broadcastService_withNoSessions_doesNotThrow() {
        assertTrue(sessionRegistry.getDispatcherSessions().isEmpty());
        assertDoesNotThrow(() ->
                broadcastService.sendCallCreated(1L, Instant.now(), "payload"));
    }

    @Test
    void broadcastService_withNoSessions_allMethodsDoNotThrow() {
        assertDoesNotThrow(() -> {
            broadcastService.sendCallCreated(1L, Instant.now(), "p");
            broadcastService.sendCallUpdated(2L, Instant.now(), "p");
            broadcastService.sendCallEnded(3L, Instant.now(), "p");
            broadcastService.sendCallAttachedToIncident(4L, Instant.now(), "c1", "i1");
            broadcastService.sendCallDetachedFromIncident(5L, Instant.now(), "c1", "i1");
            broadcastService.sendIncidentCreated(6L, Instant.now(), "p");
            broadcastService.sendIncidentLogEntryAdded(7L, Instant.now(), "p");
        });
    }

    // -----------------------------------------------------------------------
    // DispatcherWebSocketHandler constructor test
    // -----------------------------------------------------------------------

    @Test
    void handler_constructsWithoutError() {
        var tokenValidator = buildTokenValidator();
        var sessionStore = new SessionStore();
        var objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        assertDoesNotThrow(() -> new DispatcherWebSocketHandler(
                tokenValidator, sessionStore, sessionRegistry, objectMapper));
    }

    @Test
    void handler_nullArgs_throwNPE() {
        var om = new ObjectMapper();
        assertThrows(NullPointerException.class, () ->
                new DispatcherWebSocketHandler(null, new SessionStore(), sessionRegistry, om));
        assertThrows(NullPointerException.class, () ->
                new DispatcherWebSocketHandler(buildTokenValidator(), null, sessionRegistry, om));
        assertThrows(NullPointerException.class, () ->
                new DispatcherWebSocketHandler(buildTokenValidator(), new SessionStore(), null, om));
        assertThrows(NullPointerException.class, () ->
                new DispatcherWebSocketHandler(buildTokenValidator(), new SessionStore(), sessionRegistry, null));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private TokenValidator buildTokenValidator() {
        // KeyProvider that always returns null → validation fails with KEY_NOT_FOUND.
        // Sufficient for tests that only verify constructor / registry / broadcast behavior.
        return new TokenValidator(keyId -> null, "https://auth.example.com");
    }
}
