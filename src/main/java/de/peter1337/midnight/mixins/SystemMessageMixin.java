package de.peter1337.midnight.mixins;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.misc.NotificationBlocker;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class SystemMessageMixin {

    /**
     * Intercepts system messages (such as player join/leave messages)
     * and cancels them if the NotificationBlocker module is enabled and
     * configured to block system messages.
     */
    @Inject(method = "onGameMessage", at = @At("HEAD"), cancellable = true)
    private void onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        // Only block if the message has the overlay parameter set to false,
        // which typically represents system messages rather than chat
        if (!packet.overlay()) {
            NotificationBlocker notificationBlocker = (NotificationBlocker) ModuleManager.getModule("NotificationBlocker");
            if (notificationBlocker != null && notificationBlocker.shouldBlockSystemMessages()) {
                ci.cancel();
            }
        }
    }
}