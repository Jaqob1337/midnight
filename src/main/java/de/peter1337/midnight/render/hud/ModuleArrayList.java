package de.peter1337.midnight.render.hud;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.manager.ModuleVisibilityManager;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.render.font.CustomFontRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleArrayList {

    private static final int RIGHT_MARGIN = 8;
    private static final int TOP_MARGIN = 2;
    // Desired font size for the module array list.
    private static final float FONT_SIZE = 12f;

    public static void render(MatrixStack matrices) {
        MinecraftClient mc = MinecraftClient.getInstance();
        CustomFontRenderer fontRenderer = CustomFontRenderer.getInstanceForSize(FONT_SIZE);
        if (fontRenderer == null) {
            return;
        }

        int screenWidth = mc.getWindow().getScaledWidth();

        // Filter out modules that shouldn't be shown in the array list using the visibility manager
        // Sort by name length in descending order
        List<Module> enabledModules = ModuleManager.getModules().stream()
                .filter(Module::isEnabled)
                .filter(module -> !ModuleVisibilityManager.isHidden(module))
                .sorted(Comparator.comparingInt((Module module) -> module.getName().length()).reversed())
                .collect(Collectors.toList());

        int y = TOP_MARGIN;
        for (Module module : enabledModules) {
            String text = module.getName();
            int textWidth = fontRenderer.getStringWidth(text);
            int x = screenWidth - textWidth - RIGHT_MARGIN;
            fontRenderer.drawStringWithShadow(matrices, text, x, y, 0xFFFFFFFF, 0x55000000);
            y += fontRenderer.getFontHeight() + 2;
        }
    }
}