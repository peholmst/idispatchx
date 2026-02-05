package net.pkhapps.idispatchx.common.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A value object representing a Finnish municipality code.
 * <p>
 * Municipality codes are exactly 3 digits, as defined by Statistics Finland.
 * Leading zeros are significant (e.g., "091" for Helsinki).
 *
 * @param code the 3-digit municipality code
 */
public record MunicipalityCode(String code) {

    private static final Pattern MUNICIPALITY_CODE_PATTERN = Pattern.compile("^[0-9]{3}$");

    /**
     * Compact constructor that validates the municipality code.
     *
     * @param code the municipality code
     * @throws NullPointerException     if code is null
     * @throws IllegalArgumentException if code is not exactly 3 digits
     */
    public MunicipalityCode {
        Objects.requireNonNull(code, "code must not be null");
        if (!MUNICIPALITY_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "invalid municipality code '" + code + "': must be exactly 3 digits");
        }
    }

    /**
     * Creates a MunicipalityCode from the given code string.
     *
     * @param code the municipality code
     * @return the MunicipalityCode instance
     * @throws NullPointerException     if code is null
     * @throws IllegalArgumentException if code is not exactly 3 digits
     */
    @JsonCreator
    public static MunicipalityCode of(String code) {
        return new MunicipalityCode(code);
    }

    /**
     * Returns the municipality code string. Used by Jackson for serialization.
     *
     * @return the municipality code
     */
    @JsonValue
    @Override
    public String code() {
        return code;
    }

    @Override
    public String toString() {
        return code;
    }
}
