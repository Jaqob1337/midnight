package de.peter1337.midnight.utils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for raytrace operations including block placement
 */
public class RayCastUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /**
     * Checks if the player can see a specific point on an entity
     *
     * @param entity The target entity
     * @param pos The position to check
     * @return True if the point is visible to the player
     */
    public static boolean canSeePosition(Entity entity, Vec3d pos) {
        if (mc.player == null || mc.world == null) return false;

        Vec3d eyePos = mc.player.getEyePos();
        World world = mc.world;

        // Create a raytrace context
        RaycastContext context = new RaycastContext(
                eyePos,
                pos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );

        // Perform the raytrace
        var result = world.raycast(context);

        // If the hit position is close to the target position, we can see it
        return result.getPos().squaredDistanceTo(pos) < 0.01;
    }

    /**
     * Checks if the player can see an entity from the current rotation
     *
     * @param entity The target entity
     * @param yaw The yaw angle
     * @param pitch The pitch angle
     * @param maxDistance The maximum distance to check
     * @return True if the entity is visible at the specified rotation
     */
    public static boolean canSeeEntityFromRotation(Entity entity, float yaw, float pitch, double maxDistance) {
        if (mc.player == null || mc.world == null) return false;

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = getVectorForRotation(pitch, yaw);
        Vec3d endPos = eyePos.add(lookVec.multiply(maxDistance));

        // Create a raytrace context
        RaycastContext context = new RaycastContext(
                eyePos,
                endPos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );

        // Perform the raytrace against blocks
        var result = mc.world.raycast(context);
        Vec3d hitPos = result.getPos();

        // Get the distance to any block that was hit
        double blockDist = eyePos.squaredDistanceTo(hitPos);

        // Check if raytrace hit entity before any blocks
        Box entityBox = entity.getBoundingBox();
        Vec3d intersectionPoint = rayTraceEntityBox(eyePos, endPos, entityBox);

        if (intersectionPoint != null) {
            double entityDist = eyePos.squaredDistanceTo(intersectionPoint);
            return entityDist <= blockDist;
        }

        return false;
    }

    /**
     * Checks if the entity's hitbox is being looked at with server rotations
     *
     * @param entity The target entity
     * @param serverYaw The server-side yaw angle
     * @param serverPitch The server-side pitch angle
     * @param maxDistance The maximum distance to check
     * @return True if the entity is being looked at
     */
    public static boolean isEntityInServerView(Entity entity, float serverYaw, float serverPitch, double maxDistance) {
        return canSeeEntityFromRotation(entity, serverYaw, serverPitch, maxDistance);
    }

    /**
     * Raytrace against an entity's bounding box
     *
     * @param start Start position of ray
     * @param end End position of ray
     * @param box Bounding box to check
     * @return The intersection point, or null if no intersection
     */
    public static Vec3d rayTraceEntityBox(Vec3d start, Vec3d end, Box box) {
        // Calculate ray direction
        Vec3d dir = end.subtract(start).normalize();

        // Expanded box
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        // Ray-box intersection algorithm
        double tMin = (minX - start.x) / dir.x;
        double tMax = (maxX - start.x) / dir.x;

        if (tMin > tMax) {
            double temp = tMin;
            tMin = tMax;
            tMax = temp;
        }

        double tyMin = (minY - start.y) / dir.y;
        double tyMax = (maxY - start.y) / dir.y;

        if (tyMin > tyMax) {
            double temp = tyMin;
            tyMin = tyMax;
            tyMax = temp;
        }

        if ((tMin > tyMax) || (tyMin > tMax)) {
            return null;
        }

        if (tyMin > tMin) {
            tMin = tyMin;
        }

        if (tyMax < tMax) {
            tMax = tyMax;
        }

        double tzMin = (minZ - start.z) / dir.z;
        double tzMax = (maxZ - start.z) / dir.z;

        if (tzMin > tzMax) {
            double temp = tzMin;
            tzMin = tzMax;
            tzMax = temp;
        }

        if ((tMin > tzMax) || (tzMin > tMax)) {
            return null;
        }

        if (tzMin > tMin) {
            tMin = tzMin;
        }

        if (tzMax < tMax) {
            tMax = tzMax;
        }

        if (tMin < 0) {
            if (tMax < 0) {
                return null;
            }
            tMin = 0;
        }

        // Calculate intersection point
        return start.add(dir.multiply(tMin));
    }

    /**
     * Generates a look vector from pitch and yaw angles
     *
     * @param pitch The pitch angle
     * @param yaw The yaw angle
     * @return A normalized vector representing the direction
     */
    public static Vec3d getVectorForRotation(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float)Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float)Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3d(f1 * f2, f3, f * f2);
    }

    /**
     * Tests multiple points on an entity's hitbox to find the best visible point
     *
     * @param entity The target entity
     * @return The best point to target, or null if no points are visible
     */
    public static Vec3d getVisiblePoint(Entity entity) {
        if (mc.player == null) return null;

        Box box = entity.getBoundingBox();
        Vec3d eyePos = mc.player.getEyePos();

        // Check entity eye position first (priority target)
        Vec3d eyeTarget = entity.getEyePos();
        if (canSeePosition(entity, eyeTarget)) {
            return eyeTarget;
        }

        // Check center of the entity
        Vec3d centerTarget = new Vec3d((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);
        if (canSeePosition(entity, centerTarget)) {
            return centerTarget;
        }

        // Define test points (8 corners and 6 face centers of the bounding box)
        Vec3d[] testPoints = new Vec3d[] {
                // Corners
                new Vec3d(box.minX, box.minY, box.minZ),
                new Vec3d(box.minX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.minZ),
                new Vec3d(box.minX, box.maxY, box.maxZ),
                new Vec3d(box.maxX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.maxZ),
                new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.maxZ),
                // Face centers
                new Vec3d((box.minX + box.maxX) / 2, box.minY, (box.minZ + box.maxZ) / 2), // Bottom
                new Vec3d((box.minX + box.maxX) / 2, box.maxY, (box.minZ + box.maxZ) / 2), // Top
                new Vec3d(box.minX, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2), // Left
                new Vec3d(box.maxX, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2), // Right
                new Vec3d((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, box.minZ), // Front
                new Vec3d((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, box.maxZ)  // Back
        };

        // Find the closest visible point
        Vec3d closestPoint = null;
        double closestDistance = Double.MAX_VALUE;

        for (Vec3d point : testPoints) {
            if (canSeePosition(entity, point)) {
                double distance = eyePos.squaredDistanceTo(point);
                if (closestPoint == null || distance < closestDistance) {
                    closestPoint = point;
                    closestDistance = distance;
                }
            }
        }

        return closestPoint;
    }

    /**
     * Check if a line-of-sight exists to any point on the entity's hitbox
     *
     * @param entity The target entity
     * @return True if the entity is visible from any tested point
     */
    public static boolean canSeeEntity(Entity entity) {
        return getVisiblePoint(entity) != null;
    }

    /**
     * Represents a possible block placement with all necessary information
     */
    public static class BlockPlacementInfo {
        private final BlockPos targetPos;      // The block position where we want to place
        private final BlockPos placeAgainst;   // The block position we're clicking on
        private final Direction placeDir;      // The face of the block we're clicking on
        private final Vec3d hitVec;            // The precise hit vector for placement
        private final float[] rotations;       // The rotations needed to place this block [yaw, pitch]
        private final double distFromCenter;   // How far from center of face the hit is (lower is better)
        private final double eyesDistance;     // Distance from player eyes (lower is better)

        public BlockPlacementInfo(BlockPos targetPos, BlockPos placeAgainst, Direction placeDir,
                                  Vec3d hitVec, float[] rotations, double distFromCenter, double eyesDistance) {
            this.targetPos = targetPos;
            this.placeAgainst = placeAgainst;
            this.placeDir = placeDir;
            this.hitVec = hitVec;
            this.rotations = rotations;
            this.distFromCenter = distFromCenter;
            this.eyesDistance = eyesDistance;
        }

        public BlockPos getTargetPos() {
            return targetPos;
        }

        public BlockPos getPlaceAgainst() {
            return placeAgainst;
        }

        public Direction getPlaceDir() {
            return placeDir;
        }

        public Vec3d getHitVec() {
            return hitVec;
        }

        public float[] getRotations() {
            return rotations;
        }

        public double getDistFromCenter() {
            return distFromCenter;
        }

        public double getEyesDistance() {
            return eyesDistance;
        }

        @Override
        public String toString() {
            return "BlockPlacementInfo{" +
                    "targetPos=" + targetPos +
                    ", placeAgainst=" + placeAgainst +
                    ", placeDir=" + placeDir +
                    ", hitVec=" + hitVec +
                    ", rotations=[" + rotations[0] + "," + rotations[1] + "]" +
                    ", distFromCenter=" + distFromCenter +
                    ", eyesDistance=" + eyesDistance +
                    '}';
        }
    }

    /**
     * Finds the best way to place a block at the specified position
     *
     * @param pos The position to place a block at
     * @param strictCenter Whether to prioritize center hits or just any valid placement
     * @param maxReach Maximum allowed reach distance (usually 4.5)
     * @return Optional containing the placement information, or empty if placement is not possible
     */
    public static Optional<BlockPlacementInfo> findBestBlockPlacement(BlockPos pos, boolean strictCenter, double maxReach) {
        if (mc.player == null || mc.world == null) {
            return Optional.empty();
        }

        Vec3d eyesPos = mc.player.getEyePos();
        World world = mc.world;

        // Check if block can be placed
        BlockState state = world.getBlockState(pos);
        if (!state.isAir() && !state.isReplaceable()) {
            return Optional.empty();
        }

        // Create a list to store all valid placements
        List<BlockPlacementInfo> validPlacements = new ArrayList<>();

        // Check all surrounding blocks
        for (Direction dir : Direction.values()) {
            BlockPos placeAgainst = pos.offset(dir);

            // Can't place against air
            BlockState neighborState = world.getBlockState(placeAgainst);
            if (neighborState.isAir() || !neighborState.isSolid()) {
                continue;
            }

            // Check if the face has a valid shape to place against
            VoxelShape shape = neighborState.getOutlineShape(world, placeAgainst);
            if (shape.isEmpty()) {
                continue;
            }

            // The direction we're placing on is the opposite of the offset direction
            Direction placeDir = dir.getOpposite();

            // Try multiple points on the face to find best placement
            Vec3d faceCenter = Vec3d.ofCenter(placeAgainst).add(
                    placeDir.getOffsetX() * 0.5,
                    placeDir.getOffsetY() * 0.5,
                    placeDir.getOffsetZ() * 0.5
            );

            // Generate points to test - center plus offsets for better coverage
            double[] offsets = {0.0, 0.1, 0.25, 0.4, 0.45, 0.49};

            // Try center point first
            tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter,
                    eyesPos, maxReach, validPlacements, 0, 0);

            // Try horizontal and vertical axis points
            for (double offset : offsets) {
                if (offset == 0.0) continue; // Skip as we already tested center

                // Horizontal offset points (X-axis equivalent)
                tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter,
                        eyesPos, maxReach, validPlacements, offset, 0);
                tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter,
                        eyesPos, maxReach, validPlacements, -offset, 0);

                // Vertical offset points (Y-axis equivalent)
                tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter,
                        eyesPos, maxReach, validPlacements, 0, offset);
                tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter,
                        eyesPos, maxReach, validPlacements, 0, -offset);

                // Diagonal points at various offsets
                for (double secondOffset : offsets) {
                    if (secondOffset == 0.0) continue;

                    tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter,
                            eyesPos, maxReach, validPlacements, offset, secondOffset);
                    tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter,
                            eyesPos, maxReach, validPlacements, -offset, secondOffset);
                    tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter,
                            eyesPos, maxReach, validPlacements, offset, -secondOffset);
                    tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter,
                            eyesPos, maxReach, validPlacements, -offset, -secondOffset);
                }
            }
        }

        // If we found valid placements, sort and return the best one
        if (!validPlacements.isEmpty()) {
            // Sort placements based on criteria - center distance and eyes distance
            Comparator<BlockPlacementInfo> comparator;

            if (strictCenter) {
                // In strict mode, prioritize center hits more
                comparator = Comparator
                        .comparingDouble(BlockPlacementInfo::getDistFromCenter)
                        .thenComparingDouble(BlockPlacementInfo::getEyesDistance);
            } else {
                // In normal mode, balance between center and distance
                comparator = Comparator.comparingDouble(p ->
                        p.getDistFromCenter() * 0.4 + p.getEyesDistance() * 0.6);
            }

            return Optional.of(validPlacements.stream()
                    .min(comparator)
                    .orElse(validPlacements.get(0)));
        }

        return Optional.empty();
    }

    /**
     * Tests a specific point on a block face for placement and adds it to valid placements if successful
     *
     * @param pos Target block position
     * @param placeAgainst Position of block to place against
     * @param placeDir Direction of face on the block to place against
     * @param faceCenter Center point of the face
     * @param eyesPos Player's eye position
     * @param maxReach Maximum reach distance
     * @param validPlacements List to add successful placements to
     * @param offsetX X offset from center
     * @param offsetY Y offset from center
     */
    private static void tryPlacementPoint(
            BlockPos pos, BlockPos placeAgainst, Direction placeDir, Vec3d faceCenter,
            Vec3d eyesPos, double maxReach, List<BlockPlacementInfo> validPlacements,
            double offsetX, double offsetY) {

        // Calculate the actual hit vector with offsets
        Vec3d hitVec = getOffsetPoint(faceCenter, placeDir, offsetX, offsetY);

        // Check distance from eyes to hit point
        double dist = eyesPos.distanceTo(hitVec);
        if (dist > maxReach) {
            return;
        }

        // Check line of sight
        boolean canSee = canSeeBlockFace(eyesPos, hitVec, placeAgainst, placeDir);
        if (!canSee) {
            return;
        }

        // Calculate rotations needed to look at this hit point
        float[] rotations = calculateLookAt(hitVec);

        // How centered is the hit? (distance from face center)
        double centerDist = hitVec.distanceTo(faceCenter);

        // Create and add a new valid placement
        BlockPlacementInfo placement = new BlockPlacementInfo(
                pos, placeAgainst, placeDir, hitVec, rotations, centerDist, dist
        );

        validPlacements.add(placement);
    }

    /**
     * Checks if there's a clear line of sight to a specific point on a block face
     */
    private static boolean canSeeBlockFace(Vec3d eyesPos, Vec3d hitVec, BlockPos blockPos, Direction face) {
        if (mc.world == null) return false;

        // Create raytrace context
        RaycastContext context = new RaycastContext(
                eyesPos,
                hitVec,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );

        // Perform the raytrace
        BlockHitResult hit = mc.world.raycast(context);

        // Check if we hit the correct block on the correct face
        return hit.getType() == HitResult.Type.BLOCK &&
                hit.getBlockPos().equals(blockPos) &&
                hit.getSide() == face;
    }

    /**
     * Calculates an offset point on a block face
     */
    private static Vec3d getOffsetPoint(Vec3d faceCenter, Direction face, double offsetX, double offsetY) {
        // Get orthogonal vectors for the face
        Vec3d xVec, yVec;

        // Different offset vectors depending on which face we're dealing with
        switch (face.getAxis()) {
            case X:
                xVec = new Vec3d(0, 1, 0);
                yVec = new Vec3d(0, 0, 1);
                break;
            case Y:
                xVec = new Vec3d(1, 0, 0);
                yVec = new Vec3d(0, 0, 1);
                break;
            case Z:
                xVec = new Vec3d(1, 0, 0);
                yVec = new Vec3d(0, 1, 0);
                break;
            default:
                xVec = new Vec3d(0, 1, 0);
                yVec = new Vec3d(0, 0, 1);
        }

        // Apply the offsets
        return faceCenter.add(
                xVec.multiply(offsetX).add(yVec.multiply(offsetY))
        );
    }

    /**
     * Gets an offset vector perpendicular to the given direction
     */
    private static Vec3d getOrthogonalOffset(Direction dir, double amount) {
        return switch (dir.getAxis()) {
            case X -> new Vec3d(0, amount, 0);
            case Y -> new Vec3d(amount, 0, 0);
            case Z -> new Vec3d(0, amount, 0);
        };
    }

    /**
     * Gets another offset vector perpendicular to both the direction and the first orthogonal
     */
    private static Vec3d getSecondOrthogonalOffset(Direction dir, double amount) {
        return switch (dir.getAxis()) {
            case X -> new Vec3d(0, 0, amount);
            case Y -> new Vec3d(0, 0, amount);
            case Z -> new Vec3d(amount, 0, 0);
        };
    }

    /**
     * Performs a raytrace from current look direction to find what block/face would be placed on
     *
     * @param maxDistance Maximum distance to check
     * @return The hit result, or null if no valid placement spot was found
     */
    public static BlockHitResult rayTraceBlockPlacement(double maxDistance) {
        if (mc.player == null || mc.world == null) {
            return null;
        }

        Vec3d eyesPos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        Vec3d endPos = eyesPos.add(lookVec.multiply(maxDistance));

        RaycastContext context = new RaycastContext(
                eyesPos,
                endPos,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );

        BlockHitResult hit = mc.world.raycast(context);

        // Only return if we hit a block
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit;
        }

        return null;
    }

    /**
     * Calculates the rotations needed to look at a position
     *
     * @param position The target position
     * @return Array with [yaw, pitch]
     */
    public static float[] calculateLookAt(Vec3d position) {
        if (mc.player == null) return new float[]{0, 0};

        Vec3d eyePos = mc.player.getEyePos();

        double diffX = position.x - eyePos.x;
        double diffY = position.y - eyePos.y;
        double diffZ = position.z - eyePos.z;

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0f, 90.0f)};
    }

    /**
     * Checks if a block can be placed at the given position
     *
     * @param pos The position to check
     * @return true if a block can be placed
     */
    public static boolean canPlaceBlockAt(BlockPos pos) {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        // Check if position is air or replaceable
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isAir() && !state.isReplaceable()) {
            return false;
        }

        // Check if at least one adjacent block is solid to place against
        for (Direction dir : Direction.values()) {
            BlockPos adjacent = pos.offset(dir);
            BlockState adjacentState = mc.world.getBlockState(adjacent);

            if (!adjacentState.isAir() && adjacentState.isSolid()) {
                // Check if we can reach this face
                if (findBestBlockPlacement(pos, false, 4.5).isPresent()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Calculate the most legit rotation to look at a block for placement
     * This creates slightly offset rotations that look more human-like
     *
     * @param targetPos The position to place at
     * @return Array with [yaw, pitch] slightly randomized for legitimacy
     */
    public static float[] calculateLegitRotationForPlacement(BlockPos targetPos) {
        if (mc.player == null) return new float[]{0, 0};

        // Get a valid placement if possible
        Optional<BlockPlacementInfo> placement = findBestBlockPlacement(targetPos, false, 4.5);
        if (placement.isPresent()) {
            return placement.get().getRotations();
        }

        // Fallback to looking at the center of the block
        Vec3d lookPos = Vec3d.ofCenter(targetPos);
        float[] rotations = calculateLookAt(lookPos);

        // Add slight randomization for more human-like rotations
        rotations[0] += (Math.random() - 0.5) * 2.0f; // +/- 1 degree yaw
        rotations[1] += (Math.random() - 0.5) * 1.0f; // +/- 0.5 degree pitch

        return new float[]{
                MathHelper.wrapDegrees(rotations[0]),
                MathHelper.clamp(rotations[1], -90.0f, 90.0f)
        };
    }
}