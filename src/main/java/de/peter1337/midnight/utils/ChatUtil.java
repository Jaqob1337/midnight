package de.peter1337.midnight.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;

public class ChatUtil {

    /**
     * Checks if the chat screen is currently open
     * @return true if chat is open, false otherwise
     */
    public static boolean isChatOpen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && mc.currentScreen instanceof ChatScreen;
    }
}