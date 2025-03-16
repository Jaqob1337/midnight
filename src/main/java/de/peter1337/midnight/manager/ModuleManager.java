package de.peter1337.midnight.manager;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.combat.Aura;
import de.peter1337.midnight.modules.movement.Speed;
import de.peter1337.midnight.modules.movement.Sprint;
import de.peter1337.midnight.modules.player.InvManager;
import de.peter1337.midnight.modules.player.NoSlow;
import de.peter1337.midnight.modules.player.Stealer;
import de.peter1337.midnight.modules.player.Velocity;
import de.peter1337.midnight.modules.render.ClickGuiModule;
import de.peter1337.midnight.modules.render.ESP;
import de.peter1337.midnight.modules.misc.NotificationBlocker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.util.math.MatrixStack;

import javax.swing.text.html.HTMLDocument;

public class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();

    public static void init() {
        // Register your modules here.
        registerModule(new Sprint());
        registerModule(new ESP());
        registerModule(new ClickGuiModule());
        registerModule(new Stealer());
        registerModule(new NotificationBlocker());
        registerModule(new InvManager());
        registerModule(new Velocity());
        registerModule(new Aura());
        registerModule(new Speed());
        registerModule(new NoSlow());
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