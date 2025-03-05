package de.peter1337.midnight.render.screens.clickgui;

import de.peter1337.midnight.manager.ConfigManager;
import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.render.ClickGuiModule;
import de.peter1337.midnight.render.GuiScreen;
import de.peter1337.midnight.render.screens.clickgui.background.ClickGuiBackground;
import de.peter1337.midnight.render.screens.clickgui.buttons.ClickGuiCategoryButton;
import de.peter1337.midnight.render.screens.clickgui.buttons.ClickGuiModuleButton;
import de.peter1337.midnight.render.screens.clickgui.setting.SettingComponent;
import de.peter1337.midnight.render.Render2D.RenderShape;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static de.peter1337.midnight.render.screens.clickgui.buttons.ClickGuiCategoryButton.BUTTON_HEIGHT;
import static de.peter1337.midnight.render.screens.clickgui.buttons.ClickGuiModuleButton.SETTINGS_PADDING;

public class ClickGuiScreen extends GuiScreen {

    private ClickGuiBackground background;
    private final List<ClickGuiCategoryButton> categoryButtons = new ArrayList<>();
    private final Map<Category, List<ClickGuiModuleButton>> moduleButtons = new java.util.HashMap<>();
    private Category selectedCategory = null;

    // Current and target scroll values.
    private float targetCategoryScroll = 0f;
    private float currentCategoryScroll = 0f;
    private float targetModuleScroll = 0f;
    private float currentModuleScroll = 0f;

    // Scrollbar elements
    private RenderShape moduleScrollbarTrack;
    private RenderShape moduleScrollbarThumb;
    private boolean isDraggingScrollbar = false;

    // Animation parameters.
    private static final float SCROLL_SPEED = 28f;
    private static final float SCROLL_ANIMATION_SPEED = 0.15f;
    private static final float SNAP_THRESHOLD = 0.5f;

    private static final float CATEGORY_SPACING = 60f;
    private static final float INITIAL_CATEGORY_OFFSET_Y = 11f;
    private static final float MODULES_TOP_MARGIN = 20f;
    private static final float CATEGORY_GAP = 10f;
    private static final float ICON_SCALE = 0.9f;
    private static final float CATEGORY_MARGIN = 10f;

    // Scrollbar constants
    private static final float SCROLLBAR_WIDTH = 2f;
    private static final float SCROLLBAR_RIGHT_MARGIN = 4f;
    private static final float SCROLLBAR_MIN_THUMB_HEIGHT = 40f;
    private static final Color SCROLLBAR_TRACK_COLOR = new Color(40, 40, 60, 120);
    private static final Color SCROLLBAR_THUMB_COLOR = new Color(100, 100, 140, 200);
    private static final Color SCROLLBAR_THUMB_HOVER_COLOR = new Color(120, 120, 160, 225);
    private static final Color SCROLLBAR_THUMB_DRAG_COLOR = new Color(140, 140, 180, 255);

    public ClickGuiScreen() {
        super(Text.literal("ClickGUI"));
    }

    @Override
    public void init() {
        super.init();
        background = new ClickGuiBackground(render2D, width, height);
        ConfigManager.setClickGuiBackground(background);

        // Load only the ClickGUI UI state (scroll, selected category, expanded states).
        var clickGuiModule = ModuleManager.getModule("ClickGUI");
        if (clickGuiModule instanceof ClickGuiModule && ((ClickGuiModule) clickGuiModule).isPositionSavingEnabled()) {
            ConfigManager.loadConfig("lastclickgui");
        }

        categoryButtons.clear();
        moduleButtons.clear();
        targetCategoryScroll = ConfigManager.savedCategoryScroll;
        currentCategoryScroll = ConfigManager.savedCategoryScroll;
        targetModuleScroll = 0f;
        currentModuleScroll = 0f;

        initializeCategories();
        initializeModules();
        initializeScrollbars();

        // Set selected category using saved state.
        if (ConfigManager.savedCategory != null && !ConfigManager.savedCategory.isEmpty()) {
            for (Category cat : Category.values()) {
                if (cat.name().equalsIgnoreCase(ConfigManager.savedCategory)) {
                    selectedCategory = cat;
                    break;
                }
            }
        }
        if (selectedCategory == null && Category.values().length > 0) {
            selectedCategory = Category.values()[0];
        }
        categoryButtons.forEach(catButton ->
                catButton.setSelected(catButton.getCategory() == selectedCategory));
        // Hide modules from all categories.
        for (List<ClickGuiModuleButton> moduleList : moduleButtons.values()) {
            for (ClickGuiModuleButton moduleButton : moduleList) {
                moduleButton.setVisible(false);
            }
        }
        if (selectedCategory != null && moduleButtons.containsKey(selectedCategory)) {
            moduleButtons.get(selectedCategory).forEach(moduleButton -> {
                moduleButton.setVisible(true);
                if (ConfigManager.savedExpanded.containsKey(moduleButton.getModule().getName())) {
                    moduleButton.setExpanded(ConfigManager.savedExpanded.get(moduleButton.getModule().getName()));
                }
            });
        }
        // Set both the main and module clip regions for combined clipping.
        render2D.setMainClip(background.getBackground());
        render2D.setModuleClip(background.getModuleSection());
    }

    private void initializeCategories() {
        float currentOffsetY = INITIAL_CATEGORY_OFFSET_Y;
        for (Category category : Category.values()) {
            ClickGuiCategoryButton categoryButton = new ClickGuiCategoryButton(
                    render2D,
                    category,
                    background.getBackground(),
                    20f,
                    currentOffsetY,
                    CATEGORY_GAP,
                    ICON_SCALE
            );
            categoryButtons.add(categoryButton);
            currentOffsetY += CATEGORY_SPACING;
        }
    }

    private void initializeModules() {
        for (Category category : Category.values()) {
            List<ClickGuiModuleButton> modules = new ArrayList<>();
            for (var module : ModuleManager.getModules()) {
                if (module.getCategory() == category) {
                    ClickGuiModuleButton moduleButton = new ClickGuiModuleButton(
                            render2D,
                            module,
                            background.getBackground()
                    );
                    modules.add(moduleButton);
                }
            }
            moduleButtons.put(category, modules);
        }
    }

    private void initializeScrollbars() {
        if (background == null || background.getModuleSection() == null) return;

        // For modules section
        RenderShape moduleSection = background.getModuleSection();
        float moduleX = moduleSection.getX() + moduleSection.getWidth() - SCROLLBAR_WIDTH - SCROLLBAR_RIGHT_MARGIN;
        float moduleY = moduleSection.getY();
        float moduleHeight = moduleSection.getHeight();

        moduleScrollbarTrack = render2D.createRoundedRect(
                moduleX,
                moduleY,
                SCROLLBAR_WIDTH,
                moduleHeight,
                SCROLLBAR_WIDTH / 2,
                SCROLLBAR_TRACK_COLOR
        );
        moduleScrollbarTrack.attachTo(moduleSection,
                moduleSection.getWidth() - SCROLLBAR_WIDTH - SCROLLBAR_RIGHT_MARGIN,
                0);

        // Create scrollbar thumb with placeholder size/position (will be updated in render)
        moduleScrollbarThumb = render2D.createRoundedRect(
                moduleX,
                moduleY,
                SCROLLBAR_WIDTH,
                SCROLLBAR_MIN_THUMB_HEIGHT,
                SCROLLBAR_WIDTH / 2,
                SCROLLBAR_THUMB_COLOR
        );
        moduleScrollbarThumb.attachTo(moduleScrollbarTrack, 0, 0);
    }

    private void updateScrollbarThumb() {
        if (moduleScrollbarThumb == null || moduleScrollbarTrack == null || selectedCategory == null) return;

        float maxScroll = calculateMaxModuleScroll();

        // Calculate basic thumb properties
        float trackHeight = moduleScrollbarTrack.getHeight();
        float totalContentHeight = maxScroll + background.getModuleSection().getHeight() - MODULES_TOP_MARGIN;
        float visibleRatio = background.getModuleSection().getHeight() / totalContentHeight;
        visibleRatio = Math.min(1.0f, Math.max(0.1f, visibleRatio)); // Clamp between 0.1 and 1.0
        float thumbHeight = Math.max(SCROLLBAR_MIN_THUMB_HEIGHT,
                visibleRatio * trackHeight);

        // Position the thumb based on current scroll state
        float thumbY;

        // Check if we're at the scroll limits or close to them (allowing some floating point imprecision)
        float epsilon = 0.0f; // Small tolerance value

        if (maxScroll <= 0) {
            // No scrollable content, position at top
            thumbY = 0;
        } else if (currentModuleScroll <= epsilon) {
            // At the top
            thumbY = 0;
        } else if (maxScroll - targetCategoryScroll <= epsilon) {
            // At the bottom - snap exactly to bottom
            thumbY = trackHeight - thumbHeight;
        } else {
            // Between limits, calculate proportional position
            float scrollRatio = currentModuleScroll / maxScroll;
            // Ensure ratio is between 0 and 1
            scrollRatio = Math.min(1.0f, Math.max(0.0f, scrollRatio));
            float availableSpace = trackHeight - thumbHeight;
            thumbY = availableSpace * scrollRatio;
        }

        // Update thumb position
        moduleScrollbarThumb.attachTo(moduleScrollbarTrack, 0, thumbY);

        // Only show scrollbar if content is scrollable
        boolean scrollable = maxScroll > 0;
        Color thumbColor = SCROLLBAR_THUMB_COLOR;

        // Check if mouse is hovering over scrollbar
        if (scrollable) {
            double mouseX = MinecraftClient.getInstance().mouse.getX() /
                    MinecraftClient.getInstance().getWindow().getScaleFactor();
            double mouseY = MinecraftClient.getInstance().mouse.getY() /
                    MinecraftClient.getInstance().getWindow().getScaleFactor();

            boolean hovered = mouseX >= moduleScrollbarThumb.getX() &&
                    mouseX <= moduleScrollbarThumb.getX() + moduleScrollbarThumb.getWidth() &&
                    mouseY >= moduleScrollbarThumb.getY() &&
                    mouseY <= moduleScrollbarThumb.getY() + thumbHeight;

            if (isDraggingScrollbar) {
                thumbColor = SCROLLBAR_THUMB_DRAG_COLOR;
            } else if (hovered) {
                thumbColor = SCROLLBAR_THUMB_HOVER_COLOR;
            }
        }

        // Show/hide and update thumb color
        moduleScrollbarTrack.setFillColor(scrollable ? SCROLLBAR_TRACK_COLOR : new Color(0, 0, 0, 0));
        moduleScrollbarThumb.setFillColor(scrollable ? thumbColor : new Color(0, 0, 0, 0));

        // Debug information - uncomment if needed
        /*
        System.out.println("Current scroll: " + currentModuleScroll);
        System.out.println("Max scroll: " + maxScroll);
        System.out.println("Ratio: " + (currentModuleScroll / maxScroll));
        System.out.println("Thumb position: " + thumbY);
        System.out.println("Track height: " + trackHeight);
        System.out.println("Thumb height: " + thumbHeight);
        System.out.println("At bottom: " + (maxScroll - currentModuleScroll <= epsilon));
        */
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (background != null && background.getOverlay() != null) {
            // Update shadow position before rendering to make sure it follows the panel
            background.updateShadowPosition();
            render2D.renderShapes();
        }
    }

    @Override
    public void setFocused(boolean focused) { }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Update shadow position at the beginning of each render frame
        if (background != null) {
            background.updateShadowPosition();
        }

        currentCategoryScroll += (targetCategoryScroll - currentCategoryScroll) * SCROLL_ANIMATION_SPEED;
        currentModuleScroll += (targetModuleScroll - currentModuleScroll) * SCROLL_ANIMATION_SPEED;

        categoryButtons.forEach(button -> button.updatePosition(currentCategoryScroll));
        if (selectedCategory != null) {
            float cumulativeY = MODULES_TOP_MARGIN - currentModuleScroll;
            for (ClickGuiModuleButton moduleButton : moduleButtons.get(selectedCategory)) {
                moduleButton.updatePosition(cumulativeY);
                cumulativeY += moduleButton.getTotalHeight() + 5f;
            }
        }

        // Update scrollbar position and appearance
        updateScrollbarThumb();

        super.render(context, mouseX, mouseY, delta);
        categoryButtons.forEach(button -> {
            button.updateColor();
            button.render(context);
        });
        moduleButtons.values().stream()
                .flatMap(list -> list.stream())
                .forEach(moduleButton -> {
                    moduleButton.update();
                    moduleButton.render(context);
                });

        ConfigManager.savedCategoryScroll = currentCategoryScroll;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if clicking on scrollbar thumb
        if (moduleScrollbarThumb != null && button == 0) {
            boolean thumbVisible = moduleScrollbarThumb.getFillColor().getAlpha() > 0;
            if (thumbVisible && mouseX >= moduleScrollbarThumb.getX() &&
                    mouseX <= moduleScrollbarThumb.getX() + moduleScrollbarThumb.getWidth() &&
                    mouseY >= moduleScrollbarThumb.getY() &&
                    mouseY <= moduleScrollbarThumb.getY() + moduleScrollbarThumb.getHeight()) {
                isDraggingScrollbar = true;
                return true;
            }

            // Check if clicking on scrollbar track
            if (thumbVisible && moduleScrollbarTrack != null &&
                    mouseX >= moduleScrollbarTrack.getX() &&
                    mouseX <= moduleScrollbarTrack.getX() + moduleScrollbarTrack.getWidth() &&
                    mouseY >= moduleScrollbarTrack.getY() &&
                    mouseY <= moduleScrollbarTrack.getY() + moduleScrollbarTrack.getHeight()) {
                // Jump the scrollbar to this position
                float clickPositionRatio = (float)(mouseY - moduleScrollbarTrack.getY()) / moduleScrollbarTrack.getHeight();
                targetModuleScroll = calculateMaxModuleScroll() * clickPositionRatio;
                return true;
            }
        }

        if (selectedCategory != null) {
            for (ClickGuiModuleButton moduleButton : moduleButtons.get(selectedCategory)) {
                if (moduleButton.isHovered(mouseX, mouseY)) {
                    moduleButton.onClick(button);
                    return true;
                }
                for (SettingComponent setting : moduleButton.getSettingComponents()) {
                    if (setting.isHovered(mouseX, mouseY)) {
                        setting.onClick(mouseX, mouseY);
                        return true;
                    }
                }
            }
        }
        for (ClickGuiCategoryButton categoryButton : categoryButtons) {
            if (categoryButton.isHovered(mouseX, mouseY)) {
                if (selectedCategory != categoryButton.getCategory()) {
                    if (selectedCategory != null && moduleButtons.containsKey(selectedCategory)) {
                        for (ClickGuiModuleButton moduleButton : moduleButtons.get(selectedCategory)) {
                            ConfigManager.savedExpanded.put(moduleButton.getModule().getName(), moduleButton.isExpanded());
                        }
                    }
                    for (List<ClickGuiModuleButton> moduleList : moduleButtons.values()) {
                        for (ClickGuiModuleButton moduleButton : moduleList) {
                            moduleButton.setVisible(false);
                        }
                    }
                    selectedCategory = categoryButton.getCategory();
                    targetModuleScroll = 0f;
                    currentModuleScroll = 0f;
                    moduleButtons.get(selectedCategory).forEach(moduleButton -> {
                        moduleButton.setVisible(true);
                        if (ConfigManager.savedExpanded.containsKey(moduleButton.getModule().getName())) {
                            moduleButton.setExpanded(ConfigManager.savedExpanded.get(moduleButton.getModule().getName()));
                        }
                    });
                    categoryButtons.forEach(catButton ->
                            catButton.setSelected(catButton.getCategory() == selectedCategory));

                    float btnY = categoryButton.getY();
                    float btnH = categoryButton.getHeight();
                    float clipY = background.getBackground().getY();
                    float clipH = background.getBackground().getHeight();

                    if (btnY < clipY + CATEGORY_MARGIN) {
                        targetCategoryScroll -= (clipY + CATEGORY_MARGIN - btnY);
                    } else if (btnY + btnH > clipY + clipH - CATEGORY_MARGIN) {
                        targetCategoryScroll += (btnY + btnH - (clipY + clipH - CATEGORY_MARGIN));
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Handle scrollbar dragging
        if (isDraggingScrollbar && button == 0 && moduleScrollbarTrack != null) {
            // Calculate the new position ratio
            float dragPosition = (float)(mouseY - moduleScrollbarTrack.getY());
            float trackHeight = moduleScrollbarTrack.getHeight();

            // Convert to scroll ratio (0.0 to 1.0)
            float scrollRatio = dragPosition / trackHeight;
            scrollRatio = Math.min(1.0f, Math.max(0.0f, scrollRatio)); // Clamp between 0 and 1

            // Apply the new scroll position
            targetModuleScroll = calculateMaxModuleScroll() * scrollRatio;
            return true;
        }

        // Update shadow position during dragging to ensure it follows in real-time
        if (background != null) {
            background.updateShadowPosition();
        }

        if (selectedCategory != null) {
            for (ClickGuiModuleButton moduleButton : moduleButtons.get(selectedCategory)) {
                for (SettingComponent setting : moduleButton.getSettingComponents()) {
                    if (setting.isHovered(mouseX, mouseY)) {
                        setting.onMouseDrag(mouseX, mouseY);
                        return true;
                    }
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Handle scrollbar release
        if (isDraggingScrollbar && button == 0) {
            isDraggingScrollbar = false;
            return true;
        }

        if (selectedCategory != null) {
            for (ClickGuiModuleButton moduleButton : moduleButtons.get(selectedCategory)) {
                for (SettingComponent setting : moduleButton.getSettingComponents()) {
                    if (setting.isHovered(mouseX, mouseY)) {
                        setting.onMouseUp(mouseX, mouseY);
                        return true;
                    }
                }
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isMouseOverCategories(mouseX, mouseY)) {
            float totalCategoryHeight = categoryButtons.size() * CATEGORY_SPACING;
            float visibleHeight = background.getBackground().getHeight() - INITIAL_CATEGORY_OFFSET_Y;
            float maxScroll = Math.max(0, totalCategoryHeight - visibleHeight);
            targetCategoryScroll = Math.max(0, Math.min(targetCategoryScroll - (float) verticalAmount * SCROLL_SPEED, maxScroll));
            return true;
        } else if (selectedCategory != null && isMouseOverModules(mouseX, mouseY)) {
            float maxScroll = calculateMaxModuleScroll();
            targetModuleScroll = Math.max(0, Math.min(targetModuleScroll - (float) verticalAmount * SCROLL_SPEED, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private float calculateMaxModuleScroll() {
        if (selectedCategory == null) return 0;
        float totalHeight = 0;

        for (ClickGuiModuleButton moduleButton : moduleButtons.get(selectedCategory)) {
            // Get the base module height
            float moduleHeight = BUTTON_HEIGHT;

            // If expanded, add all setting heights including dropdowns
            if (moduleButton.isExpanded()) {
                for (SettingComponent setting : moduleButton.getSettingComponents()) {
                    // This will include extra height for expanded dropdowns
                    if (setting.isDependencyVisible()) {
                        moduleHeight += setting.getTotalHeight() + SETTINGS_PADDING;
                    }
                }
            }

            // Add this module's total height plus spacing
            totalHeight += moduleHeight + 5; // 5px spacing between modules
        }

        float visibleHeight = background.getBackground().getHeight() - MODULES_TOP_MARGIN;
        return Math.max(0, totalHeight - visibleHeight);
    }

    private boolean isMouseOverCategories(double mouseX, double mouseY) {
        if (background != null && background.getBackground() != null) {
            var bg = background.getBackground();
            return mouseX >= bg.getX() &&
                    mouseX <= bg.getX() + 90 &&
                    mouseY >= bg.getY() &&
                    mouseY <= bg.getY() + bg.getHeight();
        }
        return false;
    }

    private boolean isMouseOverModules(double mouseX, double mouseY) {
        if (background != null && background.getBackground() != null) {
            var bg = background.getBackground();
            return mouseX >= bg.getX() + 100 &&
                    mouseX <= bg.getX() + bg.getWidth() &&
                    mouseY >= bg.getY() &&
                    mouseY <= bg.getY() + bg.getHeight();
        }
        return false;
    }

    @Override
    public void close() {
        // Save the last selected category.
        ConfigManager.savedCategory = (selectedCategory != null ? selectedCategory.name() : "");
        // Save expanded state from all module buttons.
        ConfigManager.savedExpanded.clear();
        for (List<ClickGuiModuleButton> moduleList : moduleButtons.values()) {
            for (ClickGuiModuleButton moduleButton : moduleList) {
                ConfigManager.savedExpanded.put(moduleButton.getModule().getName(), moduleButton.isExpanded());
            }
        }
        // Save category scroll position.
        ConfigManager.savedCategoryScroll = currentCategoryScroll;

        var clickGuiModule = ModuleManager.getModule("ClickGUI");
        if (clickGuiModule instanceof ClickGuiModule && ((ClickGuiModule) clickGuiModule).isPositionSavingEnabled()) {
            ConfigManager.saveConfig("lastclickgui");
        }
        if (clickGuiModule != null && clickGuiModule.isEnabled()) {
            clickGuiModule.toggle();
        }
        MinecraftClient.getInstance().options.getMenuBackgroundBlurriness().setValue(8);
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}