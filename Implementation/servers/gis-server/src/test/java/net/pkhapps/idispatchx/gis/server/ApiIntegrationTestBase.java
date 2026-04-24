package net.pkhapps.idispatchx.gis.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import net.pkhapps.idispatchx.common.auth.JwksKeyProvider;
import net.pkhapps.idispatchx.common.auth.Role;
import net.pkhapps.idispatchx.common.auth.SessionStore;
import net.pkhapps.idispatchx.common.auth.TokenValidator;
import net.pkhapps.idispatchx.gis.server.api.error.GlobalExceptionHandler;
import net.pkhapps.idispatchx.gis.server.api.geocode.GeocodeController;
import net.pkhapps.idispatchx.gis.server.api.health.HealthController;
import net.pkhapps.idispatchx.gis.server.api.layers.LayersController;
import net.pkhapps.idispatchx.gis.server.api.wmts.CapabilitiesGenerator;
import net.pkhapps.idispatchx.gis.server.api.wmts.WmtsController;
import net.pkhapps.idispatchx.gis.server.auth.BackChannelLogoutHandler;
import net.pkhapps.idispatchx.gis.server.auth.JwtAuthHandler;
import net.pkhapps.idispatchx.gis.server.auth.RoleAuthHandler;
import net.pkhapps.idispatchx.gis.server.service.geocode.GeocodeService;
import net.pkhapps.idispatchx.gis.server.service.tile.TileService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/**
 * Base class for API integration tests that start a full Javalin server.
 * <p>
 * Extends {@link IntegrationTestBase} to provide a real PostgreSQL database.
 * Auth is handled via a test RSA key pair; no external OIDC server is needed.
 * <p>
 * The server is started once per test suite and shared across subclasses.
 * Subclasses use {@link #httpGet(String)} and {@link #httpGet(String, String)}
 * to make HTTP requests, and {@link #createToken(Role...)} to generate valid JWTs.
 */
public abstract class ApiIntegrationTestBase extends IntegrationTestBase {

    static final String TEST_ISSUER = "https://test.idp.idispatchx.example";
    private static final String TEST_KEY_ID = "test-rsa-key-1";

    protected static int serverPort;
    protected static Path tileDirectory;

    private static Javalin javalin;
    private static RSAKey testRsaKey;
    private static JWSSigner signer;
    protected static SessionStore sessionStore;

    @BeforeAll
    static void startApiServer() throws Exception {
        // Generate RSA key pair for JWT signing
        var keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        var keyPair = keyGen.generateKeyPair();

        testRsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(TEST_KEY_ID)
                .build();
        signer = new RSASSASigner(testRsaKey);

        // Create a test JwksKeyProvider backed by our RSA key
        var publicKey = testRsaKey.toPublicJWK();
        JwksKeyProvider keyProvider = keyId -> TEST_KEY_ID.equals(keyId) ? publicKey : null;

        var tokenValidator = new TokenValidator(keyProvider, TEST_ISSUER);
        sessionStore = new SessionStore();
        var jwtAuth = new JwtAuthHandler(tokenValidator, sessionStore);
        var roleAuth = RoleAuthHandler.requireAnyRole(Role.DISPATCHER, Role.OBSERVER);

        // Empty tile directory for tests
        tileDirectory = Files.createTempDirectory("gis-test-tiles");

        var tileService = TileService.create(tileDirectory);
        var capGen = new CapabilitiesGenerator("", tileService.getLayers());

        var geocodeService = GeocodeService.create(dsl);

        var objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        javalin = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(objectMapper, true));
            config.showJavalinBanner = false;
        });

        GlobalExceptionHandler.register(javalin);
        new HealthController(dataSource, tileDirectory, tileService.getLayers()).registerRoutes(javalin, "");
        new WmtsController(tileService, capGen).registerRoutes(javalin, jwtAuth, roleAuth, "");
        new GeocodeController(geocodeService).registerRoutes(javalin, jwtAuth, roleAuth, "");
        new LayersController(tileService.getLayers()).registerRoutes(javalin, jwtAuth, roleAuth, "");

        // Back-channel logout (not used in most tests, but registered for completeness)
        JwksKeyProvider logoutKeyProvider = keyId -> TEST_KEY_ID.equals(keyId) ? publicKey : null;
        var logoutValidator = new net.pkhapps.idispatchx.common.auth.LogoutTokenValidator(
                logoutKeyProvider, TEST_ISSUER, "gis-client");
        javalin.post("/api/v1/auth/logout", new BackChannelLogoutHandler(logoutValidator, sessionStore));

        serverPort = findFreePort();
        javalin.start(serverPort);
    }

    @AfterAll
    static void stopApiServer() {
        if (javalin != null) {
            javalin.stop();
        }
    }

    /**
     * Creates a valid signed JWT for the given roles.
     */
    protected static String createToken(Role... roles) {
        return createTokenWithExpiry(new Date(System.currentTimeMillis() + 3_600_000L), roles);
    }

    /**
     * Creates an expired JWT (1 second in the past).
     */
    protected static String createExpiredToken(Role... roles) {
        return createTokenWithExpiry(new Date(System.currentTimeMillis() - 1000L), roles);
    }

    private static String createTokenWithExpiry(Date expiry, Role... roles) {
        try {
            var roleValues = Arrays.stream(roles)
                    .map(Role::getClaimValue)
                    .toList();

            var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(TEST_KEY_ID)
                    .build();

            var claims = new JWTClaimsSet.Builder()
                    .subject("test-user-" + UUID.randomUUID())
                    .issuer(TEST_ISSUER)
                    .issueTime(new Date())
                    .expirationTime(expiry)
                    .claim("roles", roleValues)
                    .build();

            var jwt = new SignedJWT(header, claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test token", e);
        }
    }

    /**
     * Performs a GET request to the given path (no auth).
     */
    protected HttpResponse<String> httpGet(String path) throws IOException, InterruptedException {
        return httpGet(path, null);
    }

    /**
     * Performs a GET request to the given path with an optional Bearer token.
     */
    protected HttpResponse<String> httpGet(String path, @Nullable String bearerToken)
            throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + path))
                .GET();

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Returns a pre-built ObjectMapper for parsing JSON responses.
     */
    protected ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static int findFreePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
