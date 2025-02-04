package de.peter1337.midnight.manager.alt;

public class AltAccount {
    private final String username;
    private final String password; // For cracked accounts, may be empty

    public AltAccount(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // Overloaded constructor for cracked accounts (no password)
    public AltAccount(String username) {
        this(username, "");
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
