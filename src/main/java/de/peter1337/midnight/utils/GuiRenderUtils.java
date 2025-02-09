package de.peter1337.midnight.utils;

import org.lwjgl.opengl.GL30;

public class GuiRenderUtils {
    public static void setupBlending() {
        GL30.glEnable(GL30.GL_BLEND);
        GL30.glBlendFunc(GL30.GL_SRC_ALPHA, GL30.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void cleanupBlending() {
        GL30.glDisable(GL30.GL_BLEND);
    }
}