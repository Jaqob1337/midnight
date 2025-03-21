package de.peter1337.midnight.modules.movement;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.modules.player.Scaffold;
import de.peter1337.midnight.manager.ModuleManager;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.MinecraftClient;

public class Sprint extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final Setting<Boolean> omniSprint = register(
            new Setting<>("OmniSprint", Boolean.TRUE, "Allows sprinting in any direction")
    );

    private final Setting<Boolean> jumpSprint = register(
            new Setting<>("JumpSprint", Boolean.FALSE, "Automatically jump while sprinting")
    );

    // Slider setting for jump delay in seconds, dependent on JumpSprint being enabled.
    private final Setting<Float> jumpDelay = register(
            new Setting<>("JumpDelay", 0.5f, 0.1f, 2.0f, "Delay (in seconds) between jumps")
                    .dependsOn(jumpSprint) // This setting is only visible when jumpSprint is enabled
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

            // IMPROVED CHECK: Always verify Scaffold state at the start of every update
            // If Scaffold is active and blocking sprint, don't just return - actively disable sprint
            if (isScaffoldBlockingSprint()) {
                if (mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                }
                return;
            }

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
    }

    /**
     * Checks if the Scaffold module is active and has sprint disabled
     * @return true if Scaffold is blocking sprinting
     */
    private boolean isScaffoldBlockingSprint() {
        try {
            // Find the Scaffold module in all registered modules
            for (Module module : ModuleManager.getModules()) {
                if (module instanceof Scaffold) {
                    // Found Scaffold module - check if it's enabled and blocking sprint
                    if (module.isEnabled()) {
                        Scaffold scaffold = (Scaffold) module;
                        // If Scaffold allows sprint (its sprint setting is true), return false
                        // If Scaffold blocks sprint (its sprint setting is false), return true
                        return !scaffold.isSprintAllowed();
                    }
                }
            }
        } catch (Exception e) {
            // If there's any error accessing the module, log it
            System.out.println("Error checking Scaffold sprint status: " + e.getMessage());
        }

        // Default: don't block sprint if Scaffold isn't found or enabled
        return false;
    }
}