package de.peter1337.midnight.modules;

import de.peter1337.midnight.utils.Category;
import de.peter1337.midnight.utils.Setting;
import de.peter1337.midnight.manager.BindManager;
import java.util.ArrayList;
import java.util.List;

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

        // Register initial bind if provided
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
        if (enabled) onEnable();
        else onDisable();
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onUpdate() {}

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public String getBind() { return bind; }
    public void setBind(String bind) {
        BindManager.updateBind(this, bind);
    }
}