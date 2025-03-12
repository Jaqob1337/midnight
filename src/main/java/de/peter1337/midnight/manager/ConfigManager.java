package de.peter1337.midnight.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.modules.render.ClickGuiModule;
import de.peter1337.midnight.render.screens.clickgui.background.ClickGuiBackground;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static ClickGuiBackground clickGuiBackground;

    // Persist UI state only.
    public static String savedCategory = null;
    public static Map<String, Boolean> savedExpanded = new HashMap<>();
    public static float savedCategoryScroll = 0f;

    // Track the currently loaded config
    private static String currentConfigName = "default";
    private static boolean configLoaded = false;

    // Store position for ClickGUI
    private static float savedGuiX = -1;
    private static float savedGuiY = -1;

    public static void setClickGuiBackground(ClickGuiBackground background) {
        clickGuiBackground = background;

        // Check if position saving is enabled
        ClickGuiModule clickGuiModule = (ClickGuiModule) ModuleManager.getModule("ClickGUI");

        // Apply saved position if we have one and position saving is enabled
        if (savedGuiX >= 0 && savedGuiY >= 0 && clickGuiModule != null && clickGuiModule.isSavePositionEnabled()) {
            background.getBackground().setPosition(savedGuiX, savedGuiY);
            Midnight.LOGGER.info("Applied saved ClickGUI position: x={}, y={}", savedGuiX, savedGuiY);
        }
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

    /**
     * Create the default config if it doesn't exist
     */
    public static void createDefaultConfigIfNotExists() {
        if (!configExists("default")) {
            Midnight.LOGGER.info("Creating default config");
            saveConfig("default");
        }
    }

    /**
     * Load the default config
     */
    public static void loadDefaultConfig() {
        if (configExists("default")) {
            Midnight.LOGGER.info("Loading default config");
            loadConfig("default");
        } else {
            Midnight.LOGGER.info("Default config doesn't exist, creating it");
            saveConfig("default");
        }
    }

    public static void saveConfig(String configName) {
        if (!Midnight.CONFIG_DIR.exists()) {
            Midnight.CONFIG_DIR.mkdirs();
        }

        // Update the current config name
        currentConfigName = configName;
        configLoaded = true;

        File configFile = new File(Midnight.CONFIG_DIR, configName + ".json");
        JsonObject configJson = new JsonObject();

        // Save ClickGUI position if we have it
        if (clickGuiBackground != null) {
            JsonObject clickGuiJson = new JsonObject();

            // Get the current position from the background
            float x = clickGuiBackground.getBackground().getX();
            float y = clickGuiBackground.getBackground().getY();

            // Save in the config
            clickGuiJson.addProperty("x", x);
            clickGuiJson.addProperty("y", y);

            // Also update our static saved position
            savedGuiX = x;
            savedGuiY = y;

            configJson.add("clickgui", clickGuiJson);
        }

        // Save modules state and settings
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

        // Update the current config name
        currentConfigName = configName;
        configLoaded = true;

        try (FileReader reader = new FileReader(configFile)) {
            JsonObject configJson = gson.fromJson(reader, JsonObject.class);

            // Load ClickGUI position if available
            if (configJson.has("clickgui")) {
                JsonObject clickGuiJson = configJson.getAsJsonObject("clickgui");

                if (clickGuiJson.has("x") && clickGuiJson.has("y")) {
                    // Store the position to apply later when the GUI is opened
                    savedGuiX = clickGuiJson.get("x").getAsFloat();
                    savedGuiY = clickGuiJson.get("y").getAsFloat();

                    // If clickGuiBackground is already available, apply it now
                    if (clickGuiBackground != null) {
                        clickGuiBackground.getBackground().setPosition(savedGuiX, savedGuiY);
                        Midnight.LOGGER.info("Loaded and applied ClickGUI position: x={}, y={}", savedGuiX, savedGuiY);
                    } else {
                        Midnight.LOGGER.info("Loaded ClickGUI position (will apply when GUI opens): x={}, y={}", savedGuiX, savedGuiY);
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

    /**
     * Saves only the ClickGUI position to the currently loaded config
     * This is called every time the ClickGUI is closed
     */
    public static void saveClickGuiPosition() {
        // Check if position saving is enabled
        ClickGuiModule clickGuiModule = (ClickGuiModule) ModuleManager.getModule("ClickGUI");
        if (clickGuiModule == null || !clickGuiModule.isSavePositionEnabled()) {
            Midnight.LOGGER.info("ClickGUI position saving is disabled, skipping");
            return;
        }

        if (!Midnight.CONFIG_DIR.exists()) {
            Midnight.CONFIG_DIR.mkdirs();
        }

        if (clickGuiBackground == null) {
            Midnight.LOGGER.warn("Cannot save ClickGUI position: background is null");
            return;
        }

        // Get current position
        float x = clickGuiBackground.getBackground().getX();
        float y = clickGuiBackground.getBackground().getY();

        // Update our saved position
        savedGuiX = x;
        savedGuiY = y;

        // If no config is loaded yet, use default
        if (!configLoaded) {
            if (configExists("default")) {
                currentConfigName = "default";
                configLoaded = true;
            } else {
                // Create default config with just the position
                JsonObject newConfigJson = new JsonObject();

                // Add ClickGUI position
                JsonObject clickGuiJson = new JsonObject();
                clickGuiJson.addProperty("x", x);
                clickGuiJson.addProperty("y", y);
                newConfigJson.add("clickgui", clickGuiJson);

                // Empty modules object
                newConfigJson.add("modules", new JsonObject());

                File configFile = new File(Midnight.CONFIG_DIR, "default.json");
                try (FileWriter writer = new FileWriter(configFile)) {
                    gson.toJson(newConfigJson, writer);
                    Midnight.LOGGER.info("Created default config with ClickGUI position: {}", configFile.getPath());
                    currentConfigName = "default";
                    configLoaded = true;
                } catch (IOException e) {
                    e.printStackTrace();
                    Midnight.LOGGER.error("Failed to create default config: {}", e.getMessage());
                }

                return;
            }
        }

        File configFile = new File(Midnight.CONFIG_DIR, currentConfigName + ".json");

        // If the file doesn't exist yet, create it with just the position
        if (!configFile.exists()) {
            JsonObject newConfigJson = new JsonObject();

            // Add ClickGUI position
            JsonObject clickGuiJson = new JsonObject();
            clickGuiJson.addProperty("x", x);
            clickGuiJson.addProperty("y", y);
            newConfigJson.add("clickgui", clickGuiJson);

            // Empty modules object
            newConfigJson.add("modules", new JsonObject());

            try (FileWriter writer = new FileWriter(configFile)) {
                gson.toJson(newConfigJson, writer);
                Midnight.LOGGER.info("Created new config with ClickGUI position: {}", configFile.getPath());
            } catch (IOException e) {
                e.printStackTrace();
                Midnight.LOGGER.error("Failed to create new config: {}", e.getMessage());
            }

            return;
        }

        // If the file exists, update only the ClickGUI position
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject configJson = gson.fromJson(reader, JsonObject.class);

            // Update or create the clickgui section
            JsonObject clickGuiJson;
            if (configJson.has("clickgui")) {
                clickGuiJson = configJson.getAsJsonObject("clickgui");
            } else {
                clickGuiJson = new JsonObject();
                configJson.add("clickgui", clickGuiJson);
            }

            // Update position
            clickGuiJson.addProperty("x", x);
            clickGuiJson.addProperty("y", y);

            // Write back to file
            try (FileWriter writer = new FileWriter(configFile)) {
                gson.toJson(configJson, writer);
                Midnight.LOGGER.info("Updated ClickGUI position in config: {} (x={}, y={})",
                        configFile.getPath(), x, y);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Midnight.LOGGER.error("Failed to update ClickGUI position: {}", e.getMessage());
        }
    }

    /**
     * Gets the currently loaded config name
     * @return The name of the currently loaded config
     */
    public static String getCurrentConfigName() {
        return currentConfigName;
    }

    /**
     * Checks if a config has been loaded
     * @return true if a config has been loaded
     */
    public static boolean isConfigLoaded() {
        return configLoaded;
    }
}