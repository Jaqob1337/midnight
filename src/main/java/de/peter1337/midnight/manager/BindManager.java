package de.peter1337.midnight.manager;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.events.KeyEvent;
import de.peter1337.midnight.modules.Module;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * BindManager handles the registration and updating of module key binds.
 */
public class BindManager {
    // Mapping from lower-case key names to GLFW key codes.
    private static final Map<String, Integer> BIND_MAP = new HashMap<>();

    static {
        // Add all alphabet keys a to z.
        for (char c = 'a'; c <= 'z'; c++) {
            BIND_MAP.put(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'a'));
        }
        // You can add additional specific mappings here if necessary.
    }

    /**
     * Registers a key bind for a module.
     *
     * @param module The module to bind.
     * @param key    The key (as a string) to bind the module toggle.
     */
    public static void registerBind(Module module, String key) {
        if (key == null || key.isEmpty()) return;

        Integer keyCode = BIND_MAP.get(key.toLowerCase());
        if (keyCode == null) {
            Midnight.LOGGER.warn("Unknown key bind: {} for module {}", key, module.getName());
            return;
        }

        // Register the key event callback.
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

    /**
     * Updates the key bind for a module.
     *
     * @param module The module whose bind is to be updated.
     * @param newKey The new key to bind.
     */
    public static void updateBind(Module module, String newKey) {
        if (module == null || newKey == null || newKey.isEmpty()) return;

        // Unregister the old binding.
        KeyEvent.unregister(module.getName().toLowerCase());
        // Register the new binding.
        registerBind(module, newKey);
        module.setBind(newKey);
        Midnight.LOGGER.info("[Bind] Updated {} bind to {}", module.getName(), newKey);
    }

    /**
     * Returns the key name for a given GLFW key code.
     *
     * @param keyCode The GLFW key code.
     * @return The key name or "UNKNOWN" if not found.
     */
    public static String getKeyName(int keyCode) {
        for (Map.Entry<String, Integer> entry : BIND_MAP.entrySet()) {
            if (entry.getValue() == keyCode) {
                return entry.getKey();
            }
        }
        return "UNKNOWN";
    }
}
