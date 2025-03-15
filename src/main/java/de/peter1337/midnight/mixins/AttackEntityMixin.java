package de.peter1337.midnight.mixins;

import de.peter1337.midnight.handler.RotationHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Alternate approach using a more specific target
 */
@Mixin(ClientPlayerInteractionManager.class)
public class AttackEntityMixin {
    private float originalYaw;
    private float originalPitch;
    private float originalHeadYaw;
    private float originalBodyYaw;
    private boolean rotationsChanged = false;

    @Inject(
            method = "attackEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/packet/c2s/play/PlayerInteractEntityC2SPacket;attack(Lnet/minecraft/entity/Entity;Z)Lnet/minecraft/network/packet/c2s/play/PlayerInteractEntityC2SPacket;"
            )
    )
    private void beforeAttackPacket(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (player != null && RotationHandler.isRotationActive() && !RotationHandler.isRotatingClient()) {
            // Store original values
            originalYaw = player.getYaw();
            originalPitch = player.getPitch();
            originalHeadYaw = player.headYaw;
            originalBodyYaw = player.bodyYaw;

            // Apply server rotations before attack packet is created
            player.setYaw(RotationHandler.getServerYaw());
            player.setPitch(RotationHandler.getServerPitch());
            player.headYaw = RotationHandler.getServerYaw();

            if (RotationHandler.isBodyRotation()) {
                player.bodyYaw = RotationHandler.getServerYaw();
            }

            rotationsChanged = true;
        }
    }

    @Inject(
            method = "attackEntity",
            at = @At("RETURN")
    )
    private void afterAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (player != null && rotationsChanged) {
            // Restore original values
            player.setYaw(originalYaw);
            player.setPitch(originalPitch);
            player.headYaw = originalHeadYaw;
            player.bodyYaw = originalBodyYaw;

            rotationsChanged = false;
        }
    }
}