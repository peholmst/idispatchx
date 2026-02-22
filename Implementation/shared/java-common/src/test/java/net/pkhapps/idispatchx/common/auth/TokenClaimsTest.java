package net.pkhapps.idispatchx.common.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TokenClaimsTest {

    @Test
    void constructor_validatesRequiredFields() {
        assertThrows(NullPointerException.class, () ->
                new TokenClaims(null, "issuer", null, Set.of(), Instant.now().plusSeconds(3600), null));

        assertThrows(NullPointerException.class, () ->
                new TokenClaims("subject", null, null, Set.of(), Instant.now().plusSeconds(3600), null));

        assertThrows(NullPointerException.class, () ->
                new TokenClaims("subject", "issuer", null, null, Instant.now().plusSeconds(3600), null));

        assertThrows(NullPointerException.class, () ->
                new TokenClaims("subject", "issuer", null, Set.of(), null, null));
    }

    @Test
    void constructor_rejectsBlankSubject() {
        assertThrows(IllegalArgumentException.class, () ->
                new TokenClaims("", "issuer", null, Set.of(), Instant.now().plusSeconds(3600), null));

        assertThrows(IllegalArgumentException.class, () ->
                new TokenClaims("   ", "issuer", null, Set.of(), Instant.now().plusSeconds(3600), null));
    }

    @Test
    void constructor_rejectsBlankIssuer() {
        assertThrows(IllegalArgumentException.class, () ->
                new TokenClaims("subject", "", null, Set.of(), Instant.now().plusSeconds(3600), null));

        assertThrows(IllegalArgumentException.class, () ->
                new TokenClaims("subject", "   ", null, Set.of(), Instant.now().plusSeconds(3600), null));
    }

    @Test
    void constructor_makesRolesImmutable() {
        var mutableRoles = new java.util.HashSet<Role>();
        mutableRoles.add(Role.DISPATCHER);

        var claims = new TokenClaims("subject", "issuer", null, mutableRoles,
                Instant.now().plusSeconds(3600), null);

        assertThrows(UnsupportedOperationException.class, () ->
                claims.roles().add(Role.ADMIN));
    }

    @Test
    void isExpired_returnsTrueForExpiredToken() {
        var claims = new TokenClaims("subject", "issuer", null, Set.of(),
                Instant.now().minus(1, ChronoUnit.HOURS), null);
        assertTrue(claims.isExpired());
    }

    @Test
    void isExpired_returnsFalseForValidToken() {
        var claims = new TokenClaims("subject", "issuer", null, Set.of(),
                Instant.now().plus(1, ChronoUnit.HOURS), null);
        assertFalse(claims.isExpired());
    }

    @Test
    void hasRole_returnsTrueWhenRolePresent() {
        var claims = new TokenClaims("subject", "issuer", null,
                Set.of(Role.DISPATCHER, Role.OBSERVER),
                Instant.now().plusSeconds(3600), null);

        assertTrue(claims.hasRole(Role.DISPATCHER));
        assertTrue(claims.hasRole(Role.OBSERVER));
    }

    @Test
    void hasRole_returnsFalseWhenRoleAbsent() {
        var claims = new TokenClaims("subject", "issuer", null,
                Set.of(Role.DISPATCHER),
                Instant.now().plusSeconds(3600), null);

        assertFalse(claims.hasRole(Role.ADMIN));
        assertFalse(claims.hasRole(Role.STATION));
    }

    @Test
    void hasAnyRole_returnsTrueWhenAnyRolePresent() {
        var claims = new TokenClaims("subject", "issuer", null,
                Set.of(Role.DISPATCHER),
                Instant.now().plusSeconds(3600), null);

        assertTrue(claims.hasAnyRole(Role.DISPATCHER, Role.ADMIN));
        assertTrue(claims.hasAnyRole(Role.ADMIN, Role.DISPATCHER));
    }

    @Test
    void hasAnyRole_returnsFalseWhenNoRolesPresent() {
        var claims = new TokenClaims("subject", "issuer", null,
                Set.of(Role.DISPATCHER),
                Instant.now().plusSeconds(3600), null);

        assertFalse(claims.hasAnyRole(Role.ADMIN, Role.STATION));
    }

    @Test
    void hasAnyRole_returnsFalseForEmptyRoles() {
        var claims = new TokenClaims("subject", "issuer", null,
                Set.of(),
                Instant.now().plusSeconds(3600), null);

        assertFalse(claims.hasAnyRole(Role.DISPATCHER, Role.ADMIN));
    }

    @Test
    void optionalFieldsCanBeNull() {
        var claims = new TokenClaims("subject", "issuer", null, Set.of(),
                Instant.now().plusSeconds(3600), null);

        assertNull(claims.sessionId());
        assertNull(claims.issuedAt());
    }

    @Test
    void allFieldsAccessible() {
        var now = Instant.now();
        var expiresAt = now.plusSeconds(3600);
        var claims = new TokenClaims("user123", "https://issuer.example.com", "session456",
                Set.of(Role.DISPATCHER, Role.ADMIN), expiresAt, now);

        assertEquals("user123", claims.subject());
        assertEquals("https://issuer.example.com", claims.issuer());
        assertEquals("session456", claims.sessionId());
        assertEquals(Set.of(Role.DISPATCHER, Role.ADMIN), claims.roles());
        assertEquals(expiresAt, claims.expiresAt());
        assertEquals(now, claims.issuedAt());
    }
}
