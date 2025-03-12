package de.peter1337.midnight.mixins;

import de.peter1337.midnight.manager.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class PlayerJoinMixin {

    /**
     * Inject at the onGameJoin method to load the default config when the player joins a world
     */
    @Inject(method = "onGameJoin", at = @At("RETURN"))
    private void onGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        // Load the default config when the player joins a world
        if (!ConfigManager.isConfigLoaded()) {
            ConfigManager.loadDefaultConfig();
        }
    }
}