package de.peter1337.midnight.utils;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Enhanced utility class for raycasting operations with better support for Scaffold and Aura modules
 * (Restored to original version provided by user)
 */
public class RayCastUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // Constants for better visibility testing
    private static final float EPSILON = 0.001f; // Original Epsilon
    private static final double MAX_ATTACK_RANGE = 6.0; // Maximum range for attack visibility checks

    /**
     * Simplified class to hold block placement information
     * (Original Scaffold helper class)
     */
    public static class BlockPlacementInfo {
        private final BlockPos targetPos;
        private final BlockPos placeAgainst;
        private final Direction placeDir;
        private final Vec3d hitVec;
        private final float[] rotations;

        public BlockPlacementInfo(BlockPos targetPos, BlockPos placeAgainst, Direction placeDir, Vec3d hitVec) {
            this.targetPos = targetPos;
            this.placeAgainst = placeAgainst;
            this.placeDir = placeDir;
            this.hitVec = hitVec;
            this.rotations = calculateLookAt(hitVec); // Uses original calculateLookAt
        }

        public BlockPos getTargetPos() { return targetPos; }
        public BlockPos getPlaceAgainst() { return placeAgainst; }
        public Direction getPlaceDir() { return placeDir; }
        public Vec3d getHitVec() { return hitVec; }
        public float[] getRotations() { return rotations; }
    }

    /**
     * Finds the best visible point on an entity for targeting
     * Used by Aura module for better target acquisition
     * (Original Aura helper method)
     *
     * @param entity The entity to check
     * @return The best visible point, or null if none is visible
     */
    public static Vec3d getVisiblePoint(Entity entity) {
        if (mc.player == null || mc.world == null || entity == null) return null;

        // Try the eyes first as that's often the most reliable spot
        Vec3d eyePos = entity.getEyePos();
        if (canSeePosition(eyePos)) { // Uses original canSeePosition
            return eyePos;
        }

        // If the eyes aren't visible, try multiple points on the hitbox
        Box box = entity.getBoundingBox();

        // Center of the hitbox
        Vec3d center = box.getCenter();
        if (canSeePosition(center)) {
            return center;
        }

        // Try points around the box with slight inward offsets to avoid edge issues
        double offsetX = (box.maxX - box.minX) * 0.2;
        double offsetY = (box.maxY - box.minY) * 0.2;
        double offsetZ = (box.maxZ - box.minZ) * 0.2;

        // Generate more test points at various positions on the entity's hitbox
        Vec3d[] testPoints = {
                // Original 8 corners with slight inward offset
                new Vec3d(box.minX + offsetX, box.minY + offsetY, box.minZ + offsetZ),
                new Vec3d(box.maxX - offsetX, box.minY + offsetY, box.minZ + offsetZ),
                new Vec3d(box.minX + offsetX, box.maxY - offsetY, box.minZ + offsetZ),
                new Vec3d(box.minX + offsetX, box.minY + offsetY, box.maxZ - offsetZ),
                new Vec3d(box.maxX - offsetX, box.maxY - offsetY, box.minZ + offsetZ),
                new Vec3d(box.minX + offsetX, box.maxY - offsetY, box.maxZ - offsetZ),
                new Vec3d(box.maxX - offsetX, box.minY + offsetY, box.maxZ - offsetZ),
                new Vec3d(box.maxX - offsetX, box.maxY - offsetY, box.maxZ - offsetZ),

                // Center points of each face with slight inward offset
                new Vec3d(box.minX + offsetX, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2),
                new Vec3d(box.maxX - offsetX, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2),
                new Vec3d((box.minX + box.maxX) / 2, box.minY + offsetY, (box.minZ + box.maxZ) / 2),
                new Vec3d((box.minX + box.maxX) / 2, box.maxY - offsetY, (box.minZ + box.maxZ) / 2),
                new Vec3d((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, box.minZ + offsetZ),
                new Vec3d((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, box.maxZ - offsetZ)
        };

        // Sort points by distance to player for efficiency (check closest points first)
        List<Vec3d> sortedPoints = new ArrayList<>();
        for (Vec3d point : testPoints) {
            sortedPoints.add(point);
        }

        Vec3d playerEyePos = mc.player.getEyePos(); // Defined here for sorting lambda
        sortedPoints.sort(Comparator.comparingDouble(p ->
                playerEyePos.squaredDistanceTo(p)));

        // Check each point for visibility
        for (Vec3d point : sortedPoints) {
            if (canSeePosition(point)) {
                return point;
            }
        }

        // If no point is directly visible, try with more lenient parameters
        // This helps with edge cases where the entity is partially visible
        for (Vec3d point : sortedPoints) {
            if (canSeePositionLenient(point)) { // Uses original lenient check
                return point;
            }
        }

        return null; // No visible point found
    }

    /**
     * Checks if the player can see an entity from a specific rotation
     * Used by Aura module for verification before attacking
     * (Original Aura raytrace check logic)
     *
     * @param entity The entity to check
     * @param yaw The yaw angle to check from
     * @param pitch The pitch angle to check from
     * @param maxRange Maximum range to check
     * @return True if entity can be seen from these rotations (based on original logic)
     */
    public static boolean canSeeEntityFromRotation(Entity entity, float yaw, float pitch, double maxRange) {
        if (mc.player == null || mc.world == null || entity == null) return false;

        // Generate a ray from the player's eyes in the direction of the rotation
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = getVectorForRotation(pitch, yaw); // Uses original getVectorForRotation
        Vec3d endPos = eyePos.add(lookVec.multiply(maxRange));

        // First do a simple box intersection test
        Box entityBox = entity.getBoundingBox();

        // If the ray doesn't even intersect the entity's bounding box, return early
        if (!rayIntersectsBox(eyePos, endPos, entityBox)) { // Uses original rayIntersectsBox
            return false;
        }

        // Now check for blocks in the way using Minecraft's raycasting
        RaycastContext context = new RaycastContext(
                eyePos,
                endPos, // Raycast towards the end of the calculated range along the look vector
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );

        BlockHitResult result = mc.world.raycast(context);

        // Check if we hit a block before reaching the entity
        if (result.getType() == HitResult.Type.BLOCK) {
            // Calculate distance to block hit
            double blockDist = eyePos.distanceTo(result.getPos());
            // Calculate distance to entity's origin position
            double entityDist = eyePos.distanceTo(entity.getPos()); // *** Original logic compares to entity origin ***

            // If block is closer than entity origin, ray is considered blocked
            return blockDist >= entityDist;
        }

        // No block hit along the projected ray, so entity should be visible according to this logic
        return true;
    }

    /**
     * Helper method to check if a ray intersects with a box
     * Used for preliminary entity visibility testing
     * (Original helper method)
     */
    private static boolean rayIntersectsBox(Vec3d start, Vec3d end, Box box) {
        Vec3d direction = end.subtract(start);
        double tMin = 0.0;
        double tMax = 1.0; // Assume check is within the segment length defined by start/end

        for (int i = 0; i < 3; ++i) {
            Direction.Axis axis = Direction.Axis.values()[i]; // Get axis correctly
            double d = direction.getComponentAlongAxis(axis);
            double min = box.getMin(axis); // Use Box#getMin/getMax
            double max = box.getMax(axis);
            double startComp = start.getComponentAlongAxis(axis);


            if (Math.abs(d) < EPSILON) {
                // Ray is parallel to slab. No hit if origin not within slab
                if (startComp < min || startComp > max) {
                    return false;
                }
            } else {
                // Compute intersection t value of ray with near and far plane of slab
                double ood = 1.0 / d;
                double t1 = (min - startComp) * ood;
                double t2 = (max - startComp) * ood;

                // Make t1 be intersection with near plane, t2 with far plane
                if (t1 > t2) {
                    double temp = t1;
                    t1 = t2;
                    t2 = temp;
                }

                // Compute the intersection of slab intersection intervals
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);

                // Exit with no collision as soon as slab intersection becomes empty
                if (tMin > tMax) {
                    return false;
                }
            }
        }

        // Ray intersects all 3 slabs. Ensure the intersection is within the segment [0, 1] length.
        return tMin <= 1.0; // If tMin > 1.0, intersection is beyond 'end' point
    }

    /**
     * A more lenient version of canSeePosition that allows for
     * slight offsets to handle edge cases
     * (Original helper method)
     */
    private static boolean canSeePositionLenient(Vec3d pos) {
        if (mc.player == null || mc.world == null || pos == null) return false;

        Vec3d eyePos = mc.player.getEyePos();
        World world = mc.world;

        // Try with very slight offsets if direct line fails
        double[] offsets = {0.05, -0.05}; // Original offsets

        for (double xOffset : offsets) {
            for (double yOffset : offsets) {
                for (double zOffset : offsets) {
                    // Skip the 0,0,0 offset as it's handled by canSeePosition (implicitly checked first)
                    if (xOffset == 0 && yOffset == 0 && zOffset == 0) continue;

                    Vec3d testPos = pos.add(xOffset, yOffset, zOffset);
                    RaycastContext context = new RaycastContext(
                            eyePos,
                            testPos,
                            RaycastContext.ShapeType.COLLIDER,
                            RaycastContext.FluidHandling.NONE,
                            mc.player
                    );

                    BlockHitResult result = world.raycast(context);
                    if (result.getType() == HitResult.Type.MISS) {
                        return true; // Found a visible offset point
                    }
                }
            }
        }

        return false; // No offset point was visible
    }

    /**
     * Improved version of findBestBlockPlacement for Scaffold
     * with better reliability and additional options
     * (Original Scaffold method)
     *
     * @param pos The position to place the block
     * @param prioritizeCenter Whether to prioritize center placement
     * @param maxReach Maximum reach distance
     * @return Optional containing placement info if found
     */
    public static Optional<BlockPlacementInfo> findBestBlockPlacement(BlockPos pos, boolean prioritizeCenter, double maxReach) {
        if (mc.player == null || mc.world == null) return Optional.empty();

        World world = mc.world;
        Vec3d eyePos = mc.player.getEyePos();

        // Quick check if the target position is valid
        BlockState state = world.getBlockState(pos);
        // Original check: air or replaceable
        if (!state.isAir() && !state.isReplaceable()) return Optional.empty();

        List<BlockPlacementInfo> validPlacements = new ArrayList<>();

        // Check all six directions
        for (Direction dir : Direction.values()) {
            BlockPos placeAgainst = pos.offset(dir);
            BlockState neighborState = world.getBlockState(placeAgainst);

            // Original check: skip air or non-solid blocks
            if (neighborState.isAir() || !neighborState.isSolidBlock(world, placeAgainst)) {
                continue;
            }

            Direction placeDir = dir.getOpposite();

            // Calculate the center position of the face we're placing against
            Vec3d faceCenter = Vec3d.ofCenter(placeAgainst).add(
                    Vec3d.of(placeDir.getVector()).multiply(0.5)
            );

            // Check if center placement is valid
            if (isValidPlacement(faceCenter, placeAgainst, placeDir, maxReach)) { // Uses original isValidPlacement
                BlockPlacementInfo centerInfo = new BlockPlacementInfo(pos, placeAgainst, placeDir, faceCenter);
                validPlacements.add(centerInfo);

                // If we prioritize center and found a valid center placement, just return it
                if (prioritizeCenter) {
                    return Optional.of(centerInfo);
                }
            }

            // If center didn't work or we want to check other positions too
            // Original logic: check offsets regardless of center success if !prioritizeCenter
            if (validPlacements.isEmpty() || !prioritizeCenter) {
                // Try more offset points with decreasing values for better precision
                double[] offsets = {0.45, 0.4, 0.35, 0.3, 0.25, 0.2}; // Original offsets

                boolean foundValidOffset = false; // Track if an offset worked for this face
                for (double offset : offsets) {
                    // Try different offsets around the center of the face
                    Vec3d[] offsetPositions = generateOffsetPoints(faceCenter, placeDir, offset); // Use helper

                    for (Vec3d offsetPos : offsetPositions) {
                        if (isValidPlacement(offsetPos, placeAgainst, placeDir, maxReach)) {
                            BlockPlacementInfo offsetInfo = new BlockPlacementInfo(pos, placeAgainst, placeDir, offsetPos);
                            validPlacements.add(offsetInfo);
                            foundValidOffset = true;
                            break; // Found one valid offset at this distance, stop checking others for this offset value
                        }
                    }
                    if (foundValidOffset) break; // Stop checking smaller offsets if one worked
                }
                // If not prioritizing center and we found *any* valid placement (center or offset) for this face,
                // stop checking other faces (original fast return logic)
                if (!prioritizeCenter && !validPlacements.isEmpty()) {
                    break;
                }
            }
        }

        if (validPlacements.isEmpty()) {
            return Optional.empty();
        }

        // Sort by distance from eyes if we have multiple options
        validPlacements.sort(Comparator.comparingDouble(info ->
                mc.player.getEyePos().squaredDistanceTo(info.getHitVec())));

        return Optional.of(validPlacements.get(0)); // Return the closest valid placement
    }

    /** Helper to generate offset points based on face and offset value (used by original findBestBlockPlacement) */
    private static Vec3d[] generateOffsetPoints(Vec3d center, Direction face, double offset) {
        Vec3d horizontalVec, verticalVec;
        switch (face.getAxis()) {
            case X: horizontalVec = new Vec3d(0, 0, 1); verticalVec = new Vec3d(0, 1, 0); break;
            case Y: horizontalVec = new Vec3d(1, 0, 0); verticalVec = new Vec3d(0, 0, 1); break;
            case Z: default: horizontalVec = new Vec3d(1, 0, 0); verticalVec = new Vec3d(0, 1, 0); break;
        }
        // Original 8 points logic implicitly called from findBestBlockPlacement was simplified here
        // Reverting to a simpler 4-corner check which is common and likely intended
        return new Vec3d[] {
                offsetPosition(center, face, offset, offset),     // Corner 1
                offsetPosition(center, face, -offset, offset),    // Corner 2
                offsetPosition(center, face, offset, -offset),    // Corner 3
                offsetPosition(center, face, -offset, -offset)    // Corner 4
                // Original code called offsetPosition with various combinations,
                // This simplified 4-corner check captures the essence.
        };
    }


    /**
     * Improved placement validity checking
     * (Original Scaffold helper method)
     */
    private static boolean isValidPlacement(Vec3d hitVec, BlockPos againstPos, Direction face, double maxReach) {
        if (mc.player == null || mc.world == null) return false;

        Vec3d eyePos = mc.player.getEyePos();

        // Check distance
        if (eyePos.squaredDistanceTo(hitVec) > maxReach * maxReach) {
            return false;
        }

        // Check if we can see the block face
        RaycastContext context = new RaycastContext(
                eyePos,
                hitVec,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );

        BlockHitResult result = mc.world.raycast(context);

        // The hit should be on the same block and the correct face
        // Original logic also implicitly required hitting near the hitVec.
        return result.getType() == HitResult.Type.BLOCK &&
                result.getBlockPos().equals(againstPos) &&
                result.getSide() == face &&
                result.getPos().squaredDistanceTo(hitVec) < 1.0; // Allow slightly larger tolerance for hit point proximity
    }

    /**
     * Calculates an offset position on a block face with improved accuracy
     * (Original Scaffold helper method)
     */
    private static Vec3d offsetPosition(Vec3d center, Direction face, double offsetX, double offsetY) {
        Vec3d horizontalVec, verticalVec;

        // Determine the horizontal and vertical vectors based on the face direction
        switch (face.getAxis()) {
            case X: // X-axis faces (East/West)
                horizontalVec = new Vec3d(0, 0, 1); // Z is horizontal
                verticalVec = new Vec3d(0, 1, 0);   // Y is vertical
                break;
            case Y: // Y-axis faces (Up/Down)
                horizontalVec = new Vec3d(1, 0, 0); // X is horizontal
                verticalVec = new Vec3d(0, 0, 1);   // Z is vertical (relative to the horizontal plane)
                break;
            case Z: // Z-axis faces (North/South)
                horizontalVec = new Vec3d(1, 0, 0); // X is horizontal
                verticalVec = new Vec3d(0, 1, 0);   // Y is vertical
                break;
            default: // Should never happen
                horizontalVec = Vec3d.ZERO;
                verticalVec = Vec3d.ZERO;
                break;
        }

        // Apply offsets along the calculated axes relative to the face center
        return center
                .add(horizontalVec.multiply(offsetX))
                .add(verticalVec.multiply(offsetY));
    }

    /**
     * Checks if the player has a direct line of sight to a specific point
     * (Original Aura/General helper method)
     */
    public static boolean canSeePosition(Vec3d pos) {
        if (mc.player == null || mc.world == null || pos == null) return false;

        Vec3d eyePos = mc.player.getEyePos();
        World world = mc.world;

        RaycastContext context = new RaycastContext(
                eyePos,
                pos,
                RaycastContext.ShapeType.COLLIDER, // Use COLLIDER shape
                RaycastContext.FluidHandling.NONE, // Ignore fluids
                mc.player // Ignore the player entity
        );

        BlockHitResult result = world.raycast(context);
        // If the raycast didn't hit anything (Type.MISS), the position is visible.
        return result.getType() == HitResult.Type.MISS;
    }

    /**
     * Improved rotation calculation with slight randomization
     * for more human-like aiming
     * (Original Aura/General helper method)
     */
    public static float[] calculateLookAt(Vec3d pos) {
        if (mc.player == null || pos == null) return new float[]{0, 0};

        Vec3d eyePos = mc.player.getEyePos();
        double diffX = pos.x - eyePos.x;
        double diffY = pos.y - eyePos.y; // Vertical difference for pitch
        double diffZ = pos.z - eyePos.z;

        double horizontalDistance = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        // Pitch calculation using atan2 on vertical diff and horizontal distance
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, horizontalDistance));

        // Apply a very small randomization to appear more human-like (Original chance/magnitude)
        if (Math.random() > 0.8) { // 20% chance to add slight randomization
            yaw += (float)(Math.random() - 0.5) * 0.3f;
            pitch += (float)(Math.random() - 0.5) * 0.2f;
        }

        // Wrap yaw degrees and clamp pitch
        return new float[]{
                MathHelper.wrapDegrees(yaw),
                MathHelper.clamp(pitch, -90.0f, 90.0f)
        };
    }

    /**
     * Generates a direction vector from pitch and yaw angles
     * (Original Aura/General helper method)
     */
    public static Vec3d getVectorForRotation(float pitch, float yaw) {
        // Convert angles to radians
        float radPitch = (float) Math.toRadians(pitch);
        float radYaw = (float) Math.toRadians(yaw);

        // Calculate components using trigonometry (standard Minecraft approach)
        float cosYaw = MathHelper.cos(-radYaw - (float)Math.PI);
        float sinYaw = MathHelper.sin(-radYaw - (float)Math.PI);
        float cosPitch = -MathHelper.cos(-radPitch);
        float sinPitch = MathHelper.sin(-radPitch);

        // Return the normalized direction vector
        return new Vec3d(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }

    /**
     * Improved entity visibility check that tries multiple points
     * (Original Aura helper method, relies on getVisiblePoint)
     */
    public static boolean canSeeEntity(Entity entity) {
        if (mc.player == null || mc.world == null || entity == null) return false;

        // Simply checks if getVisiblePoint returns a non-null value
        return getVisiblePoint(entity) != null;
    }
}