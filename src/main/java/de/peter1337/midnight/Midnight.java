package de.peter1337.midnight;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.peter1337.midnight.events.KeyEvent;
import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.manager.CommandManager;

public class Midnight implements ModInitializer {
	public static final String MOD_ID = "midnight";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Midnight mod...");

		// Initialize systems
		KeyEvent.init();
		ModuleManager.init();
		CommandManager.init();

		// Set up tick event for module updates
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ModuleManager.onUpdate();
		});

		LOGGER.info("Midnight mod initialized!");
	}
}