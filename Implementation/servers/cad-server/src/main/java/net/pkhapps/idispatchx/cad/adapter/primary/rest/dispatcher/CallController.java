package net.pkhapps.idispatchx.cad.adapter.primary.rest.dispatcher;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
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
import net.pkhapps.idispatchx.cad.domain.model.call.CallState;
import net.pkhapps.idispatchx.cad.domain.model.incident.IncidentId;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallerName;
import net.pkhapps.idispatchx.cad.domain.model.shared.CallId;
import net.pkhapps.idispatchx.cad.domain.model.shared.Description;
import net.pkhapps.idispatchx.cad.domain.model.shared.PhoneNumber;
import net.pkhapps.idispatchx.cad.domain.repository.CallRepository;
import net.pkhapps.idispatchx.common.api.ValidationException;
import net.pkhapps.idispatchx.common.auth.IPAddress;
import net.pkhapps.idispatchx.common.auth.Role;
import net.pkhapps.idispatchx.common.auth.UserId;
import org.jspecify.annotations.Nullable;

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
     * @param app             the Javalin application
     * @param jwtAuthHandler  before-handler that validates the JWT and populates {@link AuthContext}
     * @param contextPath     configurable URL prefix (empty or starts with {@code /})
     */
    public void registerRoutes(Javalin app, Handler jwtAuthHandler, String contextPath) {
        var base = contextPath + "/api/v1/calls";

        // Apply JWT auth to all call routes (skip OPTIONS for CORS pre-flight)
        app.before(base, ctx -> { if (ctx.method() != HandlerType.OPTIONS) jwtAuthHandler.handle(ctx); });
        app.before(base + "/*", ctx -> { if (ctx.method() != HandlerType.OPTIONS) jwtAuthHandler.handle(ctx); });

        app.post(base, this::handleCreateCall);
        app.patch(base + "/{callId}", this::handleUpdateCallDetails);
        app.post(base + "/{callId}/end", this::handleEndCall);
        app.post(base + "/{callId}/attach-to-incident", this::handleAttachToIncident);
        app.post(base + "/{callId}/detach-from-incident", this::handleDetachFromIncident);
        app.get(base, this::handleListCalls);
        app.get(base + "/{callId}", this::handleGetCall);
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

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

    private void handleUpdateCallDetails(Context ctx) {
        requireRole(ctx, Role.DISPATCHER);
        var commandId = CommandIdExtractor.extract(ctx);
        var claims = AuthContext.requireClaims(ctx);
        var callId = new CallId(ctx.pathParam("callId"));
        var body = ctx.bodyAsClass(CallDtos.UpdateCallDetailsRequest.class);

        boolean hasDetailFields = body.callerName() != null || body.callerPhoneNumber() != null
                || body.location() != null || body.description() != null;
        boolean hasOutcomeFields = body.outcome() != null;

        if (hasDetailFields) {
            dispatcher.dispatch(updateCallDetailsHandler, new UpdateCallDetailsCommand(
                    commandId,
                    new UserId(claims.subject()),
                    extractIpAddress(ctx),
                    callId,
                    body.callerName() != null ? new CallerName(body.callerName()) : null,
                    body.callerPhoneNumber() != null ? new PhoneNumber(body.callerPhoneNumber()) : null,
                    body.location() != null ? body.location().toDomain() : null,
                    body.description() != null ? new Description(body.description()) : null
            ));
        }

        if (hasOutcomeFields) {
            var outcome = CallDtos.parseOutcome(body.outcome());
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

    private void handleEndCall(Context ctx) {
        requireRole(ctx, Role.DISPATCHER);
        var commandId = CommandIdExtractor.extract(ctx);
        var claims = AuthContext.requireClaims(ctx);
        var callId = new CallId(ctx.pathParam("callId"));
        var body = ctx.bodyAsClass(CallDtos.EndCallRequest.class);

        // If outcome is supplied in body, set it before ending
        if (body.outcome() != null) {
            var outcome = CallDtos.parseOutcome(body.outcome());
            dispatcher.dispatch(setCallOutcomeHandler, new SetCallOutcomeCommand(
                    CommandIdExtractor.derive(commandId, "pre-end-set-outcome"),
                    new UserId(claims.subject()),
                    extractIpAddress(ctx),
                    callId,
                    outcome,
                    body.outcomeRationale() != null ? new Description(body.outcomeRationale()) : null
            ));
        }

        // Validate that outcome is now set (gives 400 instead of 409 for missing outcome)
        var call = callRepository.findById(callId)
                .orElseThrow(() -> new NoSuchElementException("call not found: " + callId));
        if (call.state() == CallState.ENDED) {
            throw new IllegalStateException("call is already ENDED: " + callId);
        }
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

    private void handleAttachToIncident(Context ctx) {
        requireRole(ctx, Role.DISPATCHER);
        var commandId = CommandIdExtractor.extract(ctx);
        var claims = AuthContext.requireClaims(ctx);
        var callId = new CallId(ctx.pathParam("callId"));
        var body = ctx.bodyAsClass(CallDtos.AttachCallToIncidentRequest.class);

        dispatcher.dispatch(attachCallToIncidentHandler, new AttachCallToIncidentCommand(
                commandId,
                new UserId(claims.subject()),
                extractIpAddress(ctx),
                callId,
                new IncidentId(body.incidentId())
        ));
        ctx.status(200);
    }

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

    private void handleListCalls(Context ctx) {
        requireAnyRole(ctx, Role.DISPATCHER, Role.OBSERVER);
        var calls = callRepository.findActive()
                .map(CallDtos::fromDomain)
                .toList();
        ctx.json(new CallDtos.CallListResponse(calls));
    }

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
