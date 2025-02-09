package de.peter1337.midnight.modules;

import de.peter1337.midnight.manager.BindManager;
import de.peter1337.midnight.utils.Setting;
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

    public void toggle() {
        enabled = !enabled;
        System.out.println(name + " toggled to " + enabled);
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
