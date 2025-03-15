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
import net.minecraft.item.SwordItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.TridentItem;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

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
    private long lastPacketSentTime = 0;
    private boolean rotating = false;
    private float originalYaw;
    private float originalPitch;
    private boolean attackedThisTick = false;
    private int comboHits = 0;
    private long comboStartTime = 0;
    private static final long MIN_PACKET_INTERVAL = 25; // Minimum time between packets in ms
    private int criticalTicks = 0;
    private boolean isCriticaling = false;
    private Vec3d lastTargetPos = null;

    public Aura() {
        super("Aura", "Automatically attacks nearby entities", Category.COMBAT, "r");
    }

    @Override
    public void onEnable() {
        targetEntities.clear();
        primaryTarget = null;
        lastTarget = null;
        targetPoint = null;
        rotating = false;
        targetMovementSpeed = 0.0;
        targetDistance = 0.0f;
        comboHits = 0;
        comboStartTime = 0;
        criticalTicks = 0;
        isCriticaling = false;
        lastTargetPos = null;

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
        comboHits = 0;
        comboStartTime = 0;
        criticalTicks = 0;
        isCriticaling = false;
        lastTargetPos = null;

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

        // Handle critical hit process
        if (isCriticaling) {
            handleCriticalProcess();
        }

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

        // Add smart randomization to avoid patterns (always randomize slightly)
        long attackDelay;
        if (randomCps.getValue()) {
            // More natural randomization (30% variation)
            float variation = baseDelay * 0.3f;
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

            // Adjust attack timing for combo attacks
            if (primaryTarget == lastTarget) {
                // Target hasn't changed, consider this a combo
                if (System.currentTimeMillis() - comboStartTime > 4000) {
                    // Reset combo if too much time has passed
                    comboHits = 0;
                    comboStartTime = currentTime;
                } else {
                    // Natural combo timing: speed up initially then slow down
                    if (comboHits == 1) {
                        // First follow-up hit is quicker
                        attackDelay = (long)(attackDelay * 0.8);
                    } else if (comboHits == 2) {
                        // Second follow-up continues combo
                        attackDelay = (long)(attackDelay * 0.9);
                    } else if (comboHits >= 3) {
                        // After 3 hits, add a slight delay to avoid patterns
                        attackDelay = (long)(attackDelay * 1.1);
                        // Reset combo after a while
                        if (comboHits >= 4 + random.nextInt(3)) {
                            comboHits = 0;
                        }
                    }
                }
            } else {
                // Target changed, reset combo
                comboHits = 0;
                comboStartTime = currentTime;
                lastTarget = primaryTarget;
            }

            // Adjust timing based on target vulnerability
            if (isTargetVulnerable(primaryTarget)) {
                // Attack faster when target is vulnerable
                attackDelay = (long)(attackDelay * 0.85);
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

            // Switch to best weapon before attacking if appropriate

            // Attack if rotation is complete, cooldown is ready, and we can attack
            if (canAttack && cooldownReady && RotationHandler.isRotationActive() && canHit && !isCriticaling) {
                // Check for critical hit opportunity
                boolean shouldCritical = shouldPerformCritical();

                if (shouldCritical) {
                    // Start critical hit sequence
                    startCriticalAttack(primaryTarget);
                } else {
                    // Normal attack
                    attack(primaryTarget);
                    lastAttackTime = currentTime;
                    comboHits++;

                    // Handle auto-blocking if enabled
                    if (autoBlock.getValue()) {
                        tryBlock();
                    }
                }
            }
        } else {
            // No targets, cancel pending rotations
            RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
            rotating = false;
            resetCombo();

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

        // Calculate current movement speed
        Vec3d currentPos = primaryTarget.getPos();
        if (lastTargetPos != null) {
            // Calculate actual movement over time for more accurate prediction
            targetMovementSpeed = currentPos.distanceTo(lastTargetPos);
        } else {
            // Fallback calculation
            targetMovementSpeed = Math.sqrt(
                    Math.pow(primaryTarget.getX() - primaryTarget.prevX, 2) +
                            Math.pow(primaryTarget.getZ() - primaryTarget.prevZ, 2)
            );
        }

        // Store position for next tick
        lastTargetPos = currentPos;
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
     * @param entity The target entity
     * @return A visible position on the entity, or null if none found
     */
    private Vec3d findVisiblePoint(Entity entity) {
        Vec3d visiblePoint = RaytraceUtil.getVisiblePoint(entity);

        // Apply predictive aiming if enabled
        if (visiblePoint != null && predictiveAim.getValue() && entity.distanceTo(mc.player) > 2.0) {
            // Calculate velocity of the target entity
            double velocityX = entity.getX() - entity.prevX;
            double velocityY = entity.getY() - entity.prevY;
            double velocityZ = entity.getZ() - entity.prevZ;

            // Enhanced prediction using past positions for better trajectory estimation
            if (lastTargetPos != null && targetMovementSpeed > 0.05) {
                Vec3d currentPos = entity.getPos();
                Vec3d direction = currentPos.subtract(lastTargetPos).normalize();

                // Apply momentum-based prediction
                float predictionStrength = aimPrediction.getValue();
                // Scale prediction based on ping and target speed
                float pingFactor = 1.0f; // Ideally would be adjusted based on ping
                float speedMultiplier = (float) Math.min(1.0, targetMovementSpeed * 5);
                predictionStrength *= pingFactor * speedMultiplier;

                // Create a more accurate prediction for moving targets
                Vec3d prediction = new Vec3d(
                        direction.x * predictionStrength * 10.0,
                        Math.min(Math.abs(direction.y), 0.1) * Math.signum(direction.y) * predictionStrength * 5.0, // Limit vertical prediction
                        direction.z * predictionStrength * 10.0
                );

                // Apply prediction to the visible point
                visiblePoint = visiblePoint.add(prediction);
            } else {
                // Fallback to basic prediction
                Vec3d prediction = new Vec3d(
                        velocityX * aimPrediction.getValue() * 10.0,
                        velocityY * aimPrediction.getValue() * 2.5, // Less vertical prediction
                        velocityZ * aimPrediction.getValue() * 10.0
                );

                // Apply prediction to the visible point
                visiblePoint = visiblePoint.add(prediction);
            }

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
     * Handles rotations to the primary target with advanced humanization
     */
    private void handleRotations() {
        if (primaryTarget == null || targetPoint == null) return;

        // Calculate rotation speed based on target metrics
        float speedMultiplier = rotationSpeed.getValue();

        // Adjust speed based on target movement and distance
        if (targetMovementSpeed > 0.1) {
            // Faster rotations for moving targets
            speedMultiplier = Math.min(1.0f, speedMultiplier * (1.0f + (float)targetMovementSpeed * 2.0f));
        }

        if (targetDistance < 3.0f) {
            // Faster rotations at close range
            speedMultiplier = Math.min(1.0f, speedMultiplier * 1.3f);
        }

        // Add slight randomization to rotation speed to avoid patterns
        speedMultiplier *= (0.95f + random.nextFloat() * 0.1f);

        // Calculate the rotations to the target point
        float[] rotations = RotationHandler.calculateLookAt(targetPoint);

        // Add minor jitter to rotations for more human-like movements
        float jitterAmount = 0.3f;
        if (speedMultiplier > 0.7f) {
            // More jitter for faster rotations
            jitterAmount = 0.5f;
        }

        // Only add jitter randomly to break patterns
        if (random.nextFloat() < 0.7f) {
            rotations[0] += (random.nextFloat() - 0.5f) * jitterAmount;
            rotations[1] += (random.nextFloat() - 0.5f) * jitterAmount * 0.5f; // Less jitter for pitch
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
     * Attacks the target entity with optimized packet timing
     */
    private void attack(Entity target) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Make sure we're still in range
        if (mc.player.distanceTo(target) <= range.getValue()) {
            long currentTime = System.currentTimeMillis();

            // Ensure we don't send packets too frequently (avoid packet spam detection)
            if (currentTime - lastPacketSentTime >= MIN_PACKET_INTERVAL) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                lastPacketSentTime = currentTime;
            }
        }
    }

    /**
     * Resets the combo counter
     */
    private void resetCombo() {
        comboHits = 0;
        comboStartTime = 0;
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

    /**
     * Checks if we should perform a critical hit
     */
    private boolean shouldPerformCritical() {
        // Check if player can perform a critical hit
        if (mc.player == null) return false;

        // Don't critical when already in the process
        if (isCriticaling) return false;

        // Basic conditions for critical hits
        boolean canCritical = mc.player.isOnGround() &&
                !mc.player.isInLava() &&
                !mc.player.isTouchingWater() &&
                !mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS) &&
                !mc.player.hasVehicle();

        // Only critical sometimes to avoid patterns (70% chance)
        return canCritical && random.nextFloat() < 0.7f;
    }

    /**
     * Starts a critical hit sequence
     */
    private void startCriticalAttack(Entity target) {
        if (!isCriticaling) {
            isCriticaling = true;
            criticalTicks = 0;
            // Store target for the critical sequence
            lastTarget = target;
        }
    }

    /**
     * Handles the critical hit process over multiple ticks
     */
    private void handleCriticalProcess() {
        if (!isCriticaling || mc.player == null || lastTarget == null) {
            isCriticaling = false;
            return;
        }

        criticalTicks++;

        // Execute critical packet sequence
        if (criticalTicks == 1) {
            // First tick: Send position packets
        } else if (criticalTicks == 2) {
            // Second tick: Execute the attack
            attack(lastTarget);
            lastAttackTime = System.currentTimeMillis();
            comboHits++;
            isCriticaling = false;

            // Handle auto-blocking if enabled
            if (autoBlock.getValue()) {
                tryBlock();
            }
        }
    }



    /**
     * Checks if the target is in a vulnerable state for increased damage
     */
    private boolean isTargetVulnerable(Entity target) {
        if (!(target instanceof LivingEntity livingTarget)) return false;

        // Check if target is in vulnerable state
        boolean isJumping = !livingTarget.isOnGround() && livingTarget.getVelocity().y > 0;
        boolean isSlowed = livingTarget.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
        boolean isLowHealth = livingTarget instanceof PlayerEntity &&
                ((PlayerEntity)livingTarget).getHealth() < 6.0f;
        boolean isRegenerating = livingTarget.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.REGENERATION);
        boolean isEating = livingTarget instanceof PlayerEntity &&
                ((PlayerEntity)livingTarget).isUsingItem();

        return isJumping || isSlowed || isLowHealth || (isRegenerating && isLowHealth) || isEating;
    }

    /**
     * Switches to the optimal weapon for combat if available
     */

    }