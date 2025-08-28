package com.himanshu.securex.util;

import java.security.SecureRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A utility class for generating strong, random passwords.
 * TODO: add an entropy checker function and re-generate password if criteria not met
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
     * @return A randomly generated password as a char array.
     */
    public static char[] generatePassword(int length) {
        if (length < 12) {
            throw new IllegalArgumentException("Password length must be at least 12 characters for adequate security.");
        }

        char[] password = new char[length];
        for (int i = 0; i < length; i++) {
            password[i] = ALL_CHARS.charAt(RANDOM.nextInt(ALL_CHARS.length()));
        }
        return password;
    }
}