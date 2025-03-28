package de.peter1337.midnight.utils;

// --- Keep all existing imports ---
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
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;


public class RayCastUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /**
     * Checks if the player has a direct line of sight to a specific point,
     * ignoring entities but checking for block obstructions.
     *
     * @param pos The 3D world position to check visibility for.
     * @return True if there are no obstructing blocks between the player's eyes and the position.
     */
    public static boolean canSeePosition(Vec3d pos) { // Removed unused 'entity' parameter
        if (mc.player == null || mc.world == null || pos == null) return false;

        Vec3d eyePos = mc.player.getEyePos();
        World world = mc.world;

        // Create a raytrace context checking for block colliders
        RaycastContext context = new RaycastContext(
                eyePos,
                pos,
                RaycastContext.ShapeType.COLLIDER, // Use COLLIDER for block checks
                RaycastContext.FluidHandling.NONE,
                mc.player // The entity performing the raycast (ignored for collision)
        );

        // Perform the raycast
        BlockHitResult result = world.raycast(context);

        // If the raycast MISSES, it means there was no block obstruction.
        return result.getType() == HitResult.Type.MISS;

        // Alternate check (less clean): Check if hit is very close to target OR further away
        // return result.getType() == HitResult.Type.MISS ||
        //        (result.getType() == HitResult.Type.BLOCK && eyePos.squaredDistanceTo(result.getPos()) >= eyePos.squaredDistanceTo(pos) - 0.1);
    }


    /**
     * Checks if the player can see an entity from a specific hypothetical rotation.
     * Casts a ray and checks if it hits the entity's box before hitting a block.
     *
     * @param entity The target entity.
     * @param yaw The hypothetical yaw angle.
     * @param pitch The hypothetical pitch angle.
     * @param maxDistance The maximum distance to check.
     * @return True if the entity would be visible at the specified rotation within the distance.
     */
    public static boolean canSeeEntityFromRotation(Entity entity, float yaw, float pitch, double maxDistance) {
        if (mc.player == null || mc.world == null || entity == null) return false;

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = getVectorForRotation(pitch, yaw);
        Vec3d endPos = eyePos.add(lookVec.multiply(maxDistance));
        double maxDistSq = maxDistance * maxDistance; // Use squared distance

        // Raycast for blocks first
        RaycastContext blockContext = new RaycastContext(
                eyePos,
                endPos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );
        BlockHitResult blockHit = mc.world.raycast(blockContext);
        double blockHitDistSq = (blockHit.getType() == HitResult.Type.MISS) ? Double.MAX_VALUE : eyePos.squaredDistanceTo(blockHit.getPos());

        // Check distance limit based on block hit or max distance
        if (blockHitDistSq > maxDistSq) {
            blockHitDistSq = maxDistSq; // Don't check further than maxDistance
        }


        // Raycast against the entity's bounding box
        Box entityBox = entity.getBoundingBox().expand(0.05); // Slightly expand box for robustness
        Optional<Vec3d> entityHitOpt = entityBox.raycast(eyePos, endPos); // Use built-in Box raycast


        if (entityHitOpt.isPresent()) {
            double entityHitDistSq = eyePos.squaredDistanceTo(entityHitOpt.get());
            // Entity is visible if it's hit within max distance AND closer than any block hit
            return entityHitDistSq <= maxDistSq && entityHitDistSq < blockHitDistSq;
        }

        // No intersection with entity box along the ray
        return false;
    }


    // Alias for clarity if needed
    public static boolean isEntityInServerView(Entity entity, float serverYaw, float serverPitch, double maxDistance) {
        return canSeeEntityFromRotation(entity, serverYaw, serverPitch, maxDistance);
    }

    /**
     * Finds the closest visible point on an entity's hitbox by testing multiple points.
     * Improved for close-range targeting and critical points.
     *
     * @param entity The target entity.
     * @return The closest visible Vec3d point on the entity's hitbox, or null if none are visible.
     */
    public static Vec3d getVisiblePoint(Entity entity) {
        if (mc.player == null || mc.world == null || entity == null) return null;

        Box box = entity.getBoundingBox();
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d entityEyePos = entity.getEyePos(); // Cache entity eye pos

        List<Vec3d> pointsToCheck = new ArrayList<>();

        // Add priority points first
        pointsToCheck.add(entityEyePos); // Entity eyes
        pointsToCheck.add(box.getCenter());    // Center of bounding box

        // Add middle body point (better for sword hits)
        pointsToCheck.add(new Vec3d(box.getCenter().x, box.minY + (box.maxY - box.minY) * 0.5, box.getCenter().z));

        // Add upper chest point (good for sword critical hits)
        pointsToCheck.add(new Vec3d(box.getCenter().x, box.minY + (box.maxY - box.minY) * 0.8, box.getCenter().z));

        // Add corners
        pointsToCheck.add(new Vec3d(box.minX, box.minY, box.minZ));
        pointsToCheck.add(new Vec3d(box.maxX, box.minY, box.minZ));
        pointsToCheck.add(new Vec3d(box.minX, box.maxY, box.minZ));
        pointsToCheck.add(new Vec3d(box.minX, box.minY, box.maxZ));
        pointsToCheck.add(new Vec3d(box.maxX, box.maxY, box.minZ));
        pointsToCheck.add(new Vec3d(box.minX, box.maxY, box.maxZ));
        pointsToCheck.add(new Vec3d(box.maxX, box.minY, box.maxZ));
        pointsToCheck.add(new Vec3d(box.maxX, box.maxY, box.maxZ));

        // Add more hitbox test points
        float interval = 0.4f;
        for (float y = 0.2f; y <= 0.8; y += interval) {
            // Add horizontal ring of points at this height
            pointsToCheck.add(new Vec3d(box.minX, box.minY + (box.maxY - box.minY) * y, box.getCenter().z));
            pointsToCheck.add(new Vec3d(box.maxX, box.minY + (box.maxY - box.minY) * y, box.getCenter().z));
            pointsToCheck.add(new Vec3d(box.getCenter().x, box.minY + (box.maxY - box.minY) * y, box.minZ));
            pointsToCheck.add(new Vec3d(box.getCenter().x, box.minY + (box.maxY - box.minY) * y, box.maxZ));
        }

        // Check if we're very close to the entity
        boolean isClose = mc.player.squaredDistanceTo(entity) < 3.0;

        Vec3d bestPoint = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Vec3d point : pointsToCheck) {
            if (canSeePosition(point)) {
                double distSq = eyePos.squaredDistanceTo(point);

                // For close combat, prefer points requiring less head movement
                if (isClose) {
                    Vec3d lookVec = mc.player.getRotationVec(1.0f);
                    Vec3d toPoint = point.subtract(eyePos).normalize();
                    double dotProduct = lookVec.dotProduct(toPoint);

                    // Adjust distance by how much rotation would be needed
                    // Higher dot product = less rotation needed
                    distSq = distSq * (2.0 - dotProduct);
                }

                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestPoint = point;
                }
            }
        }

        // Special handling for very close combat
        if (bestPoint == null && isClose) {
            // If no visible points found and entity is very close,
            // try entity center with a slight y-offset as a last resort
            Vec3d centerFallback = box.getCenter().add(0, -0.2, 0);
            if (canSeePosition(centerFallback)) {
                return centerFallback;
            }
        }

        return bestPoint; // Returns null if no points were visible
    }

    /** Check if any significant part of the entity is visible. */
    public static boolean canSeeEntity(Entity entity) {
        return getVisiblePoint(entity) != null;
    }

    /** Generates a look vector from pitch and yaw angles. */
    public static Vec3d getVectorForRotation(float pitch, float yaw) {
        float radPitch = pitch * 0.017453292F; // pitch * PI / 180
        float radYaw = -yaw * 0.017453292F;    // -yaw * PI / 180

        float cosPitch = MathHelper.cos(radPitch);
        float sinPitch = MathHelper.sin(radPitch);
        float cosYaw = MathHelper.cos(radYaw);
        float sinYaw = MathHelper.sin(radYaw);

        return new Vec3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    /** Calculates rotations needed to look at a position from player eyes. */
    public static float[] calculateLookAt(Vec3d position) {
        if (mc.player == null || position == null) return new float[]{0, 0};

        Vec3d eyePos = mc.player.getEyePos();
        double deltaX = position.x - eyePos.x;
        double deltaY = position.y - eyePos.y; // Vertical difference
        double deltaZ = position.z - eyePos.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0f;
        // Use atan2 for pitch as well for better handling of straight up/down cases
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));

        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0f, 90.0f)};
    }


    // --- Block Placement Logic (Keep as is unless issues arise) ---
    // Includes: BlockPlacementInfo, findBestBlockPlacement, tryPlacementPoint,
    // canSeeBlockFace, getOffsetPoint, rayTraceBlockPlacement, canPlaceBlockAt,
    // calculateLegitRotationForPlacement

    public static class BlockPlacementInfo {
        // ... (Keep existing BlockPlacementInfo code) ...
        private final BlockPos targetPos;
        private final BlockPos placeAgainst;
        private final Direction placeDir;
        private final Vec3d hitVec;
        private final float[] rotations;
        private final double distFromCenter;
        private final double eyesDistance;

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

        // Getters...
        public BlockPos getTargetPos() { return targetPos; }
        public BlockPos getPlaceAgainst() { return placeAgainst; }
        public Direction getPlaceDir() { return placeDir; }
        public Vec3d getHitVec() { return hitVec; }
        public float[] getRotations() { return rotations; }
        public double getDistFromCenter() { return distFromCenter; }
        public double getEyesDistance() { return eyesDistance; }

        @Override
        public String toString() { /* Keep existing toString */ return ""; }
    }

    public static Optional<BlockPlacementInfo> findBestBlockPlacement(BlockPos pos, boolean strictCenter, double maxReach) {
        if (mc.player == null || mc.world == null) return Optional.empty();
        Vec3d eyesPos = mc.player.getEyePos();
        World world = mc.world;
        BlockState state = world.getBlockState(pos);
        if (!state.isAir() && !state.isReplaceable()) return Optional.empty();

        List<BlockPlacementInfo> validPlacements = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            BlockPos placeAgainst = pos.offset(dir);
            BlockState neighborState = world.getBlockState(placeAgainst);
            if (neighborState.isAir() || !neighborState.isSolid() || neighborState.getOutlineShape(world, placeAgainst).isEmpty()) continue;

            Direction placeDir = dir.getOpposite();
            Vec3d faceCenter = Vec3d.ofCenter(placeAgainst).add(Vec3d.of(placeDir.getVector()).multiply(0.5));

            // Test center first
            tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter, eyesPos, maxReach, validPlacements, 0, 0);

            // Test other points if needed (simplified loop)
            double[] offsets = {0.1, 0.25, 0.45, 0.49}; // Reduced number of offsets
            for (double offset : offsets) {
                // Horizontal/Vertical
                tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter, eyesPos, maxReach, validPlacements, offset, 0);
                tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter, eyesPos, maxReach, validPlacements, -offset, 0);
                tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter, eyesPos, maxReach, validPlacements, 0, offset);
                tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter, eyesPos, maxReach, validPlacements, 0, -offset);
                // Diagonals (optional, adds more checks)
                tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter, eyesPos, maxReach, validPlacements, offset, offset);
                tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter, eyesPos, maxReach, validPlacements, -offset, offset);
                tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter, eyesPos, maxReach, validPlacements, offset, -offset);
                tryPlacementPoint(pos, placeAgainst, placeDir, faceCenter, eyesPos, maxReach, validPlacements, -offset, -offset);
            }
        }

        if (validPlacements.isEmpty()) return Optional.empty();

        // Sorting based on strictCenter or balanced
        Comparator<BlockPlacementInfo> comparator = strictCenter ?
                Comparator.<BlockPlacementInfo, Double>comparing(BlockPlacementInfo::getDistFromCenter).thenComparingDouble(BlockPlacementInfo::getEyesDistance) :
                Comparator.comparingDouble(p -> p.getDistFromCenter() * 0.4 + p.getEyesDistance() * 0.6); // Example balance

        return validPlacements.stream().min(comparator);
    }

    private static void tryPlacementPoint(
            BlockPos pos, BlockPos placeAgainst, Direction placeDir, Vec3d faceCenter,
            Vec3d eyesPos, double maxReach, List<BlockPlacementInfo> validPlacements,
            double offsetX, double offsetY)
    {
        Vec3d hitVec = getOffsetPoint(faceCenter, placeDir, offsetX, offsetY);
        double distSq = eyesPos.squaredDistanceTo(hitVec); // Use squared distance
        if (distSq > maxReach * maxReach) return;

        if (!canSeeBlockFace(eyesPos, hitVec, placeAgainst, placeDir)) return;

        float[] rotations = calculateLookAt(hitVec);
        double centerDist = hitVec.distanceTo(faceCenter); // Keep linear distance for center comparison
        double eyeDist = Math.sqrt(distSq); // Store linear eye distance

        validPlacements.add(new BlockPlacementInfo(pos, placeAgainst, placeDir, hitVec, rotations, centerDist, eyeDist));
    }


    private static boolean canSeeBlockFace(Vec3d eyesPos, Vec3d hitVec, BlockPos blockPos, Direction face) {
        if (mc.world == null || mc.player == null) return false;
        RaycastContext context = new RaycastContext(eyesPos, hitVec, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        BlockHitResult hit = mc.world.raycast(context);
        // Check if hit is close enough to the intended hitVec AND hits the correct block/face
        return hit.getType() == HitResult.Type.BLOCK &&
                hit.getBlockPos().equals(blockPos) &&
                hit.getSide() == face &&
                hit.getPos().squaredDistanceTo(hitVec) < 0.01; // Ensure the raycast endpoint is very close to calculated hitVec
    }


    private static Vec3d getOffsetPoint(Vec3d faceCenter, Direction face, double offsetX, double offsetY) {
        // Determine basis vectors for the plane of the face
        Vec3d vecU, vecV;
        switch (face.getAxis()) {
            case X -> { // +/- X faces
                vecU = new Vec3d(0, 1, 0); // Up/Down offset
                vecV = new Vec3d(0, 0, 1); // Forward/Backward offset
            }
            case Y -> { // +/- Y faces (Top/Bottom)
                vecU = new Vec3d(1, 0, 0); // Left/Right offset
                vecV = new Vec3d(0, 0, 1); // Forward/Backward offset
            }
            case Z -> { // +/- Z faces
                vecU = new Vec3d(1, 0, 0); // Left/Right offset
                vecV = new Vec3d(0, 1, 0); // Up/Down offset
            }
            default -> throw new IllegalStateException("Unexpected axis: " + face.getAxis());
        }

        // Apply offsets along the basis vectors
        // Clamp offsets to prevent going outside the block face (-0.5 to 0.5 relative to center)
        offsetX = MathHelper.clamp(offsetX, -0.5, 0.5);
        offsetY = MathHelper.clamp(offsetY, -0.5, 0.5);

        return faceCenter.add(vecU.multiply(offsetX)).add(vecV.multiply(offsetY));
    }


    public static BlockHitResult rayTraceBlockPlacement(double maxDistance) {
        if (mc.player == null || mc.world == null) return null;
        Vec3d eyesPos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0f); // Use player's current look vec
        Vec3d endPos = eyesPos.add(lookVec.multiply(maxDistance));
        RaycastContext context = new RaycastContext(eyesPos, endPos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player);
        BlockHitResult hit = mc.world.raycast(context);
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }


    public static boolean canPlaceBlockAt(BlockPos pos) {
        if (mc.player == null || mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isAir() && !state.isReplaceable()) return false;
        // Check reachability implicitly via findBestBlockPlacement
        return findBestBlockPlacement(pos, false, 4.5).isPresent(); // Adjust reach if needed
    }


    public static float[] calculateLegitRotationForPlacement(BlockPos targetPos) {
        Optional<BlockPlacementInfo> placement = findBestBlockPlacement(targetPos, false, 4.5); // Adjust reach
        if (placement.isPresent()) {
            // Add slight randomization to the best found rotation
            float[] rotations = placement.get().getRotations().clone(); // Clone to avoid modifying original
            rotations[0] += (float)(Math.random() - 0.5) * 1.0f; // +/- 0.5 deg yaw
            rotations[1] += (float)(Math.random() - 0.5) * 0.5f; // +/- 0.25 deg pitch
            return new float[] {
                    MathHelper.wrapDegrees(rotations[0]),
                    MathHelper.clamp(rotations[1], -90.0f, 90.0f)
            };
        }
        // Fallback: Look at center with randomization
        Vec3d lookPos = Vec3d.ofCenter(targetPos);
        float[] rotations = calculateLookAt(lookPos);
        rotations[0] += (float)(Math.random() - 0.5) * 2.0f;
        rotations[1] += (float)(Math.random() - 0.5) * 1.0f;
        return new float[] {
                MathHelper.wrapDegrees(rotations[0]),
                MathHelper.clamp(rotations[1], -90.0f, 90.0f)
        };
    }


    // --- Removed unused helper methods getOrthogonalOffset / getSecondOrthogonalOffset ---

}