package com.himanshu.securex.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

public class HashUtil {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_SIZE = 16;

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
            throw new RuntimeException("Critical hashing error", e);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Verifies a plaintext password against a stored PBKDF2 hash using a constant-time comparison.
     *
     * @param password   The plaintext password to verify.
     * @param storedHash The Base64 encoded string of the salt and hash from storage.
     * @return True if the password is correct, false otherwise.
     */
    public static boolean verifyPassword(char[] password, String storedHash) {
        try {
            byte[] hashWithSalt = Base64.getDecoder().decode(storedHash);
            byte[] salt = Arrays.copyOfRange(hashWithSalt, 0, SALT_SIZE);
            byte[] originalHash = Arrays.copyOfRange(hashWithSalt, SALT_SIZE, hashWithSalt.length);

            KeySpec spec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] testHash = factory.generateSecret(spec).getEncoded();

            return MessageDigest.isEqual(originalHash, testHash);

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            return false;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Generates a cryptographically secure random salt.
     */
    private static byte[] generateSalt() {
        byte[] salt = new byte[SALT_SIZE];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /**
     * Extracts the salt from a Base64 encoded hash string.
     * @param storedHash The stored hash string.
     * @return The salt.
     */
    public static byte[] extractSalt(String storedHash) {
        byte[] hashWithSalt = Base64.getDecoder().decode(storedHash);
        return Arrays.copyOfRange(hashWithSalt, 0, SALT_SIZE);
    }
}