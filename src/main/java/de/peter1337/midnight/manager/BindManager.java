package de.peter1337.midnight.manager;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.events.KeyEvent;
import de.peter1337.midnight.modules.Module;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class BindManager {
    // Mapping from lower-case key names to GLFW key codes.
    private static final Map<String, Integer> BIND_MAP = new HashMap<>();

    static {
        // Use reflection to add all GLFW_KEY_* constants.
        Field[] fields = GLFW.class.getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) && field.getName().startsWith("GLFW_KEY_")) {
                try {
                    int keyCode = field.getInt(null);
                    // Remove "GLFW_KEY_" prefix and convert to lower case.
                    String keyName = field.getName().substring("GLFW_KEY_".length()).toLowerCase();
                    BIND_MAP.put(keyName, keyCode);
                } catch (IllegalAccessException e) {
                    Midnight.LOGGER.error("Error accessing GLFW key field: " + field.getName(), e);
                }
            }
        }
        // Add aliases for common shorthand keys.
        BIND_MAP.put("lshift", BIND_MAP.get("left_shift"));
        BIND_MAP.put("rshift", BIND_MAP.get("right_shift"));
        BIND_MAP.put("lcontrol", BIND_MAP.get("left_control"));
        BIND_MAP.put("rcontrol", BIND_MAP.get("right_control"));
        BIND_MAP.put("lalt", BIND_MAP.get("left_alt"));
        BIND_MAP.put("ralt", BIND_MAP.get("right_alt"));
    }

    public static void registerBind(Module module, String key) {
        if (key == null || key.isEmpty()) return;

        Integer keyCode = BIND_MAP.get(key.toLowerCase());
        if (keyCode == null) {
            Midnight.LOGGER.warn("Unknown key bind: {} for module {}", key, module.getName());
            return;
        }

        KeyEvent.register(
                module.getName().toLowerCase(),
                keyCode,
                "midnight",
                () -> {
                    Midnight.LOGGER.info("[Bind] Callback triggered for {} with key {}", module.getName(), key);
                    module.toggle();
                    Midnight.LOGGER.info("[Bind] {} toggled by key {}", module.getName(), key);
                }
        );
        Midnight.LOGGER.info("[Bind] Registered {} to key {}", module.getName(), key);
    }

    public static void updateBind(Module module, String newKey) {
        if (module == null || newKey == null || newKey.isEmpty()) return;

        KeyEvent.unregister(module.getName().toLowerCase());
        registerBind(module, newKey);
        module.setBind(newKey);
        Midnight.LOGGER.info("[Bind] Updated {} bind to {}", module.getName(), newKey);
    }

    public static String getKeyName(int keyCode) {
        for (Map.Entry<String, Integer> entry : BIND_MAP.entrySet()) {
            if (entry.getValue() == keyCode) {
                return entry.getKey();
            }
        }
        return "UNKNOWN";
    }
}
