package de.peter1337.midnight.modules;

import de.peter1337.midnight.manager.BindManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.util.math.MatrixStack;

public class Module {
    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled;
    private String bind;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // Flag to bypass chat check for config loading
    private static boolean bypassChatCheck = false;

    public Module(String name, String description, Category category, String bind) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.bind = bind;
        if (bind != null && !bind.isEmpty()) {
            BindManager.registerBind(this, bind);
        }
    }

    protected <T> Setting<T> register(Setting<T> setting) {
        settings.add(setting);
        return setting;
    }

    /**
     * Check if chat is open
     * @return true if chat is open, false otherwise
     */
    protected boolean isChatOpen() {
        return mc != null && mc.currentScreen instanceof ChatScreen;
    }

    /**
     * Method to enable/disable the chat check bypass
     * Should be used when loading configs
     */
    public static void setBypassChatCheck(boolean bypass) {
        bypassChatCheck = bypass;
    }

    /**
     * Toggle the module's enabled state
     */
    public void toggle() {
        // Check if chat is open and we're not bypassing the check
        if (isChatOpen() && !bypassChatCheck) {
            System.out.println("Cannot toggle " + name + " while chat is open");
            return;
        }

        enabled = !enabled;
        System.out.println(name + " toggled to " + enabled);
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    /**
     * Set the module's enabled state directly without checks
     * For internal use only when loading config
     */
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return; // No change needed
        }

        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    // Called during the render phase.
    public void onRender(MatrixStack matrices) { }

    // Called when the module is enabled.
    public void onEnable() { }

    // Called when the module is disabled.
    public void onDisable() { }

    // Called on every tick.
    public void onUpdate() { }

    // New method: should this module update even if disabled?
    public boolean shouldAlwaysUpdate() {
        return false;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public Category getCategory() {
        return this.category;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public String getBind() {
        return this.bind;
    }

    public void setBind(String bind) {
        this.bind = bind;
    }

    public List<Setting<?>> getSettings() {
        return this.settings;
    }
}