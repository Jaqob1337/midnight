package de.peter1337.midnight.manager.command;

import de.peter1337.midnight.render.CustomFontRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class CommandSuggestionRenderer {

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Only render suggestions when the chat screen is open.
        if (!(client.currentScreen instanceof ChatScreen)) {
            return;
        }
        if (CommandAutoComplete.currentSuggestions == null || CommandAutoComplete.currentSuggestions.isEmpty()) {
            return;
        }

        // Use your custom font renderer instead of the default text renderer.
        CustomFontRenderer customFontRenderer = CustomFontRenderer.getInstance();
        if (customFontRenderer == null) {
            return;
        }

        // Draw suggestions near the bottom left (adjust x and y as needed).
        int x = 10;
        int y = client.getWindow().getScaledHeight() - 50 - (CommandAutoComplete.currentSuggestions.size() * 10);
        for (String suggestion : CommandAutoComplete.currentSuggestions) {
            // Render the suggestion using your custom font renderer.
            customFontRenderer.drawString(context.getMatrices(), suggestion, x, y, 0xFFFFFFFF);
            y += 10;
        }

        // --- Optional: Draw a test string ---
        customFontRenderer.drawString(context.getMatrices(), "Custom Font Test", x, y - 20, 0xFF00FF00);
    }
}
