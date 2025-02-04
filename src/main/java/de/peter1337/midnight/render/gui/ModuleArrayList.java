package de.peter1337.midnight.render.gui;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.render.CustomFontRenderer;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;
import java.util.stream.Collectors;

public class ModuleArrayList {

    // Define the right boundary for the module list.
    private static final int FIXED_RIGHT_MARGIN = 630;

    public void render(MatrixStack matrices) {
        CustomFontRenderer fontRenderer = CustomFontRenderer.getInstance();
        if (fontRenderer == null) {
            return;
        }
        // Get all enabled modules.
        List<Module> enabledModules = ModuleManager.getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());

        int y = 3; // starting y-coordinate

        for (Module module : enabledModules) {
            String name = module.getName();
            int textWidth = fontRenderer.getStringWidth(name);

            // Optional: If the text is wider than available space, clip it (with ellipsis) so it fits.
            if (textWidth > FIXED_RIGHT_MARGIN - 3) { // leaving a small left margin (3px)
                while (textWidth > FIXED_RIGHT_MARGIN - 3 && name.length() > 0) {
                    name = name.substring(0, name.length() - 1);
                    textWidth = fontRenderer.getStringWidth(name + "...");
                }
                name = name + "...";
                textWidth = fontRenderer.getStringWidth(name);
            }

            // Calculate the starting x coordinate so that the text's right edge is at FIXED_RIGHT_MARGIN.
            int x = FIXED_RIGHT_MARGIN - textWidth;

            fontRenderer.drawStringWithShadow(matrices, name, x, y, 0xFFFFFFFF, 0x55000000);
            y += fontRenderer.getFontHeight() + 2; // move down for the next module
        }
    }
}
