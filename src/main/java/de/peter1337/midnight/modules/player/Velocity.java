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

    // Mode selection dropdown (Cancel, Intave)
    public final Setting<String> mode = register(
            new Setting<>("Mode", "Cancel", Arrays.asList("Cancel", "Intave"), "Velocity bypass mode")
    );

    // Delay settings for Intave mode
    private final Setting<Float> jumpDelay = register(
            new Setting<>("JumpDelay", 0.2f, 0.0f, 2.0f, "Delay before jumping (in seconds)")
                    .visibleWhen(() -> mode.getValue().equals("Intave"))
    );

    private final Setting<Boolean> randomDelay = register(
            new Setting<>("RandomDelay", Boolean.TRUE, "Add random variation to delay")
                    .visibleWhen(() -> mode.getValue().equals("Intave"))
    );

    // Flag to track when player is hit by velocity
    private boolean shouldJump = false;
    private long jumpScheduledTime = 0;

    // Track if last hit was from entity or explosion
    private boolean hitByEntity = false;

    public Velocity() {
        super("Velocity", "Modifies knockback received from attacks", Category.PLAYER, "v");
    }

    @Override
    public void onUpdate() {
        // For Intave mode: jump when hit while on ground after the delay has passed
        if (!isEnabled() || mc.player == null) return;

        // If world is loading or player isn't fully initialized, reset state
        if (mc.world == null || !mc.player.isAlive()) {
            resetState();
            return;
        }

        if (mode.getValue().equals("Intave") && shouldJump) {
            // Only process jump if hit was from an entity
            if (!hitByEntity) {
                shouldJump = false;
                return;
            }

            // Check if delay time has passed
            if (System.currentTimeMillis() >= jumpScheduledTime) {
                // Execute jump immediately when the player is on ground
                // This makes the jump more responsive
                if (mc.player.isOnGround()) {
                    mc.player.jump();
                    resetState();
                }
            }
        }
    }

    /**
     * Calculate the actual delay based on settings
     * @return delay in milliseconds
     */
    private long calculateDelay() {
        float baseDelay = jumpDelay.getValue();

        if (randomDelay.getValue()) {
            // Add random variation (±20% of the base delay)
            float variation = baseDelay * 0.2f;
            float actualDelay = baseDelay + (float)(Math.random() * variation * 2 - variation);
            return (long)(actualDelay * 1000);
        } else {
            return (long)(baseDelay * 1000);
        }
    }

    /**
     * Handles entity velocity packets (knockback from attacks)
     *
     * @param packet The velocity packet
     * @return true to cancel the packet, false to let it through
     */
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
            // Intave mode: set jump flag and schedule jump with delay
            // Don't cancel the packet, just prepare to jump
            shouldJump = true;
            hitByEntity = true;
            jumpScheduledTime = System.currentTimeMillis() + calculateDelay();
            return false; // Let the velocity packet through
        }

        return false;
    }

    /**
     * Handles explosion velocity
     *
     * @param packet The explosion packet
     * @return true to cancel, false to let through
     */
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

    @Override
    public void onEnable() {
        // Reset flags when module is enabled
        resetState();
    }

    @Override
    public void onDisable() {
        // Reset flags when module is disabled
        resetState();
    }

    /**
     * Resets the module's state
     */
    private void resetState() {
        shouldJump = false;
        hitByEntity = false;
        jumpScheduledTime = 0;
    }

    /**
     * Called when the player changes worlds or servers
     * This method should be called from the main client event handler
     */
    public void onWorldChange() {
        resetState();
    }
}