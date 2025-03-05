package de.peter1337.midnight.render.screens.clickgui.setting.toggle;

import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.Render2D.RenderShape;
import de.peter1337.midnight.render.font.CustomFontRenderer;
import de.peter1337.midnight.modules.Setting;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.List;

public class SliderComponent {
    private final Render2D render2D;
    private final RenderShape sliderTrack;
    private final RenderShape sliderKnob;
    private final Setting<? extends Number> setting;

    // Instance field for the slider width.
    private final float sliderWidth;
    public static final float SLIDER_HEIGHT = 8f;
    public static final float KNOB_SIZE = 9f;
    private static final float PADDING = 5f;
    private static final float VALUE_TEXT_PADDING = 2f;
    private static final float VALUE_TEXT_MIN_WIDTH = 40f;

    // Colors.
    private static final Color TRACK_BACKGROUND_COLOR = new Color(45, 45, 65, 255);
    private static final Color KNOB_COLOR = new Color(255, 255, 255, 255);
    private static final Color DRAGGING_KNOB_COLOR = new Color(200, 200, 200, 255);
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    private static final Color VALUE_TEXT_COLOR = new Color(255, 255, 255, 255);
    private static final Color VALUE_BG_COLOR = new Color(25, 25, 45, 255);

    // Numeric bounds for the slider.
    private final float minValue;
    private final float maxValue;

    // Animation fields.
    private float currentValue;
    private float targetValue;
    private boolean animating = false;
    private long lastUpdateTime;
    private static final float ANIMATION_DURATION = 150f; // in ms

    // Drag support flag.
    private boolean isDragging = false;

    // Font renderer.
    private final CustomFontRenderer fontRenderer = CustomFontRenderer.getInstanceForSize(10f);

    /**
     * Constructs a slider component.
     *
     * @param render2D    The Render2D instance.
     * @param background  The background shape to which the slider is attached.
     * @param sliderX     The initial X coordinate.
     * @param sliderY     The initial Y coordinate.
     * @param sliderWidth The width of the slider track.
     * @param setting     The numeric Setting.
     */
    public SliderComponent(Render2D render2D, RenderShape background, float sliderX, float sliderY, float sliderWidth, Setting<? extends Number> setting) {
        this.render2D = render2D;
        this.sliderWidth = sliderWidth;
        this.setting = setting;

        // Use the setting's min/max.
        this.minValue = setting.getMinValue().floatValue();
        this.maxValue = setting.getMaxValue().floatValue();

        float defaultFraction = (setting.getValue().floatValue() - minValue) / (maxValue - minValue);
        this.currentValue = defaultFraction;
        this.targetValue = defaultFraction;

        sliderTrack = render2D.createRoundedRect(
                sliderX,
                sliderY,
                sliderWidth,
                SLIDER_HEIGHT,
                SLIDER_HEIGHT / 2,
                TRACK_BACKGROUND_COLOR
        );
        sliderTrack.attachTo(background, sliderX - background.getX(), sliderY - background.getY());

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
     * Returns the horizontal offset for the knob.
     */
    private float getKnobOffset() {
        return currentValue * (sliderWidth - KNOB_SIZE);
    }

    /**
     * Sets the slider's target fraction.
     *
     * @param value The new fraction (0f to 1f).
     */
    public void setValue(float value) {
        value = Math.max(0f, Math.min(1f, value));
        if (value != targetValue) {
            targetValue = value;
            animating = true;
            lastUpdateTime = System.currentTimeMillis();
        }
    }

    /**
     * Returns the target fraction.
     */
    public float getValue() {
        return targetValue;
    }

    // --- Accessor methods added below ---
    public float getMinValue() {
        return minValue;
    }

    public float getMaxValue() {
        return maxValue;
    }
    // --- End accessors ---

    public void onMouseDown(double mouseX, double mouseY) {
        if (isHovered(mouseX, mouseY) || isKnobHovered(mouseX, mouseY, currentValue)) {
            isDragging = true;
            onDrag(mouseX);
        }
    }

    public void onDrag(double mouseX) {
        if (!isDragging) return;
        float trackX = sliderTrack.getX();
        float fraction = (float) ((mouseX - trackX) / (sliderWidth - KNOB_SIZE));
        fraction = Math.max(0f, Math.min(1f, fraction));
        setValue(fraction);
        float newValue = minValue + fraction * (maxValue - minValue);
        setting.setValue(newValue);
        currentValue = targetValue;
    }

    public void onMouseUp(double mouseX, double mouseY) {
        isDragging = false;
    }

    public void render(DrawContext context, float fraction) {
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
        float knobOffset = getKnobOffset();
        sliderKnob.attachTo(sliderTrack, knobOffset, (SLIDER_HEIGHT - KNOB_SIZE) / 2);

        if (isDragging) {
            sliderKnob.setFillColor(DRAGGING_KNOB_COLOR);
        } else {
            sliderKnob.setFillColor(KNOB_COLOR);
        }

        // Calculate the actual value to display
        float actualValue = minValue + currentValue * (maxValue - minValue);
        String valueText = String.format("%.2f", actualValue);

        // Get font dimensions
        int fontHeight = fontRenderer.getFontHeight();
        int textWidth = fontRenderer.getStringWidth(valueText);

        // Calculate text position, adding padding to avoid the text touching the slider
        int textX = (int) (sliderTrack.getX() + sliderWidth + VALUE_TEXT_PADDING);

        // Calculate vertical center for proper alignment - align with the center of the track
        int textY = (int) (sliderTrack.getY() + SLIDER_HEIGHT / 4.0f - fontHeight / 2.0f);

        // Find the top-level container (main panel) for clipping
        RenderShape mainPanel = sliderTrack;
        while (mainPanel.getParent() != null) {
            mainPanel = mainPanel.getParent();
        }

        // Find the module section panel if available
        RenderShape moduleSection = findModuleSectionPanel(sliderTrack);

        // Set up clipping to ensure the value text is visible
        // Define a clip area for the value text that's wider than the text itself
        float valueTextWidth = fontRenderer.getStringWidth(valueText) + VALUE_TEXT_PADDING * 2;
        fontRenderer.setClipBounds(
                textX - VALUE_TEXT_PADDING,
                sliderTrack.getY(),
                valueTextWidth,
                SLIDER_HEIGHT
        );

        // Add additional clip regions for both the main panel and module section
        fontRenderer.clearAdditionalClipRegions();
        if (mainPanel != null) {
            fontRenderer.addClipRegion(
                    mainPanel.getX(),
                    mainPanel.getY(),
                    mainPanel.getWidth(),
                    mainPanel.getHeight()
            );
        }

        // If we found a module section panel, add it as an additional clip region
        if (moduleSection != null) {
            fontRenderer.addClipRegion(
                    moduleSection.getX(),
                    moduleSection.getY(),
                    moduleSection.getWidth(),
                    moduleSection.getHeight()
            );
        }

        // Draw the value text with proper shadow
        fontRenderer.drawStringWithShadow(
                context.getMatrices(),
                valueText,
                textX,
                textY,
                VALUE_TEXT_COLOR.getRGB(),
                0x55000000
        );

        // Reset any clipping
        fontRenderer.resetClipBounds();
    }

    /**
     * Tries to find the module section panel in the container hierarchy.
     * This is a heuristic that looks for shapes that might be the module section panel.
     *
     * @param container The container to start searching from
     * @return The module section panel, or null if not found
     */
    private RenderShape findModuleSectionPanel(RenderShape container) {
        if (container == null) {
            return null;
        }

        // Check if this container has children that are likely to be the module section
        List<RenderShape> children = container.getChildren();
        if (children != null) {
            for (RenderShape child : children) {
                // In ClickGuiBackground, the module section is the second shape created
                // and has specific dimensions. This is a heuristic to find it.
                if (child.getWidth() > container.getWidth() * 0.6f &&
                        child.getHeight() > container.getHeight() * 0.8f) {
                    // This looks like the module section panel
                    return child;
                }
            }
        }

        // If we didn't find it at this level, check the parent
        if (container.getParent() != null) {
            return findModuleSectionPanel(container.getParent());
        }

        // If we get here, we couldn't find it
        return null;
    }

    public void updatePosition(RenderShape background, float sliderX, float sliderY) {
        sliderTrack.attachTo(background, sliderX - background.getX(), sliderY - background.getY());
        float knobOffset = getKnobOffset();
        sliderKnob.attachTo(sliderTrack, knobOffset, (SLIDER_HEIGHT - KNOB_SIZE) / 2);
    }

    public void setVisible(boolean visible) {
        if (visible) {
            sliderTrack.setFillColor(TRACK_BACKGROUND_COLOR);
            sliderKnob.setFillColor(KNOB_COLOR);
        } else {
            sliderTrack.setFillColor(TRANSPARENT);
            sliderKnob.setFillColor(TRANSPARENT);
        }
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= sliderTrack.getX() &&
                mouseX <= sliderTrack.getX() + sliderWidth &&
                mouseY >= sliderTrack.getY() &&
                mouseY <= sliderTrack.getY() + SLIDER_HEIGHT;
    }

    public boolean isKnobHovered(double mouseX, double mouseY, float fraction) {
        float knobOffset = fraction * (sliderWidth - KNOB_SIZE);
        float knobX = sliderTrack.getX() + knobOffset;
        float knobY = sliderTrack.getY() + (SLIDER_HEIGHT - KNOB_SIZE) / 2;
        return mouseX >= knobX && mouseX <= knobX + KNOB_SIZE &&
                mouseY >= knobY && mouseY <= knobY + KNOB_SIZE;
    }
}