package com.himanshu.securex.model;

import java.util.Arrays;

public class PasswordEntry {
    private String account;
    private String username;
    private char[] password;

    public PasswordEntry(String account, String username, char[] password) {
        this.account = account;
        this.username = username;
        // --- THIS IS THE FIX ---
        // Create a defensive copy to prevent the original array from being modified externally.
        this.password = Arrays.copyOf(password, password.length);
    }

    // --- Getters and Setters ---

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public char[] getPassword() {
        return password;
    }

    public void setPassword(char[] password) {
        // Defensively copy the array to prevent external modification
        this.password = Arrays.copyOf(password, password.length);
    }

    /**
     * Securely clears the password from memory.
     */
    public void clearPassword() {
        if (this.password != null) {
            Arrays.fill(this.password, '\0');
        }
    }

    /**
     * Provides a string representation for display purposes, e.g., in a ListView.
     * @return The account name.
     */
    @Override
    public String toString() {
        return this.account;
    }
}