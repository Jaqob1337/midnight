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

    public static java.util.List<String> currentSuggestions;

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof ChatScreen)) {
            return;
        }
        if (currentSuggestions == null || currentSuggestions.isEmpty()) {
            return;
        }

        // Use a font renderer for command suggestions with a desired size.
        CustomFontRenderer customFontRenderer = CustomFontRenderer.getInstanceForSize(9f);
        if (customFontRenderer == null) {
            return;
        }

        int suggestionColor = ColorUtil.getColor(100, 150, 255, 255);

        int x = 10;
        int y = client.getWindow().getScaledHeight() - 50 - (currentSuggestions.size() * customFontRenderer.getFontHeight());
        for (String suggestion : currentSuggestions) {
            customFontRenderer.drawStringWithShadow(context.getMatrices(), suggestion, x, y, suggestionColor, 0x55000000);
            y += customFontRenderer.getFontHeight() + 2;
        }
    }
}
