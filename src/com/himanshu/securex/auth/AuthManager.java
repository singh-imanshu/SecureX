package com.himanshu.securex.auth;

import com.himanshu.securex.util.HashUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Set;

public class AuthManager {
    private static final Path APP_DIR = Paths.get(System.getProperty("user.home"), ".securex");
    private static final Path MASTER_FILE_PATH = APP_DIR.resolve("master.dat");

    public AuthManager() {
        if (!Files.exists(APP_DIR)) {
            try {
                Files.createDirectories(APP_DIR);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("Could not create application data directory.");
            }
        }
    }

    public boolean masterPasswordExists() {
        return Files.exists(MASTER_FILE_PATH);
    }

    /**
     * Saves the master password hash to a secure file with restricted permissions.
     * @param password The master password to save.
     * @return true if saving was successful, false otherwise.
     */
    public boolean saveMasterPassword(char[] password) {
        String hashed = HashUtil.hashPassword(password);
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                Files.writeString(MASTER_FILE_PATH, hashed);
            } else {
                // For POSIX-compliant systems (Linux, macOS)
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
                Files.writeString(MASTER_FILE_PATH, hashed, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                Files.setPosixFilePermissions(MASTER_FILE_PATH, perms);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Verifies the provided master password against the stored hash.
     * @param password The master password to verify.
     * @return true if the password is correct, false otherwise.
     */
    public boolean verifyPassword(char[] password) {
        if (!masterPasswordExists()) {
            return false;
        }
        try {
            String storedHash = Files.readString(MASTER_FILE_PATH).trim();
            return HashUtil.verifyPassword(password, storedHash);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}