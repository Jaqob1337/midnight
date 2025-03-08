package de.peter1337.midnight.mixins;

import de.peter1337.midnight.handler.RotationHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle body rotation rendering
 */
@Mixin(ClientPlayerEntity.class)
public class BodyRotationMixin {

    /**
     * Inject at the start of the movement tick method to update body rotations
     */
    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void onTickMovementStart(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;

        // Apply body rotations if in body rotation mode
        if (RotationHandler.isRotationActive() && RotationHandler.isBodyRotation()) {
            // Set only the basic rotation fields that are guaranteed to exist
            player.bodyYaw = RotationHandler.getServerYaw();
            player.headYaw = RotationHandler.getServerYaw();
            player.prevHeadYaw = RotationHandler.getServerYaw();
            player.prevBodyYaw = RotationHandler.getServerYaw();
        }
    }

    /**
     * Inject at multiple points during rendering to ensure body rotation stays consistent
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickStart(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;

        // Apply body rotations if in body rotation mode
        if (RotationHandler.isRotationActive() && RotationHandler.isBodyRotation()) {
            // Update during regular ticks too for consistency
            player.bodyYaw = RotationHandler.getServerYaw();
            player.headYaw = RotationHandler.getServerYaw();
        }
    }
}