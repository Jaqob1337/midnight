package de.peter1337.midnight.modules.render;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.render.screens.clickgui.ClickGuiScreen;
import net.minecraft.client.MinecraftClient;

public class ClickGuiModule extends Module {

    // New setting: if enabled, the ClickGUI panel position will be saved and restored.
    private final Setting<Boolean> positionSaving = register(
            new Setting<>("PositionSaving", Boolean.TRUE, "Saves and loads the ClickGUI panel position")
    );

    public ClickGuiModule() {
        super("ClickGUI", "Opens the client ClickGUI", Category.RENDER, "rshift");
    }


    public boolean isPositionSavingEnabled() {
        return positionSaving.getValue();
    }

    @Override
    public void onEnable() {
        // Open the GUI; do not immediately toggle off.
        MinecraftClient.getInstance().setScreen(new ClickGuiScreen());
    }
}
