package de.peter1337.midnight.modules.movement;

import de.peter1337.midnight.manager.BindManager;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.utils.Category;
import net.minecraft.client.MinecraftClient;

public class Sprint extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public Sprint() {
        super("Sprint", "Automatically sprints whenever possible.", Category.MOVEMENT, "b");
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null) return;

        if (mc.player.input.movementForward > 0 &&
                !mc.player.isSneaking() &&
                !mc.player.horizontalCollision) {
            mc.player.setSprinting(true);
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }
}
