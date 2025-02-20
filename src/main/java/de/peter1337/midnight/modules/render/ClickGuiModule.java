package de.peter1337.midnight.modules.render;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.render.screens.clickgui.ClickGuiScreen;
import net.minecraft.client.MinecraftClient;

public class ClickGuiModule extends Module {

    // Setting that prevents resetting module button positions when enabled.
    private final Setting<Boolean> disableResetPosition = register(
            new Setting<>("DisableResetPosition", Boolean.FALSE, "Prevents module button positions from resetting when opening the ClickGUI")
    );

    public ClickGuiModule() {
        super("ClickGUI", "Opens the client ClickGUI", Category.RENDER, "rshift");
    }

    public boolean isResetDisabled() {
        return disableResetPosition.getValue();
    }

    @Override
    public void onEnable() {
        MinecraftClient.getInstance().setScreen(new ClickGuiScreen());
        // Immediately disable this module so it only acts as a trigger.
        toggle();
    }
}
