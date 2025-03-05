package de.peter1337.midnight.modules.render;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;

public class ESP extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean active = false;

    // Settings for different entity types
    private final Setting<Boolean> glowPlayers = register(
            new Setting<>("Players", Boolean.TRUE, "Glow effect for player entities")
    );

    private final Setting<Boolean> glowHostileMobs = register(
            new Setting<>("Mobs", Boolean.FALSE, "Glow effect for hostile mobs")
    );

    private final Setting<Boolean> glowPassiveMobs = register(
            new Setting<>("Animals", Boolean.FALSE, "Glow effect for passive animals")
    );

    // New method to check if a specific entity should glow
    public boolean shouldEntityGlow(Entity entity) {
        // Always skip the local player
        if (entity == mc.player) {
            return false;
        }

        // Check entity type against settings
        return (glowPlayers.getValue() && entity instanceof PlayerEntity) ||
                (glowHostileMobs.getValue() && entity instanceof MobEntity && !(entity instanceof AnimalEntity)) ||
                (glowPassiveMobs.getValue() && entity instanceof AnimalEntity);
    }

    public ESP() {
        super("ESP", "Zeigt Entity-Hitboxes mittels selektiven Glow", Category.RENDER, "j");
    }

    @Override
    public void onEnable() {
        active = true;
        Midnight.LOGGER.info("ESP on");
    }

    @Override
    public void onDisable() {
        active = false;
        Midnight.LOGGER.info("ESP off");
    }

    @Override
    public void onUpdate() {
        // This method is now mostly a placeholder
        // The actual glowing is controlled by the EntityGlowingMixin
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}