package de.peter1337.midnight.modules.movement;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class Speed extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final Setting<Boolean> bocksmc = register(
            new Setting<>("bocksmc", Boolean.TRUE, "Strafe bhop movement")
    );

    private final Setting<Boolean> ignoreJump = register(
            new Setting<>("IgnoreJump", Boolean.TRUE, "Ignore space key when calculating speed")
    );

    // Consistent movement speed value
    private static final double MOVE_SPEED = 0.2553;

    // Track if the player is manually jumping
    private boolean isJumping = false;
    private long lastJumpTime = 0;
    private static final long JUMP_COOLDOWN = 300; // 300ms cooldown to detect manual jumps

    public Speed() {
        super("Speed", "Increases movement speed.", Category.MOVEMENT, "z");
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || !bocksmc.getValue()) return;

        // Check if player is moving
        boolean isMoving = mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0;

        // Check if the player is pressing space key (jump)
        boolean spacePressed = mc.options.jumpKey.isPressed();

        // Track manual jumping to distinguish from auto-jumps
        long currentTime = System.currentTimeMillis();
        if (spacePressed && !isJumping && currentTime - lastJumpTime > JUMP_COOLDOWN) {
            isJumping = true;
            lastJumpTime = currentTime;
        } else if (!spacePressed) {
            isJumping = false;
        }

        if (isMoving) {
            // Force sprinting
            mc.player.setSprinting(true);

            // Only auto-jump when not manually jumping and on ground
            if (mc.player.isOnGround() && (!isJumping || !ignoreJump.getValue())) {
                mc.player.jump();
            } else {
                // Apply strafe in air
                applyStrafe();
            }
        }
    }

    private void applyStrafe() {
        // Get current movement input
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        float yaw = mc.player.getYaw();

        // Get current velocity
        Vec3d currentVelocity = mc.player.getVelocity();

        // If player is manually jumping and we're ignoring jump key,
        // preserve the Y velocity but apply our speed to X and Z
        double newY = currentVelocity.y;

        // Apply the check for ignoring space key while jumping
        boolean isManualJumpPhase = isJumping && ignoreJump.getValue();

        // Handle strafe movement (A or D keys)
        if (strafe != 0) {
            // Calculate yaw in radians
            float yawRad = (float) Math.toRadians(yaw);

            // Fix: Reverse the strafe direction to correct the keys
            strafe = -strafe;

            // Calculate strafe movement components
            double strafeAngle = yawRad + (Math.PI / 2) * Math.signum(strafe);
            double strafeX = -Math.sin(strafeAngle) * MOVE_SPEED;
            double strafeZ = Math.cos(strafeAngle) * MOVE_SPEED;

            // Fix: If also moving forward or backward, merge the movements
            if (forward != 0) {
                // Calculate forward/backward movement components
                double forwardX = -Math.sin(yawRad) * 0.2 * forward;
                double forwardZ = Math.cos(yawRad) * 0.2 * forward;

                // Apply scaling factor for diagonal movement to prevent speed addition
                double diagonalScalingFactor = 0.7991; // sqrt(0.5) for diagonal movement

                // Apply combined movement (both forward/back and strafe) with scaling
                mc.player.setVelocity(
                        (strafeX + forwardX) * diagonalScalingFactor,
                        newY,
                        (strafeZ + forwardZ) * diagonalScalingFactor
                );
            } else {
                // Apply only strafe movement
                mc.player.setVelocity(strafeX, newY, strafeZ);
            }
        }
        // Handle backwards movement (S key)
        else if (forward < 0) {
            // Calculate yaw in radians
            float yawRad = (float) Math.toRadians(yaw);

            // For backwards movement, use the inverse of forward direction
            double backAngle = yawRad + Math.PI; // 180 degrees from forward

            // Calculate backward velocity components
            double backX = -Math.sin(backAngle) * MOVE_SPEED;
            double backZ = Math.cos(backAngle) * MOVE_SPEED;

            // Apply backward movement, preserving y velocity
            mc.player.setVelocity(backX, newY, backZ);
        }
        // Forward movement is handled naturally by the game
        else if (forward > 0 && isManualJumpPhase) {
            // Calculate yaw in radians
            float yawRad = (float) Math.toRadians(yaw);

            // Calculate forward velocity components
            double forwardX = -Math.sin(yawRad) * MOVE_SPEED;
            double forwardZ = Math.cos(yawRad) * MOVE_SPEED;

            // Apply forward movement while preserving y velocity during manual jumps
            mc.player.setVelocity(forwardX, newY, forwardZ);
        }
    }
}