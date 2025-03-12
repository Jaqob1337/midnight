package de.peter1337.midnight.modules.render;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.render.screens.clickgui.ClickGuiScreen;
import net.minecraft.client.MinecraftClient;

public class ClickGuiModule extends Module {

    // Setting to toggle position saving
    private final Setting<Boolean> savePosition = register(
            new Setting<>("SavePosition", Boolean.TRUE, "Saves and loads the ClickGUI panel position")
    );

    public ClickGuiModule() {
        super("ClickGUI", "Opens the client ClickGUI", Category.RENDER, "rshift");
    }

    /**
     * Check if position saving is enabled
     * @return true if position saving is enabled
     */
    public boolean isSavePositionEnabled() {
        return savePosition.getValue();
    }

    @Override
    public void onEnable() {
        // Open the GUI; do not immediately toggle off.
        MinecraftClient.getInstance().setScreen(new ClickGuiScreen());
    }
}