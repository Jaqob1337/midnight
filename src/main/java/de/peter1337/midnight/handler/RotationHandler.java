package de.peter1337.midnight.handler;

import de.peter1337.midnight.Midnight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Optimized RotationHandler for better Intave bypass and combat performance
 */
public class RotationHandler {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    // Active rotation requests
    private static final Map<Integer, RotationRequest> activeRequests = new ConcurrentHashMap<>();

    // Current rotations
    private static float serverYaw = 0f;
    private static float serverPitch = 0f;
    private static float prevClientYaw = 0f;
    private static float prevClientPitch = 0f;

    // Status flags
    private static boolean rotatingClient = false;
    private static boolean rotationInProgress = false;
    private static boolean bodyRotation = false;
    private static boolean moveFixEnabled = false;
    private static boolean usingMoveFix = false;

    // Bypass mechanics
    private static long lastRotationTime = 0L;
    private static float lastYawChange = 0f;
    private static float lastPitchChange = 0f;
    private static boolean inCombat = false;
    private static long combatStartTime = 0L;

    // Improved parameters for bypass
    private static final float MIN_ROTATION_SPEED = 0.7f;  // Faster minimum
    private static final float MAX_ROTATION_SPEED = 1.0f;  // Allow instant
    private static final float COMBAT_SPEED_MULTIPLIER = 1.4f;  // Faster in combat
    private static final float SNAP_ANGLE_THRESHOLD = 30.0f;  // Threshold for snap rotations

    // Humanization
    private static final float MAX_YAW_CHANGE = 45.0f;  // Increased for faster rotations
    private static final float MAX_PITCH_CHANGE = 35.0f;  // Increased for faster rotations
    private static final float MICRO_JITTER_CHANCE = 0.08f;  // 8% chance for micro movements
    private static final float MICRO_JITTER_AMOUNT = 0.4f;  // Amount of micro jitter

    // GCD bypass
    private static final float BASE_GCD = 0.05f;  // Higher base GCD for more natural movement
    private static final float COMBAT_GCD_REDUCTION = 0.7f;  // Reduce GCD in combat

    private static String moveFixContext = "default";

    public static void init() {
        Midnight.LOGGER.info("Enhanced RotationHandler initialized (Intave Optimized)");
        if (mc.player != null) {
            serverYaw = mc.player.getYaw();
            serverPitch = mc.player.getPitch();
            prevClientYaw = serverYaw;
            prevClientPitch = serverPitch;
        }
        lastRotationTime = System.currentTimeMillis();
    }

    public static void onUpdate() {
        if (mc.player == null) return;

        updateCombatState();

        rotatingClient = false;
        if (!rotationInProgress) {
            prevClientYaw = mc.player.getYaw();
            prevClientPitch = mc.player.getPitch();
        }

        processRotations();
    }

    private static void updateCombatState() {
        // Simple combat detection - can be improved
        boolean wasInCombat = inCombat;
        inCombat = activeRequests.size() > 0;

        if (inCombat && !wasInCombat) {
            combatStartTime = System.currentTimeMillis();
        }
    }

    private static void processRotations() {
        long currentTime = System.currentTimeMillis();
        activeRequests.entrySet().removeIf(entry -> currentTime >= entry.getValue().expirationTime);

        RotationRequest highestPriority = null;
        int highestPriorityValue = Integer.MIN_VALUE;
        for (RotationRequest request : activeRequests.values()) {
            if (request.priority > highestPriorityValue) {
                highestPriorityValue = request.priority;
                highestPriority = request;
            }
        }

        if (highestPriority != null) {
            applyRotation(highestPriority);
        } else {
            rotationInProgress = false;
            moveFixEnabled = false;
            usingMoveFix = false;
            bodyRotation = false;

            if (mc.player != null) {
                prevClientYaw = mc.player.getYaw();
                prevClientPitch = mc.player.getPitch();
            }
        }
    }

    private static void applyRotation(RotationRequest request) {
        rotationInProgress = true;
        moveFixEnabled = request.moveFix;

        float currentYaw = request.silent ? serverYaw : mc.player.getYaw();
        float currentPitch = request.silent ? serverPitch : mc.player.getPitch();

        float targetYaw = request.yaw;
        float targetPitch = request.pitch;

        // Calculate rotation differences
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        float totalAngleDiff = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);

        // Determine if we should use snap rotation
        boolean shouldSnap = totalAngleDiff > SNAP_ANGLE_THRESHOLD || request.speed >= 0.98f;

        // Apply combat speed bonus
        float effectiveSpeed = request.speed;
        if (inCombat) {
            effectiveSpeed = Math.min(1.0f, effectiveSpeed * COMBAT_SPEED_MULTIPLIER);
        }

        float[] newRotations;
        if (shouldSnap) {
            newRotations = calculateSnapRotation(currentYaw, currentPitch, targetYaw, targetPitch, effectiveSpeed);
        } else {
            newRotations = calculateSmoothRotation(currentYaw, currentPitch, targetYaw, targetPitch, effectiveSpeed);
        }

        // Apply humanization
        newRotations = applyHumanization(newRotations, currentYaw, currentPitch);

        // Apply GCD with combat considerations
        float gcdFactor = calculateGcdFactor();
        newRotations[0] = applyGCD(newRotations[0], currentYaw, gcdFactor);
        newRotations[1] = applyGCD(newRotations[1], currentPitch, gcdFactor * 0.8f); // Pitch GCD slightly lower

        // Clamp pitch
        newRotations[1] = MathHelper.clamp(newRotations[1], -90.0f, 90.0f);

        // Apply rotations
        if (request.silent) {
            serverYaw = newRotations[0];
            serverPitch = newRotations[1];
            rotatingClient = false;

            if (request.bodyOnly && mc.player != null) {
                bodyRotation = true;
                mc.player.bodyYaw = newRotations[0];
                mc.player.headYaw = newRotations[0];
            } else {
                bodyRotation = false;
            }
        } else {
            rotatingClient = true;
            bodyRotation = false;
            moveFixEnabled = false;
            serverYaw = newRotations[0];
            serverPitch = newRotations[1];
            if (mc.player != null) {
                mc.player.setYaw(newRotations[0]);
                mc.player.setPitch(newRotations[1]);
            }
        }

        // Store last changes for humanization
        lastYawChange = MathHelper.wrapDegrees(newRotations[0] - currentYaw);
        lastPitchChange = newRotations[1] - currentPitch;
        lastRotationTime = System.currentTimeMillis();

        if (request.callback != null) {
            request.callback.accept(new RotationState(serverYaw, serverPitch, prevClientYaw, prevClientPitch));
        }
    }

    private static float[] calculateSnapRotation(float currentYaw, float currentPitch,
                                                 float targetYaw, float targetPitch, float speed) {
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        // For snap rotations, use high percentage of the difference
        float snapFactor = Math.max(0.85f, speed);

        // Add slight randomization to snap to appear more human
        if (random.nextFloat() < 0.3f) {
            snapFactor *= (0.9f + random.nextFloat() * 0.15f); // 90-105% range
        }

        float yawChange = yawDiff * snapFactor;
        float pitchChange = pitchDiff * snapFactor;

        // Clamp to maximum changes
        yawChange = MathHelper.clamp(yawChange, -MAX_YAW_CHANGE, MAX_YAW_CHANGE);
        pitchChange = MathHelper.clamp(pitchChange, -MAX_PITCH_CHANGE, MAX_PITCH_CHANGE);

        return new float[]{
                MathHelper.wrapDegrees(currentYaw + yawChange),
                MathHelper.clamp(currentPitch + pitchChange, -90.0f, 90.0f)
        };
    }

    private static float[] calculateSmoothRotation(float currentYaw, float currentPitch,
                                                   float targetYaw, float targetPitch, float speed) {
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        // Enhanced speed calculation
        float effectiveSpeed = Math.max(MIN_ROTATION_SPEED, speed);

        // Increase speed for larger angles
        float angleMagnitude = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        if (angleMagnitude > 15.0f) {
            effectiveSpeed = Math.min(MAX_ROTATION_SPEED, effectiveSpeed * 1.3f);
        }

        // Apply speed with slight randomization
        float speedVariation = 1.0f + (random.nextFloat() - 0.5f) * 0.1f; // ±5% variation
        effectiveSpeed *= speedVariation;

        float yawChange = yawDiff * effectiveSpeed;
        float pitchChange = pitchDiff * effectiveSpeed;

        // Dynamic limits based on current speed and context
        float yawLimit = MAX_YAW_CHANGE * effectiveSpeed;
        float pitchLimit = MAX_PITCH_CHANGE * effectiveSpeed;

        yawChange = MathHelper.clamp(yawChange, -yawLimit, yawLimit);
        pitchChange = MathHelper.clamp(pitchChange, -pitchLimit, pitchLimit);

        return new float[]{
                MathHelper.wrapDegrees(currentYaw + yawChange),
                MathHelper.clamp(currentPitch + pitchChange, -90.0f, 90.0f)
        };
    }

    private static float[] applyHumanization(float[] rotations, float currentYaw, float currentPitch) {
        float newYaw = rotations[0];
        float newPitch = rotations[1];

        // Micro jitter for humanization
        if (random.nextFloat() < MICRO_JITTER_CHANCE) {
            newYaw += (random.nextFloat() - 0.5f) * MICRO_JITTER_AMOUNT;
            newPitch += (random.nextFloat() - 0.5f) * MICRO_JITTER_AMOUNT * 0.6f; // Less jitter on pitch
        }

        // Occasional small overshoots for realism
        if (random.nextFloat() < 0.12f) {
            float yawDiff = MathHelper.wrapDegrees(newYaw - currentYaw);
            float pitchDiff = newPitch - currentPitch;

            if (Math.abs(yawDiff) > 5.0f) {
                newYaw += Math.signum(yawDiff) * random.nextFloat() * 0.8f;
            }
            if (Math.abs(pitchDiff) > 3.0f) {
                newPitch += Math.signum(pitchDiff) * random.nextFloat() * 0.5f;
            }
        }

        return new float[]{newYaw, newPitch};
    }

    private static float calculateGcdFactor() {
        float gcdFactor = BASE_GCD;

        // Reduce GCD in combat for more responsive rotations
        if (inCombat) {
            long combatDuration = System.currentTimeMillis() - combatStartTime;
            if (combatDuration < 2000) { // First 2 seconds of combat
                gcdFactor *= COMBAT_GCD_REDUCTION;
            } else {
                gcdFactor *= 0.85f; // Slightly reduced GCD after initial period
            }
        }

        // Slightly randomize GCD for more natural feel
        gcdFactor *= (0.9f + random.nextFloat() * 0.2f); // 90-110% variation

        return Math.max(0.01f, gcdFactor);
    }

    private static float applyGCD(float targetRotation, float currentRotation, float gcdFactor) {
        float delta = MathHelper.wrapDegrees(targetRotation - currentRotation);

        // Skip GCD for very small movements
        if (Math.abs(delta) < 0.1f) {
            return targetRotation;
        }

        float deltaGcd = delta - (delta % gcdFactor);

        // Ensure we still move for small but non-zero deltas
        if (Math.abs(deltaGcd) < 0.01f && Math.abs(delta) > 0.01f) {
            deltaGcd = Math.signum(delta) * gcdFactor;
        }

        return MathHelper.wrapDegrees(currentRotation + deltaGcd);
    }

    // Public methods for requesting rotations
    public static void requestRotation(float yaw, float pitch, int priority, long durationMs,
                                       boolean silent, boolean bodyOnly, boolean moveFix,
                                       float speed, Consumer<RotationState> callback) {
        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(MathHelper.wrapDegrees(pitch), -90f, 90f);
        long expirationTime = System.currentTimeMillis() + durationMs;

        RotationRequest request = new RotationRequest(yaw, pitch, priority, expirationTime,
                silent, bodyOnly, moveFix, callback, speed);
        activeRequests.put(priority, request);
    }

    // Overloaded convenience methods
    public static void requestRotation(float yaw, float pitch, int priority, long durationMs,
                                       boolean silent, boolean bodyOnly, Consumer<RotationState> callback) {
        requestRotation(yaw, pitch, priority, durationMs, silent, bodyOnly, false, 0.8f, callback);
    }

    public static void requestRotation(float yaw, float pitch, int priority, long durationMs,
                                       boolean silent, Consumer<RotationState> callback) {
        requestRotation(yaw, pitch, priority, durationMs, silent, false, false, 0.8f, callback);
    }

    public static void requestLookAt(Vec3d position, int priority, long durationMs,
                                     boolean silent, boolean moveFix, Consumer<RotationState> callback) {
        float[] rotations = calculateLookAt(position);
        requestRotation(rotations[0], rotations[1], priority, durationMs, silent, false, moveFix, 0.8f, callback);
    }

    public static void requestLookAt(Vec3d position, int priority, long durationMs,
                                     boolean silent, Consumer<RotationState> callback) {
        requestLookAt(position, priority, durationMs, silent, false, callback);
    }

    public static void requestFastLookAt(Vec3d position, int priority, long durationMs,
                                         boolean silent, Consumer<RotationState> callback) {
        float[] rotations = calculateLookAt(position);
        requestRotation(rotations[0], rotations[1], priority, durationMs, silent, false, false, 0.95f, callback);
    }

    public static void requestSnapRotation(float yaw, float pitch, int priority, long durationMs,
                                           boolean silent, Consumer<RotationState> callback) {
        requestRotation(yaw, pitch, priority, durationMs, silent, false, false, 1.0f, callback);
    }

    public static void cancelRotationByPriority(int priority) {
        activeRequests.remove(priority);
        processRotations();
    }

    public static void resetRotations() {
        activeRequests.clear();
        rotationInProgress = false;
        rotatingClient = false;
        moveFixEnabled = false;
        usingMoveFix = false;
        bodyRotation = false;
        inCombat = false;
    }

    // Utility methods
    public static float[] calculateLookAt(Vec3d position) {
        if (mc.player == null) return new float[]{0f, 0f};

        Vec3d eyePos = mc.player.getEyePos();
        double diffX = position.x - eyePos.x;
        double diffY = position.y - eyePos.y;
        double diffZ = position.z - eyePos.z;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float pitch;
        if (dist < 0.1) {
            pitch = (diffY > 0) ? 89.9f : -89.9f;
        } else {
            pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));
        }

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f;
        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(MathHelper.wrapDegrees(pitch), -90f, 90f);

        return new float[]{yaw, pitch};
    }

    public static boolean isWithinRange(float targetYaw, float targetPitch, float maxDifference) {
        if (mc.player == null) return false;

        float currentYaw, currentPitch;
        if (isRotationActive() && !isRotatingClient()) {
            currentYaw = serverYaw;
            currentPitch = serverPitch;
        } else {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
        }

        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - currentYaw));
        float pitchDiff = Math.abs(targetPitch - currentPitch);

        return yawDiff <= maxDifference && pitchDiff <= maxDifference;
    }

    public static boolean isLookingAt(Vec3d position, float maxDifference) {
        float[] rotations = calculateLookAt(position);
        return isWithinRange(rotations[0], rotations[1], maxDifference);
    }

    // Getters
    public static float getClientYaw() { return prevClientYaw; }
    public static float getClientPitch() { return prevClientPitch; }
    public static float getServerYaw() { return serverYaw; }
    public static float getServerPitch() { return serverPitch; }
    public static boolean isRotatingClient() { return rotatingClient; }
    public static boolean isBodyRotation() { return bodyRotation; }
    public static boolean isRotationActive() { return rotationInProgress; }
    public static boolean isMoveFixEnabled() { return moveFixEnabled; }
    public static boolean isUsingMoveFix() { return usingMoveFix; }
    public static void setUsingMoveFix(boolean using) { usingMoveFix = using; }
    public static void setMoveFixContext(String context) { moveFixContext = context != null ? context : "default"; }
    public static String getMoveFixContext() { return moveFixContext; }

    // Internal classes
    private static class RotationRequest {
        final float yaw;
        final float pitch;
        final int priority;
        final long expirationTime;
        final boolean silent;
        final boolean bodyOnly;
        final boolean moveFix;
        final Consumer<RotationState> callback;
        final float speed;

        public RotationRequest(float yaw, float pitch, int priority, long expirationTime,
                               boolean silent, boolean bodyOnly, boolean moveFix,
                               Consumer<RotationState> callback, float speed) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.priority = priority;
            this.expirationTime = expirationTime;
            this.silent = silent;
            this.bodyOnly = bodyOnly;
            this.moveFix = moveFix;
            this.callback = callback;
            this.speed = Math.max(0.2f, Math.min(1.0f, speed));
        }
    }

    public static class RotationState {
        public final float serverYaw;
        public final float serverPitch;
        public final float clientYaw;
        public final float clientPitch;

        public RotationState(float serverYaw, float serverPitch, float clientYaw, float clientPitch) {
            this.serverYaw = serverYaw;
            this.serverPitch = serverPitch;
            this.clientYaw = clientYaw;
            this.clientPitch = clientPitch;
        }
    }
}