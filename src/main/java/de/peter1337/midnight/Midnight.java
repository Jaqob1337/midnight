package de.peter1337.midnight;

import de.peter1337.midnight.events.KeyEvent;
import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.manager.command.CommandManager;
import de.peter1337.midnight.manager.command.CommandAutoComplete;
import de.peter1337.midnight.manager.command.CommandSuggestionRenderer;
import de.peter1337.midnight.render.CustomFontRenderer;
import de.peter1337.midnight.render.gui.ModuleArrayList;
import me.x150.renderer.render.MSAAFramebuffer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Midnight implements ModInitializer {
	public static final String CLIENT_NAME = "midnight";
	public static final String MOD_ID = CLIENT_NAME;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final String VERSION = "0.0.1";

	// Instance of the module array list overlay.
	private static final ModuleArrayList moduleArrayList = new ModuleArrayList();

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing {} mod...", CLIENT_NAME);
		ModuleManager.init();
		CommandManager.init();

		// Do NOT initialize the font renderer immediately.
		// Instead, delay initialization until the client window is available.

		// Register a client tick event to check if the window is ready.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.getWindow() != null && CustomFontRenderer.getInstance() == null) {
				try {
					CustomFontRenderer.init();
					LOGGER.info("CustomFontRenderer initialized successfully.");
				} catch (Exception e) {
					LOGGER.error("Failed to initialize CustomFontRenderer", e);
				}
			}
			ModuleManager.onUpdate();
			KeyEvent.tick();
			CommandAutoComplete.tick();
			if (client != null && client.getWindow() != null) {
				client.getWindow().setTitle(CLIENT_NAME + " " + VERSION);
			}
		});

		// Register HUD render callback (client-side only).
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
			HudRenderCallback.EVENT.register((DrawContext context, RenderTickCounter tickCounter) -> {
				// Render the modules overlay.
				moduleArrayList.render(context.getMatrices());
				CommandSuggestionRenderer.render(context);

				// Only attempt to render text if the font renderer is initialized.
				if (CustomFontRenderer.getInstance() != null) {
					MSAAFramebuffer.use(8, () -> {
						CustomFontRenderer.getInstance().drawString(
								context.getMatrices(),
								"Hello, Midnight!",
								10, 10,
								0xFFFFFFFF // White color in ARGB.
						);
					});
				} else {
					LOGGER.warn("CustomFontRenderer instance is still null during HUD rendering.");
				}
			});
		}
		LOGGER.info("{} mod initialized!", CLIENT_NAME);
	}
}
