package net.pkhapps.idispatchx.gis.server.api.error;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;
import net.pkhapps.idispatchx.common.api.CommonErrorCode;
import net.pkhapps.idispatchx.common.api.ErrorResponse;
import net.pkhapps.idispatchx.common.api.ValidationException;
import net.pkhapps.idispatchx.gis.server.service.geocode.DatabaseUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private Context ctx;

    @BeforeEach
    void setUp() {
        when(ctx.path()).thenReturn("/test");
    }

    // ==================== ValidationException ====================

    @Test
    void handleValidationException_returns400WithErrorCode() throws Exception {
        var exception = new ValidationException("INVALID_QUERY", "Query too short");
        invokeHandler(ValidationException.class, exception);

        verify(ctx).status(400);
        var response = captureJsonResponse();
        assertEquals("INVALID_QUERY", response.error().code());
        assertEquals("Query too short", response.error().message());
        assertNull(response.error().details());
    }

    @Test
    void handleValidationException_withDetails_includesDetails() throws Exception {
        var details = Map.<String, Object>of("minLength", 3);
        var exception = new ValidationException("INVALID_QUERY", "Query too short", details);
        invokeHandler(ValidationException.class, exception);

        verify(ctx).status(400);
        var response = captureJsonResponse();
        assertEquals("INVALID_QUERY", response.error().code());
        assertNotNull(response.error().details());
        assertEquals(3, response.error().details().get("minLength"));
    }

    // ==================== BadRequestResponse ====================

    @Test
    void handleBadRequest_returns400() throws Exception {
        invokeHandler(BadRequestResponse.class, new BadRequestResponse("Invalid input"));

        verify(ctx).status(400);
        var response = captureJsonResponse();
        assertEquals(GisErrorCode.INVALID_PARAMETER, response.error().code());
        assertEquals("Invalid input", response.error().message());
    }

    // ==================== UnauthorizedResponse ====================

    @Test
    void handleUnauthorized_returns401() throws Exception {
        invokeHandler(UnauthorizedResponse.class, new UnauthorizedResponse("Missing token"));

        verify(ctx).status(401);
        var response = captureJsonResponse();
        assertEquals(CommonErrorCode.UNAUTHORIZED, response.error().code());
        assertEquals("Missing token", response.error().message());
    }

    // ==================== ForbiddenResponse ====================

    @Test
    void handleForbidden_returns403() throws Exception {
        invokeHandler(ForbiddenResponse.class, new ForbiddenResponse("Insufficient permissions"));

        verify(ctx).status(403);
        var response = captureJsonResponse();
        assertEquals(CommonErrorCode.FORBIDDEN, response.error().code());
        assertEquals("Insufficient permissions", response.error().message());
    }

    // ==================== NotFoundResponse ====================

    @Test
    void handleNotFound_returns404() throws Exception {
        invokeHandler(NotFoundResponse.class, new NotFoundResponse("Unknown layer: foo"));

        verify(ctx).status(404);
        var response = captureJsonResponse();
        assertEquals(GisErrorCode.LAYER_NOT_FOUND, response.error().code());
        assertEquals("Unknown layer: foo", response.error().message());
    }

    // ==================== DatabaseUnavailableException ====================

    @Test
    void handleDatabaseUnavailable_returns503() throws Exception {
        invokeHandler(DatabaseUnavailableException.class,
                new DatabaseUnavailableException("Connection refused", new RuntimeException()));

        verify(ctx).status(503);
        var response = captureJsonResponse();
        assertEquals(CommonErrorCode.DATABASE_ERROR, response.error().code());
        assertEquals("Database service unavailable", response.error().message());
    }

    // ==================== Unexpected exceptions ====================

    @Test
    void handleUnexpected_returns500() throws Exception {
        invokeHandler(Exception.class, new RuntimeException("Something went wrong"));

        verify(ctx).status(500);
        var response = captureJsonResponse();
        assertEquals(CommonErrorCode.INTERNAL_ERROR, response.error().code());
        assertEquals("An unexpected error occurred", response.error().message());
    }

    // ==================== Response includes path ====================

    @Test
    void allResponses_includeRequestPath() throws Exception {
        when(ctx.path()).thenReturn("/api/v1/geocode/search");

        invokeHandler(BadRequestResponse.class, new BadRequestResponse("test"));

        var response = captureJsonResponse();
        assertEquals("/api/v1/geocode/search", response.path());
    }

    // ==================== helpers ====================

    @SuppressWarnings("unchecked")
    private <T extends Exception> void invokeHandler(Class<T> exceptionType, T exception) throws Exception {
        // Use reflection to invoke the private handler methods
        var methodName = switch (exceptionType.getSimpleName()) {
            case "ValidationException" -> "handleValidationException";
            case "BadRequestResponse" -> "handleBadRequest";
            case "UnauthorizedResponse" -> "handleUnauthorized";
            case "ForbiddenResponse" -> "handleForbidden";
            case "NotFoundResponse" -> "handleNotFound";
            case "DatabaseUnavailableException" -> "handleDatabaseUnavailable";
            default -> "handleUnexpected";
        };

        var method = GlobalExceptionHandler.class.getDeclaredMethod(methodName, exceptionType, Context.class);
        method.setAccessible(true);
        method.invoke(null, exception, ctx);
    }

    private ErrorResponse captureJsonResponse() {
        var captor = ArgumentCaptor.forClass(ErrorResponse.class);
        verify(ctx).json(captor.capture());
        return captor.getValue();
    }
}
