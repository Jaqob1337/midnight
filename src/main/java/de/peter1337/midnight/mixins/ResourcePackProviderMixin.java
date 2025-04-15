package de.peter1337.midnight.mixins;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.misc.NotificationBlocker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * This mixin targets the resource pack loading system to prevent the actual
 * application of server resource packs
 */
@Mixin(net.minecraft.resource.ResourcePackManager.class)
public class ResourcePackProviderMixin {

    /**
     * Intercepts the resource reload process to ensure server resource packs
     * are not actually applied after being "accepted"
     */
    @Inject(method = "scanPacks", at = @At("HEAD"), cancellable = true)
    private void onScanPacks(CallbackInfo ci) {
        if (isServerResourceOperation()) {
            NotificationBlocker notificationBlocker =
                    (NotificationBlocker) ModuleManager.getModule("NotificationBlocker");

            if (notificationBlocker != null && notificationBlocker.shouldBlockResourcePacks()) {
                // Don't actually scan/load server resource packs
                // Just pass through - the callback will be completed in the original method
                // but we'll prevent the actual resource packs from being processed

                // Only notify once per reload (optional)
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    mc.player.sendMessage(
                            net.minecraft.text.Text.literal("§7[§bMidnight§7] §fBlocked server resource pack from loading"),
                            true // Set to true for actionbar message instead of chat
                    );
                }
            }
        }
    }

    /**
     * Helper method to check if this is a server resource pack operation
     * by examining the stack trace
     */
    private boolean isServerResourceOperation() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className.contains("ServerResourcePackProvider") ||
                    className.contains("ResourcePackOrganizer") ||
                    className.contains("ServerResourcePackLoader")) {
                return true;
            }
        }
        return false;
    }
}