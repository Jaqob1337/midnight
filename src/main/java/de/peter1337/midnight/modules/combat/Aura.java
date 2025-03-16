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
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

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

    // High ping specific settings
    private final Setting<Boolean> pingCompensation = register(
            new Setting<>("PingCompensation", Boolean.TRUE, "Adjust timing for high ping")
    );

    private final Setting<Integer> pingEstimate = register(
            new Setting<>("PingEstimate", 150, 0, 1000, "Your estimated ping in milliseconds")
                    .dependsOn(pingCompensation)
    );

    private final Setting<Boolean> predictPosition = register(
            new Setting<>("PredictPosition", Boolean.TRUE, "Predict where target will be based on ping")
                    .dependsOn(pingCompensation)
    );

    private final Setting<Boolean> preAttack = register(
            new Setting<>("PreAttack", Boolean.TRUE, "Start attack sequence earlier based on ping")
                    .dependsOn(pingCompensation)
    );

    private final Setting<Boolean> extraPackets = register(
            new Setting<>("ExtraPackets", Boolean.TRUE, "Send additional attack packets for high ping")
                    .dependsOn(pingCompensation)
    );

    private final Setting<Integer> packetMultiplier = register(
            new Setting<>("PacketMultiplier", 2, 1, 5, "How many extra packets to send")
                    .dependsOn(extraPackets)
    );

    private final Setting<Integer> targetHistorySize = register(
            new Setting<>("TargetHistorySize", 3, 1, 10, "How many past targets to remember")
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

    // High ping specific variables
    private List<TargetHistory> targetHistory = new ArrayList<>();
    private long lastAttackPacketTime = 0;
    private boolean isAttackConfirmed = false;
    private int attackTimeoutCounter = 0;
    private static final int MAX_ATTACK_TIMEOUT = 20; // 1 second timeout (20 ticks)
    private List<TargetRequest> pendingAttacks = new ArrayList<>();

    // Store entity health to detect successful hits
    private class TargetHistory {
        Entity entity;
        float lastHealth;
        long timestamp;

        public TargetHistory(Entity entity, float health) {
            this.entity = entity;
            this.lastHealth = health;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // For high-ping, track attack requests
    private class TargetRequest {
        Entity entity;
        long timestamp;
        boolean processed;

        public TargetRequest(Entity entity) {
            this.entity = entity;
            this.timestamp = System.currentTimeMillis();
            this.processed = false;
        }
    }

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
        targetHistory.clear();
        isAttackConfirmed = false;
        attackTimeoutCounter = 0;
        pendingAttacks.clear();

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
        targetHistory.clear();
        isAttackConfirmed = false;
        attackTimeoutCounter = 0;
        pendingAttacks.clear();

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

        // Check if we've successfully hit a target
        checkHitConfirmation();

        // Process pending attacks (for high-ping compensation)
        processPendingAttacks();

        // Handle attack timeout (for high ping situations)
        if (!isAttackConfirmed && primaryTarget != null && attackTimeoutCounter > 0) {
            attackTimeoutCounter--;

            // If we've waited too long for hit confirmation, try again
            if (attackTimeoutCounter <= 0 && pingCompensation.getValue()) {
                // Re-attempt attack if no confirmation came through
                if (primaryTarget instanceof LivingEntity) {
                    if (preAttack.getValue()) {
                        requestAttack(primaryTarget);
                        lastAttackTime = System.currentTimeMillis();
                    }
                }
            }
        }
    }

    /**
     * Process pending attack requests
     */
    private void processPendingAttacks() {
        if (!pingCompensation.getValue() || !extraPackets.getValue()) {
            pendingAttacks.clear();
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Remove old requests
        pendingAttacks.removeIf(request -> currentTime - request.timestamp > 2000);

        // Process unprocessed requests
        for (TargetRequest request : pendingAttacks) {
            if (!request.processed && request.entity != null && !request.entity.isRemoved() &&
                    request.entity instanceof LivingEntity && ((LivingEntity) request.entity).getHealth() > 0) {

                // Send packet directly
                directAttack(request.entity);
                request.processed = true;
            }
        }
    }

    /**
     * Check if our attacks actually landed by monitoring target health
     */
    private void checkHitConfirmation() {
        // Remove old entries from history
        long currentTime = System.currentTimeMillis();
        targetHistory.removeIf(entry -> currentTime - entry.timestamp > 2000); // 2 second timeout

        // Check for hit confirmation
        if (primaryTarget instanceof LivingEntity livingTarget) {
            float currentHealth = livingTarget.getHealth();

            // Check if this target is in our history
            for (TargetHistory entry : targetHistory) {
                if (entry.entity == primaryTarget && entry.lastHealth > currentHealth) {
                    // We've confirmed a hit!
                    isAttackConfirmed = true;
                    attackTimeoutCounter = 0;

                    // Update the health in history
                    entry.lastHealth = currentHealth;
                    entry.timestamp = currentTime;
                    return;
                }
            }

            // Add target to history if not already there
            boolean found = false;
            for (TargetHistory entry : targetHistory) {
                if (entry.entity == primaryTarget) {
                    entry.lastHealth = currentHealth;
                    entry.timestamp = currentTime;
                    found = true;
                    break;
                }
            }

            if (!found) {
                targetHistory.add(new TargetHistory(primaryTarget, currentHealth));

                // Limit history size
                while (targetHistory.size() > targetHistorySize.getValue()) {
                    targetHistory.remove(0);
                }
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

        // Apply ping compensation to attack timing
        if (pingCompensation.getValue()) {
            // If ping is high, we need to reduce delay to compensate
            float pingFactor = 1.0f - MathHelper.clamp(pingEstimate.getValue() / 2000.0f, 0.0f, 0.5f);
            baseDelay *= pingFactor;
        }

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

            // Apply ping compensation for target position
            if (pingCompensation.getValue() && predictPosition.getValue()) {
                predictTargetPosition();
            } else {
                // Find a visible point on the target to aim at
                targetPoint = findVisiblePoint(primaryTarget);
            }

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
                isAttackConfirmed = false;
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

            // Attack if rotation is complete, cooldown is ready, and we can attack
            if (canAttack && cooldownReady && RotationHandler.isRotationActive() && canHit && !isCriticaling) {
                // For high ping, we need to be more careful about when to start attack animations
                if (pingCompensation.getValue() && preAttack.getValue()) {
                    // Start attack process earlier for high ping
                    attackTimeoutCounter = MAX_ATTACK_TIMEOUT;
                    isAttackConfirmed = false;
                }

                // Check for critical hit opportunity
                boolean shouldCritical = shouldPerformCritical();

                if (shouldCritical) {
                    // Start critical hit sequence
                    startCriticalAttack(primaryTarget);
                } else {
                    // Request the attack (possibly multiple packets for high ping)
                    requestAttack(primaryTarget);
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
     * Request an attack on a target, with extra packets for high ping
     */
    private void requestAttack(Entity target) {
        if (target == null || mc.player == null) return;

        // Always perform the normal attack
        attack(target);

        // For high ping, possibly send additional attack packets
        if (pingCompensation.getValue() && extraPackets.getValue() && packetMultiplier.getValue() > 1) {
            // Add to pending attacks for processing in upcoming ticks
            pendingAttacks.add(new TargetRequest(target));

            // Also send some immediate extra packets
            for (int i = 1; i < packetMultiplier.getValue(); i++) {
                if (random.nextFloat() < 0.7f) {  // 70% chance to send each extra packet
                    directAttack(target);
                }
            }
        }
    }

    /**
     * Send attack packet directly without animation
     */
    private void directAttack(Entity target) {
        if (mc.player == null || mc.interactionManager == null || target == null) return;

        // Send the attack packet directly without animation
        if (System.currentTimeMillis() - lastPacketSentTime >= MIN_PACKET_INTERVAL) {
            mc.interactionManager.attackEntity(mc.player, target);
            lastPacketSentTime = System.currentTimeMillis();
        }
    }

    /**
     * Predict where the target will be after ping delay
     */
    private void predictTargetPosition() {
        if (primaryTarget == null || lastTargetPos == null) {
            targetPoint = findVisiblePoint(primaryTarget);
            return;
        }

        // Calculate current velocity vector
        Vec3d currentPos = primaryTarget.getPos();
        Vec3d velocity = currentPos.subtract(lastTargetPos);

        // Scale velocity by ping (convert ms to seconds)
        float pingSeconds = pingEstimate.getValue() / 1000.0f;

        // Predict future position based on current velocity and ping
        Vec3d predictedPos = currentPos.add(
                velocity.x * pingSeconds * 15.0,
                Math.max(-0.5, Math.min(0.5, velocity.y * pingSeconds * 10.0)), // Limit vertical prediction
                velocity.z * pingSeconds * 15.0
        );

        // Check if predicted position is reasonable (not too far)
        double maxPredictDistance = 5.0;
        if (predictedPos.distanceTo(currentPos) > maxPredictDistance) {
            // Limit the prediction distance
            Vec3d direction = predictedPos.subtract(currentPos).normalize();
            predictedPos = currentPos.add(direction.multiply(maxPredictDistance));
        }

        // Now try to find a visible point at this predicted position
        Vec3d visiblePoint = RaytraceUtil.getVisiblePoint(primaryTarget);

        // If we can get a visible point, offset it by our prediction
        if (visiblePoint != null) {
            Vec3d offset = predictedPos.subtract(currentPos);
            targetPoint = visiblePoint.add(offset);
        } else {
            // Fallback to direct predicted position
            targetPoint = predictedPos.add(0, primaryTarget.getHeight() / 2, 0);
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

        // For high ping, increase effective range slightly to compensate for movement delay
        if (pingCompensation.getValue()) {
            float pingRangeBonus = Math.min(2.0f, pingEstimate.getValue() / 300.0f);
            rangeSq = (range.getValue() + pingRangeBonus) * (range.getValue() + pingRangeBonus);
        }

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
                float pingFactor = 1.0f;
                if (pingCompensation.getValue()) {
                    // Increase prediction for high ping
                    pingFactor = 1.0f + (pingEstimate.getValue() / 500.0f);
                }

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

        // High ping adjustments
        if (pingCompensation.getValue()) {
            // For high ping, we need faster rotations to compensate for delay
            float pingFactor = 1.0f + (pingEstimate.getValue() / 500.0f);
            speedMultiplier = Math.min(1.0f, speedMultiplier * pingFactor);
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

            // For high ping, we might want to be more aggressive with packets
            long minInterval = pingCompensation.getValue() ?
                    Math.max(5, MIN_PACKET_INTERVAL - (pingEstimate.getValue() / 50)) :
                    MIN_PACKET_INTERVAL;

            // Ensure we don't send packets too frequently (avoid packet spam detection)
            if (currentTime - lastPacketSentTime >= minInterval) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                lastPacketSentTime = currentTime;
                lastAttackPacketTime = currentTime;
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

        // For high ping, we adjust the critical sequence timing
        int jumpTick = pingCompensation.getValue() ? 1 : 2;
        int attackTick = pingCompensation.getValue() ? 2 : 3;

        // Execute critical packet sequence
        if (criticalTicks == jumpTick) {
            // First tick: Apply the jump effect
            if (!pingCompensation.getValue()) {
                // For normal ping: Send position packets
                double x = mc.player.getX();
                double y = mc.player.getY();
                double z = mc.player.getZ();

                // Send subtle position changes that trigger critical hit
                mc.player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, true, false)
                );
                mc.player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, false)
                );
            } else {
                // For high ping: Send optimized critical hit packets
                double x = mc.player.getX();
                double y = mc.player.getY();
                double z = mc.player.getZ();

                // Send a sequence of position packets that reliably trigger critical hits
                mc.player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.05, z, false, false)
                );
                mc.player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0, z, false, false)
                );
                mc.player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.012, z, false, false)
                );
                mc.player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0, z, true, false)
                );
            }
        } else if (criticalTicks == attackTick) {
            // Attack tick: Execute the attack
            requestAttack(lastTarget);
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
}