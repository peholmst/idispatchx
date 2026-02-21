package net.pkhapps.idispatchx.common.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class TokenValidationExceptionTest {

    @ParameterizedTest
    @EnumSource(TokenValidationException.ErrorCode.class)
    void constructorWithMessage_preservesErrorCodeAndMessage(TokenValidationException.ErrorCode errorCode) {
        var exception = new TokenValidationException(errorCode, "Test message");

        assertEquals(errorCode, exception.getErrorCode());
        assertEquals("Test message", exception.getMessage());
        assertNull(exception.getCause());
    }

    @ParameterizedTest
    @EnumSource(TokenValidationException.ErrorCode.class)
    void constructorWithCause_preservesAllFields(TokenValidationException.ErrorCode errorCode) {
        var cause = new RuntimeException("Underlying error");
        var exception = new TokenValidationException(errorCode, "Test message", cause);

        assertEquals(errorCode, exception.getErrorCode());
        assertEquals("Test message", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void allErrorCodesExist() {
        var errorCodes = TokenValidationException.ErrorCode.values();

        assertTrue(contains(errorCodes, TokenValidationException.ErrorCode.INVALID_TOKEN));
        assertTrue(contains(errorCodes, TokenValidationException.ErrorCode.INVALID_SIGNATURE));
        assertTrue(contains(errorCodes, TokenValidationException.ErrorCode.TOKEN_EXPIRED));
        assertTrue(contains(errorCodes, TokenValidationException.ErrorCode.INVALID_ISSUER));
        assertTrue(contains(errorCodes, TokenValidationException.ErrorCode.MISSING_CLAIM));
        assertTrue(contains(errorCodes, TokenValidationException.ErrorCode.KEY_NOT_FOUND));
    }

    private boolean contains(TokenValidationException.ErrorCode[] codes, TokenValidationException.ErrorCode code) {
        for (var c : codes) {
            if (c == code) return true;
        }
        return false;
    }

    @Test
    void isRuntimeException() {
        var exception = new TokenValidationException(
                TokenValidationException.ErrorCode.INVALID_TOKEN, "Test");
        assertTrue(exception instanceof RuntimeException);
    }
}
