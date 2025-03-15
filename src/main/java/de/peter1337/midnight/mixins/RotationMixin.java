package de.peter1337.midnight.mixins;

import de.peter1337.midnight.handler.RotationHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A revised mixin that focuses on packet sending without affecting rendering
 */
@Mixin(ClientPlayerEntity.class)
public class RotationMixin {
    // Store the original rotations
    private float originalYaw;
    private float originalPitch;
    private float originalHeadYaw;
    private float originalBodyYaw;

    // Track if our rotations have been applied for packet sending
    private boolean rotationsAppliedForPacket = false;

    /**
     * Apply server-side rotations for packet sending, but store originals
     */
    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void beforeSendMovementPackets(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (RotationHandler.isRotationActive() && !RotationHandler.isRotatingClient()) {
            ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;

            // Store original values
            originalYaw = player.getYaw();
            originalPitch = player.getPitch();
            originalHeadYaw = player.headYaw;
            originalBodyYaw = player.bodyYaw;

            // Apply server-side rotations for packet
            player.setYaw(RotationHandler.getServerYaw());
            player.setPitch(RotationHandler.getServerPitch());
            player.headYaw = RotationHandler.getServerYaw();

            // If body rotation mode is active, also update body yaw
            if (RotationHandler.isBodyRotation()) {
                player.bodyYaw = RotationHandler.getServerYaw();
            }

            rotationsAppliedForPacket = true;

            // Set flag to indicate rotations are being used for movement
            RotationHandler.setUsingMoveFix(true);
        }
    }

    /**
     * Restore original rotations after packet sending
     */
    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void afterSendMovementPackets(CallbackInfo ci) {
        if (rotationsAppliedForPacket) {
            ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;

            // Restore original values
            player.setYaw(originalYaw);
            player.setPitch(originalPitch);
            player.headYaw = originalHeadYaw;
            player.bodyYaw = originalBodyYaw;

            rotationsAppliedForPacket = false;

            // Reset the flag
            RotationHandler.setUsingMoveFix(false);
        }
    }
}