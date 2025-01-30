package de.peter1337.midnight.modules.movement;

import de.peter1337.midnight.utils.Category;
import de.peter1337.midnight.utils.Setting;
import de.peter1337.midnight.modules.Module;
import net.minecraft.client.MinecraftClient;

public class Sprint extends Module {
    private final Setting<Boolean> alwaysSprinting = register(
            new Setting<>("Always Sprinting", true, "If enabled, sprint all the time when moving.")
    );

    public Sprint() {
        super("Sprint", "Automatically sprints whenever possible.", Category.MOVEMENT, "b");
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || MinecraftClient.getInstance().player == null) return;

        if (alwaysSprinting.getValue()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player.forwardSpeed > 0) {
                mc.player.setSprinting(true);
            }
        }
    }

    @Override
    public void onDisable() {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.setSprinting(false);
        }
    }
}