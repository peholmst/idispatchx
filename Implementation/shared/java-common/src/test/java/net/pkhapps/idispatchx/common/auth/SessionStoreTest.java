package net.pkhapps.idispatchx.common.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SessionStoreTest {

    private SessionStore sessionStore;

    @BeforeEach
    void setUp() {
        // Create without scheduled cleanup for testing
        sessionStore = new SessionStore(Duration.ofMinutes(5), false);
    }

    @AfterEach
    void tearDown() {
        sessionStore.close();
    }

    @Test
    void constructor_rejectsNullRevocationTtl() {
        assertThrows(NullPointerException.class, () -> new SessionStore(null));
    }

    @Test
    void constructor_rejectsNegativeRevocationTtl() {
        assertThrows(IllegalArgumentException.class, () ->
                new SessionStore(Duration.ofMinutes(-1)));
    }

    @Test
    void constructor_rejectsZeroRevocationTtl() {
        assertThrows(IllegalArgumentException.class, () ->
                new SessionStore(Duration.ZERO));
    }

    @Test
    void revokeSession_marksSessionAsRevoked() {
        sessionStore.revokeSession("session-123");

        assertTrue(sessionStore.isRevoked("session-123"));
        assertEquals(1, sessionStore.size());
    }

    @Test
    void revokeSession_rejectsNullSessionId() {
        assertThrows(NullPointerException.class, () ->
                sessionStore.revokeSession(null));
    }

    @Test
    void revokeSessionWithExpiration_marksSessionAsRevoked() {
        var expiration = Instant.now().plus(Duration.ofHours(1));
        sessionStore.revokeSession("session-123", expiration);

        assertTrue(sessionStore.isRevoked("session-123"));
    }

    @Test
    void isRevoked_returnsFalseForUnknownSession() {
        assertFalse(sessionStore.isRevoked("unknown-session"));
    }

    @Test
    void isRevoked_returnsFalseForExpiredRevocation() {
        var expiration = Instant.now().minus(Duration.ofSeconds(1));
        sessionStore.revokeSession("session-123", expiration);

        assertFalse(sessionStore.isRevoked("session-123"));
    }

    @Test
    void isRevoked_rejectsNullSessionId() {
        assertThrows(NullPointerException.class, () ->
                sessionStore.isRevoked(null));
    }

    @Test
    void validateSession_throwsForRevokedSession() {
        sessionStore.revokeSession("session-123");

        var ex = assertThrows(TokenValidationException.class, () ->
                sessionStore.validateSession("session-123"));
        assertEquals(TokenValidationException.ErrorCode.TOKEN_EXPIRED, ex.getErrorCode());
    }

    @Test
    void validateSession_passesForUnknownSession() {
        assertDoesNotThrow(() -> sessionStore.validateSession("unknown-session"));
    }

    @Test
    void cleanupExpired_removesExpiredRevocations() {
        var expired = Instant.now().minus(Duration.ofSeconds(1));
        var valid = Instant.now().plus(Duration.ofHours(1));

        sessionStore.revokeSession("expired-session", expired);
        sessionStore.revokeSession("valid-session", valid);
        assertEquals(2, sessionStore.size());

        sessionStore.cleanupExpired();

        assertEquals(1, sessionStore.size());
        assertFalse(sessionStore.isRevoked("expired-session"));
        assertTrue(sessionStore.isRevoked("valid-session"));
    }

    @Test
    void clear_removesAllRevocations() {
        sessionStore.revokeSession("session-1");
        sessionStore.revokeSession("session-2");
        sessionStore.revokeSession("session-3");
        assertEquals(3, sessionStore.size());

        sessionStore.clear();

        assertEquals(0, sessionStore.size());
    }

    @Test
    void defaultRevocationTtlIsOneHour() {
        assertEquals(Duration.ofHours(1), SessionStore.DEFAULT_REVOCATION_TTL);
    }

    @Test
    void multipleRevocationsOfSameSession() {
        sessionStore.revokeSession("session-123");
        sessionStore.revokeSession("session-123");

        assertEquals(1, sessionStore.size());
        assertTrue(sessionStore.isRevoked("session-123"));
    }

    @Test
    void close_canBeCalledMultipleTimes() {
        var store = new SessionStore(Duration.ofMinutes(5));
        assertDoesNotThrow(() -> {
            store.close();
            store.close();
        });
    }
}
