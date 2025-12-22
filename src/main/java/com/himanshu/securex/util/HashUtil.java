package com.himanshu.securex.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Arrays;

/**
 * Utility class for password hashing and verification using PBKDF2.
 */
public class HashUtil {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_SIZE = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_SIZE];
        RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * Hashes a password using PBKDF2. The salt is generated randomly and stored with the hash.
     *
     * @param password The plaintext password to hash.
     * @return A Base64 encoded string containing the salt and the hash.
     */
    public static String hashPassword(char[] password) {
        try {
            byte[] salt = generateSalt();
            KeySpec spec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hashedPassword = factory.generateSecret(spec).getEncoded();

            byte[] hashWithSalt = new byte[SALT_SIZE + hashedPassword.length];
            System.arraycopy(salt, 0, hashWithSalt, 0, SALT_SIZE);
            System.arraycopy(hashedPassword, 0, hashWithSalt, SALT_SIZE, hashedPassword.length);

            return Base64.getEncoder().encodeToString(hashWithSalt);

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    public static boolean verifyPassword(char[] password, String storedHashBase64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(storedHashBase64);
            byte[] salt = new byte[SALT_SIZE];
            System.arraycopy(decoded, 0, salt, 0, SALT_SIZE);
            byte[] storedHash = new byte[decoded.length - SALT_SIZE];
            System.arraycopy(decoded, SALT_SIZE, storedHash, 0, storedHash.length);

            KeySpec spec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] computedHash = factory.generateSecret(spec).getEncoded();

            boolean equal = Arrays.equals(storedHash, computedHash);
            Arrays.fill(password, '\0');
            return equal;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            Arrays.fill(password, '\0');
            throw new RuntimeException("Failed to verify password", ex);
        }
    }

    /**
     * Extracts the salt bytes from a previously-produced Base64 hash string (salt+hash).
     *
     * @param storedHashBase64 Base64 string produced by hashPassword
     * @return salt bytes
     */
    public static byte[] extractSaltFromHash(String storedHashBase64) {
        byte[] decoded = Base64.getDecoder().decode(storedHashBase64);
        byte[] salt = new byte[SALT_SIZE];
        System.arraycopy(decoded, 0, salt, 0, SALT_SIZE);
        return salt;
    }
}