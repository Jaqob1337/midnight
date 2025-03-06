package de.peter1337.midnight.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.render.screens.clickgui.background.ClickGuiBackground;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static ClickGuiBackground clickGuiBackground;

    // Persist UI state only.
    public static String savedCategory = null;
    public static Map<String, Boolean> savedExpanded = new HashMap<>();
    public static float savedCategoryScroll = 0f;

    public static void setClickGuiBackground(ClickGuiBackground background) {
        clickGuiBackground = background;
    }

    /**
     * Check if a config with the given name exists
     *
     * @param configName The name of the config to check
     * @return True if the config exists, false otherwise
     */
    public static boolean configExists(String configName) {
        File configFile = new File(Midnight.CONFIG_DIR, configName + ".json");
        return configFile.exists() && configFile.isFile();
    }

    public static void saveConfig(String configName) {
        if (!Midnight.CONFIG_DIR.exists()) {
            Midnight.CONFIG_DIR.mkdirs();
        }
        File configFile = new File(Midnight.CONFIG_DIR, configName + ".json");
        JsonObject configJson = new JsonObject();

        // Save ClickGUI UI state
        JsonObject clickGuiJson = new JsonObject();
        if (clickGuiBackground != null) {
            clickGuiJson.addProperty("x", clickGuiBackground.getBackground().getX());
            clickGuiJson.addProperty("y", clickGuiBackground.getBackground().getY());
        }
        clickGuiJson.addProperty("categoryScroll", savedCategoryScroll);
        if (savedCategory != null && !savedCategory.isEmpty()) {
            clickGuiJson.addProperty("selectedCategory", savedCategory);
        }
        if (savedExpanded != null && !savedExpanded.isEmpty()) {
            JsonObject expandedJson = new JsonObject();
            for (Map.Entry<String, Boolean> entry : savedExpanded.entrySet()) {
                expandedJson.addProperty(entry.getKey(), entry.getValue());
            }
            clickGuiJson.add("expanded", expandedJson);
        }
        configJson.add("clickgui", clickGuiJson);

        // Save all modules state and settings
        JsonObject modulesJson = new JsonObject();

        for (Module module : ModuleManager.getModules()) {
            JsonObject moduleJson = new JsonObject();

            // Save module state (enabled/disabled)
            moduleJson.addProperty("enabled", module.isEnabled());

            // Save bind if exists
            if (module.getBind() != null && !module.getBind().isEmpty()) {
                moduleJson.addProperty("bind", module.getBind());
            }

            // Save all settings for this module
            if (!module.getSettings().isEmpty()) {
                JsonObject settingsJson = new JsonObject();

                for (Setting<?> setting : module.getSettings()) {
                    Object value = setting.getValue();
                    if (value != null) {
                        // Handle different setting types
                        if (value instanceof Boolean) {
                            settingsJson.addProperty(setting.getName(), (Boolean) value);
                        } else if (value instanceof Number) {
                            if (value instanceof Float) {
                                settingsJson.addProperty(setting.getName(), (Float) value);
                            } else if (value instanceof Double) {
                                settingsJson.addProperty(setting.getName(), (Double) value);
                            } else if (value instanceof Integer) {
                                settingsJson.addProperty(setting.getName(), (Integer) value);
                            } else {
                                settingsJson.addProperty(setting.getName(), value.toString());
                            }
                        } else if (value instanceof String) {
                            settingsJson.addProperty(setting.getName(), (String) value);
                        } else if (value instanceof Enum<?>) {
                            settingsJson.addProperty(setting.getName(), value.toString());
                        } else {
                            settingsJson.addProperty(setting.getName(), value.toString());
                        }
                    }
                }

                moduleJson.add("settings", settingsJson);
            }

            modulesJson.add(module.getName(), moduleJson);
        }

        configJson.add("modules", modulesJson);

        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(configJson, writer);
            Midnight.LOGGER.info("Saved config to file: {}", configFile.getPath());
        } catch (IOException e) {
            e.printStackTrace();
            Midnight.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    public static void loadConfig(String configName) {
        File configFile = new File(Midnight.CONFIG_DIR, configName + ".json");
        if (!configFile.exists()) {
            Midnight.LOGGER.warn("Config file {} does not exist.", configFile.getPath());
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            JsonObject configJson = gson.fromJson(reader, JsonObject.class);

            // Load UI state
            if (configJson.has("clickgui")) {
                JsonObject clickGuiJson = configJson.getAsJsonObject("clickgui");

                if (clickGuiBackground != null && clickGuiJson.has("x") && clickGuiJson.has("y")) {
                    float x = clickGuiJson.get("x").getAsFloat();
                    float y = clickGuiJson.get("y").getAsFloat();
                    clickGuiBackground.getBackground().setPosition(x, y);
                }

                if (clickGuiJson.has("categoryScroll")) {
                    savedCategoryScroll = clickGuiJson.get("categoryScroll").getAsFloat();
                }

                if (clickGuiJson.has("selectedCategory")) {
                    savedCategory = clickGuiJson.get("selectedCategory").getAsString();
                }

                if (clickGuiJson.has("expanded")) {
                    JsonObject expandedJson = clickGuiJson.getAsJsonObject("expanded");
                    savedExpanded.clear();
                    for (String key : expandedJson.keySet()) {
                        savedExpanded.put(key, expandedJson.get(key).getAsBoolean());
                    }
                }
            }

            // Load modules configuration
            if (configJson.has("modules")) {
                JsonObject modulesJson = configJson.getAsJsonObject("modules");

                for (Module module : ModuleManager.getModules()) {
                    String moduleName = module.getName();

                    if (modulesJson.has(moduleName)) {
                        JsonObject moduleJson = modulesJson.getAsJsonObject(moduleName);

                        // Load enabled state
                        if (moduleJson.has("enabled")) {
                            boolean shouldBeEnabled = moduleJson.get("enabled").getAsBoolean();

                            // Only toggle if the current state doesn't match the desired state
                            if (shouldBeEnabled != module.isEnabled()) {
                                module.toggle();
                                Midnight.LOGGER.info("Set module {} state to {}", moduleName, shouldBeEnabled);
                            }
                        }

                        // Load keybind
                        if (moduleJson.has("bind")) {
                            String bind = moduleJson.get("bind").getAsString();
                            if (!bind.equals(module.getBind())) {
                                BindManager.updateBind(module, bind);
                                Midnight.LOGGER.info("Updated bind for {} to {}", moduleName, bind);
                            }
                        }

                        // Load settings
                        if (moduleJson.has("settings")) {
                            JsonObject settingsJson = moduleJson.getAsJsonObject("settings");

                            for (Setting<?> setting : module.getSettings()) {
                                String settingName = setting.getName();

                                if (settingsJson.has(settingName)) {
                                    JsonElement settingElement = settingsJson.get(settingName);
                                    try {
                                        // Skip if value is already the same to avoid unnecessary setting changes
                                        if (setting.getValue() instanceof Boolean &&
                                                setting.getValue().equals(settingElement.getAsBoolean())) {
                                            continue;
                                        } else if (setting.getValue() instanceof Float &&
                                                setting.getValue().equals(settingElement.getAsFloat())) {
                                            continue;
                                        } else if (setting.getValue() instanceof Double &&
                                                setting.getValue().equals(settingElement.getAsDouble())) {
                                            continue;
                                        } else if (setting.getValue() instanceof Integer &&
                                                setting.getValue().equals(settingElement.getAsInt())) {
                                            continue;
                                        } else if (setting.getValue() instanceof String &&
                                                setting.getValue().equals(settingElement.getAsString())) {
                                            continue;
                                        }

                                        // Apply the setting value based on type
                                        if (setting.getValue() instanceof Boolean) {
                                            setting.setValue(settingElement.getAsBoolean());
                                        } else if (setting.getValue() instanceof Float) {
                                            setting.setValue(settingElement.getAsFloat());
                                        } else if (setting.getValue() instanceof Double) {
                                            setting.setValue(settingElement.getAsDouble());
                                        } else if (setting.getValue() instanceof Integer) {
                                            setting.setValue(settingElement.getAsInt());
                                        } else if (setting.getValue() instanceof String) {
                                            setting.setValue(settingElement.getAsString());
                                        } else {
                                            setting.setValue(settingElement.getAsString());
                                        }
                                        Midnight.LOGGER.info("Loaded setting {}:{} = {}",
                                                moduleName, settingName, settingElement);
                                    } catch (Exception e) {
                                        Midnight.LOGGER.error("Failed to load setting {}:{}: {}",
                                                moduleName, settingName, e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Midnight.LOGGER.info("Successfully loaded config: {}", configFile.getPath());
        } catch (IOException e) {
            e.printStackTrace();
            Midnight.LOGGER.error("Failed to load config: {}", e.getMessage());
        }
    }
}