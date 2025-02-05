package de.peter1337.midnight.render.shape;

import net.minecraft.util.math.MathHelper;

public class ShapeUtils {
    /**
     * Generates vertex positions for a filled rounded rectangle.
     * The shape is rendered as a triangle fan (first vertex is the center,
     * followed by perimeter vertices computed from the corner arcs).
     *
     * @param x                 Top-left x coordinate.
     * @param y                 Top-left y coordinate.
     * @param width             Rectangle width.
     * @param height            Rectangle height.
     * @param radius            Desired corner radius (clamped to half the width/height).
     * @param segmentsPerCorner Number of segments to approximate each corner.
     * @return A float array containing vertex positions (x, y, z) in triangle fan order.
     */
    public static float[] generateRoundedRectVertices(int x, int y, int width, int height, int radius, int segmentsPerCorner) {
        // Clamp the radius.
        radius = Math.min(radius, Math.min(width, height) / 2);
        // Compute the center of the rectangle.
        float cx = x + width / 2f;
        float cy = y + height / 2f;
        // Total number of perimeter vertices: 4 * (segmentsPerCorner + 1)
        int perimeterCount = 4 * (segmentsPerCorner + 1);
        // Total vertices: center + perimeter vertices.
        float[] vertices = new float[(perimeterCount + 1) * 3];
        int index = 0;
        // Center vertex.
        vertices[index++] = cx;
        vertices[index++] = cy;
        vertices[index++] = 0;

        // Top-left arc: center (x+radius, y+radius), angles 180° to 270°.
        for (int i = 0; i <= segmentsPerCorner; i++) {
            float t = i / (float) segmentsPerCorner;
            float angle = MathHelper.lerp(t, (float) Math.toRadians(180), (float) Math.toRadians(270));
            float vx = (x + radius) + MathHelper.cos(angle) * radius;
            float vy = (y + radius) + MathHelper.sin(angle) * radius;
            vertices[index++] = vx;
            vertices[index++] = vy;
            vertices[index++] = 0;
        }
        // Top-right arc: center (x+width-radius, y+radius), angles 270° to 360°.
        for (int i = 0; i <= segmentsPerCorner; i++) {
            float t = i / (float) segmentsPerCorner;
            float angle = MathHelper.lerp(t, (float) Math.toRadians(270), (float) Math.toRadians(360));
            float vx = (x + width - radius) + MathHelper.cos(angle) * radius;
            float vy = (y + radius) + MathHelper.sin(angle) * radius;
            vertices[index++] = vx;
            vertices[index++] = vy;
            vertices[index++] = 0;
        }
        // Bottom-right arc: center (x+width-radius, y+height-radius), angles 0° to 90°.
        for (int i = 0; i <= segmentsPerCorner; i++) {
            float t = i / (float) segmentsPerCorner;
            float angle = MathHelper.lerp(t, (float) Math.toRadians(0), (float) Math.toRadians(90));
            float vx = (x + width - radius) + MathHelper.cos(angle) * radius;
            float vy = (y + height - radius) + MathHelper.sin(angle) * radius;
            vertices[index++] = vx;
            vertices[index++] = vy;
            vertices[index++] = 0;
        }
        // Bottom-left arc: center (x+radius, y+height-radius), angles 90° to 180°.
        for (int i = 0; i <= segmentsPerCorner; i++) {
            float t = i / (float) segmentsPerCorner;
            float angle = MathHelper.lerp(t, (float) Math.toRadians(90), (float) Math.toRadians(180));
            float vx = (x + radius) + MathHelper.cos(angle) * radius;
            float vy = (y + height - radius) + MathHelper.sin(angle) * radius;
            vertices[index++] = vx;
            vertices[index++] = vy;
            vertices[index++] = 0;
        }
        return vertices;
    }
}
