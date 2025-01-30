package de.peter1337.midnight.manager;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.events.KeyEvent;
import de.peter1337.midnight.modules.Module;
import org.lwjgl.glfw.GLFW;
import java.util.HashMap;
import java.util.Map;

public class BindManager {
    private static final Map<String, Integer> BIND_MAP = new HashMap<>();

    static {
        // Initialize common key mappings
        BIND_MAP.put("b", GLFW.GLFW_KEY_B);
        BIND_MAP.put("v", GLFW.GLFW_KEY_V);
        // Add more as needed
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
                    module.toggle();
                    Midnight.LOGGER.info("[Bind] {} toggled by key {}", module.getName(), key);
                }
        );
        Midnight.LOGGER.info("[Bind] Registered {} to key {}", module.getName(), key);
    }

    public static void updateBind(Module module, String newKey) {
        if (module == null || newKey == null) return;

        // Remove old binding (you might need to implement this in KeyEvent)
        // KeyEvent.unregister(module.getName().toLowerCase());

        // Register new binding
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