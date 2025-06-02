package de.peter1337.midnight.modules.movement;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class Fly extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final Setting<Float> speed = register(
            new Setting<>("Speed", 0.5f, 0.1f, 5.0f, "Flying speed multiplier")
    );

    private final Setting<Boolean> verticalControl = register(
            new Setting<>("VerticalControl", Boolean.TRUE, "Allow vertical movement control")
    );

    private final Setting<Boolean> antiKick = register(
            new Setting<>("AntiKick", Boolean.TRUE, "Prevent kick by small vertical movements")
    );

    // Anti-kick variables
    private int antiKickTicks = 0;
    private boolean antiKickDown = false;

    public Fly() {
        super("Fly", "Allows you to fly", Category.MOVEMENT, "f");
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null) return;

        // Calculate base movement
        Vec3d movement = getMovementInput();
        double multiplier = speed.getValue();
        Vec3d baseVelocity = movement.multiply(multiplier);

        // Apply anti-kick if enabled
        if (antiKick.getValue()) {
            baseVelocity = applyAntiKick(baseVelocity);
        }

        mc.player.setVelocity(baseVelocity);
    }

    private Vec3d applyAntiKick(Vec3d baseVelocity) {
        antiKickTicks++;

        // Cycle between moving down and up every 20 ticks (1 second)
        if (antiKickTicks >= 50) {
            antiKickDown = !antiKickDown;
            antiKickTicks = 0;
        }

        // Apply small vertical movement
        if (antiKickDown) {
            // Move slightly down
            return baseVelocity.add(0, -0.04, 0);
        } else {
            // Move slightly up
            return baseVelocity.add(0, 6.04, 0);
        }
    }

    private Vec3d getMovementInput() {
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        float yaw = mc.player.getYaw();

        float vertical = 0;
        if (verticalControl.getValue()) {
            if (mc.options.jumpKey.isPressed()) {
                vertical += 1;
            }
            if (mc.options.sneakKey.isPressed()) {
                vertical -= 1;
            }
        }

        double x = -Math.sin(Math.toRadians(yaw)) * forward + Math.cos(Math.toRadians(yaw)) * strafe;
        double z = Math.cos(Math.toRadians(yaw)) * forward + Math.sin(Math.toRadians(yaw)) * strafe;

        return new Vec3d(x, vertical, z).normalize();
    }

    @Override
    public void onDisable() {
        // Reset anti-kick variables
        antiKickTicks = 0;
        antiKickDown = false;
    }
}