package de.peter1337.midnight.render.shape;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Shape {
    private float x;
    private float y;
    private final float width;
    private final float height;
    private final float radius;
    private final float smoothing;
    private Color fillColor;
    private final Color outlineColor;
    private final float outlineWidth;
    private boolean isDraggable;
    private boolean isDragging;
    private float dragOffsetX;
    private float dragOffsetY;

    private Shape parent;
    private final List<Shape> children;
    private float relativeX;
    private float relativeY;

    private Shape(Builder builder) {
        this.x = builder.x;
        this.y = builder.y;
        this.width = builder.width;
        this.height = builder.height;
        this.radius = builder.radius;
        this.smoothing = builder.smoothing;
        this.fillColor = builder.fillColor;
        this.outlineColor = builder.outlineColor;
        this.outlineWidth = builder.outlineWidth;
        this.isDraggable = builder.isDraggable;
        this.children = new ArrayList<>();
    }

    public void attachTo(Shape parent, float relativeX, float relativeY) {
        if (this.parent != null) {
            detachFromParent();
        }
        this.parent = parent;
        this.relativeX = relativeX;
        this.relativeY = relativeY;
        parent.children.add(this);
        updatePosition();
    }

    public void detachFromParent() {
        if (parent != null) {
            parent.children.remove(this);
            parent = null;
        }
    }

    private void updatePosition() {
        if (parent != null) {
            this.x = parent.getX() + relativeX;
            this.y = parent.getY() + relativeY;
            updateChildren(0, 0);
        }
    }

    private void updateChildren(float deltaX, float deltaY) {
        for (Shape child : children) {
            child.x += deltaX;
            child.y += deltaY;
            child.updateChildren(deltaX, deltaY);
        }
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height;
    }

    public boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        if (isDraggable && isHovered(mouseX, mouseY)) {
            isDragging = true;
            dragOffsetX = (float) mouseX - x;
            dragOffsetY = (float) mouseY - y;
            return true;
        }
        return false;
    }

    public boolean handleMouseDragged(double mouseX, double mouseY) {
        if (isDragging) {
            float newX = (float) mouseX - dragOffsetX;
            float newY = (float) mouseY - dragOffsetY;
            float deltaX = newX - x;
            float deltaY = newY - y;
            x = newX;
            y = newY;
            updateChildren(deltaX, deltaY);
            return true;
        }
        return false;
    }

    public void handleMouseReleased() {
        isDragging = false;
    }

    // Getters and Setters
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public float getRadius() { return radius; }
    public float getSmoothing() { return smoothing; }
    public Color getFillColor() { return fillColor; }
    public Color getOutlineColor() { return outlineColor; }
    public float getOutlineWidth() { return outlineWidth; }
    public boolean isDraggable() { return isDraggable; }
    public void setDraggable(boolean draggable) { this.isDraggable = draggable; }
    public List<Shape> getChildren() { return children; }
    public Shape getParent() { return parent; }

    public void setFillColor(Color color) {
        this.fillColor = color;
    }

    public void setPosition(float x, float y) {
        float deltaX = x - this.x;
        float deltaY = y - this.y;
        this.x = x;
        this.y = y;
        updateChildren(deltaX, deltaY);
    }

    public static class Builder {
        private float x;
        private float y;
        private float width;
        private float height;
        private float radius = 0;
        private float smoothing = 1.0f;
        private Color fillColor = Color.WHITE;
        private Color outlineColor = null;
        private float outlineWidth = 0;
        private boolean isDraggable = false;

        public Builder(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public Builder radius(float radius) {
            this.radius = radius;
            return this;
        }

        public Builder smoothing(float smoothing) {
            this.smoothing = smoothing;
            return this;
        }

        public Builder fillColor(Color fillColor) {
            this.fillColor = fillColor;
            return this;
        }

        public Builder outline(Color color, float width) {
            this.outlineColor = color;
            this.outlineWidth = width;
            return this;
        }

        public Builder draggable(boolean draggable) {
            this.isDraggable = draggable;
            return this;
        }

        public Shape build() {
            return new Shape(this);
        }
    }
}