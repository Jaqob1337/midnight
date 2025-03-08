package de.peter1337.midnight.mixins;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.player.Velocity;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class PlayerVelocityMixin {
    /**
     * Prevents push out of blocks when in Cancel mode
     */
    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void onPushOutOfBlocks(double x, double z, CallbackInfo ci) {
        Velocity velocityModule = (Velocity) ModuleManager.getModule("Velocity");

        if (velocityModule != null && velocityModule.isEnabled() &&
                velocityModule.mode.getValue().equals("Cancel")) {
            // Cancel pushing out of blocks entirely in Cancel mode
            ci.cancel();
        }
    }
}