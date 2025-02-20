package de.peter1337.midnight.render.screens.clickgui;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.render.GuiScreen;
import de.peter1337.midnight.render.screens.clickgui.background.ClickGuiBackground;
import de.peter1337.midnight.render.screens.clickgui.buttons.ClickGuiCategoryButton;
import de.peter1337.midnight.render.screens.clickgui.buttons.ClickGuiModuleButton;
import de.peter1337.midnight.render.screens.clickgui.setting.SettingComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.*;

public class ClickGuiScreen extends GuiScreen {

    private ClickGuiBackground background;
    private final List<ClickGuiCategoryButton> categoryButtons = new ArrayList<>();
    private final Map<Category, List<ClickGuiModuleButton>> moduleButtons = new HashMap<>();
    private Category selectedCategory = null;

    private float targetCategoryScroll = 0f;
    private float currentCategoryScroll = 0f;
    private float targetModuleScroll = 0f;
    private float currentModuleScroll = 0f;
    private static final float SCROLL_SPEED = 15f;
    private static final float SCROLL_ANIMATION_SPEED = 0.02f;
    private static final float CATEGORY_SPACING = 60f;
    private static final float INITIAL_CATEGORY_OFFSET_Y = 11f;
    private static final float MODULES_TOP_MARGIN = 20f;
    private static final float CATEGORY_GAP = 10f;
    private static final float ICON_SCALE = 0.9f;

    public ClickGuiScreen() {
        super(Text.literal("ClickGUI"));
    }

    @Override
    public void init() {
        super.init();
        MinecraftClient.getInstance().options.getMenuBackgroundBlurriness().setValue(0);

        background = new ClickGuiBackground(render2D, width, height);
        categoryButtons.clear();
        moduleButtons.clear();

        targetCategoryScroll = 0f;
        currentCategoryScroll = 0f;
        targetModuleScroll = 0f;
        currentModuleScroll = 0f;

        initializeCategories();
        initializeModules();

        if (selectedCategory != null) {
            categoryButtons.forEach(catButton ->
                    catButton.setSelected(catButton.getCategory() == selectedCategory));
            moduleButtons.get(selectedCategory).forEach(moduleButton ->
                    moduleButton.setVisible(true));
        }

        render2D.setClipBounds(background.getBackground());
    }

    private void initializeCategories() {
        float currentOffsetY = INITIAL_CATEGORY_OFFSET_Y;
        for (Category category : Category.values()) {
            ClickGuiCategoryButton categoryButton = new ClickGuiCategoryButton(
                    render2D,
                    category,
                    background.getBackground(),
                    20f,              // offsetX
                    currentOffsetY,   // y-coordinate
                    CATEGORY_GAP,     // gap between buttons
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

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (background != null && background.getOverlay() != null) {
            render2D.renderShapes();
        }
    }

    @Override
    public void setFocused(boolean focused) {
        // Prevent screen dimming.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Animate scrolls.
        currentCategoryScroll += (targetCategoryScroll - currentCategoryScroll) * SCROLL_ANIMATION_SPEED;
        currentModuleScroll += (targetModuleScroll - currentModuleScroll) * SCROLL_ANIMATION_SPEED;

        // Update category button positions.
        categoryButtons.forEach(button -> button.updatePosition(currentCategoryScroll));

        // Update module buttons for the selected category.
        if (selectedCategory != null) {
            float cumulativeY = MODULES_TOP_MARGIN - currentModuleScroll;
            for (ClickGuiModuleButton moduleButton : moduleButtons.get(selectedCategory)) {
                moduleButton.updatePosition(cumulativeY);
                cumulativeY += moduleButton.getTotalHeight() + 5f;  // 5f is spacing between module buttons.
            }
        }

        super.render(context, mouseX, mouseY, delta);

        // Render categories.
        categoryButtons.forEach(button -> {
            button.updateColor();
            button.render(context);
        });

        // Render modules.
        moduleButtons.values().stream()
                .flatMap(List::stream)
                .forEach(moduleButton -> {
                    moduleButton.update();
                    moduleButton.render(context);
                });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // First, check module buttons and their settings.
        if (selectedCategory != null) {
            List<ClickGuiModuleButton> currentModules = moduleButtons.get(selectedCategory);
            for (ClickGuiModuleButton moduleButton : currentModules) {
                if (moduleButton.isHovered(mouseX, mouseY)) {
                    moduleButton.onClick(button);
                    return true;
                }
                // Also check settings if the module is expanded.
                for (SettingComponent setting : moduleButton.getSettingComponents()) {
                    if (setting.isHovered(mouseX, mouseY)) {
                        setting.onClick(mouseX, mouseY);
                        return true;
                    }
                }
            }
        }

        // Then check category buttons.
        for (ClickGuiCategoryButton categoryButton : categoryButtons) {
            if (categoryButton.isHovered(mouseX, mouseY)) {
                if (selectedCategory != categoryButton.getCategory()) {
                    if (selectedCategory != null) {
                        moduleButtons.get(selectedCategory).forEach(moduleButton ->
                                moduleButton.setVisible(false));
                    }
                    selectedCategory = categoryButton.getCategory();
                    targetModuleScroll = 0f;
                    currentModuleScroll = 0f;
                    moduleButtons.get(selectedCategory).forEach(moduleButton -> {
                        moduleButton.setVisible(true);
                    });
                    categoryButtons.forEach(catButton ->
                            catButton.setSelected(catButton.getCategory() == selectedCategory));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (selectedCategory != null) {
            List<ClickGuiModuleButton> currentModules = moduleButtons.get(selectedCategory);
            for (ClickGuiModuleButton moduleButton : currentModules) {
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
        if (selectedCategory != null) {
            List<ClickGuiModuleButton> currentModules = moduleButtons.get(selectedCategory);
            for (ClickGuiModuleButton moduleButton : currentModules) {
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
            targetCategoryScroll = Math.max(0, Math.min(targetCategoryScroll - (float)verticalAmount * SCROLL_SPEED, maxScroll));
            return true;
        } else if (selectedCategory != null && isMouseOverModules(mouseX, mouseY)) {
            float maxScroll = calculateMaxModuleScroll();
            targetModuleScroll = Math.max(0, Math.min(targetModuleScroll - (float)verticalAmount * SCROLL_SPEED, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private float calculateMaxModuleScroll() {
        if (selectedCategory == null) return 0;
        float totalHeight = 0;
        for (ClickGuiModuleButton moduleButton : moduleButtons.get(selectedCategory)) {
            totalHeight += moduleButton.getTotalHeight() + 5;
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
        MinecraftClient.getInstance().options.getMenuBackgroundBlurriness().setValue(8);
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
