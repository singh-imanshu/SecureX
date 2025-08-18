package com.himanshu.securex.util;

import java.security.SecureRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A utility class for generating strong, random passwords.
 */
public class PasswordGenerator {

    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()_+-=[]|,./?><";
    private static final String ALL_CHARS = LOWER + UPPER + DIGITS + SYMBOLS;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a random password of a specified length.
     *
     * @param length The desired length of the password.
     * @return A randomly generated password.
     */
    public static String generatePassword(int length) {
        if (length < 12) {
            throw new IllegalArgumentException("Password length must be at least 12 characters for adequate security.");
        }

        // Use a stream to generate a sequence of random character indices and map them to characters.
        return IntStream.range(0, length)
                .map(i -> RANDOM.nextInt(ALL_CHARS.length()))
                .mapToObj(ALL_CHARS::charAt)
                .map(Object::toString)
                .collect(Collectors.joining());
    }
}