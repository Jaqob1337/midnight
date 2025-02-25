package de.peter1337.midnight.render.font;

import me.x150.renderer.font.FontRenderer;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.HashMap;
import java.util.Map;

public class CustomFontRenderer {

    // Cache instances by their font size.
    private static Map<Float, CustomFontRenderer> instances = new HashMap<>();

    private FontRenderer renderer;
    private Font baseFont;
    private final FontRenderContext frc;

    private CustomFontRenderer(FontRenderer renderer, Font baseFont) {
        this.renderer = renderer;
        this.baseFont = baseFont;
        this.frc = new FontRenderContext(null, true, true);
    }

    /**
     * Returns an instance of CustomFontRenderer for the specified font size.
     * If one does not exist, it will be created and cached.
     *
     * @param size the desired font size (e.g., 12f)
     * @return a CustomFontRenderer instance configured with that font size.
     */
    public static CustomFontRenderer getInstanceForSize(float size) {
        if (instances.containsKey(size)) {
            return instances.get(size);
        }
        try {
            Font literFont = CustomFontLoader.loadFont("/assets/midnight/fonts/OutfitRegular.ttf", 600f);
            // Derive a new font at the desired size.
            Font newFont = literFont.deriveFont(size);
            FontRenderer fr = new FontRenderer(newFont, size);
            CustomFontRenderer instance = new CustomFontRenderer(fr, newFont);
            instances.put(size, instance);
            System.out.println("CustomFontRenderer created for size " + size);
            return instance;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("CustomFontRenderer creation failed! Falling back to Arial.");
            Font fallback = new Font("Arial", Font.PLAIN, 600);
            Font newFont = fallback.deriveFont(size);
            FontRenderer fr = new FontRenderer(newFont, size);
            CustomFontRenderer instance = new CustomFontRenderer(fr, newFont);
            instances.put(size, instance);
            return instance;
        }
    }

    public int getStringWidth(String text) {
        return (int) baseFont.getStringBounds(text, frc).getWidth();
    }

    public int getFontHeight() {
        return baseFont.getSize();
    }

    public void drawString(MatrixStack matrices, String text, int x, int y, int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        renderer.drawString(matrices, text, x, y, r, g, b, a);
    }

    public void drawStringWithShadow(MatrixStack matrices, String text, int x, int y, int mainColor, int shadowColor) {
        // Draw shadow with a vertical offset of 1 pixel.
        drawString(matrices, text, x, y + 1, shadowColor);
        // Draw main text at original coordinates.
        drawString(matrices, text, x, y, mainColor);
    }

    // Optionally, you can also add the overloaded method with scaling if needed.
}
