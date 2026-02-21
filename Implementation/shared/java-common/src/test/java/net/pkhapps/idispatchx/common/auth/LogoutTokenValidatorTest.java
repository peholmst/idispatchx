package net.pkhapps.idispatchx.common.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class LogoutTokenValidatorTest {

    private static final String EXPECTED_ISSUER = "https://issuer.example.com";
    private static final String EXPECTED_AUDIENCE = "gis-server-client";
    private static final String KEY_ID = "logout-key-1";

    private RSAKey rsaKey;
    private TestJwksKeyProvider keyProvider;
    private LogoutTokenValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        keyProvider = new TestJwksKeyProvider();
        keyProvider.addKey(rsaKey);
        validator = new LogoutTokenValidator(keyProvider, EXPECTED_ISSUER, EXPECTED_AUDIENCE);
    }

    @Test
    void constructor_rejectsNullKeyProvider() {
        assertThrows(NullPointerException.class, () ->
                new LogoutTokenValidator(null, EXPECTED_ISSUER, EXPECTED_AUDIENCE));
    }

    @Test
    void constructor_rejectsNullExpectedIssuer() {
        assertThrows(NullPointerException.class, () ->
                new LogoutTokenValidator(keyProvider, null, EXPECTED_AUDIENCE));
    }

    @Test
    void constructor_rejectsNullExpectedAudience() {
        assertThrows(NullPointerException.class, () ->
                new LogoutTokenValidator(keyProvider, EXPECTED_ISSUER, null));
    }

    @Test
    void constructor_rejectsBlankExpectedIssuer() {
        assertThrows(IllegalArgumentException.class, () ->
                new LogoutTokenValidator(keyProvider, "", EXPECTED_AUDIENCE));
        assertThrows(IllegalArgumentException.class, () ->
                new LogoutTokenValidator(keyProvider, "   ", EXPECTED_AUDIENCE));
    }

    @Test
    void constructor_rejectsBlankExpectedAudience() {
        assertThrows(IllegalArgumentException.class, () ->
                new LogoutTokenValidator(keyProvider, EXPECTED_ISSUER, ""));
        assertThrows(IllegalArgumentException.class, () ->
                new LogoutTokenValidator(keyProvider, EXPECTED_ISSUER, "   "));
    }

    @Test
    void validate_rejectsNullToken() {
        assertThrows(NullPointerException.class, () -> validator.validate(null));
    }

    @Test
    void validate_rejectsInvalidTokenFormat() {
        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate("not.a.valid.jwt"));
        assertEquals(TokenValidationException.ErrorCode.INVALID_TOKEN, ex.getErrorCode());
    }

    @Test
    void validate_acceptsValidLogoutToken() throws Exception {
        var claims = createValidLogoutClaims()
                .claim("sid", "session-123")
                .build();
        var token = createToken(claims);

        var result = validator.validate(token);

        assertNotNull(result);
        assertEquals("session-123", result.sessionId());
    }

    @Test
    void validate_acceptsLogoutTokenWithSubjectOnly() throws Exception {
        var claims = createValidLogoutClaims()
                .subject("user-456")
                .build();
        var token = createToken(claims);

        var result = validator.validate(token);

        assertNotNull(result);
        assertNull(result.sessionId());
        assertEquals("user-456", result.subject());
        assertTrue(result.isSubjectLogout());
    }

    @Test
    void validate_rejectsTokenWithWrongIssuer() throws Exception {
        var claims = createValidLogoutClaims()
                .issuer("https://wrong-issuer.example.com")
                .claim("sid", "session-123")
                .build();
        var token = createToken(claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.INVALID_ISSUER, ex.getErrorCode());
    }

    @Test
    void validate_rejectsTokenWithoutIssuer() throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .audience(EXPECTED_AUDIENCE)
                .claim("events", createLogoutEventsMap())
                .claim("sid", "session-123")
                .build();
        var token = createToken(claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.MISSING_CLAIM, ex.getErrorCode());
    }

    @Test
    void validate_rejectsTokenWithWrongAudience() throws Exception {
        var claims = createValidLogoutClaims()
                .audience("wrong-client")
                .claim("sid", "session-123")
                .build();
        var token = createToken(claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.INVALID_TOKEN, ex.getErrorCode());
    }

    @Test
    void validate_rejectsTokenWithoutAudience() throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .issuer(EXPECTED_ISSUER)
                .claim("events", createLogoutEventsMap())
                .claim("sid", "session-123")
                .build();
        var token = createToken(claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.MISSING_CLAIM, ex.getErrorCode());
    }

    @Test
    void validate_rejectsExpiredToken() throws Exception {
        var claims = createValidLogoutClaims()
                .expirationTime(Date.from(Instant.now().minusSeconds(120))) // 2 minutes ago
                .claim("sid", "session-123")
                .build();
        var token = createToken(claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.TOKEN_EXPIRED, ex.getErrorCode());
    }

    @Test
    void validate_allowsClockSkew() throws Exception {
        // Token that "expired" 30 seconds ago should still be valid (60s skew allowed)
        var claims = createValidLogoutClaims()
                .expirationTime(Date.from(Instant.now().minusSeconds(30)))
                .claim("sid", "session-123")
                .build();
        var token = createToken(claims);

        var result = validator.validate(token);

        assertNotNull(result);
    }

    @Test
    void validate_rejectsTokenWithoutEvents() throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .issuer(EXPECTED_ISSUER)
                .audience(EXPECTED_AUDIENCE)
                .claim("sid", "session-123")
                .build();
        var token = createToken(claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.MISSING_CLAIM, ex.getErrorCode());
    }

    @Test
    void validate_rejectsTokenWithWrongEventType() throws Exception {
        var claims = createValidLogoutClaims()
                .claim("events", Map.of("http://wrong.event/type", Map.of()))
                .claim("sid", "session-123")
                .build();
        var token = createToken(claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.INVALID_TOKEN, ex.getErrorCode());
    }

    @Test
    void validate_rejectsTokenWithNonce() throws Exception {
        var claims = createValidLogoutClaims()
                .claim("nonce", "some-nonce-value")
                .claim("sid", "session-123")
                .build();
        var token = createToken(claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.INVALID_TOKEN, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("nonce"));
    }

    @Test
    void validate_rejectsTokenWithoutSidOrSub() throws Exception {
        var claims = createValidLogoutClaims().build();
        var token = createToken(claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.MISSING_CLAIM, ex.getErrorCode());
    }

    @Test
    void validate_acceptsTokenWithBothSidAndSub() throws Exception {
        var claims = createValidLogoutClaims()
                .claim("sid", "session-123")
                .subject("user-456")
                .build();
        var token = createToken(claims);

        var result = validator.validate(token);

        assertEquals("session-123", result.sessionId());
        assertEquals("user-456", result.subject());
        assertTrue(result.hasSessionId());
        assertFalse(result.isSubjectLogout());
    }

    @Test
    void logoutEventTypeConstant() {
        assertEquals("http://schemas.openid.net/event/backchannel-logout",
                LogoutTokenValidator.LOGOUT_EVENT_TYPE);
    }

    // Helper methods

    private JWTClaimsSet.Builder createValidLogoutClaims() {
        return new JWTClaimsSet.Builder()
                .issuer(EXPECTED_ISSUER)
                .audience(EXPECTED_AUDIENCE)
                .claim("events", createLogoutEventsMap());
    }

    private Map<String, Object> createLogoutEventsMap() {
        return Map.of(LogoutTokenValidator.LOGOUT_EVENT_TYPE, Map.of());
    }

    private String createToken(JWTClaimsSet claims) throws Exception {
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKey.getKeyID())
                .build();
        var jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    private static class TestJwksKeyProvider implements JwksKeyProvider {
        private final Map<String, JWK> keys = new ConcurrentHashMap<>();

        void addKey(JWK key) {
            keys.put(key.getKeyID(), key);
        }

        @Override
        public @Nullable JWK getKey(String keyId) {
            return keys.get(keyId);
        }
    }
}
