package de.peter1337.midnight;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.manager.command.CommandManager;
import de.peter1337.midnight.handler.TickHandler;
import de.peter1337.midnight.render.HudRenderer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Midnight implements ModInitializer {
	public static final String CLIENT_NAME = "midnight";
	public static final String MOD_ID = CLIENT_NAME;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final String VERSION = "0.0.1";

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing {} mod...", CLIENT_NAME);

		// Manager
		ModuleManager.init();
		CommandManager.init();

		//Handler
		TickHandler.init();

		//HUD
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
			HudRenderer.init();
		}

		LOGGER.info("{} mod initialized!", CLIENT_NAME);
	}
}
