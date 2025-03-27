package de.peter1337.midnight.render.screens.clickgui;

import com.mojang.blaze3d.systems.RenderSystem;
import de.peter1337.midnight.manager.ConfigManager;
import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.render.GuiScreen;
import de.peter1337.midnight.render.screens.clickgui.background.ClickGuiBackground;
import de.peter1337.midnight.render.screens.clickgui.buttons.ClickGuiCategoryButton;
import de.peter1337.midnight.render.screens.clickgui.buttons.ClickGuiModuleButton;
import de.peter1337.midnight.render.screens.clickgui.setting.SettingComponent;
import de.peter1337.midnight.render.Render2D.RenderShape;
import de.peter1337.midnight.utils.GifRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.io.IOException;
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

        // Load the GIF animation with debug output
        try {
            // Fixed size for the GIF
            float gifSize = 100;

            System.out.println("Attempting to load GIF animation...");

            // Try loading from both possible paths to ensure the file is found
            try {
                GifRenderer.loadGif("textures/gui/logo_animation.gif", "logo_animation", gifSize, gifSize);
                System.out.println("Successfully loaded GIF from textures/gui/logo_animation.gif");
            } catch (IOException e) {
                System.out.println("Failed to load from primary path: " + e.getMessage());
                // Try alternate path
                try {
                    GifRenderer.loadGif("assets/midnight/textures/gui/logo_animation.gif", "logo_animation", gifSize, gifSize);
                    System.out.println("Successfully loaded GIF from alternate path");
                } catch (IOException e2) {
                    System.out.println("Failed to load from alternate path: " + e2.getMessage());
                    // Try with png as fallback
                    try {
                        GifRenderer.loadGif("textures/gui/logo.png", "logo_animation", gifSize, gifSize);
                        System.out.println("Loaded PNG as fallback");
                    } catch (IOException e3) {
                        System.out.println("Failed to load any image: " + e3.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            // Log the error but continue
            System.out.println("Error during GIF loading: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void renderImage(DrawContext context) {
        // Calculate a fixed position in the bottom-right corner of the screen
        int screenWidth = this.width;
        int screenHeight = this.height;

        // Fixed size for the image
        int imageSize = 100;

        // Position in bottom-right with margin
        int imageX = screenWidth - imageSize - 20;
        int imageY = screenHeight - imageSize - 20;

        // Draw background rectangle to make the area visible
        context.fill(imageX - 5, imageY - 5, imageX + imageSize + 5, imageY + imageSize + 5, 0x80000000);

        // Directly use a static image without GIF animation (much more reliable)
        try {
            // Use static logo texture
            Identifier imageTexture = Identifier.of("midnight", "textures/gui/logo.png");

            // Draw the texture using the simpler vanilla method
            // This method is more compatible across Minecraft versions
            RenderSystem.setShaderTexture(0, imageTexture);

            // Save matrix state
            context.getMatrices().push();

            System.out.println("Rendered static image using vanilla methods");
        } catch (Exception e) {
            System.out.println("Error rendering image: " + e.getMessage());
            e.printStackTrace();

            // If rendering fails, show a red rectangle as fallback
            context.fill(imageX, imageY, imageX + imageSize, imageY + imageSize, 0xFFFF0000);
        }

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

        // Save the ClickGUI position to the current config
        ConfigManager.saveClickGuiPosition();

        var clickGuiModule = ModuleManager.getModule("ClickGUI");
        if (clickGuiModule != null && clickGuiModule.isEnabled()) {
            clickGuiModule.toggle();
        }
        MinecraftClient.getInstance().options.getMenuBackgroundBlurriness().setValue(8);
        super.close();
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

        // Early exit if no scrollable content
        if (maxScroll <= 0) {
            moduleScrollbarTrack.setFillColor(new Color(0, 0, 0, 0));
            moduleScrollbarThumb.setFillColor(new Color(0, 0, 0, 0));
            return;
        }

        // Make scrollbar visible
        moduleScrollbarTrack.setFillColor(SCROLLBAR_TRACK_COLOR);

        // Dimensions
        float trackHeight = moduleScrollbarTrack.getHeight();

        // Use a fixed size for the thumb - this makes it more usable
        float thumbHeight = SCROLLBAR_MIN_THUMB_HEIGHT;

        // The available space where thumb can move
        float availableTrackSpace = trackHeight - thumbHeight;

        // Handle special case: force thumb to bottom when target is at max
        // This ensures the thumb reaches the bottom when you can't scroll further
        if (targetModuleScroll >= maxScroll || currentModuleScroll >= maxScroll * 0.95f) {
            moduleScrollbarThumb.attachTo(moduleScrollbarTrack, 0, availableTrackSpace);
            moduleScrollbarThumb.setFillColor(isDraggingScrollbar ? SCROLLBAR_THUMB_DRAG_COLOR : SCROLLBAR_THUMB_COLOR);
            return;
        }

        // Handle special case: force thumb to top when at top
        if (targetModuleScroll <= 0 || currentModuleScroll <= 0) {
            moduleScrollbarThumb.attachTo(moduleScrollbarTrack, 0, 0);
            moduleScrollbarThumb.setFillColor(isDraggingScrollbar ? SCROLLBAR_THUMB_DRAG_COLOR : SCROLLBAR_THUMB_COLOR);
            return;
        }

        // Normal case: calculate proportion
        float scrollProgress = currentModuleScroll / maxScroll;
        scrollProgress = Math.min(0.95f, scrollProgress); // Cap at 95% to allow forcing to bottom

        // Position the thumb proportionally
        float thumbY = scrollProgress * availableTrackSpace;

        // Check if mouse is hovering
        double mouseX = MinecraftClient.getInstance().mouse.getX() /
                MinecraftClient.getInstance().getWindow().getScaleFactor();
        double mouseY = MinecraftClient.getInstance().mouse.getY() /
                MinecraftClient.getInstance().getWindow().getScaleFactor();

        boolean hovered = mouseX >= moduleScrollbarThumb.getX() &&
                mouseX <= moduleScrollbarThumb.getX() + moduleScrollbarThumb.getWidth() &&
                mouseY >= moduleScrollbarThumb.getY() &&
                mouseY <= moduleScrollbarThumb.getY() + thumbHeight;

        // Set color and position
        Color thumbColor;
        if (isDraggingScrollbar) {
            thumbColor = SCROLLBAR_THUMB_DRAG_COLOR;
        } else if (hovered) {
            thumbColor = SCROLLBAR_THUMB_HOVER_COLOR;
        } else {
            thumbColor = SCROLLBAR_THUMB_COLOR;
        }

        moduleScrollbarThumb.setFillColor(thumbColor);
        moduleScrollbarThumb.attachTo(moduleScrollbarTrack, 0, thumbY);
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

        // Update GIF animations
        GifRenderer.updateAll();

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

        // Render the image on the right side panel
        renderImage(context);

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
                // Calculate scrollbar properties
                float trackHeight = moduleScrollbarTrack.getHeight();
                float maxScroll = calculateMaxModuleScroll();
                float totalContentHeight = maxScroll + background.getModuleSection().getHeight();
                float visiblePortionRatio = background.getModuleSection().getHeight() / totalContentHeight;
                float thumbHeight = Math.max(SCROLLBAR_MIN_THUMB_HEIGHT, visiblePortionRatio * trackHeight);
                float availableTrackSpace = trackHeight - thumbHeight;

                // Calculate the target position, accounting for thumb height
                float clickPosition = (float)(mouseY - moduleScrollbarTrack.getY());

                // If clicking above/below the possible thumb positions, adjust accordingly
                if (clickPosition < thumbHeight/2) {
                    clickPosition = 0;
                } else if (clickPosition > trackHeight - thumbHeight/2) {
                    clickPosition = availableTrackSpace;
                } else {
                    clickPosition -= thumbHeight/2;
                    clickPosition = Math.max(0, Math.min(availableTrackSpace, clickPosition));
                }

                // Convert to scroll ratio and apply
                float clickPositionRatio = clickPosition / availableTrackSpace;
                targetModuleScroll = maxScroll * clickPositionRatio;
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
            // Calculate the available track space (where thumb can move)
            float trackHeight = moduleScrollbarTrack.getHeight();
            float maxScroll = calculateMaxModuleScroll();
            float totalContentHeight = maxScroll + background.getModuleSection().getHeight();
            float visiblePortionRatio = background.getModuleSection().getHeight() / totalContentHeight;
            float thumbHeight = Math.max(SCROLLBAR_MIN_THUMB_HEIGHT, visiblePortionRatio * trackHeight);
            float availableTrackSpace = trackHeight - thumbHeight;

            // Calculate mouse position relative to track
            float dragPosition = (float)(mouseY - moduleScrollbarTrack.getY());

            // Account for thumb height by clamping drag position
            if (dragPosition < thumbHeight/2) {
                dragPosition = 0;
            } else if (dragPosition > trackHeight - thumbHeight/2) {
                dragPosition = availableTrackSpace;
            } else {
                // Adjust to account for thumb position
                dragPosition -= thumbHeight/2;
                // Ensure it's within the available space
                dragPosition = Math.max(0, Math.min(availableTrackSpace, dragPosition));
            }

            // Convert to scroll ratio (0.0 to 1.0)
            float scrollRatio = dragPosition / availableTrackSpace;

            // Apply the new scroll position
            targetModuleScroll = maxScroll * scrollRatio;
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

            // Apply scrolling with an extra step to detect reaching max scroll
            float newTargetScroll = targetModuleScroll - (float) verticalAmount * SCROLL_SPEED;

            // If we're scrolling down (verticalAmount < 0) and would exceed max,
            // explicitly set to exactly max
            if (verticalAmount < 0 && newTargetScroll > maxScroll) {
                targetModuleScroll = maxScroll;
            } else {
                targetModuleScroll = Math.max(0, Math.min(newTargetScroll, maxScroll));
            }
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

        float visibleHeight = background.getModuleSection().getHeight() - MODULES_TOP_MARGIN;
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
    public boolean shouldPause() {
        return false;
    }
}