package net.pkhapps.idispatchx.cad.domain.model.shared;

import java.security.SecureRandom;

/**
 * Generates Nano ID strings: 21 URL-safe characters from the alphabet [A-Za-z0-9_-].
 */
public final class NanoIdGenerator {

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";
    private static final int ID_LENGTH = 21;
    private static final SecureRandom RANDOM = new SecureRandom();

    private NanoIdGenerator() {
    }

    public static String generate() {
        var chars = new char[ID_LENGTH];
        for (int i = 0; i < ID_LENGTH; i++) {
            chars[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
        }
        return new String(chars);
    }
}
