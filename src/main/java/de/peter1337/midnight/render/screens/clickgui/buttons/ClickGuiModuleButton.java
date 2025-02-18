    package de.peter1337.midnight.render.screens.clickgui.buttons;

    import de.peter1337.midnight.modules.Module;
    import de.peter1337.midnight.render.Render2D;
    import de.peter1337.midnight.render.Render2D.RenderShape;
    import de.peter1337.midnight.render.screens.clickgui.setting.SettingComponent;
    import de.peter1337.midnight.render.font.CustomFontRenderer;
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
        private final int index;
        private final List<SettingComponent> settingComponents;

        private static final float BUTTON_WIDTH = 100f;
        private static final float BUTTON_HEIGHT = 20f;
        private static final float BUTTON_RADIUS = 3f;
        private static final Color BUTTON_COLOR = new Color(40, 35, 55, 255);
        private static final Color BUTTON_ENABLED_COLOR = new Color(45, 45, 65, 255);
        private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
        private static final float FONT_SIZE = 11f;
        private static final float SETTINGS_PADDING = 5f;

        public ClickGuiModuleButton(Render2D render2D, Module module, RenderShape parent, int index) {
            this.module = module;
            this.visible = false;
            this.expanded = false;
            this.fontRenderer = CustomFontRenderer.getInstanceForSize(FONT_SIZE);
            this.index = index;
            this.settingComponents = new ArrayList<>();

            float xPos = parent.getWidth() * 0.3f;
            float yPos = 25f + (index * (BUTTON_HEIGHT + 5));

            button = render2D.createRoundedRect(
                    xPos, yPos, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_RADIUS, TRANSPARENT
            );
            button.attachTo(parent, xPos, yPos);

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

        public void update() {
            if (button != null) {
                if (visible) {
                    button.setFillColor(module.isEnabled() ? BUTTON_ENABLED_COLOR : BUTTON_COLOR);
                } else {
                    button.setFillColor(TRANSPARENT);
                }
            }
        }

        public void render(DrawContext context) {
            if (!visible) return;

            // Center text within the button
            if (fontRenderer != null && button != null) {
                String moduleName = module.getName();
                float textWidth = fontRenderer.getStringWidth(moduleName);
                float fontHeight = fontRenderer.getFontHeight();

                // Calculate horizontal center
                int textX = (int)(button.getX() + (BUTTON_WIDTH - textWidth) / 2);

                // Calculate vertical center with slight upward adjustment
                int textY = (int)(button.getY() + (BUTTON_HEIGHT - fontHeight) / 2f - 2.8f);

                fontRenderer.drawStringWithShadow(
                        context.getMatrices(),
                        moduleName,
                        textX,
                        textY,
                        module.isEnabled() ? 0xFFFFFFFF : 0xAAAAAAFF,
                        0x55000000
                );
            }

            // Render settings if expanded
            if (expanded) {
                for (SettingComponent setting : settingComponents) {
                    setting.render(context);
                }
            }
        }

        public void updatePosition(float scrollOffset) {
            if (button != null && button.getParent() != null) {
                RenderShape parent = button.getParent();
                float xPos = parent.getWidth() * 0.3f;
                float yPos = 25f + (index * (getTotalHeight() + 5)) - scrollOffset;
                button.attachTo(parent, xPos, yPos);

                if (expanded) {
                    float settingYOffset = BUTTON_HEIGHT + SETTINGS_PADDING;
                    for (SettingComponent setting : settingComponents) {
                        setting.updatePosition(settingYOffset);
                        settingYOffset += setting.getHeight() + SETTINGS_PADDING;
                    }
                }
            }
        }

        public void resetPosition() {
            if (button != null && button.getParent() != null) {
                RenderShape parent = button.getParent();
                float xPos = parent.getWidth() * 0.3f;
                float yPos = 30f + (index * (BUTTON_HEIGHT + 5));
                button.attachTo(parent, xPos, yPos);
            }
        }

        public void onClick(int button) {
            if (!visible) return;

            if (button == 0) { // Left click
                module.toggle();
            } else if (button == 1) { // Right click
                expanded = !expanded;
                updateSettingsVisibility();
            }
        }

        private void updateSettingsVisibility() {
            for (SettingComponent setting : settingComponents) {
                setting.setVisible(expanded);
            }
        }

        public float getTotalHeight() {
            if (!expanded) return BUTTON_HEIGHT;

            float height = BUTTON_HEIGHT;
            for (SettingComponent setting : settingComponents) {
                height += setting.getHeight() + SETTINGS_PADDING;
            }
            return height;
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
    }