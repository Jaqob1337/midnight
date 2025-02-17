package de.peter1337.midnight.render.screens.clickgui.buttons;

import de.peter1337.midnight.manager.TextureManager;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.Render2D.RenderShape;
import de.peter1337.midnight.Midnight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

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

    // Layout parameters
    private final float buttonLine;
    private final float gapBetweenButtons;

    // Button constants
    private static final float BUTTON_WIDTH = 50f;
    private static final float BUTTON_HEIGHT = 50f;
    private static final float BUTTON_RADIUS = 5f;
    private static final Color BUTTON_COLOR = new Color(40, 40, 60, 255);
    private static final Color BUTTON_HOVER_COLOR = new Color(50, 50, 70, 255);
    private static final Color BUTTON_SELECTED_COLOR = new Color(60, 60, 80, 255);
    private static final Color BUTTON_SELECTED_HOVER_COLOR = new Color(70, 70, 90, 255);

    // Icon dimensions
    private int naturalIconWidth;
    private int naturalIconHeight;
    private float iconScale = 1.0f;

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

        // Load image dimensions
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

        // Calculate icon scale
        float scaleX = BUTTON_WIDTH / naturalIconWidth;
        float scaleY = BUTTON_HEIGHT / naturalIconHeight;
        float maxScale = Math.min(scaleX, scaleY);
        this.iconScale = maxScale * relativeScale;

        // Create button shape
        button = render2D.createRoundedRect(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_RADIUS, BUTTON_COLOR);
        button.attachTo(parent, offsetX, buttonLine);
    }

    public void render(DrawContext context) {
        if (button.getParent() != null) {
            updateColor();

            // Calculate icon dimensions and position
            int drawnWidth = (int)(naturalIconWidth * iconScale);
            int drawnHeight = (int)(naturalIconHeight * iconScale);
            float iconX = button.getX() + (BUTTON_WIDTH - drawnWidth) / 2;
            float iconY = button.getY() + (BUTTON_HEIGHT - drawnHeight) / 2;

            // Find main background panel for clipping
            RenderShape background = button.getParent();
            while (background.getParent() != null) {
                background = background.getParent();
            }

            // Draw the icon with clipping against the background bounds
            TextureManager.drawClippedTexture(
                    context,
                    iconTexture,
                    iconX,
                    iconY,
                    drawnWidth,
                    drawnHeight,
                    background.getX(),
                    background.getY(),
                    background.getWidth(),
                    background.getHeight()
            );
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

    public void shiftHorizontallyByGap() {
        float newX = button.getX() + gapBetweenButtons;
        button.setPosition(newX, button.getY());
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return button.isHovered(mouseX, mouseY);
    }

    // Getters
    public Category getCategory() { return category; }
    public float getY() { return button.getY(); }
    public float getHeight() { return BUTTON_HEIGHT; }
    public float getWidth() { return BUTTON_WIDTH; }
    public float getButtonLine() { return buttonLine; }
    public float getGapBetweenButtons() { return gapBetweenButtons; }
}