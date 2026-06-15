package net.pkhapps.idispatchx.cad.adapter.primary.rest.shared;

/**
 * CAD Server specific error codes used in {@link net.pkhapps.idispatchx.common.api.ErrorResponse}.
 */
public final class CadErrorCode {

    private CadErrorCode() {}

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String INVARIANT_VIOLATION = "INVARIANT_VIOLATION";
    public static final String NOT_IMPLEMENTED = "NOT_IMPLEMENTED";
}
