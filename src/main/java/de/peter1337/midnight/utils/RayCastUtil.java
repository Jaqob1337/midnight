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
 * Utility class for raycasting operations
 */
public class RayCastUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

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
     * Finds the best block placement for a target position
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

            // If center didn't work or we want to check other positions too, try offsets
            if (validPlacements.isEmpty() || !prioritizeCenter) {
                // Try various offset combinations to find a valid placement
                double[] offsets = {0.3, 0.4, 0.45};

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
     * Checks if a placement point is valid (reachable and visible)
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

        return result.getType() == HitResult.Type.BLOCK &&
                result.getBlockPos().equals(againstPos) &&
                result.getSide() == face;
    }

    /**
     * Calculates an offset position on a block face
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

    // ---- Existing methods from RayCastUtil that are useful for Scaffold ----

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
     * Calculates rotations needed to look at a position
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
     * Checks if an entity is visible through raycasting
     */
    public static boolean canSeeEntity(Entity entity) {
        if (mc.player == null || mc.world == null || entity == null) return false;

        // Try multiple points on the entity's hitbox
        Box box = entity.getBoundingBox();
        Vec3d[] testPoints = {
                entity.getEyePos(),
                box.getCenter(),
                new Vec3d(box.minX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.minZ),
                new Vec3d(box.minX, box.maxY, box.minZ),
                new Vec3d(box.minX, box.minY, box.maxZ),
                new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.minX, box.maxY, box.maxZ),
                new Vec3d(box.maxX, box.minY, box.maxZ),
                new Vec3d(box.maxX, box.maxY, box.maxZ)
        };

        for (Vec3d point : testPoints) {
            if (canSeePosition(point)) {
                return true;
            }
        }

        return false;
    }
}