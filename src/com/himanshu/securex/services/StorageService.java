package com.himanshu.securex.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.himanshu.securex.model.PasswordEntry;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages the persistence of the encrypted password vault to the user's local filesystem.

 * This service is responsible for both loading the vault from disk and saving it securely.

 * It incorporates two critical data safety features:
 * 1. Automatic Encrypted Backups:
 * Before any save operation, the existing vault file is moved to a dedicated backups
 * directory with a timestamp. This creates a version history of the vault. The service
 * automatically prunes older backups, retaining only the most recent versions to prevent excessive disk usage.
 * This provides a safety net against accidental data deletion or corruption

 * 2. Atomic Save Operations:
 * To prevent data loss in case of an unexpected application crash or shutdown during a writing operation,
 * this service performs an atomic save. All new data is first written to a temporary file
 * Only after the writing operation is fully successful is the temporary file
 * atomically moved to replace the main vault file. This ensures that the
 * primary vault file is never left in a corrupted, partially-written state.
 */


public class StorageService {

    private static final Path APP_DIR = Paths.get(System.getProperty("user.home"), ".securex");
    private static final Path VAULT_FILE = APP_DIR.resolve("vault.dat");
    private static final Path TEMP_FILE = APP_DIR.resolve("vault.tmp");
    private static final Path BACKUPS_DIR = APP_DIR.resolve("backups");
    private static final int MAX_BACKUPS = 5;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");


    private final CryptoService cryptoService;
    private final Gson gson;

    public StorageService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void save(List<PasswordEntry> entries) throws Exception {
        // Step 1: Backup the existing vault before doing anything else.
        backupExistingVault();

        // Step 2: Proceed with the original atomic save operation.
        String json = gson.toJson(entries);
        String encryptedData = cryptoService.encrypt(json);

        Files.writeString(TEMP_FILE, encryptedData);
        Files.move(TEMP_FILE, VAULT_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void backupExistingVault() throws IOException {
        if (Files.exists(VAULT_FILE)) {
            // Ensure the backups directory exists.
            Files.createDirectories(BACKUPS_DIR);

            // Create a timestamped name for the backup file.
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
            Path backupFile = BACKUPS_DIR.resolve("vault-" + timestamp + ".dat");

            // Move the current vault to the backup directory.
            Files.move(VAULT_FILE, backupFile, StandardCopyOption.ATOMIC_MOVE);

            // Prune old backups to keep the directory clean.
            pruneOldBackups();
        }
    }

    private void pruneOldBackups() throws IOException {
        try (Stream<Path> stream = Files.list(BACKUPS_DIR)) {
            List<Path> backups = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .collect(Collectors.toList());

            if (backups.size() > MAX_BACKUPS) {
                // Get the sublist of the oldest backups that exceed the max count.
                List<Path> toDelete = backups.subList(MAX_BACKUPS, backups.size());
                for (Path oldBackup : toDelete) {
                    Files.delete(oldBackup);
                }
            }
        }
    }

    public List<PasswordEntry> load() throws Exception {
        if (!Files.exists(VAULT_FILE)) {
            return new ArrayList<>();
        }

        String encryptedData = Files.readString(VAULT_FILE);
        if (encryptedData.isEmpty()) {
            return new ArrayList<>();
        }

        String json = cryptoService.decrypt(encryptedData);

        Type type = new TypeToken<ArrayList<PasswordEntry>>() {}.getType();
        List<PasswordEntry> entries = gson.fromJson(json, type);

        return entries != null ? entries : new ArrayList<>();
    }
}