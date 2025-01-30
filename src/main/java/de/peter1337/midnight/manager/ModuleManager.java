package de.peter1337.midnight.manager;

import de.peter1337.midnight.modules.Module;  // Import the correct Module class
import de.peter1337.midnight.modules.movement.Sprint;
import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();

    public static void init() {
        // Register modules
        registerModule(new Sprint());
        // Add more modules here
    }

    private static void registerModule(Module module) {
        modules.add(module);
    }

    public static List<Module> getModules() {
        return modules;
    }

    public static Module getModule(String name) {
        return modules.stream()
                .filter(module -> module.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public static void onUpdate() {
        modules.stream()
                .filter(Module::isEnabled)
                .forEach(Module::onUpdate);
    }
}