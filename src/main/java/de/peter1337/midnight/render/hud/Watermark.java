package de.peter1337.midnight.render.hud;

import net.minecraft.client.util.math.MatrixStack;
import de.peter1337.midnight.render.font.CustomFontRenderer;

import static de.peter1337.midnight.Midnight.CLIENT_NAME;

/**
 * A utility class for rendering a watermark with a drop shadow effect.
 */
public class Watermark {

    private static final int X_POSITION = 7;
    private static final int Y_POSITION = 2;

    // RGB values for white (255, 255, 255) with full alpha (255)
    private static final int MAIN_COLOR = (255 << 24) | (100 << 16) | (100 << 8) | 255;

    // RGB values for black (0, 0, 0) with partial alpha (85)
    private static final int SHADOW_COLOR = (120 << 24) | (0 << 16) | (0 << 2) | 0;

    // Desired font size for the watermark.
    private static final float FONT_SIZE = 14f;

    public static void render(MatrixStack matrices) {
        CustomFontRenderer fontRenderer = CustomFontRenderer.getInstanceForSize(FONT_SIZE);
        if (fontRenderer == null) {
            return;
        }
        fontRenderer.drawStringWithShadow(matrices, CLIENT_NAME, X_POSITION, Y_POSITION, MAIN_COLOR, SHADOW_COLOR);
    }
}