package com.himanshu.securex.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.himanshu.securex.model.PasswordEntry;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages the persistence of the encrypted password vault to the user's local filesystem.
 *
 * This service is responsible for both loading the vault from disk and saving it securely.
 * It incorporates robust data safety features:
 *
 * 1. Automatic Encrypted Backups:
 * Before any save operation, the existing vault file is copied to a dedicated backups
 * directory with a timestamp. Metadata (entry count) is embedded in the filename
 * for fast inspection.
 *
 * 2. Atomic Save Operations:
 * To prevent data loss, all new data is first written to a temporary file.
 * Only after the writing operation is fully successful is the temporary file
 * atomically moved to replace the main vault file.
 *
 * 3. Rotating Restore Points:
 * When restoring a backup, the current state is saved to a rotating set of
 * "vault-before-restore" files, ensuring data is never lost even during
 * multiple panicked restore attempts.
 */
public class StorageService {
    private static final Path APP_DIR = Paths.get(System.getProperty("user.home"), ".securex");
    private static final Path VAULT_FILE = APP_DIR.resolve("vault.dat");
    private static final Path TEMP_FILE = APP_DIR.resolve("vault.tmp");
    private static final Path BACKUPS_DIR = APP_DIR.resolve("backups");

    private static final int MAX_REGULAR_BACKUPS = 5;
    private static final int MAX_RESTORE_POINTS = 3;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    // Regex to extract count from filename: matches ending with _(\d+).dat
    private static final Pattern COUNT_PATTERN = Pattern.compile("_(\\d+)\\.dat$");

    private final CryptoService cryptoService;
    private final Gson gson;

    public StorageService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void save(List<PasswordEntry> entries) throws Exception {
        backupCurrentVault();
        saveWithoutBackup(entries);
    }

    /**
     * Saves the entries to disk WITHOUT creating a backup of the existing file first.
     */
    public void saveWithoutBackup(List<PasswordEntry> entries) throws Exception {
        String json = gson.toJson(entries);
        String encryptedData = cryptoService.encrypt(json);

        Files.writeString(TEMP_FILE, encryptedData);
        try {
            Files.move(TEMP_FILE, VAULT_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(TEMP_FILE, VAULT_FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Creates a timestamped backup of the current vault.dat.
     */
    public void backupCurrentVault() {
        if (Files.exists(VAULT_FILE)) {
            try {
                Files.createDirectories(BACKUPS_DIR);

                // Read the current vault to verify integrity and get count
                String encryptedContent = Files.readString(VAULT_FILE);
                int count = -1;
                try {
                    count = countEntriesInEncryptedString(encryptedContent);
                } catch (Exception e) {
                    System.err.println("Warning: Could not verify vault integrity during backup. Marking as unknown.");
                }

                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
                String countSuffix = (count >= 0) ? "_" + count : "_error";

                Path backupFile = BACKUPS_DIR.resolve("vault-" + timestamp + countSuffix + ".dat");

                Files.writeString(backupFile, encryptedContent);

                pruneBackups();

            } catch (IOException e) {
                System.err.println("Warning: Failed to create backup before save: " + e.getMessage());
            }
        }
    }

    public void restoreFromBackup(Path backupFile) throws IOException {
        if (Files.exists(VAULT_FILE)) {
            // Create a safe restore point of the data we are about to overwrite
            String currentContent = Files.readString(VAULT_FILE);
            int count = -1;
            try {
                count = countEntriesInEncryptedString(currentContent);
            } catch (Exception ignore) {}

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
            String countSuffix = (count >= 0) ? "_" + count : "_unknown";

            Path preRestoreBackup = BACKUPS_DIR.resolve("vault-before-restore-" + timestamp + countSuffix + ".dat");
            Files.writeString(preRestoreBackup, currentContent);
        }

        Files.copy(backupFile, VAULT_FILE, StandardCopyOption.REPLACE_EXISTING);
        pruneBackups();
    }

    /**
     * Fast method to get entry count purely from filename metadata.
     * Returns -1 if the filename does not contain a count (legacy file).
     */
    public int getEntryCountFast(Path backupFile) {
        String filename = backupFile.getFileName().toString();

        Matcher matcher = COUNT_PATTERN.matcher(filename);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }

        // No legacy decryption fallback
        return -1;
    }

    private int countEntriesInEncryptedString(String encryptedJson) throws Exception {
        if (encryptedJson == null || encryptedJson.isEmpty()) return 0;
        String json = cryptoService.decrypt(encryptedJson);
        Type type = new TypeToken<ArrayList<PasswordEntry>>() {}.getType();
        List<PasswordEntry> entries = gson.fromJson(json, type);
        return entries != null ? entries.size() : 0;
    }

    private void pruneBackups() throws IOException {
        if (!Files.exists(BACKUPS_DIR)) return;

        try (Stream<Path> stream = Files.list(BACKUPS_DIR)) {
            List<Path> allFiles = stream
                    .filter(Files::isRegularFile)
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .collect(Collectors.toList());

            List<Path> regularBackups = new ArrayList<>();
            List<Path> restorePoints = new ArrayList<>();

            for (Path p : allFiles) {
                String name = p.getFileName().toString();
                if (name.startsWith("vault-before-restore-")) {
                    restorePoints.add(p);
                } else if (name.startsWith("vault-") && name.endsWith(".dat")) {
                    regularBackups.add(p);
                }
            }

            if (regularBackups.size() > MAX_REGULAR_BACKUPS) {
                for (int i = MAX_REGULAR_BACKUPS; i < regularBackups.size(); i++) {
                    Files.deleteIfExists(regularBackups.get(i));
                }
            }
            if (restorePoints.size() > MAX_RESTORE_POINTS) {
                for (int i = MAX_RESTORE_POINTS; i < restorePoints.size(); i++) {
                    Files.deleteIfExists(restorePoints.get(i));
                }
            }
        }
    }

    public List<PasswordEntry> load() throws Exception {
        if (!Files.exists(VAULT_FILE)) return new ArrayList<>();
        String encryptedData = Files.readString(VAULT_FILE);
        if (encryptedData.isEmpty()) return new ArrayList<>();
        String json = cryptoService.decrypt(encryptedData);
        Type type = new TypeToken<ArrayList<PasswordEntry>>() {}.getType();
        List<PasswordEntry> entries = gson.fromJson(json, type);
        return entries != null ? entries : new ArrayList<>();
    }

    public List<Path> getBackupFiles() throws IOException {
        if (!Files.exists(BACKUPS_DIR)) return new ArrayList<>();
        try (Stream<Path> stream = Files.list(BACKUPS_DIR)) {
            return stream.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    public void reencryptAllBackups(CryptoService oldCrypto, CryptoService newCrypto) {
        try {
            List<Path> backups = getBackupFiles();
            for (Path backup : backups) {
                try {
                    String oldEncrypted = Files.readString(backup);
                    if (oldEncrypted.isEmpty()) continue;
                    String json = oldCrypto.decrypt(oldEncrypted);
                    String newEncrypted = newCrypto.encrypt(json);
                    // Overwrite content but keep the filename (and thus the count metadata) intact
                    Files.writeString(backup, newEncrypted, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (Exception e) {
                    System.err.println("Skipping backup " + backup.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}