package de.peter1337.midnight.mixins;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.misc.NotificationBlocker;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public class ToastMixin {

    /**
     * Cancels all toast notifications when the NotificationBlocker module is enabled
     * and configured to block toasts.
     */
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void onAddToast(Toast toast, CallbackInfo ci) {
        NotificationBlocker notificationBlocker = (NotificationBlocker) ModuleManager.getModule("NotificationBlocker");
        if (notificationBlocker != null && notificationBlocker.shouldBlockToasts()) {
            ci.cancel();
        }
    }
}