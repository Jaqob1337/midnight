package de.peter1337.midnight.render.screens.clickgui.setting;

import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.Render2D.RenderShape;
import de.peter1337.midnight.render.font.CustomFontRenderer;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.render.screens.clickgui.setting.toggle.DropdownComponent;
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
    private DropdownComponent dropdownComponent; // For dropdown (String) settings
    private boolean visible;
    private boolean dependencyVisible; // Whether this setting should be visible based on dependencies

    // Layout constants.
    private static final float HEIGHT = 20f;
    private static final float PADDING = 5f;
    private static final Color BG_COLOR = new Color(25, 25, 45, 255);
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private static final float EXTRA_SPACING = 10f; // space between text and control
    private static final float VALUE_DISPLAY_PADDING = 2f;
    private float sliderXOffset = 0f;
    private float totalWidth = 0f;
    private float sliderWidth = 0f;
    private final float valueDisplayWidth;

    // Dropdown width for right-aligned dropdowns
    private static final float DROPDOWN_WIDTH = 150f;

    public SettingComponent(Render2D render2D, Setting<?> setting, RenderShape parent, float yOffset) {
        this.setting = setting;
        this.fontRenderer = CustomFontRenderer.getInstanceForSize(10f);
        this.visible = false;
        this.dependencyVisible = setting.isVisible(); // Initialize based on setting's visibility

        float textWidth = fontRenderer.getStringWidth(setting.getName());
        valueDisplayWidth = fontRenderer.getStringWidth("00.00") + VALUE_DISPLAY_PADDING * 2;
        sliderXOffset = textWidth + EXTRA_SPACING;

        totalWidth = parent.getWidth();
        sliderWidth = totalWidth - (textWidth + EXTRA_SPACING + valueDisplayWidth);

        // Create a background shape for the setting.
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
        } else if (setting.getValue() instanceof String && setting.getOptions() != null) {
            // For dropdown settings, position at the right side like the toggle
            float dropdownX = background.getX() + totalWidth - DROPDOWN_WIDTH - PADDING;
            float dropdownY = background.getY() + (HEIGHT - DropdownComponent.DROPDOWN_HEIGHT) / 2;
            dropdownComponent = new DropdownComponent(
                    render2D,
                    background,
                    dropdownX,
                    dropdownY,
                    DROPDOWN_WIDTH,
                    (Setting<String>) setting,
                    ((Setting<String>) setting).getOptions()
            );
        }
    }

    /**
     * Updates the visibility state based on dependencies
     */
    public void updateDependencyVisibility() {
        dependencyVisible = setting.isVisible();
        updateVisibility();
    }

    /**
     * Gets the total height of this setting component, taking into account its visibility.
     * If the setting is not visible due to dependencies, it returns 0.
     */
    public float getTotalHeight() {
        if (!visible || !dependencyVisible) return 0;

        // Base height of the setting component
        float height = HEIGHT;

        // Add dropdown options height if this has an expanded dropdown
        if (dropdownComponent != null && dropdownComponent.isExpanded()) {
            // Add height for each option in the dropdown
            height += dropdownComponent.getOptionsCount() * DropdownComponent.DROPDOWN_HEIGHT;
        }

        return height;
    }

    public void render(DrawContext context) {
        if (!visible || !dependencyVisible) return;

        // Find the top-level container (main panel)
        RenderShape mainPanel = background;
        while (mainPanel.getParent() != null) {
            mainPanel = mainPanel.getParent();
        }

        // Find the module section panel if available
        RenderShape moduleSection = findModuleSectionPanel(background);

        // Calculate maximum width for text to prevent overlap with controls
        float maxTextWidth;
        if (toggleComponent != null) {
            maxTextWidth = totalWidth - ToggleComponent.TOGGLE_WIDTH - (PADDING * 2);
        } else if (dropdownComponent != null) {
            maxTextWidth = totalWidth - DROPDOWN_WIDTH - (PADDING * 2);
        } else if (sliderComponent != null) {
            maxTextWidth = sliderXOffset - PADDING;
        } else {
            maxTextWidth = totalWidth - (PADDING * 2);
        }

        // Set clipping for the setting name text
        fontRenderer.setClipBounds(
                background.getX() + PADDING,
                background.getY(),
                maxTextWidth,
                HEIGHT
        );

        // Add additional clip regions for both the main panel and module section
        fontRenderer.clearAdditionalClipRegions();
        fontRenderer.addClipRegion(
                mainPanel.getX(),
                mainPanel.getY(),
                mainPanel.getWidth(),
                mainPanel.getHeight()
        );

        // If we found a module section panel, add it as an additional clip region
        if (moduleSection != null) {
            fontRenderer.addClipRegion(
                    moduleSection.getX(),
                    moduleSection.getY(),
                    moduleSection.getWidth(),
                    moduleSection.getHeight()
            );
        }

        // Calculate exact vertical center for the text
        int fontHeight = fontRenderer.getFontHeight();
        int textY = (int) (background.getY() + (HEIGHT - fontHeight) / 4f);

        // Render the setting name with all clipping regions applied
        fontRenderer.drawStringWithShadow(
                context.getMatrices(),
                setting.getName(),
                (int) (background.getX() + PADDING),
                textY,
                0xFFFFFFFF,
                0x55000000
        );

        // Reset clipping after drawing
        fontRenderer.resetClipBounds();

        // Render components
        if (toggleComponent != null) {
            toggleComponent.render(context, (Boolean) setting.getValue());
        }
        if (sliderComponent != null && setting.getValue() instanceof Number) {
            float currentValue = ((Number) setting.getValue()).floatValue();
            float fraction = (currentValue - sliderComponent.getMinValue()) / (sliderComponent.getMaxValue() - sliderComponent.getMinValue());
            sliderComponent.render(context, fraction);
        }
        if (dropdownComponent != null && setting.getValue() instanceof String) {
            dropdownComponent.render(context);
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
            if (dropdownComponent != null) {
                // Update dropdown to stay right-aligned
                float dropdownX = background.getX() + totalWidth - DROPDOWN_WIDTH - PADDING;
                float dropdownY = background.getY() + (HEIGHT - DropdownComponent.DROPDOWN_HEIGHT) / 2;
                dropdownComponent.updatePosition(dropdownX, dropdownY);
            }
        }
    }

    private void updateVisibility() {
        background.setFillColor((visible && dependencyVisible) ? BG_COLOR : TRANSPARENT);
        if (toggleComponent != null) {
            toggleComponent.setVisible(visible && dependencyVisible);
        }
        if (sliderComponent != null) {
            sliderComponent.setVisible(visible && dependencyVisible);
        }
        if (dropdownComponent != null) {
            dropdownComponent.setVisible(visible && dependencyVisible);
        }
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        updateVisibility();
    }

    public float getHeight() {
        return HEIGHT;
    }

    /**
     * Updated hit detection to include the extended area of an expanded dropdown.
     */
    public boolean isHovered(double mouseX, double mouseY) {
        if (!visible || !dependencyVisible) return false;

        // Base hitbox for the setting (the 20f height area).
        boolean baseHovered = mouseX >= background.getX() &&
                mouseX <= background.getX() + background.getWidth() &&
                mouseY >= background.getY() &&
                mouseY <= background.getY() + HEIGHT;

        // If a dropdown is present and expanded, check its full area.
        if (dropdownComponent != null && dropdownComponent.isExpanded()) {
            return baseHovered || dropdownComponent.isMouseOver(mouseX, mouseY);
        }

        return baseHovered;
    }

    public boolean isToggleHovered(double mouseX, double mouseY) {
        if (!visible || !dependencyVisible || toggleComponent == null) return false;
        return toggleComponent.isHovered(mouseX, mouseY);
    }

    public void onClick(double mouseX, double mouseY) {
        if (!visible || !dependencyVisible) return;
        if (toggleComponent != null && setting.getValue() instanceof Boolean && isToggleHovered(mouseX, mouseY)) {
            boolean currentValue = (Boolean) setting.getValue();
            setting.setValue(!currentValue);
            toggleComponent.startAnimation();
        } else if (sliderComponent != null && setting.getValue() instanceof Number) {
            sliderComponent.onMouseDown(mouseX, mouseY);
        } else if (dropdownComponent != null && setting.getValue() instanceof String) {
            dropdownComponent.onMouseDown(mouseX, mouseY);
        }
    }

    public void onMouseDrag(double mouseX, double mouseY) {
        if (!visible || !dependencyVisible) return;
        if (sliderComponent != null && setting.getValue() instanceof Number) {
            sliderComponent.onDrag(mouseX);
        }
        // Dropdowns typically do not require drag handling.
    }

    public void onMouseUp(double mouseX, double mouseY) {
        if (!visible || !dependencyVisible) return;
        if (sliderComponent != null && setting.getValue() instanceof Number) {
            sliderComponent.onMouseUp(mouseX, mouseY);
        }
        // Dropdowns typically do not require a mouse up handler.
    }

    /**
     * Returns the setting associated with this component.
     */
    public Setting<?> getSetting() {
        return setting;
    }

    /**
     * Returns whether this setting is visible based on its dependencies.
     */
    public boolean isDependencyVisible() {
        return dependencyVisible;
    }

    /**
     * Tries to find the module section panel in the container hierarchy.
     * This is a heuristic that looks for shapes that might be the module section panel.
     *
     * @param container The container to start searching from
     * @return The module section panel, or null if not found
     */
    private RenderShape findModuleSectionPanel(RenderShape container) {
        // Check if this container has children that are likely to be the module section
        List<RenderShape> children = container.getChildren();
        if (children != null) {
            for (RenderShape child : children) {
                // In your ClickGuiBackground, the module section is the second shape created
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
}