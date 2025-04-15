package de.peter1337.midnight.handler;

import de.peter1337.midnight.Midnight; // Ensure correct import
import de.peter1337.midnight.events.KeyEvent;
import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.manager.command.CommandAutoComplete;
import de.peter1337.midnight.modules.combat.Aura;
import de.peter1337.midnight.modules.player.Scaffold;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient; // Import MinecraftClient
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TickHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TickHandler.class);
    // Cache module instances for minor performance gain if accessed frequently per tick
    private static Aura auraModule = null;
    private static Scaffold scaffoldModule = null;


    public static void init() {
        // Initialize the RotationHandler first
        RotationHandler.init();
        LOGGER.info("TickHandler initializing...");

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            // Pre-Update phase: Logic that needs to run before the game processes player actions/input for the tick.
            if (client == null || client.player == null) {
                // Clear cached modules if player logs out or client is invalid
                auraModule = null;
                scaffoldModule = null;
                return; // Nothing to do if no player/client
            }

            // Cache modules if not already done (or if player changed)
            if (auraModule == null) {
                auraModule = (Aura) ModuleManager.getModule("Aura");
            }
            if (scaffoldModule == null) {
                scaffoldModule = (Scaffold) ModuleManager.getModule("Scaffold");
            }


            // Call pre-update methods for enabled modules
            try {
                if (auraModule != null && auraModule.isEnabled()) {
                    auraModule.preUpdate();
                }
            } catch (Exception e) {
                LOGGER.error("Error during Aura preUpdate:", e);
                // Optionally disable the module to prevent further errors:
                // if (auraModule != null) auraModule.toggle();
            }

            try {
                if (scaffoldModule != null && scaffoldModule.isEnabled()) {
                    // Ensure Scaffold's preUpdate method exists and is public
                }
            } catch (Exception e) {
                LOGGER.error("Error during Scaffold preUpdate:", e);
                // if (scaffoldModule != null) scaffoldModule.toggle();
            }

            // Add other pre-update module calls here...

        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Post-Update phase: Logic that runs after the main game tick processing.

            if (client == null) return; // Basic check

            // Update Rotation Handler (processes requests made during START_TICK)
            // Needs to happen even if player is null briefly during login/logout screens
            try {
                RotationHandler.onUpdate();
            } catch (Exception e) {
                LOGGER.error("Error during RotationHandler onUpdate:", e);
            }


            // Update modules (general update, not pre-update)
            try {
                ModuleManager.onUpdate();
            } catch (Exception e) {
                LOGGER.error("Error during ModuleManager onUpdate:", e);
            }


            // Process key events.
            try {
                KeyEvent.tick();
            } catch (Exception e) {
                LOGGER.error("Error during KeyEvent tick:", e);
            }


            // Update command auto-completion.
            try {
                CommandAutoComplete.tick();
            } catch (Exception e) {
                LOGGER.error("Error during CommandAutoComplete tick:", e);
            }


            // Update window title (only needs to be done once per tick, END is fine)
            if (client.getWindow() != null) {
                try {
                    client.getWindow().setTitle(Midnight.CLIENT_NAME + " " + Midnight.VERSION);
                } catch (Exception e) {
                    // Catch potential rare exceptions during title setting
                    LOGGER.warn("Could not set window title", e);
                }
            }
        });


        LOGGER.info("TickHandler initialized successfully.");
    }
}