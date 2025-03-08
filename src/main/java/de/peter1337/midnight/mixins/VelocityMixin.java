package de.peter1337.midnight.mixins;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.player.Velocity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class VelocityMixin {

    /**
     * Intercepts velocity packets and cancels them if needed
     */
    @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"), cancellable = true)
    private void onEntityVelocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        Velocity velocityModule = (Velocity) ModuleManager.getModule("Velocity");
        if (velocityModule == null || !velocityModule.isEnabled()) return;

        // Check if this velocity is for the local player
        if (packet.getEntityId() == mc.player.getId()) {
            // If module wants to cancel the packet, do so
            if (velocityModule.handleEntityVelocity(packet)) {
                ci.cancel();
            }
        }
    }

    /**
     * Intercepts explosion packets and cancels them if needed
     */
    @Inject(method = "onExplosion", at = @At("HEAD"), cancellable = true)
    private void onExplosion(ExplosionS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        Velocity velocityModule = (Velocity) ModuleManager.getModule("Velocity");
        if (velocityModule == null || !velocityModule.isEnabled()) return;

        // Let the module handle the explosion
        if (velocityModule.handleExplosion(packet)) {
            ci.cancel();
        }
    }
}