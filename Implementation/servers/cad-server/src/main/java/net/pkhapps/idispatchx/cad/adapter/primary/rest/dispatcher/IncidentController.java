package net.pkhapps.idispatchx.cad.adapter.primary.rest.dispatcher;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import net.pkhapps.idispatchx.cad.adapter.auth.AuthContext;
import net.pkhapps.idispatchx.cad.adapter.primary.rest.shared.CadErrorCode;
import net.pkhapps.idispatchx.cad.adapter.primary.rest.shared.CommandIdExtractor;
import net.pkhapps.idispatchx.cad.application.handler.CreateIncidentFromCallCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.IdempotentCommandDispatcher;
import net.pkhapps.idispatchx.cad.domain.command.CreateIncidentFromCallCommand;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentType;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.repository.CallRepository;
import net.pkhapps.idispatchx.cad.domain.repository.IncidentRepository;
import net.pkhapps.idispatchx.common.api.ErrorResponse;
import net.pkhapps.idispatchx.common.auth.IPAddress;
import net.pkhapps.idispatchx.common.auth.Role;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Javalin controller for dispatcher incident management endpoints (partial for UC-Enter-Call-Details).
 * <p>
 * Only the endpoints required for this use case are implemented:
 * <ul>
 *   <li>{@code POST /api/v1/incidents} — create incident from call (requires {@code sourceCallId}).
 *       Without {@code sourceCallId}, responds 501 Not Implemented.</li>
 *   <li>{@code GET /api/v1/incidents} — list incident summaries</li>
 *   <li>{@code GET /api/v1/incidents/{incidentId}} — get full incident detail</li>
 * </ul>
 *
 * <p>Full incident management endpoints (update, set-state, log entries, unit assignment) will be
 * implemented in later issues and added to this controller at that time.
 */
public final class IncidentController {

    private final IdempotentCommandDispatcher dispatcher;
    private final CreateIncidentFromCallCommandHandler createIncidentFromCallHandler;
    private final IncidentRepository incidentRepository;
    private final CallRepository callRepository;

    public IncidentController(
            IdempotentCommandDispatcher dispatcher,
            CreateIncidentFromCallCommandHandler createIncidentFromCallHandler,
            IncidentRepository incidentRepository,
            CallRepository callRepository) {
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.createIncidentFromCallHandler = Objects.requireNonNull(createIncidentFromCallHandler);
        this.incidentRepository = Objects.requireNonNull(incidentRepository);
        this.callRepository = Objects.requireNonNull(callRepository);
    }

    /**
     * Registers incident management routes on the Javalin instance.
     *
     * @param app            the Javalin application
     * @param jwtAuthHandler before-handler that validates the JWT and populates {@link AuthContext}
     * @param contextPath    configurable URL prefix (empty or starts with {@code /})
     */
    public void registerRoutes(Javalin app, Handler jwtAuthHandler, String contextPath) {
        var base = contextPath + "/api/v1/incidents";

        app.before(base, ctx -> { if (ctx.method() != HandlerType.OPTIONS) jwtAuthHandler.handle(ctx); });
        app.before(base + "/*", ctx -> { if (ctx.method() != HandlerType.OPTIONS) jwtAuthHandler.handle(ctx); });

        app.post(base, this::handleCreateIncident);
        app.get(base, this::handleListIncidents);
        app.get(base + "/{incidentId}", this::handleGetIncident);
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    @OpenApi(
        path = "/api/v1/incidents",
        methods = {HttpMethod.POST},
        operationId = "createIncident",
        tags = {"Incidents"},
        summary = "Create an incident from a call",
        headers = {@OpenApiParam(name = "X-Command-Id", description = "Idempotency key", required = true)},
        requestBody = @OpenApiRequestBody(content = {@OpenApiContent(from = IncidentDtos.CreateIncidentRequest.class)}),
        responses = {
            @OpenApiResponse(status = "201", content = {@OpenApiContent(from = IncidentDtos.CreateIncidentResponse.class)}),
            @OpenApiResponse(status = "400", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "401", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "403", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "501", content = {@OpenApiContent(from = ErrorResponse.class)})
        }
    )
    private void handleCreateIncident(Context ctx) {
        requireRole(ctx, Role.DISPATCHER);
        var commandId = CommandIdExtractor.extract(ctx);
        var claims = AuthContext.requireClaims(ctx);
        var body = ctx.bodyAsClass(IncidentDtos.CreateIncidentRequest.class);

        // Standalone incident creation (no sourceCallId) is out of scope for this issue
        if (body.sourceCallId() == null) {
            ctx.status(501).json(ErrorResponse.of(
                    CadErrorCode.NOT_IMPLEMENTED,
                    "Standalone incident creation is not yet implemented; provide sourceCallId",
                    ctx.path()));
            return;
        }

        var incidentId = dispatcher.dispatch(createIncidentFromCallHandler, new CreateIncidentFromCallCommand(
                commandId,
                new UserId(claims.subject()),
                extractIpAddress(ctx),
                new CallId(body.sourceCallId()),
                body.incidentType() != null ? new IncidentType(body.incidentType()) : null,
                body.incidentPriority() != null ? IncidentDtos.parsePriority(body.incidentPriority()) : null,
                body.location() != null ? body.location().toDomain() : null,
                body.description() != null ? new Description(body.description()) : null
        ));

        ctx.status(201).json(new IncidentDtos.CreateIncidentResponse(incidentId.value()));
    }

    @OpenApi(
        path = "/api/v1/incidents",
        methods = {HttpMethod.GET},
        operationId = "listIncidents",
        tags = {"Incidents"},
        summary = "List incidents",
        queryParams = {@OpenApiParam(name = "includeEnded", description = "Include ended incidents", type = Boolean.class)},
        responses = {
            @OpenApiResponse(status = "200", content = {@OpenApiContent(from = IncidentDtos.IncidentListResponse.class)}),
            @OpenApiResponse(status = "401", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "403", content = {@OpenApiContent(from = ErrorResponse.class)})
        }
    )
    private void handleListIncidents(Context ctx) {
        requireAnyRole(ctx, Role.DISPATCHER, Role.OBSERVER);
        var includeEnded = Boolean.parseBoolean(ctx.queryParam("includeEnded"));

        var incidents = (includeEnded
                ? incidentRepository.findAll()
                : incidentRepository.findActive())
                .map(incident -> IncidentDtos.toSummary(incident, callRepository))
                .toList();

        ctx.json(new IncidentDtos.IncidentListResponse(incidents));
    }

    @OpenApi(
        path = "/api/v1/incidents/{incidentId}",
        methods = {HttpMethod.GET},
        operationId = "getIncident",
        tags = {"Incidents"},
        summary = "Get a specific incident",
        pathParams = {@OpenApiParam(name = "incidentId", description = "The incident ID", required = true)},
        responses = {
            @OpenApiResponse(status = "200", content = {@OpenApiContent(from = IncidentDtos.IncidentDetailResponse.class)}),
            @OpenApiResponse(status = "401", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "403", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "404", content = {@OpenApiContent(from = ErrorResponse.class)})
        }
    )
    private void handleGetIncident(Context ctx) {
        requireAnyRole(ctx, Role.DISPATCHER, Role.OBSERVER);
        var incidentId = new IncidentId(ctx.pathParam("incidentId"));
        var incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NoSuchElementException("incident not found: " + incidentId));
        ctx.json(IncidentDtos.toDetail(incident, callRepository));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void requireRole(Context ctx, Role role) {
        var claims = AuthContext.requireClaims(ctx);
        if (!claims.hasRole(role)) {
            throw new ForbiddenResponse("Insufficient permissions");
        }
    }

    private static void requireAnyRole(Context ctx, Role... roles) {
        var claims = AuthContext.requireClaims(ctx);
        if (!claims.hasAnyRole(roles)) {
            throw new ForbiddenResponse("Insufficient permissions");
        }
    }

    private static @Nullable IPAddress extractIpAddress(Context ctx) {
        var ip = ctx.ip();
        if (ip == null || ip.isBlank()) return null;
        try {
            return new IPAddress(ip);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
