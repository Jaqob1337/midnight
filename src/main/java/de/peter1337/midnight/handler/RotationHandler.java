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
 * Enhanced RotationHandler - Optimized for high-speed combat and movement
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

    // Previous rotation data
    private static float lastYawDelta = 0f;
    private static float lastPitchDelta = 0f;
    private static long lastFastRotationTime = 0L;
    private static boolean inFastRotationMode = false;

    // Smooth rotation parameters - CRITICAL CHANGES HERE FOR SPEED
    private static final float MIN_ROTATION_SPEED = 0.45f;          // Lowered to allow slower base speed
    private static final float QUICK_ROTATION_THRESHOLD = 0.85f;    // Lowered to trigger fast rotations more often
    private static final float MAX_INSTANT_YAW_CHANGE = 180f;       // Maximum possible change in one step
    private static final float MAX_INSTANT_PITCH_CHANGE = 90f;
    private static final float MAX_SMOOTH_YAW_CHANGE = 40f;         // DOUBLED for faster rotations
    private static final float MAX_SMOOTH_PITCH_CHANGE = 30f;       // DOUBLED for faster rotations

    // Human-like settings - Optimized for fast combat
    private static final float BASE_GCD = 0.001f;                   // Base GCD factor (increased for faster rotations)
    private static final float YAW_RANDOMIZATION = 0.30f;           // Reduced for more consistent targeting
    private static final float PITCH_RANDOMIZATION = 0.05f;         // Reduced for more accurate vertical tracking

    // Rotation timing
    private static int rotationCounter = 0;
    private static float lastYawChange = 0f;
    private static float lastPitchChange = 0f;
    private static long lastRotationTime = 0L;
    private static final int ROTATION_BREAK_THRESHOLD = 25;

    // Movement detection
    private static Vec3d lastPlayerPos = null;
    private static Vec3d playerVelocity = null;
    private static boolean isPlayerMovingFast = false;
    private static long lastPlayerPosUpdateTime = 0L;
    private static final double FAST_MOVEMENT_THRESHOLD = 3.8;      // Blocks per second

    private static String moveFixContext = "default";

    /**
     * Initializes the rotation handler.
     */
    public static void init() {
        Midnight.LOGGER.info("Enhanced RotationHandler initialized (High-Speed Optimized)");
        if (mc.player != null) {
            serverYaw = mc.player.getYaw();
            serverPitch = mc.player.getPitch();
            prevClientYaw = serverYaw;
            prevClientPitch = serverPitch;
            lastPlayerPos = mc.player.getPos();
        }
        lastRotationTime = System.currentTimeMillis();
        lastPlayerPosUpdateTime = System.currentTimeMillis();
    }

    /**
     * Updates rotations. Should be called every tick.
     */
    public static void onUpdate() {
        if (mc.player == null) return;

        // Update player movement tracking for speed-aware rotations
        updatePlayerMovementTracking();

        rotatingClient = false;
        if (!rotationInProgress) {
            prevClientYaw = mc.player.getYaw();
            prevClientPitch = mc.player.getPitch();
        }
        processRotations();
    }

    /**
     * Updates tracking of player movement to detect high-speed situations
     */
    private static void updatePlayerMovementTracking() {
        if (mc.player == null) return;

        long currentTime = System.currentTimeMillis();
        Vec3d currentPos = mc.player.getPos();

        if (lastPlayerPos != null) {
            // Calculate time delta in seconds
            float deltaTime = (currentTime - lastPlayerPosUpdateTime) / 1000.0f;
            if (deltaTime > 0 && deltaTime < 0.5f) { // Ignore long time gaps
                // Calculate player velocity
                Vec3d newVelocity = currentPos.subtract(lastPlayerPos).multiply(1.0f / deltaTime);

                if (playerVelocity == null) {
                    playerVelocity = newVelocity;
                } else {
                    // Smooth velocity updates with emphasis on new data (70% new, 30% old)
                    playerVelocity = playerVelocity.multiply(0.3).add(newVelocity.multiply(0.7));
                }

                // Check if player is moving fast (important for rotation speed adjustment)
                double playerSpeed = playerVelocity.horizontalLength();
                isPlayerMovingFast = playerSpeed > FAST_MOVEMENT_THRESHOLD;

                // Reset fast rotation mode after a delay
                if (inFastRotationMode && currentTime - lastFastRotationTime > 500) {
                    inFastRotationMode = false;
                }

                // If player is moving fast, activate fast rotation mode
                if (isPlayerMovingFast) {
                    inFastRotationMode = true;
                    lastFastRotationTime = currentTime;
                }
            }
        }

        lastPlayerPos = currentPos;
        lastPlayerPosUpdateTime = currentTime;
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
     * Process rotation requests based on priority with improved handling
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

            // Store current rotations when not rotating
            if (mc.player != null) {
                prevClientYaw = mc.player.getYaw();
                prevClientPitch = mc.player.getPitch();
            }
        }
    }

    /**
     * Apply a rotation request with improved smooth transitions and high-speed optimizations
     */
    private static void applyRotation(RotationRequest request) {
        rotationInProgress = true;
        moveFixEnabled = request.moveFix;

        float currentYaw = request.silent ? serverYaw : mc.player.getYaw();
        float currentPitch = request.silent ? serverPitch : mc.player.getPitch();

        float targetYaw = request.yaw;
        float targetPitch = request.pitch;

        // Determine effective speed based on movement and request parameters
        float effectiveSpeed;

        // If in fast rotation mode or player is moving fast, increase speed dramatically
        if (inFastRotationMode || isPlayerMovingFast) {
            effectiveSpeed = Math.max(0.92f, request.speed * 1.5f); // Much faster rotations
        } else {
            effectiveSpeed = Math.max(MIN_ROTATION_SPEED, request.speed);
        }

        // Check for large angle changes - need fast rotations
        float yawDelta = Math.abs(MathHelper.wrapDegrees(targetYaw - currentYaw));
        float pitchDelta = Math.abs(targetPitch - currentPitch);

        // If the target is moving quickly (large deltas), enter fast rotation mode
        if (yawDelta > 20.0f || pitchDelta > 15.0f) {
            inFastRotationMode = true;
            lastFastRotationTime = System.currentTimeMillis();
            // For large movements, force higher speed
            effectiveSpeed = Math.max(effectiveSpeed, 0.9f);
        }

        // Store rotation deltas for next calculation
        lastYawDelta = yawDelta;
        lastPitchDelta = pitchDelta;

        // Calculate new rotations based on determined speed
        float[] newRotations;
        if (effectiveSpeed >= QUICK_ROTATION_THRESHOLD) {
            newRotations = calculateInstantRotation(currentYaw, currentPitch, targetYaw, targetPitch);
        } else {
            newRotations = calculateSmoothRotation(currentYaw, currentPitch, targetYaw, targetPitch, effectiveSpeed);
        }

        // Apply human randomization (reduced when fast movement detected)
        float randomScale = Math.max(0.1f, 1.0f - effectiveSpeed);
        if (inFastRotationMode || isPlayerMovingFast) {
            randomScale *= 0.4f; // Even less randomness during fast rotations
        }

        newRotations[0] += (random.nextFloat() - 0.5f) * YAW_RANDOMIZATION * randomScale;
        newRotations[1] += (random.nextFloat() - 0.5f) * PITCH_RANDOMIZATION * randomScale;

        // Apply GCD with dynamic factor based on rotation speed and context
        float gcdFactor = calculateDynamicGcdFactor(MathHelper.wrapDegrees(newRotations[0] - currentYaw));

        // Apply GCD with importance based on rotation speed
        float rotatedYaw = applyGCD(newRotations[0], currentYaw, gcdFactor);
        float rotatedPitch = applyGCD(newRotations[1], currentPitch, gcdFactor);

        // Apply occasional humanization, but skip during fast rotations
        rotationCounter++;
        if (rotationCounter >= ROTATION_BREAK_THRESHOLD && !inFastRotationMode && !isPlayerMovingFast) {
            rotationCounter = 0;
            if (random.nextFloat() < 0.1f) { // Reduced probability
                try { Thread.sleep(random.nextInt(3) + 1); } // Reduced sleep time
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            if (random.nextFloat() < 0.15f) { // Reduced probability
                rotatedYaw += (random.nextFloat() - 0.5f) * 0.6f;  // Reduced jitter
                rotatedPitch += (random.nextFloat() - 0.5f) * 0.3f; // Reduced jitter
            }
        }

        // Clamp pitch
        rotatedPitch = MathHelper.clamp(rotatedPitch, -90.0f, 90.0f);

        if (request.silent) {
            serverYaw = rotatedYaw;
            serverPitch = rotatedPitch;
            rotatingClient = false;

            // Body rotation handling
            if (request.bodyOnly && mc.player != null) {
                bodyRotation = true;
                // Simply assign the calculated yaw to both body and head
                mc.player.bodyYaw = rotatedYaw;
                mc.player.headYaw = rotatedYaw;
            } else {
                bodyRotation = false;
            }
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
     * Calculate fast, near-instant rotation with improved responsiveness
     */
    private static float[] calculateInstantRotation(float currentYaw, float currentPitch,
                                                    float targetYaw, float targetPitch) {
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        // Much more aggressive factor for faster convergence (0.95 instead of 0.9)
        float yawChange = yawDiff * 0.95f;
        float pitchChange = pitchDiff * 0.95f;

        // Apply velocity-based scaling to rotation changes
        if (isPlayerMovingFast) {
            // Even more aggressive for fast movement
            yawChange = yawDiff * 0.98f;
            pitchChange = pitchDiff * 0.98f;
        }

        yawChange = MathHelper.clamp(yawChange, -MAX_INSTANT_YAW_CHANGE, MAX_INSTANT_YAW_CHANGE);
        pitchChange = MathHelper.clamp(pitchChange, -MAX_INSTANT_PITCH_CHANGE, MAX_INSTANT_PITCH_CHANGE);

        return new float[] {
                MathHelper.wrapDegrees(currentYaw + yawChange),
                MathHelper.clamp(currentPitch + pitchChange, -90.0f, 90.0f)
        };
    }

    /**
     * Calculate smooth rotation with faster, more responsive approach
     */
    private static float[] calculateSmoothRotation(float currentYaw, float currentPitch,
                                                   float targetYaw, float targetPitch,
                                                   float speed) {
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        // CRITICAL CHANGE: More responsive speed calculation - Linear instead of quadratic
        // This means lower speed values don't get penalized as much
        float effectiveSpeed = speed * 1.5f; // Linear scale with amplification

        // Further boost for larger angle changes
        if (Math.abs(yawDiff) > 30 || Math.abs(pitchDiff) > 20) {
            effectiveSpeed *= 1.5f; // Faster for bigger adjustments
        }

        // Faster convergence for rotations in progress
        if (Math.abs(lastYawChange) > 0.1f || Math.abs(lastPitchChange) > 0.1f) {
            effectiveSpeed *= 1.2f; // Keep momentum going
        }

        float yawChange = yawDiff * effectiveSpeed;
        float pitchChange = pitchDiff * effectiveSpeed;

        // Dynamically adjust limits based on speed and movement state
        float yawLimit, pitchLimit;

        if (isPlayerMovingFast || inFastRotationMode) {
            // Much higher limits during fast movement
            yawLimit = MAX_SMOOTH_YAW_CHANGE * 1.5f;
            pitchLimit = MAX_SMOOTH_PITCH_CHANGE * 1.5f;
        } else {
            yawLimit = MAX_SMOOTH_YAW_CHANGE * speed * 1.8f; // Increased multiplier
            pitchLimit = MAX_SMOOTH_PITCH_CHANGE * speed * 1.8f; // Increased multiplier
        }

        // Clamp to prevent too large jumps
        yawChange = MathHelper.clamp(yawChange, -yawLimit, yawLimit);
        pitchChange = MathHelper.clamp(pitchChange, -pitchLimit, pitchLimit);

        return new float[] {
                MathHelper.wrapDegrees(currentYaw + yawChange),
                MathHelper.clamp(currentPitch + pitchChange, -90.0f, 90.0f)
        };
    }

    /**
     * Calculates a dynamic GCD factor based on the context
     */
    private static float calculateDynamicGcdFactor(float delta) {
        float gcdFactor = BASE_GCD;

        // During high-speed movement, use minimal GCD to ensure responsive rotations
        if (inFastRotationMode || isPlayerMovingFast) {
            gcdFactor *= 0.25f; // Dramatically reduce GCD impact
            return Math.max(0.0001f, gcdFactor); // Ensure it's not zero
        }

        // Scaling based on delta size
        if (Math.abs(delta) < 1.0f) {
            gcdFactor = 0.03f; // More precise for small adjustments
        } else if (Math.abs(delta) > 30.0f) {
            // Allow faster rotation when delta is large
            gcdFactor *= 0.4f; // Reduced GCD impact for large rotations
        } else {
            gcdFactor *= Math.max(0.5f, Math.min(1.4f, Math.abs(delta) / 20.0f));
        }

        return Math.max(0.0001f, gcdFactor); // Ensure it's not zero
    }

    /**
     * Apply GCD with improved handling for fast movements
     */
    private static float applyGCD(float targetRotation, float currentRotation, float gcdFactor) {
        float delta = MathHelper.wrapDegrees(targetRotation - currentRotation);

        // If player is moving fast or in fast rotation mode, minimize GCD impact
        if (inFastRotationMode || isPlayerMovingFast) {
            // For very fast movement, practically bypass GCD
            if (Math.abs(delta) > 15.0f) {
                return MathHelper.wrapDegrees(currentRotation + delta * 0.95f);
            }
        }

        // If delta is very small but non-zero, ensure we still move
        float deltaGcd = delta - (delta % gcdFactor);
        if (Math.abs(deltaGcd) < 0.001f && Math.abs(delta) > 0.001f) {
            deltaGcd = Math.signum(delta) * gcdFactor;
        }

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
    public static boolean isPlayerMovingFast() { return isPlayerMovingFast; }
    public static boolean isInFastRotationMode() { return inFastRotationMode; }

    public static void resetRotations() {
        activeRequests.clear();
        rotationInProgress = false;
        rotatingClient = false;
        moveFixEnabled = false;
        usingMoveFix = false;
        bodyRotation = false;
        rotationCounter = 0;
        inFastRotationMode = false;
    }

    public static float[] calculateLookAt(Vec3d position) {
        if (mc.player == null) return new float[]{0f, 0f};
        Vec3d eyePos = mc.player.getEyePos();
        double diffX = position.x - eyePos.x;
        double diffY = position.y - eyePos.y;
        double diffZ = position.z - eyePos.z;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float pitch;
        if (dist < 0.1) pitch = (diffY > 0) ? 89.9f : -89.9f;
        else pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f;
        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(MathHelper.wrapDegrees(pitch), -90f, 90f);
        return new float[]{yaw, pitch};
    }

    public static float[] smoothRotation(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float speed) {
        if (speed >= QUICK_ROTATION_THRESHOLD || isPlayerMovingFast) {
            return calculateInstantRotation(currentYaw, currentPitch, targetYaw, targetPitch);
        } else {
            return calculateSmoothRotation(currentYaw, currentPitch, targetYaw, targetPitch, speed);
        }
    }

    public static void requestRotation(float yaw, float pitch, int priority, long durationMs, boolean silent, boolean bodyOnly, boolean moveFix, Consumer<RotationState> callback) {
        requestRotation(yaw, pitch, priority, durationMs, silent, bodyOnly, moveFix, 1.0f, callback);
    }

    public static void requestRotation(float yaw, float pitch, int priority, long durationMs, boolean silent, boolean bodyOnly, boolean moveFix, float speed, Consumer<RotationState> callback) {
        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(MathHelper.wrapDegrees(pitch), -90f, 90f);
        long expirationTime = System.currentTimeMillis() + durationMs;
        RotationRequest request = new RotationRequest(yaw, pitch, priority, expirationTime, silent, bodyOnly, moveFix, callback, speed);
        activeRequests.put(priority, request);
    }

    public static void setMoveFixContext(String context) {
        moveFixContext = context != null ? context : "default";
    }

    public static String getMoveFixContext() {
        return moveFixContext;
    }

    public static void requestRotation(float yaw, float pitch, int priority, long durationMs, boolean silent, boolean bodyOnly, Consumer<RotationState> callback) {
        requestRotation(yaw, pitch, priority, durationMs, silent, bodyOnly, false, 1.0f, callback);
    }

    public static void requestRotation(float yaw, float pitch, int priority, long durationMs, boolean silent, Consumer<RotationState> callback) {
        requestRotation(yaw, pitch, priority, durationMs, silent, false, false, 1.0f, callback);
    }

    public static void requestLookAt(Vec3d position, int priority, long durationMs, boolean silent, boolean moveFix, Consumer<RotationState> callback) {
        float[] rotations = calculateLookAt(position);
        requestRotation(rotations[0], rotations[1], priority, durationMs, silent, false, moveFix, 1.0f, callback);
    }

    public static void requestLookAt(Vec3d position, int priority, long durationMs, boolean silent, Consumer<RotationState> callback) {
        requestLookAt(position, priority, durationMs, silent, false, callback);
    }

    public static void requestFastLookAt(Vec3d position, int priority, long durationMs, boolean silent, Consumer<RotationState> callback) {
        float[] rotations = calculateLookAt(position);
        // Force high speed for fast look
        requestRotation(rotations[0], rotations[1], priority, durationMs, silent, false, false, 0.95f, callback);
    }

    public static void cancelRotationByPriority(int priority) {
        activeRequests.remove(priority);
        processRotations();
    }

    public static void requestLookAtEntity(Entity entity, boolean targetEyes, int priority, long durationMs, boolean silent, boolean moveFix, Consumer<RotationState> callback) {
        if (entity == null) return;

        // Reduced offset for more accuracy
        double offsetX = (random.nextDouble() - 0.5) * 0.05;
        double offsetY = (random.nextDouble() - 0.5) * 0.05;
        double offsetZ = (random.nextDouble() - 0.5) * 0.05;

        Vec3d pos;
        if (targetEyes) {
            pos = entity.getEyePos().add(offsetX, offsetY, offsetZ);
        } else {
            pos = entity.getBoundingBox().getCenter().add(offsetX, offsetY, offsetZ);
        }

        requestLookAt(pos, priority, durationMs, silent, moveFix, callback);
    }

    public static void requestLookAtEntity(Entity entity, boolean targetEyes, int priority, long durationMs, boolean silent, Consumer<RotationState> callback) {
        requestLookAtEntity(entity, targetEyes, priority, durationMs, silent, false, callback);
    }

    public static void requestSmoothLookAt(Vec3d position, float speed, int priority, long durationMs, boolean silent, boolean moveFix, Consumer<RotationState> callback) {
        float[] targetRotations = calculateLookAt(position);
        requestRotation(targetRotations[0], targetRotations[1], priority, durationMs, silent, false, moveFix, speed, callback);
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

    public static boolean isInFieldOfView(Vec3d position, float fov) {
        if (mc.player == null) return false;
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        Vec3d toTarget = position.subtract(mc.player.getEyePos()).normalize();
        if (Double.isNaN(toTarget.x) || Double.isNaN(toTarget.y) || Double.isNaN(toTarget.z)) return true;
        double dot = lookVec.dotProduct(toTarget);
        double angleDegrees = Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1.0, 1.0)));
        return angleDegrees <= fov / 2.0;
    }

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
            this.speed = Math.max(0.1f, Math.min(1.0f, speed)); // Increased minimum speed
        }
    }

    public static boolean shouldSkipModelRotations() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && mc.options != null && mc.options.getPerspective().isFirstPerson();
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