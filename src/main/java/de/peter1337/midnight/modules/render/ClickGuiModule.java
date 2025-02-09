package de.peter1337.midnight.modules.render;

import net.minecraft.client.MinecraftClient;
import de.peter1337.midnight.render.gui.clickgui.ClickGuiScreen;
import de.peter1337.midnight.modules.Category;
import de.peter1337.   midnight.modules.Module;


/**
 * A module that, when toggled, opens the ClickGUI.
 * It immediately disables itself so it is only used as a trigger.
 */
public class ClickGuiModule extends Module {

    public ClickGuiModule() {
        // Parameters: name, description, category (CLIENT for client-side), and bind ("NONE" here)
        super("ClickGUI", "Opens the client ClickGUI", Category.RENDER, "rshift");
    }

    @Override
    public void onEnable() {
        // Open the ClickGUI screen.
        MinecraftClient.getInstance().setScreen(new ClickGuiScreen());
        // Immediately toggle off to prevent the module from staying enabled.
        toggle();
    }
}
