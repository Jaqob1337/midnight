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
 * Enhanced RotationHandler - Manages player rotations with improved smoothing and speed
 * Supports both client-side visible rotations and server-side silent rotations.
 */
public class RotationHandler {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    // Thread-safe storage for active rotation requests
    private static final Map<Integer, RotationRequest> activeRequests = new ConcurrentHashMap<>();

    // Last sent rotation to the server
    private static float serverYaw = 0f;
    private static float serverPitch = 0f;

    // Previous client rotations (for restoring after silent rotations)
    private static float prevClientYaw = 0f;
    private static float prevClientPitch = 0f;

    // Status flags
    private static boolean rotatingClient = false;
    private static boolean rotationInProgress = false;
    private static boolean bodyRotation = false;
    private static boolean moveFixEnabled = false;
    private static boolean usingMoveFix = false;

    // Smooth rotation parameters
    private static final float MIN_ROTATION_SPEED = 0.1f;
    private static final float QUICK_ROTATION_THRESHOLD = 0.9f;
    private static final float MAX_INSTANT_YAW_CHANGE = 180f;
    private static final float MAX_INSTANT_PITCH_CHANGE = 90f;
    private static final float MAX_SMOOTH_YAW_CHANGE = 20f;
    private static final float MAX_SMOOTH_PITCH_CHANGE = 15f;

    // Human-like settings
    private static final float GCD_BASE = 0.14f;
    private static final float YAW_RANDOMIZATION = 0.18f;
    private static final float PITCH_RANDOMIZATION = 0.10f;

    // Rotation timing
    private static int rotationCounter = 0;
    private static float lastYawChange = 0f;
    private static float lastPitchChange = 0f;
    private static long lastRotationTime = 0L;
    private static final int ROTATION_BREAK_THRESHOLD = 20;

    private static String moveFixContext = "default";

    /**
     * Initializes the rotation handler.
     */
    public static void init() {
        Midnight.LOGGER.info("Enhanced RotationHandler initialized");
        if (mc.player != null) {
            serverYaw = mc.player.getYaw();
            serverPitch = mc.player.getPitch();
            prevClientYaw = serverYaw;
            prevClientPitch = serverPitch;
        }
        lastRotationTime = System.currentTimeMillis();
    }

    /**
     * Updates rotations. Should be called every tick.
     */
    public static void onUpdate() {
        if (mc.player == null) return;
        rotatingClient = false;
        if (!rotationInProgress) {
            prevClientYaw = mc.player.getYaw();
            prevClientPitch = mc.player.getPitch();
        }
        processRotations();
    }

    /**
     * Converts rotation angles to a direction vector
     */
    public static Vec3d getVectorForRotation(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float)Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float)Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3d(f1 * f2, f3, f * f2);
    }

    /**
     * Process rotation requests based on priority
     */
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
            rotationCounter = 0;
        }
    }

    /**
     * Apply a rotation request with improved smooth transitions
     */
    private static void applyRotation(RotationRequest request) {
        rotationInProgress = true;
        moveFixEnabled = request.moveFix;

        float currentYaw = request.silent ? serverYaw : mc.player.getYaw();
        float currentPitch = request.silent ? serverPitch : mc.player.getPitch();

        float targetYaw = request.yaw;
        float targetPitch = request.pitch;
        float effectiveSpeed = Math.max(MIN_ROTATION_SPEED, request.speed);

        float[] newRotations;
        if (effectiveSpeed >= QUICK_ROTATION_THRESHOLD) {
            newRotations = calculateInstantRotation(currentYaw, currentPitch, targetYaw, targetPitch);
        } else {
            newRotations = calculateSmoothRotation(currentYaw, currentPitch, targetYaw, targetPitch, effectiveSpeed);
        }

        // Apply human randomization BEFORE GCD
        float randomScale = Math.max(0.2f, 1.0f - effectiveSpeed);
        newRotations[0] += (random.nextFloat() - 0.5f) * YAW_RANDOMIZATION * randomScale;
        newRotations[1] += (random.nextFloat() - 0.5f) * PITCH_RANDOMIZATION * randomScale;

        // Apply GCD AFTER randomization
        float rotatedYaw = applyGCD(newRotations[0], currentYaw);
        float rotatedPitch = applyGCD(newRotations[1], currentPitch);

        rotationCounter++;
        if (rotationCounter >= ROTATION_BREAK_THRESHOLD) {
            rotationCounter = 0;
            if (random.nextFloat() < 0.2f) {
                try { Thread.sleep(random.nextInt(5) + 1); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            if (random.nextFloat() < 0.3f) {
                rotatedYaw += (random.nextFloat() - 0.5f) * 1.2f;
                rotatedPitch += (random.nextFloat() - 0.5f) * 0.5f;
            }
        }

        // Clamp pitch finally
        rotatedPitch = MathHelper.clamp(rotatedPitch, -90.0f, 90.0f);

        if (request.silent) {
            serverYaw = rotatedYaw;
            serverPitch = rotatedPitch;
            rotatingClient = false;

            // --- REVERTED PART ---
            // Set body and head yaw for bodyOnly mode as it was originally (or intended)
            if (request.bodyOnly && mc.player != null) {
                bodyRotation = true;
                // Simply assign the calculated yaw to both body and head
                mc.player.bodyYaw = rotatedYaw;
                mc.player.headYaw = rotatedYaw;
            } else {
                bodyRotation = false;
            }
            // --- END REVERTED PART ---

        } else { // Client rotation
            rotatingClient = true;
            bodyRotation = false;
            moveFixEnabled = false;
            serverYaw = rotatedYaw;
            serverPitch = rotatedPitch;
            if (mc.player != null) {
                mc.player.setYaw(rotatedYaw);
                mc.player.setPitch(rotatedPitch);
            }
        }

        lastYawChange = MathHelper.wrapDegrees(rotatedYaw - currentYaw);
        lastPitchChange = rotatedPitch - currentPitch;
        lastRotationTime = System.currentTimeMillis();

        if (request.callback != null) {
            request.callback.accept(new RotationState(serverYaw, serverPitch, prevClientYaw, prevClientPitch));
        }
    }

    /**
     * Calculate fast, near-instant rotation
     */
    private static float[] calculateInstantRotation(float currentYaw, float currentPitch,
                                                    float targetYaw, float targetPitch) {
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        float yawChange = yawDiff * 0.9f;
        float pitchChange = pitchDiff * 0.9f;
        yawChange = MathHelper.clamp(yawChange, -MAX_INSTANT_YAW_CHANGE, MAX_INSTANT_YAW_CHANGE);
        pitchChange = MathHelper.clamp(pitchChange, -MAX_INSTANT_PITCH_CHANGE, MAX_INSTANT_PITCH_CHANGE);
        return new float[] {
                MathHelper.wrapDegrees(currentYaw + yawChange),
                MathHelper.clamp(currentPitch + pitchChange, -90.0f, 90.0f)
        };
    }

    /**
     * Calculate smooth rotation
     */
    private static float[] calculateSmoothRotation(float currentYaw, float currentPitch,
                                                   float targetYaw, float targetPitch,
                                                   float speed) {
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        float effectiveSpeed = speed * speed * 2.0f;
        float yawChange = yawDiff * effectiveSpeed;
        float pitchChange = pitchDiff * effectiveSpeed;
        float yawLimit = MAX_SMOOTH_YAW_CHANGE * speed * 1.5f;
        float pitchLimit = MAX_SMOOTH_PITCH_CHANGE * speed * 1.5f;
        yawChange = MathHelper.clamp(yawChange, -yawLimit, yawLimit);
        pitchChange = MathHelper.clamp(pitchChange, -pitchLimit, pitchLimit);
        return new float[] {
                MathHelper.wrapDegrees(currentYaw + yawChange),
                MathHelper.clamp(currentPitch + pitchChange, -90.0f, 90.0f)
        };
    }

    /**
     * Apply GCD fix
     */
    private static float applyGCD(float targetRotation, float currentRotation) {
        float delta = MathHelper.wrapDegrees(targetRotation - currentRotation);
        float gcdFactor = GCD_BASE;
        if (Math.abs(delta) < 1.0f) gcdFactor = 0.05f;
        else gcdFactor *= Math.max(1.0f, Math.min(2.0f, Math.abs(delta) / 10.0f));
        float deltaGcd = delta - (delta % gcdFactor);
        if (Math.abs(deltaGcd) < 0.001f && Math.abs(delta) > 0.001f) deltaGcd = Math.signum(delta) * gcdFactor;
        return MathHelper.wrapDegrees(currentRotation + deltaGcd);
    }

    // --- Accessor methods and other public methods remain unchanged ---
    public static float getClientYaw() { return prevClientYaw; }
    public static float getClientPitch() { return prevClientPitch; }
    public static float getServerYaw() { return serverYaw; }
    public static float getServerPitch() { return serverPitch; }
    public static boolean isRotatingClient() { return rotatingClient; }
    public static boolean isBodyRotation() { return bodyRotation; }
    public static boolean isRotationActive() { return rotationInProgress; }
    public static boolean isMoveFixEnabled() { return moveFixEnabled; }
    public static void setUsingMoveFix(boolean using) { usingMoveFix = using; }
    public static boolean isUsingMoveFix() { return usingMoveFix; }
    public static void resetRotations() { activeRequests.clear(); rotationInProgress = false; rotatingClient = false; moveFixEnabled = false; usingMoveFix = false; bodyRotation = false; rotationCounter = 0; }
    public static float[] calculateLookAt(Vec3d position) { if (mc.player == null) return new float[]{0f, 0f}; Vec3d eyePos = mc.player.getEyePos(); double diffX = position.x - eyePos.x; double diffY = position.y - eyePos.y; double diffZ = position.z - eyePos.z; double dist = Math.sqrt(diffX * diffX + diffZ * diffZ); float pitch; if (dist < 0.1) pitch = (diffY > 0) ? 89.9f : -89.9f; else pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist)); float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f; yaw = MathHelper.wrapDegrees(yaw); pitch = MathHelper.clamp(MathHelper.wrapDegrees(pitch), -90f, 90f); return new float[]{yaw, pitch}; }
    public static float[] smoothRotation(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float speed) { if (speed >= QUICK_ROTATION_THRESHOLD) return calculateInstantRotation(currentYaw, currentPitch, targetYaw, targetPitch); else return calculateSmoothRotation(currentYaw, currentPitch, targetYaw, targetPitch, speed); }
    public static void requestRotation(float yaw, float pitch, int priority, long durationMs, boolean silent, boolean bodyOnly, boolean moveFix, Consumer<RotationState> callback) { requestRotation(yaw, pitch, priority, durationMs, silent, bodyOnly, moveFix, 1.0f, callback); }
    public static void requestRotation(float yaw, float pitch, int priority, long durationMs, boolean silent, boolean bodyOnly, boolean moveFix, float speed, Consumer<RotationState> callback) { yaw = MathHelper.wrapDegrees(yaw); pitch = MathHelper.clamp(MathHelper.wrapDegrees(pitch), -90f, 90f); long expirationTime = System.currentTimeMillis() + durationMs; RotationRequest request = new RotationRequest(yaw, pitch, priority, expirationTime, silent, bodyOnly, moveFix, callback, speed); activeRequests.put(priority, request); }
    public static void setMoveFixContext(String context) { moveFixContext = context != null ? context : "default"; }
    public static String getMoveFixContext() { return moveFixContext; }
    public static void requestRotation(float yaw, float pitch, int priority, long durationMs, boolean silent, boolean bodyOnly, Consumer<RotationState> callback) { requestRotation(yaw, pitch, priority, durationMs, silent, bodyOnly, false, 1.0f, callback); }
    public static void requestRotation(float yaw, float pitch, int priority, long durationMs, boolean silent, Consumer<RotationState> callback) { requestRotation(yaw, pitch, priority, durationMs, silent, false, false, 1.0f, callback); }
    public static void requestLookAt(Vec3d position, int priority, long durationMs, boolean silent, boolean moveFix, Consumer<RotationState> callback) { float[] rotations = calculateLookAt(position); requestRotation(rotations[0], rotations[1], priority, durationMs, silent, false, moveFix, 1.0f, callback); }
    public static void requestLookAt(Vec3d position, int priority, long durationMs, boolean silent, Consumer<RotationState> callback) { requestLookAt(position, priority, durationMs, silent, false, callback); }
    public static void requestFastLookAt(Vec3d position, int priority, long durationMs, boolean silent, Consumer<RotationState> callback) { float[] rotations = calculateLookAt(position); requestRotation(rotations[0], rotations[1], priority, durationMs, silent, false, false, 1.0f, callback); }
    public static void cancelRotationByPriority(int priority) { activeRequests.remove(priority); processRotations(); } // Re-process after cancelling
    public static void requestLookAtEntity(Entity entity, boolean targetEyes, int priority, long durationMs, boolean silent, boolean moveFix, Consumer<RotationState> callback) { if (entity == null) return; double offsetX = (random.nextDouble() - 0.5) * 0.1; double offsetY = (random.nextDouble() - 0.5) * 0.1; double offsetZ = (random.nextDouble() - 0.5) * 0.1; Vec3d pos; if (targetEyes) pos = entity.getEyePos().add(offsetX, offsetY, offsetZ); else pos = entity.getBoundingBox().getCenter().add(offsetX, offsetY, offsetZ); requestLookAt(pos, priority, durationMs, silent, moveFix, callback); }
    public static void requestLookAtEntity(Entity entity, boolean targetEyes, int priority, long durationMs, boolean silent, Consumer<RotationState> callback) { requestLookAtEntity(entity, targetEyes, priority, durationMs, silent, false, callback); }
    public static void requestSmoothLookAt(Vec3d position, float speed, int priority, long durationMs, boolean silent, boolean moveFix, Consumer<RotationState> callback) { float[] targetRotations = calculateLookAt(position); requestRotation(targetRotations[0], targetRotations[1], priority, durationMs, silent, false, moveFix, speed, callback); }
    public static boolean isWithinRange(float targetYaw, float targetPitch, float maxDifference) { if (mc.player == null) return false; float currentYaw, currentPitch; if (isRotationActive() && !isRotatingClient()) { currentYaw = serverYaw; currentPitch = serverPitch; } else { currentYaw = mc.player.getYaw(); currentPitch = mc.player.getPitch(); } float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - currentYaw)); float pitchDiff = Math.abs(targetPitch - currentPitch); return yawDiff <= maxDifference && pitchDiff <= maxDifference; }
    public static boolean isLookingAt(Vec3d position, float maxDifference) { float[] rotations = calculateLookAt(position); return isWithinRange(rotations[0], rotations[1], maxDifference); }
    public static boolean isInFieldOfView(Vec3d position, float fov) { if (mc.player == null) return false; Vec3d lookVec = mc.player.getRotationVec(1.0f); Vec3d toTarget = position.subtract(mc.player.getEyePos()).normalize(); if (Double.isNaN(toTarget.x) || Double.isNaN(toTarget.y) || Double.isNaN(toTarget.z)) return true; double dot = lookVec.dotProduct(toTarget); double angleDegrees = Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1.0, 1.0))); return angleDegrees <= fov / 2.0; }
    private static class RotationRequest { final float yaw; final float pitch; final int priority; final long expirationTime; final boolean silent; final boolean bodyOnly; final boolean moveFix; final Consumer<RotationState> callback; final float speed; public RotationRequest(float yaw, float pitch, int priority, long expirationTime, boolean silent, boolean bodyOnly, boolean moveFix, Consumer<RotationState> callback, float speed) { this.yaw = yaw; this.pitch = pitch; this.priority = priority; this.expirationTime = expirationTime; this.silent = silent; this.bodyOnly = bodyOnly; this.moveFix = moveFix; this.callback = callback; this.speed = Math.max(0.05f, Math.min(1.0f, speed)); } }
    public static boolean shouldSkipModelRotations() { MinecraftClient mc = MinecraftClient.getInstance(); return mc != null && mc.options != null && mc.options.getPerspective().isFirstPerson(); }
    public static class RotationState { public final float serverYaw; public final float serverPitch; public final float clientYaw; public final float clientPitch; public RotationState(float serverYaw, float serverPitch, float clientYaw, float clientPitch) { this.serverYaw = serverYaw; this.serverPitch = serverPitch; this.clientYaw = clientYaw; this.clientPitch = clientPitch; } }
}