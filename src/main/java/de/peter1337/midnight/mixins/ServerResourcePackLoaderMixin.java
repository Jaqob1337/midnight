package de.peter1337.midnight.mixins;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.misc.NotificationBlocker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.server.ServerResourcePackLoader;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URL;
import java.util.UUID;

/**
 * Mixin targeting the ServerResourcePackLoader class
 */
@Mixin(ServerResourcePackLoader.class)
public class ServerResourcePackLoaderMixin {

    @Shadow
    private MinecraftClient client;

    /**
     * Intercepts the addResourcePack method that receives a URL and hash
     */
    @Inject(method = "addResourcePack(Ljava/util/UUID;Ljava/net/URL;Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void onAddResourcePack(UUID id, URL url, String hash, CallbackInfo ci) {
        NotificationBlocker notificationBlocker =
                (NotificationBlocker) ModuleManager.getModule("NotificationBlocker");

        if (notificationBlocker != null && notificationBlocker.shouldBlockResourcePacks()) {
            // Get the client network connection
            if (this.client.getNetworkHandler() != null && this.client.getNetworkHandler().getConnection() != null) {
                // Send "ACCEPTED" status to avoid getting kicked
                this.client.getNetworkHandler().getConnection().send(
                        new ResourcePackStatusC2SPacket(id, ResourcePackStatusC2SPacket.Status.ACCEPTED)
                );

                // Also send "SUCCESSFULLY_LOADED" to make it look like we loaded it
                this.client.getNetworkHandler().getConnection().send(
                        new ResourcePackStatusC2SPacket(id, ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED)
                );

                // Notify player
                if (this.client.player != null) {
                    this.client.player.sendMessage(
                            Text.literal("§7[§bMidnight§7] §fBlocked server resource pack: " + url.toString()),
                            false
                    );
                }

                // Cancel the original method to prevent the resource pack from loading
                ci.cancel();
            }
        }
    }
}