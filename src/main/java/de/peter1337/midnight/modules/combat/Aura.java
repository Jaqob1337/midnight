package de.peter1337.midnight.modules.combat;

import de.peter1337.midnight.handler.RotationHandler;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.utils.RaytraceUtil;
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
    private Vec3d targetPoint = null;

    // Target motion tracking variables (defined at class level)
    private double targetMovementSpeed = 0.0;
    private float targetDistance = 0.0f;

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
            new Setting<>("RotationSpeed", 0.7f, 0.1f, 1.0f, "Rotation speed factor")
    );

    private final Setting<Boolean> predictiveAim = register(
            new Setting<>("PredictiveAim", Boolean.TRUE, "Predict target movement for more accurate rotations")
    );

    private final Setting<Float> aimPrediction = register(
            new Setting<>("AimPrediction", 0.5f, 0.1f, 2.0f, "Prediction strength for target movement")
                    .dependsOn(predictiveAim)
    );

    private final Setting<Boolean> throughWalls = register(
            new Setting<>("ThroughWalls", Boolean.FALSE, "Attack through walls")
    );

    private final Setting<Boolean> rayTrace = register(
            new Setting<>("RayTrace", Boolean.TRUE, "Only attack if server rotations are looking at the target")
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

    // Properties to track state
    private long lastAttackTime = 0;
    private boolean rotating = false;
    private float originalYaw;
    private float originalPitch;
    private boolean attackedThisTick = false;

    public Aura() {
        super("Aura", "Automatically attacks nearby entities", Category.COMBAT, "r");
    }

    @Override
    public void onEnable() {
        targetEntities.clear();
        primaryTarget = null;
        targetPoint = null;
        rotating = false;
        targetMovementSpeed = 0.0;
        targetDistance = 0.0f;

        if (mc.player != null) {
            originalYaw = mc.player.getYaw();
            originalPitch = mc.player.getPitch();
        }
    }

    @Override
    public void onDisable() {
        targetEntities.clear();
        primaryTarget = null;
        targetPoint = null;
        targetMovementSpeed = 0.0;
        targetDistance = 0.0f;

        // Cancel rotations when disabling
        RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
        rotating = false;

        // Rotate back to original position if enabled
        if (rotateBack.getValue() && mc.player != null) {
            mc.player.setYaw(originalYaw);
            mc.player.setPitch(originalPitch);
        }
    }

    /**
     * Method called during START_CLIENT_TICK
     */
    public void preUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Process attack logic in PRE tick
        processAttack();

        // Set the flag to indicate we've already attacked in this tick
        attackedThisTick = true;
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Skip attack logic if already processed in PRE tick
        if (attackedThisTick) {
            attackedThisTick = false; // Reset for next tick
            return;
        }

        // Process attack logic in POST tick if not already done in PRE
        processAttack();
    }

    /**
     * Processes the attack logic
     */
    private void processAttack() {
        // Calculate attack delay based on CPS
        long currentTime = System.currentTimeMillis();
        float baseDelay = 600.0f / cps.getValue(); // Convert CPS to milliseconds

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
            // Update target metrics for this tick
            updateTargetMetrics();

            // Check attack cooldown if smart attack enabled
            float cooldownProgress = mc.player.getAttackCooldownProgress(0.0f);
            boolean cooldownReady = !smartAttack.getValue() || cooldownProgress >= 0.9f;

            // Find a visible point on the target to aim at
            targetPoint = findVisiblePoint(primaryTarget);

            // If no visible point and we're not allowed to attack through walls, skip
            if (targetPoint == null && !throughWalls.getValue()) {
                return;
            }

            // Use center point if no visible point and throughWalls is enabled
            if (targetPoint == null) {
                targetPoint = primaryTarget.getPos().add(0, primaryTarget.getHeight() / 2, 0);
            }

            // Handle rotations to the primary target
            handleRotations();

            // Validate that the server rotations are actually looking at the entity if rayTrace is enabled
            boolean canHit = true;
            if (rayTrace.getValue() && RotationHandler.isRotationActive()) {
                float serverYaw = RotationHandler.getServerYaw();
                float serverPitch = RotationHandler.getServerPitch();

                // Use a more tolerant raytrace for fast-moving targets
                if (targetMovementSpeed > 0.15) {
                    // Create a slightly expanded hitbox for fast-moving targets
                    Box expandedBox = primaryTarget.getBoundingBox().expand(0.2);
                    Vec3d eyePos = mc.player.getEyePos();
                    Vec3d lookVec = RaytraceUtil.getVectorForRotation(serverPitch, serverYaw);
                    Vec3d endPos = eyePos.add(lookVec.multiply(range.getValue() + 1));

                    // Check if rotations would hit the expanded hitbox
                    canHit = RaytraceUtil.rayTraceEntityBox(eyePos, endPos, expandedBox) != null;
                } else {
                    // Normal raytrace for slower targets
                    canHit = RaytraceUtil.isEntityInServerView(primaryTarget, serverYaw, serverPitch, range.getValue() + 1);
                }
            }

            // Attack if rotation is complete, cooldown is ready, and we can attack
            if (canAttack && cooldownReady && RotationHandler.isRotationActive() && canHit) {
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
     * Updates metrics about the current target
     */
    private void updateTargetMetrics() {
        if (primaryTarget == null || mc.player == null) return;

        // Update target distance
        targetDistance = (float)mc.player.getPos().distanceTo(primaryTarget.getPos());

        // Update movement speed
        targetMovementSpeed = Math.sqrt(
                Math.pow(primaryTarget.getX() - primaryTarget.prevX, 2) +
                        Math.pow(primaryTarget.getZ() - primaryTarget.prevZ, 2)
        );
    }

    /**
     * Finds and prioritizes potential targets
     */
    private void findTargets() {
        targetEntities.clear();
        primaryTarget = null;
        targetPoint = null;

        if (mc.player == null || mc.world == null) return;

        List<Entity> potentialTargets = new ArrayList<>();
        double rangeSq = range.getValue() * range.getValue();

        // Search for entities within range
        for (Entity entity : mc.world.getEntities()) {
            // Skip ineligible entities
            if (!(entity instanceof LivingEntity livingEntity)) continue;
            if (entity == mc.player) continue;
            if (entity.isRemoved()) continue;
            if (livingEntity.isDead() || livingEntity.getHealth() <= 0) continue;

            // Apply entity type filters
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

            // Check if entity is visible through walls
            boolean isVisible = RaytraceUtil.canSeeEntity(entity);
            if (!isVisible && !throughWalls.getValue()) continue;

            // Check if entity is within the player's field of view
            if (fovCheck.getValue() < 360f) {
                // Calculate angle between player's look direction and entity position
                Vec3d playerLook = mc.player.getRotationVec(1.0f);
                Vec3d entityDir = entity.getPos().subtract(mc.player.getEyePos()).normalize();
                double dot = playerLook.dotProduct(entityDir);
                double angleDegrees = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));

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
        }

        // Set targets
        targetEntities = potentialTargets;

        // Set primary target for rotation (first target)
        if (!targetEntities.isEmpty()) {
            primaryTarget = targetEntities.get(0);
        }
    }

    /**
     * Finds a point on the entity that is visible to the player for precise targeting
     * @param entity The target entity
     * @return A visible position on the entity, or null if none found
     */
    private Vec3d findVisiblePoint(Entity entity) {
        Vec3d visiblePoint = RaytraceUtil.getVisiblePoint(entity);

        // Apply predictive aiming if enabled
        if (visiblePoint != null && predictiveAim.getValue() && entity.distanceTo(mc.player) > 2.0) {
            // Calculate velocity of the target entity
            Vec3d velocity = new Vec3d(
                    entity.getX() - entity.prevX,
                    entity.getY() - entity.prevY,
                    entity.getZ() - entity.prevZ
            );

            // Only predict horizontal movement for more stable aiming
            Vec3d prediction = new Vec3d(
                    velocity.x * aimPrediction.getValue() * 10.0,
                    velocity.y * aimPrediction.getValue() * 2.5, // Less vertical prediction
                    velocity.z * aimPrediction.getValue() * 10.0
            );

            // Apply prediction to the visible point
            visiblePoint = visiblePoint.add(prediction);

            // Make sure the prediction still aims at the entity's hitbox
            Box expandedBox = entity.getBoundingBox().expand(0.4); // Slightly expand hitbox for prediction tolerance
            if (!expandedBox.contains(visiblePoint)) {
                // If prediction is outside hitbox, clamp it to the edge of the hitbox
                visiblePoint = RaytraceUtil.rayTraceEntityBox(mc.player.getEyePos(), visiblePoint, expandedBox);

                // If still null, fall back to the original point
                if (visiblePoint == null) {
                    visiblePoint = RaytraceUtil.getVisiblePoint(entity);
                }
            }
        }

        return visiblePoint;
    }

    /**
     * Handles rotations to the primary target
     */
    private void handleRotations() {
        if (primaryTarget == null || targetPoint == null) return;

        // Calculate rotation speed based on target metrics
        float speedMultiplier = rotationSpeed.getValue();

        // Increase speed for fast-moving targets
        if (targetMovementSpeed > 0.1) {
            speedMultiplier = Math.min(1.0f, speedMultiplier * 1.5f);
        }

        // Decrease smoothing at close range for more responsive rotations
        if (targetDistance < 3.0f) {
            speedMultiplier = Math.min(1.0f, speedMultiplier * 1.3f);
        }

        // Calculate the rotations to the target point
        float[] rotations = RotationHandler.calculateLookAt(targetPoint);

        // Determine rotation mode parameters
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

        // If body rotation mode, use the specialized method
        if (bodyOnly) {
            RotationHandler.requestRotation(
                    rotations[0],
                    rotations[1],
                    ROTATION_PRIORITY,
                    50, // Shorter duration for faster rotation updates
                    true, // silent
                    true, // bodyOnly
                    state -> rotating = true
            );
        } else {
            // Apply faster rotations for moving targets or close range
            if (targetMovementSpeed > 0.15 || targetDistance < 2.5f) {
                // Direct rotation for fast-moving or close targets
                RotationHandler.requestRotation(
                        rotations[0],
                        rotations[1],
                        ROTATION_PRIORITY,
                        40, // Very short duration for rapid updates
                        silent,
                        false,
                        state -> rotating = true
                );
            } else {
                // Use smoother rotation with look-at for normal cases
                RotationHandler.requestSmoothLookAt(
                        targetPoint,
                        speedMultiplier * 2.0f, // Increase speed multiplier
                        ROTATION_PRIORITY,
                        50, // Shorter duration for faster rotation updates
                        silent,
                        state -> rotating = true
                );
            }
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
        // Get mainhand and offhand items
        boolean hasShield = mc.player.getOffHandStack().isOf(net.minecraft.item.Items.SHIELD) ||
                mc.player.getMainHandStack().isOf(net.minecraft.item.Items.SHIELD);

        if (hasShield) {
            // Use key binding to block with shield
            mc.options.useKey.setPressed(true);

            // Schedule releasing the key after a short delay (150ms)
            new Thread(() -> {
                try {
                    Thread.sleep(150);
                    mc.options.useKey.setPressed(false);
                } catch (InterruptedException e) {
                    // Ignore interruption
                }
            }).start();
        }
    }
}