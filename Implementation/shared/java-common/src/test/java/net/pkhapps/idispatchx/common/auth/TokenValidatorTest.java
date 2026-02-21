package net.pkhapps.idispatchx.common.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class TokenValidatorTest {

    private static final String EXPECTED_ISSUER = "https://issuer.example.com";
    private static final String KEY_ID = "test-key-1";

    private RSAKey rsaKey;
    private TestJwksKeyProvider keyProvider;
    private TokenValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        keyProvider = new TestJwksKeyProvider();
        keyProvider.addKey(rsaKey);
        validator = new TokenValidator(keyProvider, EXPECTED_ISSUER);
    }

    @Test
    void constructor_rejectsNullKeyProvider() {
        assertThrows(NullPointerException.class, () ->
                new TokenValidator(null, EXPECTED_ISSUER));
    }

    @Test
    void constructor_rejectsNullExpectedIssuer() {
        assertThrows(NullPointerException.class, () ->
                new TokenValidator(keyProvider, null));
    }

    @Test
    void constructor_rejectsBlankExpectedIssuer() {
        assertThrows(IllegalArgumentException.class, () ->
                new TokenValidator(keyProvider, ""));
        assertThrows(IllegalArgumentException.class, () ->
                new TokenValidator(keyProvider, "   "));
    }

    @Test
    void validate_rejectsNullToken() {
        assertThrows(NullPointerException.class, () ->
                validator.validate(null));
    }

    @Test
    void validate_rejectsInvalidTokenFormat() {
        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate("not.a.valid.jwt"));
        assertEquals(TokenValidationException.ErrorCode.INVALID_TOKEN, ex.getErrorCode());
    }

    @Test
    void validate_rejectsTokenWithoutKeyId() throws Exception {
        // Create token without key ID
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).build();
        var claims = createValidClaims().build();
        var token = createAndSignToken(header, claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.INVALID_TOKEN, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("key ID"));
    }

    @Test
    void validate_rejectsTokenWithUnknownKeyId() throws Exception {
        var unknownKey = new RSAKeyGenerator(2048).keyID("unknown-key").generate();
        var token = createValidToken(unknownKey);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.KEY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void validate_rejectsTokenWithInvalidSignature() throws Exception {
        // Create token signed with a different key
        var differentKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        var token = createValidToken(differentKey);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.INVALID_SIGNATURE, ex.getErrorCode());
    }

    @Test
    void validate_rejectsExpiredToken() throws Exception {
        var claims = createValidClaims()
                .expirationTime(Date.from(Instant.now().minusSeconds(3600)))
                .build();
        var token = createToken(rsaKey, claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.TOKEN_EXPIRED, ex.getErrorCode());
    }

    @Test
    void validate_rejectsTokenWithoutExpiration() throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .issuer(EXPECTED_ISSUER)
                .subject("user123")
                .build();
        var token = createToken(rsaKey, claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.MISSING_CLAIM, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("exp"));
    }

    @Test
    void validate_rejectsTokenWithWrongIssuer() throws Exception {
        var claims = createValidClaims()
                .issuer("https://wrong-issuer.example.com")
                .build();
        var token = createToken(rsaKey, claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.INVALID_ISSUER, ex.getErrorCode());
    }

    @Test
    void validate_rejectsTokenWithoutIssuer() throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .subject("user123")
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();
        var token = createToken(rsaKey, claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.MISSING_CLAIM, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("iss"));
    }

    @Test
    void validate_rejectsTokenWithoutSubject() throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .issuer(EXPECTED_ISSUER)
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();
        var token = createToken(rsaKey, claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.MISSING_CLAIM, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("sub"));
    }

    @Test
    void validate_rejectsTokenWithBlankSubject() throws Exception {
        var claims = createValidClaims()
                .subject("   ")
                .build();
        var token = createToken(rsaKey, claims);

        var ex = assertThrows(TokenValidationException.class, () ->
                validator.validate(token));
        assertEquals(TokenValidationException.ErrorCode.MISSING_CLAIM, ex.getErrorCode());
    }

    @Test
    void validate_acceptsValidToken() throws Exception {
        var token = createValidToken(rsaKey);

        var result = validator.validate(token);

        assertNotNull(result);
        assertEquals("user123", result.subject());
        assertEquals(EXPECTED_ISSUER, result.issuer());
    }

    @Test
    void validate_extractsRolesFromListClaim() throws Exception {
        var claims = createValidClaims()
                .claim("roles", List.of("Dispatcher", "Observer"))
                .build();
        var token = createToken(rsaKey, claims);

        var result = validator.validate(token);

        assertEquals(Set.of(Role.DISPATCHER, Role.OBSERVER), result.roles());
    }

    @Test
    void validate_extractsRolesFromStringClaim() throws Exception {
        var claims = createValidClaims()
                .claim("roles", "Admin")
                .build();
        var token = createToken(rsaKey, claims);

        var result = validator.validate(token);

        assertEquals(Set.of(Role.ADMIN), result.roles());
    }

    @Test
    void validate_ignoresUnknownRoles() throws Exception {
        var claims = createValidClaims()
                .claim("roles", List.of("Dispatcher", "UnknownRole", "Admin"))
                .build();
        var token = createToken(rsaKey, claims);

        var result = validator.validate(token);

        assertEquals(Set.of(Role.DISPATCHER, Role.ADMIN), result.roles());
    }

    @Test
    void validate_returnsEmptyRolesWhenClaimMissing() throws Exception {
        var claims = createValidClaims().build();
        var token = createToken(rsaKey, claims);

        var result = validator.validate(token);

        assertTrue(result.roles().isEmpty());
    }

    @Test
    void validate_extractsSessionId() throws Exception {
        var claims = createValidClaims()
                .claim("sid", "session-abc123")
                .build();
        var token = createToken(rsaKey, claims);

        var result = validator.validate(token);

        assertEquals("session-abc123", result.sessionId());
    }

    @Test
    void validate_extractsIssuedAt() throws Exception {
        var issuedAt = Instant.now().minusSeconds(60);
        var claims = createValidClaims()
                .issueTime(Date.from(issuedAt))
                .build();
        var token = createToken(rsaKey, claims);

        var result = validator.validate(token);

        assertNotNull(result.issuedAt());
        // Compare with some tolerance for processing time
        assertTrue(Math.abs(result.issuedAt().getEpochSecond() - issuedAt.getEpochSecond()) < 2);
    }

    @Test
    void validate_worksWithEcKey() throws Exception {
        var ecKey = new ECKeyGenerator(Curve.P_256).keyID("ec-key").generate();
        keyProvider.addKey(ecKey);

        var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID("ec-key")
                .build();
        var claims = createValidClaims().build();
        var jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(ecKey));
        var token = jwt.serialize();

        var result = validator.validate(token);

        assertNotNull(result);
        assertEquals("user123", result.subject());
    }

    @Test
    void validate_customRolesClaim() throws Exception {
        var customValidator = new TokenValidator(keyProvider, EXPECTED_ISSUER, "realm_access");

        var claims = createValidClaims()
                .claim("realm_access", List.of("Dispatcher"))
                .build();
        var token = createToken(rsaKey, claims);

        var result = customValidator.validate(token);

        assertEquals(Set.of(Role.DISPATCHER), result.roles());
    }

    @Test
    void defaultRolesClaim_isRoles() {
        assertEquals("roles", TokenValidator.DEFAULT_ROLES_CLAIM);
    }

    // Helper methods

    private JWTClaimsSet.Builder createValidClaims() {
        return new JWTClaimsSet.Builder()
                .issuer(EXPECTED_ISSUER)
                .subject("user123")
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)));
    }

    private String createValidToken(RSAKey key) throws Exception {
        return createToken(key, createValidClaims().build());
    }

    private String createToken(RSAKey key, JWTClaimsSet claims) throws Exception {
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID())
                .build();
        var jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private String createAndSignToken(JWSHeader header, JWTClaimsSet claims) throws Exception {
        var jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    /**
     * Test implementation of JwksKeyProvider that holds keys in memory.
     */
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
