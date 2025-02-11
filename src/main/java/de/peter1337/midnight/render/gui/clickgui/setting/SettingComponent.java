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
    private final Shape toggleTrack;
    private final Shape toggleKnob;
    private final CustomFontRenderer fontRenderer;
    private boolean visible;

    // Animation fields
    private float animationProgress = 0f;
    private boolean animating = false;
    private long lastUpdateTime;
    private static final float ANIMATION_DURATION = 150f; // Duration in milliseconds

    private static final float HEIGHT = 20f;
    private static final float PADDING = 5f;
    private static final float TOGGLE_WIDTH = 28f;
    private static final float TOGGLE_HEIGHT = 14f;
    private static final float KNOB_SIZE = 12f;

    // Colors
    private static final Color BG_COLOR = new Color(25, 25, 45, 255);
    private static final Color TOGGLE_OFF_COLOR = new Color(45, 45, 65, 255);
    private static final Color TOGGLE_ON_COLOR = new Color(0, 255, 128, 255);
    private static final Color KNOB_COLOR = new Color(255, 255, 255, 255);
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    public SettingComponent(Render2D render2D, Setting<?> setting, Shape parent, float yOffset) {
        this.setting = setting;
        this.fontRenderer = CustomFontRenderer.getInstanceForSize(10f);
        this.visible = false;

        // Create main background
        background = render2D.createRoundedRect(
                parent.getX(),
                parent.getY() + yOffset,
                parent.getWidth(),
                HEIGHT,
                3f,
                TRANSPARENT
        );
        background.attachTo(parent, 0, yOffset);

        // For boolean settings, create toggle components
        if (setting.getValue() instanceof Boolean) {
            float toggleX = parent.getX() + parent.getWidth() - TOGGLE_WIDTH - PADDING;
            float toggleY = parent.getY() + yOffset + (HEIGHT - TOGGLE_HEIGHT) / 2;
            boolean value = (Boolean) setting.getValue();

            // Initialize animation progress based on current value
            animationProgress = value ? 1f : 0f;

            // Create toggle track
            toggleTrack = render2D.createRoundedRect(
                    toggleX,
                    toggleY,
                    TOGGLE_WIDTH,
                    TOGGLE_HEIGHT,
                    TOGGLE_HEIGHT / 2,
                    TOGGLE_OFF_COLOR
            );
            toggleTrack.attachTo(background, parent.getWidth() - TOGGLE_WIDTH - PADDING, (HEIGHT - TOGGLE_HEIGHT) / 2);

            // Create toggle knob with initial position
            float knobOffset = getKnobOffset();
            toggleKnob = render2D.createCircle(
                    toggleX + knobOffset + KNOB_SIZE / 2,
                    toggleY + (TOGGLE_HEIGHT - KNOB_SIZE) / 2 + KNOB_SIZE / 2,
                    KNOB_SIZE / 2,
                    KNOB_COLOR
            );
            toggleKnob.attachTo(toggleTrack, knobOffset, (TOGGLE_HEIGHT - KNOB_SIZE) / 2);
        } else {
            toggleTrack = null;
            toggleKnob = null;
        }

        setVisible(false);
    }

    private float getKnobOffset() {
        return 1f + (TOGGLE_WIDTH - KNOB_SIZE - 2f) * animationProgress;
    }

    private Color interpolateColor(Color color1, Color color2, float progress) {
        int r = (int) (color1.getRed() + (color2.getRed() - color1.getRed()) * progress);
        int g = (int) (color1.getGreen() + (color2.getGreen() - color1.getGreen()) * progress);
        int b = (int) (color1.getBlue() + (color2.getBlue() - color1.getBlue()) * progress);
        return new Color(r, g, b, color1.getAlpha());
    }

    public void render(DrawContext context) {
        if (!visible) return;

        // Draw setting name
        fontRenderer.drawStringWithShadow(
                context.getMatrices(),
                setting.getName(),
                (int)(background.getX() + PADDING),
                (int)(background.getY() + (HEIGHT - fontRenderer.getFontHeight()) / 2),
                0xFFFFFFFF,
                0x55000000
        );

        // Update toggle animation
        if (animating && setting.getValue() instanceof Boolean) {
            long currentTime = System.currentTimeMillis();
            float deltaTime = (currentTime - lastUpdateTime) / ANIMATION_DURATION;
            lastUpdateTime = currentTime;

            boolean targetValue = (Boolean) setting.getValue();
            float targetProgress = targetValue ? 1f : 0f;

            if (targetValue && animationProgress < 1f) {
                animationProgress = Math.min(1f, animationProgress + deltaTime);
            } else if (!targetValue && animationProgress > 0f) {
                animationProgress = Math.max(0f, animationProgress - deltaTime);
            } else {
                animating = false;
            }

            // Update toggle appearance
            if (toggleTrack != null && toggleKnob != null) {
                // Interpolate track color
                Color trackColor = interpolateColor(TOGGLE_OFF_COLOR, TOGGLE_ON_COLOR, animationProgress);
                toggleTrack.setFillColor(trackColor);

                // Update knob position
                float knobOffset = getKnobOffset();
                toggleKnob.attachTo(toggleTrack, knobOffset, (TOGGLE_HEIGHT - KNOB_SIZE) / 2);
            }
        }
    }

    public void updatePosition(float yOffset) {
        if (background != null && background.getParent() != null) {
            background.attachTo(background.getParent(), 0, yOffset);

            if (toggleTrack != null) {
                toggleTrack.attachTo(background, background.getWidth() - TOGGLE_WIDTH - PADDING, (HEIGHT - TOGGLE_HEIGHT) / 2);

                if (toggleKnob != null) {
                    float knobOffset = getKnobOffset();
                    toggleKnob.attachTo(toggleTrack, knobOffset, (TOGGLE_HEIGHT - KNOB_SIZE) / 2);
                }
            }
        }
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        background.setFillColor(visible ? BG_COLOR : TRANSPARENT);

        if (toggleTrack != null) {
            boolean value = (Boolean) setting.getValue();
            Color trackColor = interpolateColor(TOGGLE_OFF_COLOR, TOGGLE_ON_COLOR, animationProgress);
            toggleTrack.setFillColor(visible ? trackColor : TRANSPARENT);
            toggleKnob.setFillColor(visible ? KNOB_COLOR : TRANSPARENT);
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
        if (!visible || toggleTrack == null) return false;
        return mouseX >= toggleTrack.getX() &&
                mouseX <= toggleTrack.getX() + TOGGLE_WIDTH &&
                mouseY >= toggleTrack.getY() &&
                mouseY <= toggleTrack.getY() + TOGGLE_HEIGHT;
    }

    public void onClick(double mouseX, double mouseY) {
        if (!visible) return;

        if (setting.getValue() instanceof Boolean && isToggleHovered(mouseX, mouseY)) {
            boolean value = (Boolean) setting.getValue();
            setting.setValue(!value);

            // Start animation
            animating = true;
            lastUpdateTime = System.currentTimeMillis();
        }
    }
}