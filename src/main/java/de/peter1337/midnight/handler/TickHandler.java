package de.peter1337.midnight.handler;

import de.peter1337.midnight.events.KeyEvent;
import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.manager.command.CommandAutoComplete;
import de.peter1337.midnight.modules.render.ESP;
import de.peter1337.midnight.render.font.CustomFontRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.peter1337.midnight.Midnight;

public class TickHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TickHandler.class);

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Initialize the custom font renderer if it hasn't been created yet.
            if (client.getWindow() != null && CustomFontRenderer.getInstance() == null) {
                try {
                    CustomFontRenderer.init();
                    LOGGER.info("CustomFontRenderer initialized successfully.");
                } catch (Exception e) {
                    LOGGER.error("Failed to initialize CustomFontRenderer", e);
                }
            }

            // Update all modules.
            ModuleManager.onUpdate();

            // Process key events.
            KeyEvent.tick();

            // Call ESP.tick() every tick to update glowing effects.
            // Update command auto-completion.
            CommandAutoComplete.tick();

            // Optionally update the window title with the client name and version.
            if (client != null && client.getWindow() != null) {
                client.getWindow().setTitle(Midnight.CLIENT_NAME + " " + Midnight.VERSION);
            }
        });
    }
}
