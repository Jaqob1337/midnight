package de.peter1337.midnight.render.screens.clickgui.setting;

import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.Render2D.RenderShape;
import de.peter1337.midnight.render.font.CustomFontRenderer;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.render.screens.clickgui.setting.toggle.SliderComponent;
import de.peter1337.midnight.render.screens.clickgui.setting.toggle.ToggleComponent;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

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

    // For slider settings.
    // Removed fixed slider track width; it will be computed dynamically.
    private static final float EXTRA_SPACING = 10f; // space between text and slider track
    private static final float VALUE_DISPLAY_PADDING = 2f;
    private float sliderXOffset = 0f;
    private float totalWidth = 0f;
    private float sliderWidth = 0f; // computed dynamically based on parent width.
    private final float valueDisplayWidth;

    /**
     * Constructs a SettingComponent.
     *
     * @param render2D the Render2D instance
     * @param setting  the setting to represent
     * @param parent   the parent RenderShape (module button’s background)
     * @param yOffset  the vertical offset relative to parent
     */
    public SettingComponent(Render2D render2D, Setting<?> setting, RenderShape parent, float yOffset) {
        this.setting = setting;
        this.fontRenderer = CustomFontRenderer.getInstanceForSize(10f);
        this.visible = false;

        // Compute the width for the setting name.
        float textWidth = fontRenderer.getStringWidth(setting.getName());
        valueDisplayWidth = fontRenderer.getStringWidth("00.00") + VALUE_DISPLAY_PADDING * 2;
        sliderXOffset = textWidth + EXTRA_SPACING;

        // Use the parent's width as the total available width.
        totalWidth = parent.getWidth(); // For example, 230f if that's the module button width.
        // Compute the slider width so that the entire component spans the parent's width:
        // totalWidth = textWidth + EXTRA_SPACING + sliderWidth + valueDisplayWidth.
        sliderWidth = totalWidth - (textWidth + EXTRA_SPACING + valueDisplayWidth);

        // Create the background for this setting row.
        background = render2D.createRoundedRect(
                parent.getX(),
                parent.getY() + yOffset,
                totalWidth,
                HEIGHT,
                3f,
                TRANSPARENT
        );
        background.attachTo(parent, 0, yOffset);

        // Create control based on the setting type.
        if (setting.getValue() instanceof Boolean) {
            float toggleX = background.getX() + totalWidth - ToggleComponent.TOGGLE_WIDTH - PADDING;
            float toggleY = background.getY() + (HEIGHT - ToggleComponent.TOGGLE_HEIGHT) / 2;
            boolean value = (Boolean) setting.getValue();
            toggleComponent = new ToggleComponent(render2D, background, toggleX, toggleY, value);
        } else if (setting.getValue() instanceof Number) {
            // Optionally adjust min/max values if needed.
            float sliderX = background.getX() + sliderXOffset;
            float sliderY = background.getY() + (HEIGHT - SliderComponent.SLIDER_HEIGHT) / 2;
            sliderComponent = new SliderComponent(render2D, background, sliderX, sliderY, sliderWidth, (Setting<? extends Number>) setting);
        }

        setVisible(false);
    }

    /**
     * Renders the setting’s name and control.
     */
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
            // Calculate fraction from current value, assuming slider component handles clamping.
            float fraction = (currentValue - sliderComponent.getMinValue()) / (sliderComponent.getMaxValue() - sliderComponent.getMinValue());
            sliderComponent.render(context, fraction);
        }
    }

    /**
     * Updates this setting’s position relative to its parent.
     *
     * @param yOffset new vertical offset
     */
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

    /**
     * Sets the visibility of this setting.
     */
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

    /**
     * Returns the height of this setting.
     */
    public float getHeight() {
        return HEIGHT;
    }

    /**
     * Checks if the mouse is over the setting background.
     */
    public boolean isHovered(double mouseX, double mouseY) {
        if (!visible) return false;
        return mouseX >= background.getX() &&
                mouseX <= background.getX() + background.getWidth() &&
                mouseY >= background.getY() &&
                mouseY <= background.getY() + HEIGHT;
    }

    /**
     * Checks if the mouse is over the toggle control.
     */
    public boolean isToggleHovered(double mouseX, double mouseY) {
        if (!visible || toggleComponent == null) return false;
        return toggleComponent.isHovered(mouseX, mouseY);
    }

    /**
     * Called when the setting is clicked.
     */
    public void onClick(double mouseX, double mouseY) {
        if (!visible) return;
        if (setting.getValue() instanceof Boolean && toggleComponent != null && isToggleHovered(mouseX, mouseY)) {
            boolean currentValue = (Boolean) setting.getValue();
            setting.setValue(!currentValue);
            toggleComponent.startAnimation();
        } else if (setting.getValue() instanceof Number && sliderComponent != null) {
            sliderComponent.onMouseDown(mouseX, mouseY);
        }
    }

    /**
     * Called when the mouse is dragged.
     */
    public void onMouseDrag(double mouseX, double mouseY) {
        if (setting.getValue() instanceof Number && sliderComponent != null) {
            sliderComponent.onDrag(mouseX);
        }
    }

    /**
     * Called when the mouse is released.
     */
    public void onMouseUp(double mouseX, double mouseY) {
        if (setting.getValue() instanceof Number && sliderComponent != null) {
            sliderComponent.onMouseUp(mouseX, mouseY);
        }
    }
}
