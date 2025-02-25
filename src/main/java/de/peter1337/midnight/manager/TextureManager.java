package de.peter1337.midnight.manager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.RenderLayer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;

import java.util.HashMap;
import java.util.Map;

public class TextureManager {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Map<Identifier, Boolean> textureSetupCache = new HashMap<>();

    /**
     * Draws a texture within the bounds of a clipping rectangle.
     */
    public static void drawClippedTexture(DrawContext context, Identifier texture,
                                          float x, float y, float width, float height,
                                          float clipX, float clipY, float clipWidth, float clipHeight) {
        // Check if the texture is completely outside the clip bounds.
        if (x >= clipX + clipWidth || y >= clipY + clipHeight ||
                x + width <= clipX || y + height <= clipY) {
            return;
        }

        // Calculate visible portion in float precision.
        float visibleX = Math.max(x, clipX);
        float visibleY = Math.max(y, clipY);
        float visibleWidth = Math.min(x + width, clipX + clipWidth) - visibleX;
        float visibleHeight = Math.min(y + height, clipY + clipHeight) - visibleY;

        // Calculate UV coordinates for the visible portion.
        float u1 = (visibleX - x) / width;
        float v1 = (visibleY - y) / height;
        float u2 = u1 + (visibleWidth / width);
        float v2 = v1 + (visibleHeight / height);

        // Enable blending and set shader.
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, texture);

        // Setup texture filtering parameters once.
        if (!textureSetupCache.containsKey(texture)) {
            int glId = mc.getTextureManager().getTexture(texture).getGlId();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
            // Generate mipmaps once.
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            float maxAniso = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, maxAniso);
            textureSetupCache.put(texture, true);
        }

        // Use rounding for drawn coordinates.
        int drawX = (int) Math.floor(visibleX);
        int drawY = (int) Math.floor(visibleY);
        int drawWidth = (int) Math.ceil(visibleWidth);
        int drawHeight = (int) Math.ceil(visibleHeight);

        // Draw the visible portion of the texture.
        context.drawTexture(
                RenderLayer::getGuiTextured,
                texture,
                drawX,
                drawY,
                (int) (u1 * width),  // u offset
                (int) (v1 * height), // v offset
                drawWidth,
                drawHeight,
                (int) width,
                (int) height
        );

        RenderSystem.disableBlend();
    }
}
