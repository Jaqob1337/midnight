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
    private SliderComponent sliderComponent;  // For numeric settings
    private boolean visible;

    // Layout constants
    private static final float HEIGHT = 20f;
    private static final float PADDING = 5f;
    private static final Color BG_COLOR = new Color(25, 25, 45, 255);
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    // For slider settings, obtain min and max from the setting
    private float sliderMin = 0f;
    private float sliderMax = 1f;

    // For slider drag support
    private boolean draggingSlider = false;

    /**
     * Constructs a SettingComponent.
     *
     * @param render2D the Render2D instance
     * @param setting  the setting to represent
     * @param parent   the parent RenderShape (use a container large enough for the slider)
     * @param yOffset  the vertical offset (relative to parent)
     */
    public SettingComponent(Render2D render2D, Setting<?> setting, RenderShape parent, float yOffset) {
        this.setting = setting;
        this.fontRenderer = CustomFontRenderer.getInstanceForSize(10f);
        this.visible = false;

        // Create background for the setting row.
        background = render2D.createRoundedRect(
                parent.getX(),
                parent.getY() + yOffset,
                parent.getWidth(),
                HEIGHT,
                3f,
                TRANSPARENT
        );
        background.attachTo(parent, 0, yOffset);

        // Determine which control to create.
        if (setting.getValue() instanceof Boolean) {
            // Create a toggle control.
            float toggleX = parent.getX() + parent.getWidth() - ToggleComponent.TOGGLE_WIDTH - PADDING;
            float toggleY = parent.getY() + yOffset + (HEIGHT - ToggleComponent.TOGGLE_HEIGHT) / 2;
            boolean value = (Boolean) setting.getValue();
            toggleComponent = new ToggleComponent(render2D, background, toggleX, toggleY, value);
            System.out.println("SettingComponent: Created ToggleComponent for " + setting.getName());
        } else if (setting.getValue() instanceof Number) {
            // For number settings, try to get the min and max.
            if (setting.getMinValue() != null && setting.getMaxValue() != null) {
                sliderMin = ((Number) setting.getMinValue()).floatValue();
                sliderMax = ((Number) setting.getMaxValue()).floatValue();
            }
            // Create the slider control.
            // IMPORTANT: Attach the slider to a larger parent (e.g., the ClickGuiBackground or module section)
            // so it isn't clipped by a small container.
            float sliderX = parent.getX() + PADDING;  // Adjust as needed.
            float sliderY = parent.getY() + yOffset + (HEIGHT - SliderComponent.SLIDER_HEIGHT) / 2;
            sliderComponent = new SliderComponent(render2D, parent, sliderX, sliderY, sliderMin, sliderMax);
            System.out.println("SettingComponent: Created SliderComponent for " + setting.getName());
        }

        setVisible(false);
    }

    /**
     * Renders the setting: draws its name on the left and the control (toggle or slider) on the right.
     */
    public void render(DrawContext context) {
        if (!visible) return;

        // Draw setting name.
        fontRenderer.drawStringWithShadow(
                context.getMatrices(),
                setting.getName(),
                (int) (background.getX() + PADDING),
                (int) (background.getY() + (HEIGHT - fontRenderer.getFontHeight()) / 2),
                0xFFFFFFFF,
                0x55000000
        );

        // Render the control.
        if (toggleComponent != null) {
            toggleComponent.render(context, (Boolean) setting.getValue());
        }
        if (sliderComponent != null && setting.getValue() instanceof Number) {
            float currentValue = ((Number) setting.getValue()).floatValue();
            float fraction = (currentValue - sliderMin) / (sliderMax - sliderMin);
            sliderComponent.render(context, fraction);
        }
    }

    /**
     * Updates the position of this setting relative to its parent container.
     *
     * @param yOffset the new vertical offset (relative to parent)
     */
    public void updatePosition(float yOffset) {
        if (background != null && background.getParent() != null) {
            background.attachTo(background.getParent(), 0, yOffset);
            if (toggleComponent != null) {
                toggleComponent.updatePosition(background);
            }
            if (sliderComponent != null) {
                float sliderX = background.getX() + PADDING;
                float sliderY = background.getY() + (HEIGHT - SliderComponent.SLIDER_HEIGHT) / 2;
                sliderComponent.updatePosition(background, sliderX, sliderY);
            }
        }
    }

    /**
     * Sets the visibility of this setting and its control.
     *
     * @param visible if true, the setting is visible.
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
     * Returns true if the mouse is over the setting's background.
     */
    public boolean isHovered(double mouseX, double mouseY) {
        if (!visible) return false;
        return mouseX >= background.getX() &&
                mouseX <= background.getX() + background.getWidth() &&
                mouseY >= background.getY() &&
                mouseY <= background.getY() + HEIGHT;
    }

    /**
     * Returns true if the mouse is over the toggle control.
     */
    public boolean isToggleHovered(double mouseX, double mouseY) {
        if (!visible || toggleComponent == null) return false;
        return toggleComponent.isHovered(mouseX, mouseY);
    }

    /**
     * Called on mouse click.
     * For toggles, clicking flips the Boolean value.
     * For sliders, clicking on the track updates the numeric value.
     */
    public void onClick(double mouseX, double mouseY) {
        if (!visible) return;

        if (setting.getValue() instanceof Boolean && toggleComponent != null && isToggleHovered(mouseX, mouseY)) {
            boolean currentValue = (Boolean) setting.getValue();
            setting.setValue(!currentValue);
            toggleComponent.startAnimation();
        }

        if (setting.getValue() instanceof Number && sliderComponent != null) {
            // Compute new value based on mouse X relative to slider track.
            float sliderX = background.getX() + PADDING;
            float fraction = (float) ((mouseX - sliderX) / (SliderComponent.SLIDER_WIDTH - SliderComponent.KNOB_SIZE));
            fraction = Math.max(0f, Math.min(1f, fraction));
            float newValue = sliderMin + fraction * (sliderMax - sliderMin);
            setting.setValue(newValue);
        }
    }

    // --- Slider Drag Support ---

    /**
     * Called when the mouse is pressed.
     * For numeric settings, checks if the mouse is over the slider knob.
     */
    public void mousePressed(double mouseX, double mouseY) {
        if (!visible) return;
        if (setting.getValue() instanceof Number && sliderComponent != null) {
            float sliderX = background.getX() + PADDING;
            float currentValue = ((Number) setting.getValue()).floatValue();
            float fraction = (currentValue - sliderMin) / (sliderMax - sliderMin);
            if (sliderComponent.isKnobHovered(mouseX, mouseY, fraction)) {
                draggingSlider = true;
            }
        }
    }

    /**
     * Called when the mouse is dragged.
     * Updates the slider value as the mouse moves.
     */
    public void onDrag(double mouseX, double mouseY) {
        if (!visible) return;
        if (draggingSlider && setting.getValue() instanceof Number && sliderComponent != null) {
            sliderComponent.onDrag(mouseX);
            float fraction = sliderComponent.getValue();
            float newValue = sliderMin + fraction * (sliderMax - sliderMin);
            setting.setValue(newValue);
        }
    }

    /**
     * Called when the mouse button is released.
     * Stops slider dragging.
     */
    public void mouseReleased() {
        draggingSlider = false;
    }
}
