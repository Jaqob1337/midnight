package de.peter1337.midnight.mixins;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.misc.NotificationBlocker;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to prevent the status effect icons (potion effects) from rendering
 * in the top-right corner of the screen.
 */
@Mixin(InGameHud.class)
public class StatusEffectHudMixin {

    /**
     * Injects at the start of the renderStatusEffectOverlay method to
     * cancel the rendering of potion effects if the NotificationBlocker
     * module is enabled and configured to block status effects.
     */
    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        NotificationBlocker notificationBlocker = (NotificationBlocker) ModuleManager.getModule("NotificationBlocker");
        if (notificationBlocker != null && notificationBlocker.shouldBlockStatusEffects()) {
            ci.cancel();
        }
    }
}