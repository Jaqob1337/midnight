package de.peter1337.midnight.manager.command;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class CommandAutoComplete {
    // Supported dot commands (without the dot)
    private static final List<String> COMMANDS = List.of("bind", "list", "toggle");

    // Store the current suggestions to be rendered.
    public static List<String> currentSuggestions = new ArrayList<>();

    // Debounce for Tab key presses (in milliseconds)
    private static long lastTabPress = 0;

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof ChatScreen) {
            ChatScreen chatScreen = (ChatScreen) client.currentScreen;
            // Use a mixin accessor (ChatScreenAccessor) to get the private chat text field.
            TextFieldWidget textField = ((de.peter1337.midnight.mixin.ChatScreenAccessor) chatScreen).getChatField();
            if (textField != null) {
                String currentText = textField.getText();
                // Only work if the chat text starts with a dot.
                if (currentText.startsWith(".")) {
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
                    } else if (parts.length == 2 &&
                            (parts[0].equalsIgnoreCase("bind") || parts[0].equalsIgnoreCase("toggle"))) {
                        // Autocomplete module name for 'bind' or 'toggle'.
                        String partialModule = parts[1];
                        for (Module module : ModuleManager.getModules()) {
                            if (module.getName().toLowerCase().startsWith(partialModule.toLowerCase())) {
                                suggestions.add(module.getName());
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
