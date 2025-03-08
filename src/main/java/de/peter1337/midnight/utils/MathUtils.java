package de.peter1337.midnight.utils;

import net.minecraft.util.math.MathHelper;

/**
 * Utility class for various math operations
 */
public class MathUtils {

    /**
     * Interpolates linearly between two values
     *
     * @param start Start value
     * @param end End value
     * @param progress Progress (0.0 - 1.0)
     * @return Interpolated value
     */
    public static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    /**
     * Calculates the difference between two angles in degrees,
     * ensuring the result is the shortest path
     *
     * @param angle1 First angle in degrees
     * @param angle2 Second angle in degrees
     * @return The shortest difference between the angles
     */
    public static float angleDifference(float angle1, float angle2) {
        return MathHelper.wrapDegrees(angle2 - angle1);
    }

    /**
     * Calculates if the first angle is within a certain range of the second angle
     *
     * @param angle The angle to check
     * @param target The target angle
     * @param maxDifference The maximum allowed difference
     * @return True if angle is within range of target
     */
    public static boolean isAngleWithin(float angle, float target, float maxDifference) {
        return Math.abs(angleDifference(angle, target)) <= maxDifference;
    }

    /**
     * Clamps a value between a minimum and maximum
     *
     * @param value The value to clamp
     * @param min The minimum allowed value
     * @param max The maximum allowed value
     * @return The clamped value
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps a value between a minimum and maximum
     *
     * @param value The value to clamp
     * @param min The minimum allowed value
     * @param max The maximum allowed value
     * @return The clamped value
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Maps a value from one range to another
     *
     * @param value The value to map
     * @param oldMin The minimum of the original range
     * @param oldMax The maximum of the original range
     * @param newMin The minimum of the target range
     * @param newMax The maximum of the target range
     * @return The mapped value
     */
    public static float map(float value, float oldMin, float oldMax, float newMin, float newMax) {
        return newMin + (newMax - newMin) * ((value - oldMin) / (oldMax - oldMin));
    }
}