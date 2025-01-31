package de.peter1337.midnight.manager;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.Module;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class CommandManager {

    public static void init() {
        // Register an event for when the player sends a chat message
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith(".")) {
                handleCommand(message);
                return false; // Cancel the message from being sent to chat
            }
            return true;
        });

        Midnight.LOGGER.info("Command system initialized");
    }

    public static void handleCommand(String message) {
        String[] args = message.substring(1).trim().split(" ");
        if (args.length == 0) return;

        // "help" command
        if (args[0].equalsIgnoreCase("help")) {
            sendMessage("Available commands:");
            sendMessage(".bind <module> <key> - Binds a key to a module");
            // Expand with more commands as needed
            return;
        }

        // "bind" command
        if (args[0].equalsIgnoreCase("bind")) {
            if (args.length < 3) {
                sendMessage("Usage: .bind <module> <key>");
                return;
            }

            String moduleName = args[1];
            String key = args[2];

            Module module = ModuleManager.getModule(moduleName);
            if (module == null) {
                sendMessage("Module not found: " + moduleName);
                return;
            }

            module.setBind(key.toLowerCase());
            sendMessage("Bound " + module.getName() + " to key: " + key);
            Midnight.LOGGER.info("Set bind for " + moduleName + " to " + key);
        }
    }

    private static void sendMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§7[§bMidnight§7] §f" + message), false);
        }
    }
}