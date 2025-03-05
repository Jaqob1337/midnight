package de.peter1337.midnight.mixins;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.render.ESP;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityGlowingMixin {
    /**
     * Override isGlowing() to control glowing based on ESP module settings.
     */
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void overrideIsGlowing(CallbackInfoReturnable<Boolean> cir) {
        var espModule = ModuleManager.getModule("ESP");
        if (espModule instanceof ESP esp && esp.isEnabled()) {
            // Check if this specific entity should glow
            cir.setReturnValue(esp.shouldEntityGlow((Entity)(Object)this));
        }
    }
}