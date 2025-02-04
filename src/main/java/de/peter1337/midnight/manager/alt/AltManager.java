package de.peter1337.midnight.manager.alt;

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

    /**
     * Logs in using the specified AltAccount by replacing the MinecraftClient's session.
     * This method uses reflection because there is no public setSession method.
     *
     * @param alt the AltAccount to log in with
     */
    public static void login(AltAccount alt) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            // Use reflection to access the private "session" field.
            Field sessionField = MinecraftClient.class.getDeclaredField("session");
            sessionField.setAccessible(true);
            // Create a new offline session:
            // - profileId: we use the username
            // - UUID: generated from the username
            // - username: alt.getUsername()
            // - accessToken: Optional.empty()
            // - sessionToken: Optional.empty()
            // - accountType: Session.AccountType.LEGACY
            Session newSession = new Session(
                    alt.getUsername(),
                    UUID.nameUUIDFromBytes(alt.getUsername().getBytes()),
                    alt.getUsername(),
                    Optional.empty(),
                    Optional.empty(),
                    Session.AccountType.LEGACY
            );
            sessionField.set(client, newSession);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
