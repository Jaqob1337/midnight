package de.peter1337.midnight.modules.combat;

import de.peter1337.midnight.handler.RotationHandler;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Aura extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // Current target entities
    private List<Entity> targetEntities = new ArrayList<>();
    private Entity primaryTarget = null;

    // The rotation priority for this module
    private static final int ROTATION_PRIORITY = 100;

    // Settings
    private final Setting<Float> range = register(
            new Setting<>("Range", 4.0f, 1.0f, 6.0f, "Attack range in blocks")
    );

    private final Setting<Float> cps = register(
            new Setting<>("CPS", 10.0f, 1.0f, 20.0f, "Clicks per second")
    );

    private final Setting<Boolean> randomCps = register(
            new Setting<>("RandomCPS", Boolean.TRUE, "Add randomization to CPS")
    );

    private final Setting<String> targetMode = register(
            new Setting<>("TargetMode", "Closest",
                    List.of("Closest", "Health", "Angle"),
                    "Method used to prioritize targets")
    );

    private final Setting<Boolean> targetPlayers = register(
            new Setting<>("TargetPlayers", Boolean.TRUE, "Target other players")
    );

    private final Setting<Boolean> targetHostiles = register(
            new Setting<>("TargetHostiles", Boolean.TRUE, "Target hostile mobs")
    );

    private final Setting<Boolean> targetPassives = register(
            new Setting<>("TargetPassives", Boolean.FALSE, "Target passive mobs")
    );

    private final Setting<String> rotationMode = register(
            new Setting<>("RotationMode", "Silent",
                    List.of("Silent", "Client", "Body"),
                    "Silent: server-only, Client: visible, Body: shows on body only")
    );

    private final Setting<Float> rotationSpeed = register(
            new Setting<>("RotationSpeed", 0.3f, 0.1f, 1.0f, "Rotation speed factor")
    );

    private final Setting<Boolean> throughWalls = register(
            new Setting<>("ThroughWalls", Boolean.FALSE, "Attack through walls")
    );

    private final Setting<Boolean> autoBlock = register(
            new Setting<>("AutoBlock", Boolean.FALSE, "Automatically block with shield")
    );

    private final Setting<Boolean> smartAttack = register(
            new Setting<>("SmartAttack", Boolean.TRUE, "Only attack when your weapon is fully charged")
    );

    private final Setting<Boolean> rotateBack = register(
            new Setting<>("RotateBack", Boolean.FALSE, "Rotate back to original position after attacking")
    );

    private final Setting<Float> fovCheck = register(
            new Setting<>("FOVCheck", 180f, 30f, 360f, "Field of view angle for target visibility")
    );

    private long lastAttackTime = 0;
    private boolean rotating = false;
    private float originalYaw;
    private float originalPitch;

    public Aura() {
        super("Aura", "Automatically attacks nearby entities", Category.COMBAT, "r");
    }

    @Override
    public void onEnable() {
        targetEntities.clear();
        primaryTarget = null;
        rotating = false;

        if (mc.player != null) {
            originalYaw = mc.player.getYaw();
            originalPitch = mc.player.getPitch();
        }
    }

    @Override
    public void onDisable() {
        targetEntities.clear();
        primaryTarget = null;

        // Cancel rotations when disabling
        RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
        rotating = false;

        // Rotate back to original position if enabled
        if (rotateBack.getValue() && mc.player != null) {
            mc.player.setYaw(originalYaw);
            mc.player.setPitch(originalPitch);
        }
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Calculate attack delay based on CPS
        long currentTime = System.currentTimeMillis();
        float baseDelay = 1000.0f / cps.getValue(); // Convert CPS to milliseconds

        // Add randomization if enabled (±30% variation)
        long attackDelay;
        if (randomCps.getValue()) {
            float variation = baseDelay * 0.3f;
            attackDelay = (long)(baseDelay + (Math.random() * variation * 2 - variation));
        } else {
            attackDelay = (long)baseDelay;
        }

        boolean canAttack = currentTime - lastAttackTime >= attackDelay;

        // Update original rotation if not actively targeting
        if (!rotating && mc.player != null) {
            originalYaw = mc.player.getYaw();
            originalPitch = mc.player.getPitch();
        }

        // Find potential targets
        findTargets();

        // If we have targets, handle rotations and attacks
        if (!targetEntities.isEmpty() && primaryTarget != null) {
            // Check attack cooldown if smart attack enabled
            boolean cooldownReady = !smartAttack.getValue() ||
                    (mc.player.getAttackCooldownProgress(0.0f) >= 0.9f);

            // Handle rotations to the primary target
            handleRotations();

            // Attack if rotation is complete, cooldown is ready, and we can attack
            if (canAttack && cooldownReady && RotationHandler.isRotationActive()) {
                // Attack the primary target
                attack(primaryTarget);
                lastAttackTime = currentTime;

                // Handle auto-blocking if enabled
                if (autoBlock.getValue()) {
                    tryBlock();
                }
            }
        } else {
            // No targets, cancel pending rotations
            RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
            rotating = false;

            // Rotate back to original position if enabled
            if (rotateBack.getValue() && mc.player != null) {
                mc.player.setYaw(originalYaw);
                mc.player.setPitch(originalPitch);
            }
        }
    }

    /**
     * Finds and prioritizes potential targets
     */
    private void findTargets() {
        targetEntities.clear();
        primaryTarget = null;

        if (mc.player == null || mc.world == null) return;

        List<Entity> potentialTargets = new ArrayList<>();
        double rangeSq = range.getValue() * range.getValue();

        // Collect potential targets
        for (Entity entity : mc.world.getEntities()) {
            // Basic entity checks
            if (!(entity instanceof LivingEntity)) continue;
            if (entity == mc.player) continue;
            if (entity.isRemoved()) continue;

            LivingEntity livingEntity = (LivingEntity) entity;
            if (livingEntity.isDead() || livingEntity.getHealth() <= 0) continue;

            // Check entity type filters
            if (entity instanceof PlayerEntity) {
                if (!targetPlayers.getValue()) continue;
                if (((PlayerEntity) entity).isSpectator()) continue;
            } else if (entity instanceof HostileEntity) {
                if (!targetHostiles.getValue()) continue;
            } else if (entity instanceof PassiveEntity) {
                if (!targetPassives.getValue()) continue;
            } else {
                // Skip other entity types
                continue;
            }

            // Check distance
            if (mc.player.squaredDistanceTo(entity) > rangeSq) continue;

            // Skip if not visible and through walls is disabled
            if (!throughWalls.getValue() && !mc.player.canSee(entity)) continue;

            // Check if entity is within the player's field of view
            if (fovCheck.getValue() < 360f) {
                // Calculate angle between player's look direction and entity position
                Vec3d playerLook = mc.player.getRotationVec(1.0f);
                Vec3d entityDir = entity.getPos().subtract(mc.player.getEyePos()).normalize();
                double dot = playerLook.dotProduct(entityDir);
                double angleDegrees = Math.toDegrees(Math.acos(dot));

                // Skip if outside the FOV check setting
                if (angleDegrees > fovCheck.getValue() / 2) continue;
            }

            // Add to potential targets
            potentialTargets.add(entity);
        }

        // Sort potential targets based on the targeting mode
        switch (targetMode.getValue()) {
            case "Closest":
                potentialTargets.sort(Comparator.comparingDouble(mc.player::squaredDistanceTo));
                break;

            case "Health":
                potentialTargets.sort(Comparator.comparingDouble(e -> ((LivingEntity) e).getHealth()));
                break;

            case "Angle":
                // Sort by angle difference to current look
                if (mc.player != null) {
                    float playerYaw = mc.player.getYaw();
                    float playerPitch = mc.player.getPitch();

                    potentialTargets.sort(Comparator.comparingDouble(entity -> {
                        float[] rotations = RotationHandler.calculateLookAt(entity.getEyePos());
                        float yawDiff = MathHelper.wrapDegrees(rotations[0] - playerYaw);
                        float pitchDiff = MathHelper.wrapDegrees(rotations[1] - playerPitch);
                        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
                    }));
                }
                break;

            default:
                potentialTargets.sort(Comparator.comparingDouble(mc.player::squaredDistanceTo));
                break;
        }

        // Set targets
        targetEntities = potentialTargets;

        // Set primary target for rotation (first target)
        if (!targetEntities.isEmpty()) {
            primaryTarget = targetEntities.get(0);
        }
    }

    /**
     * Handles rotations to the primary target
     */
    private void handleRotations() {
        if (primaryTarget == null) return;

        // Get the position to aim at (eye height for players, center for other entities)
        Vec3d aimPos;
        if (primaryTarget instanceof PlayerEntity) {
            aimPos = primaryTarget.getEyePos();
        } else {
            aimPos = primaryTarget.getPos().add(0, primaryTarget.getHeight() / 2, 0);
        }

        // Calculate raw target angles
        float[] targetAngles = RotationHandler.calculateLookAt(aimPos);

        // Apply smoother interpolation
        float[] currentAngles;
        if (mc.player != null) {
            currentAngles = new float[]{mc.player.getYaw(), mc.player.getPitch()};

            // Calculate differences
            float yawDiff = MathHelper.wrapDegrees(targetAngles[0] - currentAngles[0]);
            float pitchDiff = targetAngles[1] - currentAngles[1];

            // Apply speed factor
            float yawChange = yawDiff * rotationSpeed.getValue();
            float pitchChange = pitchDiff * rotationSpeed.getValue();

            // Clamp to avoid very small changes that never reach target
            if (Math.abs(yawDiff) < 1.0f) yawChange = yawDiff;
            if (Math.abs(pitchDiff) < 1.0f) pitchChange = pitchDiff;

            // Smooth angles
            float smoothYaw = currentAngles[0] + yawChange;
            float smoothPitch = MathHelper.clamp(currentAngles[1] + pitchChange, -90f, 90f);

            // Handle different rotation modes
            boolean silent;
            boolean bodyOnly;

            switch (rotationMode.getValue()) {
                case "Silent":
                    // Server-only rotation, camera doesn't move
                    silent = true;
                    bodyOnly = false;
                    break;

                case "Client":
                    // Full visible rotation, camera moves
                    silent = false;
                    bodyOnly = false;
                    break;

                case "Body":
                    // Body rotation but camera stays still
                    silent = true;
                    bodyOnly = true;
                    break;

                default:
                    // Default to silent mode
                    silent = true;
                    bodyOnly = false;
                    break;
            }

            // Send rotation request
            RotationHandler.requestRotation(
                    smoothYaw,
                    smoothPitch,
                    ROTATION_PRIORITY,
                    100, // Shorter duration allows more frequent updates
                    silent,
                    bodyOnly,
                    state -> rotating = true
            );
        }
    }

    /**
     * Attacks the target entity
     */
    private void attack(Entity target) {
        // Make sure we're still in range
        if (mc.player.distanceTo(target) <= range.getValue()) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    /**
     * Attempts to block with a shield after attacking
     */
    private void tryBlock() {
        // TODO: Implement shield blocking logic
        // Check if player has a shield in offhand or mainhand
        // and use mc.options.useKey to start blocking
    }
}