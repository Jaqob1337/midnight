package de.peter1337.midnight.render.screens.clickgui.buttons;

import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.Render2D.RenderShape;
import de.peter1337.midnight.Midnight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.RenderLayer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Optional;

public class ClickGuiCategoryButton {
    private final Category category;
    private final RenderShape button;
    private final Identifier iconTexture;
    private boolean selected;
    private final float initialY;
    private float currentY;

    // Layout parameters:
    private final float buttonLine;        // The y-coordinate ("line") where the button is placed.
    private final float gapBetweenButtons; // Horizontal gap between buttons.

    // Button constants
    private static final float BUTTON_WIDTH = 50f;
    private static final float BUTTON_HEIGHT = 50f;
    private static final float BUTTON_RADIUS = 5f;
    private static final Color BUTTON_COLOR = new Color(40, 40, 60, 255);
    private static final Color BUTTON_HOVER_COLOR = new Color(50, 50, 70, 255);
    private static final Color BUTTON_SELECTED_COLOR = new Color(60, 60, 80, 255);
    private static final Color BUTTON_SELECTED_HOVER_COLOR = new Color(70, 70, 90, 255);

    // Natural dimensions (read from the PNG at full resolution)
    private int naturalIconWidth;
    private int naturalIconHeight;

    // The computed scale factor so that the icon (when scaled to its natural size) fits within the button.
    // A relative factor (e.g. 0.9) makes the icon a bit smaller than the full button.
    private float iconScale = 1.0f;

    /**
     * @param render2D          the Render2D instance
     * @param category          the category represented by this button
     * @param parent            the parent RenderShape (typically the background) to attach to and clip against
     * @param offsetX           the initial x-offset for the button
     * @param buttonLine        the y-coordinate (line) for the button
     * @param gapBetweenButtons the horizontal gap between buttons
     * @param relativeScale     the relative scale factor (e.g. 0.9 means the icon is 90% of maximum possible)
     */
    public ClickGuiCategoryButton(Render2D render2D, Category category, RenderShape parent,
                                  float offsetX, float buttonLine, float gapBetweenButtons, float relativeScale) {
        this.category = category;
        this.selected = false;
        this.buttonLine = buttonLine;
        this.gapBetweenButtons = gapBetweenButtons;
        this.initialY = buttonLine;
        this.currentY = buttonLine;

        String categoryName = (category == null || category.name().isEmpty()) ? "unknown" : category.name().toLowerCase();
        this.iconTexture = Identifier.of("midnight", "textures/gui/category/" + categoryName + ".png");

        // Load the image to determine its natural dimensions.
        try {
            Optional<?> optResource = MinecraftClient.getInstance().getResourceManager().getResource(iconTexture);
            if (optResource.isPresent()) {
                try (InputStream stream = ((net.minecraft.resource.Resource) optResource.get()).getInputStream()) {
                    BufferedImage image = ImageIO.read(stream);
                    naturalIconWidth = image.getWidth();
                    naturalIconHeight = image.getHeight();
                }
            } else {
                naturalIconWidth = 24;
                naturalIconHeight = 24;
            }
        } catch (Exception e) {
            e.printStackTrace();
            naturalIconWidth = 24;
            naturalIconHeight = 24;
        }

        // Compute maximum scale so that the icon fits entirely within the button.
        float scaleX = BUTTON_WIDTH / naturalIconWidth;
        float scaleY = BUTTON_HEIGHT / naturalIconHeight;
        float maxScale = Math.min(scaleX, scaleY);
        this.iconScale = maxScale * relativeScale;

        // Create the button shape with fixed size.
        button = render2D.createRoundedRect(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_RADIUS, BUTTON_COLOR);
        button.attachTo(parent, offsetX, buttonLine);

        Midnight.LOGGER.info("Created category button for: {} with texture: {} (natural size: {}x{}, max scale: {}, relative scale: {}, final scale: {}), buttonLine: {}, gap: {}",
                category.name(), iconTexture, naturalIconWidth, naturalIconHeight, maxScale, relativeScale, this.iconScale, buttonLine, gapBetweenButtons);
    }

    public void render(DrawContext context) {
        if (button.getParent() != null) {
            updateColor();
            int drawnWidth = (int) (naturalIconWidth * iconScale);
            int drawnHeight = (int) (naturalIconHeight * iconScale);
            int iconX = (int) (button.getX() + (BUTTON_WIDTH - drawnWidth) / 2);
            int iconY = (int) (button.getY() + (BUTTON_HEIGHT - drawnHeight) / 2);

            // Use parent's bounds for clipping (i.e. the background).
            float clipX = button.getParent().getX();
            float clipY = button.getParent().getY();
            float clipW = button.getParent().getWidth();
            float clipH = button.getParent().getHeight();
            Render2D.beginScissor(clipX, clipY, clipX + clipW, clipY + clipH);

            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.setShaderTexture(0, iconTexture);
            int glId = MinecraftClient.getInstance().getTextureManager().getTexture(iconTexture).getGlId();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            float maxAniso = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, maxAniso);

            context.drawTexture(
                    RenderLayer::getGuiTextured,
                    iconTexture,
                    iconX,
                    iconY,
                    0, // u offset
                    0, // v offset
                    drawnWidth,
                    drawnHeight,
                    drawnWidth,
                    drawnHeight
            );

            RenderSystem.disableBlend();
            Render2D.endScissor();
        }
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        updateColor();
    }

    public void updateColor() {
        Color currentColor;
        if (selected) {
            currentColor = isHovered(0, 0) ? BUTTON_SELECTED_HOVER_COLOR : BUTTON_SELECTED_COLOR;
        } else {
            currentColor = isHovered(0, 0) ? BUTTON_HOVER_COLOR : BUTTON_COLOR;
        }
        button.setFillColor(currentColor);
    }

    public void updatePosition(float scrollOffset) {
        if (button != null && button.getParent() != null) {
            currentY = initialY - scrollOffset;
            button.attachTo(button.getParent(), button.getX() - button.getParent().getX(), currentY);
        }
    }

    public void resetPosition() {
        if (button != null && button.getParent() != null) {
            currentY = initialY;
            button.attachTo(button.getParent(), button.getX() - button.getParent().getX(), initialY);
        }
    }

    /**
     * Shifts the button horizontally by the configured gap.
     */
    public void shiftHorizontallyByGap() {
        float newX = button.getX() + gapBetweenButtons;
        button.setPosition(newX, button.getY());
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return button.isHovered(mouseX, mouseY);
    }

    public Category getCategory() {
        return category;
    }

    public float getY() {
        return button.getY();
    }

    public float getHeight() {
        return BUTTON_HEIGHT;
    }

    public float getWidth() {
        return BUTTON_WIDTH;
    }

    public float getButtonLine() {
        return buttonLine;
    }

    public float getGapBetweenButtons() {
        return gapBetweenButtons;
    }

    private String formatCategoryName(String name) {
        return (name == null || name.isEmpty()) ? "unknown" : name.toLowerCase();
    }
}
