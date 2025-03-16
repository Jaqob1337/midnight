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
import java.util.Random;

public class Aura extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    // Current target entities
    private List<Entity> targetEntities = new ArrayList<>();
    private Entity primaryTarget = null;
    private Entity lastTarget = null;
    private Vec3d targetPoint = null;

    // Target motion tracking variables
    private double targetMovementSpeed = 0.0;
    private float targetDistance = 0.0f;

    // For mouse movement tracking
    private float lastPlayerYaw = 0;
    private float lastPlayerPitch = 0;

    // The rotation priority for this module
    private static final int ROTATION_PRIORITY = 100;

    // Last position to prevent target switching/flickering
    private Vec3d lastTargetPos = null;
    private long lastTargetPosTime = 0;
    private static final long TARGET_POS_TIMEOUT = 100; // ms

    // Track mouse movement to simulate more natural rotations
    private boolean playerMovedMouse = false;
    private long lastMouseMovedTime = 0;

    // Add these as class fields
    private boolean wasBlocking = false;
    private int blockReleaseDelay = 0;
    private int blockPressTicks = 0;

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

    private final Setting<Float> rotationSpeed = register(
            new Setting<>("RotationSpeed", 0.5f, 0.1f, 1.0f, "Rotation speed factor")
    );

    private final Setting<Boolean> autoBlock = register(
            new Setting<>("AutoBlock", Boolean.FALSE, "Automatically block with right mouse button")
    );

    private final Setting<String> blockMode = register(
            new Setting<>("BlockMode", "Hold",
                    List.of("Hold"),
                    "How to perform auto-blocking")
                    .dependsOn(autoBlock)
    );

    private final Setting<Boolean> throughWalls = register(
            new Setting<>("ThroughWalls", Boolean.FALSE, "Attack through walls")
    );

    private final Setting<Boolean> rayTrace = register(
            new Setting<>("RayTrace", Boolean.TRUE, "Only attack if server rotations are looking at the target")
    );

    private final Setting<Boolean> smartAttack = register(
            new Setting<>("SmartAttack", Boolean.TRUE, "Only attack when your weapon is fully charged")
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

    // For pre-aim behavior switching
    private long preAimStartTime = 0;
    private Entity preAimTarget = null;

    @Override
    public void onEnable() {
        targetEntities.clear();
        primaryTarget = null;
        lastTarget = null;
        targetPoint = null;
        rotating = false;
        targetMovementSpeed = 0.0;
        targetDistance = 0.0f;
        preAimStartTime = 0;
        preAimTarget = null;

        if (mc.player != null) {
            originalYaw = mc.player.getYaw();
            originalPitch = mc.player.getPitch();
        }
    }

    @Override
    public void onDisable() {
        targetEntities.clear();
        primaryTarget = null;
        lastTarget = null;
        targetPoint = null;
        targetMovementSpeed = 0.0;
        targetDistance = 0.0f;
        preAimStartTime = 0;
        preAimTarget = null;

        // Force release immediately on disable
        if (mc.options != null) {
            // Always release when disabling, regardless of internal state
            mc.options.useKey.setPressed(false);
            wasBlocking = false;
            blockReleaseDelay = 0;
            blockPressTicks = 0;
        }

        // Cancel rotations when disabling
        RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
        rotating = false;
    }

    // Make sure the preUpdate method calls updateAutoBlock too
    public void preUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Update autoblock state in preUpdate too for more responsive handling
        updateAutoBlock();

        // Process attack logic in PRE tick
        processAttack();

        // Set the flag to indicate we've already attacked in this tick
        attackedThisTick = true;
    }

    /**
     * Method called during START_CLIENT_TICK
     */

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Skip attack logic if already processed in PRE tick
        if (attackedThisTick) {
            attackedThisTick = false; // Reset for next tick
            return;
        }

        // Track if player has moved their mouse (for more natural rotations)
        if (mc.player != null) {
            float currentYaw = mc.player.getYaw();
            float currentPitch = mc.player.getPitch();

            if (Math.abs(currentYaw - lastPlayerYaw) > 0.1f ||
                    Math.abs(currentPitch - lastPlayerPitch) > 0.1f) {
                playerMovedMouse = true;
                lastMouseMovedTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lastMouseMovedTime > 500) {
                playerMovedMouse = false;
            }

            lastPlayerYaw = currentYaw;
            lastPlayerPitch = currentPitch;
        }

        // Process attack logic in POST tick if not already done in PRE
        processAttack();

        // Clean up stale target position tracking
        if (System.currentTimeMillis() - lastTargetPosTime > TARGET_POS_TIMEOUT) {
            lastTargetPos = null;
        }

        // Handle auto-block when no targets or out of range
        updateAutoBlock();
    }

    /**
     * Manages the auto-block feature, ensuring blocking stops when no valid targets exist
     */
    /**
     * Manages the auto-block feature, ensuring blocking stops when no valid targets exist
     * or when not actively attacking
     */
    private void updateAutoBlock() {
        if (mc.options == null) return;

        // Handle block release with delay
        if (blockReleaseDelay > 0) {
            blockReleaseDelay--;
            if (blockReleaseDelay == 0) {
                // Force release the key
                mc.options.useKey.setPressed(false);
                wasBlocking = false;
            }
            return;
        }

        // If module not enabled or autoblock not enabled, release block and return
        if (!isEnabled() || !autoBlock.getValue()) {
            if (wasBlocking) {
                // Schedule key release
                mc.options.useKey.setPressed(false);
                wasBlocking = false;
            }
            return;
        }

        boolean shouldBlock = false;

        // Check if we have a valid target
        if (primaryTarget != null &&
                !primaryTarget.isRemoved() &&
                primaryTarget instanceof LivingEntity living &&
                living.getHealth() > 0 &&
                mc.player.distanceTo(primaryTarget) <= range.getValue() * 1.5) {

            // Only block if we've recently attacked or are about to
            long timeSinceAttack = System.currentTimeMillis() - lastAttackTime;
            if (timeSinceAttack < 500) {
                shouldBlock = true;
            }
        }

        // If no targets at all, force turn off blocking
        if (targetEntities.isEmpty() || primaryTarget == null) {
            shouldBlock = false;
        }

        // Manage key press state changes
        if (shouldBlock && !wasBlocking) {
            // Start blocking
            mc.options.useKey.setPressed(true);
            wasBlocking = true;
            blockPressTicks = 0;
        } else if (!shouldBlock && wasBlocking) {
            // Schedule block release with short delay
            blockReleaseDelay = 2; // 2 ticks delay before release
        }

        // Keep track of how long we've been blocking
        if (wasBlocking) {
            blockPressTicks++;

            // Safety check: force-release after a certain time even if we think we should block
            // This prevents stuck keys if something goes wrong with state tracking
            if (blockPressTicks > 80) { // 4 seconds at 20 ticks/sec
                mc.options.useKey.setPressed(false);
                wasBlocking = false;
                blockPressTicks = 0;
            }
        }
    }

    /**
     * Processes the attack logic
     */
    private void processAttack() {
        // Calculate attack delay based on CPS
        long currentTime = System.currentTimeMillis();
        float baseDelay = 1000.0f / cps.getValue(); // Convert CPS to milliseconds

        // Add smart randomization to avoid patterns
        long attackDelay;
        if (randomCps.getValue()) {
            // More natural randomization (20% variation)
            float variation = baseDelay * 0.2f;
            attackDelay = (long)(baseDelay + (random.nextDouble() * variation * 2 - variation));
        } else {
            // Add minimal randomization even when setting is off (5% variation)
            float variation = baseDelay * 0.05f;
            attackDelay = (long)(baseDelay + (random.nextDouble() * variation * 2 - variation));
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

            // Implement preaiming - start rotating to target before attack is ready
            boolean shouldPreAim = shouldPerformPreAim(canAttack, cooldownProgress);

            if (shouldPreAim) {
                // Handle rotations early - this is the pre-aiming logic
                handleRotations();
            }

            // Validate that the server rotations are actually looking at the entity if rayTrace is enabled
            boolean canHit = true;
            if (rayTrace.getValue() && RotationHandler.isRotationActive()) {
                float serverYaw = RotationHandler.getServerYaw();
                float serverPitch = RotationHandler.getServerPitch();

                // Create a slightly expanded hitbox for better hit registration
                Box expandedBox = primaryTarget.getBoundingBox().expand(0.1);
                Vec3d eyePos = mc.player.getEyePos();
                Vec3d lookVec = RaytraceUtil.getVectorForRotation(serverPitch, serverYaw);
                Vec3d endPos = eyePos.add(lookVec.multiply(range.getValue() + 1));

                // Check if rotations would hit the expanded hitbox
                canHit = RaytraceUtil.rayTraceEntityBox(eyePos, endPos, expandedBox) != null;
            }

            // Attack if rotation is complete, cooldown is ready, and we can attack
            if (canAttack && cooldownReady && RotationHandler.isRotationActive() && canHit) {
                // If we haven't pre-aimed yet, do final rotation adjustment
                if (!shouldPreAim) {
                    handleRotations();
                }

                // Perform the attack
                attack(primaryTarget);
                lastAttackTime = currentTime;
                lastTarget = primaryTarget;
            }
        } else {
            // No targets, cancel pending rotations
            RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
            rotating = false;

        }
    }

    /**
     * Determines whether to start rotating early (pre-aiming)
     * based on cooldown progress, target metrics, and time until next attack
     */
    private boolean shouldPerformPreAim(boolean canAttackNow, float cooldownProgress) {
        if (canAttackNow) {
            // If we can attack now, no need for pre-aim
            return true;
        }

        long timeUntilNextAttack = (long)(1000.0f / cps.getValue() * (1.0f - cooldownProgress));

        // Start preaiming earlier for moving targets and farther targets
        float preAimThreshold = 200f; // Base threshold in ms

        // Adjust for target movement - faster targets need earlier pre-aim
        if (targetMovementSpeed > 0.1) {
            preAimThreshold += targetMovementSpeed * 300f; // Up to 300ms earlier for fast targets
        }

        // Adjust for distance - further targets need more lead time
        if (targetDistance > 3.0f) {
            preAimThreshold += (targetDistance - 3.0f) * 50f; // 50ms per block beyond 3 blocks
        }

        // Adjust based on angle difference between current rotation and target
        if (mc.player != null && targetPoint != null) {
            float[] targetRotations = RotationHandler.calculateLookAt(targetPoint);
            float currentYaw = mc.player.getYaw();
            float currentPitch = mc.player.getPitch();

            float yawDiff = Math.abs(MathHelper.wrapDegrees(targetRotations[0] - currentYaw));
            float pitchDiff = Math.abs(targetRotations[1] - currentPitch);

            // More pre-aim time for larger angle differences
            float angleDiffFactor = (yawDiff + pitchDiff) / 10.0f; // Scale appropriately
            preAimThreshold += angleDiffFactor * 50.0f; // Up to 50ms per 10 degrees
        }

        // Add randomization to make behavior less predictable
        preAimThreshold *= 0.8f + random.nextFloat() * 0.4f; // 80% to 120% variation

        // Return true if we're within the threshold time before next attack
        return timeUntilNextAttack <= preAimThreshold;
    }

    /**
     * Updates metrics about the current target
     */
    private void updateTargetMetrics() {
        if (primaryTarget == null || mc.player == null) return;

        // Update target distance
        targetDistance = (float)mc.player.getPos().distanceTo(primaryTarget.getPos());

        // Calculate current movement speed
        targetMovementSpeed = Math.sqrt(
                Math.pow(primaryTarget.getX() - primaryTarget.prevX, 2) +
                        Math.pow(primaryTarget.getZ() - primaryTarget.prevZ, 2)
        );

        // Update lastTargetPos for tracking
        lastTargetPos = primaryTarget.getPos();
        lastTargetPosTime = System.currentTimeMillis();

        // Track pre-aim switching for natural aim transitions
        if (preAimTarget != primaryTarget) {
            // We've switched targets, record this for varied rotation behavior
            preAimTarget = primaryTarget;
            preAimStartTime = System.currentTimeMillis();
        }
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
                // Skip friendslist players if a friend system exists
                // if (FriendManager.isFriend(((PlayerEntity) entity).getName().getString())) continue;
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
     * with improved aim smoothing and target point selection
     *
     * @param entity The target entity
     * @return A visible position on the entity, or null if none found
     */
    private Vec3d findVisiblePoint(Entity entity) {
        // Cache points to avoid flickering between target spots
        if (primaryTarget == entity && lastTarget == entity && targetPoint != null) {
            // Still use the same target point for a short time to avoid erratic movement
            // But verify it's still valid with a raycast
            if (RaytraceUtil.canSeePosition(entity, targetPoint)) {
                // Add minor variation to avoid perfect tracking
                if (random.nextFloat() < 0.05f) {
                    // Occasionally add very slight jitter to aim point
                    double jitterX = (random.nextDouble() - 0.5) * 0.03;
                    double jitterY = (random.nextDouble() - 0.5) * 0.02;
                    double jitterZ = (random.nextDouble() - 0.5) * 0.03;
                    return targetPoint.add(jitterX, jitterY, jitterZ);
                }
                return targetPoint;
            }
        }

        // Check entity's eye position first (more natural aim point)
        if (entity instanceof LivingEntity) {
            Vec3d eyePos = entity.getEyePos();
            if (RaytraceUtil.canSeePosition(entity, eyePos)) {
                return eyePos;
            }
        }

        // Try to find any visible point
        Vec3d visiblePoint = RaytraceUtil.getVisiblePoint(entity);

        // If no visible point, return null
        if (visiblePoint == null) {
            return null;
        }

        return visiblePoint;
    }

    /**
     * Handles rotations to the primary target with improved tracking and more natural movement
     */
    private void handleRotations() {
        if (primaryTarget == null || targetPoint == null) return;

        // Calculate rotation speed based on target metrics
        float speedMultiplier = rotationSpeed.getValue();

        // Calculate the rotations to the target point
        float[] rotations = RotationHandler.calculateLookAt(targetPoint);

        // Create human-like rotation patterns
        // Slower initial movement, faster middle portion, then slower final approach
        if (mc.player != null) {
            float currentYaw = mc.player.getYaw();
            float currentPitch = mc.player.getPitch();

            // Calculate angle differences
            float yawDiff = Math.abs(MathHelper.wrapDegrees(rotations[0] - currentYaw));
            float pitchDiff = Math.abs(MathHelper.wrapDegrees(rotations[1] - currentPitch));
            float totalDiff = yawDiff + pitchDiff;

            // Adjust speed based on how far we need to rotate
            // Slower for small adjustments, faster for big movements
            if (totalDiff < 10) {
                // For fine adjustments, move slower to avoid jitter
                speedMultiplier *= 0.7f;
            } else if (totalDiff > 60) {
                // For large movements, start somewhat faster
                speedMultiplier *= 1.1f;
            }

            // Adjust for player's own manual aiming
            // If player is actively moving their mouse, temporarily blend with their movement
            if (playerMovedMouse && System.currentTimeMillis() - lastMouseMovedTime < 300) {
                // Blend rotations for a more natural feel when player is also moving mouse
                // This creates the impression that the player is helping with the aim
                speedMultiplier *= 0.85f;
            }

            // Special case: preAiming to a new target
            // When we first start tracking a new target, use a more dynamic rotation pattern
            if (preAimTarget == primaryTarget) {
                long timeOnTarget = System.currentTimeMillis() - preAimStartTime;

                if (timeOnTarget < 500) {
                    // Initial acquisition phase - more "searching" behavior
                    // This mimics a player first finding the target
                    // First quickly get close, then slow down for precision
                    if (timeOnTarget < 200 && totalDiff > 20) {
                        // Quick initial movement toward target
                        speedMultiplier *= 1.2f;
                    } else {
                        // Then slow down as we get closer
                        speedMultiplier *= 0.85f;

                        // Slightly overshoot and correct (very human-like)
                        if (random.nextFloat() < 0.3f && timeOnTarget < 300 && totalDiff < 15) {
                            float overshootAmount = 1.0f + (totalDiff / 30.0f);
                            rotations[0] += MathHelper.wrapDegrees(rotations[0] - currentYaw) * 0.1f * overshootAmount;
                            rotations[1] += (rotations[1] - currentPitch) * 0.1f * overshootAmount;
                        }
                    }
                }
            }
        }

        // Adjust speed based on target movement state
        if (targetMovementSpeed > 0.12) {
            // Slightly increased rotation speed for moving targets
            speedMultiplier = Math.min(0.65f, speedMultiplier * (1.0f + (float)targetMovementSpeed * 1.2f));
        } else if (targetMovementSpeed < 0.01) {
            // Slightly slower for stationary targets - looks more natural
            speedMultiplier *= 0.9f;
        }

        // Close range targets need slightly faster tracking
        if (targetDistance < 2.5f) {
            speedMultiplier = Math.min(0.65f, speedMultiplier * 1.1f);
        } else if (targetDistance > 4.0f) {
            // Longer distances can have slightly slower rotations
            speedMultiplier *= 0.9f;
        }

        // Add subtle randomization to rotation speed to avoid patterns
        // Using a bell curve distribution for more natural variation
        float randomFactor = 0;
        for (int i = 0; i < 3; i++) {
            randomFactor += (random.nextFloat() - 0.5f) * 0.1f;
        }
        speedMultiplier *= (1.0f + randomFactor);

        // Ensure speed stays within reasonable bounds
        speedMultiplier = MathHelper.clamp(speedMultiplier, 0.3f, 0.65f);

        // Calculate a slight lead for pre-aim (looking slightly ahead of where target is going)
        if (targetMovementSpeed > 0.05 && mc.player != null) {
            // Calculate movement direction vector
            double moveX = primaryTarget.getX() - primaryTarget.prevX;
            double moveZ = primaryTarget.getZ() - primaryTarget.prevZ;

            // Normalize and scale by a small amount for "leading" the target
            double moveLength = Math.sqrt(moveX * moveX + moveZ * moveZ);
            if (moveLength > 0) {
                double leadFactor = 0.5 + (random.nextDouble() * 0.5); // Random lead amount

                // Scale lead by distance (more lead at medium distances)
                double distanceFactor = 1.0;
                if (targetDistance < 3.0) {
                    distanceFactor = targetDistance / 3.0;
                } else if (targetDistance > 5.0) {
                    distanceFactor = 1.0 - ((targetDistance - 5.0) / 5.0);
                    distanceFactor = Math.max(0.3, distanceFactor);
                }

                // Create a lead point that's slightly ahead of the target's movement
                leadFactor *= distanceFactor;
                leadFactor *= random.nextDouble() < 0.3 ? 1.5 : 1.0; // Occasionally lead more

                Vec3d leadPoint = targetPoint.add(
                        moveX * leadFactor,
                        0, // Don't lead vertically
                        moveZ * leadFactor
                );

                // Calculate rotations to the lead point
                float[] leadRotations = RotationHandler.calculateLookAt(leadPoint);

                // Blend between direct and lead rotations
                float blendFactor = 0.7f; // How much to lead vs direct aim
                rotations[0] = rotations[0] * (1 - blendFactor) + leadRotations[0] * blendFactor;
                rotations[1] = rotations[1] * (1 - blendFactor) + leadRotations[1] * blendFactor;
            }
        }

        // Add occasional minor humanization to rotations
        // Less predictable than applying every time
        if (random.nextFloat() < 0.4f) {
            float jitterAmount = 0.2f + (random.nextFloat() * 0.15f); // 0.2 to 0.35 degrees
            rotations[0] += (random.nextFloat() - 0.5f) * jitterAmount;
            rotations[1] += (random.nextFloat() - 0.5f) * jitterAmount * 0.7f; // Less jitter for pitch
        }

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
                    45, // Slightly faster updates for smoother tracking
                    true, // silent
                    true, // bodyOnly
                    state -> rotating = true
            );
        } else {
            // Occasionally use direct rotation for quick movements
            // This creates more varied rotation patterns that look more human
            if (random.nextFloat() < 0.2f && targetMovementSpeed > 0.15f) {
                RotationHandler.requestRotation(
                        rotations[0],
                        rotations[1],
                        ROTATION_PRIORITY,
                        40, // Faster response for sudden movements
                        silent,
                        false,
                        state -> rotating = true
                );
            } else {
                // Use smoother rotation with look-at for most cases
                RotationHandler.requestSmoothLookAt(
                        targetPoint,
                        speedMultiplier,
                        ROTATION_PRIORITY,
                        45, // Balanced update rate that still tracks well
                        silent,
                        state -> rotating = true
                );
            }
        }
    }

    /**
     * Attacks the target entity with a single packet
     */
    private void attack(Entity target) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Make sure we're still in range
        if (mc.player.distanceTo(target) <= range.getValue()) {
            // Auto-block handling
            boolean wasBlocking = mc.options.useKey.isPressed();
            if (autoBlock.getValue() && wasBlocking) {
                // Release block before attacking
                mc.options.useKey.setPressed(false);
            }

            // Attack the entity
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);

            // Resume blocking if needed
            if (autoBlock.getValue() && wasBlocking) {
                mc.options.useKey.setPressed(true);
            } else if (autoBlock.getValue()) {
                // Start blocking if auto-block is enabled
                mc.options.useKey.setPressed(true);
            }
        }
    }
}