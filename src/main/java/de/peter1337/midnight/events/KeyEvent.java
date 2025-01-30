package de.peter1337.midnight.events;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import java.util.HashMap;
import java.util.Map;

import de.peter1337.midnight.Midnight;

public class KeyEvent {
    private static final Map<KeyBinding, Runnable> KEY_EVENTS = new HashMap<>();

    public static void register(String key, int keyCode, String category, Runnable action) {
        KeyBinding keyBinding = new KeyBinding(
                "key." + Midnight.MOD_ID + "." + key,
                InputUtil.Type.KEYSYM,
                keyCode,
                "category." + Midnight.MOD_ID + "." + category
        );

        KeyBindingHelper.registerKeyBinding(keyBinding);
        KEY_EVENTS.put(keyBinding, action);
        Midnight.LOGGER.info("Registered key binding: " + key);
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            KEY_EVENTS.forEach((key, action) -> {
                while(key.wasPressed()) {
                    action.run();
                }
            });
        });
        Midnight.LOGGER.info("Initialized key event system");
    }
}