package de.peter1337.midnight.render.gui;

import net.minecraft.client.util.math.MatrixStack;
import de.peter1337.midnight.render.CustomFontRenderer;

import static de.peter1337.midnight.Midnight.CLIENT_NAME;

/**
 * A utility class for rendering a watermark (or any persistent text overlay)
 * with a drop shadow effect.
 */
public class Watermark {

    // Adjust these constants as needed.
    private static final int X_POSITION = 3;
    private static final int Y_POSITION = 2;
    private static final int MAIN_COLOR = 0xFFFFFFFF;      // White color (ARGB)
    private static final int SHADOW_COLOR = 0x55000000;      // More transparent black

    /**
     * Renders the watermark text with a drop shadow using the CustomFontRenderer.
     *
     * @param matrices the MatrixStack to render the text
     */
    public static void render(MatrixStack matrices) {
        CustomFontRenderer fontRenderer = CustomFontRenderer.getInstance();
        if (fontRenderer == null) {
            return;
        }
        // Draw the text with shadow using the integrated helper method.
        fontRenderer.drawStringWithShadow(matrices, CLIENT_NAME, X_POSITION, Y_POSITION, MAIN_COLOR, SHADOW_COLOR);
    }
}
