// File: Render2D.java
package de.peter1337.midnight.render;

import de.peter1337.midnight.manager.shader.ShaderManager;
import de.peter1337.midnight.render.shape.Shape;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Render2D {
    private final ShaderManager shaderManager;
    private final List<Shape> shapes;
    private Shape clipBounds;

    public Render2D() {
        this.shaderManager = new ShaderManager();
        this.shapes = new ArrayList<>();
        this.clipBounds = null;
    }

    public void init(int width, int height) {
        shaderManager.init(width, height);
    }

    public void cleanup() {
        shaderManager.cleanup();
    }

    public void setClipBounds(Shape clipBounds) {
        this.clipBounds = clipBounds;
    }

    public Shape createRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        Shape shape = new Shape.Builder(x, y, width, height)
                .radius(radius)
                .fillColor(color)
                .build();
        shapes.add(shape);
        return shape;
    }

    public Shape createRoundedRectWithOutline(float x, float y, float width, float height, float radius,
                                              Color fillColor, Color outlineColor, float outlineWidth) {
        Shape shape = new Shape.Builder(x, y, width, height)
                .radius(radius)
                .fillColor(fillColor)
                .outline(outlineColor, outlineWidth)
                .build();
        shapes.add(shape);
        return shape;
    }

    public Shape createCircle(float x, float y, float radius, Color color) {
        Shape shape = new Shape.Builder(x - radius, y - radius, radius * 2, radius * 2)
                .radius(radius)
                .fillColor(color)
                .build();
        shapes.add(shape);
        return shape;
    }

    public void renderShapes() {
        if (clipBounds != null) {
            shaderManager.setClipBounds(
                    clipBounds.getX(),
                    clipBounds.getY(),
                    clipBounds.getWidth(),
                    clipBounds.getHeight(),
                    clipBounds.getRadius()
            );
        }

        for (Shape shape : shapes) {
            shaderManager.drawShape(
                    shape.getX(),
                    shape.getY(),
                    shape.getWidth(),
                    shape.getHeight(),
                    shape.getRadius(),
                    shape.getSmoothing(),
                    shape.getFillColor(),
                    shape.getOutlineColor(),
                    shape.getOutlineWidth()
            );
        }

        if (clipBounds != null) {
            shaderManager.resetClipBounds();
        }
    }

    public boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        for (int i = shapes.size() - 1; i >= 0; i--) {
            Shape shape = shapes.get(i);
            if (shape.handleMouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean handleMouseDragged(double mouseX, double mouseY) {
        for (Shape shape : shapes) {
            if (shape.handleMouseDragged(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    public void handleMouseReleased() {
        for (Shape shape : shapes) {
            shape.handleMouseReleased();
        }
    }
}