package de.peter1337.midnight.modules.render;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.manager.BindManager;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.utils.Category;
import net.minecraft.client.MinecraftClient;

public class ESP extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean active = false;

    public ESP() {
        super("ESP", "Shows entity hitboxes using the built-in glowing outline", Category.RENDER, "j");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        active = true;
        Midnight.LOGGER.info("ESP toggled ON");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        active = false;
        if (mc.world != null) {
            mc.world.getEntities().forEach(entity -> {
                if (entity != mc.player) {
                    entity.setGlowing(false);
                }
            });
        }
        Midnight.LOGGER.info("ESP toggled OFF");
    }

    @Override
    public void onUpdate() {
    }

    public boolean isEnabled() {
        return active;
    }
}
