package de.peter1337.midnight.render.hud;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.render.font.CustomFontRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;
import java.util.stream.Collectors;

public class ModuleArrayList {

    private static final int RIGHT_MARGIN = 8;
    private static final int Y_POSITION = 2;

    public static void render(MatrixStack matrices) {
        MinecraftClient mc = MinecraftClient.getInstance();
        CustomFontRenderer fontRenderer = CustomFontRenderer.getInstance();
        if (fontRenderer == null) {
            return;
        }

        // Get the screen width using the window's getScaledWidth() method.
        int screenWidth = mc.getWindow().getScaledWidth();

        // Retrieve enabled modules.
        List<Module> enabledModules = ModuleManager.getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());

        int y = Y_POSITION;
        for (Module module : enabledModules) {
            String text = module.getName();
            int textWidth = fontRenderer.getStringWidth(text);

            // Calculate x so that the right edge of the text sits RIGHT_MARGIN pixels from the screen edge.
            int x = screenWidth - textWidth - RIGHT_MARGIN;

            fontRenderer.drawStringWithShadow(matrices, text, x, y, 0xFFFFFFFF, 0x55000000);
            y += fontRenderer.getFontHeight() + 2;
        }
    }
}
