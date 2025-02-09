package de.peter1337.midnight.render.gui;

public class DraggableComponent {
    private int x;
    private int y;
    private final int width;
    private final int height;
    private boolean isDraggable;

    public DraggableComponent(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.isDraggable = true;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void setDraggable(boolean draggable) {
        this.isDraggable = draggable;
    }

    public boolean isDraggable() {
        return isDraggable;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height;
    }
}