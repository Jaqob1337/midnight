package de.peter1337.midnight.render;

import com.mojang.blaze3d.systems.RenderSystem;
import de.peter1337.midnight.render.shader.CustomMSDFShader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class CustomFontRenderer {

    private static CustomFontRenderer instance;

    private final Font baseFont;       // Original font for measurements.
    private final Font highResFont;    // Derived font (scaled by resolutionScale) for atlas generation.
    private final Identifier fontTextureId;

    // Target on-screen cell dimensions (in pixels)
    private final int baseGlyphWidth;    // Fixed cell width for each letter.
    private final int baseGlyphHeight;
    private final int resolutionScale;   // e.g., 2 means atlas is generated at 2x resolution.
    // Actual atlas cell dimensions (in high-res pixels)
    private final int glyphWidth;        // = baseGlyphWidth * resolutionScale
    private final int glyphHeight;       // = baseGlyphHeight * resolutionScale

    private final int charsPerRow;
    private final int firstChar = 32;
    private static final int lastChar = 126;

    private final NativeImage atlas;
    private NativeImageBackedTexture texture;

    // Options: use MSDF and drop shadow.
    private final boolean useMSDF;
    private final boolean dropShadow;
    private final float extraLetterSpacing; // Extra spacing between letters (in base pixels).

    // Per-glyph metrics (in high-res pixels) computed via GlyphVector (logical bounds).
    private final float[] glyphAdvancesHigh;
    private final float[] glyphLeftOffsetsHigh;
    private final float[] glyphNaturalWidthsHigh;

    // Padding (in high-res pixels) added to natural width to avoid clipping.
    private final float glyphPaddingHigh;

    private CustomFontRenderer(Font baseFont, Font highResFont, Identifier fontTextureId, NativeImage atlas,
                               int baseGlyphWidth, int baseGlyphHeight, int resolutionScale, int charsPerRow,
                               boolean useMSDF, boolean dropShadow,
                               float[] glyphAdvancesHigh, float[] glyphLeftOffsetsHigh, float[] glyphNaturalWidthsHigh,
                               float extraLetterSpacing, float glyphPaddingHigh) {
        this.baseFont = baseFont;
        this.highResFont = highResFont;
        this.fontTextureId = fontTextureId;
        this.atlas = atlas;
        this.baseGlyphWidth = baseGlyphWidth;
        this.baseGlyphHeight = baseGlyphHeight;
        this.resolutionScale = resolutionScale;
        this.glyphWidth = baseGlyphWidth * resolutionScale;
        this.glyphHeight = baseGlyphHeight * resolutionScale;
        this.charsPerRow = charsPerRow;
        this.useMSDF = useMSDF;
        this.dropShadow = dropShadow;
        this.glyphAdvancesHigh = glyphAdvancesHigh;
        this.glyphLeftOffsetsHigh = glyphLeftOffsetsHigh;
        this.glyphNaturalWidthsHigh = glyphNaturalWidthsHigh;
        this.extraLetterSpacing = extraLetterSpacing;
        this.glyphPaddingHigh = glyphPaddingHigh;
    }

    public static void init() {
        // Set target on-screen cell height and resolution scale.
        int baseGlyphHeight = 16;
        int resolutionScale = 1; // Generate atlas at 2x resolution.
        // Load your custom font.
        Font baseFont = CustomFontLoader.loadFont("/assets/midnight/fonts/Liter.ttf", 1f);
        // Derive a high-res font for atlas generation.
        Font highResFont = baseFont.deriveFont(12f * resolutionScale);

        // Compute per-glyph metrics for characters 32 to 126 using logical bounds.
        int totalGlyphs = lastChar - 32 + 1;
        float[] advancesHigh = new float[totalGlyphs];
        float[] leftOffsetsHigh = new float[totalGlyphs];
        float[] naturalWidthsHigh = new float[totalGlyphs];

        FontRenderContext frc = new FontRenderContext(null, true, true);
        float sumGlyphWidthHigh = 0f;
        for (int i = 32; i <= lastChar; i++) {
            int index = i - 32;
            GlyphVector gv = highResFont.createGlyphVector(frc, String.valueOf((char) i));
            advancesHigh[index] = (float) gv.getGlyphMetrics(0).getAdvance();
            Rectangle2D bounds = gv.getGlyphLogicalBounds(0).getBounds2D();
            leftOffsetsHigh[index] = (float) -bounds.getX();
            naturalWidthsHigh[index] = (float) bounds.getWidth();
            sumGlyphWidthHigh += (leftOffsetsHigh[index] + naturalWidthsHigh[index]);
        }
        // Compute average glyph width (in high-res pixels).
        float avgGlyphWidthHigh = sumGlyphWidthHigh / totalGlyphs;
        // Set a padding value (in high-res pixels) to avoid clipping.
        float glyphPaddingHigh = 2.0f;
        // Compute the cell width as average width plus padding, converted to base units.
        int computedWidth = (int) Math.ceil((avgGlyphWidthHigh + glyphPaddingHigh) / resolutionScale);
        // Apply a multiplier to shrink the fixed cell width.
        float multiplier = 1.0f; // Change this factor as needed.
        int baseGlyphWidth = (int) (computedWidth * multiplier);

        int charsPerRow = 16;
        int rows = (int) Math.ceil((double) totalGlyphs / charsPerRow);
        int atlasWidth = charsPerRow * baseGlyphWidth * resolutionScale;
        int atlasHeight = rows * baseGlyphHeight * resolutionScale;

        BufferedImage bufferedAtlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bufferedAtlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(highResFont);
        for (int i = 32; i <= lastChar; i++) {
            int index = i - 32;
            int col = index % charsPerRow;
            int row = index / charsPerRow;
            int cellX = col * baseGlyphWidth * resolutionScale;
            int cellY = row * baseGlyphHeight * resolutionScale;
            int drawX = cellX;
            int drawY = cellY + (baseGlyphHeight * resolutionScale) - 4;
            g.drawString(String.valueOf((char) i), drawX, drawY);
        }
        g.dispose();

        // Generate an MSDF atlas from the high-res atlas.
        // Replace this stub with an actual MSDF generator call.
        boolean enableMSDF = true;
        BufferedImage msdfAtlas = generateMSDFAtlas(bufferedAtlas);

        NativeImage atlasImage;
        try {
            atlasImage = bufferedImageToNativeImage(msdfAtlas);
        } catch (IOException e) {
            e.printStackTrace();
            atlasImage = new NativeImage(atlasWidth, atlasHeight, false);
        }

        Identifier textureId = Identifier.of("midnight", "textures/font/myfont_atlas.png");
        boolean enableDropShadow = true;
        // Use minimal extra spacing because spacing is now uniform.
        float extraLetterSpacing = 0f;

        instance = new CustomFontRenderer(baseFont, highResFont, textureId, atlasImage,
                baseGlyphWidth, baseGlyphHeight, resolutionScale, charsPerRow,
                enableMSDF, enableDropShadow,
                advancesHigh, leftOffsetsHigh, naturalWidthsHigh,
                extraLetterSpacing, glyphPaddingHigh);
    }

    public static CustomFontRenderer getInstance() {
        return instance;
    }

    public static NativeImage bufferedImageToNativeImage(BufferedImage bufferedImage) throws IOException {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        NativeImage nativeImage = new NativeImage(width, height, false);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int argb = bufferedImage.getRGB(x, y);
                nativeImage.setColorArgb(x, y, argb);
            }
        }
        return nativeImage;
    }

    /**
     * Stub for generating an MSDF atlas from a high-res BufferedImage.
     * Replace this method with an actual MSDF generator call.
     */
    private static BufferedImage generateMSDFAtlas(BufferedImage source) {
        // For demonstration, return the source image.
        return source;
    }

    private void ensureTextureUploaded() {
        if (this.texture == null) {
            this.texture = new NativeImageBackedTexture(this.atlas);
            MinecraftClient.getInstance().getTextureManager().registerTexture(this.fontTextureId, this.texture);
        }
    }

    /**
     * Draws the given text using uniform fixed spacing.
     * Each letter is drawn in a cell of width baseGlyphWidth, and the glyph is centered in that cell.
     */
    public void drawString(MatrixStack matrices, String text, int x, int y, int packedColor) {
        MinecraftClient client = MinecraftClient.getInstance();
        ensureTextureUploaded();

        if (useMSDF) {
            CustomMSDFShader.getInstance().bind();
        } else {
            RenderSystem.setShaderTexture(0, this.fontTextureId);
        }
        RenderSystem.enableBlend();

        VertexConsumerProvider.Immediate vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getText(this.fontTextureId));

        int fixedAdvance = baseGlyphWidth; // Each letter's box width.

        if (dropShadow) {
            int shadowColor = (packedColor & 0xFEFEFE) >> 1;
            float shadowX = x;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < firstChar || c > lastChar) {
                    shadowX += fixedAdvance;
                    continue;
                }
                int index = c - firstChar;
                int col = index % this.charsPerRow;
                int row = index / this.charsPerRow;
                int cellX = col * fixedAdvance * resolutionScale;
                int cellY = row * baseGlyphHeight * resolutionScale;
                int letterWidth = (int)(glyphNaturalWidthsHigh[index] / resolutionScale);
                int letterOffset = (fixedAdvance - letterWidth) / 2;
                float u0 = (cellX + glyphLeftOffsetsHigh[index]) / (float)this.atlas.getWidth();
                float u1 = (cellX + glyphLeftOffsetsHigh[index] + glyphNaturalWidthsHigh[index]) / (float)this.atlas.getWidth();
                float v0 = cellY / (float)this.atlas.getHeight();
                float v1 = (cellY + glyphHeight) / (float)this.atlas.getHeight();
                renderQuad(consumer, (int)(shadowX + letterOffset + 1), y + 1,
                        letterWidth, baseGlyphHeight,
                        u0, v0, u1, v1, shadowColor);
                shadowX += fixedAdvance;
            }
        }

        float drawX = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < firstChar || c > lastChar) {
                drawX += fixedAdvance;
                continue;
            }
            int index = c - firstChar;
            int col = index % this.charsPerRow;
            int row = index / this.charsPerRow;
            int cellX = col * fixedAdvance * resolutionScale;
            int cellY = row * baseGlyphHeight * resolutionScale;
            int letterWidth = (int)(glyphNaturalWidthsHigh[index] / resolutionScale);
            int letterOffset = (fixedAdvance - letterWidth) / 2;
            float u0 = (cellX + glyphLeftOffsetsHigh[index]) / (float)this.atlas.getWidth();
            float u1 = (cellX + glyphLeftOffsetsHigh[index] + glyphNaturalWidthsHigh[index]) / (float)this.atlas.getWidth();
            float v0 = cellY / (float)this.atlas.getHeight();
            float v1 = (cellY + glyphHeight) / (float)this.atlas.getHeight();
            renderQuad(consumer, (int)(drawX + letterOffset), y,
                    letterWidth, baseGlyphHeight,
                    u0, v0, u1, v1, packedColor);
            drawX += fixedAdvance;
        }

        if (useMSDF) {
            CustomMSDFShader.getInstance().unbind();
        }
        vertexConsumers.draw();
        RenderSystem.disableBlend();
    }

    public int getStringWidth(String text) {
        return text.length() * baseGlyphWidth;
    }

    public int getFontHeight() {
        return baseGlyphHeight;
    }

    private void renderQuad(VertexConsumer consumer, int x, int y, int width, int height,
                            float u0, float v0, float u1, float v1, int packedColor) {
        int fullBright = 0xF000F0;
        int overlay = 0;
        consumer.vertex((float)x, (float)(y + height), 0.0F, packedColor, u0, v1, overlay, fullBright, 0.0F, 0.0F, 0.0F);
        consumer.vertex((float)(x + width), (float)(y + height), 0.0F, packedColor, u1, v1, overlay, fullBright, 0.0F, 0.0F, 0.0F);
        consumer.vertex((float)(x + width), (float)y, 0.0F, packedColor, u1, v0, overlay, fullBright, 0.0F, 0.0F, 0.0F);
        consumer.vertex((float)x, (float)y, 0.0F, packedColor, u0, v0, overlay, fullBright, 0.0F, 0.0F, 0.0F);
    }
}
