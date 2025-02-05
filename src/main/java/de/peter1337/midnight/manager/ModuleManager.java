package de.peter1337.midnight.manager;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.movement.Sprint;
import de.peter1337.midnight.modules.render.ClickGuiModule;
import de.peter1337.midnight.modules.render.ESP;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.util.math.MatrixStack;

public class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();

    public static void init() {
        // Register your modules here.
        registerModule(new Sprint());
        registerModule(new ESP());
        registerModule(new ClickGuiModule());
    }

    private static void registerModule(Module module) {
        modules.add(module);
    }

    public static List<Module> getModules() {
        return new ArrayList<>(modules);
    }

    public static Module getModule(String name) {
        return modules.stream()
                .filter(module -> module.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public static void onRender(MatrixStack matrixStack) {
        modules.stream()
                .filter(Module::isEnabled)
                .forEach(module -> module.onRender(matrixStack));
    }

    public static void onUpdate() {
        modules.forEach(module -> {
            if (module.isEnabled() || module.shouldAlwaysUpdate()) {
                module.onUpdate();
            }
        });
    }
}
