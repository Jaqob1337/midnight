package de.peter1337.midnight.manager.alt;

public class AltAccount {

    public enum Type {
        CRACKED,
        MICROSOFT
    }

    private final String username;
    private String refreshToken;
    private final Type type;

    // For cracked accounts
    public AltAccount(String username) {
        this.username = username;
        this.type = Type.CRACKED;
        this.refreshToken = "";
    }

    // For Microsoft accounts
    public AltAccount(String username, String refreshToken) {
        this.username = username;
        this.refreshToken = refreshToken;
        this.type = Type.MICROSOFT;
    }

    public String getUsername() {
        return username;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Type getType() {
        return type;
    }
}