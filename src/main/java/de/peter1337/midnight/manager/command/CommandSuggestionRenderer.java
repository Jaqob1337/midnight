package de.peter1337.midnight.manager.command;

import de.peter1337.midnight.render.font.CustomFontRenderer;
import de.peter1337.midnight.utils.ColorUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

@Environment(EnvType.CLIENT)
public class CommandSuggestionRenderer {

    // This list should be maintained by your auto-complete logic.
    public static java.util.List<String> currentSuggestions;

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Only render suggestions when the chat screen is open.
        if (!(client.currentScreen instanceof ChatScreen)) {
            return;
        }
        if (currentSuggestions == null || currentSuggestions.isEmpty()) {
            return;
        }

        // Use your custom font renderer instead of the default text renderer.
        CustomFontRenderer customFontRenderer = CustomFontRenderer.getInstance();
        if (customFontRenderer == null) {
            return;
        }

        // Create a color using the RGB system.
        // Example: A light blue color (red=100, green=150, blue=255) with full opacity (alpha=255)
        int suggestionColor = ColorUtil.getColor(100, 150, 255, 255);

        // Draw suggestions near the bottom left (adjust x and y as needed).
        int x = 10;
        int y = client.getWindow().getScaledHeight() - 50 - (currentSuggestions.size() * 10);
        for (String suggestion : currentSuggestions) {
            // Render the suggestion using your custom font renderer with the RGB color.
            customFontRenderer.drawStringWithShadow(context.getMatrices(), suggestion, x, y, suggestionColor, 0x55000000);
            y += 10;
        }

    }
}
