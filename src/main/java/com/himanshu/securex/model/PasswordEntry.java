package com.himanshu.securex.model;

import java.util.Arrays;

public class PasswordEntry {
    private String account;
    private String username;
    private char[] password;
    private String url;

    public PasswordEntry(String account, String username, char[] password, String url) {
        this.account = account;
        this.username = username;
        this.password = Arrays.copyOf(password, password.length);
        this.url = url;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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