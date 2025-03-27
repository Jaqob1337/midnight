package de.peter1337.midnight.modules.movement;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.modules.player.Scaffold; // Ensure this matches your Scaffold class package/name
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
    // Removed unimplemented "Toggle2", "Toggle4"
    private final Setting<String> sprintMode = register(
            new Setting<>("SprintMode", "Default",
                    Arrays.asList("Default", "Hold", "Toggle"), // Cleaned list
                    "Select sprint mode")
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
        // Don't force sprint immediately, let onUpdate handle it based on conditions
        sprintToggled = false; // Reset toggle state
        wasSprintKeyPressed = mc.options.sprintKey.isPressed(); // Initialize key state
    }

    @Override
    public void onDisable() {
        // Ensure sprint is turned off when the module is disabled
        if (mc.player != null && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
        sprintToggled = false; // Reset toggle state
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // --- Scaffold Interaction Check ---
        // If Scaffold is active and blocking sprint, force sprint off and stop.
        if (isScaffoldBlockingSprint()) {
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
            }
            return; // Don't proceed with Sprint module logic
        }

        // --- Determine if Sprinting is Intended ---
        boolean intendToSprint = false;
        String mode = sprintMode.getValue();

        switch (mode) {
            case "Default":
                // Check movement input based on omniSprint setting
                if (omniSprint.getValue()) {
                    intendToSprint = mc.player.input.movementForward != 0f || mc.player.input.movementSideways != 0f;
                } else {
                    intendToSprint = mc.player.input.movementForward > 0f;
                }
                break;

            case "Hold":
                // Check if the vanilla sprint key is held
                intendToSprint = mc.options.sprintKey.isPressed();
                break;

            case "Toggle":
                // Handle toggle logic based on vanilla sprint key press edge
                boolean currentlyPressed = mc.options.sprintKey.isPressed();
                if (currentlyPressed && !wasSprintKeyPressed) {
                    sprintToggled = !sprintToggled; // Flip the toggle state
                }
                wasSprintKeyPressed = currentlyPressed; // Update key state for next tick

                // If toggled on, check movement input based on omniSprint
                if (sprintToggled) {
                    if (omniSprint.getValue()) {
                        intendToSprint = mc.player.input.movementForward != 0f || mc.player.input.movementSideways != 0f;
                    } else {
                        intendToSprint = mc.player.input.movementForward > 0f;
                    }
                } else {
                    intendToSprint = false; // Not sprinting if toggled off
                }
                break;

            default:
                // Should not happen with cleaned list, but default to false
                intendToSprint = false;
                break;
        }

        // --- Check Sprinting Conditions ---
        boolean canSprint = intendToSprint &&
                !mc.player.isSneaking() &&
                !mc.player.isUsingItem() &&
                !mc.player.horizontalCollision &&
                mc.player.getHungerManager().getFoodLevel() > 6; // Added hunger check

        // --- Apply Sprint State ---
        // Set sprinting state only if it needs changing
        if (mc.player.isSprinting() != canSprint) {
            mc.player.setSprinting(canSprint);
        }


        // --- Handle JumpSprint ---
        if (canSprint && jumpSprint.getValue() && mc.player.isOnGround()) {
            long currentTime = System.currentTimeMillis();
            float delaySeconds = jumpDelay.getValue();
            // Ensure delay is not zero to avoid rapid jumping
            if (delaySeconds > 0 && currentTime - lastJumpTime >= delaySeconds * 1000) {
                mc.player.jump();
                lastJumpTime = currentTime;
            }
        }
    }

    /**
     * Checks if the Scaffold module is active and configured to disallow sprinting.
     * Relies on Scaffold having a public boolean isSprintAllowed() method.
     * @return true if Scaffold is blocking sprinting, false otherwise.
     */
    private boolean isScaffoldBlockingSprint() {
        try {
            // Find the Scaffold module instance
            // Using ModuleManager.getModule("Scaffold") might be slightly cleaner if names are unique and stable
            Module scaffoldModule = ModuleManager.getModule("Scaffold"); // Assuming "Scaffold" is the registered name

            if (scaffoldModule instanceof Scaffold scaffold && scaffold.isEnabled()) {
                // If Scaffold module exists, is enabled, and its setting disallows sprint, return true.
                return !scaffold.isSprintAllowed();
            }

            // Alternative: Iterate if getModule(name) isn't reliable
            /*
            for (Module module : ModuleManager.getModules()) {
                if (module instanceof Scaffold scaffold && module.isEnabled()) {
                    // Found enabled Scaffold module - check its preference
                    return !scaffold.isSprintAllowed();
                }
            }
            */
        } catch (Exception e) {
            // Log error if something goes wrong finding/checking the module
            System.err.println("Error checking Scaffold sprint status: " + e.getMessage());
            e.printStackTrace(); // Print stack trace for debugging
        }

        // Default: Scaffold is not blocking sprint (module not found, not enabled, or allows sprint)
        return false;
    }
}