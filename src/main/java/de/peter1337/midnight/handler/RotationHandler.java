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

/**
 * Manages rotations for modules that need to control player's yaw and pitch.
 * Supports both client-side visible rotations and server-side silent rotations.
 */
public class RotationHandler {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    // Rotation priority system - higher priority will override lower ones
    private static final List<RotationRequest> activeRequests = new ArrayList<>();

    // Last sent rotation to the server
    private static float serverYaw = 0f;
    private static float serverPitch = 0f;

    // Previous client rotations (for restoring after silent rotations)
    private static float prevClientYaw = 0f;
    private static float prevClientPitch = 0f;

    // Current status flags
    private static boolean rotatingClient = false;
    private static boolean rotationInProgress = false;
    private static boolean bodyRotation = false;
    private static boolean moveFixEnabled = false;
    private static boolean usingMoveFix = false;

    // GCD and randomization settings
    private static final float GCD_MULTIPLIER = 0.1499f; // Slightly less than Minecraft's GCD value
    private static final float YAW_RANDOMIZATION = 0.2f;  // Small randomization for yaw
    private static final float PITCH_RANDOMIZATION = 0.1f; // Smaller randomization for pitch

    // Anti-pattern settings
    private static int rotationCounter = 0;
    private static float lastYawChange = 0f;
    private static float lastPitchChange = 0f;
    private static long lastRotationTime = 0L;
    private static final int ROTATION_BREAK_THRESHOLD = 15; // Number of rotations before introducing variation

    // NCP direction-specific avoidance
    private static final float MAX_YAW_CHANGE = 40f;     // Maximum degrees of yaw change per tick
    private static final float MAX_PITCH_CHANGE = 30f;   // Maximum degrees of pitch change per tick

    /**
     * Initializes the rotation handler. Should be called during client setup.
     */
    public static void init() {
        Midnight.LOGGER.info("RotationHandler initialized");
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
     * Process rotation requests based on priority
     */
    private static void processRotations() {
        // Clean up expired rotations
        activeRequests.removeIf(request -> System.currentTimeMillis() >= request.expirationTime);

        // Find the highest priority active rotation
        if (!activeRequests.isEmpty()) {
            RotationRequest highestPriority = activeRequests.stream()
                    .max((r1, r2) -> Integer.compare(r1.priority, r2.priority))
                    .orElse(null);

            if (highestPriority != null) {
                applyRotation(highestPriority);
            }
        } else {
            rotationInProgress = false;
            moveFixEnabled = false;
            rotationCounter = 0;
        }
    }

    /**
     * Apply a rotation request with improved anti-detection
     */
    private static void applyRotation(RotationRequest request) {
        // Track that we're in a rotation currently
        rotationInProgress = true;
        moveFixEnabled = request.moveFix;

        // Calculate NCP-friendly rotation values
        float[] safeRotation = getSafeRotation(
                request.silent ? serverYaw : mc.player.getYaw(),
                request.silent ? serverPitch : mc.player.getPitch(),
                request.yaw,
                request.pitch
        );

        // Increment counter for pattern avoidance
        rotationCounter++;

        // Every few rotations, add slight randomization to avoid patterns
        if (rotationCounter >= ROTATION_BREAK_THRESHOLD) {
            // Reset counter and add variation to break pattern
            rotationCounter = 0;

            // Add random delay before applying this rotation to break timing patterns
            if (random.nextFloat() < 0.3f) {
                try {
                    Thread.sleep(random.nextInt(20) + 5);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }

            // Add slight variation to rotation values
            if (random.nextFloat() < 0.4f) {
                safeRotation[0] += (random.nextFloat() - 0.5f) * 2.0f;
                safeRotation[1] += (random.nextFloat() - 0.5f) * 0.7f;
            }
        }

        // Store the rotation for server
        float serverYawNew = safeRotation[0];
        float serverPitchNew = safeRotation[1];

        // Apply GCD (Greatest Common Divisor) fix
        serverYawNew = applyGCD(serverYawNew, (request.silent ? serverYaw : mc.player.getYaw()));
        serverPitchNew = applyGCD(serverPitchNew, (request.silent ? serverPitch : mc.player.getPitch()));

        if (request.silent) {
            // For silent rotations, only update server-side rotations
            serverYaw = serverYawNew;
            serverPitch = serverPitchNew;

            // For body rotation mode, update player's body and head yaw
            if (request.bodyOnly && mc.player != null) {
                bodyRotation = true;
                mc.player.bodyYaw = serverYawNew;
                mc.player.headYaw = serverYawNew;
            } else {
                bodyRotation = false;
            }
        } else {
            // For client-side rotations, update both
            rotatingClient = true;
            bodyRotation = false;
            moveFixEnabled = false; // Move fix not needed for visible rotations
            serverYaw = serverYawNew;
            serverPitch = serverPitchNew;

            // Apply to client visibly
            mc.player.setYaw(serverYawNew);
            mc.player.setPitch(serverPitchNew);
        }

        // Store time and changes for next comparison
        lastYawChange = serverYaw - (request.silent ? serverYaw : mc.player.getYaw());
        lastPitchChange = serverPitch - (request.silent ? serverPitch : mc.player.getPitch());
        lastRotationTime = System.currentTimeMillis();

        // Call the callback if one was provided
        if (request.callback != null) {
            request.callback.accept(new RotationState(serverYaw, serverPitch, prevClientYaw, prevClientPitch));
        }
    }

    /**
     * Calculate a safe rotation to avoid fight.direction flags
     */
    private static float[] getSafeRotation(float currentYaw, float currentPitch, float targetYaw, float targetPitch) {
        // Calculate differences
        float yawDifference = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDifference = targetPitch - currentPitch;

        // Limit change rates to avoid NCP direction flags
        float safeYawChange = Math.signum(yawDifference) * Math.min(Math.abs(yawDifference), MAX_YAW_CHANGE);
        float safePitchChange = Math.signum(pitchDifference) * Math.min(Math.abs(pitchDifference), MAX_PITCH_CHANGE);

        // Calculate new rotation values
        float newYaw = currentYaw + safeYawChange;
        float newPitch = MathHelper.clamp(currentPitch + safePitchChange, -90.0f, 90.0f);

        // Randomize slightly to avoid patterns
        newYaw += (random.nextFloat() - 0.5f) * YAW_RANDOMIZATION;
        newPitch += (random.nextFloat() - 0.5f) * PITCH_RANDOMIZATION;

        return new float[]{newYaw, newPitch};
    }

    /**
     * Apply the GCD fix to rotation values to make them more human-like
     */
    private static float applyGCD(float targetRotation, float currentRotation) {
        float delta = MathHelper.wrapDegrees(targetRotation - currentRotation);
        delta -= delta % (GCD_MULTIPLIER * Math.max(1.0f, Math.abs(delta) / 8.0f));
        return MathHelper.wrapDegrees(currentRotation + delta);
    }

    /**
     * Get the client-side yaw (the original camera rotation)
     */
    public static float getClientYaw() {
        return prevClientYaw;
    }

    /**
     * Get the client-side pitch (the original camera rotation)
     */
    public static float getClientPitch() {
        return prevClientPitch;
    }

    /**
     * Get the server-side yaw (the rotation the server thinks we have)
     */
    public static float getServerYaw() {
        return serverYaw;
    }

    /**
     * Get the server-side pitch (the rotation the server thinks we have)
     */
    public static float getServerPitch() {
        return serverPitch;
    }

    /**
     * Check if we're currently applying a client-visible rotation
     */
    public static boolean isRotatingClient() {
        return rotatingClient;
    }

    /**
     * Check if we're currently in body-only rotation mode
     */
    public static boolean isBodyRotation() {
        return bodyRotation;
    }

    /**
     * Check if any rotation is currently active (silent or visible)
     */
    public static boolean isRotationActive() {
        return rotationInProgress;
    }

    /**
     * Check if movement fix is currently enabled
     */
    public static boolean isMoveFixEnabled() {
        return moveFixEnabled;
    }

    /**
     * Set whether the MoveFix is currently being used
     * This is called from the RotationMixin to coordinate with rendering
     */
    public static void setUsingMoveFix(boolean using) {
        usingMoveFix = using;
    }

    /**
     * Check if client is currently using MoveFix
     * This helps coordinate with rendering systems
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

        // Calculate difference
        double diffX = position.x - eyePos.x;
        double diffY = position.y - eyePos.y;
        double diffZ = position.z - eyePos.z;

        // Calculate distance in XZ plane
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        // Calculate yaw and pitch
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));

        // Randomize slightly for more human-like targeting
        float accuracy = 0.25f;  // Lower value means more accurate
        yaw += (random.nextFloat() - 0.5f) * accuracy;
        pitch += (random.nextFloat() - 0.5f) * accuracy;

        // Normalize angles
        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(MathHelper.wrapDegrees(pitch), -90f, 90f);

        return new float[]{yaw, pitch};
    }

    /**
     * Smoothly interpolates between current rotation and target rotation
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
        // Calculate angle differences
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        // Randomize speed slightly for human-like movement
        float randomizedSpeed = speed * (0.95f + random.nextFloat() * 0.1f);

        // Apply speed factor with minimum and maximum rotation constraints
        float maxChange = Math.min(10f, MAX_YAW_CHANGE / 2);
        float yawChange = Math.max(Math.min(yawDiff * randomizedSpeed, maxChange), -maxChange);
        float pitchChange = Math.max(Math.min(pitchDiff * randomizedSpeed, maxChange), -maxChange);

        // Calculate new rotations
        float newYaw = currentYaw + yawChange;
        float newPitch = MathHelper.clamp(currentPitch + pitchChange, -90f, 90f);

        // Apply GCD to make it look human
        newYaw = applyGCD(newYaw, currentYaw);
        newPitch = applyGCD(newPitch, currentPitch);

        return new float[]{newYaw, newPitch};
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

        // Create and add the request
        RotationRequest request = new RotationRequest(yaw, pitch, priority, expirationTime, silent, bodyOnly, moveFix, callback);
        activeRequests.add(request);
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
     * @param callback   Optional callback when rotation is applied
     */
    public static void requestRotation(float yaw, float pitch, int priority, long durationMs,
                                       boolean silent, boolean bodyOnly, Consumer<RotationState> callback) {
        requestRotation(yaw, pitch, priority, durationMs, silent, bodyOnly, false, callback);
    }

    /**
     * Request a rotation (will be applied based on priority)
     *
     * @param yaw        Target yaw angle
     * @param pitch      Target pitch angle
     * @param priority   Priority level (higher numbers take precedence)
     * @param durationMs How long to hold this rotation (in milliseconds)
     * @param silent     Whether the rotation should only be sent to the server (not visible to client)
     * @param callback   Optional callback when rotation is applied
     */
    public static void requestRotation(float yaw, float pitch, int priority, long durationMs,
                                       boolean silent, Consumer<RotationState> callback) {
        requestRotation(yaw, pitch, priority, durationMs, silent, false, false, callback);
    }

    /**
     * Request a rotation to look at a specific position
     *
     * @param position   Position to look at
     * @param priority   Priority level (higher numbers take precedence)
     * @param durationMs How long to hold this rotation (in milliseconds)
     * @param silent     Whether the rotation should only be sent to the server (not visible to client)
     * @param moveFix    Whether to apply movement direction fix for silent rotations
     * @param callback   Optional callback when rotation is applied
     */
    public static void requestLookAt(Vec3d position, int priority, long durationMs,
                                     boolean silent, boolean moveFix, Consumer<RotationState> callback) {
        float[] rotations = calculateLookAt(position);
        requestRotation(rotations[0], rotations[1], priority, durationMs, silent, false, moveFix, callback);
    }

    /**
     * Request a rotation to look at a specific position
     *
     * @param position   Position to look at
     * @param priority   Priority level (higher numbers take precedence)
     * @param durationMs How long to hold this rotation (in milliseconds)
     * @param silent     Whether the rotation should only be sent to the server (not visible to client)
     * @param callback   Optional callback when rotation is applied
     */
    public static void requestLookAt(Vec3d position, int priority, long durationMs,
                                     boolean silent, Consumer<RotationState> callback) {
        requestLookAt(position, priority, durationMs, silent, false, callback);
    }

    /**
     * Cancel all rotations for a specific priority level
     */
    public static void cancelRotationByPriority(int priority) {
        activeRequests.removeIf(request -> request.priority == priority);
    }

    /**
     * Request a rotation to look at an entity (targeting its eyes or center)
     *
     * @param entity     Entity to look at
     * @param targetEyes Whether to target eyes (true) or center (false)
     * @param priority   Priority level (higher numbers take precedence)
     * @param durationMs How long to hold this rotation (in milliseconds)
     * @param silent     Whether the rotation should only be sent to the server (not visible to client)
     * @param moveFix    Whether to apply movement direction fix for silent rotations
     * @param callback   Optional callback when rotation is applied
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

    /**
     * Request a rotation to look at an entity (targeting its eyes or center)
     *
     * @param entity     Entity to look at
     * @param targetEyes Whether to target eyes (true) or center (false)
     * @param priority   Priority level (higher numbers take precedence)
     * @param durationMs How long to hold this rotation (in milliseconds)
     * @param silent     Whether the rotation should only be sent to the server (not visible to client)
     * @param callback   Optional callback when rotation is applied
     */
    public static void requestLookAtEntity(Entity entity, boolean targetEyes, int priority, long durationMs,
                                           boolean silent, Consumer<RotationState> callback) {
        requestLookAtEntity(entity, targetEyes, priority, durationMs, silent, false, callback);
    }

    /**
     * Request smooth rotation to look at a position
     *
     * @param position   Position to look at
     * @param speed      Rotation speed factor (higher = faster)
     * @param priority   Priority level (higher numbers take precedence)
     * @param durationMs How long to hold this rotation (in milliseconds)
     * @param silent     Whether the rotation should only be sent to the server (not visible to client)
     * @param moveFix    Whether to apply movement direction fix for silent rotations
     * @param callback   Optional callback when rotation is applied
     */
    public static void requestSmoothLookAt(Vec3d position, float speed, int priority, long durationMs,
                                           boolean silent, boolean moveFix, Consumer<RotationState> callback) {
        float[] targetRotations = calculateLookAt(position);

        float[] currentRotations;
        if (silent) {
            // For silent rotations, use server rotation as starting point
            currentRotations = new float[]{serverYaw, serverPitch};
        } else {
            // For visible rotations, use client rotation as starting point
            currentRotations = new float[]{
                    mc.player != null ? mc.player.getYaw() : 0f,
                    mc.player != null ? mc.player.getPitch() : 0f
            };
        }

        float[] smoothRotations = smoothRotation(
                currentRotations[0], currentRotations[1],
                targetRotations[0], targetRotations[1],
                speed
        );

        requestRotation(smoothRotations[0], smoothRotations[1], priority, durationMs, silent, false, moveFix, callback);
    }

    /**
     * Request smooth rotation to look at a position
     *
     * @param position   Position to look at
     * @param speed      Rotation speed factor (higher = faster)
     * @param priority   Priority level (higher numbers take precedence)
     * @param durationMs How long to hold this rotation (in milliseconds)
     * @param silent     Whether the rotation should only be sent to the server (not visible to client)
     * @param callback   Optional callback when rotation is applied
     */
    public static void requestSmoothLookAt(Vec3d position, float speed, int priority, long durationMs,
                                           boolean silent, Consumer<RotationState> callback) {
        requestSmoothLookAt(position, speed, priority, durationMs, silent, false, callback);
    }

    /**
     * Check if the current rotation is within a certain angle of the target rotation
     *
     * @param targetYaw     Target yaw angle
     * @param targetPitch   Target pitch angle
     * @param maxDifference Maximum allowed difference in degrees
     * @return Whether current rotation is within range
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
     *
     * @param position      Position to check
     * @param maxDifference Maximum allowed difference in degrees
     * @return Whether current rotation is looking at the position
     */
    public static boolean isLookingAt(Vec3d position, float maxDifference) {
        float[] rotations = calculateLookAt(position);
        return isWithinRange(rotations[0], rotations[1], maxDifference);
    }

    /**
     * Check if the current rotation would be able to see the given position
     *
     * @param position Position to check
     * @param fov      Field of view in degrees
     * @return Whether the position is within the given field of view
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

        public RotationRequest(float yaw, float pitch, int priority, long expirationTime,
                               boolean silent, boolean bodyOnly, boolean moveFix, Consumer<RotationState> callback) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.priority = priority;
            this.expirationTime = expirationTime;
            this.silent = silent;
            this.bodyOnly = bodyOnly;
            this.moveFix = moveFix;
            this.callback = callback;
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