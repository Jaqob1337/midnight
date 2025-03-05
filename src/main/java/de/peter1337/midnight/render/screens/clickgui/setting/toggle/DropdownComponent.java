package de.peter1337.midnight.render.screens.clickgui.setting.toggle;

import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.Render2D.RenderShape;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.render.font.CustomFontRenderer;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class DropdownComponent {
    private final Render2D render2D;
    // Container in which this dropdown is placed.
    private final RenderShape container;
    // The dropdown button (background).
    private final RenderShape dropdownBox;
    // The arrow indicator.
    private final RenderShape arrowIndicator;
    // Associated setting and its options.
    private final Setting<String> setting;
    private final List<String> options;
    // Expanded state flag.
    private boolean expanded = false;
    // Cached option shapes (created once per expansion).
    private final List<RenderShape> optionShapes = new ArrayList<>();
    // Font renderer.
    private final CustomFontRenderer fontRenderer = CustomFontRenderer.getInstanceForSize(10f);

    // Layout constants.
    public static final float DROPDOWN_HEIGHT = 15f;
    private static final float ARROW_SIZE = 6f;
    private static final float PADDING = 4f;
    private static final Color BOX_COLOR = new Color(45, 45, 65, 255);
    private static final Color TEXT_COLOR = new Color(255, 255, 255, 255);
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    /**
     * Constructs a DropdownComponent.
     *
     * @param render2D the Render2D instance.
     * @param parent   the container shape to which this dropdown belongs.
     * @param x        absolute x-coordinate for the dropdown button.
     * @param y        absolute y-coordinate for the dropdown button.
     * @param width    width of the dropdown button.
     * @param setting  the setting associated with this dropdown.
     * @param options  list of option strings.
     */
    public DropdownComponent(Render2D render2D, RenderShape parent, float x, float y, float width, Setting<String> setting, List<String> options) {
        this.render2D = render2D;
        this.container = parent;
        this.setting = setting;
        this.options = options;

        // Create the dropdown button.
        dropdownBox = render2D.createRoundedRect(x, y, width, DROPDOWN_HEIGHT, DROPDOWN_HEIGHT / 2, BOX_COLOR);
        // Attach relative to container.
        dropdownBox.attachTo(container, x - container.getX(), y - container.getY());

        // Create the arrow indicator.
        arrowIndicator = render2D.createCircle(x + width - PADDING - ARROW_SIZE / 2,
                y + DROPDOWN_HEIGHT / 2,
                ARROW_SIZE / 2,
                TEXT_COLOR);
        arrowIndicator.attachTo(dropdownBox, width - PADDING - ARROW_SIZE, (DROPDOWN_HEIGHT - ARROW_SIZE) / 2);
    }

    /**
     * Creates and attaches option shapes for each option.
     * Called only once when expanding.
     */
    private void createOptionShapes() {
        optionShapes.clear();
        float baseX = dropdownBox.getX();
        float baseY = dropdownBox.getY();
        float optionY = baseY + DROPDOWN_HEIGHT;

        for (String option : options) {
            // Change from createRoundedRect with radius 0 to using DROPDOWN_HEIGHT / 2 for rounded corners
            RenderShape optionShape = render2D.createRoundedRect(baseX, optionY,
                    dropdownBox.getWidth(), DROPDOWN_HEIGHT, DROPDOWN_HEIGHT / 100, BOX_COLOR);
            optionShape.attachTo(container, baseX - container.getX(), optionY - container.getY());
            optionShapes.add(optionShape);
            optionY += DROPDOWN_HEIGHT;
        }
    }

    /**
     * Collapses the dropdown by setting each option's fill color to transparent and clearing the option shapes.
     */
    private void collapseDropdown() {
        expanded = false;
        for (RenderShape optionShape : optionShapes) {
            optionShape.setFillColor(TRANSPARENT);
        }
        optionShapes.clear();
    }

    /**
     * Renders the dropdown button and, if visible and expanded, its options.
     */
    public void render(DrawContext context) {
        // Find the top-level container (main panel)
        RenderShape mainPanel = container;
        while (mainPanel.getParent() != null) {
            mainPanel = mainPanel.getParent();
        }

        // Find the module section panel if available
        RenderShape moduleSection = findModuleSectionPanel(container);

        // Render the current selected value on the dropdown button.
        String selectedValue = setting.getValue();

        // Move our drawing forward in the Z-buffer so it appears on top
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 10);  // Move forward in Z to ensure dropdown is on top

        // Set up the basic clipping to the dropdown itself
        fontRenderer.setClipBounds(
                dropdownBox.getX(),
                dropdownBox.getY(),
                dropdownBox.getWidth() - ARROW_SIZE - (PADDING * 2),
                DROPDOWN_HEIGHT
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

        // Calculate perfect vertical center position for text
        int fontHeight = fontRenderer.getFontHeight();
        int textY = (int) (dropdownBox.getY() + (DROPDOWN_HEIGHT - fontHeight) / 30.0f);

        // Render the text with all clipping regions applied
        fontRenderer.drawStringWithShadow(
                context.getMatrices(),
                selectedValue,
                (int) (dropdownBox.getX() + PADDING),
                textY,
                TEXT_COLOR.getRGB(),
                0x55000000
        );

        // Reset clipping after drawing
        fontRenderer.resetClipBounds();

        // Arrow indicator renders automatically as it is attached to dropdownBox.

        // Check visible state via dropdownBox fill color.
        if (dropdownBox.getFillColor().equals(TRANSPARENT)) {
            // Do not render options if the component is invisible.
            context.getMatrices().pop();  // Restore matrix stack
            return;
        }

        // Render options if expanded.
        if (expanded) {
            if (optionShapes.isEmpty()) {
                createOptionShapes();
            }

            // Render each option
            for (int i = 0; i < optionShapes.size(); i++) {
                RenderShape optionShape = optionShapes.get(i);

                // Skip rendering options that are outside the module section boundaries
                if (moduleSection != null) {
                    float optionTop = optionShape.getY();
                    float optionBottom = optionTop + DROPDOWN_HEIGHT;
                    float moduleSectionTop = moduleSection.getY();
                    float moduleSectionBottom = moduleSection.getY() + moduleSection.getHeight();

                    // Skip if option is completely outside visible area
                    if (optionBottom < moduleSectionTop || optionTop > moduleSectionBottom) {
                        optionShape.setFillColor(TRANSPARENT);
                        continue;
                    } else {
                        optionShape.setFillColor(BOX_COLOR);
                    }
                }

                // Set clipping bounds for each option
                fontRenderer.setClipBounds(
                        optionShape.getX(),
                        optionShape.getY(),
                        optionShape.getWidth() - (PADDING * 2),
                        DROPDOWN_HEIGHT
                );

                // Add additional clipping regions again
                fontRenderer.clearAdditionalClipRegions();
                fontRenderer.addClipRegion(
                        mainPanel.getX(),
                        mainPanel.getY(),
                        mainPanel.getWidth(),
                        mainPanel.getHeight()
                );

                if (moduleSection != null) {
                    fontRenderer.addClipRegion(
                            moduleSection.getX(),
                            moduleSection.getY(),
                            moduleSection.getWidth(),
                            moduleSection.getHeight()
                    );
                }

                // Calculate vertical center for option text
                int optionTextY = (int) (optionShape.getY() + (DROPDOWN_HEIGHT - fontHeight) / 2.0f);

                // Draw the option text with all clipping applied
                fontRenderer.drawStringWithShadow(
                        context.getMatrices(),
                        options.get(i),
                        (int) (optionShape.getX() + PADDING),
                        optionTextY,
                        TEXT_COLOR.getRGB(),
                        0x55000000
                );

                // Reset clipping after each option
                fontRenderer.resetClipBounds();
            }
        }

        context.getMatrices().pop();  // Restore matrix stack
    }

    /**
     * Handles mouse clicks to toggle expansion and select an option.
     * Coordinates (mouseX, mouseY) are in container coordinate space.
     */
    public void onMouseDown(double mouseX, double mouseY) {
        float boxX = dropdownBox.getX();
        float boxY = dropdownBox.getY();
        boolean clickOnButton = mouseX >= boxX && mouseX <= boxX + dropdownBox.getWidth() &&
                mouseY >= boxY && mouseY <= boxY + DROPDOWN_HEIGHT;
        if (!expanded) {
            if (clickOnButton) {
                expanded = true;
                createOptionShapes();
            }
            return;
        } else {
            // If expanded, clicking on the button toggles collapse.
            if (clickOnButton) {
                collapseDropdown();
                return;
            }

            // Find the module section to check visibility
            RenderShape moduleSection = findModuleSectionPanel(container);

            // Check if an option is clicked.
            for (int i = 0; i < optionShapes.size(); i++) {
                RenderShape optionShape = optionShapes.get(i);
                float optX = optionShape.getX();
                float optY = optionShape.getY();

                // Skip options outside the visible area
                if (moduleSection != null) {
                    float optionTop = optionShape.getY();
                    float optionBottom = optionTop + DROPDOWN_HEIGHT;
                    float moduleSectionTop = moduleSection.getY();
                    float moduleSectionBottom = moduleSection.getY() + moduleSection.getHeight();

                    // Skip if completely outside visible area
                    if (optionBottom < moduleSectionTop || optionTop > moduleSectionBottom) {
                        continue;
                    }
                }

                if (mouseX >= optX && mouseX <= optX + optionShape.getWidth() &&
                        mouseY >= optY && mouseY <= optY + DROPDOWN_HEIGHT) {
                    setting.setValue(options.get(i));
                    collapseDropdown();
                    return;
                }
            }

            // If click is outside the entire dropdown region, collapse.
            float totalHeight = DROPDOWN_HEIGHT * (options.size() + 1);
            if (mouseX < boxX || mouseX > boxX + dropdownBox.getWidth() ||
                    mouseY < boxY || mouseY > boxY + totalHeight) {
                collapseDropdown();
            }
        }
    }

    /**
     * Updates the position of the dropdown button and, if expanded, its options.
     * The provided (x, y) are absolute coordinates; they are converted to relative coordinates for attachment.
     *
     * @param x new absolute x-coordinate for the dropdown button.
     * @param y new absolute y-coordinate for the dropdown button.
     */
    public void updatePosition(float x, float y) {
        dropdownBox.attachTo(container, x - container.getX(), y - container.getY());
        arrowIndicator.attachTo(dropdownBox, dropdownBox.getWidth() - PADDING - ARROW_SIZE, (DROPDOWN_HEIGHT - ARROW_SIZE) / 2);

        if (expanded && !optionShapes.isEmpty()) {
            // Update option positions when dropdown moves
            float baseX = dropdownBox.getX();
            float baseY = dropdownBox.getY() + DROPDOWN_HEIGHT;

            for (int i = 0; i < optionShapes.size(); i++) {
                RenderShape optionShape = optionShapes.get(i);
                float optionY = baseY + (i * DROPDOWN_HEIGHT);
                optionShape.attachTo(container, baseX - container.getX(), optionY - container.getY());
            }
        }
    }

    /**
     * Determines whether this dropdown contributes to the maximum scroll
     * calculation in ClickGuiScreen. This should return true when expanded
     * so the ClickGuiScreen can factor in the dropdown options when calculating
     * max scroll amount.
     *
     * @return true if this dropdown affects scrolling
     */
    public boolean affectsScrolling() {
        return expanded && !options.isEmpty();
    }

    /**
     * Calculates how much extra scroll space this dropdown needs when expanded.
     * Used by the ClickGuiScreen to adjust the total scrollable height.
     *
     * @return additional scroll space needed by this dropdown when expanded
     */
    public float getExtraScrollHeight() {
        if (!expanded) return 0;
        return options.size() * DROPDOWN_HEIGHT;
    }

    /**
     * Sets the visibility of the dropdown.
     * When visible, the dropdown uses its standard colors.
     * When not visible, it sets its colors to TRANSPARENT, collapses the dropdown, and clears options.
     *
     * @param visible if false, hides the dropdown.
     */
    public void setVisible(boolean visible) {
        if (visible) {
            dropdownBox.setFillColor(BOX_COLOR);
            arrowIndicator.setFillColor(TEXT_COLOR);
        } else {
            dropdownBox.setFillColor(TRANSPARENT);
            arrowIndicator.setFillColor(TRANSPARENT);
            collapseDropdown();
        }
    }

    /**
     * Checks whether the mouse (in container coordinate space) is over the dropdown button or its options.
     *
     * @param mouseX x-coordinate in container space.
     * @param mouseY y-coordinate in container space.
     * @return true if hovered.
     */
    public boolean isMouseOver(double mouseX, double mouseY) {
        float boxX = dropdownBox.getX();
        float boxY = dropdownBox.getY();
        if (mouseX >= boxX && mouseX <= boxX + dropdownBox.getWidth() &&
                mouseY >= boxY && mouseY <= boxY + DROPDOWN_HEIGHT) {
            return true;
        }
        if (expanded && !optionShapes.isEmpty()) {
            for (RenderShape optionShape : optionShapes) {
                float optX = optionShape.getX();
                float optY = optionShape.getY();
                if (mouseX >= optX && mouseX <= optX + optionShape.getWidth() &&
                        mouseY >= optY && mouseY <= optY + DROPDOWN_HEIGHT) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether the dropdown is currently expanded.
     *
     * @return true if expanded.
     */
    public boolean isExpanded() {
        return expanded;
    }

    /**
     * Returns the number of options in this dropdown.
     *
     * @return the number of options
     */
    public int getOptionsCount() {
        return options.size();
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
        if (container == null) {
            return null;
        }

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