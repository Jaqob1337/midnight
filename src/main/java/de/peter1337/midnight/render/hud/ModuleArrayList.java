package de.peter1337.midnight.render.hud;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.render.font.CustomFontRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;
import java.util.stream.Collectors;

public class ModuleArrayList {

    // Offset from the right edge in raw pixels.
    private static final int RIGHT_OFFSET = 10;

    public void render(MatrixStack matrices) {
        MinecraftClient mc = MinecraftClient.getInstance();
        CustomFontRenderer fontRenderer = CustomFontRenderer.getInstance();
        if (fontRenderer == null) {
            return;
        }

        // Get the unscaled width in raw pixels.
        int unscaledWidth = mc.getWindow().getWidth();
        int fixedRightMargin = unscaledWidth - RIGHT_OFFSET;

        // The GUI scale factor (as applied by Minecraft’s GUI rendering).
        // In many versions this is obtained from the window itself.
        // Note: This factor is an integer representing the effective scale.
        int guiScale = (int) mc.getWindow().getScaleFactor();

        // --- Undo the GUI scaling:
        // The MatrixStack passed to us is already scaled by guiScale.
        // To draw in raw pixel space, push a new matrix and scale by 1/guiScale.
        matrices.push();
        matrices.scale(1.0F / guiScale, 1.0F / guiScale, 1.0F);

        // In these coordinates, 1 unit equals 1 raw pixel.
        // Calculate our right margin in these unscaled coordinates.
        int adjustedRightMargin = fixedRightMargin / guiScale;

        // Get enabled modules.
        List<Module> enabledModules = ModuleManager.getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());

        int y = 3; // starting y-coordinate in raw pixels

        for (Module module : enabledModules) {
            String text = module.getName();
            int textWidth = fontRenderer.getStringWidth(text);

            // Clip text if it would overflow.
            if (textWidth > adjustedRightMargin - 3) { // leaving a small margin (3 pixels)
                while (textWidth > adjustedRightMargin - 3 && text.length() > 0) {
                    text = text.substring(0, text.length() - 1);
                    textWidth = fontRenderer.getStringWidth(text + "...");
                }
                text = text + "...";
                textWidth = fontRenderer.getStringWidth(text);
            }

            // Right-align: calculate x so that the text’s right edge sits at adjustedRightMargin.
            int x = adjustedRightMargin - textWidth;

            // Draw text using your custom font renderer.
            fontRenderer.drawStringWithShadow(matrices, text, x, y, 0xFFFFFFFF, 0x55000000);
            y += fontRenderer.getFontHeight() + 2;
        }

        matrices.pop();
    }
}
