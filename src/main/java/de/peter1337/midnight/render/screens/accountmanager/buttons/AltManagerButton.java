package de.peter1337.midnight.render.screens.accountmanager.buttons;

import de.peter1337.midnight.render.screens.accountmanager.AltManagerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class AltManagerButton {
    /**
     * Creates a ButtonWidget that opens the Alt Manager screen when pressed.
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @param width The button width.
     * @param height The button height.
     * @param parent The screen to return to when the Alt Manager is closed.
     * @return a ButtonWidget configured to open the Alt Manager.
     */
    public static ButtonWidget create(int x, int y, int width, int height, Screen parent) {
        return ButtonWidget.builder(Text.literal("Alt Manager"), button -> {
                    MinecraftClient.getInstance().setScreen(new AltManagerScreen(parent));
                })
                .dimensions(x, y, width, height)
                .build();
    }
}
