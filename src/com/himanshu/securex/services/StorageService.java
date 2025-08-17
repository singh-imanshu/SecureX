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
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the storage of the encrypted password vault on disk.
 * Implements an atomic save mechanism to prevent data corruption.
 */
public class StorageService {

    private static final Path APP_DIR = Paths.get(System.getProperty("user.home"), ".securex");
    private static final Path VAULT_FILE = APP_DIR.resolve("vault.dat");
    private static final Path TEMP_FILE = APP_DIR.resolve("vault.tmp");

    private final CryptoService cryptoService;
    private final Gson gson;

    public StorageService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
        // Using pretty printing for better readability if the file is ever inspected (though it will be encrypted).
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Saves the list of password entries to the encrypted vault file.
     * @param entries The list of entries to save.
     * @throws Exception if saving fails at any step.
     */
    public void save(List<PasswordEntry> entries) throws Exception {
        String json = gson.toJson(entries);
        String encryptedData = cryptoService.encrypt(json);

        // --- Atomic Save Operation ---
        // 1. Write to a temporary file.
        Files.writeString(TEMP_FILE, encryptedData);

        // 2. If the write was successful, replace the original file with the temporary one.
        // This move is an atomic operation on most file systems.
        Files.move(TEMP_FILE, VAULT_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Loads the list of password entries from the encrypted vault file.
     * @return A list of password entries. Returns an empty list if the vault doesn't exist.
     * @throws Exception if loading or decryption fails.
     */
    public List<PasswordEntry> load() throws Exception {
        if (!Files.exists(VAULT_FILE)) {
            return new ArrayList<>(); // Return an empty list for a new user.
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