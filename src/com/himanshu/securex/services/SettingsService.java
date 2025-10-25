package com.himanshu.securex.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Service for managing application settings persistence.
 * Handles storing and retrieving user preferences like auto-lock timeout.
 */
public class SettingsService {

    private static final Path APP_DIR = Paths.get(System.getProperty("user.home"), ".securex");
    private static final Path SETTINGS_FILE = APP_DIR.resolve("settings.properties");

    private static final String AUTO_LOCK_TIMEOUT_KEY = "autolock.timeout.minutes";
    private static final int DEFAULT_AUTO_LOCK_TIMEOUT = 5; // 5 minutes default

    private Properties properties;

    public SettingsService() {
        ensureAppDirectoryExists();
        loadSettings();
    }

    private void ensureAppDirectoryExists() {
        if (!Files.exists(APP_DIR)) {
            try {
                Files.createDirectories(APP_DIR);
            } catch (IOException e) {
                throw new RuntimeException("Could not create application data directory.", e);
            }
        }
    }

    private void loadSettings() {
        properties = new Properties();

        if (Files.exists(SETTINGS_FILE)) {
            try {
                properties.load(Files.newInputStream(SETTINGS_FILE));
            } catch (IOException e) {
                System.err.println("Warning: Could not load settings file. Using defaults.");
                // Continue with empty properties (defaults will be used)
            }
        }
    }

    private void saveSettings() {
        try {
            properties.store(Files.newOutputStream(SETTINGS_FILE), "SecureX Application Settings");
        } catch (IOException e) {
            System.err.println("Error: Could not save settings file.");
            e.printStackTrace();
        }
    }

    /**
     * Gets the auto-lock timeout in minutes.
     * @return The timeout in minutes, or -1 if auto-lock is disabled.
     */
    public int getAutoLockTimeout() {
        String value = properties.getProperty(AUTO_LOCK_TIMEOUT_KEY, String.valueOf(DEFAULT_AUTO_LOCK_TIMEOUT));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return DEFAULT_AUTO_LOCK_TIMEOUT;
        }
    }

    /**
     * Sets the auto-lock timeout in minutes.
     * @param timeoutMinutes The timeout in minutes, or -1 to disable auto-lock.
     */
    public void setAutoLockTimeout(int timeoutMinutes) {
        properties.setProperty(AUTO_LOCK_TIMEOUT_KEY, String.valueOf(timeoutMinutes));
        saveSettings();
    }
}