package de.peter1337.midnight.render.gui.clickgui.setting;

import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.shape.Shape;
import de.peter1337.midnight.render.font.CustomFontRenderer;
import de.peter1337.midnight.utils.Setting;
import net.minecraft.client.gui.DrawContext;
import java.awt.Color;

public class SettingComponent {
    private final Setting<?> setting;
    private final Shape background;
    private final CustomFontRenderer fontRenderer;
    private boolean visible;
    private static final float HEIGHT = 15f;
    private static final float PADDING = 5f;
    private static final float TOGGLE_WIDTH = 20f;
    private static final float TOGGLE_HEIGHT = 10f;

    public SettingComponent(Render2D render2D, Setting<?> setting, Shape parent, float yOffset) {
        this.setting = setting;
        this.fontRenderer = CustomFontRenderer.getInstanceForSize(10f);
        this.visible = false;

        background = render2D.createRoundedRect(
                parent.getX(),
                parent.getY() + yOffset,
                parent.getWidth(),
                HEIGHT,
                3f,
                new Color(25, 25, 45, 255)
        );
        background.attachTo(parent, 0, yOffset);
    }

    public void render(DrawContext context) {
        if (!visible) return;

        // Always white text for settings
        fontRenderer.drawStringWithShadow(
                context.getMatrices(),
                setting.getName(),
                (int)(background.getX() + PADDING),
                (int)(background.getY() + (HEIGHT - fontRenderer.getFontHeight()) / 2),
                0xFFFFFFFF,  // Always white
                0x55000000
        );

        // Render setting control based on type
        if (setting.getValue() instanceof Boolean) {
            renderBooleanSetting(context);
        } else if (setting.getValue() instanceof Number) {
            renderNumberSetting(context);
        } else if (setting.getValue() instanceof Enum<?>) {
            renderEnumSetting(context);
        }
    }

    private void renderBooleanSetting(DrawContext context) {
        boolean value = (Boolean) setting.getValue();
        float toggleX = background.getX() + background.getWidth() - TOGGLE_WIDTH - PADDING;
        float toggleY = background.getY() + (HEIGHT - TOGGLE_HEIGHT) / 2;

        // Draw toggle background
        context.fill(
                (int)toggleX,
                (int)toggleY,
                (int)(toggleX + TOGGLE_WIDTH),
                (int)(toggleY + TOGGLE_HEIGHT),
                value ? 0xFF00FF00 : 0xFF555555
        );

        // Draw toggle knob
        float knobX = value ? toggleX + TOGGLE_WIDTH - TOGGLE_HEIGHT : toggleX;
        context.fill(
                (int)knobX,
                (int)toggleY,
                (int)(knobX + TOGGLE_HEIGHT),
                (int)(toggleY + TOGGLE_HEIGHT),
                0xFFFFFFFF
        );
    }

    private void renderNumberSetting(DrawContext context) {
        // TODO: Implement number slider rendering
    }

    private void renderEnumSetting(DrawContext context) {
        // TODO: Implement enum selector rendering
    }

    public void updatePosition(float yOffset) {
        if (background != null && background.getParent() != null) {
            background.attachTo(background.getParent(), 0, yOffset);
        }
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        background.setFillColor(visible ? new Color(25, 25, 45, 255) : new Color(0, 0, 0, 0));
    }

    public float getHeight() {
        return HEIGHT;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return visible && background.isHovered(mouseX, mouseY);
    }

    public void onClick(double mouseX, double mouseY) {
        if (!visible) return;

        if (setting.getValue() instanceof Boolean) {
            boolean value = (Boolean) setting.getValue();
            setting.setValue(!value);
        }
        // TODO: Handle other setting types
    }
}