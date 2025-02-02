package de.peter1337.midnight.mixin;

import de.peter1337.midnight.manager.ModuleManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityGlowingMixin {
    /**
     * Override isGlowing() so that if the ESP module is enabled,
     * the entity always appears glowing.
     */
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void overrideIsGlowing(CallbackInfoReturnable<Boolean> cir) {
        // Retrieve the module instance from ModuleManager
        var espModule = ModuleManager.getModule("ESP");
        if (espModule != null && espModule.isEnabled()) {
            cir.setReturnValue(true);
        }
    }
}
