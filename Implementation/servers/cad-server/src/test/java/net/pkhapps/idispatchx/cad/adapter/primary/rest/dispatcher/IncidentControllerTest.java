package net.pkhapps.idispatchx.cad.adapter.primary.rest.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import net.pkhapps.idispatchx.cad.adapter.auth.AuthContext;
import net.pkhapps.idispatchx.cad.adapter.primary.rest.shared.GlobalExceptionHandler;
import net.pkhapps.idispatchx.cad.application.handler.CreateCallCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.CreateIncidentFromCallCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.DispatcherFactory;
import net.pkhapps.idispatchx.cad.application.handler.EntityLockManager;
import net.pkhapps.idispatchx.cad.domain.command.CommandId;
import net.pkhapps.idispatchx.cad.domain.command.CreateCallCommand;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import net.pkhapps.idispatchx.cad.domain.repository.InMemoryCallRepository;
import net.pkhapps.idispatchx.cad.domain.repository.InMemoryIncidentRepository;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import net.pkhapps.idispatchx.cad.port.secondary.commandlog.CommandLogPort;
import net.pkhapps.idispatchx.cad.port.secondary.wal.WalPort;
import net.pkhapps.idispatchx.common.auth.Role;
import net.pkhapps.idispatchx.common.auth.TokenClaims;
import net.pkhapps.idispatchx.common.auth.UserId;
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

class IncidentControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-06-13T10:00:00Z");
    private static final TokenClaims DISPATCHER_CLAIMS = new TokenClaims(
            "dispatcher-user", "https://auth.example.com", "sid-1",
            Set.of(Role.DISPATCHER), FIXED_TIME.plusSeconds(3600), FIXED_TIME);
    private static final TokenClaims OBSERVER_CLAIMS = new TokenClaims(
            "observer-user", "https://auth.example.com", "sid-2",
            Set.of(Role.OBSERVER), FIXED_TIME.plusSeconds(3600), FIXED_TIME);

    private InMemoryCallRepository callRepository;
    private InMemoryIncidentRepository incidentRepository;
    private WalPort walPort;
    private EntityLockManager lockManager;
    private ClockPort clock;
    private Javalin app;
    private int port;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() throws IOException {
        callRepository = new InMemoryCallRepository();
        incidentRepository = new InMemoryIncidentRepository();
        walPort = new RecordingWalPort();
        lockManager = new EntityLockManager();
        clock = () -> FIXED_TIME;
        app = buildApp(DISPATCHER_CLAIMS);
        try (var s = new ServerSocket(0)) { port = s.getLocalPort(); }
        app.start(port);
    }

    @AfterEach
    void tearDown() {
        if (app != null) app.stop();
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/incidents
    // -----------------------------------------------------------------------

    @Test
    void createIncident_withSourceCallId_returns201() throws Exception {
        var callId = createCallInRepository();
        var response = post("/api/v1/incidents",
                "{\"sourceCallId\":\"" + callId + "\"}",
                UUID.randomUUID().toString());
        assertEquals(201, response.statusCode());
        assertTrue(objectMapper.readTree(response.body()).has("incidentId"));
    }

    @Test
    void createIncident_withoutSourceCallId_returns501() throws Exception {
        var response = post("/api/v1/incidents", "{}", UUID.randomUUID().toString());
        assertEquals(501, response.statusCode());
        assertTrue(response.body().contains("NOT_IMPLEMENTED"));
    }

    @Test
    void createIncident_sourceCallNotFound_returns404() throws Exception {
        var response = post("/api/v1/incidents",
                "{\"sourceCallId\":\"V1StGXR8_Z5jdHi6B-myT\"}",
                UUID.randomUUID().toString());
        assertEquals(404, response.statusCode());
    }

    @Test
    void createIncident_observerForbidden() throws Exception {
        var observerApp = buildApp(OBSERVER_CLAIMS);
        int observerPort;
        try (var s = new ServerSocket(0)) { observerPort = s.getLocalPort(); }
        observerApp.start(observerPort);
        try {
            assertEquals(403, postOnPort(observerPort, "/api/v1/incidents", "{}", UUID.randomUUID().toString()).statusCode());
        } finally {
            observerApp.stop();
        }
    }

    @Test
    void createIncident_missingCommandId_returns400() throws Exception {
        var response = postNoCommandId("/api/v1/incidents", "{}");
        assertEquals(400, response.statusCode());
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/incidents
    // -----------------------------------------------------------------------

    @Test
    void listIncidents_returnsEmptyList() throws Exception {
        var response = get("/api/v1/incidents");
        assertEquals(200, response.statusCode());
        var body = objectMapper.readTree(response.body());
        assertNotNull(body.get("incidents"));
        assertEquals(0, body.get("incidents").size());
    }

    @Test
    void listIncidents_includesCallIds() throws Exception {
        var callId = createCallInRepository();
        post("/api/v1/incidents", "{\"sourceCallId\":\"" + callId + "\"}", UUID.randomUUID().toString());

        var response = get("/api/v1/incidents");
        assertEquals(200, response.statusCode());
        var body = response.body();
        assertTrue(body.contains("incidentId"));
        assertTrue(body.contains("callIds"));
        assertTrue(body.contains(callId));
    }

    @Test
    void listIncidents_allowsObserverRole() throws Exception {
        var observerApp = buildApp(OBSERVER_CLAIMS);
        int observerPort;
        try (var s = new ServerSocket(0)) { observerPort = s.getLocalPort(); }
        observerApp.start(observerPort);
        try {
            assertEquals(200, getOnPort(observerPort, "/api/v1/incidents").statusCode());
        } finally {
            observerApp.stop();
        }
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/incidents/{incidentId}
    // -----------------------------------------------------------------------

    @Test
    void getIncident_notFound_returns404() throws Exception {
        assertEquals(404, get("/api/v1/incidents/6byYFiLM_BkBZ5IFKhbRF").statusCode());
    }

    @Test
    void getIncident_returnsDetailWithLogEntriesAndCallIds() throws Exception {
        var callId = createCallInRepository();
        var createResp = post("/api/v1/incidents",
                "{\"sourceCallId\":\"" + callId + "\"}", UUID.randomUUID().toString());
        var incidentId = objectMapper.readTree(createResp.body()).get("incidentId").asText();

        var response = get("/api/v1/incidents/" + incidentId);
        assertEquals(200, response.statusCode());
        var body = response.body();
        assertTrue(body.contains("logEntries"));
        assertTrue(body.contains("callIds"));
        assertTrue(body.contains(callId));
    }

    @Test
    void getIncident_allowsObserverRole() throws Exception {
        var observerApp = buildApp(OBSERVER_CLAIMS);
        int observerPort;
        try (var s = new ServerSocket(0)) { observerPort = s.getLocalPort(); }
        observerApp.start(observerPort);
        try {
            assertEquals(404, getOnPort(observerPort, "/api/v1/incidents/6byYFiLM_BkBZ5IFKhbRF").statusCode());
        } finally {
            observerApp.stop();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String createCallInRepository() {
        CommandLogPort logPort = entry -> {};
        var dispatcher = DispatcherFactory.create(logPort, clock, Duration.ofMinutes(5));
        var handler = new CreateCallCommandHandler(walPort, lockManager, callRepository, clock);
        var cmd = new CreateCallCommand(CommandId.generate(), new UserId("test-dispatcher"),
                null, null, null, null, null);
        return dispatcher.dispatch(handler, cmd).value();
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

    private Javalin buildApp(TokenClaims claims) {
        var om = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var dispatcher = DispatcherFactory.create(entry -> {}, clock, Duration.ofMinutes(5));
        var controller = new IncidentController(dispatcher,
                new CreateIncidentFromCallCommandHandler(walPort, lockManager, callRepository, incidentRepository, clock),
                incidentRepository, callRepository);
        var javalinApp = Javalin.create(cfg -> {
            cfg.jsonMapper(new JavalinJackson(om, true));
            cfg.showJavalinBanner = false;
        });
        GlobalExceptionHandler.register(javalinApp);
        controller.registerRoutes(javalinApp, ctx -> AuthContext.setClaims(ctx, claims), "");
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
