package de.peter1337.midnight.mixins;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.handler.RotationHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to control player model rotations when using the RotationHandler
 * Only applies in third-person view
 */
@Mixin(BipedEntityModel.class)
public class PlayerModelRotationMixin<T extends BipedEntityRenderState> {

    /**
     * Injects at the end of the setAngles method to override rotations if our handler is active
     * Only applies rotations in third-person view
     */
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at = @At("RETURN"))
    private void onSetAngles(T state, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();

            // Check if we're in first-person view - if so, don't apply rotations
            if (mc.options.getPerspective().isFirstPerson()) {
                return;
            }

            if (!RotationHandler.isRotationActive() ||
                    RotationHandler.isRotatingClient() ||
                    !(state instanceof PlayerEntityRenderState) ||
                    mc.player == null) {
                return;
            }

            // Cast to PlayerEntityRenderState
            PlayerEntityRenderState playerState = (PlayerEntityRenderState)state;

            // Check if this is for the local player (by comparing player ID)
            if (playerState.id == mc.player.getId()) {
                // Get the current model instance
                BipedEntityModel<T> model = (BipedEntityModel<T>)(Object)this;

                // Get the server-side rotations from our handler
                float serverYaw = RotationHandler.getServerYaw();
                float serverPitch = RotationHandler.getServerPitch();

                // Calculate the relative yaw for the head based on body rotation
                float bodyYawRadians = state.bodyYaw * ((float)Math.PI / 180F);
                float serverYawRadians = serverYaw * ((float)Math.PI / 180F);

                // Calculate the angle difference in radians
                float relativeYawRadians = serverYawRadians - bodyYawRadians;

                // Apply to head
                model.head.yaw = relativeYawRadians;
                model.head.pitch = serverPitch * ((float)Math.PI / 180F);

                // Hat copies the head rotation
                model.hat.copyTransform(model.head);

            }
        } catch (Exception e) {
            // Log any errors but don't crash the game
            Midnight.LOGGER.error("Error in PlayerModelRotationMixin: " + e.getMessage());
        }
    }
}