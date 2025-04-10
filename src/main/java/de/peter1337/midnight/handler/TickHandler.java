package de.peter1337.midnight.handler;

import de.peter1337.midnight.events.KeyEvent;
import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.manager.command.CommandAutoComplete;
import de.peter1337.midnight.modules.combat.Aura;
import de.peter1337.midnight.modules.player.Scaffold;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.peter1337.midnight.Midnight;

public class TickHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TickHandler.class);

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // You no longer need to call CustomFontRenderer.init() here,
            // as each component will request its own instance.

            // Initialize RotationHandler if not done yet
            if (client != null && client.player != null) {
                RotationHandler.onUpdate();
            }

            // Update modules.
            ModuleManager.onUpdate();
            // Process key events.
            KeyEvent.tick();
            // Update command auto-completion.
            CommandAutoComplete.tick();

            if (client != null && client.getWindow() != null) {
                client.getWindow().setTitle(Midnight.CLIENT_NAME + " " + Midnight.VERSION);
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client != null && client.getWindow() != null) {
                client.getWindow().setTitle(Midnight.CLIENT_NAME + " " + Midnight.VERSION);
            }

            // Call pre-update methods during PRE tick
            if (client != null && client.player != null) {
                // Process Aura's preUpdate
                Aura aura = (Aura) ModuleManager.getModule("Aura");
                if (aura != null && aura.isEnabled()) {
                    aura.preUpdate();
                }

                // Add Scaffold preUpdate call
                Scaffold scaffold = (Scaffold) ModuleManager.getModule("Scaffold");
                if (scaffold != null && scaffold.isEnabled()) {
                    scaffold.preUpdate();
                }
            }
        });

        // Initialize the RotationHandler
        RotationHandler.init();
        LOGGER.info("TickHandler initialized with RotationHandler");
    }
}