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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
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

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (background != null && background.getOverlay() != null) {
            render2D.renderShapes();
        }
    }

    @Override
    public void setFocused(boolean focused) { }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
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
            // Base module height
            float moduleHeight = BUTTON_HEIGHT;

            // If expanded, add heights of all settings including dropdowns
            if (moduleButton.isExpanded()) {
                for (SettingComponent setting : moduleButton.getSettingComponents()) {
                    // Use the setting's total height which includes dropdown options if expanded
                    moduleHeight += setting.getTotalHeight() + SETTINGS_PADDING;
                }
            }

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
