package de.peter1337.midnight.manager.command;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CommandAutoComplete {
    // Supported dot commands (without the dot)
    private static final List<String> COMMANDS = List.of("bind", "list", "toggle", "config");
    private static final List<String> CONFIG_COMMANDS = List.of("save", "load", "list");

    // Store the current suggestions to be rendered.
    public static List<String> currentSuggestions = new ArrayList<>();

    // Flag to cancel default Minecraft chat suggestions
    public static boolean suppressDefaultSuggestions = false;

    // Debounce for Tab key presses (in milliseconds)
    private static long lastTabPress = 0;

    // Get list of existing config files
    private static List<String> getConfigFiles() {
        List<String> configNames = new ArrayList<>();

        if (Midnight.CONFIG_DIR.exists() && Midnight.CONFIG_DIR.isDirectory()) {
            File[] configFiles = Midnight.CONFIG_DIR.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (configFiles != null) {
                for (File file : configFiles) {
                    // Remove .json extension
                    configNames.add(file.getName().substring(0, file.getName().length() - 5));
                }
            }
        }

        return configNames;
    }

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        suppressDefaultSuggestions = false;

        if (client.currentScreen instanceof ChatScreen) {
            ChatScreen chatScreen = (ChatScreen) client.currentScreen;
            // Use a mixin accessor (ChatScreenAccessor) to get the private chat text field.
            TextFieldWidget textField = ((de.peter1337.midnight.mixins.ChatScreenAccessor) chatScreen).getChatField();
            if (textField != null) {
                String currentText = textField.getText();
                // Check if the chat text starts with a dot.
                if (currentText.startsWith(".")) {
                    // Suppress default Minecraft suggestions
                    suppressDefaultSuggestions = true;

                    String commandText = currentText.substring(1); // Remove the dot
                    String[] parts = commandText.split(" ");
                    List<String> suggestions = new ArrayList<>();
                    if (parts.length == 0 || parts[0].isEmpty()) {
                        // No command typed yet; suggest all commands.
                        suggestions.addAll(COMMANDS);
                    } else if (parts.length == 1) {
                        // Autocomplete the command name.
                        String partial = parts[0];
                        for (String cmd : COMMANDS) {
                            if (cmd.startsWith(partial.toLowerCase())) {
                                suggestions.add(cmd);
                            }
                        }
                    } else if (parts.length == 2) {
                        if (parts[0].equalsIgnoreCase("bind") || parts[0].equalsIgnoreCase("toggle")) {
                            // Autocomplete module name for 'bind' or 'toggle'.
                            String partialModule = parts[1];
                            for (Module module : ModuleManager.getModules()) {
                                if (module.getName().toLowerCase().startsWith(partialModule.toLowerCase())) {
                                    suggestions.add(module.getName());
                                }
                            }
                        } else if (parts[0].equalsIgnoreCase("config")) {
                            // Autocomplete config commands
                            String partialConfigCmd = parts[1];
                            for (String configCmd : CONFIG_COMMANDS) {
                                if (configCmd.startsWith(partialConfigCmd.toLowerCase())) {
                                    suggestions.add(configCmd);
                                }
                            }
                        }
                    } else if (parts.length == 3 &&
                            parts[0].equalsIgnoreCase("config") &&
                            (parts[1].equalsIgnoreCase("save") || parts[1].equalsIgnoreCase("load"))) {
                        // Autocomplete config names for save/load
                        String partialConfigName = parts[2];
                        for (String configName : getConfigFiles()) {
                            if (configName.toLowerCase().startsWith(partialConfigName.toLowerCase())) {
                                suggestions.add(configName);
                            }
                        }
                    }

                    // Update the static suggestions list so it can be rendered.
                    currentSuggestions = suggestions;

                    // If the Tab key is pressed, auto-complete the text field.
                    if (isTabPressed(client) && (System.currentTimeMillis() - lastTabPress > 200)) {
                        lastTabPress = System.currentTimeMillis();
                        if (!suggestions.isEmpty()) {
                            if (parts.length == 1) {
                                textField.setText("." + suggestions.get(0));
                            } else if (parts.length == 2) {
                                textField.setText("." + parts[0] + " " + suggestions.get(0));
                            } else if (parts.length == 3) {
                                textField.setText("." + parts[0] + " " + parts[1] + " " + suggestions.get(0));
                            }
                        }
                    }
                } else {
                    // If the chat text doesn't start with a dot, clear suggestions.
                    currentSuggestions.clear();
                }
            }
        } else {
            currentSuggestions.clear();
        }
    }

    private static boolean isTabPressed(MinecraftClient client) {
        long windowHandle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_TAB) == GLFW.GLFW_PRESS;
    }
}