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
 */
public class RayCastUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // Constants for better visibility testing
    private static final float EPSILON = 0.001f;
    private static final double MAX_ATTACK_RANGE = 6.0; // Maximum range for attack visibility checks

    /**
     * Simplified class to hold block placement information
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
            this.rotations = calculateLookAt(hitVec);
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
     *
     * @param entity The entity to check
     * @return The best visible point, or null if none is visible
     */
    public static Vec3d getVisiblePoint(Entity entity) {
        if (mc.player == null || mc.world == null || entity == null) return null;

        // Try the eyes first as that's often the most reliable spot
        Vec3d eyePos = entity.getEyePos();
        if (canSeePosition(eyePos)) {
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

        sortedPoints.sort(Comparator.comparingDouble(p ->
                mc.player.getEyePos().squaredDistanceTo(p)));

        // Check each point for visibility
        for (Vec3d point : sortedPoints) {
            if (canSeePosition(point)) {
                return point;
            }
        }

        // If no point is directly visible, try with more lenient parameters
        // This helps with edge cases where the entity is partially visible
        for (Vec3d point : sortedPoints) {
            if (canSeePositionLenient(point)) {
                return point;
            }
        }

        return null; // No visible point found
    }

    /**
     * Checks if the player can see an entity from a specific rotation
     * Used by Aura module for verification before attacking
     *
     * @param entity The entity to check
     * @param yaw The yaw angle to check from
     * @param pitch The pitch angle to check from
     * @param maxRange Maximum range to check
     * @return True if entity can be seen from these rotations
     */
    public static boolean canSeeEntityFromRotation(Entity entity, float yaw, float pitch, double maxRange) {
        if (mc.player == null || mc.world == null || entity == null) return false;

        // Generate a ray from the player's eyes in the direction of the rotation
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = getVectorForRotation(pitch, yaw);
        Vec3d endPos = eyePos.add(lookVec.multiply(maxRange));

        // First do a simple box intersection test
        Box entityBox = entity.getBoundingBox();

        // If the ray doesn't even intersect the entity's bounding box, return early
        if (!rayIntersectsBox(eyePos, endPos, entityBox)) {
            return false;
        }

        // Now check for blocks in the way using Minecraft's raycasting
        RaycastContext context = new RaycastContext(
                eyePos,
                endPos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );

        BlockHitResult result = mc.world.raycast(context);

        // Check if we hit a block before reaching the entity
        if (result.getType() == HitResult.Type.BLOCK) {
            // Calculate distance to block hit
            double blockDist = eyePos.distanceTo(result.getPos());
            // Calculate distance to entity
            double entityDist = eyePos.distanceTo(entity.getPos());

            // If block is closer than entity, ray is blocked
            return blockDist >= entityDist;
        }

        // No block hit, so entity should be visible
        return true;
    }

    /**
     * Helper method to check if a ray intersects with a box
     * Used for preliminary entity visibility testing
     */
    private static boolean rayIntersectsBox(Vec3d start, Vec3d end, Box box) {
        Vec3d direction = end.subtract(start);
        double tMin = 0.0;
        double tMax = 1.0;

        for (int i = 0; i < 3; ++i) {
            double d = direction.getComponentAlongAxis(Direction.Axis.values()[i]);
            double min, max;

            if (i == 0) { // X-axis
                min = box.minX;
                max = box.maxX;
            } else if (i == 1) { // Y-axis
                min = box.minY;
                max = box.maxY;
            } else { // Z-axis
                min = box.minZ;
                max = box.maxZ;
            }

            if (Math.abs(d) < EPSILON) {
                // Ray is parallel to slab. No hit if origin not within slab
                double comp = start.getComponentAlongAxis(Direction.Axis.values()[i]);
                if (comp < min || comp > max) {
                    return false;
                }
            } else {
                // Compute intersection t value of ray with near and far plane of slab
                double startComp = start.getComponentAlongAxis(Direction.Axis.values()[i]);
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

        // Ray intersects all 3 slabs, so there's a hit
        return true;
    }

    /**
     * A more lenient version of canSeePosition that allows for
     * slight offsets to handle edge cases
     */
    private static boolean canSeePositionLenient(Vec3d pos) {
        if (mc.player == null || mc.world == null || pos == null) return false;

        Vec3d eyePos = mc.player.getEyePos();
        World world = mc.world;

        // Try with very slight offsets if direct line fails
        // This helps with block edge cases and server-client desync
        double[] offsets = {0.05, -0.05};

        for (double xOffset : offsets) {
            for (double yOffset : offsets) {
                for (double zOffset : offsets) {
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
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Improved version of findBestBlockPlacement for Scaffold
     * with better reliability and additional options
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
        if (!state.isAir() && !state.isReplaceable()) return Optional.empty();

        List<BlockPlacementInfo> validPlacements = new ArrayList<>();

        // Check all six directions
        for (Direction dir : Direction.values()) {
            BlockPos placeAgainst = pos.offset(dir);
            BlockState neighborState = world.getBlockState(placeAgainst);

            // Skip if the block we'd place against isn't solid
            if (neighborState.isAir() || !neighborState.isSolidBlock(world, placeAgainst)) {
                continue;
            }

            Direction placeDir = dir.getOpposite();

            // Calculate the center position of the face we're placing against
            Vec3d faceCenter = Vec3d.ofCenter(placeAgainst).add(
                    Vec3d.of(placeDir.getVector()).multiply(0.5)
            );

            // Check if center placement is valid
            if (isValidPlacement(faceCenter, placeAgainst, placeDir, maxReach)) {
                validPlacements.add(new BlockPlacementInfo(pos, placeAgainst, placeDir, faceCenter));

                // If we prioritize center and found a valid center placement, just return it
                if (prioritizeCenter) {
                    return Optional.of(validPlacements.get(0));
                }
            }

            // If center didn't work or we want to check other positions too
            if (validPlacements.isEmpty() || !prioritizeCenter) {
                // Try more offset points with decreasing values for better precision
                double[] offsets = {0.45, 0.4, 0.35, 0.3, 0.25, 0.2};

                for (double offset : offsets) {
                    // Try different offsets around the center of the face
                    Vec3d[] offsetPositions = {
                            offsetPosition(faceCenter, placeDir, offset, 0),   // Right
                            offsetPosition(faceCenter, placeDir, -offset, 0),  // Left
                            offsetPosition(faceCenter, placeDir, 0, offset),   // Up
                            offsetPosition(faceCenter, placeDir, 0, -offset),  // Down
                            offsetPosition(faceCenter, placeDir, offset, offset),    // Up-Right
                            offsetPosition(faceCenter, placeDir, -offset, offset),   // Up-Left
                            offsetPosition(faceCenter, placeDir, offset, -offset),   // Down-Right
                            offsetPosition(faceCenter, placeDir, -offset, -offset)   // Down-Left
                    };

                    for (Vec3d offsetPos : offsetPositions) {
                        if (isValidPlacement(offsetPos, placeAgainst, placeDir, maxReach)) {
                            validPlacements.add(new BlockPlacementInfo(pos, placeAgainst, placeDir, offsetPos));

                            // If we found at least one valid placement and want to be fast, just return it
                            if (!prioritizeCenter) {
                                return Optional.of(validPlacements.get(validPlacements.size() - 1));
                            }
                        }
                    }
                }
            }
        }

        if (validPlacements.isEmpty()) {
            return Optional.empty();
        }

        // Sort by distance from eyes if we have multiple options
        validPlacements.sort(Comparator.comparingDouble(info ->
                mc.player.getEyePos().squaredDistanceTo(info.getHitVec())));

        return Optional.of(validPlacements.get(0));
    }

    /**
     * Improved placement validity checking
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
        return result.getType() == HitResult.Type.BLOCK &&
                result.getBlockPos().equals(againstPos) &&
                result.getSide() == face;
    }

    /**
     * Calculates an offset position on a block face with improved accuracy
     */
    private static Vec3d offsetPosition(Vec3d center, Direction face, double offsetX, double offsetY) {
        Vec3d horizontalVec, verticalVec;

        // Determine the horizontal and vertical vectors based on the face direction
        switch (face.getAxis()) {
            case X: // X-axis faces (East/West)
                horizontalVec = new Vec3d(0, 0, 1);
                verticalVec = new Vec3d(0, 1, 0);
                break;
            case Y: // Y-axis faces (Up/Down)
                horizontalVec = new Vec3d(1, 0, 0);
                verticalVec = new Vec3d(0, 0, 1);
                break;
            case Z: // Z-axis faces (North/South)
                horizontalVec = new Vec3d(1, 0, 0);
                verticalVec = new Vec3d(0, 1, 0);
                break;
            default:
                return center; // Should never happen
        }

        return center
                .add(horizontalVec.multiply(offsetX))
                .add(verticalVec.multiply(offsetY));
    }

    /**
     * Checks if the player has a direct line of sight to a specific point
     */
    public static boolean canSeePosition(Vec3d pos) {
        if (mc.player == null || mc.world == null || pos == null) return false;

        Vec3d eyePos = mc.player.getEyePos();
        World world = mc.world;

        RaycastContext context = new RaycastContext(
                eyePos,
                pos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );

        BlockHitResult result = world.raycast(context);
        return result.getType() == HitResult.Type.MISS;
    }

    /**
     * Improved rotation calculation with slight randomization
     * for more human-like aiming
     */
    public static float[] calculateLookAt(Vec3d pos) {
        if (mc.player == null || pos == null) return new float[]{0, 0};

        Vec3d eyePos = mc.player.getEyePos();
        double diffX = pos.x - eyePos.x;
        double diffY = pos.y - eyePos.y;
        double diffZ = pos.z - eyePos.z;

        double horizontalDistance = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, horizontalDistance));

        // Apply a very small randomization to appear more human-like
        if (Math.random() > 0.8) { // 20% chance to add slight randomization
            yaw += (float)(Math.random() - 0.5) * 0.3f;
            pitch += (float)(Math.random() - 0.5) * 0.2f;
        }

        return new float[]{
                MathHelper.wrapDegrees(yaw),
                MathHelper.clamp(pitch, -90.0f, 90.0f)
        };
    }

    /**
     * Generates a direction vector from pitch and yaw angles
     */
    public static Vec3d getVectorForRotation(float pitch, float yaw) {
        float radYaw = -yaw * 0.017453292F - (float)Math.PI;
        float radPitch = -pitch * 0.017453292F;
        float cosYaw = MathHelper.cos(radYaw);
        float sinYaw = MathHelper.sin(radYaw);
        float cosPitch = MathHelper.cos(radPitch);
        float sinPitch = MathHelper.sin(radPitch);
        return new Vec3d(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }

    /**
     * Improved entity visibility check that tries multiple points
     */
    public static boolean canSeeEntity(Entity entity) {
        if (mc.player == null || mc.world == null || entity == null) return false;

        // Try getting a visible point
        return getVisiblePoint(entity) != null;
    }
}