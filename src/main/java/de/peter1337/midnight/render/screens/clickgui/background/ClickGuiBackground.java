package de.peter1337.midnight.render.screens.clickgui.background;

import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.render.Render2D.RenderShape;
import java.awt.Color;

public class ClickGuiBackground {
    private final RenderShape overlay;
    private final RenderShape shadow;
    private final RenderShape background;
    private final RenderShape moduleSection;
    private final RenderShape imagePanel;

    private static final float PANEL_WIDTH = 380f;
    private static final float PANEL_HEIGHT = 200f;
    private static final float PANEL_RADIUS = 15f;
    private static final float MODULE_SECTION_MARGIN = -30f;
    private static final float MODULE_SECTION_RIGHT_MARGIN = 10f;
    private static final float MODULE_SECTION_TOP_MARGIN = 10f;
    private static final float MODULE_SECTION_RADIUS = 15f;

    // Image panel settings
    private static final float IMAGE_PANEL_WIDTH = 150f;
    private static final float IMAGE_PANEL_HEIGHT = 150f;
    private static final float IMAGE_PANEL_MARGIN = 15f;
    private static final float IMAGE_PANEL_RADIUS = 10f;

    // Shadow settings
    private static final float SHADOW_EXPAND = 0.9f;

    // Colors
    private static final Color PANEL_COLOR = new Color(25, 25, 45, 255);
    private static final Color MODULE_SECTION_COLOR = new Color(30, 30, 50, 255);
    private static final Color IMAGE_PANEL_COLOR = new Color(35, 35, 55, 255);
    private static final Color OVERLAY_COLOR = new Color(0, 0, 0, 120);
    private static final Color SHADOW_COLOR = new Color(25, 25, 45, 255);

    public ClickGuiBackground(Render2D render2D, float screenWidth, float screenHeight) {
        // Calculate panel position
        float panelX = (screenWidth - PANEL_WIDTH) / 2f;
        float panelY = (screenHeight - PANEL_HEIGHT) / 2f;

        // Create full-screen overlay first
        overlay = render2D.createRoundedRect(
                0, 0, screenWidth, screenHeight, 0, OVERLAY_COLOR
        );
        overlay.setUseCombinedClip(false);

        // Create the shadow shape with identical properties except size and color
        shadow = render2D.createRoundedRect(
                panelX - SHADOW_EXPAND/2,
                panelY - SHADOW_EXPAND/2,
                PANEL_WIDTH + SHADOW_EXPAND,
                PANEL_HEIGHT + SHADOW_EXPAND,
                PANEL_RADIUS,
                SHADOW_COLOR
        );
        shadow.setUseCombinedClip(false);
        render2D.markAsShadow(shadow);

        // Create main panel by "cloning" the module section's creation style
        background = render2D.createRoundedRect(
                panelX,
                panelY,
                PANEL_WIDTH,
                PANEL_HEIGHT,
                PANEL_RADIUS,
                PANEL_COLOR
        );
        background.setDraggable(true);
        background.setUseCombinedClip(false);

        // Create module section
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

        // Create image panel - placed to the right of the module section with some margin
        float imagePanelX = moduleSectionX + moduleSectionWidth + IMAGE_PANEL_MARGIN;
        float imagePanelY = panelY + (PANEL_HEIGHT - IMAGE_PANEL_HEIGHT) / 2;

        imagePanel = render2D.createRoundedRect(
                imagePanelX,
                imagePanelY,
                IMAGE_PANEL_WIDTH,
                IMAGE_PANEL_HEIGHT,
                IMAGE_PANEL_RADIUS,
                IMAGE_PANEL_COLOR
        );

        // Important: attach the module section first, then the background will inherit its properties
        moduleSection.attachTo(background, PANEL_WIDTH * 0.3f + MODULE_SECTION_MARGIN, MODULE_SECTION_TOP_MARGIN);
        moduleSection.setUseCombinedClip(false);

        // Attach the image panel to the background
        imagePanel.attachTo(background,
                moduleSectionX + moduleSectionWidth + IMAGE_PANEL_MARGIN - panelX,
                (PANEL_HEIGHT - IMAGE_PANEL_HEIGHT) / 2);
        imagePanel.setUseCombinedClip(false);
    }

    /**
     * Updates the shadow position to match the current background position.
     * Call this method before rendering shapes.
     */
    public void updateShadowPosition() {
        shadow.setPosition(
                background.getX() - SHADOW_EXPAND/2,
                background.getY() - SHADOW_EXPAND/2
        );
    }

    public RenderShape getBackground() {
        return background;
    }

    public RenderShape getModuleSection() {
        return moduleSection;
    }

    public RenderShape getImagePanel() {
        return imagePanel;
    }

    public RenderShape getOverlay() {
        return overlay;
    }

    public RenderShape getShadow() {
        return shadow;
    }
}