package de.peter1337.midnight.mixins;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.player.Scaffold;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin intercepts keyboard input at its origin to reverse movement for Scaffold
 */
@Mixin(KeyboardInput.class)
public class ScaffoldInputMixin {
}


    /**
     * Inject at the end of the tick method to reverse inputs when scaffolding

    @Inject(method = "tick", at = @At("TAIL"))
    private void onInputTick(CallbackInfo ci) {
        // Get the current input instance
        Input input = (Input)(Object)this;

        // Check if Scaffold is active and MoveFix is enabled
        Scaffold scaffoldModule = (Scaffold) ModuleManager.getModule("Scaffold");
        if (scaffoldModule != null && scaffoldModule.isEnabled() && scaffoldModule.isMovingFixEnabled()) {
            // Store the original input values
            float originalForward = input.movementForward;
            float originalSideways = input.movementSideways;

            // REVERSE ALL DIRECTIONS
            // - Change forward to backward (and vice versa)
            input.movementForward = -originalForward;

            // - Change left to right (and vice versa)
            input.movementSideways = -originalSideways;

            }
        }
    }
     */