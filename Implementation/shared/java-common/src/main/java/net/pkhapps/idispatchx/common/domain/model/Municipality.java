package net.pkhapps.idispatchx.common.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * A value object representing a Finnish municipality, consisting of an optional municipality code
 * and a multilingual name.
 * <p>
 * In normal operation, municipalities have both a code (from Statistics Finland) and a name.
 * In degraded mode (when reference data is unavailable), a municipality may have only a name
 * without a code.
 * <p>
 * The name must never be empty — a municipality must always have at least one language version
 * of its name.
 *
 * @param code the municipality code, or null in degraded mode
 * @param name the multilingual name (must not be null or empty)
 */
public record Municipality(@Nullable MunicipalityCode code, MultilingualName name) {

    /**
     * Compact constructor that validates the municipality.
     *
     * @param code the municipality code, or null in degraded mode
     * @param name the multilingual name
     * @throws NullPointerException     if name is null
     * @throws IllegalArgumentException if name is empty
     */
    public Municipality {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
    }

    /**
     * Creates a Municipality from the given code and name.
     *
     * @param code the municipality code, or null in degraded mode
     * @param name the multilingual name
     * @return the Municipality instance
     * @throws NullPointerException     if name is null
     * @throws IllegalArgumentException if name is empty
     */
    @JsonCreator
    public static Municipality of(@JsonProperty("code") @Nullable MunicipalityCode code,
                                  @JsonProperty("name") MultilingualName name) {
        return new Municipality(code, name);
    }

    /**
     * Creates a Municipality without a code, for degraded mode when reference data is unavailable.
     *
     * @param name the multilingual name
     * @return the Municipality instance without a code
     * @throws NullPointerException     if name is null
     * @throws IllegalArgumentException if name is empty
     */
    public static Municipality withoutCode(MultilingualName name) {
        return new Municipality(null, name);
    }

    /**
     * Returns true if this municipality has a code.
     *
     * @return true if the code is present
     */
    public boolean hasCode() {
        return code != null;
    }

    /**
     * Returns the municipality code as an Optional.
     *
     * @return the code, or empty if not present
     */
    public Optional<MunicipalityCode> optionalCode() {
        return Optional.ofNullable(code);
    }

    @Override
    public String toString() {
        if (code != null) {
            return code + " " + name;
        }
        return name.toString();
    }
}
