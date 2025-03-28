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
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ShieldItem;
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

    // Current target entity
    private Entity primaryTarget = null;
    private Vec3d targetPoint = null;

    // The rotation priority for this module
    private static final int ROTATION_PRIORITY = 100;

    // --- Settings ---
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
            new Setting<>("RotationSpeed", 0.4f, 0.0f, 2.0f, "Speed of rotation to targets (0 = smooth, 1 = instant)")
    );

    private final Setting<Boolean> useMoveFixSetting = register( // New MoveFix Toggle
            new Setting<>("MoveFix", Boolean.TRUE, "Correct movement direction during silent/body rotations")
            // Optional: Add dependency if needed, e.g., only show if rotationMode is Silent/Body
            // .dependsOn(rotationMode, mode -> mode.equals("Silent") || mode.equals("Body"))
    );

    private final Setting<Boolean> throughWalls = register(
            new Setting<>("ThroughWalls", Boolean.FALSE, "Attack through walls")
    );

    private final Setting<Boolean> smartAttack = register(
            new Setting<>("SmartAttack", Boolean.TRUE, "Only attack when weapon is charged (>= 0.95)")
    );

    private final Setting<Boolean> raytraceCheck = register(
            new Setting<>("RaytraceCheck", Boolean.TRUE, "Verify rotation line-of-sight before attacking")
    );

    private final Setting<Boolean> autoBlock = register(
            new Setting<>("AutoBlock", Boolean.FALSE, "Automatically block with weapon")
    );

    // Correct dependency syntax might depend on your Setting class implementation
    private final Setting<String> blockMode = register(
            new Setting<>("BlockMode", "Real",
                    List.of("Real", "Fake"),
                    "Real: Actually block, Fake: Visual only")
            // Example: .dependsOn(autoBlock, val -> val == Boolean.TRUE) // Adjust based on actual Setting class
    );

    // --- State Properties ---
    private long lastAttackTime = 0;
    private boolean rotating = false;
    private boolean attackedThisTick = false;
    private boolean isBlocking = false;
    private int ticksToWaitBeforeBlock = -1; // Timer for blocking delay

    public Aura() {
        super("Aura", "Automatically attacks nearby entities", Category.COMBAT, "r");
        // Initialize dependencies here if needed by your Setting class framework
    }

    @Override
    public void onEnable() {
        primaryTarget = null;
        targetPoint = null;
        rotating = false;
        isBlocking = false;
        attackedThisTick = false;
        ticksToWaitBeforeBlock = -1;
        lastAttackTime = 0;
    }

    @Override
    public void onDisable() {
        primaryTarget = null;
        targetPoint = null;
        rotating = false;
        attackedThisTick = false; // Reset flag

        RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);

        if (isBlocking) {
            stopBlocking(); // Also sets isBlocking to false
        }
        ticksToWaitBeforeBlock = -1;
    }

    // Use a pre-update hook if available (e.g., ClientTickEvent.START_CLIENT_TICK)
    public void preUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) {
            // Ensure state is clean if disabled mid-tick
            if (isBlocking) stopBlocking();
            if (rotating) RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
            rotating = false;
            primaryTarget = null;
            targetPoint = null;
            return;
        }

        handleBlockTimer(); // Handle block delay
        findTargets();      // Find targets early
        processAttack();    // Process logic

        attackedThisTick = true; // Mark logic as done for this tick
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Skip if logic was done in preUpdate
        if (attackedThisTick) {
            attackedThisTick = false; // Reset for next tick
            return;
        }

        // Fallback: Run logic in onUpdate if preUpdate isn't used/reliable
        // handleBlockTimer(); // Uncomment if preUpdate isn't reliable
        // findTargets();      // Uncomment if preUpdate isn't reliable
        processAttack();
    }

    /** Handles the tick-based delay before re-blocking after an attack. */
    private void handleBlockTimer() {
        if (this.ticksToWaitBeforeBlock > 0) {
            this.ticksToWaitBeforeBlock--;
        } else if (this.ticksToWaitBeforeBlock == 0) {
            handleAutoBlock(this.primaryTarget != null); // Try to resume blocking if target exists
            this.ticksToWaitBeforeBlock = -1; // Reset timer
        }
    }

    /** Processes the main attack and rotation logic. */
    private void processAttack() {
        // --- Attack Timer ---
        long currentTime = System.currentTimeMillis();
        float baseDelay = 1000.0f / Math.max(0.1f, cps.getValue()); // Avoid division by zero
        long attackDelay;
        if (randomCps.getValue()) {
            float variation = baseDelay * 0.2f; // 20% variation
            attackDelay = (long) (baseDelay + (random.nextDouble() * variation * 2.0 - variation));
        } else {
            float variation = baseDelay * 0.05f; // 5% variation
            attackDelay = (long) (baseDelay + (random.nextDouble() * variation * 2.0 - variation));
        }
        attackDelay = Math.max(50, attackDelay); // Min ~1 tick delay
        boolean canAttack = currentTime - lastAttackTime >= attackDelay;

        // --- Target Handling ---
        if (primaryTarget != null && mc.player != null) {
            // Cooldown Check
            float cooldownProgress = mc.player.getAttackCooldownProgress(0.0f);
            boolean cooldownReady = !smartAttack.getValue() || cooldownProgress >= 0.95f;

            // Target Point Calculation
            targetPoint = getBestTargetPoint(primaryTarget);
            if (targetPoint == null) { // Fallback if no point found (e.g., throughWalls=false, no LoS)
                targetPoint = primaryTarget.getEyePos(); // Or potentially cancel here?
            }

            // Visibility Check (if needed)
            if (!throughWalls.getValue() && !isPointVisible(targetPoint)) {
                // No target point visible and ThroughWalls is off
                RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
                rotating = false;
                handleAutoBlock(false); // Stop blocking
                return; // Don't rotate or attack
            }

            // Rotation Handling
            handleRotations(); // Requests rotation via RotationHandler

            // Rotation Line-of-Sight Check (if rotating and setting enabled)
            boolean canHitWithRotation = true;
            if (raytraceCheck.getValue() && RotationHandler.isRotationActive() && rotating) {
                canHitWithRotation = RayCastUtil.canSeeEntityFromRotation(
                        primaryTarget,
                        RotationHandler.getServerYaw(),
                        RotationHandler.getServerPitch(),
                        range.getValue() + 1.0 // Buffer
                );
            }

            // Auto-Blocking (attempt to block if conditions met)
            if (ticksToWaitBeforeBlock < 0) { // Only if not on post-attack cooldown
                handleAutoBlock(true); // Attempt to block if target exists
            }

            // Attack Execution
            if (canAttack && cooldownReady && RotationHandler.isRotationActive() && rotating && canHitWithRotation && ticksToWaitBeforeBlock < 0) {
                boolean wasBlocking = isBlocking;
                if (wasBlocking) {
                    stopBlocking();
                }

                attack(primaryTarget);
                lastAttackTime = currentTime;

                if (wasBlocking) {
                    this.ticksToWaitBeforeBlock = 5; // Start post-attack block cooldown
                }
            }
        } else {
            // --- No Target ---
            targetPoint = null; // Clear target point
            RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
            rotating = false;
            if (ticksToWaitBeforeBlock < 0) { // Only stop blocking if not on cooldown
                handleAutoBlock(false);
            }
        }
    }

    /** Handles starting/stopping blocking based on settings and conditions. */
    private void handleAutoBlock(boolean shouldBlock) {
        if (!autoBlock.getValue()) {
            if (isBlocking) stopBlocking();
            return;
        }
        if (ticksToWaitBeforeBlock >= 0) { // Check block cooldown timer
            if(isBlocking) stopBlocking(); // Ensure we stop if on cooldown
            return;
        }

        boolean canCurrentlyBlock = shouldBlock && canBlockWithCurrentItem();

        if (canCurrentlyBlock && !isBlocking) { // Start blocking
            if (blockMode.getValue().equals("Real")) startRealBlocking();
            else if (blockMode.getValue().equals("Fake")) startFakeBlocking();
        } else if (!canCurrentlyBlock && isBlocking) { // Stop blocking
            stopBlocking();
        }
    }

    /** Starts real blocking - sends actual block commands to server */
    private void startRealBlocking() {
        if (mc.player == null || mc.interactionManager == null || isBlocking) return;
        mc.options.useKey.setPressed(true);
        // Optional: Check which hand has shield/sword and use that hand?
        if (!mc.player.isUsingItem()) { // Avoid spamming interact packet if holding key is enough
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND); // Adjust hand if needed
        }
        isBlocking = true;
    }

    /** Stops real blocking */
    private void stopRealBlocking() {
        if (mc.player == null || !isBlocking) return;
        mc.options.useKey.setPressed(false);
        // If interactItem was needed, maybe send stop action? Needs testing.
        isBlocking = false;
    }

    /** Starts fake blocking - client-side only */
    private void startFakeBlocking() {
        if (mc.player == null || isBlocking) return;
        if (!mc.player.isUsingItem()) { // Only start if not already using
            mc.player.setCurrentHand(Hand.MAIN_HAND); // Adjust hand if needed
        }
        isBlocking = true;
    }

    /** Stops fake blocking */
    private void stopFakeBlocking() {
        if (mc.player == null || !isBlocking) return;
        // Only clear if we were the one using the item (check hand?)
        if (mc.player.getActiveHand() == Hand.MAIN_HAND) { // Adjust hand if needed
            mc.player.clearActiveItem();
        }
        isBlocking = false;
    }

    /** Stops blocking based on current mode */
    private void stopBlocking() {
        if (!isBlocking) return;
        if (blockMode.getValue().equals("Real")) stopRealBlocking();
        else if (blockMode.getValue().equals("Fake")) stopFakeBlocking();
        isBlocking = false; // Ensure flag is always false after calling stop
    }

    /** Checks if the player can block with the current item in main or offhand */
    private boolean canBlockWithCurrentItem() {
        if (mc.player == null) return false;
        ItemStack mainHandItem = mc.player.getMainHandStack();
        ItemStack offHandItem = mc.player.getOffHandStack();
        return (!mainHandItem.isEmpty() && (mainHandItem.getItem() instanceof SwordItem || mainHandItem.getItem() instanceof ShieldItem)) ||
                (!offHandItem.isEmpty() && (offHandItem.getItem() instanceof ShieldItem));
    }

    /** Finds the best point on an entity's hitbox to target. */
    private Vec3d getBestTargetPoint(Entity entity) {
        if (entity == null || mc.player == null) return entity != null ? entity.getEyePos() : null;

        Vec3d visiblePoint = RayCastUtil.getVisiblePoint(entity); // Uses the multi-point check
        if (visiblePoint != null) return visiblePoint;

        if (throughWalls.getValue()) {
            Box box = entity.getBoundingBox();
            return new Vec3d((box.minX + box.maxX) / 2.0, (box.minY + box.maxY) / 2.0, (box.minZ + box.maxZ) / 2.0);
        }

        // If !throughWalls and no visible point, return null or fallback?
        // Fallback to eye pos allows rotation even if not visible (visibility checked later)
        return entity.getEyePos();
        // return null; // Returning null would stop rotation if no visible point found
    }

    /** Check if a specific point is visible to the player using RayCastUtil. */
    private boolean isPointVisible(Vec3d point) {
        if (mc.player == null || mc.world == null || point == null) return false;
        // Assumes RayCastUtil.canSeePosition(Vec3d) is correctly implemented
        return RayCastUtil.canSeePosition(point);
    }

    /** Finds and prioritizes potential targets */
    private void findTargets() {
        primaryTarget = null; // Reset target each tick
        if (mc.player == null || mc.world == null) return;

        List<Entity> potentialTargets = new ArrayList<>();
        float currentRange = range.getValue();
        double rangeSq = currentRange * currentRange; // Use squared range for box check

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || entity.isRemoved() || !(entity instanceof LivingEntity livingEntity)) continue;
            if (livingEntity.isDead() || livingEntity.getHealth() <= 0) continue;

            // Rough distance check
            double roughRangeCheck = Math.pow(currentRange + entity.getWidth() + 2.0, 2);
            if (mc.player.squaredDistanceTo(entity) > roughRangeCheck) continue;


            // Type filters
            if (entity instanceof PlayerEntity player) {
                if (!targetPlayers.getValue() || player.isSpectator() || player.isCreative()) continue;
                // Add team/friend checks if needed
            } else if (entity instanceof HostileEntity) {
                if (!targetHostiles.getValue()) continue;
            } else if (entity instanceof PassiveEntity) {
                if (!targetPassives.getValue()) continue;
            } else {
                continue; // Skip other types
            }

            // Precise range check (distance to closest point on bounding box)
            if (!isEntityInRange(entity, currentRange)) continue;

            // Visibility check (if not attacking through walls)
            // Check if ANY part is visible for targeting purposes
            if (!throughWalls.getValue() && !RayCastUtil.canSeeEntity(entity)) {
                continue;
            }

            potentialTargets.add(entity);
        }

        // Sort and select target
        if (!potentialTargets.isEmpty()) {
            switch (targetMode.getValue()) {
                case "Closest":
                    potentialTargets.sort(Comparator.comparingDouble(mc.player::squaredDistanceTo));
                    break;
                case "Health":
                    potentialTargets.sort(Comparator.comparingDouble(e -> ((LivingEntity) e).getHealth()));
                    break;
                case "Angle":
                    final float playerYaw = mc.player.getYaw();
                    final float playerPitch = mc.player.getPitch();
                    potentialTargets.sort(Comparator.comparingDouble(entity -> {
                        float[] rotations = RotationHandler.calculateLookAt(entity.getEyePos());
                        float yawDiff = Math.abs(MathHelper.wrapDegrees(rotations[0] - playerYaw));
                        float pitchDiff = Math.abs(rotations[1] - playerPitch);
                        return yawDiff + pitchDiff; // Simple sum or sqrt(yaw^2 + pitch^2)
                    }));
                    break;
                default:
                    potentialTargets.sort(Comparator.comparingDouble(mc.player::squaredDistanceTo));
                    break;
            }
            primaryTarget = potentialTargets.get(0); // Pick the best one
        }
    }

    /** Checks if any part of an entity's hitbox is within range using bounding box distance. */
    private boolean isEntityInRange(Entity entity, float range) {
        if (mc.player == null || entity == null) return false;
        Vec3d playerEyePos = mc.player.getEyePos();
        Box entityBox = entity.getBoundingBox();
        double rangeSq = range * range;
        double closestX = MathHelper.clamp(playerEyePos.x, entityBox.minX, entityBox.maxX);
        double closestY = MathHelper.clamp(playerEyePos.y, entityBox.minY, entityBox.maxY);
        double closestZ = MathHelper.clamp(playerEyePos.z, entityBox.minZ, entityBox.maxZ);
        return playerEyePos.squaredDistanceTo(closestX, closestY, closestZ) <= rangeSq;
    }

    /**
     * Handles rotations to the primary target using RotationHandler.
     */
    private void handleRotations() {
        if (mc.player == null || targetPoint == null || primaryTarget == null) return;

        // Calculate base rotations to target
        float[] baseRotations;

        // For very close targets, use a different targeting approach
        if (mc.player.squaredDistanceTo(primaryTarget) < 3.0) { // If within ~1.7 blocks
            // Get target's eye height to avoid aiming too high
            float targetEyeHeight = primaryTarget.getEyeHeight(primaryTarget.getPose());
            Vec3d closeTargetPos = primaryTarget.getPos().add(0, targetEyeHeight * 0.85, 0);

            // Calculate more accurate rotations for close combat
            baseRotations = RotationHandler.calculateLookAt(closeTargetPos);

            // For close targets, limit the pitch to avoid looking too far up/down
            baseRotations[1] = MathHelper.clamp(baseRotations[1], -45f, 60f);
        } else {
            // Normal targeting for regular distances
            baseRotations = RotationHandler.calculateLookAt(targetPoint);
        }

        // Determine rotation mode parameters
        boolean silent;
        boolean bodyOnly;
        boolean moveFixRequired;

        switch (rotationMode.getValue()) {
            case "Silent":
                silent = true;
                bodyOnly = false;
                break;
            case "Client":
                silent = false;
                bodyOnly = false;
                break;
            case "Body":
                // For Body mode, rotations are sent to server but also applied to model
                silent = true; // Still "silent" from camera perspective
                bodyOnly = true; // Apply to body model
                break;
            default:
                silent = true;
                bodyOnly = false;
                break;
        }

        moveFixRequired = (silent || bodyOnly) && useMoveFixSetting.getValue();

        float currentYaw = (silent || bodyOnly) ? RotationHandler.getServerYaw() : mc.player.getYaw();
        float currentPitch = (silent || bodyOnly) ? RotationHandler.getServerPitch() : mc.player.getPitch();

        float speed = rotationSpeed.getValue();

        // Apply smooth rotation with the current speed setting
        float[] smoothedRotations = RotationHandler.smoothRotation(
                currentYaw,
                currentPitch,
                baseRotations[0],
                baseRotations[1],
                speed
        );

        // Request the rotation step with correct parameters
        RotationHandler.requestRotation(
                smoothedRotations[0],
                smoothedRotations[1],
                ROTATION_PRIORITY,
                60, // Duration slightly longer than a tick
                silent,
                bodyOnly,
                moveFixRequired,
                null
        );
        rotating = true;
    }


    /** Attacks the target entity */
    private void attack(Entity target) {
        if (mc.player == null || mc.interactionManager == null || target == null) return;
        if (isEntityInRange(target, range.getValue())) { // Final range check
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }
}