package de.peter1337.midnight.handler;

import de.peter1337.midnight.Midnight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages rotations for modules that need to control player's yaw and pitch.
 * Supports both client-side visible rotations and server-side silent rotations.
 */
public class RotationHandler {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

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
        }
    }

    /**
     * Apply a rotation request
     */
    private static void applyRotation(RotationRequest request) {
        // Track that we're in a rotation currently
        rotationInProgress = true;

        if (request.silent) {
            // For silent rotations, only update server-side rotations
            serverYaw = request.yaw;
            serverPitch = request.pitch;

            // For body rotation mode, update player's body and head yaw
            if (request.bodyOnly && mc.player != null) {
                bodyRotation = true;
                mc.player.bodyYaw = request.yaw;
                mc.player.headYaw = request.yaw;
            } else {
                bodyRotation = false;
            }
        } else {
            // For client-side rotations, update both
            rotatingClient = true;
            bodyRotation = false;
            serverYaw = request.yaw;
            serverPitch = request.pitch;

            // Apply to client visibly
            mc.player.setYaw(request.yaw);
            mc.player.setPitch(request.pitch);
        }

        // Call the callback if one was provided
        if (request.callback != null) {
            request.callback.accept(new RotationState(serverYaw, serverPitch, prevClientYaw, prevClientPitch));
        }
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
     * Reset any active rotations
     */
    public static void resetRotations() {
        activeRequests.clear();
        rotationInProgress = false;
        rotatingClient = false;
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

        // Apply speed factor with minimum rotation
        float yawChange = Math.max(Math.min(yawDiff * speed, 10f), -10f);
        float pitchChange = Math.max(Math.min(pitchDiff * speed, 10f), -10f);

        // Calculate new rotations
        float newYaw = currentYaw + yawChange;
        float newPitch = MathHelper.clamp(currentPitch + pitchChange, -90f, 90f);

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
     * @param callback   Optional callback when rotation is applied
     */
    public static void requestRotation(float yaw, float pitch, int priority, long durationMs,
                                       boolean silent, boolean bodyOnly, Consumer<RotationState> callback) {
        // Normalize rotation angles
        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(MathHelper.wrapDegrees(pitch), -90f, 90f);

        // Calculate expiration time
        long expirationTime = System.currentTimeMillis() + durationMs;

        // Create and add the request
        RotationRequest request = new RotationRequest(yaw, pitch, priority, expirationTime, silent, bodyOnly, callback);
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
     * @param callback   Optional callback when rotation is applied
     */
    public static void requestRotation(float yaw, float pitch, int priority, long durationMs,
                                       boolean silent, Consumer<RotationState> callback) {
        requestRotation(yaw, pitch, priority, durationMs, silent, false, callback);
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
        float[] rotations = calculateLookAt(position);
        requestRotation(rotations[0], rotations[1], priority, durationMs, silent, callback);
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
     * @param callback   Optional callback when rotation is applied
     */
    public static void requestLookAtEntity(Entity entity, boolean targetEyes, int priority, long durationMs,
                                           boolean silent, Consumer<RotationState> callback) {
        Vec3d pos;
        if (targetEyes) {
            // Target the entity's eyes
            pos = entity.getEyePos();
        } else {
            // Target the entity's center
            pos = entity.getPos().add(0, entity.getHeight() / 2, 0);
        }

        requestLookAt(pos, priority, durationMs, silent, callback);
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

        requestRotation(smoothRotations[0], smoothRotations[1], priority, durationMs, silent, callback);
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
        final Consumer<RotationState> callback;

        public RotationRequest(float yaw, float pitch, int priority, long expirationTime,
                               boolean silent, boolean bodyOnly, Consumer<RotationState> callback) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.priority = priority;
            this.expirationTime = expirationTime;
            this.silent = silent;
            this.bodyOnly = bodyOnly;
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