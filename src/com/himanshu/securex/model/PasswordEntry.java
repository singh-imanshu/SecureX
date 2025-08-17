package com.himanshu.securex.model;

public class PasswordEntry {
    private String account;
    private String username;
    private String password;

    public PasswordEntry(String account, String username, String password) {
        this.account = account;
        this.username = username;
        this.password = password;
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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