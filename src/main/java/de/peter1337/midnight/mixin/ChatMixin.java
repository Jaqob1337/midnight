package de.peter1337.midnight.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import de.peter1337.midnight.manager.CommandManager;

@Mixin(ClientPlayNetworkHandler.class)
public class ChatMixin {
    @Inject(method = "onGameMessage", at = @At("HEAD"), cancellable = true)
    private void onChatMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        String message = packet.content().getString();
        if (message.startsWith(".")) {
            CommandManager.handleCommand(message);
            ci.cancel();
        }
    }
}