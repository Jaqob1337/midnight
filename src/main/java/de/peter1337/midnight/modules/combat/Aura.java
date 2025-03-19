package de.peter1337.midnight.modules.combat;

import de.peter1337.midnight.handler.RotationHandler;
import de.peter1337.midnight.utils.RayCastUtil;
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
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class Aura extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    // Current target entity
    private Entity primaryTarget = null;
    private Vec3d targetPoint = null;

    // The rotation priority for this module
    private static final int ROTATION_PRIORITY = 100;

    // Settings
    private final Setting<Float> range = register(
            new Setting<>("Range", 3.5f, 1.0f, 6.0f, "Attack range in blocks")
    );

    private final Setting<Float> cps = register(
            new Setting<>("CPS", 8.0f, 1.0f, 20.0f, "Clicks per second")
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

    // New rotation speed setting
    private final Setting<Float> rotationSpeed = register(
            new Setting<>("RotationSpeed", 0.4f, 0.0f, 1.0f, "Speed of rotation to targets (0 = smooth, 1 = instant)")
    );

    private final Setting<Boolean> throughWalls = register(
            new Setting<>("ThroughWalls", Boolean.FALSE, "Attack through walls")
    );

    private final Setting<Boolean> smartAttack = register(
            new Setting<>("SmartAttack", Boolean.TRUE, "Only attack when your weapon is fully charged")
    );

    private final Setting<Boolean> raytraceCheck = register(
            new Setting<>("RaytraceCheck", Boolean.TRUE, "Verify that server rotations are actually pointing at the target")
    );

    private final Setting<Boolean> autoBlock = register(
            new Setting<>("AutoBlock", Boolean.FALSE, "Automatically block with weapon")
    );

    private final Setting<String> blockMode = register(
            new Setting<>("BlockMode", "Real",
                    List.of("Real", "Fake"),
                    "Real: Actually block, Fake: Visual only")
                    .dependsOn(autoBlock)
    );

    // Properties to track state
    private long lastAttackTime = 0;
    private boolean rotating = false;
    private boolean attackedThisTick = false;
    private boolean isBlocking = false;
    private long blockingStartTime = 0;

    public Aura() {
        super("Aura", "Automatically attacks nearby entities", Category.COMBAT, "r");
    }

    @Override
    public void onEnable() {
        primaryTarget = null;
        targetPoint = null;
        rotating = false;
        isBlocking = false;
    }

    @Override
    public void onDisable() {
        primaryTarget = null;
        targetPoint = null;

        // Cancel rotations when disabling
        RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
        rotating = false;

        // Stop blocking if we were
        if (isBlocking) {
            stopBlocking();
        }
    }

    // Make sure the preUpdate method calls updateAutoBlock too
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
        float baseDelay = 1000.0f / cps.getValue(); // Convert CPS to milliseconds

        // Add randomization to avoid patterns
        long attackDelay;
        if (randomCps.getValue()) {
            // Natural randomization (20% variation)
            float variation = baseDelay * 0.2f;
            attackDelay = (long)(baseDelay + (random.nextDouble() * variation * 2 - variation));
        } else {
            // Add minimal randomization even when setting is off (5% variation)
            float variation = baseDelay * 0.05f;
            attackDelay = (long)(baseDelay + (random.nextDouble() * variation * 2 - variation));
        }

        boolean canAttack = currentTime - lastAttackTime >= attackDelay;

        // Find potential targets
        findTargets();

        // If we have a target, handle rotations and attacks
        if (primaryTarget != null && mc.player != null) {
            // Check attack cooldown if smart attack enabled
            float cooldownProgress = mc.player.getAttackCooldownProgress(0.0f);
            boolean cooldownReady = !smartAttack.getValue() || cooldownProgress >= 0.9f;

            // Find best point to target on hitbox using RayCastUtil
            targetPoint = getBestTargetPoint(primaryTarget);

            // Skip if we can't see and throughWalls is disabled
            boolean canSee = RayCastUtil.canSeeEntity(primaryTarget);
            if (!canSee && !throughWalls.getValue()) {
                return;
            }

            // Handle rotations
            handleRotations();

            // Handle auto-blocking if enabled
            handleAutoBlock(true);

            // Check if our server rotations are pointing at the entity
            boolean canHitWithRotation = true;
            if (raytraceCheck.getValue() && RotationHandler.isRotationActive()) {
                float serverYaw = RotationHandler.getServerYaw();
                float serverPitch = RotationHandler.getServerPitch();
                canHitWithRotation = RayCastUtil.canSeeEntityFromRotation(
                        primaryTarget,
                        serverYaw,
                        serverPitch,
                        range.getValue() + 2.0
                );
            }

            // Attack if cooldown is ready, we can attack, and our rotations are valid
            if (canAttack && cooldownReady && RotationHandler.isRotationActive() && canHitWithRotation) {
                // Before attacking, stop blocking temporarily if needed
                boolean wasBlocking = isBlocking;
                if (wasBlocking) {
                    stopBlocking();
                }

                // Perform the attack
                attack(primaryTarget);
                lastAttackTime = currentTime;

                // Resume blocking after attack if we were blocking
                if (wasBlocking) {
                    // Small delay before blocking again
                    new Thread(() -> {
                        try {
                            Thread.sleep(100);
                            handleAutoBlock(true);
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                    }).start();
                }
            }
        } else {
            // No targets, cancel pending rotations
            RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
            rotating = false;

            // Stop blocking if no targets
            handleAutoBlock(false);
        }
    }

    /**
     * Handles auto-blocking based on settings
     *
     * @param shouldBlock Whether blocking should be active based on targeting
     */
    private void handleAutoBlock(boolean shouldBlock) {
        if (!autoBlock.getValue()) {
            // If autoblock is disabled but we were blocking, stop
            if (isBlocking) {
                stopBlocking();
            }
            return;
        }

        // For fake mode, we always block regardless of targets
        if (blockMode.getValue().equals("Fake")) {
            if (!isBlocking) {
                startFakeBlocking();
            }
            return;
        }

        // For real mode, follow target-based logic
        if (blockMode.getValue().equals("Real")) {
            // Determine if we should start or stop blocking
            if (shouldBlock && !isBlocking) {
                startRealBlocking();
            } else if (!shouldBlock && isBlocking) {
                stopRealBlocking();
            }
        }
    }

    /**
     * Starts real blocking - sends actual block commands to server
     */
    private void startRealBlocking() {
        if (mc.player == null) return;

        // Check if we have a sword or shield in hand
        if (!canBlockWithCurrentItem()) return;

        // Real blocking - actually send use action to server
        mc.options.useKey.setPressed(true);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

        isBlocking = true;
        blockingStartTime = System.currentTimeMillis();
    }

    /**
     * Stops real blocking
     */
    private void stopRealBlocking() {
        if (mc.player == null) return;

        // Real blocking - actually release use action
        mc.options.useKey.setPressed(false);

        isBlocking = false;
    }

    /**
     * Starts fake blocking - client-side only
     */
    private void startFakeBlocking() {
        if (mc.player == null) return;

        // Check if we have a sword or shield in hand
        if (!canBlockWithCurrentItem()) return;

        // Fake blocking - only display the animation client-side
        mc.player.setCurrentHand(Hand.MAIN_HAND);

        isBlocking = true;
        blockingStartTime = System.currentTimeMillis();
    }

    /**
     * Stops fake blocking
     */
    private void stopFakeBlocking() {
        if (mc.player == null) return;

        // Fake blocking - stop the animation client-side
        mc.player.clearActiveItem();

        isBlocking = false;
    }

    /**
     * Stops blocking based on current mode
     */
    private void stopBlocking() {
        if (blockMode.getValue().equals("Real")) {
            stopRealBlocking();
        } else if (blockMode.getValue().equals("Fake")) {
            stopFakeBlocking();
        }
    }

    /**
     * Checks if the player can block with the current item
     */
    private boolean canBlockWithCurrentItem() {
        if (mc.player == null) return false;

        // Get the item in main hand
        net.minecraft.item.ItemStack mainHandItem = mc.player.getMainHandStack();

        // Check if it's a sword or shield
        return !mainHandItem.isEmpty() &&
                (mainHandItem.getItem() instanceof net.minecraft.item.SwordItem ||
                        mainHandItem.getItem() instanceof net.minecraft.item.ShieldItem);
    }

    /**
     * Finds the best point on an entity's hitbox to target for attacking
     *
     * @param entity The target entity
     * @return The best target point for rotation
     */
    private Vec3d getBestTargetPoint(Entity entity) {
        if (entity == null || mc.player == null) {
            return entity.getPos();
        }

        // Use RayCastUtil to find the best visible point on the entity's hitbox
        Vec3d visiblePoint = RayCastUtil.getVisiblePoint(entity);

        // If we found a visible point, use it
        if (visiblePoint != null) {
            return visiblePoint;
        }

        // If no visible point and throughWalls is enabled, use center of hitbox
        if (throughWalls.getValue()) {
            return entity.getPos().add(0, entity.getHeight() / 2, 0);
        }

        // Default to eye position as fallback
        return entity.getEyePos();
    }

    /**
     * Check if a specific point on an entity is visible to the player
     *
     * @param entity The entity
     * @param point The point to check
     * @return true if the point is visible
     */
    private boolean isPointVisible(Entity entity, Vec3d point) {
        return RayCastUtil.canSeePosition(entity, point);
    }

    /**
     * Finds and prioritizes potential targets
     */
    private void findTargets() {
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

            // Check if any part of the entity's hitbox is within range
            if (isEntityInRange(entity, range.getValue())) {
                potentialTargets.add(entity);
            }
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
                        float yawDiff = Math.abs(rotations[0] - playerYaw);
                        float pitchDiff = Math.abs(rotations[1] - playerPitch);
                        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
                    }));
                }
                break;
        }

        // Set primary target (first target after sorting)
        if (!potentialTargets.isEmpty()) {
            primaryTarget = potentialTargets.get(0);
        }
    }

    /**
     * Checks if any part of an entity's hitbox is within the specified range
     *
     * @param entity The entity to check
     * @param range The maximum distance
     * @return true if any part of the hitbox is within range
     */
    private boolean isEntityInRange(Entity entity, float range) {
        if (mc.player == null) return false;

        // Get player eye position
        Vec3d playerPos = mc.player.getEyePos();

        // Use ray tracing to find the closest point on the entity's hitbox
        net.minecraft.util.math.Box box = entity.getBoundingBox();

        // Test multiple points on the hitbox to find the closest one
        Vec3d[] testPoints = new Vec3d[] {
                // Center
                new Vec3d((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2),
                // Eye position
                entity.getEyePos(),
                // Corners
                new Vec3d(box.minX, box.minY, box.minZ),
                new Vec3d(box.minX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.minZ),
                new Vec3d(box.minX, box.maxY, box.maxZ),
                new Vec3d(box.maxX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.maxZ),
                new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.maxZ)
        };

        double rangeSq = range * range;
        double minDistSq = Double.MAX_VALUE;

        for (Vec3d point : testPoints) {
            double distSq = playerPos.squaredDistanceTo(point);
            minDistSq = Math.min(minDistSq, distSq);

            // Early exit if we find a point within range
            if (distSq <= rangeSq) {
                return true;
            }
        }

        // Try to find a more precise point using ray tracing
        // This can find points on edges that might be closer
        Vec3d playerLook = RayCastUtil.getVectorForRotation(mc.player.getPitch(), mc.player.getYaw());
        Vec3d endPos = playerPos.add(playerLook.multiply(range + 1.0));
        Vec3d hitPoint = RayCastUtil.rayTraceEntityBox(playerPos, endPos, box);

        if (hitPoint != null) {
            double hitDistSq = playerPos.squaredDistanceTo(hitPoint);
            return hitDistSq <= rangeSq;
        }

        return false;
    }

    /**
     * Handles rotations to the primary target
     */
    private void handleRotations() {
        if (primaryTarget == null || targetPoint == null) return;

        // Calculate the rotations to the target point
        float[] targetRotations = RotationHandler.calculateLookAt(targetPoint);

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

        // Get the rotation speed - where 0 = smooth, 1 = instant
        float speedFactor = rotationSpeed.getValue();

        // If speed is 1.0, use instant rotations
        if (speedFactor >= 0.99f) {
            // Use direct instant rotation
            RotationHandler.requestRotation(
                    targetRotations[0],
                    targetRotations[1],
                    ROTATION_PRIORITY,
                    25, // Update interval in milliseconds
                    silent,
                    bodyOnly,
                    state -> rotating = true
            );
            return;
        }

        // For lower speeds, interpolate between current and target rotations
        // Get current rotations based on mode
        float currentYaw, currentPitch;
        if (silent || bodyOnly) {
            currentYaw = RotationHandler.getServerYaw();
            currentPitch = RotationHandler.getServerPitch();
        } else {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
        }

        // Map the speed factor (0-1) to a more consistent rotation speed
        // Higher values = faster rotations
        float actualSpeed = 0.2f + (speedFactor * 0.8f);

        // Calculate the angle differences
        float yawDiff = targetRotations[0] - currentYaw;
        // Normalize yaw difference to -180 to 180 range
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;

        float pitchDiff = targetRotations[1] - currentPitch;

        // Apply speed factor
        float newYaw = currentYaw + (yawDiff * actualSpeed);
        float newPitch = currentPitch + (pitchDiff * actualSpeed);

        // Apply the interpolated rotation
        RotationHandler.requestRotation(
                newYaw,
                newPitch,
                ROTATION_PRIORITY,
                25, // Update interval in milliseconds
                silent,
                bodyOnly,
                state -> rotating = true
        );
    }

    /**
     * Attacks the target entity with a single packet
     */
    private void attack(Entity target) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Check if any part of the hitbox is in range
        if (isEntityInRange(target, range.getValue())) {
            // Attack the entity
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }
}