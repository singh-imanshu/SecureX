package com.himanshu.securex.auth;

import com.himanshu.securex.model.PasswordEntry;
import com.himanshu.securex.services.CryptoService;
import com.himanshu.securex.services.StorageService;
import com.himanshu.securex.util.HashUtil;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.stream.Collectors;

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

    public boolean saveMasterPassword(char[] password) {
        String hashed = HashUtil.hashPassword(password);
        try {
            writeMasterFileAtomic(hashed);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

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

    public byte[] getSalt() {
        try {
            if (!masterPasswordExists()) return null;
            String storedHash = Files.readString(MASTER_FILE_PATH).trim();
            return HashUtil.extractSaltFromHash(storedHash);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Backwards-compatible entry point
    public boolean changeMasterPassword(char[] oldPassword, char[] newPassword, StorageService currentStorage) {
        return changeMasterPassword(oldPassword, newPassword, currentStorage, null);
    }

    /**
     * Atomic password change:
     * - Use provided plaintext entries if available (preferred).
     * - Otherwise, load current vault with the old key; if that fails, try to load from backups.
     * - Save entries with new key (atomically replaces the vault and backs up old one).
     * - Only after that succeeds, atomically update master.dat.
     * - Finally, delete all backups as per policy when changing the master password.
     */
    public boolean changeMasterPassword(char[] oldPassword,
                                        char[] newPassword,
                                        StorageService currentStorage,
                                        List<PasswordEntry> currentPlainEntries) {
        try {
            // 1) Verify old password first
            if (!verifyPassword(Arrays.copyOf(oldPassword, oldPassword.length))) {
                return false;
            }

            // 2) Derive old and new crypto keys (pass copies so CryptoService can zero inputs safely)
            byte[] oldSalt = getSalt();
            if (oldSalt == null) return false;

            CryptoService oldCrypto = new CryptoService(Arrays.copyOf(oldPassword, oldPassword.length), oldSalt);

            String newHashed = HashUtil.hashPassword(newPassword);
            byte[] newSalt = HashUtil.extractSaltFromHash(newHashed);
            CryptoService newCrypto = new CryptoService(Arrays.copyOf(newPassword, newPassword.length), newSalt);

            // 3) Gather plaintext entries safely
            List<PasswordEntry> entries;
            if (currentPlainEntries != null) {
                entries = deepCopyEntries(currentPlainEntries);
            } else {
                // Try loading current vault with the old key
                try {
                    StorageService oldStorage = new StorageService(oldCrypto);
                    entries = oldStorage.load();
                } catch (Exception loadEx) {
                    // Fallback: try newest backups and attempt to decrypt with old key
                    try {
                        entries = tryLoadFromBackups(currentStorage, oldCrypto);
                    } catch (Exception backupEx) {
                        loadEx.printStackTrace();
                        backupEx.printStackTrace();
                        return false;
                    }
                }
            }

            // 4) Save entries using the new key (atomically replaces vault and backs up existing one)
            StorageService newStorage = new StorageService(newCrypto);
            newStorage.save(entries);

            // 5) Only now swap master.dat to the new hash atomically
            writeMasterFileAtomic(newHashed);

            // 6) Delete all backups after a successful password change
            deleteAllBackupsQuietly();

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            Arrays.fill(oldPassword, '\0');
            Arrays.fill(newPassword, '\0');
        }
    }

    private List<PasswordEntry> deepCopyEntries(List<PasswordEntry> source) {
        List<PasswordEntry> copy = new ArrayList<>(source.size());
        for (PasswordEntry e : source) {
            char[] pwd = e.getPassword() != null ? Arrays.copyOf(e.getPassword(), e.getPassword().length) : new char[0];
            copy.add(new PasswordEntry(e.getAccount(), e.getUsername(), pwd));
            Arrays.fill(pwd, '\0'); // new entry will copy internally; clear our temp
        }
        return copy;
    }

    private List<PasswordEntry> tryLoadFromBackups(StorageService storageService, CryptoService oldCrypto) throws Exception {
        List<Path> backups = storageService.getBackupFiles();
        if (backups == null || backups.isEmpty()) {
            throw new IOException("No backups found to attempt decryption.");
        }

        // Newest first based on filename (timestamp included)
        List<Path> sorted = backups.stream()
                .filter(Files::isRegularFile)
                .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                .collect(Collectors.toList());

        for (Path p : sorted) {
            try {
                String encrypted = Files.readString(p);
                if (encrypted == null || encrypted.isEmpty()) continue;
                String json = oldCrypto.decrypt(encrypted);
                java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<ArrayList<PasswordEntry>>() {}.getType();
                List<PasswordEntry> entries = new com.google.gson.Gson().fromJson(json, type);
                if (entries != null) return entries;
            } catch (Exception ignore) {
                // try next backup
            }
        }
        throw new IOException("Failed to decrypt any backup with the current key.");
    }

    private void writeMasterFileAtomic(String content) throws IOException {
        Path temp = APP_DIR.resolve("master.tmp");
        Files.writeString(temp, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        try {
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(temp, perms);
            }
        } catch (UnsupportedOperationException ignored) {}

        try {
            Files.move(temp, MASTER_FILE_PATH, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicEx) {
            Files.move(temp, MASTER_FILE_PATH, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Deletes the entire backups directory and its files.
     */
    private void deleteAllBackupsQuietly() {
        Path backupsDir = APP_DIR.resolve("backups");
        try {
            if (Files.exists(backupsDir)) {
                try (java.util.stream.Stream<Path> s = Files.list(backupsDir)) {
                    for (Path p : s.collect(Collectors.toList())) {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {}
                    }
                }
                try {
                    Files.deleteIfExists(backupsDir);
                } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {
        }
    }
}