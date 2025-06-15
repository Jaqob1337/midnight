package de.peter1337.midnight.modules.movement;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;

public class BloxdMove extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private int jumpfunny = 0;
    private int groundTicks = 0;
    private boolean jumpedLastTick = false;

    public BloxdMove() {
        super("BloxdMove", "Replicates the movement from bloxd.io.", Category.MOVEMENT, "b");
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null) return;

        // --- Ground and Jump Combo Logic ---
        if (mc.player.isOnGround()) {
            groundTicks++;
            // Reset velocity on ground to prevent sliding
            if (mc.player.getVelocity().y < 0) {
                mc.player.setVelocity(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
            }
        } else {
            groundTicks = 0;
        }

        // Reset the jump combo counter if on the ground for more than 5 ticks
        if (groundTicks > 5) {
            jumpfunny = 0;
        }

        // Detect a jump to increment the combo counter.
        // This checks if the player is on the ground and presses the jump key.
        if (mc.options.jumpKey.isPressed() && mc.player.isOnGround()) {
            if (!jumpedLastTick) {
                jumpfunny = Math.min(jumpfunny + 1, 3);
                // Apply an upward impulse similar to the snippet
                mc.player.setVelocity(mc.player.getVelocity().x, 0.42f, mc.player.getVelocity().z);
            }
            jumpedLastTick = true;
        } else {
            jumpedLastTick = false;
        }

        // --- Speed Calculation ---
        // The base speed is determined by the jump combo counter
        double speed = mc.player.isUsingItem() ? 0.06d : 0.26d + 0.025d * jumpfunny;

        // Add speed boost from the Speed potion effect
        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            int amplifier = mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier();
            speed += 0.14d * (amplifier + 1); // +1 because amplifier is 0-indexed
        }

        // --- Apply Movement and Gravity ---
        if (mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0) {
            setMoveSpeed(speed);
        }

        // Apply custom gravity (stronger downward pull) when in the air
        if (!mc.player.isOnGround()) {
            // The snippet implies a gravity multiplier. We simulate this by adding to the downward velocity.
            // A multiplier of 2.0 on standard gravity (-0.08) is -0.16. Let's apply a slightly less value for stability.
            mc.player.setVelocity(mc.player.getVelocity().add(0, -0.05, 0));
        }
    }

    private void setMoveSpeed(double speed) {
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        float yaw = mc.player.getYaw();

        if (forward == 0.0f && strafe == 0.0f) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            return;
        }

        if (forward != 0) {
            if (strafe > 0) {
                yaw += (forward > 0 ? -45 : 45);
            } else if (strafe < 0) {
                yaw += (forward > 0 ? 45 : -45);
            }
            strafe = 0;
            if (forward > 0) {
                forward = 1;
            } else if (forward < 0) {
                forward = -1;
            }
        }

        double newX = (forward * speed * Math.cos(Math.toRadians(yaw + 90.0f)) + strafe * speed * Math.sin(Math.toRadians(yaw + 90.0f)));
        double newZ = (forward * speed * Math.sin(Math.toRadians(yaw + 90.0f)) - strafe * speed * Math.cos(Math.toRadians(yaw + 90.0f)));

        mc.player.setVelocity(newX, mc.player.getVelocity().y, newZ);
    }
}