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


    //we overload changeMasterPassword()

    public boolean changeMasterPassword(char[] oldPassword, char[] newPassword, StorageService currentStorage) {
        return changeMasterPassword(oldPassword, newPassword, currentStorage, null);
    }

    public boolean changeMasterPassword(char[] oldPassword,
                                        char[] newPassword,
                                        StorageService currentStorage,
                                        List<PasswordEntry> currentPlainEntries) {
        try {
            if (!verifyPassword(Arrays.copyOf(oldPassword, oldPassword.length))) {
                return false;
            }

            byte[] oldSalt = getSalt();
            if (oldSalt == null) return false;

            CryptoService oldCrypto = new CryptoService(Arrays.copyOf(oldPassword, oldPassword.length), oldSalt);

            String newHashed = HashUtil.hashPassword(newPassword);
            byte[] newSalt = HashUtil.extractSaltFromHash(newHashed);
            CryptoService newCrypto = new CryptoService(Arrays.copyOf(newPassword, newPassword.length), newSalt);

            List<PasswordEntry> entries;
            if (currentPlainEntries != null) {
                entries = deepCopyEntries(currentPlainEntries);
            } else {
                try {
                    StorageService oldStorage = new StorageService(oldCrypto);
                    entries = oldStorage.load();
                } catch (Exception loadEx) {
                    entries = tryLoadFromBackups(currentStorage, oldCrypto);
                }
            }

            // 1. Explicitly backup the CURRENT vault using the OLD key (currentStorage).
            if (currentStorage != null) {
                currentStorage.backupCurrentVault();
            }

            // 2. Save entries using the NEW key service WITHOUT trying to backup again.
            StorageService newStorage = new StorageService(newCrypto);
            newStorage.saveWithoutBackup(entries);

            // 5) Swap master.dat
            writeMasterFileAtomic(newHashed);

            // 6) Re-encrypt old backups so they are accessible with the new password
            currentStorage.reencryptAllBackups(oldCrypto, newCrypto);

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
            Arrays.fill(pwd, '\0');
        }
        return copy;
    }

    private List<PasswordEntry> tryLoadFromBackups(StorageService storageService, CryptoService oldCrypto) throws Exception {
        List<Path> backups = storageService.getBackupFiles();
        if (backups == null || backups.isEmpty()) {
            throw new IOException("No backups found to attempt decryption.");
        }

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
            } catch (Exception ignore) {}
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
}