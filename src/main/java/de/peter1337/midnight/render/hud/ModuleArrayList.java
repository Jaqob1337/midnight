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
    private static final int TOP_MARGIN = 2;
    // Desired font size for the module array list.
    private static final float FONT_SIZE = 11f;

    public static void render(MatrixStack matrices) {
        MinecraftClient mc = MinecraftClient.getInstance();
        CustomFontRenderer fontRenderer = CustomFontRenderer.getInstanceForSize(FONT_SIZE);
        if (fontRenderer == null) {
            return;
        }

        int screenWidth = mc.getWindow().getScaledWidth();

        List<Module> enabledModules = ModuleManager.getModules().stream()
                .filter(Module::isEnabled)
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
