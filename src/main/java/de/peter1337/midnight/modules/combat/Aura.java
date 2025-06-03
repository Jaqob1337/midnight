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

    // Current target
    private Entity target = null;
    private Vec3d targetPoint = null;
    private long lastAttackTime = 0;
    private boolean isBlocking = false;

    // Settings
    private final Setting<Float> range = register(new Setting<>("Range", 3.8f, 1.0f, 6.0f, "Attack range"));
    private final Setting<Float> cps = register(new Setting<>("CPS", 9.5f, 1.0f, 20.0f, "Clicks per second"));
    private final Setting<Boolean> randomCps = register(new Setting<>("RandomCPS", Boolean.TRUE, "Randomize CPS"));

    private final Setting<String> targetMode = register(new Setting<>("TargetMode", "Closest",
            List.of("Closest", "Health", "Angle"), "Target priority"));

    private final Setting<Boolean> targetPlayers = register(new Setting<>("TargetPlayers", Boolean.TRUE, "Target players"));
    private final Setting<Boolean> targetHostiles = register(new Setting<>("TargetHostiles", Boolean.TRUE, "Target hostile mobs"));
    private final Setting<Boolean> targetPassives = register(new Setting<>("TargetPassives", Boolean.FALSE, "Target passive mobs"));

    private final Setting<String> rotationMode = register(new Setting<>("RotationMode", "Silent",
            List.of("Silent", "Client", "Body"), "Rotation mode"));
    private final Setting<Float> rotationSpeed = register(new Setting<>("RotationSpeed", 0.85f, 0.1f, 1.0f, "Rotation speed"));
    private final Setting<Boolean> moveFix = register(new Setting<>("MoveFix", Boolean.TRUE, "Fix movement during rotations"));

    private final Setting<Boolean> throughWalls = register(new Setting<>("ThroughWalls", Boolean.FALSE, "Attack through walls"));
    private final Setting<Boolean> autoBlock = register(new Setting<>("AutoBlock", Boolean.FALSE, "Auto block with sword/shield"));
    private final Setting<Boolean> onlyWhenWeapon = register(new Setting<>("OnlyWhenWeapon", Boolean.TRUE, "Only attack when holding a weapon"));

    // Advanced settings
    private final Setting<Boolean> prediction = register(new Setting<>("Prediction", Boolean.TRUE, "Predict target movement"));
    private final Setting<Float> predictionStrength = register(new Setting<>("PredictionStrength", 0.3f, 0.0f, 1.0f, "Prediction strength").dependsOn(prediction));
    private final Setting<Boolean> sprintReset = register(new Setting<>("SprintReset", Boolean.TRUE, "Reset sprint for better hits"));
    private final Setting<Boolean> smartAttack = register(new Setting<>("SmartAttack", Boolean.TRUE, "Only attack when cooldown is ready"));

    // Target prediction
    private Vec3d lastTargetPos = null;
    private Vec3d targetVelocity = null;
    private long lastPosUpdateTime = 0;

    // Sprint management
    private boolean needsSprintReset = false;
    private int sprintResetTimer = 0;

    public Aura() {
        super("Aura", "Automatically attacks nearby entities", Category.COMBAT, "r");
    }

    @Override
    public void onEnable() {
        target = null;
        targetPoint = null;
        lastAttackTime = 0;
        isBlocking = false;
        needsSprintReset = false;
        sprintResetTimer = 0;

        // Reset prediction
        lastTargetPos = null;
        targetVelocity = null;
    }

    @Override
    public void onDisable() {
        target = null;
        targetPoint = null;

        // Cancel rotations
        RotationHandler.resetRotations();

        // Stop blocking
        if (isBlocking) {
            stopBlocking();
        }
    }

    /**
     * Pre-update method called by TickHandler before main game tick processing
     */
    public void preUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) {
            // Clean up if disabled
            if (isBlocking) stopBlocking();
            target = null;
            targetPoint = null;
            return;
        }

        // Main aura logic
        processAura();
    }

    @Override
    public void onUpdate() {
        // Main logic is handled in preUpdate(), but keep this as fallback
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Only run if preUpdate wasn't called
        processAura();
    }

    private void processAura() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Check if we should only attack with weapons
        if (onlyWhenWeapon.getValue() && !isHoldingWeapon()) {
            target = null;
            if (isBlocking) stopBlocking();
            return;
        }

        // Handle sprint reset
        if (sprintResetTimer > 0) {
            sprintResetTimer--;
            if (sprintResetTimer == 1) {
                mc.player.setSprinting(true); // Re-enable sprint
            }
            return; // Skip this tick
        }

        // Find target
        findTarget();

        if (target != null) {
            // Update target prediction
            if (prediction.getValue()) {
                updateTargetPrediction();
            }

            // Calculate target point
            targetPoint = calculateTargetPoint();

            // Check if we can see target (unless through walls is enabled)
            if (!throughWalls.getValue() && !canSeeTarget()) {
                target = null;
                if (isBlocking) stopBlocking();
                return;
            }

            // Handle rotations
            handleRotations();

            // Handle auto blocking
            if (autoBlock.getValue()) {
                handleAutoBlock();
            }

            // Try to attack
            tryAttack();
        } else {
            // No target - stop blocking and reset rotations
            if (isBlocking) {
                stopBlocking();
            }
            targetPoint = null;
        }
    }

    private boolean isHoldingWeapon() {
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandStack();
        return !mainHand.isEmpty() && (
                mainHand.getItem() instanceof SwordItem ||
                        mainHand.getItem().toString().toLowerCase().contains("axe") ||
                        mainHand.getItem().toString().toLowerCase().contains("sword")
        );
    }

    private void findTarget() {
        target = null;
        if (mc.player == null || mc.world == null) return;

        List<Entity> potentialTargets = new ArrayList<>();
        double rangeSquared = range.getValue() * range.getValue();

        // Find all valid targets
        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity)) continue;

            // Distance check
            double distance = mc.player.squaredDistanceTo(entity);
            if (distance > rangeSquared) continue;

            potentialTargets.add(entity);
        }

        if (potentialTargets.isEmpty()) return;

        // Sort targets based on mode
        switch (targetMode.getValue()) {
            case "Closest":
                potentialTargets.sort(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)));
                break;
            case "Health":
                potentialTargets.sort(Comparator.comparingDouble(e ->
                        e instanceof LivingEntity ? ((LivingEntity) e).getHealth() : Float.MAX_VALUE));
                break;
            case "Angle":
                potentialTargets.sort(Comparator.comparingDouble(this::getAngleToEntity));
                break;
        }

        target = potentialTargets.get(0);
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mc.player || !entity.isAlive()) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        if (living.isDead() || living.getHealth() <= 0) return false;

        // Type checks
        if (entity instanceof PlayerEntity player) {
            if (!targetPlayers.getValue()) return false;
            if (player.isSpectator() || player.isCreative()) return false;
            // Add team/friend checks here if needed
        } else if (entity instanceof HostileEntity) {
            if (!targetHostiles.getValue()) return false;
        } else if (entity instanceof PassiveEntity) {
            if (!targetPassives.getValue()) return false;
        } else {
            return false;
        }

        return true;
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

    private void updateTargetPrediction() {
        if (target == null) return;

        Vec3d currentPos = target.getPos();
        long currentTime = System.currentTimeMillis();

        if (lastTargetPos != null) {
            float deltaTime = (currentTime - lastPosUpdateTime) / 1000.0f;
            if (deltaTime > 0 && deltaTime < 0.5f) {
                Vec3d newVelocity = currentPos.subtract(lastTargetPos).multiply(1.0f / deltaTime);

                if (targetVelocity == null) {
                    targetVelocity = newVelocity;
                } else {
                    // Smooth velocity calculation
                    targetVelocity = targetVelocity.multiply(0.7).add(newVelocity.multiply(0.3));
                }
            }
        }

        lastTargetPos = currentPos;
        lastPosUpdateTime = currentTime;
    }

    private Vec3d calculateTargetPoint() {
        if (target == null) return null;

        Vec3d basePos = target.getEyePos();

        // Apply prediction if enabled
        if (prediction.getValue() && targetVelocity != null) {
            Vec3d predicted = targetVelocity.multiply(predictionStrength.getValue());
            basePos = basePos.add(predicted);
        }

        // Aim slightly lower for better hit registration
        if (target instanceof PlayerEntity) {
            basePos = basePos.subtract(0, 0.1, 0);
        }

        return basePos;
    }

    private boolean canSeeTarget() {
        if (target == null || targetPoint == null) return false;

        // Simple raycast check
        return RayCastUtil.canSeePosition(targetPoint) ||
                RayCastUtil.canSeePosition(target.getEyePos()) ||
                RayCastUtil.canSeePosition(target.getBoundingBox().getCenter());
    }

    private void handleRotations() {
        if (target == null || targetPoint == null) return;

        // Calculate rotations
        float[] rotations = calculateLookAt(targetPoint);

        // Add slight randomization
        if (random.nextFloat() < 0.1f) {
            rotations[0] += (random.nextFloat() - 0.5f) * 1.0f;
            rotations[1] += (random.nextFloat() - 0.5f) * 0.5f;
        }

        // Apply rotations
        boolean silent = rotationMode.getValue().equals("Silent");
        boolean bodyOnly = rotationMode.getValue().equals("Body");

        RotationHandler.requestRotation(
                rotations[0],
                rotations[1],
                100, // priority
                100, // duration
                silent,
                bodyOnly,
                moveFix.getValue(),
                rotationSpeed.getValue(),
                null
        );
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

    private void handleAutoBlock() {
        if (!canBlock()) {
            if (isBlocking) stopBlocking();
            return;
        }

        // Start blocking if we're not attacking soon
        long timeSinceAttack = System.currentTimeMillis() - lastAttackTime;
        if (!isBlocking && timeSinceAttack > 100) {
            startBlocking();
        }
    }

    private boolean canBlock() {
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();

        return (!mainHand.isEmpty() && (mainHand.getItem() instanceof SwordItem || mainHand.getItem() instanceof ShieldItem)) ||
                (!offHand.isEmpty() && offHand.getItem() instanceof ShieldItem);
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

    private void tryAttack() {
        if (target == null || mc.player == null || mc.interactionManager == null) return;

        // Check attack delay
        long currentTime = System.currentTimeMillis();
        long attackDelay = calculateAttackDelay();
        if (currentTime - lastAttackTime < attackDelay) return;

        // Check cooldown if smart attack is enabled
        if (smartAttack.getValue()) {
            float cooldown = mc.player.getAttackCooldownProgress(0.0f);
            if (cooldown < 0.9f) return;
        }

        // Check range
        double distance = mc.player.distanceTo(target);
        if (distance > range.getValue()) return;

        // Stop blocking before attacking
        if (isBlocking) {
            stopBlocking();
        }

        // Handle sprint reset
        if (sprintReset.getValue() && mc.player.isSprinting() && !needsSprintReset) {
            needsSprintReset = true;
            mc.player.setSprinting(false);
            sprintResetTimer = 3; // Reset for 3 ticks
            return;
        }

        // Perform attack
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        lastAttackTime = currentTime;
        needsSprintReset = false;
    }

    private long calculateAttackDelay() {
        float targetCps = cps.getValue();
        long baseDelay = (long) (1000.0f / targetCps);

        if (randomCps.getValue()) {
            // Add ±20% randomization
            float randomFactor = 0.8f + random.nextFloat() * 0.4f;
            baseDelay = (long) (baseDelay * randomFactor);
        }

        return Math.max(50, baseDelay); // Minimum 50ms delay
    }

    // Method for other modules to check if Aura has a target
    public boolean hasTarget() {
        return target != null;
    }

    public Entity getCurrentTarget() {
        return target;
    }
}