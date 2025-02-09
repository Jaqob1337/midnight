package de.peter1337.midnight.render.gui.clickgui.buttons;

import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.shape.Shape;
import de.peter1337.midnight.render.font.CustomFontRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;

public class ClickGuiCategoryButton {
    private final Category category;
    private final Shape button;
    private final CustomFontRenderer fontRenderer;
    private boolean selected;
    private final float initialY;
    private float currentY;

    private static final float BUTTON_WIDTH = 50f;
    private static final float BUTTON_HEIGHT = 50f;
    private static final float BUTTON_RADIUS = 5f;
    private static final Color BUTTON_COLOR = new Color(40, 40, 60, 255);
    private static final Color BUTTON_HOVER_COLOR = new Color(50, 50, 70, 255);
    private static final Color BUTTON_SELECTED_COLOR = new Color(60, 60, 80, 255);
    private static final Color BUTTON_SELECTED_HOVER_COLOR = new Color(70, 70, 90, 255);
    private static final float FONT_SIZE = 10f;

    public ClickGuiCategoryButton(Render2D render2D, Category category, Shape parent, float offsetX, float offsetY) {
        this.category = category;
        this.selected = false;
        this.initialY = offsetY;
        this.currentY = offsetY;
        this.fontRenderer = CustomFontRenderer.getInstanceForSize(FONT_SIZE);

        button = render2D.createRoundedRect(
                0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_RADIUS, BUTTON_COLOR
        );
        button.attachTo(parent, offsetX, offsetY);
    }

    public void render(DrawContext context) {
        if (fontRenderer != null && button.getParent() != null) {
            Shape parent = button.getParent();
            String categoryName = category.name();

            // Calculate text dimensions and position
            float textWidth = fontRenderer.getStringWidth(categoryName);
            float textHeight = fontRenderer.getFontHeight();
            float textX = button.getX() + (BUTTON_WIDTH - textWidth) / 2;
            float textY = button.getY() + (BUTTON_HEIGHT - textHeight) / 2;

            // Get window dimensions
            MinecraftClient mc = MinecraftClient.getInstance();
            double scale = mc.getWindow().getScaleFactor();
            int windowHeight = mc.getWindow().getHeight();

            // Enable scissor test
            RenderSystem.enableScissor(
                    (int)(parent.getX() * scale),
                    (int)(windowHeight - (parent.getY() + parent.getHeight()) * scale),
                    (int)(parent.getWidth() * scale),
                    (int)(parent.getHeight() * scale)
            );

            // Draw text
            fontRenderer.drawStringWithShadow(
                    context.getMatrices(),
                    categoryName,
                    (int)textX,
                    (int)textY,
                    selected ? 0xFFFFFFFF : 0xAAAAAAFF,
                    0x55000000
            );

            // Disable scissor test
            RenderSystem.disableScissor();
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
}