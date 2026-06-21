package net.pkhapps.idispatchx.cad.server;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson3;
import tools.jackson.databind.json.JsonMapper;
import io.javalin.openapi.plugin.OpenApiPlugin;
import net.pkhapps.idispatchx.cad.adapter.auth.BackChannelLogoutHandler;
import net.pkhapps.idispatchx.cad.adapter.auth.JwtAuthHandler;
import net.pkhapps.idispatchx.cad.adapter.broadcast.ArchiveHealthMonitor;
import net.pkhapps.idispatchx.cad.adapter.broadcast.DispatcherBroadcastService;
import net.pkhapps.idispatchx.cad.adapter.broadcast.EventBroadcaster;
import net.pkhapps.idispatchx.cad.adapter.broadcast.SessionRegistry;
import net.pkhapps.idispatchx.cad.adapter.primary.rest.dispatcher.CallController;
import net.pkhapps.idispatchx.cad.adapter.primary.rest.dispatcher.IncidentController;
import net.pkhapps.idispatchx.cad.adapter.primary.rest.shared.GlobalExceptionHandler;
import net.pkhapps.idispatchx.cad.adapter.primary.websocket.dispatcher.DispatcherWebSocketHandler;
import net.pkhapps.idispatchx.cad.adapter.secondary.commandlog.FileBasedCommandLogAdapter;
import net.pkhapps.idispatchx.cad.adapter.secondary.snapshot.FileBasedSnapshotAdapter;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.DomainEventSerializer;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.EventPublishingWalPort;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.FileBasedWalAdapter;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.JsonDomainEventSerializer;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.SmileDomainEventSerializer;
import net.pkhapps.idispatchx.cad.adapter.secondary.wal.WalFormat;
import net.pkhapps.idispatchx.cad.application.handler.AttachCallToIncidentCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.CreateCallCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.CreateIncidentFromCallCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.DetachCallFromIncidentCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.DispatcherFactory;
import net.pkhapps.idispatchx.cad.application.handler.EndCallCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.EntityLockManager;
import net.pkhapps.idispatchx.cad.application.handler.SetCallOutcomeCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.UpdateCallDetailsCommandHandler;
import net.pkhapps.idispatchx.cad.application.replay.WalReplayService;
import net.pkhapps.idispatchx.cad.domain.event.CallAttachedToIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallDetachedFromIncidentEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallEndedEvent;
import net.pkhapps.idispatchx.cad.domain.event.CallUpdatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.DomainEvent;
import net.pkhapps.idispatchx.cad.domain.event.IncidentCreatedEvent;
import net.pkhapps.idispatchx.cad.domain.event.IncidentLogEntryAddedEvent;
import net.pkhapps.idispatchx.cad.domain.repository.InMemoryCallRepository;
import net.pkhapps.idispatchx.cad.domain.repository.InMemoryIncidentRepository;
import net.pkhapps.idispatchx.cad.domain.model.shared.SequenceNumber;
import net.pkhapps.idispatchx.cad.port.secondary.archive.NoOpArchivePort;
import net.pkhapps.idispatchx.cad.port.secondary.clock.ClockPort;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.CallSnapshot;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.IncidentSnapshot;
import net.pkhapps.idispatchx.cad.port.secondary.snapshot.OperationalState;
import net.pkhapps.idispatchx.cad.server.config.CadServerConfig;
import net.pkhapps.idispatchx.common.auth.JwksClient;
import net.pkhapps.idispatchx.common.auth.SessionStore;
import net.pkhapps.idispatchx.common.auth.TokenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CAD Server application that provides Computer-Aided Dispatch services.
 * <p>
 * Manages the full server lifecycle including:
 * <ul>
 *   <li>WAL and snapshot initialization and startup replay</li>
 *   <li>Domain repositories and command handlers</li>
 *   <li>WebSocket broadcasting</li>
 *   <li>JWT authentication</li>
 *   <li>HTTP and WebSocket server (Javalin)</li>
 *   <li>Periodic WAL snapshots with WAL truncation and old snapshot purging</li>
 * </ul>
 */
public final class CadServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CadServer.class);

    private static final List<Class<? extends DomainEvent>> DOMAIN_EVENT_TYPES = List.of(
            CallCreatedEvent.class,
            CallUpdatedEvent.class,
            CallEndedEvent.class,
            CallAttachedToIncidentEvent.class,
            CallDetachedFromIncidentEvent.class,
            IncidentCreatedEvent.class,
            IncidentLogEntryAddedEvent.class
    );

    private final CadServerConfig config;
    private final FileBasedWalAdapter walAdapter;
    private final FileBasedCommandLogAdapter commandLogAdapter;
    private final FileBasedSnapshotAdapter snapshotAdapter;
    private final InMemoryCallRepository callRepository;
    private final InMemoryIncidentRepository incidentRepository;
    private final EventPublishingWalPort walPort;
    private final SessionStore sessionStore;
    private final Javalin javalin;
    private final ScheduledExecutorService snapshotScheduler;
    private final ScheduledExecutorService healthCheckScheduler;
    private final ExecutorService broadcastExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Creates and initializes the CAD server.
     *
     * @param config the server configuration
     * @throws IOException if WAL, snapshot, or command log storage cannot be initialized
     */
    public CadServer(CadServerConfig config) throws IOException {
        this.config = Objects.requireNonNull(config, "config must not be null");

        log.info("Initializing CAD Server...");

        // Create serializer matching the WAL format
        DomainEventSerializer serializer = config.walConfig().format() == WalFormat.TEXT
                ? new JsonDomainEventSerializer(DOMAIN_EVENT_TYPES)
                : new SmileDomainEventSerializer(DOMAIN_EVENT_TYPES);

        // Initialize storage adapters
        this.walAdapter = new FileBasedWalAdapter(config.walConfig(), serializer);
        this.snapshotAdapter = new FileBasedSnapshotAdapter(config.snapshotConfig(), serializer);
        this.commandLogAdapter = new FileBasedCommandLogAdapter(config.commandLogConfig());

        // Initialize domain repositories
        this.callRepository = new InMemoryCallRepository();
        this.incidentRepository = new InMemoryIncidentRepository();

        // Initialize broadcasting
        this.broadcastExecutor = Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r, "broadcast-executor");
            t.setDaemon(true);
            return t;
        });
        var sessionRegistry = new SessionRegistry();
        var dispatcherBroadcastService = new DispatcherBroadcastService(sessionRegistry);
        var eventBroadcaster = new EventBroadcaster(dispatcherBroadcastService, callRepository);

        // Wrap WAL adapter with event-publishing decorator
        this.walPort = new EventPublishingWalPort(walAdapter, eventBroadcaster, broadcastExecutor);

        // Initialize archive health monitoring
        var archivePort = new NoOpArchivePort();
        var archiveHealthMonitor = new ArchiveHealthMonitor(archivePort, dispatcherBroadcastService, walPort);
        this.healthCheckScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "archive-health-checker");
            t.setDaemon(true);
            return t;
        });
        archiveHealthMonitor.start(healthCheckScheduler, config.archiveHealthCheckIntervalSeconds());

        // Initialize command infrastructure
        var lockManager = new EntityLockManager();
        ClockPort clock = Instant::now;
        var idempotentDispatcher = DispatcherFactory.create(
                commandLogAdapter, clock, config.idempotencyRetentionPeriod());

        // Create command handlers
        var createCallHandler = new CreateCallCommandHandler(
                walPort, lockManager, callRepository, clock);
        var updateCallDetailsHandler = new UpdateCallDetailsCommandHandler(
                walPort, lockManager, callRepository, clock);
        var setCallOutcomeHandler = new SetCallOutcomeCommandHandler(
                walPort, lockManager, callRepository, clock);
        var endCallHandler = new EndCallCommandHandler(
                walPort, lockManager, callRepository, clock, archivePort);
        var attachCallToIncidentHandler = new AttachCallToIncidentCommandHandler(
                walPort, lockManager, callRepository, incidentRepository, clock);
        var detachCallFromIncidentHandler = new DetachCallFromIncidentCommandHandler(
                walPort, lockManager, callRepository, incidentRepository, clock);
        var createIncidentFromCallHandler = new CreateIncidentFromCallCommandHandler(
                walPort, lockManager, callRepository, incidentRepository, clock);

        // Perform startup WAL replay (with snapshot if available)
        replayWalOnStartup();

        // Initialize auth components
        var jwksClient = new JwksClient(config.oidcConfig().jwksUrl());
        var tokenValidator = new TokenValidator(jwksClient, config.oidcConfig().issuer().toString());
        this.sessionStore = new SessionStore();
        var jwtAuthHandler = new JwtAuthHandler(tokenValidator, sessionStore);
        var backChannelLogoutHandler = new BackChannelLogoutHandler(
                jwksClient,
                config.oidcConfig().issuer().toString(),
                sessionStore,
                sessionRegistry);

        // Initialize Javalin
        this.javalin = createJavalin();

        // Register global exception handler
        GlobalExceptionHandler.register(javalin.unsafe.routes);

        // Register routes
        var contextPath = config.contextPath();
        new CallController(
                idempotentDispatcher,
                createCallHandler,
                updateCallDetailsHandler,
                setCallOutcomeHandler,
                endCallHandler,
                attachCallToIncidentHandler,
                detachCallFromIncidentHandler,
                callRepository
        ).registerRoutes(javalin.unsafe.routes, jwtAuthHandler, contextPath);

        new IncidentController(
                idempotentDispatcher,
                createIncidentFromCallHandler,
                incidentRepository,
                callRepository
        ).registerRoutes(javalin.unsafe.routes, jwtAuthHandler, contextPath);

        new DispatcherWebSocketHandler(
                tokenValidator,
                sessionStore,
                sessionRegistry,
                createObjectMapper(),
                walPort,
                archiveHealthMonitor
        ).registerRoutes(javalin.unsafe.routes, contextPath);

        backChannelLogoutHandler.registerRoutes(javalin.unsafe.routes, contextPath);

        // Schedule periodic WAL snapshots
        this.snapshotScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "snapshot-scheduler");
            t.setDaemon(true);
            return t;
        });
        snapshotScheduler.scheduleAtFixedRate(
                this::createSnapshot,
                config.snapshotIntervalSeconds(),
                config.snapshotIntervalSeconds(),
                TimeUnit.SECONDS);

        log.info("CAD Server initialized");
    }

    /**
     * Starts the HTTP server.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting CAD Server on port {}", config.port());
            javalin.start(config.port());
            log.info("CAD Server started on port {}", config.port());
        } else {
            log.warn("CAD Server is already running");
        }
    }

    /**
     * Stops the server and releases all resources.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping CAD Server...");
            snapshotScheduler.shutdown();
            healthCheckScheduler.shutdown();
            javalin.stop();
            broadcastExecutor.shutdown();
            sessionStore.close();
            try {
                commandLogAdapter.close();
            } catch (Exception e) {
                log.warn("Error closing command log adapter", e);
            }
            try {
                walAdapter.close();
            } catch (Exception e) {
                log.warn("Error closing WAL adapter", e);
            }
            log.info("CAD Server stopped");
        }
    }

    @Override
    public void close() {
        stop();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void replayWalOnStartup() {
        var walReplayService = new WalReplayService(walPort, callRepository, incidentRepository);
        var snapshot = snapshotAdapter.loadLatestSnapshot();
        if (snapshot.isPresent()) {
            var s = snapshot.get();
            log.info("Restoring state from snapshot at seq={}", s.sequenceNumber().value());
            s.state().calls().stream().map(CallSnapshot::toCall).forEach(callRepository::add);
            s.state().incidents().stream().map(IncidentSnapshot::toIncident).forEach(incidentRepository::add);
            walReplayService.replayFrom(s.sequenceNumber());
        } else {
            walReplayService.replayAll();
        }
    }

    private void createSnapshot() {
        try {
            log.info("Creating WAL snapshot...");
            var state = new OperationalState(
                    incidentRepository.findAll().map(IncidentSnapshot::from).toList(),
                    callRepository.findAll().map(CallSnapshot::from).toList(),
                    List.of()
            );
            // Use lastAppliedSequence rather than currentSequence() so the snapshot only
            // claims up to the highest sequence whose repository mutation is fully applied.
            // This closes the race where the WAL has event N but its mutation is not yet
            // reflected in the repository; a snapshot at N would then lose N's effect after
            // WAL truncation and replay-from(N+1).
            var lastApplied = walPort.lastAppliedSequence();
            if (lastApplied == 0) {
                log.info("Skipping WAL snapshot: no mutations applied yet");
                return;
            }
            var seq = new SequenceNumber(lastApplied);
            snapshotAdapter.createSnapshot(state, seq);
            walPort.truncate(seq);
            snapshotAdapter.purgeOlderSnapshots(seq);
            log.info("WAL snapshot created at seq={}", lastApplied);
        } catch (Exception e) {
            log.error("Failed to create WAL snapshot", e);
        }
    }

    private Javalin createJavalin() {
        var objectMapper = createObjectMapper();
        var corsOrigins = config.corsAllowedOrigins();
        return Javalin.create(javalinConfig -> {
            javalinConfig.jsonMapper(new JavalinJackson3(objectMapper, true));
            javalinConfig.startup.showJavalinBanner = false;
            javalinConfig.registerPlugin(new OpenApiPlugin(openApiConfig -> openApiConfig
                .withDocumentationPath(config.contextPath() + "/openapi")
                .withDefinitionConfiguration((version, definition) ->
                    OpenApiContextPathSupport.applyContextPathServer(definition, config.contextPath())
                        .info(info -> info
                            .title("CAD Server API")
                            .version("1.0.0")
                            .description("Computer-Aided Dispatch REST API for iDispatchX")))
            ));
            if (!corsOrigins.isBlank()) {
                var origins = corsOrigins.split(",");
                javalinConfig.bundledPlugins.enableCors(cors ->
                        cors.addRule(rule -> {
                            for (var origin : origins) {
                                rule.allowHost(origin.trim());
                            }
                        })
                );
            }
        });
    }

    private static JsonMapper createObjectMapper() {
        return JsonMapper.builder().build();
    }
}
