package de.peter1337.midnight.modules.movement;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.modules.player.Scaffold;
import de.peter1337.midnight.manager.ModuleManager;
import java.util.Arrays;
import net.minecraft.client.MinecraftClient;

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
        }
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Check if Scaffold is active and blocking sprint
        if (isScaffoldBlockingSprint()) {
            mc.options.sprintKey.setPressed(false);
            return;
        }

        // Check basic conditions for sprint
        boolean canSprint = !mc.player.isSneaking() &&
                !mc.player.isUsingItem() &&
                !mc.player.horizontalCollision &&
                mc.player.getHungerManager().getFoodLevel() > 6;



        // Set sprint key state
        mc.options.sprintKey.setPressed(canSprint);

        // Handle jumping while sprinting
        if (canSprint && jumpSprint.getValue() && mc.player.isOnGround()) {
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
                return !scaffold.isSprintAllowed();
            }
        } catch (Exception e) {
            System.err.println("Error checking Scaffold sprint status: " + e.getMessage());
        }
        return false;
    }
}