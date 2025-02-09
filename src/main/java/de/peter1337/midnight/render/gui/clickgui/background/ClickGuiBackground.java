package de.peter1337.midnight.render.gui.clickgui.background;

import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.shape.Shape;
import java.awt.Color;

public class ClickGuiBackground {
    private final Shape background;
    private final Shape overlay;
    private static final float PANEL_WIDTH = 400f;
    private static final float PANEL_HEIGHT = 200f;
    private static final float PANEL_RADIUS = 15f;
    private static final Color PANEL_COLOR = new Color(25, 25, 45, 255);
    private static final Color OVERLAY_COLOR = new Color(0, 0, 0, 120); // Adjustable transparency

    public ClickGuiBackground(Render2D render2D, float screenWidth, float screenHeight) {
        // Create full-screen overlay first (it will render behind the panel)
        overlay = render2D.createRoundedRect(
                0, 0, screenWidth, screenHeight, 0, OVERLAY_COLOR
        );

        // Create main panel
        float panelX = (screenWidth - PANEL_WIDTH) / 2f;
        float panelY = (screenHeight - PANEL_HEIGHT) / 2f;

        background = render2D.createRoundedRect(
                panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, PANEL_RADIUS, PANEL_COLOR
        );
        background.setDraggable(true);
    }

    public Shape getBackground() {
        return background;
    }

    public Shape getOverlay() {
        return overlay;
    }


}