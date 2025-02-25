package de.peter1337.midnight.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.peter1337.midnight.render.screens.clickgui.background.ClickGuiBackground;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_DIR = new File("configs");
    private static ClickGuiBackground clickGuiBackground;

    // Persist UI state only.
    public static String savedCategory = null;
    public static Map<String, Boolean> savedExpanded = new HashMap<>();
    public static float savedCategoryScroll = 0f;

    public static void setClickGuiBackground(ClickGuiBackground background) {
        clickGuiBackground = background;
    }

    public static void saveConfig(String configName) {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }
        File configFile = new File(CONFIG_DIR, configName + ".json");
        JsonObject configJson = new JsonObject();

        JsonObject clickGuiJson = new JsonObject();
        clickGuiJson.addProperty("x", clickGuiBackground.getBackground().getX());
        clickGuiJson.addProperty("y", clickGuiBackground.getBackground().getY());
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

        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(configJson, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadConfig(String configName) {
        File configFile = new File(CONFIG_DIR, configName + ".json");
        if (!configFile.exists()) {
            System.out.println("Config file " + configFile.getPath() + " does not exist.");
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            JsonObject configJson = gson.fromJson(reader, JsonObject.class);
            if (configJson.has("clickgui") && clickGuiBackground != null) {
                JsonObject clickGuiJson = configJson.getAsJsonObject("clickgui");
                float x = clickGuiJson.get("x").getAsFloat();
                float y = clickGuiJson.get("y").getAsFloat();
                clickGuiBackground.getBackground().setPosition(x, y);
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
