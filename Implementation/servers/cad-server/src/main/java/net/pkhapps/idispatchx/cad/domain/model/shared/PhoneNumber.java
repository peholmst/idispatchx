package net.pkhapps.idispatchx.cad.domain.model.shared;

import java.util.Objects;

/**
 * A phone number stored in E.164 format.
 * <p>
 * Accepts numbers with or without a leading {@code +}. Numbers without {@code +} are assumed
 * to be domestic. Only digits (0–9) are allowed after the optional {@code +}, and the total
 * number of digits must not exceed 15.
 *
 * @param value the phone number string in E.164 format
 */
public record PhoneNumber(String value) {

    private static final int MAX_DIGITS = 15;

    public PhoneNumber {
        Objects.requireNonNull(value, "value must not be null");
        validate(value);
    }

    private static void validate(String value) {
        int start = 0;
        if (!value.isEmpty() && value.charAt(0) == '+') {
            start = 1;
        }
        if (start >= value.length()) {
            throw new IllegalArgumentException("PhoneNumber must contain at least one digit: " + value);
        }
        int digitCount = 0;
        for (int i = start; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException(
                        "PhoneNumber contains non-digit character at position " + i + ": " + value);
            }
            digitCount++;
        }
        if (digitCount > MAX_DIGITS) {
            throw new IllegalArgumentException(
                    "PhoneNumber must not exceed " + MAX_DIGITS + " digits, got: " + digitCount);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
