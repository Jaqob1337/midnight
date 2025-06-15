package de.peter1337.midnight.modules.movement;

import de.peter1337.midnight.handler.RotationHandler; // Added import
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.modules.player.Scaffold;
import de.peter1337.midnight.manager.ModuleManager;
import java.util.Arrays;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper; // Added import

public class Sprint extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final Setting<Boolean> jumpSprint = register(
            new Setting<>("JumpSprint", Boolean.FALSE, "Automatically jump while sprinting")
    );

    private final Setting<Float> jumpDelay = register(
            new Setting<>("JumpDelay", 0.5f, 0.1f, 2.0f, "Delay (in seconds) between jumps")
                    .dependsOn(jumpSprint)
    );

    private long lastJumpTime = 0;

    public Sprint() {
        super("Sprint", "Automatically sprints whenever possible.", Category.MOVEMENT, "b");
    }

    @Override
    public void onEnable() {
        // Nothing specific needed on enable
    }

    @Override
    public void onDisable() {
        // Stop sprinting when module is disabled
        if (mc.player != null) {
            mc.options.sprintKey.setPressed(false);
            mc.player.setSprinting(false); // Directly set sprinting state
        }
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Check if Scaffold is active and blocking sprint
        if (isScaffoldBlockingSprint()) {
            mc.options.sprintKey.setPressed(false);
            mc.player.setSprinting(false); // Ensure sprint state is off
            return;
        }

        float clientViewForward = mc.player.input.movementForward;
        float clientViewSideways = mc.player.input.movementSideways;
        float effectiveServerForward = clientViewForward; // Default to client's view of forward

        // If RotationHandler and MoveFix are active, calculate server-relative forward movement
        // This ensures sprint decisions are made based on how the server will see the movement
        if (RotationHandler.isRotationActive() && RotationHandler.isMoveFixEnabled() && !RotationHandler.isRotatingClient()) {
            float clientYaw = mc.player.getYaw(); // Current visual yaw of the player
            float serverYaw = RotationHandler.getServerYaw(); // Target server-side yaw from RotationHandler

            // Calculate the difference in yaw and the sine/cosine for transformation
            float rotationDiff = MathHelper.wrapDegrees(serverYaw - clientYaw);
            float sinYawOffset = MathHelper.sin(rotationDiff * ((float)Math.PI / 180F));
            float cosYawOffset = MathHelper.cos(rotationDiff * ((float)Math.PI / 180F));

            // Transform the client's intended forward/sideways input into an effective forward component
            // relative to the serverYaw. This is how MovementFixMixin effectively changes input.
            effectiveServerForward = clientViewForward * cosYawOffset + clientViewSideways * sinYawOffset;
        }

        // Basic conditions required for sprinting (e.g., not sneaking, sufficient hunger)
        boolean baseSprintConditionsMet = !mc.player.isSneaking() &&
                !mc.player.isUsingItem() &&
                !mc.player.horizontalCollision && // Check for collisions
                mc.player.getHungerManager().getFoodLevel() > 6;

        // Determine if the player should sprint:
        // Base conditions must be met, AND the effective forward movement (server-relative) must be significant.
        // Vanilla Minecraft often requires input.movementForward >= 0.8F to start sprinting from a standstill.
        // Using a threshold like 0.1F is a common starting point for mods, adjust as needed.
        boolean shouldSprint = baseSprintConditionsMet && effectiveServerForward > 0.1F;

        mc.options.sprintKey.setPressed(shouldSprint); // Keep pressing the key for compatibility with some game mechanics
        mc.player.setSprinting(shouldSprint); // Directly set the player's sprinting state for more reliable control

        // Handle jumping while sprinting, using the new 'shouldSprint' logic
        if (shouldSprint && jumpSprint.getValue() && mc.player.isOnGround()) {
            long currentTime = System.currentTimeMillis();
            float delaySeconds = jumpDelay.getValue();
            if (delaySeconds > 0 && currentTime - lastJumpTime >= delaySeconds * 1000) {
                mc.player.jump();
                lastJumpTime = currentTime;
            }
        }
    }

    /**
     * Checks if the Scaffold module is active and configured to disallow sprinting.
     */
    private boolean isScaffoldBlockingSprint() {
        try {
            Module scaffoldModule = ModuleManager.getModule("Scaffold");
            if (scaffoldModule instanceof Scaffold scaffold && scaffold.isEnabled()) {
                return !scaffold.isSprintAllowed(); // isSprintAllowed() is from your Scaffold.java
            }
        } catch (Exception e) {
            System.err.println("Error checking Scaffold sprint status: " + e.getMessage());
        }
        return false;
    }
}