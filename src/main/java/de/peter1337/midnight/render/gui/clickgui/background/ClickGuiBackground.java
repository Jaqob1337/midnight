package de.peter1337.midnight.render.gui.clickgui.background;

import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.shape.Shape;
import java.awt.Color;

public class ClickGuiBackground {
    private final Shape background;
    private final Shape moduleSection;
    private final Shape overlay;

    private static final float PANEL_WIDTH = 380f;
    private static final float PANEL_HEIGHT = 200f;
    private static final float PANEL_RADIUS = 15f;
    private static final float MODULE_SECTION_MARGIN = -30f;
    private static final float MODULE_SECTION_RIGHT_MARGIN = 10f;
    private static final float MODULE_SECTION_TOP_MARGIN = 10f;
    private static final float MODULE_SECTION_RADIUS = 15f;

    // Colors
    private static final Color PANEL_COLOR = new Color(25, 25, 45, 255);
    private static final Color MODULE_SECTION_COLOR = new Color(30, 30, 50, 255);
    private static final Color OVERLAY_COLOR = new Color(0, 0, 0, 120);

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

        // Create module section background that extends to the right edge
        float moduleSectionX = panelX + PANEL_WIDTH * 0.3f + MODULE_SECTION_MARGIN;
        float moduleSectionWidth = PANEL_WIDTH * 0.7f - MODULE_SECTION_MARGIN - MODULE_SECTION_RIGHT_MARGIN;
        float moduleSectionHeight = PANEL_HEIGHT - (MODULE_SECTION_TOP_MARGIN * 2);

        moduleSection = render2D.createRoundedRect(
                moduleSectionX,
                panelY + MODULE_SECTION_TOP_MARGIN,
                moduleSectionWidth,
                moduleSectionHeight,
                MODULE_SECTION_RADIUS,
                MODULE_SECTION_COLOR
        );
        moduleSection.attachTo(background, PANEL_WIDTH * 0.3f + MODULE_SECTION_MARGIN, MODULE_SECTION_TOP_MARGIN);
    }

    public Shape getBackground() {
        return background;
    }

    public Shape getModuleSection() {
        return moduleSection;
    }

    public Shape getOverlay() {
        return overlay;
    }
}