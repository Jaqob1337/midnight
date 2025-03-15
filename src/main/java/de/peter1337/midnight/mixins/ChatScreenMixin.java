package de.peter1337.midnight.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class ChatScreenMixin {

    private static boolean chatOpen = false;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(net.minecraft.client.gui.screen.Screen screen, CallbackInfo ci) {
        chatOpen = (screen instanceof ChatScreen);
    }

    /**
     * Static accessor to check if chat is currently open
     */
    private static boolean isChatOpen() {
        return chatOpen;
    }
}