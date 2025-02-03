package de.peter1337.midnight.render.gui;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.render.CustomFontRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;
import java.util.stream.Collectors;

public class ModuleArrayList {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public void render(MatrixStack matrices) {
        // Check if the window is initialized
        if (mc.getWindow() == null) {
            return;
        }

        int screenWidth = mc.getWindow().getScaledWidth();

        List<Module> enabledModules = ModuleManager.getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());

        // Sort modules alphabetically (adjust as desired)
        enabledModules.sort((m1, m2) -> m1.getName().compareToIgnoreCase(m2.getName()));

        CustomFontRenderer fontRenderer = CustomFontRenderer.getInstance();
        // Ensure the font renderer is initialized.
        if (fontRenderer == null) {
            return;
        }

        int y = 5;
        for (Module module : enabledModules) {
            String name = module.getName();
            int textWidth = fontRenderer.getStringWidth(name);
            int x = screenWidth - textWidth - 5;  // Right-align with 5 pixels padding.
            fontRenderer.drawString(matrices, name, x, y, 0xFFFFFFFF);
            y += fontRenderer.getFontHeight() + 2;
        }
    }
}
