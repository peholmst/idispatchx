package net.pkhapps.idispatchx.cad.adapter.primary.rest.dispatcher;

import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
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
import net.pkhapps.idispatchx.cad.application.handler.AttachCallToIncidentCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.CreateCallCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.DetachCallFromIncidentCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.EndCallCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.IdempotentCommandDispatcher;
import net.pkhapps.idispatchx.cad.application.handler.SetCallOutcomeCommandHandler;
import net.pkhapps.idispatchx.cad.application.handler.UpdateCallDetailsCommandHandler;
import net.pkhapps.idispatchx.cad.domain.command.AttachCallToIncidentCommand;
import net.pkhapps.idispatchx.cad.domain.command.CreateCallCommand;
import net.pkhapps.idispatchx.cad.domain.command.DetachCallFromIncidentCommand;
import net.pkhapps.idispatchx.cad.domain.command.EndCallCommand;
import net.pkhapps.idispatchx.cad.domain.command.SetCallOutcomeCommand;
import net.pkhapps.idispatchx.cad.domain.command.UpdateCallDetailsCommand;
import net.pkhapps.idispatchx.cad.domain.model.call.CallOutcome;
import net.pkhapps.idispatchx.cad.domain.model.call.CallState;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallerName;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.PhoneNumber;
import net.pkhapps.idispatchx.cad.domain.repository.CallRepository;
import net.pkhapps.idispatchx.common.api.ErrorResponse;
import net.pkhapps.idispatchx.common.api.ValidationException;
import net.pkhapps.idispatchx.common.auth.IPAddress;
import net.pkhapps.idispatchx.common.auth.Role;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Javalin controller for dispatcher call management endpoints.
 * <p>
 * All mutating endpoints dispatch commands via {@link IdempotentCommandDispatcher};
 * no {@code CommandHandler.handle()} calls are made directly.
 * <p>
 * Role enforcement: mutating endpoints require {@code Dispatcher}; read-only endpoints
 * accept {@code Dispatcher} or {@code Observer}.
 */
public final class CallController {

    private final IdempotentCommandDispatcher dispatcher;
    private final CreateCallCommandHandler createCallHandler;
    private final UpdateCallDetailsCommandHandler updateCallDetailsHandler;
    private final SetCallOutcomeCommandHandler setCallOutcomeHandler;
    private final EndCallCommandHandler endCallHandler;
    private final AttachCallToIncidentCommandHandler attachCallToIncidentHandler;
    private final DetachCallFromIncidentCommandHandler detachCallFromIncidentHandler;
    private final CallRepository callRepository;

    public CallController(
            IdempotentCommandDispatcher dispatcher,
            CreateCallCommandHandler createCallHandler,
            UpdateCallDetailsCommandHandler updateCallDetailsHandler,
            SetCallOutcomeCommandHandler setCallOutcomeHandler,
            EndCallCommandHandler endCallHandler,
            AttachCallToIncidentCommandHandler attachCallToIncidentHandler,
            DetachCallFromIncidentCommandHandler detachCallFromIncidentHandler,
            CallRepository callRepository) {
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.createCallHandler = Objects.requireNonNull(createCallHandler);
        this.updateCallDetailsHandler = Objects.requireNonNull(updateCallDetailsHandler);
        this.setCallOutcomeHandler = Objects.requireNonNull(setCallOutcomeHandler);
        this.endCallHandler = Objects.requireNonNull(endCallHandler);
        this.attachCallToIncidentHandler = Objects.requireNonNull(attachCallToIncidentHandler);
        this.detachCallFromIncidentHandler = Objects.requireNonNull(detachCallFromIncidentHandler);
        this.callRepository = Objects.requireNonNull(callRepository);
    }

    /**
     * Registers all call management routes on the Javalin instance.
     *
     * @param router          the Javalin routing API
     * @param jwtAuthHandler  before-handler that validates the JWT and populates {@link AuthContext}
     * @param contextPath     configurable URL prefix (empty or starts with {@code /})
     */
    public void registerRoutes(JavalinDefaultRoutingApi router, Handler jwtAuthHandler, String contextPath) {
        var base = contextPath + "/api/v1/calls";

        // Apply JWT auth to all call routes (skip OPTIONS for CORS pre-flight)
        router.before(base, ctx -> { if (ctx.method() != HandlerType.OPTIONS) jwtAuthHandler.handle(ctx); });
        router.before(base + "/*", ctx -> { if (ctx.method() != HandlerType.OPTIONS) jwtAuthHandler.handle(ctx); });

        router.post(base, this::handleCreateCall);
        router.patch(base + "/{callId}", this::handleUpdateCallDetails);
        router.post(base + "/{callId}/end", this::handleEndCall);
        router.post(base + "/{callId}/attach-to-incident", this::handleAttachToIncident);
        router.post(base + "/{callId}/detach-from-incident", this::handleDetachFromIncident);
        router.get(base, this::handleListCalls);
        router.get(base + "/{callId}", this::handleGetCall);
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    @OpenApi(
        path = "/api/v1/calls",
        methods = {HttpMethod.POST},
        operationId = "createCall",
        tags = {"Calls"},
        summary = "Create a new call",
        headers = {@OpenApiParam(name = "X-Command-Id", description = "Idempotency key", required = true)},
        requestBody = @OpenApiRequestBody(content = {@OpenApiContent(from = CallDtos.CreateCallRequest.class)}),
        responses = {
            @OpenApiResponse(status = "201", content = {@OpenApiContent(from = CallDtos.CreateCallResponse.class)}),
            @OpenApiResponse(status = "400", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "401", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "403", content = {@OpenApiContent(from = ErrorResponse.class)})
        }
    )
    private void handleCreateCall(Context ctx) {
        requireRole(ctx, Role.DISPATCHER);
        var commandId = CommandIdExtractor.extract(ctx);
        var claims = AuthContext.requireClaims(ctx);
        var body = ctx.bodyAsClass(CallDtos.CreateCallRequest.class);

        var command = new CreateCallCommand(
                commandId,
                new UserId(claims.subject()),
                extractIpAddress(ctx),
                body.callerName() != null ? new CallerName(body.callerName()) : null,
                body.callerPhoneNumber() != null ? new PhoneNumber(body.callerPhoneNumber()) : null,
                body.location() != null ? body.location().toDomain() : null,
                body.description() != null ? new Description(body.description()) : null
        );

        var callId = dispatcher.dispatch(createCallHandler, command);
        var call = callRepository.findById(callId).orElseThrow();

        ctx.status(201).json(new CallDtos.CreateCallResponse(
                callId.value(),
                call.state().name().toLowerCase(),
                call.receivingDispatcher().value(),
                call.callStarted()
        ));
    }

    @OpenApi(
        path = "/api/v1/calls/{callId}",
        methods = {HttpMethod.PATCH},
        operationId = "updateCallDetails",
        tags = {"Calls"},
        summary = "Update call details",
        headers = {@OpenApiParam(name = "X-Command-Id", description = "Idempotency key", required = true)},
        pathParams = {@OpenApiParam(name = "callId", description = "The call ID", required = true)},
        requestBody = @OpenApiRequestBody(content = {@OpenApiContent(from = CallDtos.UpdateCallDetailsRequest.class)}),
        responses = {
            @OpenApiResponse(status = "200"),
            @OpenApiResponse(status = "400", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "401", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "403", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "404", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "409", content = {@OpenApiContent(from = ErrorResponse.class)})
        }
    )
    private void handleUpdateCallDetails(Context ctx) {
        requireRole(ctx, Role.DISPATCHER);
        var commandId = CommandIdExtractor.extract(ctx);
        var claims = AuthContext.requireClaims(ctx);
        var callId = new CallId(ctx.pathParam("callId"));

        // Parse as JsonNode to distinguish absent fields from explicit-null (clear) fields
        var jsonBody = ctx.bodyAsClass(JsonNode.class);
        var body = ctx.bodyAsClass(CallDtos.UpdateCallDetailsRequest.class);

        boolean callerNamePresent = jsonBody.has("callerName");
        boolean callerPhonePresent = jsonBody.has("callerPhoneNumber");
        boolean locationPresent = jsonBody.has("location");
        boolean descriptionPresent = jsonBody.has("description");
        boolean outcomePresent = jsonBody.has("outcome");
        boolean outcomeRationalePresent = jsonBody.has("outcomeRationale");

        // outcomeRationale without outcome cannot be applied — reject rather than silently discard
        if (outcomeRationalePresent && body.outcomeRationale() != null && !outcomePresent) {
            throw new ValidationException(CadErrorCode.VALIDATION_ERROR,
                    "outcomeRationale requires outcome to also be present");
        }

        boolean hasDetailFields = callerNamePresent || callerPhonePresent || locationPresent || descriptionPresent;
        boolean hasOutcomeFields = outcomePresent && body.outcome() != null;

        if (!hasDetailFields && !hasOutcomeFields) {
            // No-op body: validate the target so 404/409 are returned as documented
            var call = callRepository.findById(callId)
                    .orElseThrow(() -> new NoSuchElementException("call not found: " + callId));
            if (call.state() == CallState.ENDED) {
                throw new IllegalStateException("call is ENDED: " + callId);
            }
            ctx.status(200);
            return;
        }

        // Parse and validate outcome before any dispatch so an invalid value does not leave
        // detail fields committed while the outcome command is rejected.
        @Nullable CallOutcome outcome = null;
        if (hasOutcomeFields) {
            var outcomeStr = body.outcome();
            if (outcomeStr != null) {
                outcome = CallDtos.parseOutcome(outcomeStr);
            }
        }

        if (hasDetailFields) {
            boolean clearCallerName = callerNamePresent && body.callerName() == null;
            boolean clearCallerPhone = callerPhonePresent && body.callerPhoneNumber() == null;
            boolean clearLocation = locationPresent && body.location() == null;
            boolean clearDescription = descriptionPresent && body.description() == null;

            dispatcher.dispatch(updateCallDetailsHandler, new UpdateCallDetailsCommand(
                    commandId,
                    new UserId(claims.subject()),
                    extractIpAddress(ctx),
                    callId,
                    body.callerName() != null ? new CallerName(body.callerName()) : null,
                    clearCallerName,
                    body.callerPhoneNumber() != null ? new PhoneNumber(body.callerPhoneNumber()) : null,
                    clearCallerPhone,
                    body.location() != null ? body.location().toDomain() : null,
                    clearLocation,
                    body.description() != null ? new Description(body.description()) : null,
                    clearDescription
            ));
        }

        if (outcome != null) {
            dispatcher.dispatch(setCallOutcomeHandler, new SetCallOutcomeCommand(
                    CommandIdExtractor.derive(commandId, "set-outcome"),
                    new UserId(claims.subject()),
                    extractIpAddress(ctx),
                    callId,
                    outcome,
                    body.outcomeRationale() != null ? new Description(body.outcomeRationale()) : null
            ));
        }

        ctx.status(200);
    }

    @OpenApi(
        path = "/api/v1/calls/{callId}/end",
        methods = {HttpMethod.POST},
        operationId = "endCall",
        tags = {"Calls"},
        summary = "End a call",
        headers = {@OpenApiParam(name = "X-Command-Id", description = "Idempotency key", required = true)},
        pathParams = {@OpenApiParam(name = "callId", description = "The call ID", required = true)},
        requestBody = @OpenApiRequestBody(required = false, content = {@OpenApiContent(from = CallDtos.EndCallRequest.class)}),
        responses = {
            @OpenApiResponse(status = "200"),
            @OpenApiResponse(status = "400", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "401", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "403", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "404", content = {@OpenApiContent(from = ErrorResponse.class)})
        }
    )
    private void handleEndCall(Context ctx) {
        requireRole(ctx, Role.DISPATCHER);
        var commandId = CommandIdExtractor.extract(ctx);
        var claims = AuthContext.requireClaims(ctx);
        var callId = new CallId(ctx.pathParam("callId"));
        var body = ctx.bodyAsClass(CallDtos.EndCallRequest.class);

        // If outcome is supplied in body, set it before ending
        var endCallOutcomeStr = body.outcome();
        if (endCallOutcomeStr != null) {
            var outcome = CallDtos.parseOutcome(endCallOutcomeStr);
            dispatcher.dispatch(setCallOutcomeHandler, new SetCallOutcomeCommand(
                    CommandIdExtractor.derive(commandId, "pre-end-set-outcome"),
                    new UserId(claims.subject()),
                    extractIpAddress(ctx),
                    callId,
                    outcome,
                    body.outcomeRationale() != null ? new Description(body.outcomeRationale()) : null
            ));
        }

        // Validate that outcome is now set (gives 400 instead of 409 for missing outcome).
        // Note: no ENDED check here — an already-ended call with the same X-Command-Id is a
        // legitimate idempotent retry and must reach IdempotentCommandDispatcher to return 200.
        var call = callRepository.findById(callId)
                .orElseThrow(() -> new NoSuchElementException("call not found: " + callId));
        if (call.outcome() == null) {
            throw new ValidationException(CadErrorCode.VALIDATION_ERROR,
                    "outcome is required to end a call");
        }
        if (call.outcome().requiresRationale() && call.outcomeRationale() == null) {
            throw new ValidationException(CadErrorCode.VALIDATION_ERROR,
                    "outcomeRationale is required for outcome: " + CallDtos.serializeOutcome(call.outcome()));
        }

        dispatcher.dispatch(endCallHandler, new EndCallCommand(
                commandId,
                new UserId(claims.subject()),
                extractIpAddress(ctx),
                callId
        ));
        ctx.status(200);
    }

    @OpenApi(
        path = "/api/v1/calls/{callId}/attach-to-incident",
        methods = {HttpMethod.POST},
        operationId = "attachCallToIncident",
        tags = {"Calls"},
        summary = "Attach a call to an incident",
        headers = {@OpenApiParam(name = "X-Command-Id", description = "Idempotency key", required = true)},
        pathParams = {@OpenApiParam(name = "callId", description = "The call ID", required = true)},
        requestBody = @OpenApiRequestBody(content = {@OpenApiContent(from = CallDtos.AttachCallToIncidentRequest.class)}),
        responses = {
            @OpenApiResponse(status = "200"),
            @OpenApiResponse(status = "400", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "401", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "403", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "404", content = {@OpenApiContent(from = ErrorResponse.class)})
        }
    )
    private void handleAttachToIncident(Context ctx) {
        requireRole(ctx, Role.DISPATCHER);
        var commandId = CommandIdExtractor.extract(ctx);
        var claims = AuthContext.requireClaims(ctx);
        var callId = new CallId(ctx.pathParam("callId"));
        var body = ctx.bodyAsClass(CallDtos.AttachCallToIncidentRequest.class);

        if (body.incidentId() == null || body.incidentId().isBlank()) {
            throw new ValidationException(CadErrorCode.VALIDATION_ERROR, "incidentId is required");
        }

        dispatcher.dispatch(attachCallToIncidentHandler, new AttachCallToIncidentCommand(
                commandId,
                new UserId(claims.subject()),
                extractIpAddress(ctx),
                callId,
                new IncidentId(body.incidentId())
        ));
        ctx.status(200);
    }

    @OpenApi(
        path = "/api/v1/calls/{callId}/detach-from-incident",
        methods = {HttpMethod.POST},
        operationId = "detachCallFromIncident",
        tags = {"Calls"},
        summary = "Detach a call from its incident",
        headers = {@OpenApiParam(name = "X-Command-Id", description = "Idempotency key", required = true)},
        pathParams = {@OpenApiParam(name = "callId", description = "The call ID", required = true)},
        responses = {
            @OpenApiResponse(status = "200"),
            @OpenApiResponse(status = "401", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "403", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "404", content = {@OpenApiContent(from = ErrorResponse.class)})
        }
    )
    private void handleDetachFromIncident(Context ctx) {
        requireRole(ctx, Role.DISPATCHER);
        var commandId = CommandIdExtractor.extract(ctx);
        var claims = AuthContext.requireClaims(ctx);
        var callId = new CallId(ctx.pathParam("callId"));

        dispatcher.dispatch(detachCallFromIncidentHandler, new DetachCallFromIncidentCommand(
                commandId,
                new UserId(claims.subject()),
                extractIpAddress(ctx),
                callId
        ));
        ctx.status(200);
    }

    @OpenApi(
        path = "/api/v1/calls",
        methods = {HttpMethod.GET},
        operationId = "listCalls",
        tags = {"Calls"},
        summary = "List active calls",
        responses = {
            @OpenApiResponse(status = "200", content = {@OpenApiContent(from = CallDtos.CallListResponse.class)}),
            @OpenApiResponse(status = "401", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "403", content = {@OpenApiContent(from = ErrorResponse.class)})
        }
    )
    private void handleListCalls(Context ctx) {
        requireAnyRole(ctx, Role.DISPATCHER, Role.OBSERVER);
        var calls = callRepository.findActive()
                .map(CallDtos::fromDomain)
                .toList();
        ctx.json(new CallDtos.CallListResponse(calls));
    }

    @OpenApi(
        path = "/api/v1/calls/{callId}",
        methods = {HttpMethod.GET},
        operationId = "getCall",
        tags = {"Calls"},
        summary = "Get a specific call",
        pathParams = {@OpenApiParam(name = "callId", description = "The call ID", required = true)},
        responses = {
            @OpenApiResponse(status = "200", content = {@OpenApiContent(from = CallDtos.CallResponse.class)}),
            @OpenApiResponse(status = "401", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "403", content = {@OpenApiContent(from = ErrorResponse.class)}),
            @OpenApiResponse(status = "404", content = {@OpenApiContent(from = ErrorResponse.class)})
        }
    )
    private void handleGetCall(Context ctx) {
        requireAnyRole(ctx, Role.DISPATCHER, Role.OBSERVER);
        var callId = new CallId(ctx.pathParam("callId"));
        var call = callRepository.findById(callId)
                .orElseThrow(() -> new NoSuchElementException("call not found: " + callId));
        ctx.json(CallDtos.fromDomain(call));
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
