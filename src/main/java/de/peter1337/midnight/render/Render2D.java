package de.peter1337.midnight.render;

import de.peter1337.midnight.manager.ShaderManager;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;

public class Render2D {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private final ShaderManager shaderManager;
    private final List<RenderShape> shapes;
    private RenderShape clipBounds;

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

    public void setClipBounds(RenderShape clipBounds) {
        this.clipBounds = clipBounds;
    }

    public RenderShape createRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        RenderShape shape = new RenderShape.Builder(x, y, width, height)
                .radius(radius)
                .fillColor(color)
                .build();
        shapes.add(shape);
        return shape;
    }

    public RenderShape createCircle(float x, float y, float radius, Color color) {
        RenderShape shape = new RenderShape.Builder(x - radius, y - radius, radius * 2, radius * 2)
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

        for (RenderShape shape : shapes) {
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
            RenderShape shape = shapes.get(i);
            if (shape.handleMouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean handleMouseDragged(double mouseX, double mouseY) {
        for (RenderShape shape : shapes) {
            if (shape.handleMouseDragged(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    public void handleMouseReleased() {
        for (RenderShape shape : shapes) {
            shape.handleMouseReleased();
        }
    }

    public static class RenderShape {
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

        private RenderShape parent;
        private final List<RenderShape> children;
        private float relativeX;
        private float relativeY;

        private RenderShape(Builder builder) {
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

        public void attachTo(RenderShape parent, float relativeX, float relativeY) {
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
            for (RenderShape child : children) {
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
        public List<RenderShape> getChildren() { return children; }
        public RenderShape getParent() { return parent; }

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

            public Builder fillColor(Color fillColor) {
                this.fillColor = fillColor;
                return this;
            }

            public Builder outline(Color color, float width) {
                this.outlineColor = color;
                this.outlineWidth = width;
                return this;
            }


            public RenderShape build() {
                return new RenderShape(this);
            }
        }
    }
}