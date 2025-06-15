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
import net.minecraft.item.ShieldItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class Aura extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    // Target management
    private Entity target = null;
    private Entity lastTarget = null;
    private Vec3d targetPoint = null;
    private long lastAttackTime = 0;
    private long lastSwitchTime = 0;
    private boolean isBlocking = false;

    // Attack validation
    private long lastHitTime = 0;
    private int missCount = 0;
    private float lastTargetHealth = 0;

    // Bypass mechanics
    private boolean useSnapRotations = false;
    private long lastSnapTime = 0;
    private float[] pendingRotations = null;
    private int rotationTicks = 0;

    // Settings
    private final Setting<Float> range = register(new Setting<>("Range", 3.2f, 1.0f, 6.0f, "Attack range"));
    private final Setting<Float> cps = register(new Setting<>("CPS", 11.5f, 1.0f, 20.0f, "Clicks per second"));
    private final Setting<Boolean> randomCps = register(new Setting<>("RandomCPS", Boolean.TRUE, "Randomize CPS"));
    private final Setting<Float> cpsRandomness = register(new Setting<>("CPSRandomness", 2.0f, 0.1f, 5.0f, "CPS randomness range").dependsOn(randomCps));

    private final Setting<String> targetMode = register(new Setting<>("TargetMode", "Smart",
            Arrays.asList("Closest", "Health", "Angle", "Smart"), "Target priority"));

    private final Setting<Boolean> targetPlayers = register(new Setting<>("TargetPlayers", Boolean.TRUE, "Target players"));
    private final Setting<Boolean> targetHostiles = register(new Setting<>("TargetHostiles", Boolean.TRUE, "Target hostile mobs"));
    private final Setting<Boolean> targetPassives = register(new Setting<>("TargetPassives", Boolean.FALSE, "Target passive mobs"));

    private final Setting<String> rotationMode = register(new Setting<>("RotationMode", "Smart",
            Arrays.asList("Silent", "Client", "Smart", "Snap"), "Rotation mode"));
    private final Setting<Float> rotationSpeed = register(new Setting<>("RotationSpeed", 0.95f, 0.3f, 1.0f, "Rotation speed"));
    private final Setting<Boolean> snapRotations = register(new Setting<>("SnapRotations", Boolean.TRUE, "Use snap rotations for fast targets"));
    private final Setting<Float> snapThreshold = register(new Setting<>("SnapThreshold", 25.0f, 10.0f, 45.0f, "Angle threshold for snap rotations").dependsOn(snapRotations));

    private final Setting<Boolean> throughWalls = register(new Setting<>("ThroughWalls", Boolean.FALSE, "Attack through walls"));
    private final Setting<Boolean> autoBlock = register(new Setting<>("AutoBlock", Boolean.FALSE, "Auto block with sword/shield"));
    private final Setting<Boolean> onlyWhenWeapon = register(new Setting<>("OnlyWhenWeapon", Boolean.TRUE, "Only attack when holding a weapon"));

    // Combat settings
    private final Setting<Boolean> cooldownCheck = register(new Setting<>("CooldownCheck", Boolean.TRUE, "Wait for attack cooldown"));
    private final Setting<Float> minCooldown = register(new Setting<>("MinCooldown", 0.9f, 0.1f, 1.0f, "Minimum cooldown percentage").dependsOn(cooldownCheck));
    private final Setting<Boolean> sprintReset = register(new Setting<>("SprintReset", Boolean.TRUE, "Reset sprint for criticals"));
    private final Setting<Boolean> criticals = register(new Setting<>("Criticals", Boolean.TRUE, "Attempt critical hits"));

    // Bypass settings
    private final Setting<Boolean> hitValidation = register(new Setting<>("HitValidation", Boolean.TRUE, "Validate hits and adjust"));
    private final Setting<Boolean> targetStrafe = register(new Setting<>("TargetStrafe", Boolean.FALSE, "Strafe around target"));
    private final Setting<Float> strafeSpeed = register(new Setting<>("StrafeSpeed", 0.25f, 0.1f, 0.5f, "Strafe movement speed").dependsOn(targetStrafe));
    private final Setting<Boolean> faceTarget = register(new Setting<>("FaceTarget", Boolean.TRUE, "Face target before attacking"));
    private final Setting<Float> faceThreshold = register(new Setting<>("FaceThreshold", 15.0f, 5.0f, 30.0f, "Max angle to face target").dependsOn(faceTarget));

    // Advanced prediction
    private Vec3d lastTargetPos = null;
    private Vec3d targetVelocity = null;
    private long lastPosUpdateTime = 0;
    private List<Vec3d> targetPositionHistory = new ArrayList<>();

    public Aura() {
        super("Aura", "Advanced killaura with Intave bypass", Category.COMBAT, "r");
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
        RotationHandler.resetRotations();
        if (isBlocking) stopBlocking();
    }

    private void reset() {
        target = null;
        lastTarget = null;
        targetPoint = null;
        lastAttackTime = 0;
        lastSwitchTime = 0;
        isBlocking = false;
        lastHitTime = 0;
        missCount = 0;
        lastTargetHealth = 0;
        useSnapRotations = false;
        lastSnapTime = 0;
        pendingRotations = null;
        rotationTicks = 0;
        lastTargetPos = null;
        targetVelocity = null;
        targetPositionHistory.clear();
    }

    public void preUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) {
            if (isBlocking) stopBlocking();
            return;
        }

        processAura();
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;
        // Fallback if preUpdate wasn't called
        processAura();
    }

    private void processAura() {

        updateTargetPrediction();
        findTarget();

        if (target != null) {
            // Validate target is still valid
            if (!isValidTarget(target)) {
                target = null;
                return;
            }

            // Update target point with improved prediction
            targetPoint = calculateOptimalTargetPoint();

            // Check visibility
            if (!throughWalls.getValue() && !canSeeTarget()) {
                // Try to find visible point on target
                Vec3d visiblePoint = RayCastUtil.getVisiblePoint(target);
                if (visiblePoint != null) {
                    targetPoint = visiblePoint;
                } else {
                    target = null;
                    return;
                }
            }

            // Handle rotations with smart mode
            handleSmartRotations();

            // Handle movement (strafe)
            if (targetStrafe.getValue()) {
                handleTargetStrafe();
            }

            // Auto blocking
            if (autoBlock.getValue()) {
                handleAutoBlock();
            }

            // Attack logic
            tryAttack();

        } else {
            if (isBlocking) stopBlocking();
            targetPoint = null;
        }
    }

    private void findTarget() {
        long currentTime = System.currentTimeMillis();

        // Don't switch targets too frequently
        if (target != null && currentTime - lastSwitchTime < 150) {
            return;
        }

        List<Entity> potentialTargets = new ArrayList<>();
        double rangeSquared = range.getValue() * range.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity)) continue;

            double distance = mc.player.squaredDistanceTo(entity);
            if (distance > rangeSquared) continue;

            potentialTargets.add(entity);
        }

        if (potentialTargets.isEmpty()) {
            target = null;
            return;
        }

        Entity newTarget = selectBestTarget(potentialTargets);

        if (newTarget != target) {
            lastTarget = target;
            target = newTarget;
            lastSwitchTime = currentTime;

            // Reset some values for new target
            missCount = 0;
            if (target instanceof LivingEntity) {
                lastTargetHealth = ((LivingEntity) target).getHealth();
            }
        }
    }

    private Entity selectBestTarget(List<Entity> targets) {
        switch (targetMode.getValue()) {
            case "Smart":
                return selectSmartTarget(targets);
            case "Health":
                targets.sort(Comparator.comparingDouble(e ->
                        e instanceof LivingEntity ? ((LivingEntity) e).getHealth() : Float.MAX_VALUE));
                break;
            case "Angle":
                targets.sort(Comparator.comparingDouble(this::getAngleToEntity));
                break;
            case "Closest":
            default:
                targets.sort(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)));
                break;
        }
        return targets.get(0);
    }

    private Entity selectSmartTarget(List<Entity> targets) {
        // Smart targeting considers multiple factors
        Entity bestTarget = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : targets) {
            double score = calculateTargetScore(entity);
            if (score < bestScore) {
                bestScore = score;
                bestTarget = entity;
            }
        }

        return bestTarget;
    }

    private double calculateTargetScore(Entity entity) {
        double distance = mc.player.squaredDistanceTo(entity);
        double angle = getAngleToEntity(entity);
        double health = entity instanceof LivingEntity ? ((LivingEntity) entity).getHealth() : 20.0;

        // Prioritize current target to reduce switching
        double continuityBonus = (entity == target) ? 0.3 : 0.0;

        // Normalize factors and combine
        double distanceScore = distance / (range.getValue() * range.getValue());
        double angleScore = angle / 180.0;
        double healthScore = health / 20.0;

        return distanceScore + angleScore + healthScore - continuityBonus;
    }

    private void updateTargetPrediction() {
        if (target == null) return;

        Vec3d currentPos = target.getPos();
        long currentTime = System.currentTimeMillis();

        // Store position history for better prediction
        targetPositionHistory.add(currentPos);
        if (targetPositionHistory.size() > 5) {
            targetPositionHistory.remove(0);
        }

        if (lastTargetPos != null) {
            float deltaTime = (currentTime - lastPosUpdateTime) / 1000.0f;
            if (deltaTime > 0 && deltaTime < 0.1f) {
                Vec3d rawVelocity = currentPos.subtract(lastTargetPos).multiply(1.0f / deltaTime);

                if (targetVelocity == null) {
                    targetVelocity = rawVelocity;
                } else {
                    // Smooth velocity with higher weight on recent data
                    targetVelocity = targetVelocity.multiply(0.4).add(rawVelocity.multiply(0.6));
                }
            }
        }

        lastTargetPos = currentPos;
        lastPosUpdateTime = currentTime;
    }

    private Vec3d calculateOptimalTargetPoint() {
        if (target == null) return null;

        Vec3d basePos = target.getEyePos();

        // Apply advanced prediction
        if (targetVelocity != null && targetVelocity.length() > 0.1) {
            // Calculate time to target
            double distance = mc.player.getEyePos().distanceTo(basePos);
            double timeToTarget = distance / 40.0; // Assume ~40 blocks/second for attacks

            // Predict position
            Vec3d prediction = targetVelocity.multiply(timeToTarget * 0.8); // Slightly under-predict
            basePos = basePos.add(prediction);

            // Clamp prediction to reasonable bounds
            Vec3d currentPos = target.getPos();
            double maxPrediction = 2.0;
            if (basePos.distanceTo(currentPos) > maxPrediction) {
                Vec3d direction = basePos.subtract(currentPos).normalize();
                basePos = currentPos.add(direction.multiply(maxPrediction));
            }
        }

        // Add slight randomization for bypass
        if (random.nextFloat() < 0.15f) {
            basePos = basePos.add(
                    (random.nextFloat() - 0.5f) * 0.08f,
                    (random.nextFloat() - 0.5f) * 0.06f,
                    (random.nextFloat() - 0.5f) * 0.08f
            );
        }

        return basePos;
    }

    private void handleSmartRotations() {
        if (target == null || targetPoint == null) return;

        float[] targetRotations = calculateLookAt(targetPoint);
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetRotations[0] - currentYaw));
        float pitchDiff = Math.abs(targetRotations[1] - currentPitch);

        boolean shouldSnap = false;
        String mode = rotationMode.getValue();

        // Determine if we should use snap rotations
        if (snapRotations.getValue() &&
                (yawDiff > snapThreshold.getValue() || pitchDiff > snapThreshold.getValue() / 2)) {
            shouldSnap = true;
        }

        // Force snap for very fast targets
        if (targetVelocity != null && targetVelocity.length() > 8.0) {
            shouldSnap = true;
        }

        if (mode.equals("Snap") || (mode.equals("Smart") && shouldSnap)) {
            // Snap rotation - instant but with slight delay
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSnapTime > 50) { // Max 20 snaps per second
                applySnapRotation(targetRotations);
                lastSnapTime = currentTime;
            }
        } else {
            // Smooth rotations
            applySmoothRotation(targetRotations);
        }
    }

    private void applySnapRotation(float[] targetRotations) {
        boolean silent = !rotationMode.getValue().equals("Client");

        // Add micro-randomization to snap
        targetRotations[0] += (random.nextFloat() - 0.5f) * 0.8f;
        targetRotations[1] += (random.nextFloat() - 0.5f) * 0.4f;

        RotationHandler.requestRotation(
                targetRotations[0],
                targetRotations[1],
                200, // High priority
                100,
                silent,
                false,
                true, // Enable move fix
                1.0f, // Instant speed
                null
        );
    }

    private void applySmoothRotation(float[] targetRotations) {
        boolean silent = rotationMode.getValue().equals("Silent") ||
                rotationMode.getValue().equals("Smart");

        float speed = rotationSpeed.getValue();

        // Increase speed for close targets
        double distance = mc.player.distanceTo(target);
        if (distance < 2.5) {
            speed = Math.min(1.0f, speed * 1.3f);
        }

        RotationHandler.requestRotation(
                targetRotations[0],
                targetRotations[1],
                150,
                150,
                silent,
                false,
                true,
                speed,
                null
        );
    }

    private void handleTargetStrafe() {
        if (target == null || mc.player == null) return;

        // Simple strafe implementation
        Vec3d targetPos = target.getPos();
        Vec3d playerPos = mc.player.getPos();
        Vec3d direction = targetPos.subtract(playerPos).normalize();

        // Perpendicular direction for strafing
        Vec3d strafeDir = new Vec3d(-direction.z, 0, direction.x);

        // Alternate strafe direction
        if (System.currentTimeMillis() % 2000 < 1000) {
            strafeDir = strafeDir.multiply(-1);
        }

        // Apply strafe movement
        Vec3d strafeMovement = strafeDir.multiply(strafeSpeed.getValue());

        // This would require movement handling in the movement module
        // For now, just as a concept
    }

    private void tryAttack() {
        if (target == null || mc.player == null || mc.interactionManager == null) return;

        long currentTime = System.currentTimeMillis();

        // Check attack delay
        long attackDelay = calculateDynamicAttackDelay();
        if (currentTime - lastAttackTime < attackDelay) return;

        // Check cooldown
        if (cooldownCheck.getValue()) {
            float cooldown = mc.player.getAttackCooldownProgress(0.0f);
            if (cooldown < minCooldown.getValue()) return;
        }

        // Check if we're facing the target
        if (faceTarget.getValue() && !isFacingTarget()) {
            return;
        }

        // Check range
        double distance = mc.player.distanceTo(target);
        if (distance > range.getValue()) return;

        // Stop blocking
        if (isBlocking) {
            stopBlocking();
        }

        // Critical hit setup (simplified version)
        if (criticals.getValue() && mc.player.isOnGround()) {
            // Simple jump for criticals without packets
            mc.player.jump();
        }

        // Sprint reset for better damage
        if (sprintReset.getValue() && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

        // Perform attack
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        lastAttackTime = currentTime;

        // Re-enable sprint after attack
        if (sprintReset.getValue()) {
            mc.player.setSprinting(true);
        }

        // Hit validation
        if (hitValidation.getValue()) {
            validateHit();
        }
    }

    private long calculateDynamicAttackDelay() {
        float targetCps = cps.getValue();

        if (randomCps.getValue()) {
            float randomness = cpsRandomness.getValue();
            targetCps += (random.nextFloat() - 0.5f) * randomness;
            targetCps = Math.max(1.0f, targetCps);
        }

        // Adjust CPS based on target state
        if (target != null) {
            // Faster CPS for low health targets
            if (target instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) target;
                if (living.getHealth() < 6.0f) {
                    targetCps *= 1.2f;
                }
            }

            // Slower CPS if we're missing a lot
            if (missCount > 3) {
                targetCps *= 0.8f;
            }
        }

        long delay = (long) (1000.0f / targetCps);
        return Math.max(30, delay); // Minimum 30ms delay
    }

    private void validateHit() {
        if (target == null || !(target instanceof LivingEntity)) return;

        LivingEntity living = (LivingEntity) target;
        float currentHealth = living.getHealth();

        // Check if we actually did damage
        if (Math.abs(currentHealth - lastTargetHealth) < 0.1f) {
            missCount++;
        } else {
            missCount = Math.max(0, missCount - 1);
            lastHitTime = System.currentTimeMillis();
        }

        lastTargetHealth = currentHealth;
    }

    private boolean isFacingTarget() {
        if (target == null || targetPoint == null) return false;

        float[] requiredRotations = calculateLookAt(targetPoint);
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDiff = Math.abs(MathHelper.wrapDegrees(requiredRotations[0] - currentYaw));
        float pitchDiff = Math.abs(requiredRotations[1] - currentPitch);

        return yawDiff <= faceThreshold.getValue() && pitchDiff <= faceThreshold.getValue();
    }

    // ... [Rest of the helper methods remain similar but optimized]

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mc.player || !entity.isAlive()) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        if (living.isDead() || living.getHealth() <= 0) return false;

        if (entity instanceof PlayerEntity player) {
            if (!targetPlayers.getValue()) return false;
        } else if (entity instanceof HostileEntity) {
            if (!targetHostiles.getValue()) return false;
        } else if (entity instanceof PassiveEntity) {
            if (!targetPassives.getValue()) return false;
        } else {
            return false;
        }

        return true;
    }

    private boolean canSeeTarget() {
        if (target == null || targetPoint == null) return false;
        return RayCastUtil.canSeePosition(targetPoint) ||
                RayCastUtil.canSeePosition(target.getEyePos()) ||
                RayCastUtil.canSeePosition(target.getBoundingBox().getCenter());
    }

    private float[] calculateLookAt(Vec3d pos) {
        if (mc.player == null || pos == null) return new float[]{0, 0};

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d diff = pos.subtract(eyePos);

        double distance = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, distance));

        return new float[]{
                MathHelper.wrapDegrees(yaw),
                MathHelper.clamp(pitch, -90.0f, 90.0f)
        };
    }

    private double getAngleToEntity(Entity entity) {
        if (mc.player == null) return Double.MAX_VALUE;

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = entity.getEyePos();
        Vec3d diff = targetPos.subtract(eyePos);

        double yaw = Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90;
        double pitch = -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));

        double yawDiff = Math.abs(MathHelper.wrapDegrees((float)(yaw - mc.player.getYaw())));
        double pitchDiff = Math.abs(pitch - mc.player.getPitch());

        return yawDiff + pitchDiff;
    }

    private void handleAutoBlock() {
        if (!canBlock()) {
            if (isBlocking) stopBlocking();
            return;
        }

        long timeSinceAttack = System.currentTimeMillis() - lastAttackTime;
        if (!isBlocking && timeSinceAttack > 80) {
            startBlocking();
        }
    }

    private boolean canBlock() {
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();

        return (!mainHand.isEmpty() && mainHand.getItem() instanceof ShieldItem);
    }

    private void startBlocking() {
        if (mc.player == null || isBlocking) return;
        mc.options.useKey.setPressed(true);
        isBlocking = true;
    }

    private void stopBlocking() {
        if (mc.player == null || !isBlocking) return;
        mc.options.useKey.setPressed(false);
        isBlocking = false;
    }

    public boolean hasTarget() {
        return target != null;
    }

    public Entity getCurrentTarget() {
        return target;
    }
}