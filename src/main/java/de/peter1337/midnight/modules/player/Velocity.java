package de.peter1337.midnight.modules.player;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;

import java.util.Arrays;

public class Velocity extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public final Setting<String> mode = register(
            new Setting<>("Mode", "Cancel", Arrays.asList("Cancel", "Intave"), "Velocity bypass mode")
    );

    public Velocity() {
        super("Velocity", "Modifies knockback received from attacks", Category.PLAYER, "v");
    }

    public boolean handleEntityVelocity(EntityVelocityUpdateS2CPacket packet) {
        if (!isEnabled() || mc.player == null) {
            return false;
        }

        // Check if this velocity is for the local player
        if (packet.getEntityId() != mc.player.getId()) {
            return false;
        }

        // Check the selected mode
        if (mode.getValue().equals("Cancel")) {
            // Cancel mode: cancel the packet entirely
            return true;
        } else if (mode.getValue().equals("Intave")) {
            // Intave mode: just jump when hit by an entity
            if (mc.player != null) {
                mc.player.jump();
            }
            return false;
        }

        return false;
    }

    public boolean handleExplosion(ExplosionS2CPacket packet) {
        if (!isEnabled() || mc.player == null) {
            return false;
        }

        // Check the selected mode
        if (mode.getValue().equals("Cancel")) {
            // Cancel mode: cancel the packet entirely
            return true;
        } else if (mode.getValue().equals("Intave")) {
            // Let explosion velocity through in Intave mode
            return false;
        }

        return false;
    }
}