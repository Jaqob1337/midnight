package de.peter1337.midnight.events;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * KeyEvent handles key event registration and polling.
 */
public class KeyEvent {
    public interface KeyCallback {
        void onKey();
    }

    private static final Map<String, RegisteredKey> keyEvents = new HashMap<>();
    // Tracks previous key states to detect transitions.
    private static final Map<Integer, Boolean> keyPressed = new HashMap<>();

    private static class RegisteredKey {
        final int keyCode;
        final String modId;
        final KeyCallback callback;

        RegisteredKey(int keyCode, String modId, KeyCallback callback) {
            this.keyCode = keyCode;
            this.modId = modId;
            this.callback = callback;
        }
    }

    /**
     * Registers a key event callback for a given module.
     *
     * @param moduleId Identifier for the module.
     * @param keyCode  GLFW key code.
     * @param modId    The mod identifier.
     * @param callback The callback to trigger on key press.
     */
    public static void register(String moduleId, int keyCode, String modId, KeyCallback callback) {
        keyEvents.put(moduleId, new RegisteredKey(keyCode, modId, callback));
    }

    /**
     * Unregisters a key event callback.
     *
     * @param moduleId Identifier for the module to remove.
     */
    public static void unregister(String moduleId) {
        keyEvents.remove(moduleId);
    }

    /**
     * Called every tick to poll key states and trigger callbacks on key press transitions.
     */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        long windowHandle = mc.getWindow().getHandle();
        keyEvents.values().forEach(reg -> {
            boolean currentlyPressed = GLFW.glfwGetKey(windowHandle, reg.keyCode) == GLFW.GLFW_PRESS;
            boolean wasPressed = keyPressed.getOrDefault(reg.keyCode, false);

            // Trigger callback only on key press transition.
            if (currentlyPressed && !wasPressed) {
                reg.callback.onKey();
            }
            keyPressed.put(reg.keyCode, currentlyPressed);
        });
    }
}
