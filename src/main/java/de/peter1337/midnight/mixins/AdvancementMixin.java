package de.peter1337.midnight.mixins;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.misc.NotificationBlocker;
import net.minecraft.client.network.ClientAdvancementManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientAdvancementManager.class)
public class AdvancementMixin {

    /**
     * Intercepts advancement update packets and cancels them if the NotificationBlocker
     * module is enabled and configured to block achievements.
     */
    @Inject(method = "onAdvancements", at = @At("HEAD"), cancellable = true)
    private void onHandleAdvancements(AdvancementUpdateS2CPacket packet, CallbackInfo ci) {
        NotificationBlocker notificationBlocker = (NotificationBlocker) ModuleManager.getModule("NotificationBlocker");
        if (notificationBlocker != null && notificationBlocker.shouldBlockAchievements()) {
            ci.cancel();
        }
    }
}