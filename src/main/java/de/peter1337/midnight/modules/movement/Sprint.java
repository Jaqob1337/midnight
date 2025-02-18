package de.peter1337.midnight.modules.movement;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import net.minecraft.client.MinecraftClient;

public class Sprint extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final Setting<Boolean> omniSprint = register(
            new Setting<>("OmniSprint", Boolean.TRUE, "Allows sprinting in any direction")
    );

    private final Setting<Boolean> jumpSprint = register(
            new Setting<>("JumpSprint", Boolean.FALSE, "Automatically jump while sprinting")
    );

    // Slider setting for jump delay in seconds.
    private final Setting<Float> jumpDelay = register(
            new Setting<>("JumpDelay", 0.5f, 0.1f, 2.0f, "Delay (in seconds) between jumps")
    );

    // Timestamp to track the last jump
    private long lastJumpTime = 0;

    public Sprint() {
        super("Sprint", "Automatically sprints whenever possible.", Category.MOVEMENT, "b");
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            mc.player.setSprinting(true);
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null) return;

        boolean shouldSprint;

        // Check OmniSprint setting
        if (omniSprint.getValue()) {
            shouldSprint = mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0;
        } else {
            shouldSprint = mc.player.input.movementForward > 0;
        }

        // Basic sprint checks
        if (shouldSprint) {
            if (mc.player.isSneaking() || mc.player.isUsingItem() || mc.player.horizontalCollision) {
                shouldSprint = false;
            }
        }

        // Apply sprint state
        mc.player.setSprinting(shouldSprint);

        // Handle JumpSprint - Automatically jump when sprinting, on ground, and after the jump delay.
        if (shouldSprint && jumpSprint.getValue() && mc.player.isOnGround()) {
            long currentTime = System.currentTimeMillis();
            float delaySeconds = jumpDelay.getValue(); // jumpDelay is in seconds
            if (currentTime - lastJumpTime >= delaySeconds * 1000) {
                mc.player.jump();
                lastJumpTime = currentTime;
            }
        }
    }
}
