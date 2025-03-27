package de.peter1337.midnight.mixins;

import de.peter1337.midnight.handler.RotationHandler;
// Import the CORRECT accessor interface (adjust package/name if needed)
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity; // Import Entity
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
// No more @Shadow imports for getYaw/setYaw needed here
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import de.peter1337.midnight.mixins.EntityAccessor;

/**
 * Mixin for LivingEntity to implement MovementFix using an Accessor.
 * Applies server-side yaw during travel() calculation when silent rotations
 * with move fix are active for the local player via RotationHandler.
 */
@Mixin(LivingEntity.class) // Still target LivingEntity because we hook the travel() method here
public abstract class MovementFixMixin {

    // No @Shadow declarations needed for yaw

    private float midnight_originalYawBeforeTravel;
    private boolean midnight_modifiedYawForTravel = false;

    @Inject(
            method = "travel(Lnet/minecraft/util/math/Vec3d;)V",
            at = @At("HEAD"),
            remap = true
    )
    private void midnight_beforeTravel(Vec3d movementInput, CallbackInfo ci) {
        // instance is a LivingEntity, which is also an Entity
        Object instance = this;
        if (!(instance instanceof ClientPlayerEntity player)) {
            return;
        }

        // Check if the instance can be cast to our accessor interface.
        if (!(instance instanceof EntityAccessor accessor)) {
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
            // Use the accessor's generated getter method
            this.midnight_originalYawBeforeTravel = accessor.getYawField(); // MAKE SURE getYawField MATCHES ACCESSOR

            // Use the accessor's generated setter method
            accessor.setYawField(RotationHandler.getServerYaw()); // MAKE SURE setYawField MATCHES ACCESSOR

            // Set flags
            this.midnight_modifiedYawForTravel = true;
            RotationHandler.setUsingMoveFix(true);
        } else {
            // Ensure flags are reset if move fix is not active this tick
            this.midnight_modifiedYawForTravel = false;
            RotationHandler.setUsingMoveFix(false);
        }
    }

    @Inject(
            method = "travel(Lnet/minecraft/util/math/Vec3d;)V",
            at = @At("RETURN"),
            remap = true
    )
    private void midnight_afterTravel(Vec3d movementInput, CallbackInfo ci) {
        // Only restore if WE modified it in the corresponding HEAD injection
        if (this.midnight_modifiedYawForTravel) {
            Object instance = this;
            // Safely cast to the accessor interface before using it
            if (instance instanceof EntityAccessor accessor) {
                // Use the accessor's setter method to restore
                accessor.setYawField(this.midnight_originalYawBeforeTravel); // MAKE SURE setYawField MATCHES ACCESSOR
            } else {
                // Error already logged in HEAD if cast failed
            }

            // Reset flags
            this.midnight_modifiedYawForTravel = false;
            RotationHandler.setUsingMoveFix(false);
        }
    }
}