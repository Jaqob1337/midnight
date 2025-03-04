package de.peter1337.midnight.render.font;

import me.x150.renderer.font.FontRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL11;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomFontRenderer {

    // Cache instances by their font size.
    private static Map<Float, CustomFontRenderer> instances = new HashMap<>();

    private FontRenderer renderer;
    private Font baseFont;
    private final FontRenderContext frc;

    // Clipping rectangle (x, y, width, height)
    private float clipX = 0;
    private float clipY = 0;
    private float clipWidth = 0;
    private float clipHeight = 0;
    private boolean clippingEnabled = false;

    // Additional clipping regions
    private final List<Rectangle> additionalClipRegions = new ArrayList<>();

    // Simple rectangle class for clipping
    public static class Rectangle {
        public float x, y, width, height;

        public Rectangle(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean intersects(Rectangle other) {
            return !(x + width <= other.x ||
                    other.x + other.width <= x ||
                    y + height <= other.y ||
                    other.y + other.height <= y);
        }

        public Rectangle getIntersection(Rectangle other) {
            float ix = Math.max(this.x, other.x);
            float iy = Math.max(this.y, other.y);
            float iw = Math.min(this.x + this.width, other.x + other.width) - ix;
            float ih = Math.min(this.y + this.height, other.y + other.height) - iy;

            if (iw <= 0 || ih <= 0) {
                return null;
            }

            return new Rectangle(ix, iy, iw, ih);
        }
    }

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

    /**
     * Sets the clipping rectangle for text rendering.
     *
     * @param x The x-coordinate of the clip rectangle
     * @param y The y-coordinate of the clip rectangle
     * @param width The width of the clip rectangle
     * @param height The height of the clip rectangle
     */
    public void setClipBounds(float x, float y, float width, float height) {
        this.clipX = x;
        this.clipY = y;
        this.clipWidth = width;
        this.clipHeight = height;
        this.clippingEnabled = true;
    }

    /**
     * Disables clipping for text rendering.
     */
    public void resetClipBounds() {
        this.clippingEnabled = false;
    }

    /**
     * Adds an additional clipping region that will be intersected with the main clipping region.
     * Text will only render in the intersection of all clipping regions.
     *
     * @param x The x-coordinate of the additional clip rectangle
     * @param y The y-coordinate of the additional clip rectangle
     * @param width The width of the additional clip rectangle
     * @param height The height of the additional clip rectangle
     * @return The index of the added region, which can be used to remove it later
     */
    public int addClipRegion(float x, float y, float width, float height) {
        additionalClipRegions.add(new Rectangle(x, y, width, height));
        return additionalClipRegions.size() - 1;
    }

    /**
     * Removes a specific clip region by index.
     *
     * @param index The index of the clip region to remove
     */
    public void removeClipRegion(int index) {
        if (index >= 0 && index < additionalClipRegions.size()) {
            additionalClipRegions.remove(index);
        }
    }

    /**
     * Clears all additional clipping regions.
     */
    public void clearAdditionalClipRegions() {
        additionalClipRegions.clear();
    }

    public int getStringWidth(String text) {
        return (int) baseFont.getStringBounds(text, frc).getWidth();
    }

    public int getFontHeight() {
        return baseFont.getSize();
    }

    /**
     * Draws a string with clipping if enabled.
     */
    public void drawString(MatrixStack matrices, String text, int x, int y, int color) {
        if (clippingEnabled) {
            drawClippedString(matrices, text, x, y, color);
        } else {
            // Use the existing FontRenderer to draw the string
            float a = ((color >> 24) & 0xFF) / 255f;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            renderer.drawString(matrices, text, x, y, r, g, b, a);
        }
    }

    /**
     * Draws a string with shadow and clipping if enabled.
     */
    public void drawStringWithShadow(MatrixStack matrices, String text, int x, int y, int mainColor, int shadowColor) {
        if (clippingEnabled) {
            // Draw shadow with a vertical offset of 1 pixel.
            drawClippedString(matrices, text, x, y + 1, shadowColor);
            // Draw main text at original coordinates.
            drawClippedString(matrices, text, x, y, mainColor);
        } else {
            // Draw shadow with a vertical offset of 1 pixel.
            drawString(matrices, text, x, y + 1, shadowColor);
            // Draw main text at original coordinates.
            drawString(matrices, text, x, y, mainColor);
        }
    }

    /**
     * Internal method to draw text with clipping.
     */
    private void drawClippedString(MatrixStack matrices, String text, int x, int y, int color) {
        // Calculate final clipping rectangle by intersecting the main clip with all additional clip regions
        Rectangle finalClip = new Rectangle(clipX, clipY, clipWidth, clipHeight);

        // Apply additional clipping regions if there are any
        for (Rectangle additionalClip : additionalClipRegions) {
            Rectangle intersection = finalClip.getIntersection(additionalClip);
            if (intersection == null) {
                // No intersection means nothing to render
                return;
            }
            finalClip = intersection;
        }

        // If text is fully outside the final clip bounds, don't render it
        if (x >= finalClip.x + finalClip.width || y >= finalClip.y + finalClip.height ||
                x + getStringWidth(text) <= finalClip.x || y + getFontHeight() <= finalClip.y) {
            return;
        }

        // Save the current scissor state
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] scissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);

        try {
            // Enable scissor test if not already enabled
            GL11.glEnable(GL11.GL_SCISSOR_TEST);

            // Convert to screen coordinates and set scissor rectangle
            MinecraftClient mc = MinecraftClient.getInstance();
            float guiScale = (float) mc.options.getGuiScale().getValue();
            int windowHeight = mc.getWindow().getHeight();

            // Convert to OpenGL coordinates (bottom-left origin)
            int scissorX = (int)(finalClip.x * guiScale);
            int scissorY = (int)(windowHeight - (finalClip.y + finalClip.height) * guiScale);
            int scissorWidth = (int)(finalClip.width * guiScale);
            int scissorHeight = (int)(finalClip.height * guiScale);

            GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);

            // Draw the text
            float a = ((color >> 24) & 0xFF) / 255f;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            renderer.drawString(matrices, text, x, y, r, g, b, a);

        } finally {
            // Restore previous scissor state
            if (scissorEnabled) {
                GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    /**
     * Draws text within a clipping rectangle.
     * This is a convenience method that sets clipping, draws the text, and resets clipping.
     */
    public void drawClippedText(MatrixStack matrices, String text, int x, int y, int color,
                                float clipX, float clipY, float clipWidth, float clipHeight) {
        setClipBounds(clipX, clipY, clipWidth, clipHeight);
        drawString(matrices, text, x, y, color);
        resetClipBounds();
    }

    /**
     * Draws text with shadow within a clipping rectangle.
     * This is a convenience method that sets clipping, draws the text with shadow, and resets clipping.
     */
    public void drawClippedTextWithShadow(MatrixStack matrices, String text, int x, int y,
                                          int mainColor, int shadowColor,
                                          float clipX, float clipY, float clipWidth, float clipHeight) {
        setClipBounds(clipX, clipY, clipWidth, clipHeight);
        drawStringWithShadow(matrices, text, x, y, mainColor, shadowColor);
        resetClipBounds();
    }
}