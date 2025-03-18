package de.peter1337.midnight.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Utility class for raytrace operations
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
}