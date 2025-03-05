package de.peter1337.midnight.manager.command;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.manager.BindManager;
import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.manager.ConfigManager; // Import the new config manager
import de.peter1337.midnight.manager.ModuleVisibilityManager;
import de.peter1337.midnight.modules.Module;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;

public class CommandManager {

    public static void init() {
        // Listen for chat messages starting with a dot.
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith(".")) {
                handleCommand(message);
                return false; // Prevent sending the command message to chat.
            }
            return true;
        });
        Midnight.LOGGER.info("Command system initialized");
    }

    /**
     * Parses and handles chat commands.
     *
     * @param message The chat message.
     */
    public static void handleCommand(String message) {
        String[] args = message.substring(1).trim().split(" ");
        if (args.length == 0) {
            return;
        }
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
            // Update the binding.
            BindManager.updateBind(module, key.toLowerCase());
            sendMessage("Bound " + module.getName() + " to key: " + key);
            Midnight.LOGGER.info("Set bind for {} to {}", moduleName, key);
        } else if (args[0].equalsIgnoreCase("list")) {
            List<Module> modules = ModuleManager.getModules();
            if (modules.isEmpty()) {
                sendMessage("No modules loaded.");
            } else {
                StringBuilder builder = new StringBuilder("Modules: ");
                for (Module module : modules) {
                    builder.append(module.getName()).append(", ");
                }
                // Remove trailing comma and space.
                if (builder.length() >= 2) {
                    builder.setLength(builder.length() - 2);
                }
                sendMessage(builder.toString());
            }
        } else if (args[0].equalsIgnoreCase("toggle")) {
            if (args.length < 2) {
                sendMessage("Usage: .toggle <module>");
                return;
            }
            String moduleName = args[1];
            Module module = ModuleManager.getModule(moduleName);
            if (module == null) {
                sendMessage("Module not found: " + moduleName);
                return;
            }
            module.toggle();
            sendMessage(module.getName() + " is now " + (module.isEnabled() ? "enabled" : "disabled"));
        } else if (args[0].equalsIgnoreCase("config")) {
            if (args.length < 3) {
                sendMessage("Usage: .config <save/load> <name>");
                return;
            }
            String subCommand = args[1];
            String configName = args[2];
            if (subCommand.equalsIgnoreCase("save")) {
                ConfigManager.saveConfig(configName);
                sendMessage("Saved config: " + configName);
            } else if (subCommand.equalsIgnoreCase("load")) {
                ConfigManager.loadConfig(configName);
                sendMessage("Loaded config: " + configName);
            } else {
                sendMessage("Unknown config command: " + subCommand);
            }
        } else if (args[0].equalsIgnoreCase("visible") || args[0].equalsIgnoreCase("visibility")) {
            if (args.length < 2) {
                sendMessage("Usage: .visible <module> - Toggles a module's visibility in the HUD");
                return;
            }
            String moduleName = args[1];
            Module module = ModuleManager.getModule(moduleName);
            if (module == null) {
                sendMessage("Module not found: " + moduleName);
                return;
            }
            boolean isVisible = ModuleVisibilityManager.toggleVisibility(module);
            sendMessage(module.getName() + " is now " + (isVisible ? "visible" : "hidden") + " in the HUD");
        } else if (args[0].equalsIgnoreCase("hide")) {
            if (args.length < 2) {
                sendMessage("Usage: .hide <module> - Hides a module from the HUD");
                return;
            }
            String moduleName = args[1];
            Module module = ModuleManager.getModule(moduleName);
            if (module == null) {
                sendMessage("Module not found: " + moduleName);
                return;
            }
            ModuleVisibilityManager.hideModule(module.getClass());
            sendMessage("Hidden " + module.getName() + " from the HUD");
        } else if (args[0].equalsIgnoreCase("show")) {
            if (args.length < 2) {
                sendMessage("Usage: .show <module> - Shows a module in the HUD");
                return;
            }
            String moduleName = args[1];
            Module module = ModuleManager.getModule(moduleName);
            if (module == null) {
                sendMessage("Module not found: " + moduleName);
                return;
            }
            ModuleVisibilityManager.showModule(module.getClass());
            sendMessage("Showing " + module.getName() + " in the HUD");
        } else if (args[0].equalsIgnoreCase("help")) {
            // Display all available commands.
            sendMessage("Available commands:");
            sendMessage(" - .bind <module> <key>       : Bind a key to a module");
            sendMessage(" - .list                     : List all modules");
            sendMessage(" - .toggle <module>          : Toggle a module on/off");
            sendMessage(" - .config <save/load> <name>: Save or load a config");
            sendMessage(" - .visible <module>         : Toggle module visibility in HUD");
            sendMessage(" - .hide <module>            : Hide module from HUD");
            sendMessage(" - .show <module>            : Show module in HUD");
        }
    }

    /**
     * Sends an in-game chat message.
     *
     * @param message The message to send.
     */
    private static void sendMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§7[§bMidnight§7] §f" + message), false);
        }
    }
}