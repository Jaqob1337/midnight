package de.peter1337.midnight.manager.mouse.drag;

import de.peter1337.midnight.render.gui.DraggableComponent;

import java.util.ArrayList;
import java.util.List;

public class DragManager {
    private final List<DraggableComponent> components;
    private DraggableComponent activeComponent;
    private int dragOffsetX;
    private int dragOffsetY;

    public DragManager() {
        this.components = new ArrayList<>();
    }

    public void registerComponent(DraggableComponent component) {
        components.add(component);
    }

    public void unregisterComponent(DraggableComponent component) {
        components.remove(component);
    }

    public boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        for (int i = components.size() - 1; i >= 0; i--) {
            DraggableComponent component = components.get(i);
            if (component.isDraggable() && component.isHovered(mouseX, mouseY)) {
                activeComponent = component;
                dragOffsetX = (int) mouseX - component.getX();
                dragOffsetY = (int) mouseY - component.getY();
                return true;
            }
        }
        return false;
    }

    public boolean handleMouseDragged(double mouseX, double mouseY) {
        if (activeComponent != null) {
            activeComponent.setPosition(
                    (int) mouseX - dragOffsetX,
                    (int) mouseY - dragOffsetY
            );
            return true;
        }
        return false;
    }

    public void handleMouseReleased() {
        activeComponent = null;
    }
}