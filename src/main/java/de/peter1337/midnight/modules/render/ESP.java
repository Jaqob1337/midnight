package de.peter1337.midnight.modules.render;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.utils.Category;
import net.minecraft.client.MinecraftClient;

public class ESP extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    // Speichere den Zustand direkt in der Instanz (oder verwende super.isEnabled() falls vorhanden)
    private boolean active = false;

    public ESP() {
        super("ESP", "Zeigt Entity-Hitboxes mittels Glow", Category.RENDER, "j");
    }

    @Override
    public void onEnable() {
        active = true;
        // Direkt beim Aktivieren alle Entities außer dem Spieler glühen lassen
        updateGlow(true);
        Midnight.LOGGER.info("ESP on");
    }

    @Override
    public void onDisable() {
        active = false;
        // Direkt beim Deaktivieren wird der Glow von allen Entities entfernt
        updateGlow(false);
        Midnight.LOGGER.info("ESP off");
    }

    @Override
    public void onUpdate() {
        // Damit auch neu gespawnte Mobs den richtigen Zustand erhalten,
        // aktualisieren wir den Glow in jedem Tick
        if (mc.world != null) {
            mc.world.getEntities().forEach(entity -> {
                if (entity != mc.player) {
                    // Setze den Glow immer entsprechend dem aktuellen ESP-Zustand
                    entity.setGlowing(active);
                }
            });
        }
    }

    /**
     * Setzt oder entfernt den Glow von allen Entities (außer dem Spieler).
     *
     * @param glow true, wenn Glow aktiviert werden soll, false zum Entfernen
     */
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
