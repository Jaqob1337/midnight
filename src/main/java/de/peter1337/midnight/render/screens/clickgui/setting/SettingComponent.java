package de.peter1337.midnight.render.screens.clickgui.setting;

import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.Render2D.RenderShape;
import de.peter1337.midnight.render.font.CustomFontRenderer;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.render.screens.clickgui.setting.toggle.SliderComponent;
import de.peter1337.midnight.render.screens.clickgui.setting.toggle.ToggleComponent;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.List;

public class SettingComponent {
    private final Setting<?> setting;
    private final RenderShape background;
    private final CustomFontRenderer fontRenderer;
    private ToggleComponent toggleComponent;  // For Boolean settings
    private SliderComponent sliderComponent;    // For numeric settings
    private boolean visible;

    // Layout constants.
    private static final float HEIGHT = 20f;
    private static final float PADDING = 1f;
    private static final Color BG_COLOR = new Color(25, 25, 45, 255);
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private static final float EXTRA_SPACING = 10f; // space between text and control
    private static final float VALUE_DISPLAY_PADDING = 2f;
    private float sliderXOffset = 0f;
    private float totalWidth = 0f;
    private float sliderWidth = 0f;
    private final float valueDisplayWidth;

    public SettingComponent(Render2D render2D, Setting<?> setting, RenderShape parent, float yOffset) {
        this.setting = setting;
        this.fontRenderer = CustomFontRenderer.getInstanceForSize(10f);
        this.visible = false;

        float textWidth = fontRenderer.getStringWidth(setting.getName());
        valueDisplayWidth = fontRenderer.getStringWidth("00.00") + VALUE_DISPLAY_PADDING * 2;
        sliderXOffset = textWidth + EXTRA_SPACING;

        totalWidth = parent.getWidth();
        sliderWidth = totalWidth - (textWidth + EXTRA_SPACING + valueDisplayWidth);

        background = render2D.createRoundedRect(
                parent.getX(),
                parent.getY() + yOffset,
                totalWidth,
                HEIGHT,
                3f,
                TRANSPARENT
        );
        background.attachTo(parent, 0, yOffset);

        if (setting.getValue() instanceof Boolean) {
            float toggleX = background.getX() + totalWidth - ToggleComponent.TOGGLE_WIDTH - PADDING;
            float toggleY = background.getY() + (HEIGHT - ToggleComponent.TOGGLE_HEIGHT) / 2;
            boolean value = (Boolean) setting.getValue();
            toggleComponent = new ToggleComponent(render2D, background, toggleX, toggleY, value);
        } else if (setting.getValue() instanceof Number) {
            float sliderX = background.getX() + sliderXOffset;
            float sliderY = background.getY() + (HEIGHT - SliderComponent.SLIDER_HEIGHT) / 2;
            sliderComponent = new SliderComponent(render2D, background, sliderX, sliderY, sliderWidth, (Setting<? extends Number>) setting);

            setVisible(false);
        }
    }

    public void render(DrawContext context) {
        if (!visible) return;

        fontRenderer.drawStringWithShadow(
                context.getMatrices(),
                setting.getName(),
                (int) (background.getX() + PADDING),
                (int) (background.getY() + (HEIGHT - fontRenderer.getFontHeight()) / 2),
                0xFFFFFFFF,
                0x55000000
        );

        if (toggleComponent != null) {
            toggleComponent.render(context, (Boolean) setting.getValue());
        }
        if (sliderComponent != null && setting.getValue() instanceof Number) {
            float currentValue = ((Number) setting.getValue()).floatValue();
            float fraction = (currentValue - sliderComponent.getMinValue()) / (sliderComponent.getMaxValue() - sliderComponent.getMinValue());
            sliderComponent.render(context, fraction);
        }

    }

    public void updatePosition(float yOffset) {
        if (background != null && background.getParent() != null) {
            background.attachTo(background.getParent(), 0, yOffset);
            if (toggleComponent != null) {
                toggleComponent.updatePosition(background);
            }
            if (sliderComponent != null) {
                float sliderX = background.getX() + sliderXOffset;
                float sliderY = background.getY() + (HEIGHT - SliderComponent.SLIDER_HEIGHT) / 2;
                sliderComponent.updatePosition(background, sliderX, sliderY);
            }

        }
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        background.setFillColor(visible ? BG_COLOR : TRANSPARENT);
        if (toggleComponent != null) {
            toggleComponent.setVisible(visible);
        }
        if (sliderComponent != null) {
            sliderComponent.setVisible(visible);
        }

    }

    public float getHeight() {
        return HEIGHT;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        if (!visible) return false;
        return mouseX >= background.getX() &&
                mouseX <= background.getX() + background.getWidth() &&
                mouseY >= background.getY() &&
                mouseY <= background.getY() + HEIGHT;
    }

    public boolean isToggleHovered(double mouseX, double mouseY) {
        if (!visible || toggleComponent == null) return false;
        return toggleComponent.isHovered(mouseX, mouseY);
    }

    public void onClick(double mouseX, double mouseY) {
        if (!visible) return;
        if (toggleComponent != null && setting.getValue() instanceof Boolean && isToggleHovered(mouseX, mouseY)) {
            boolean currentValue = (Boolean) setting.getValue();
            setting.setValue(!currentValue);
            toggleComponent.startAnimation();
        } else if (sliderComponent != null && setting.getValue() instanceof Number) {
            sliderComponent.onMouseDown(mouseX, mouseY);
        }
    }

    public void onMouseDrag(double mouseX, double mouseY) {
        if (sliderComponent != null && setting.getValue() instanceof Number) {
            sliderComponent.onDrag(mouseX);
        }
    }

    public void onMouseUp(double mouseX, double mouseY) {
        if (sliderComponent != null && setting.getValue() instanceof Number) {
            sliderComponent.onMouseUp(mouseX, mouseY);
        }
    }
}
