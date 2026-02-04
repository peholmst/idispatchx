package net.pkhapps.idispatchx.cad.domain.model.unit;

import java.util.Objects;
import java.util.Set;

/**
 * A value object representing a Finnish rescue services unit identification code
 * according to Ministry of the Interior guidelines (SM 2021:35).
 * <p>
 * Callsigns are used for unit identification in emergency response, communications,
 * situation awareness, and statistical reporting.
 * <p>
 * This class performs syntactic validation only. It does not validate semantic
 * correctness such as whether organization codes correspond to existing entities.
 *
 * @param value the callsign string in canonical (uppercase) form
 */
public record Callsign(String value) {

    private static final Set<Character> VALID_SECTOR_CODES =
            Set.of('R', 'P', 'E', 'B', 'S', 'M', 'C', 'V', 'A', 'I', 'K');

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 16;

    public Callsign {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        value = value.toUpperCase();
        validate(value);
    }

    /**
     * Creates a new Callsign from the given string value.
     *
     * @param value the callsign string (will be normalized to uppercase)
     * @return the Callsign instance
     * @throws IllegalArgumentException if the value is not a valid callsign
     */
    public static Callsign of(String value) {
        return new Callsign(value);
    }

    private static void validate(String value) {
        if (value.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "callsign must be at least " + MIN_LENGTH + " characters: " + value);
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "callsign must be at most " + MAX_LENGTH + " characters: " + value);
        }

        char sectorCode = value.charAt(0);
        if (!VALID_SECTOR_CODES.contains(sectorCode)) {
            throw new IllegalArgumentException(
                    "invalid sector code '" + sectorCode + "': " + value);
        }

        validateCharacters(value);
        validateUnderscoreRules(value, sectorCode);
    }

    private static void validateCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!isValidCharacter(c)) {
                throw new IllegalArgumentException(
                        "invalid character '" + c + "' at position " + (i + 1) + ": " + value);
            }
        }
    }

    private static boolean isValidCharacter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private static void validateUnderscoreRules(String value, char sectorCode) {
        int firstUnderscore = value.indexOf('_');
        if (firstUnderscore == -1) {
            return;
        }

        int secondUnderscore = value.indexOf('_', firstUnderscore + 1);
        if (secondUnderscore != -1) {
            throw new IllegalArgumentException(
                    "callsign may contain at most one underscore: " + value);
        }

        int minUnderscorePosition = (sectorCode == 'K') ? 4 : 3;
        if (firstUnderscore < minUnderscorePosition) {
            throw new IllegalArgumentException(
                    "underscore must appear after position " + minUnderscorePosition + ": " + value);
        }

        for (int i = firstUnderscore + 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '_') {
                throw new IllegalArgumentException(
                        "characters after underscore must be alphanumeric only: " + value);
            }
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
