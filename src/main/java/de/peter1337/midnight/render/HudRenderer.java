package de.peter1337.midnight.render;

import de.peter1337.midnight.manager.command.CommandSuggestionRenderer;
import de.peter1337.midnight.render.gui.ModuleArrayList;
import de.peter1337.midnight.render.gui.Watermark;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public class HudRenderer {
    // Use your existing ModuleArrayList implementation.
    private static final ModuleArrayList moduleArrayList = new ModuleArrayList();

    public static void init() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            HudRenderCallback.EVENT.register((DrawContext context, RenderTickCounter tickCounter) -> {
                // Render the module array list overlay.
                moduleArrayList.render(context.getMatrices());
                Watermark.render(context.getMatrices());
                // Render the command suggestion overlay.
                CommandSuggestionRenderer.render(context);
                // Optionally, render a watermark:
                // Watermark.render(context.getMatrices());
            });
        }
    }
}
