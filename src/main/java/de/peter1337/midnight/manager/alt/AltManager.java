package de.peter1337.midnight.manager.alt;

import de.peter1337.midnight.utils.MicrosoftAuthenticator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AltManager {
    private static final List<AltAccount> altAccounts = new ArrayList<>();

    public static void addAlt(AltAccount alt) {
        altAccounts.add(alt);
    }

    public static List<AltAccount> getAltAccounts() {
        return altAccounts;
    }

    public static void login(AltAccount alt) {
        Session session;
        if (alt.getType() == AltAccount.Type.MICROSOFT) {
            try {
                // Call the authenticator which returns an AuthResult object
                MicrosoftAuthenticator.AuthResult authResult = MicrosoftAuthenticator.loginWithMicrosoft(alt.getRefreshToken());

                // Extract the session from the result
                session = authResult.session;

                // IMPORTANT: Update the account with the new refresh token for the next login
                alt.setRefreshToken(authResult.refreshToken);
            } catch (Exception e) {
                e.printStackTrace();
                // We could add a status message here to show the login failed.
                return;
            }
        } else { // CRACKED
            session = new Session(
                    alt.getUsername(),
                    UUID.nameUUIDFromBytes(("OfflinePlayer:" + alt.getUsername()).getBytes()),
                    "0", // No access token for cracked
                    Optional.empty(),
                    Optional.empty(),
                    Session.AccountType.LEGACY);
        }

        setSession(session);
    }

    private static void setSession(Session session) {
        try {
            // Use reflection to set the session, as there is no public setter
            Field sessionField = MinecraftClient.class.getDeclaredField("session");
            sessionField.setAccessible(true);
            sessionField.set(MinecraftClient.getInstance(), session);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // This would be a critical error if it happens
            throw new RuntimeException("Failed to set Minecraft session", e);
        }
    }
}
