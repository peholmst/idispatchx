package net.pkhapps.idispatchx.cad.domain.model.incident;

import java.util.Objects;

/**
 * The type code of an {@link Incident}.
 * <p>
 * Wraps a non-null, non-empty string code identifying the incident type.
 *
 * @param code the incident type code
 */
public record IncidentType(String code) {

    public IncidentType {
        Objects.requireNonNull(code, "code must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }

    @Override
    public String toString() {
        return code;
    }
}
