package de.peter1337.midnight;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.manager.command.CommandManager;
import de.peter1337.midnight.handler.TickHandler;
import de.peter1337.midnight.render.HudRenderer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Midnight implements ModInitializer {
	public static final String CLIENT_NAME = "midnight";
	public static final String MOD_ID = CLIENT_NAME;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final String VERSION = "0.0.1";

	// Directory structure
	public static final File BASE_DIR = new File("midnight");
	public static final File CONFIG_DIR = new File(BASE_DIR, "configs");

	/**
	 * Creates the necessary directories for the client
	 */
	private void initializeDirectories() {
		if (!BASE_DIR.exists()) {
			if (BASE_DIR.mkdir()) {
				LOGGER.info("Created base directory: {}", BASE_DIR.getAbsolutePath());
			} else {
				LOGGER.error("Failed to create base directory: {}", BASE_DIR.getAbsolutePath());
			}
		}

		if (!CONFIG_DIR.exists()) {
			if (CONFIG_DIR.mkdir()) {
				LOGGER.info("Created configs directory: {}", CONFIG_DIR.getAbsolutePath());
			} else {
				LOGGER.error("Failed to create configs directory: {}", CONFIG_DIR.getAbsolutePath());
			}
		}
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing {} mod...", CLIENT_NAME);

		// Create necessary directories
		initializeDirectories();

		// Manager
		ModuleManager.init();
		CommandManager.init();

		// Handler
		TickHandler.init();

		// Register resource reload listener
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
			ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
					new SimpleSynchronousResourceReloadListener() {
						@Override
						public Identifier getFabricId() {
							return Identifier.of(MOD_ID, "textures");
						}

						@Override
						public void reload(ResourceManager manager) {
							// Log all available resources for debugging
							manager.findResources("textures/gui/category", id -> id.getPath().endsWith(".png"))
									.forEach((identifier, resource) -> {
										LOGGER.info("Found texture resource: {}", identifier);
									});
						}
					}
			);

			// HUD
			HudRenderer.init();
		}

		LOGGER.info("{} mod initialized!", CLIENT_NAME);
	}
}