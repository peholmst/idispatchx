package net.pkhapps.idispatchx.cad.domain.model.shared;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A free-text description with a maximum length of 1000 UTF-8 characters.
 * <p>
 * Used for call descriptions, outcome rationales, incident descriptions, and incident log entry text.
 *
 * @param value the description text
 */
public record Description(String value) {

    private static final int MAX_UTF8_CHARS = 1000;

    public Description {
        Objects.requireNonNull(value, "value must not be null");
        if (value.codePointCount(0, value.length()) > MAX_UTF8_CHARS) {
            throw new IllegalArgumentException(
                    "Description must not exceed " + MAX_UTF8_CHARS + " UTF-8 characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
