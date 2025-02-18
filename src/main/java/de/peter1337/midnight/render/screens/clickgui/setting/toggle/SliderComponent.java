package de.peter1337.midnight.render.screens.clickgui.setting.toggle;

import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.Render2D.RenderShape;
import de.peter1337.midnight.render.font.CustomFontRenderer;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class SliderComponent {
    private final RenderShape sliderTrack;
    private final RenderShape sliderKnob;

    // Dimensions (feel free to adjust these values)
    public static final float SLIDER_WIDTH = 100f;
    public static final float SLIDER_HEIGHT = 8f;
    public static final float KNOB_SIZE = 16f;
    private static final float PADDING = 5f;
    private static final float SETTING_HEIGHT = 20f; // same as in your toggle

    // Colors
    private static final Color TRACK_BACKGROUND_COLOR = new Color(45, 45, 65, 255);
    private static final Color KNOB_COLOR = new Color(255, 255, 255, 255);
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    private static final Color VALUE_TEXT_COLOR = new Color(255, 255, 255, 255);

    // Numeric value bounds for the slider.
    private final float minValue;
    private final float maxValue;

    // Animation fields: we animate changes from currentValue toward targetValue (both are fractions 0f to 1f).
    private float currentValue; // current fraction value
    private float targetValue;  // target fraction value
    private boolean animating = false;
    private long lastUpdateTime;
    private static final float ANIMATION_DURATION = 150f; // in milliseconds

    // Font renderer to draw the current numeric value.
    private final CustomFontRenderer fontRenderer = CustomFontRenderer.getInstanceForSize(10f);

    /**
     * Constructs a slider component.
     *
     * @param render2D   The Render2D instance used for drawing.
     * @param background The background shape to which the slider is attached.
     * @param sliderX    The initial X coordinate for the slider track.
     * @param sliderY    The initial Y coordinate for the slider track.
     * @param minValue   The numeric minimum value.
     * @param maxValue   The numeric maximum value.
     */
    public SliderComponent(Render2D render2D, RenderShape background, float sliderX, float sliderY, float minValue, float maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        // Start at the minimum value (fraction = 0). You can adjust this if needed.
        this.currentValue = 0f;
        this.targetValue = 0f;

        // Create the slider track as a rounded rectangle.
        sliderTrack = render2D.createRoundedRect(
                sliderX,
                sliderY,
                SLIDER_WIDTH,
                SLIDER_HEIGHT,
                SLIDER_HEIGHT / 2,
                TRACK_BACKGROUND_COLOR
        );
        // Attach relative to the provided background.
        sliderTrack.attachTo(background, sliderX - background.getX(), sliderY - background.getY());

        // Create the slider knob as a circle.
        float knobOffset = getKnobOffset();
        sliderKnob = render2D.createCircle(
                sliderX + knobOffset + KNOB_SIZE / 2,
                sliderY + (SLIDER_HEIGHT - KNOB_SIZE) / 2 + KNOB_SIZE / 2,
                KNOB_SIZE / 2,
                KNOB_COLOR
        );
        sliderKnob.attachTo(sliderTrack, knobOffset, (SLIDER_HEIGHT - KNOB_SIZE) / 2);
    }

    /**
     * Computes the horizontal offset for the knob based on the current fraction value.
     */
    private float getKnobOffset() {
        // The knob can move from 0 to (SLIDER_WIDTH - KNOB_SIZE)
        return currentValue * (SLIDER_WIDTH - KNOB_SIZE);
    }

    /**
     * Sets the slider's target fraction (0f to 1f).
     *
     * @param value The new target fraction.
     */
    public void setValue(float value) {
        // Clamp the value.
        value = Math.max(0f, Math.min(1f, value));
        if (value != targetValue) {
            targetValue = value;
            animating = true;
            lastUpdateTime = System.currentTimeMillis();
        }
    }

    /**
     * Returns the current target fraction of the slider.
     */
    public float getValue() {
        return targetValue;
    }

    /**
     * Called during mouse dragging. Computes a new fraction value from the mouse X position.
     *
     * @param mouseX The current mouse X coordinate.
     */
    public void onDrag(double mouseX) {
        // Get the slider track's absolute X coordinate.
        float trackX = sliderTrack.getX();
        float fraction = (float)((mouseX - trackX) / (SLIDER_WIDTH - KNOB_SIZE));
        fraction = Math.max(0f, Math.min(1f, fraction));
        setValue(fraction);
    }

    /**
     * Renders the slider. The provided fraction (0 to 1) is used to update the target if it differs.
     * Also draws the current numeric value (computed from min/max) at the end of the slider.
     *
     * @param context  The DrawContext.
     * @param fraction The desired slider fraction (0 to 1) from the setting.
     */
    public void render(DrawContext context, float fraction) {
        // Update target value if necessary.
        if (fraction != targetValue) {
            setValue(fraction);
        }

        if (animating) {
            long currentTime = System.currentTimeMillis();
            float deltaTime = (currentTime - lastUpdateTime) / ANIMATION_DURATION;
            lastUpdateTime = currentTime;

            if (targetValue > currentValue) {
                currentValue = Math.min(targetValue, currentValue + deltaTime);
            } else if (targetValue < currentValue) {
                currentValue = Math.max(targetValue, currentValue - deltaTime);
            } else {
                animating = false;
            }
        }
        // Update the knob's position based on the current fraction.
        float knobOffset = getKnobOffset();
        sliderKnob.attachTo(sliderTrack, knobOffset, (SLIDER_HEIGHT - KNOB_SIZE) / 2);

        // Draw the current numeric value at the right of the slider.
        float actualValue = minValue + currentValue * (maxValue - minValue);
        String valueText = String.format("%.2f", actualValue);
        int textX = (int) (sliderTrack.getX() + SLIDER_WIDTH + 2);
        int textY = (int) (sliderTrack.getY() + (SLIDER_HEIGHT - fontRenderer.getFontHeight()) / 2);
        fontRenderer.drawStringWithShadow(context.getMatrices(), valueText, textX, textY, VALUE_TEXT_COLOR.getRGB(), 0x55000000);
    }

    /**
     * Repositions the slider track and knob relative to the given background.
     *
     * @param background The new parent shape.
     * @param sliderX    The new X coordinate.
     * @param sliderY    The new Y coordinate.
     */
    public void updatePosition(RenderShape background, float sliderX, float sliderY) {
        sliderTrack.attachTo(background, sliderX - background.getX(), sliderY - background.getY());
        float knobOffset = getKnobOffset();
        sliderKnob.attachTo(sliderTrack, knobOffset, (SLIDER_HEIGHT - KNOB_SIZE) / 2);
    }

    /**
     * Sets the slider’s visibility. When visible is true, the track and knob are restored to their normal colors.
     *
     * @param visible True to make the slider visible.
     */
    public void setVisible(boolean visible) {
        if (visible) {
            sliderTrack.setFillColor(TRACK_BACKGROUND_COLOR);
            sliderKnob.setFillColor(KNOB_COLOR);
        } else {
            sliderTrack.setFillColor(TRANSPARENT);
            sliderKnob.setFillColor(TRANSPARENT);
        }
    }

    /**
     * Checks if the mouse is over the slider track.
     *
     * @param mouseX The X coordinate of the mouse.
     * @param mouseY The Y coordinate of the mouse.
     * @return True if hovered.
     */
    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= sliderTrack.getX() &&
                mouseX <= sliderTrack.getX() + SLIDER_WIDTH &&
                mouseY >= sliderTrack.getY() &&
                mouseY <= sliderTrack.getY() + SLIDER_HEIGHT;
    }

    /**
     * Checks if the mouse is over the slider knob.
     *
     * @param mouseX   The X coordinate of the mouse.
     * @param mouseY   The Y coordinate of the mouse.
     * @param fraction The current slider fraction (0 to 1) used to compute knob position.
     * @return True if the mouse is over the knob.
     */
    public boolean isKnobHovered(double mouseX, double mouseY, float fraction) {
        float knobOffset = fraction * (SLIDER_WIDTH - KNOB_SIZE);
        float knobX = sliderTrack.getX() + knobOffset;
        float knobY = sliderTrack.getY() + (SLIDER_HEIGHT - KNOB_SIZE) / 2;
        return mouseX >= knobX && mouseX <= knobX + KNOB_SIZE &&
                mouseY >= knobY && mouseY <= knobY + KNOB_SIZE;
    }
}
