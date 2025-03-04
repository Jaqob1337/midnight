package de.peter1337.midnight.modules.render;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;

public class ESP extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean active = false;

    public ESP() {
        super("ESP", "Zeigt Entity-Hitboxes mittels Glow", Category.RENDER, "j");
    }
    // Setting that prevents resetting module button positions when enabled.
    private final Setting<Boolean> disableResetPosition = register(
            new Setting<>("DisableResetPosition", Boolean.FALSE, "Prevents module button positions from resetting when opening the ClickGUI")
    );

    private final Setting<String> sprintMode = register(
            new Setting<>("SprintMode", "Default", Arrays.asList("Default", "Hold", "Toggle", "Toggle2", "Toggle4"), "Select sprint mode")
    );

    @Override
    public void onEnable() {
        active = true;
        updateGlow(true);
        Midnight.LOGGER.info("ESP on");
    }

    @Override
    public void onDisable() {
        active = false;
        updateGlow(false);
        Midnight.LOGGER.info("ESP off");
    }

    @Override
    public void onUpdate() {
        if (mc.world != null) {
            mc.world.getEntities().forEach(entity -> {
                if (entity != mc.player) {
                    // Setze den Glow immer entsprechend dem aktuellen ESP-Zustand
                    entity.setGlowing(active);
                }
            });
        }
    }

    private void updateGlow(boolean glow) {
        if (mc.world == null) {
            return;
        }
        mc.world.getEntities().forEach(entity -> {
            if (entity != mc.player) {
                entity.setGlowing(glow);
            }
        });
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
