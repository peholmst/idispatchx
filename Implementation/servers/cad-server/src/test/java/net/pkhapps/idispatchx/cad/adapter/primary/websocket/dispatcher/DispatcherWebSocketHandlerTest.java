package net.pkhapps.idispatchx.cad.adapter.primary.websocket.dispatcher;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import net.pkhapps.idispatchx.cad.adapter.broadcast.ArchiveHealthMonitor;
import net.pkhapps.idispatchx.cad.adapter.broadcast.DispatcherBroadcastService;
import net.pkhapps.idispatchx.cad.adapter.broadcast.SessionRegistry;
import net.pkhapps.idispatchx.cad.port.secondary.archive.NoOpArchivePort;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;
import net.pkhapps.idispatchx.common.auth.SessionStore;
import net.pkhapps.idispatchx.common.auth.TokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

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
            broadcastService.sendSystemStatusChanged(8L, Instant.now(), true);
            broadcastService.sendSystemStatusChanged(9L, Instant.now(), false);
        });
    }

    // -----------------------------------------------------------------------
    // DispatcherWebSocketHandler constructor test
    // -----------------------------------------------------------------------

    @Test
    void handler_constructsWithoutError() {
        var tokenValidator = buildTokenValidator();
        var sessionStore = new SessionStore();
        var objectMapper = JsonMapper.builder().build();
        var wal = noopWalPort();
        var monitor = new ArchiveHealthMonitor(new NoOpArchivePort(), broadcastService, wal);
        assertDoesNotThrow(() -> new DispatcherWebSocketHandler(
                tokenValidator, sessionStore, sessionRegistry, objectMapper, wal, monitor));
    }

    @Test
    void handler_nullArgs_throwNPE() {
        var om = JsonMapper.builder().build();
        var wal = noopWalPort();
        var monitor = new ArchiveHealthMonitor(new NoOpArchivePort(), broadcastService, wal);
        assertThrows(NullPointerException.class, () ->
                new DispatcherWebSocketHandler(null, new SessionStore(), sessionRegistry, om, wal, monitor));
        assertThrows(NullPointerException.class, () ->
                new DispatcherWebSocketHandler(buildTokenValidator(), null, sessionRegistry, om, wal, monitor));
        assertThrows(NullPointerException.class, () ->
                new DispatcherWebSocketHandler(buildTokenValidator(), new SessionStore(), null, om, wal, monitor));
        assertThrows(NullPointerException.class, () ->
                new DispatcherWebSocketHandler(buildTokenValidator(), new SessionStore(), sessionRegistry, null, wal, monitor));
        assertThrows(NullPointerException.class, () ->
                new DispatcherWebSocketHandler(buildTokenValidator(), new SessionStore(), sessionRegistry, om, null, monitor));
        assertThrows(NullPointerException.class, () ->
                new DispatcherWebSocketHandler(buildTokenValidator(), new SessionStore(), sessionRegistry, om, wal, null));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private TokenValidator buildTokenValidator() {
        // KeyProvider that always returns null → validation fails with KEY_NOT_FOUND.
        // Sufficient for tests that only verify constructor / registry / broadcast behavior.
        return new TokenValidator(keyId -> null, "https://auth.example.com");
    }

    private static WalPort noopWalPort() {
        return new WalPort() {
            @Override public SequenceNumber write(DomainEvent e) { return SequenceNumber.start(); }
            @Override public SequenceNumber writeBatch(List<? extends DomainEvent> e) { return SequenceNumber.start(); }
            @Override public void replayFrom(SequenceNumber from, Consumer<DomainEvent> c) {}
            @Override public void replay(Consumer<DomainEvent> c) {}
            @Override public void truncate(SequenceNumber upTo) {}
            @Override public SequenceNumber currentSequence() { return SequenceNumber.start(); }
            @Override public long currentSequenceNumber() { return 0; }
        };
    }
}
