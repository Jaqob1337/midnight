package de.peter1337.midnight.render;

import me.x150.renderer.font.FontRenderer;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.Font;
import java.awt.font.FontRenderContext;

public class CustomFontRenderer {

    private static CustomFontRenderer instance;
    // The Renderer library’s FontRenderer used for drawing text.
    private final FontRenderer renderer;
    // The AWT Font used for measurement.
    private final Font baseFont;
    // FontRenderContext for text measurement.
    private final FontRenderContext frc;

    private CustomFontRenderer(FontRenderer renderer, Font baseFont) {
        this.renderer = renderer;
        this.baseFont = baseFont;
        this.frc = new FontRenderContext(null, true, true);
    }

    public static void init() {
        try {
            // Use CustomFontLoader to load your font from resources.
            // The resource path must start with a slash.
            Font literFont = CustomFontLoader.loadFont("/assets/midnight/fonts/Liter.ttf", 18f);
            // Create the Renderer library's FontRenderer with your loaded font and a scale factor.
            FontRenderer fr = new FontRenderer(literFont, 9f);
            instance = new CustomFontRenderer(fr, literFont);
            System.out.println("CustomFontRenderer initialized successfully using Liter.ttf");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("CustomFontRenderer initialization failed! Falling back to Arial.");
            // Fallback using Arial:
            Font fallback = new Font("Arial", Font.PLAIN, 18);
            FontRenderer fr = new FontRenderer(fallback, 9f);
            instance = new CustomFontRenderer(fr, fallback);
        }
    }

    public static CustomFontRenderer getInstance() {
        return instance;
    }

    /**
     * Calculates the width of the given text using the underlying AWT Font.
     * (This simple implementation does not process Minecraft color codes.)
     */
    public int getStringWidth(String text) {
        return (int) baseFont.getStringBounds(text, frc).getWidth();
    }

    /**
     * Returns the font's line height.
     */
    public int getFontHeight() {
        return baseFont.getSize();
    }

    /**
     * Draws text at the specified coordinates using the ARGB color.
     *
     * @param matrices the current matrix stack
     * @param text     the text to draw
     * @param x        the x-coordinate on screen
     * @param y        the y-coordinate on screen
     * @param color    the color in ARGB format (e.g., 0xFFFFFFFF for white)
     */
    public void drawString(MatrixStack matrices, String text, int x, int y, int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        renderer.drawString(matrices, text, x, y, r, g, b, a);
    }
}
