package de.peter1337.midnight.mixins;

import de.peter1337.midnight.handler.RotationHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A minimal mixin that stores the original rotation values
 * and then manually sets them just before sending a packet
 */
@Mixin(net.minecraft.client.network.ClientPlayerEntity.class)
public class RotationMixin {

    private float lastRealYaw;
    private float lastRealPitch;
    private boolean rotationChanging = false;

    /**
     * Update rotations before the game tick, which is when packets are sent
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onPreTick(CallbackInfo ci) {
        if (RotationHandler.isRotationActive() && !RotationHandler.isRotatingClient()) {
            net.minecraft.client.network.ClientPlayerEntity player = (net.minecraft.client.network.ClientPlayerEntity)(Object)this;

            // Store original rotations
            lastRealYaw = player.getYaw();
            lastRealPitch = player.getPitch();

            // Apply server rotations temporarily
            player.setYaw(RotationHandler.getServerYaw());
            player.setPitch(RotationHandler.getServerPitch());
            rotationChanging = true;
        }
    }

    /**
     * Reset rotations after the tick is done
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void onPostTick(CallbackInfo ci) {
        if (rotationChanging) {
            net.minecraft.client.network.ClientPlayerEntity player = (net.minecraft.client.network.ClientPlayerEntity)(Object)this;

            // Restore original rotations
            player.setYaw(lastRealYaw);
            player.setPitch(lastRealPitch);
            rotationChanging = false;
        }
    }
}