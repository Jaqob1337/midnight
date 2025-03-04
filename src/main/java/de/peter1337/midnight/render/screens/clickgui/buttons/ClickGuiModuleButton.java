package de.peter1337.midnight.render.screens.clickgui.buttons;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.Render2D.RenderShape;
import de.peter1337.midnight.render.font.CustomFontRenderer;
import de.peter1337.midnight.render.screens.clickgui.setting.SettingComponent;
import de.peter1337.midnight.modules.Setting;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ClickGuiModuleButton {
    private final Module module;
    private final RenderShape button;
    private boolean visible;
    private boolean expanded;
    private final CustomFontRenderer fontRenderer;
    private final List<SettingComponent> settingComponents;

    // Layout constants.
    private static final float BUTTON_WIDTH = 230f;
    private static final float BUTTON_HEIGHT = 30f;
    private static final float BUTTON_RADIUS = 3f;
    private static final Color BUTTON_COLOR = new Color(40, 35, 55, 255);
    private static final Color BUTTON_ENABLED_COLOR = new Color(45, 45, 65, 255);
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    private static final float FONT_SIZE = 11f;
    public static final float SETTINGS_PADDING = 5f;

    public ClickGuiModuleButton(Render2D render2D, Module module, RenderShape parent) {
        this.module = module;
        this.visible = false;
        this.expanded = false;
        this.fontRenderer = CustomFontRenderer.getInstanceForSize(FONT_SIZE);
        this.settingComponents = new ArrayList<>();

        // Create the button with a placeholder position.
        float initialX = parent.getWidth() * 0.3f;
        float initialY = 25f;  // Actual Y is set by the container.
        button = render2D.createRoundedRect(
                initialX, initialY, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_RADIUS, TRANSPARENT
        );
        button.attachTo(parent, initialX, initialY);

        initializeSettings(render2D);
    }


    private void initializeSettings(Render2D render2D) {
        float yOffset = BUTTON_HEIGHT + SETTINGS_PADDING;
        for (Setting<?> setting : module.getSettings()) {
            SettingComponent component = new SettingComponent(render2D, setting, button, yOffset);
            settingComponents.add(component);
            yOffset += component.getHeight() + SETTINGS_PADDING;
        }
    }

    /**
     * Updates the button’s fill color.
     */
    public void update() {
        if (button != null) {
            button.setFillColor(visible ? (module.isEnabled() ? BUTTON_ENABLED_COLOR : BUTTON_COLOR) : TRANSPARENT);
        }
    }


    /**
     * Renders the module button and its settings (if expanded).
     */

    public void render(DrawContext context) {
        if (!visible) return;

        // Find the top-level container (main panel)
        RenderShape mainPanel = button;
        while (mainPanel.getParent() != null) {
            mainPanel = mainPanel.getParent();
        }

        // Find the module section panel if available
        RenderShape moduleSection = findModuleSectionPanel(button);

        // Center the module name.
        if (fontRenderer != null && button != null) {
            String moduleName = module.getName();
            float textWidth = fontRenderer.getStringWidth(moduleName);
            float fontHeight = fontRenderer.getFontHeight();
            int textX = (int)(button.getX() + (BUTTON_WIDTH - textWidth) / 2);
            int textY = (int)(button.getY() + (BUTTON_HEIGHT - fontHeight) / 2f - 2.8f);

            // Set up the basic clipping to the button itself
            fontRenderer.setClipBounds(
                    button.getX(),
                    button.getY(),
                    BUTTON_WIDTH,
                    BUTTON_HEIGHT
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

            // Draw the module name with clipping applied
            fontRenderer.drawStringWithShadow(
                    context.getMatrices(),
                    moduleName,
                    textX,
                    textY,
                    module.isEnabled() ? 0xFFFFFFFF : 0xAAAAAAFF,
                    0x55000000
            );

            // Reset clipping after drawing
            fontRenderer.resetClipBounds();
        }

        // Render settings if expanded.
        if (expanded) {
            for (SettingComponent setting : settingComponents) {
                setting.render(context);
            }
        }
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

    /**
     * Called by the parent container to update this module button’s absolute position.
     *
     * @param yOffset the absolute y coordinate for this button.
     */
    public void updatePosition(float yOffset) {
        if (button != null && button.getParent() != null) {
            RenderShape parent = button.getParent();
            float xPos = parent.getWidth() * 0.3f;
            button.attachTo(parent, xPos, yOffset);

            // If expanded, update the relative position of the setting components.
            if (expanded) {
                float settingYOffset = BUTTON_HEIGHT + SETTINGS_PADDING;
                for (SettingComponent setting : settingComponents) {
                    setting.updatePosition(settingYOffset);
                    settingYOffset += setting.getHeight() + SETTINGS_PADDING;
                }
            }
        }
    }

    /**
     * Handles mouse clicks. Left-click toggles the module; right-click toggles settings.
     */
    public void onClick(int mouseButton) {
        if (!visible) return;
        if (mouseButton == 0) {
            module.toggle();
        } else if (mouseButton == 1) {
            // Toggle the expanded state.
            setExpanded(!expanded);
        }
    }

    private void updateSettingsVisibility() {
        for (SettingComponent setting : settingComponents) {
            setting.setVisible(expanded);
        }
    }

    /**
     * Returns the total height of this module button (including settings if expanded).
     */
    public float getTotalHeight() {
        if (!expanded) return BUTTON_HEIGHT;

        float total = BUTTON_HEIGHT;
        for (SettingComponent setting : settingComponents) {
            // Use the setting's total height which includes any dropdown options
            total += setting.getTotalHeight() + SETTINGS_PADDING;
        }
        return total;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return visible && button != null && button.isHovered(mouseX, mouseY);
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) {
            expanded = false;
            updateSettingsVisibility();
        }
        update();
    }

    public List<SettingComponent> getSettingComponents() {
        return settingComponents;
    }

    public Module getModule() {
        return module;
    }

    // Added getter for the expanded state.
    public boolean isExpanded() {
        return expanded;
    }

    // Added setter for the expanded state.
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        updateSettingsVisibility();
    }
}
