package de.peter1337.midnight.manager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.RenderLayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;

import java.util.HashMap;
import java.util.Map;

public class TextureManager {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    // Cache texture setup state to avoid redundant operations
    private static final Map<Identifier, Boolean> textureSetupCache = new HashMap<>();
    // Track time to limit texture operations during rapid UI movement
    private static long lastTextureOpTime = 0;
    // Constants for optimization
    private static final long TEXTURE_OP_THROTTLE = 8; // ~125fps for texture ops
    private static final float MAX_ANISOTROPY = 1.0f; // Reasonable value for UI

    /**
     * Draws a texture within the bounds of a clipping rectangle with optimized GPU usage.
     */
    public static void drawClippedTexture(DrawContext context, Identifier texture,
                                          float x, float y, float width, float height,
                                          float clipX, float clipY, float clipWidth, float clipHeight) {
        try {
            // Fast rejection test - skip if completely outside clip bounds
            if (x >= clipX + clipWidth || y >= clipY + clipHeight ||
                    x + width <= clipX || y + height <= clipY) {
                return;
            }

            // Throttle texture operations for better performance
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTextureOpTime < TEXTURE_OP_THROTTLE) {
                // Only allow small movements to pass the throttle
                // If texture is being drawn exactly where it was before, no need to throttle
                if (Math.abs(lastDrawX - x) > 1 || Math.abs(lastDrawY - y) > 1) {
                    Thread.yield(); // Just yield instead of skip to avoid flickering
                }
            }
            lastTextureOpTime = currentTime;
            lastDrawX = x;
            lastDrawY = y;

            // Calculate visible portion with integer precision
            int visibleX = Math.max((int)x, (int)clipX);
            int visibleY = Math.max((int)y, (int)clipY);
            int visibleWidth = (int)Math.min(x + width, clipX + clipWidth) - visibleX;
            int visibleHeight = (int)Math.min(y + height, clipY + clipHeight) - visibleY;

            // Ensure we don't try to draw zero or negative dimensions
            if (visibleWidth <= 0 || visibleHeight <= 0) {
                return;
            }

            // Calculate UV coordinates for the visible portion
            float u1 = (visibleX - x) / width;
            float v1 = (visibleY - y) / height;
            float u2 = u1 + (visibleWidth / width);
            float v2 = v1 + (visibleHeight / height);

            // Setup texture filtering parameters once and cache the result
            if (!textureSetupCache.containsKey(texture)) {
                setupTextureFiltering(texture);
            }

            // Draw the visible portion of the texture
            context.drawTexture(
                    RenderLayer::getGuiTextured,
                    texture,
                    visibleX,
                    visibleY,
                    (int)(u1 * width),  // u offset
                    (int)(v1 * height), // v offset
                    visibleWidth,
                    visibleHeight,
                    (int)width,
                    (int)height
            );
        } catch (Exception e) {
            System.out.println("Error in drawClippedTexture: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Keep track of last drawing position to detect movement
    private static float lastDrawX = 0;
    private static float lastDrawY = 0;

    /**
     * Sets up texture filtering options and caches the result
     */
    private static void setupTextureFiltering(Identifier texture) {
        try {
            int glId = mc.getTextureManager().getTexture(texture).getGlId();

            // Save previous texture binding
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

            // Bind the texture
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);

            // For UI textures, do less expensive operations:
            // 1. Use simpler filtering instead of mipmapping for UI elements
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

            // 2. Generate mipmaps only for larger textures (optional, can be skipped for UI)
            boolean isBigTexture = false;
            // You could determine texture size here if needed:
            // int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            // int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            // isBigTexture = width > 128 || height > 128;

            if (isBigTexture) {
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);

                // 3. Use limited anisotropy for better performance
                try {
                    float maxAniso = Math.min(MAX_ANISOTROPY,
                            GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT));
                    GL11.glTexParameterf(GL11.GL_TEXTURE_2D,
                            EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, maxAniso);
                } catch (Exception e) {
                    // Anisotropic filtering might not be supported - ignore
                }
            }

            // Restore previous binding
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);

            // Cache this texture as set up
            textureSetupCache.put(texture, true);
        } catch (Exception e) {
            // Gracefully handle missing textures
            textureSetupCache.put(texture, false);
            System.out.println("Error setting up texture: " + e.getMessage());
        }
    }

    /**
     * Clears the texture setup cache - call this when resources are reloaded
     */
    public static void clearCache() {
        textureSetupCache.clear();
    }

    /**
     * Draws a texture at the specified position without clipping
     */

/**
 * Draws a texture at the specified position without clipping
 * This is a simpler alternative to using DrawContext.drawTexture directly
 */
            public static void drawTexture(DrawContext context, Identifier texture,
                                           float x1, float y1, float width1, float v, float x, float y, float width, float height) {
                // Use the layer method in older Minecraft versions which is more compatible
                context.drawTexture(
                        RenderLayer::getGuiTextured,
                        texture,
                        (int)x,
                        (int)y,
                        0, // zOffset
                        0, // uOffset
                        0, // vOffset
                        (int)width,
                        (int)height,
                        (int)width,
                        (int)height
                );

    }
}