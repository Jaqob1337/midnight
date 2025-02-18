package de.peter1337.midnight.render.screens.clickgui.setting.toggle;

import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.Render2D.RenderShape;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class ToggleComponent {
    private final RenderShape toggleTrack;
    private final RenderShape toggleKnob;

    // Animation fields
    private float animationProgress = 0f;
    private boolean animating = false;
    private long lastUpdateTime;
    private static final float ANIMATION_DURATION = 150f; // in milliseconds

    // Dimensions (exactly as in the old version)
    public static final float TOGGLE_WIDTH = 28f;
    public static final float TOGGLE_HEIGHT = 14f;
    public static final float KNOB_SIZE = 12f;
    private static final float PADDING = 5f;
    private static final float SETTING_HEIGHT = 20f; // from the original SettingComponent

    // Colors
    private static final Color TOGGLE_OFF_COLOR = new Color(45, 45, 65, 255);
    private static final Color TOGGLE_ON_COLOR = new Color(0, 255, 128, 255);
    private static final Color KNOB_COLOR = new Color(255, 255, 255, 255);
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    public ToggleComponent(Render2D render2D, RenderShape background, float toggleX, float toggleY, boolean initialValue) {
        // Initialize animation progress based on current value.
        animationProgress = initialValue ? 1f : 0f;

        // Create toggle track at the given coordinates.
        toggleTrack = render2D.createRoundedRect(
                toggleX,
                toggleY,
                TOGGLE_WIDTH,
                TOGGLE_HEIGHT,
                TOGGLE_HEIGHT / 2,
                TOGGLE_OFF_COLOR
        );
        // Attach the toggle track relative to the setting background.
        toggleTrack.attachTo(background, background.getWidth() - TOGGLE_WIDTH - PADDING, (SETTING_HEIGHT - TOGGLE_HEIGHT) / 2);

        // Create toggle knob with its initial position.
        float knobOffset = getKnobOffset();
        toggleKnob = render2D.createCircle(
                toggleX + knobOffset + KNOB_SIZE / 2,
                toggleY + (TOGGLE_HEIGHT - KNOB_SIZE) / 2 + KNOB_SIZE / 2,
                KNOB_SIZE / 2,
                KNOB_COLOR
        );
        toggleKnob.attachTo(toggleTrack, knobOffset, (TOGGLE_HEIGHT - KNOB_SIZE) / 2);
    }

    private float getKnobOffset() {
        return 1f + (TOGGLE_WIDTH - KNOB_SIZE - 2f) * animationProgress;
    }

    private Color interpolateColor(Color from, Color to, float progress) {
        int r = (int) (from.getRed() + (to.getRed() - from.getRed()) * progress);
        int g = (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * progress);
        int b = (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * progress);
        return new Color(r, g, b, from.getAlpha());
    }

    /**
     * Call this each render tick. This updates any ongoing animation and always updates the track color
     * and knob position so that the knob (dot) is always rendered.
     */
    public void render(DrawContext context, boolean targetOn) {
        if (animating) {
            long currentTime = System.currentTimeMillis();
            float deltaTime = (currentTime - lastUpdateTime) / ANIMATION_DURATION;
            lastUpdateTime = currentTime;

            if (targetOn && animationProgress < 1f) {
                animationProgress = Math.min(1f, animationProgress + deltaTime);
            } else if (!targetOn && animationProgress > 0f) {
                animationProgress = Math.max(0f, animationProgress - deltaTime);
            } else {
                animating = false;
            }
        }
        // Always update track color and knob position
        Color trackColor = interpolateColor(TOGGLE_OFF_COLOR, TOGGLE_ON_COLOR, animationProgress);
        toggleTrack.setFillColor(trackColor);
        float knobOffset = getKnobOffset();
        toggleKnob.attachTo(toggleTrack, knobOffset, (TOGGLE_HEIGHT - KNOB_SIZE) / 2);
    }

    /**
     * Repositions the toggle (track and knob) relative to the setting background.
     */
    public void updatePosition(RenderShape background) {
        toggleTrack.attachTo(background, background.getWidth() - TOGGLE_WIDTH - PADDING, (SETTING_HEIGHT - TOGGLE_HEIGHT) / 2);
        float knobOffset = getKnobOffset();
        toggleKnob.attachTo(toggleTrack, knobOffset, (TOGGLE_HEIGHT - KNOB_SIZE) / 2);
    }

    /**
     * Sets the toggle’s visibility. When visible is true, the track and knob are restored to their normal colors.
     */
    public void setVisible(boolean visible) {
        if (visible) {
            Color trackColor = interpolateColor(TOGGLE_OFF_COLOR, TOGGLE_ON_COLOR, animationProgress);
            toggleTrack.setFillColor(trackColor);
            toggleKnob.setFillColor(KNOB_COLOR);
        } else {
            toggleTrack.setFillColor(TRANSPARENT);
            toggleKnob.setFillColor(TRANSPARENT);
        }
    }

    /**
     * Checks if the mouse is over the toggle track.
     */
    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= toggleTrack.getX() &&
                mouseX <= toggleTrack.getX() + TOGGLE_WIDTH &&
                mouseY >= toggleTrack.getY() &&
                mouseY <= toggleTrack.getY() + TOGGLE_HEIGHT;
    }

    /**
     * Starts the toggle animation (used when the value is changed).
     */
    public void startAnimation() {
        animating = true;
        lastUpdateTime = System.currentTimeMillis();
    }
}
