package net.pkhapps.idispatchx.cad.adapter.primary.rest.dispatcher;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson3;
import net.pkhapps.idispatchx.cad.adapter.auth.AuthContext;
import net.pkhapps.idispatchx.cad.adapter.primary.rest.shared.GlobalExceptionHandler;
import net.pkhapps.idispatchx.cad.application.handler.AttachCallToIncidentCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.CreateCallCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.DetachCallFromIncidentCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.DispatcherFactory;
import net.pkhapps.idispatchx.cad.application.handler.EndCallCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.EntityLockManager;
import net.pkhapps.idispatchx.cad.application.handler.SetCallOutcomeCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.UpdateCallDetailsCommandHandler;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import net.pkhapps.idispatchx.cad.domain.repository.InMemoryCallRepository;
import net.pkhapps.idispatchx.cad.domain.repository.InMemoryIncidentRepository;
import net.pkhapps.idispatchx.cad.port.secondary.archive.ArchivePort;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;
import net.pkhapps.idispatchx.common.auth.Role;
import net.pkhapps.idispatchx.common.auth.TokenClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class CallControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-06-13T10:00:00Z");
    private static final TokenClaims DISPATCHER_CLAIMS = new TokenClaims(
            "dispatcher-user", "https://auth.example.com", "sid-1",
            Set.of(Role.DISPATCHER), FIXED_TIME.plusSeconds(3600), FIXED_TIME);
    private static final TokenClaims OBSERVER_CLAIMS = new TokenClaims(
            "observer-user", "https://auth.example.com", "sid-2",
            Set.of(Role.OBSERVER), FIXED_TIME.plusSeconds(3600), FIXED_TIME);

    private Javalin app;
    private int port;
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .build();

    @BeforeEach
    void setUp() throws IOException {
        app = buildApp(DISPATCHER_CLAIMS);
        try (var s = new ServerSocket(0)) { port = s.getLocalPort(); }
        app.start(port);
    }

    @AfterEach
    void tearDown() {
        if (app != null) app.stop();
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/calls
    // -----------------------------------------------------------------------

    @Test
    void createCall_withEmptyBody_returns201() throws Exception {
        var response = post("/api/v1/calls", "{}", UUID.randomUUID().toString());
        assertEquals(201, response.statusCode());
        var body = objectMapper.readTree(response.body());
        assertNotNull(body.get("callId"));
        assertEquals("active", body.get("state").asText());
        assertEquals("dispatcher-user", body.get("receivingDispatcher").asText());
    }

    @Test
    void createCall_withCallerDetails_returns201() throws Exception {
        var response = post("/api/v1/calls",
                """
                {"callerName":"Matti","callerPhoneNumber":"+358401234567","description":"Smoke"}
                """,
                UUID.randomUUID().toString());
        assertEquals(201, response.statusCode());
    }

    @Test
    void createCall_withoutCommandId_returns400() throws Exception {
        var response = postNoCommandId("/api/v1/calls", "{}");
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("X-Command-Id"));
    }

    @Test
    void createCall_withMalformedCommandId_returns400() throws Exception {
        var response = postWithCommandId("/api/v1/calls", "{}", "not-a-uuid");
        assertEquals(400, response.statusCode());
    }

    @Test
    void createCall_withObserverRole_returns403() throws Exception {
        var observerApp = buildApp(OBSERVER_CLAIMS);
        int observerPort;
        try (var s = new ServerSocket(0)) { observerPort = s.getLocalPort(); }
        observerApp.start(observerPort);
        try {
            var response = postOnPort(observerPort, "/api/v1/calls", "{}", UUID.randomUUID().toString());
            assertEquals(403, response.statusCode());
        } finally {
            observerApp.stop();
        }
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/calls
    // -----------------------------------------------------------------------

    @Test
    void listCalls_returnsCallsArray() throws Exception {
        post("/api/v1/calls", "{}", UUID.randomUUID().toString());
        var response = get("/api/v1/calls");
        assertEquals(200, response.statusCode());
        var body = objectMapper.readTree(response.body());
        assertNotNull(body.get("calls"));
        assertEquals(1, body.get("calls").size());
    }

    @Test
    void listCalls_allowsObserverRole() throws Exception {
        var observerApp = buildApp(OBSERVER_CLAIMS);
        int observerPort;
        try (var s = new ServerSocket(0)) { observerPort = s.getLocalPort(); }
        observerApp.start(observerPort);
        try {
            assertEquals(200, getOnPort(observerPort, "/api/v1/calls").statusCode());
        } finally {
            observerApp.stop();
        }
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/calls/{callId}
    // -----------------------------------------------------------------------

    @Test
    void getCall_returnsCallById() throws Exception {
        var createResp = post("/api/v1/calls", "{}", UUID.randomUUID().toString());
        var callId = objectMapper.readTree(createResp.body()).get("callId").asText();
        var response = get("/api/v1/calls/" + callId);
        assertEquals(200, response.statusCode());
        assertEquals(callId, objectMapper.readTree(response.body()).get("callId").asText());
    }

    @Test
    void getCall_withUnknownId_returns404() throws Exception {
        assertEquals(404, get("/api/v1/calls/V1StGXR8_Z5jdHi6B-myT").statusCode());
    }

    @Test
    void getCall_allowsObserverRole() throws Exception {
        var observerApp = buildApp(OBSERVER_CLAIMS);
        int observerPort;
        try (var s = new ServerSocket(0)) { observerPort = s.getLocalPort(); }
        observerApp.start(observerPort);
        try {
            assertEquals(404, getOnPort(observerPort, "/api/v1/calls/V1StGXR8_Z5jdHi6B-myT").statusCode());
        } finally {
            observerApp.stop();
        }
    }

    // -----------------------------------------------------------------------
    // PATCH /api/v1/calls/{callId}
    // -----------------------------------------------------------------------

    @Test
    void updateCallDetails_returnsSuccess() throws Exception {
        var callId = createCallGetId();
        var response = patch("/api/v1/calls/" + callId,
                """
                {"callerName":"Updated Name"}
                """,
                UUID.randomUUID().toString());
        assertEquals(200, response.statusCode());
    }

    @Test
    void updateCallDetails_withOutcome_returnsSuccess() throws Exception {
        var callId = createCallGetId();
        var response = patch("/api/v1/calls/" + callId,
                """
                {"outcome":"caller_advised","outcomeRationale":"No action needed"}
                """,
                UUID.randomUUID().toString());
        assertEquals(200, response.statusCode());
    }

    @Test
    void updateCallDetails_observerForbidden() throws Exception {
        var observerApp = buildApp(OBSERVER_CLAIMS);
        int observerPort;
        try (var s = new ServerSocket(0)) { observerPort = s.getLocalPort(); }
        observerApp.start(observerPort);
        try {
            var response = patchOnPort(observerPort, "/api/v1/calls/V1StGXR8_Z5jdHi6B-myT",
                    "{}", UUID.randomUUID().toString());
            assertEquals(403, response.statusCode());
        } finally {
            observerApp.stop();
        }
    }

    @Test
    void updateCallDetails_callNotFound_returns404() throws Exception {
        var response = patch("/api/v1/calls/V1StGXR8_Z5jdHi6B-myT",
                """
                {"callerName":"Name"}
                """,
                UUID.randomUUID().toString());
        assertEquals(404, response.statusCode());
    }

    @Test
    void updateCallDetails_emptyBody_unknownCall_returns404() throws Exception {
        // Empty body (no-op patch) must still validate the target
        var response = patch("/api/v1/calls/V1StGXR8_Z5jdHi6B-myT", "{}", UUID.randomUUID().toString());
        assertEquals(404, response.statusCode());
    }

    @Test
    void updateCallDetails_outcomeRationaleWithoutOutcome_returns400() throws Exception {
        var callId = createCallGetId();
        var response = patch("/api/v1/calls/" + callId,
                """
                {"outcomeRationale":"Some rationale without outcome"}
                """,
                UUID.randomUUID().toString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("outcomeRationale"));
    }

    @Test
    void updateCallDetails_outcomeRationaleWithExplicitNullOutcome_returns400() throws Exception {
        var callId = createCallGetId();
        var response = patch("/api/v1/calls/" + callId,
                """
                {"outcome":null,"outcomeRationale":"Some rationale"}
                """,
                UUID.randomUUID().toString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("outcomeRationale"));
    }

    @Test
    void updateCallDetails_explicitNullClearsField_returns200() throws Exception {
        // First set a caller name
        var callId = createCallGetId();
        patch("/api/v1/calls/" + callId,
                """
                {"callerName":"John Doe"}
                """,
                UUID.randomUUID().toString());

        // Then clear it by sending explicit null
        var response = patch("/api/v1/calls/" + callId,
                """
                {"callerName":null}
                """,
                UUID.randomUUID().toString());
        assertEquals(200, response.statusCode());

        var getResponse = get("/api/v1/calls/" + callId);
        assertEquals(200, getResponse.statusCode());
        var body = objectMapper.readTree(getResponse.body());
        assertTrue(body.get("callerName").isNull());
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/calls/{callId}/end
    // -----------------------------------------------------------------------

    @Test
    void endCall_withOutcomeInBody_returns200() throws Exception {
        var callId = createCallGetId();
        var response = post("/api/v1/calls/" + callId + "/end",
                """
                {"outcome":"caller_advised","outcomeRationale":"No action needed"}
                """,
                UUID.randomUUID().toString());
        assertEquals(200, response.statusCode());
    }

    @Test
    void endCall_withOutcomePreSet_returns200() throws Exception {
        var callId = createCallGetId();
        // Set outcome via PATCH
        patch("/api/v1/calls/" + callId,
                """
                {"outcome":"hoax","outcomeRationale":"False alarm"}
                """,
                UUID.randomUUID().toString());
        // End without providing outcome
        var response = post("/api/v1/calls/" + callId + "/end", "{}", UUID.randomUUID().toString());
        assertEquals(200, response.statusCode());
    }

    @Test
    void endCall_withNoOutcome_returns400() throws Exception {
        var callId = createCallGetId();
        var response = post("/api/v1/calls/" + callId + "/end", "{}", UUID.randomUUID().toString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("outcome"));
    }

    @Test
    void endCall_alreadyEnded_returns409() throws Exception {
        var callId = createCallGetId();
        var body = """
                {"outcome":"hoax","outcomeRationale":"false alarm"}
                """;
        post("/api/v1/calls/" + callId + "/end", body, UUID.randomUUID().toString());
        var response = post("/api/v1/calls/" + callId + "/end", body, UUID.randomUUID().toString());
        assertEquals(409, response.statusCode());
    }

    @Test
    void endCall_retry_withSameCommandId_returns200() throws Exception {
        var callId = createCallGetId();
        var body = """
                {"outcome":"hoax","outcomeRationale":"false alarm"}
                """;
        var commandId = UUID.randomUUID().toString();
        assertEquals(200, post("/api/v1/calls/" + callId + "/end", body, commandId).statusCode());
        // Retry with identical X-Command-Id must be treated as idempotent
        assertEquals(200, post("/api/v1/calls/" + callId + "/end", body, commandId).statusCode());
    }

    @Test
    void endCall_observerForbidden() throws Exception {
        var observerApp = buildApp(OBSERVER_CLAIMS);
        int observerPort;
        try (var s = new ServerSocket(0)) { observerPort = s.getLocalPort(); }
        observerApp.start(observerPort);
        try {
            var response = postOnPort(observerPort, "/api/v1/calls/V1StGXR8_Z5jdHi6B-myT/end",
                    "{}", UUID.randomUUID().toString());
            assertEquals(403, response.statusCode());
        } finally {
            observerApp.stop();
        }
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/calls/{callId}/attach-to-incident
    // -----------------------------------------------------------------------

    @Test
    void attachToIncident_missingIncidentId_returns400() throws Exception {
        var callId = createCallGetId();
        // Body with no incidentId (null) must return 400 not 500
        var response = post("/api/v1/calls/" + callId + "/attach-to-incident",
                "{}", UUID.randomUUID().toString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("incidentId"));
    }

    @Test
    void attachToIncident_incidentNotFound_returns404() throws Exception {
        var callId = createCallGetId();
        var response = post("/api/v1/calls/" + callId + "/attach-to-incident",
                """
                {"incidentId":"6byYFiLM_BkBZ5IFKhbRF"}
                """,
                UUID.randomUUID().toString());
        assertEquals(404, response.statusCode());
    }

    @Test
    void attachToIncident_observerForbidden() throws Exception {
        var observerApp = buildApp(OBSERVER_CLAIMS);
        int observerPort;
        try (var s = new ServerSocket(0)) { observerPort = s.getLocalPort(); }
        observerApp.start(observerPort);
        try {
            var response = postOnPort(observerPort, "/api/v1/calls/V1StGXR8_Z5jdHi6B-myT/attach-to-incident",
                    """
                    {"incidentId":"6byYFiLM_BkBZ5IFKhbRF"}
                    """,
                    UUID.randomUUID().toString());
            assertEquals(403, response.statusCode());
        } finally {
            observerApp.stop();
        }
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/calls/{callId}/detach-from-incident
    // -----------------------------------------------------------------------

    @Test
    void detachFromIncident_callNotAttached_returns409() throws Exception {
        var callId = createCallGetId();
        var response = post("/api/v1/calls/" + callId + "/detach-from-incident",
                "{}", UUID.randomUUID().toString());
        assertEquals(409, response.statusCode());
    }

    @Test
    void detachFromIncident_observerForbidden() throws Exception {
        var observerApp = buildApp(OBSERVER_CLAIMS);
        int observerPort;
        try (var s = new ServerSocket(0)) { observerPort = s.getLocalPort(); }
        observerApp.start(observerPort);
        try {
            var response = postOnPort(observerPort, "/api/v1/calls/V1StGXR8_Z5jdHi6B-myT/detach-from-incident",
                    "{}", UUID.randomUUID().toString());
            assertEquals(403, response.statusCode());
        } finally {
            observerApp.stop();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String createCallGetId() throws Exception {
        var resp = post("/api/v1/calls", "{}", UUID.randomUUID().toString());
        return objectMapper.readTree(resp.body()).get("callId").asText();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return getOnPort(port, path);
    }

    private HttpResponse<String> getOnPort(int p, String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder().uri(URI.create("http://localhost:" + p + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String commandId) throws Exception {
        return postOnPort(port, path, body, commandId);
    }

    private HttpResponse<String> postOnPort(int p, String path, String body, String commandId) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + p + path))
                        .header("Content-Type", "application/json")
                        .header("X-Command-Id", commandId)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postNoCommandId(String path, String body) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithCommandId(String path, String body, String commandId) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .header("X-Command-Id", commandId)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String path, String body, String commandId) throws Exception {
        return patchOnPort(port, path, body, commandId);
    }

    private HttpResponse<String> patchOnPort(int p, String path, String body, String commandId) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + p + path))
                        .header("Content-Type", "application/json")
                        .header("X-Command-Id", commandId)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private Javalin buildApp(TokenClaims claims) {
        var objectMapper = JsonMapper.builder().build();
        var walPort = new RecordingWalPort();
        var lockManager = new EntityLockManager();
        ClockPort clock = () -> FIXED_TIME;
        ArchivePort archivePort = new net.pkhapps.idispatchx.cad.port.secondary.archive.NoOpArchivePort();
        var dispatcher = DispatcherFactory.create(entry -> {}, clock, Duration.ofMinutes(5));
        var callRepo = new InMemoryCallRepository();
        var incidentRepo = new InMemoryIncidentRepository();
        var controller = new CallController(dispatcher,
                new CreateCallCommandHandler(walPort, lockManager, callRepo, clock),
                new UpdateCallDetailsCommandHandler(walPort, lockManager, callRepo, clock),
                new SetCallOutcomeCommandHandler(walPort, lockManager, callRepo, clock),
                new EndCallCommandHandler(walPort, lockManager, callRepo, clock, archivePort),
                new AttachCallToIncidentCommandHandler(walPort, lockManager, callRepo, incidentRepo, clock),
                new DetachCallFromIncidentCommandHandler(walPort, lockManager, callRepo, incidentRepo, clock),
                callRepo);
        var javalinApp = Javalin.create(cfg -> {
            cfg.jsonMapper(new JavalinJackson3(objectMapper, true));
            cfg.startup.showJavalinBanner = false;
        });
        GlobalExceptionHandler.register(javalinApp.unsafe.routes);
        controller.registerRoutes(javalinApp.unsafe.routes, ctx -> AuthContext.setClaims(ctx, claims), "");
        return javalinApp;
    }

    static class RecordingWalPort implements WalPort {
        private long seq = 0;

        @Override
        public SequenceNumber write(DomainEvent event) { return new SequenceNumber(++seq); }

        @Override
        public SequenceNumber writeBatch(List<? extends DomainEvent> events) {
            seq += events.size();
            return new SequenceNumber(seq);
        }

        @Override public void replayFrom(SequenceNumber from, Consumer<DomainEvent> consumer) {}
        @Override public void replay(Consumer<DomainEvent> consumer) {}
        @Override public void truncate(SequenceNumber upTo) {}

        @Override
        public SequenceNumber currentSequence() {
            return seq == 0 ? SequenceNumber.start() : new SequenceNumber(seq);
        }

        @Override
        public long currentSequenceNumber() {
            return seq;
        }
    }
}
