package de.peter1337.midnight.mixins;

import de.peter1337.midnight.handler.RotationHandler;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for LivingEntity to implement MovementFix with proper control reversal
 * for scaffolding while maintaining consistent movement direction.
 */
@Mixin(LivingEntity.class)
public abstract class MovementFixMixin {

    @Unique
    private float midnight_originalYawBeforeTravel;
    @Unique
    private boolean midnight_modifiedYawForTravel = false;

    // Store original input state
    @Unique
    private float midnight_originalMovementForward = 0f;
    @Unique
    private float midnight_originalMovementSideways = 0f;
    @Unique
    private boolean midnight_modifiedMovement = false;

    @Inject(
            method = "travel(Lnet/minecraft/util/math/Vec3d;)V",
            at = @At("HEAD"),
            remap = true
    )
    private void midnight_beforeTravel(Vec3d movementInput, CallbackInfo ci) {
        // Check if this instance is a ClientPlayerEntity
        if (!((Object)this instanceof ClientPlayerEntity)) {
            return;
        }

        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;

        // Check if the instance can be cast to our accessor interface
        if (!(this instanceof EntityAccessor accessor)) {
            // Log error if cast fails - indicates mixin or accessor issue
            if (!midnight_modifiedYawForTravel) { // Only print error once per failure sequence
                System.err.println("[MovementFixMixin] ERROR: Instance cannot be cast to EntityAccessor! Check Accessor Mixin/Target.");
            }
            this.midnight_modifiedYawForTravel = true; // Set true to prevent spamming error
            RotationHandler.setUsingMoveFix(false); // Ensure flag is off
            return;
        }

        // Check conditions from RotationHandler
        if (RotationHandler.isRotationActive() &&
                !RotationHandler.isRotatingClient() &&
                RotationHandler.isMoveFixEnabled())
        {
            // Store original yaw
            this.midnight_originalYawBeforeTravel = accessor.getYawField();

            // Apply server rotations
            accessor.setYawField(RotationHandler.getServerYaw());

            // Also update body yaw to match the server rotation
            player.bodyYaw = RotationHandler.getServerYaw();
            player.headYaw = RotationHandler.getServerYaw();

            // Get player's input
            Input input = player.input;

            // Store original movement values
            midnight_originalMovementForward = input.movementForward;
            midnight_originalMovementSideways = input.movementSideways;

            // Completely reverse movement directions for consistent scaffold experience
            // This ensures W always means "forward" along the bridge regardless of rotation
            input.movementForward = -input.movementForward;
            input.movementSideways = -input.movementSideways;

            // Set flags
            this.midnight_modifiedYawForTravel = true;
            this.midnight_modifiedMovement = true;
            RotationHandler.setUsingMoveFix(true);
        } else {
            // Ensure flags are reset if move fix is not active this tick
            this.midnight_modifiedYawForTravel = false;
            this.midnight_modifiedMovement = false;
            RotationHandler.setUsingMoveFix(false);
        }
    }

    @Inject(
            method = "travel(Lnet/minecraft/util/math/Vec3d;)V",
            at = @At("RETURN"),
            remap = true
    )
    private void midnight_afterTravel(Vec3d movementInput, CallbackInfo ci) {
        // Restore original yaw if it was modified
        if (this.midnight_modifiedYawForTravel) {
            // Safely cast to the accessor interface before using it
            if (this instanceof EntityAccessor accessor) {
                // Use the accessor's setter method to restore
                accessor.setYawField(this.midnight_originalYawBeforeTravel);
            }

            // Reset flag
            this.midnight_modifiedYawForTravel = false;
            RotationHandler.setUsingMoveFix(false);
        }

        // Restore original movement if it was modified
        if (this.midnight_modifiedMovement && (Object)this instanceof ClientPlayerEntity) {
            ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
            Input input = player.input;
            input.movementForward = midnight_originalMovementForward;
            input.movementSideways = midnight_originalMovementSideways;

            this.midnight_modifiedMovement = false;
        }
    }
}