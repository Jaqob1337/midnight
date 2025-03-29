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
    private static final float MAX_INSTANT_YAW_CHANGE = 180f;  // Increased max change for faster rotations
    private static final float MAX_INSTANT_PITCH_CHANGE = 90f;
    private static final float MAX_SMOOTH_YAW_CHANGE = 20f;    // More reasonable value for smooth turns
    private static final float MAX_SMOOTH_PITCH_CHANGE = 15f;

    // Human-like settings
    private static final float GCD_BASE = 0.14f;       // GCD value that makes rotations appear human-like
    private static final float YAW_RANDOMIZATION = 0.15f;  // Small randomization for yaw
    private static final float PITCH_RANDOMIZATION = 0.08f; // Smaller randomization for pitch

    // Rotation timing
    private static int rotationCounter = 0;
    private static float lastYawChange = 0f;
    private static float lastPitchChange = 0f;
    private static long lastRotationTime = 0L;
    private static final int ROTATION_BREAK_THRESHOLD = 20; // Number of rotations before introducing variation

    /**
     * Initializes the rotation handler.
     */
    public static void init() {
        Midnight.LOGGER.info("Enhanced RotationHandler initialized");
        // Initial values based on player rotations
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

        // Reset rotation status
        rotatingClient = false;

        // Update previous rotations
        if (!rotationInProgress) {
            prevClientYaw = mc.player.getYaw();
            prevClientPitch = mc.player.getPitch();
        }

        // Process rotation requests by priority
        processRotations();
    }

    /**
     * Converts rotation angles to a direction vector
     *
     * @param pitch Pitch angle
     * @param yaw Yaw angle
     * @return Direction vector for the given angles
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
        // Remove expired rotations
        long currentTime = System.currentTimeMillis();
        activeRequests.entrySet().removeIf(entry -> currentTime >= entry.getValue().expirationTime);

        // Find the highest priority active rotation
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
            rotationCounter = 0;
        }
    }

    /**
     * Apply a rotation request with improved smooth transitions
     */
    private static void applyRotation(RotationRequest request) {
        // Track that we're in a rotation currently
        rotationInProgress = true;
        moveFixEnabled = request.moveFix;

        // Get current rotations to work with
        float currentYaw = request.silent ? serverYaw : mc.player.getYaw();
        float currentPitch = request.silent ? serverPitch : mc.player.getPitch();

        // Calculate goal rotations with GCD and human-like patterns
        float targetYaw = request.yaw;
        float targetPitch = request.pitch;

        // Determine the speed factor based on how quick we want this rotation
        float effectiveSpeed = Math.max(MIN_ROTATION_SPEED, request.speed);

        // Calculate rotation based on speed setting
        float[] newRotations;

        if (effectiveSpeed >= QUICK_ROTATION_THRESHOLD) {
            // Fast, almost instant rotation
            newRotations = calculateInstantRotation(currentYaw, currentPitch, targetYaw, targetPitch);
        } else {
            // Smooth rotation with speed scaling
            newRotations = calculateSmoothRotation(currentYaw, currentPitch, targetYaw, targetPitch, effectiveSpeed);
        }

        // Apply human randomization
        newRotations[0] += (random.nextFloat() - 0.5f) * YAW_RANDOMIZATION * Math.max(0.2f, 1.0f - effectiveSpeed);
        newRotations[1] += (random.nextFloat() - 0.5f) * PITCH_RANDOMIZATION * Math.max(0.2f, 1.0f - effectiveSpeed);

        // Apply GCD (Greatest Common Divisor) fix to make rotations look human
        float rotatedYaw = applyGCD(newRotations[0], currentYaw);
        float rotatedPitch = applyGCD(newRotations[1], currentPitch);

        // Increment counter for pattern avoidance
        rotationCounter++;

        // Every few rotations, add slight randomization to avoid patterns
        if (rotationCounter >= ROTATION_BREAK_THRESHOLD) {
            // Reset counter and add variation to break pattern
            rotationCounter = 0;

            // Add random delay before applying this rotation to break timing patterns
            if (random.nextFloat() < 0.2f) {
                try {
                    Thread.sleep(random.nextInt(10) + 1);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }

            // Add slight variation to rotation values
            if (random.nextFloat() < 0.3f) {
                rotatedYaw += (random.nextFloat() - 0.5f) * 1.2f;
                rotatedPitch += (random.nextFloat() - 0.5f) * 0.5f;
            }
        }

        if (request.silent) {
            // For silent rotations, only update server-side rotations
            serverYaw = rotatedYaw;
            serverPitch = rotatedPitch;

            // For body rotation mode, update player's body and head yaw
            if (request.bodyOnly && mc.player != null) {
                bodyRotation = true;
                mc.player.bodyYaw = rotatedYaw;
                mc.player.headYaw = rotatedYaw;
            } else {
                bodyRotation = false;
            }
        } else {
            // For client-side rotations, update both
            rotatingClient = true;
            bodyRotation = false;
            moveFixEnabled = false; // Move fix not needed for visible rotations
            serverYaw = rotatedYaw;
            serverPitch = rotatedPitch;

            // Apply to client visibly
            mc.player.setYaw(rotatedYaw);
            mc.player.setPitch(rotatedPitch);
        }

        // Store time and changes for next comparison
        lastYawChange = rotatedYaw - currentYaw;
        lastPitchChange = rotatedPitch - currentPitch;
        lastRotationTime = System.currentTimeMillis();

        // Call the callback if one was provided
        if (request.callback != null) {
            request.callback.accept(new RotationState(serverYaw, serverPitch, prevClientYaw, prevClientPitch));
        }
    }

    /**
     * Calculate fast, near-instant rotation that still looks natural
     */
    private static float[] calculateInstantRotation(float currentYaw, float currentPitch,
                                                    float targetYaw, float targetPitch) {
        // Calculate differences
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        // For instant rotations, we'll use up to 90% of the full difference
        // This gives the appearance of a very fast but still slightly smooth rotation
        float yawChange = yawDiff * 0.9f;
        float pitchChange = pitchDiff * 0.9f;

        // Apply limits to avoid obvious snap rotations
        yawChange = MathHelper.clamp(yawChange, -MAX_INSTANT_YAW_CHANGE, MAX_INSTANT_YAW_CHANGE);
        pitchChange = MathHelper.clamp(pitchChange, -MAX_INSTANT_PITCH_CHANGE, MAX_INSTANT_PITCH_CHANGE);

        return new float[] {
                MathHelper.wrapDegrees(currentYaw + yawChange),
                MathHelper.clamp(currentPitch + pitchChange, -90.0f, 90.0f)
        };
    }

    /**
     * Calculate smooth rotation with enhanced speed scaling
     */
    private static float[] calculateSmoothRotation(float currentYaw, float currentPitch,
                                                   float targetYaw, float targetPitch,
                                                   float speed) {
        // Calculate differences
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        // Apply non-linear acceleration for more natural camera movement
        // Square the speed to make the curve more dramatic at higher speeds
        float effectiveSpeed = speed * speed * 2.0f;

        // Apply speed factor with smooth acceleration and min/max limits
        float yawChange = yawDiff * effectiveSpeed;
        float pitchChange = pitchDiff * effectiveSpeed;

        // Set limits based on speed to prevent too large changes
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
     * Apply GCD (Greatest Common Divisor) to rotation values to make them more human-like
     * This simulates mouse movement increments seen in legitimate client rotations
     */
    private static float applyGCD(float targetRotation, float currentRotation) {
        float delta = MathHelper.wrapDegrees(targetRotation - currentRotation);

        // Calculate GCD factor based on rotation magnitude
        float gcdFactor = GCD_BASE;

        // For very small rotations, use smaller GCD factor to allow finer movements
        if (Math.abs(delta) < 1.0f) {
            gcdFactor = 0.05f;
        }
        // For regular rotations, scale GCD with movement size
        else {
            gcdFactor *= Math.max(1.0f, Math.abs(delta) / 10.0f);
        }

        // Apply GCD
        delta -= delta % gcdFactor;

        // For near-zero changes, ensure we make a minimal adjustment
        if (Math.abs(delta) < 0.001f && Math.abs(targetRotation - currentRotation) > 0.001f) {
            delta = Math.signum(targetRotation - currentRotation) * gcdFactor;
        }

        return MathHelper.wrapDegrees(currentRotation + delta);
    }

    // Accessor methods
    public static float getClientYaw() { return prevClientYaw; }
    public static float getClientPitch() { return prevClientPitch; }
    public static float getServerYaw() { return serverYaw; }
    public static float getServerPitch() { return serverPitch; }
    public static boolean isRotatingClient() { return rotatingClient; }
    public static boolean isBodyRotation() { return bodyRotation; }
    public static boolean isRotationActive() { return rotationInProgress; }
    public static boolean isMoveFixEnabled() { return moveFixEnabled; }

    /**
     * Set whether the MoveFix is currently being used
     */
    public static void setUsingMoveFix(boolean using) {
        usingMoveFix = using;
    }

    /**
     * Check if client is currently using MoveFix
     */
    public static boolean isUsingMoveFix() {
        return usingMoveFix;
    }

    /**
     * Reset any active rotations
     */
    public static void resetRotations() {
        activeRequests.clear();
        rotationInProgress = false;
        rotatingClient = false;
        moveFixEnabled = false;
        rotationCounter = 0;
    }

    /**
     * Calculates rotations to look at a specific position
     *
     * @param position Position to look at
     * @return array with [yaw, pitch]
     */
    public static float[] calculateLookAt(Vec3d position) {
        if (mc.player == null) return new float[]{0f, 0f};

        // Get player eye position
        Vec3d eyePos = mc.player.getEyePos();

        // Calculate differences
        double diffX = position.x - eyePos.x;
        double diffY = position.y - eyePos.y;
        double diffZ = position.z - eyePos.z;

        // Calculate distance in XZ plane
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        // Improved pitch calculation with special handling for very close targets
        float pitch;
        if (dist < 0.1) {
            // When almost directly above/below, use direct Y difference
            // and limit to a reasonable angle to prevent extreme pitch
            if (diffY > 0) {
                pitch = 80f; // Looking down
            } else {
                pitch = -80f; // Looking up
            }
        } else {
            // Normal pitch calculation
            pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));
        }

        // Calculate yaw
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f;

        // Normalize angles
        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(MathHelper.wrapDegrees(pitch), -90f, 90f);

        return new float[]{yaw, pitch};
    }

    /**
     * Smoothly interpolates between current rotation and target rotation
     * This method is an enhanced version specifically for external use when more control is needed
     *
     * @param currentYaw   Current yaw
     * @param currentPitch Current pitch
     * @param targetYaw    Target yaw
     * @param targetPitch  Target pitch
     * @param speed        Rotation speed factor (higher = faster)
     * @return Interpolated [yaw, pitch]
     */
    public static float[] smoothRotation(float currentYaw, float currentPitch,
                                         float targetYaw, float targetPitch,
                                         float speed) {
        // Use different implementations based on speed
        if (speed >= QUICK_ROTATION_THRESHOLD) {
            return calculateInstantRotation(currentYaw, currentPitch, targetYaw, targetPitch);
        } else {
            return calculateSmoothRotation(currentYaw, currentPitch, targetYaw, targetPitch, speed);
        }
    }

    /**
     * Request a rotation (will be applied based on priority)
     *
     * @param yaw        Target yaw angle
     * @param pitch      Target pitch angle
     * @param priority   Priority level (higher numbers take precedence)
     * @param durationMs How long to hold this rotation (in milliseconds)
     * @param silent     Whether the rotation should only be sent to the server (not visible to client)
     * @param bodyOnly   Whether to show rotation on player's body only (for 3rd person view)
     * @param moveFix    Whether to apply movement direction fix for silent rotations
     * @param callback   Optional callback when rotation is applied
     */
    public static void requestRotation(float yaw, float pitch, int priority, long durationMs,
                                       boolean silent, boolean bodyOnly, boolean moveFix, Consumer<RotationState> callback) {
        // Normalize rotation angles
        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(MathHelper.wrapDegrees(pitch), -90f, 90f);

        // Calculate expiration time
        long expirationTime = System.currentTimeMillis() + durationMs;

        // Add the request
        RotationRequest request = new RotationRequest(yaw, pitch, priority, expirationTime, silent, bodyOnly, moveFix, callback, 1.0f);
        activeRequests.put(priority, request);
    }

    /**
     * Request a rotation with specific speed control (will be applied based on priority)
     *
     * @param yaw        Target yaw angle
     * @param pitch      Target pitch angle
     * @param priority   Priority level (higher numbers take precedence)
     * @param durationMs How long to hold this rotation (in milliseconds)
     * @param silent     Whether the rotation should only be sent to the server (not visible to client)
     * @param bodyOnly   Whether to show rotation on player's body only (for 3rd person view)
     * @param moveFix    Whether to apply movement direction fix for silent rotations
     * @param speed      Speed factor (0.0-1.0) where 1.0 is fastest/instant
     * @param callback   Optional callback when rotation is applied
     */
    public static void requestRotation(float yaw, float pitch, int priority, long durationMs,
                                       boolean silent, boolean bodyOnly, boolean moveFix,
                                       float speed, Consumer<RotationState> callback) {
        // Normalize rotation angles
        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(MathHelper.wrapDegrees(pitch), -90f, 90f);

        // Calculate expiration time
        long expirationTime = System.currentTimeMillis() + durationMs;

        // Add the request
        RotationRequest request = new RotationRequest(yaw, pitch, priority, expirationTime,
                silent, bodyOnly, moveFix, callback, speed);
        activeRequests.put(priority, request);
    }

    /**
     * Convenience methods with fewer parameters
     */
    public static void requestRotation(float yaw, float pitch, int priority, long durationMs,
                                       boolean silent, boolean bodyOnly, Consumer<RotationState> callback) {
        requestRotation(yaw, pitch, priority, durationMs, silent, bodyOnly, false, callback);
    }

    public static void requestRotation(float yaw, float pitch, int priority, long durationMs,
                                       boolean silent, Consumer<RotationState> callback) {
        requestRotation(yaw, pitch, priority, durationMs, silent, false, false, callback);
    }

    /**
     * Request a rotation to look at a specific position
     */
    public static void requestLookAt(Vec3d position, int priority, long durationMs,
                                     boolean silent, boolean moveFix, Consumer<RotationState> callback) {
        float[] rotations = calculateLookAt(position);
        requestRotation(rotations[0], rotations[1], priority, durationMs, silent, false, moveFix, callback);
    }

    public static void requestLookAt(Vec3d position, int priority, long durationMs,
                                     boolean silent, Consumer<RotationState> callback) {
        requestLookAt(position, priority, durationMs, silent, false, callback);
    }

    /**
     * Request a fast rotation to look at a position
     */
    public static void requestFastLookAt(Vec3d position, int priority, long durationMs,
                                         boolean silent, Consumer<RotationState> callback) {
        float[] rotations = calculateLookAt(position);
        requestRotation(rotations[0], rotations[1], priority, durationMs, silent, false, false, 1.0f, callback);
    }

    /**
     * Cancel all rotations for a specific priority level
     */
    public static void cancelRotationByPriority(int priority) {
        activeRequests.remove(priority);
    }

    /**
     * Request a rotation to look at an entity (targeting its eyes or center)
     */
    public static void requestLookAtEntity(Entity entity, boolean targetEyes, int priority, long durationMs,
                                           boolean silent, boolean moveFix, Consumer<RotationState> callback) {
        // Add slight randomization to the target position for more human-like aiming
        double offsetX = (random.nextDouble() - 0.5) * 0.1;
        double offsetY = (random.nextDouble() - 0.5) * 0.1;
        double offsetZ = (random.nextDouble() - 0.5) * 0.1;

        Vec3d pos;
        if (targetEyes) {
            // Target the entity's eyes with slight offset
            pos = entity.getEyePos().add(offsetX, offsetY, offsetZ);
        } else {
            // Target the entity's center with slight offset
            pos = entity.getPos().add(offsetX, entity.getHeight() / 2 + offsetY, offsetZ);
        }

        requestLookAt(pos, priority, durationMs, silent, moveFix, callback);
    }

    public static void requestLookAtEntity(Entity entity, boolean targetEyes, int priority, long durationMs,
                                           boolean silent, Consumer<RotationState> callback) {
        requestLookAtEntity(entity, targetEyes, priority, durationMs, silent, false, callback);
    }

    /**
     * Request smooth rotation to look at a position with custom speed control
     */
    public static void requestSmoothLookAt(Vec3d position, float speed, int priority, long durationMs,
                                           boolean silent, boolean moveFix, Consumer<RotationState> callback) {
        float[] targetRotations = calculateLookAt(position);
        requestRotation(targetRotations[0], targetRotations[1], priority, durationMs,
                silent, false, moveFix, speed, callback);
    }

    /**
     * Check if the current rotation is within a certain angle of the target rotation
     */
    public static boolean isWithinRange(float targetYaw, float targetPitch, float maxDifference) {
        if (mc.player == null) return false;

        // Check if we're currently rotating - use server rotations in that case
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

    /**
     * Check if current rotation is looking at a specific position within a certain range
     */
    public static boolean isLookingAt(Vec3d position, float maxDifference) {
        float[] rotations = calculateLookAt(position);
        return isWithinRange(rotations[0], rotations[1], maxDifference);
    }

    /**
     * Check if the current rotation would be able to see the given position
     */
    public static boolean isInFieldOfView(Vec3d position, float fov) {
        if (mc.player == null) return false;

        // Get player's look vector
        Vec3d lookVec = mc.player.getRotationVec(1.0f);

        // Calculate direction to target
        Vec3d toTarget = position.subtract(mc.player.getEyePos()).normalize();

        // Calculate dot product (cosine of angle between vectors)
        double dot = lookVec.dotProduct(toTarget);

        // Convert to angle in degrees
        double angleDegrees = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));

        // Check if angle is within the specified FOV
        return angleDegrees <= fov / 2;
    }

    /**
     * Data class to hold rotation request information
     */
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
            this.speed = Math.max(0.05f, Math.min(1.0f, speed)); // Clamp speed to valid range
        }
    }

    /**
     * Determines if model rotations should be skipped
     * Used to prevent rotations in first-person view
     */
    public static boolean shouldSkipModelRotations() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && mc.options.getPerspective().isFirstPerson();
    }

    /**
     * Data class to hold current rotation state for callbacks
     */
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