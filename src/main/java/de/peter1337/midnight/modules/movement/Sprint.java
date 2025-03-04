package de.peter1337.midnight.modules.movement;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import java.util.Arrays;
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

    // Dropdown setting for sprint mode.
    private final Setting<String> sprintMode = register(
            new Setting<>("SprintMode", "Default", Arrays.asList("Default", "Hold", "Toggle", "Toggle2", "Toggle4"), "Select sprint mode")
    );

    // Timestamp to track the last jump.
    private long lastJumpTime = 0;

    // Toggle state variables for "Toggle" mode.
    private boolean sprintToggled = false;
    private boolean wasSprintKeyPressed = false;

    public Sprint() {
        super("Sprint", "Automatically sprints whenever possible.", Category.MOVEMENT, "b");
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            mc.player.setSprinting(true);
        }
        sprintToggled = false;
        wasSprintKeyPressed = false;
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
        sprintToggled = false;
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null) return;

        boolean shouldSprint = false;
        String mode = sprintMode.getValue();

        switch (mode) {
            case "Default":
                // Use omniSprint setting to decide based on movement input.
                if (omniSprint.getValue()) {
                    shouldSprint = mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0;
                } else {
                    shouldSprint = mc.player.input.movementForward > 0;
                }
                break;
            case "Hold":
                // Only sprint while the sprint key is held.
                shouldSprint = mc.options.sprintKey.isPressed();
                break;
            case "Toggle":
                // Toggle sprint when the sprint key is pressed.
                boolean currentlyPressed = mc.options.sprintKey.isPressed();
                if (currentlyPressed && !wasSprintKeyPressed) {
                    sprintToggled = !sprintToggled;
                }
                wasSprintKeyPressed = currentlyPressed;

                // When toggled on, use movement input (optionally omniSprint).
                if (sprintToggled) {
                    if (omniSprint.getValue()) {
                        shouldSprint = mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0;
                    } else {
                        shouldSprint = mc.player.input.movementForward > 0;
                    }
                } else {
                    shouldSprint = false;
                }
                break;
            default:
                shouldSprint = false;
                break;
        }

        // Additional checks.
        if (shouldSprint) {
            if (mc.player.isSneaking() || mc.player.isUsingItem() || mc.player.horizontalCollision) {
                shouldSprint = false;
            }
        }

        // Apply sprint state.
        mc.player.setSprinting(shouldSprint);

        // Handle JumpSprint.
        if (shouldSprint && jumpSprint.getValue() && mc.player.isOnGround()) {
            long currentTime = System.currentTimeMillis();
            float delaySeconds = jumpDelay.getValue();
            if (currentTime - lastJumpTime >= delaySeconds * 1000) {
                mc.player.jump();
                lastJumpTime = currentTime;
            }
        }

        // Debug: Uncomment to print the current sprint mode.
        // System.out.println("Sprint Mode: " + sprintMode.getValue());
    }
}
