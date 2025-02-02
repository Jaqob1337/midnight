package de.peter1337.midnight;

import de.peter1337.midnight.events.KeyEvent;
import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.manager.command.CommandManager;
import de.peter1337.midnight.manager.command.CommandAutoComplete;
import de.peter1337.midnight.manager.command.CommandSuggestionRenderer;
import de.peter1337.midnight.render.gui.ModuleArrayList;
import de.peter1337.midnight.render.CustomFontRenderer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Midnight implements ModInitializer {
	public static final String CLIENT_NAME = "midnight";
	public static final String MOD_ID = CLIENT_NAME;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final String VERSION = "0.0.1";

	// Create a static instance of our module array list overlay.
	private static final ModuleArrayList moduleArrayList = new ModuleArrayList();

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing {} mod...", CLIENT_NAME);
		ModuleManager.init();
		CommandManager.init();
		// Initialize custom font renderer.
		CustomFontRenderer.init();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ModuleManager.onUpdate();
			KeyEvent.tick();
			CommandAutoComplete.tick();
			if (client != null && client.getWindow() != null) {
				client.getWindow().setTitle(CLIENT_NAME + " " + VERSION);
			}
		});

		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
			HudRenderCallback.EVENT.register((DrawContext context, RenderTickCounter tickCounter) -> {
				// Render the modules arraylist overlay.
				moduleArrayList.render(context.getMatrices());
				// Render command suggestions if applicable.
				CommandSuggestionRenderer.render(context);
			});
		}
		LOGGER.info("{} mod initialized!", CLIENT_NAME);
	}
}
