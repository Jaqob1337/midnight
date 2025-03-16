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
    // Main clipping shape (e.g. main panel)
    private RenderShape clipBounds;
    // Secondary clipping shape (e.g. module section)
    private RenderShape moduleClipBounds;

    // Special shape reference for shadow identification
    private RenderShape shadowShape = null;

    public Render2D() {
        this.shaderManager = new ShaderManager();
        this.shapes = new ArrayList<>();
        this.clipBounds = null;
        this.moduleClipBounds = null;
    }

    public void init(int width, int height) {
        shaderManager.init(width, height);
    }

    public void cleanup() {
        shaderManager.cleanup();
    }

    public void setMainClip(RenderShape clipBounds) {
        this.clipBounds = clipBounds;
    }

    public void setModuleClip(RenderShape moduleClipBounds) {
        this.moduleClipBounds = moduleClipBounds;
    }

    /**
     * Mark a shape as a shadow for special rendering
     */
    public void markAsShadow(RenderShape shape) {
        this.shadowShape = shape;
    }

    // Computes and sets the combined (intersected) clipping region.
    public void setCombinedClipBounds(RenderShape shape1, RenderShape shape2) {
        float x1 = Math.max(shape1.getX(), shape2.getX());
        float y1 = Math.max(shape1.getY(), shape2.getY());
        float x2 = Math.min(shape1.getX() + shape1.getWidth(), shape2.getX() + shape2.getWidth());
        float y2 = Math.min(shape1.getY() + shape1.getHeight(), shape2.getY() + shape2.getHeight());
        if (x2 < x1 || y2 < y1) {
            shaderManager.setClipBounds(0, 0, 0, 0, 0);
        } else {
            float width = x2 - x1;
            float height = y2 - y1;
            float radius = Math.min(shape1.getRadius(), shape2.getRadius());
            shaderManager.setClipBounds(x1, y1, width, height, radius);
        }
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

    /**
     * Render each shape using specialized rendering order and clipping for shadows.
     */
    public void renderShapes() {
        // PHASE 1: Render shadow first, if it exists, without any clipping
        if (shadowShape != null) {
            // Reset all clipping to ensure shadow renders in full
            shaderManager.resetClipBounds();

            // Draw the shadow shape
            shaderManager.drawShape(
                    shadowShape.getX(),
                    shadowShape.getY(),
                    shadowShape.getWidth(),
                    shadowShape.getHeight(),
                    shadowShape.getRadius(),
                    shadowShape.getSmoothing(),
                    shadowShape.getFillColor(),
                    shadowShape.getOutlineColor(),
                    shadowShape.getOutlineWidth()
            );
        }

        // PHASE 2: Render all other shapes with normal clipping
        for (RenderShape shape : shapes) {
            // Skip the shadow shape as we already rendered it
            if (shape == shadowShape) {
                continue;
            }

            if (shape.isUseCombinedClip()) {
                if (clipBounds != null && moduleClipBounds != null) {
                    setCombinedClipBounds(clipBounds, moduleClipBounds);
                } else if (clipBounds != null) {
                    shaderManager.setClipBounds(
                            clipBounds.getX(),
                            clipBounds.getY(),
                            clipBounds.getWidth(),
                            clipBounds.getHeight(),
                            clipBounds.getRadius()
                    );
                } else {
                    shaderManager.resetClipBounds();
                }
            } else {
                // Only clip to the main panel.
                if (clipBounds != null) {
                    shaderManager.setClipBounds(
                            clipBounds.getX(),
                            clipBounds.getY(),
                            clipBounds.getWidth(),
                            clipBounds.getHeight(),
                            clipBounds.getRadius()
                    );
                } else {
                    shaderManager.resetClipBounds();
                }
            }

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

        // Reset clipping state at the end
        shaderManager.resetClipBounds();
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

    // ========================================================================
    // Inner class representing a renderable shape.
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
        // New flag to choose between combined clipping and main-panel clipping.
        private boolean useCombinedClip = true;

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

        // Getters and setters.
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

        public boolean isUseCombinedClip() {
            return useCombinedClip;
        }

        public void setUseCombinedClip(boolean useCombinedClip) {
            this.useCombinedClip = useCombinedClip;
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