package de.peter1337.midnight.manager;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.render.ClickGuiModule;
import de.peter1337.midnight.modules.misc.NotificationBlocker;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages the visibility of modules in the HUD array list.
 */
public class ModuleVisibilityManager {
    // Set of module class names that should be hidden from the HUD array list
    private static final Set<String> hiddenModules = new HashSet<>();

    // Initialize default hidden modules
    static {
        // Hide these modules by default
        hideModule(ClickGuiModule.class);
        hideModule(NotificationBlocker.class);
    }

    /**
     * Hides a module from the HUD array list by its class.
     *
     * @param moduleClass The class of the module to hide
     */
    public static void hideModule(Class<? extends Module> moduleClass) {
        hiddenModules.add(moduleClass.getName());
        Midnight.LOGGER.info("[Visibility] Hidden module: " + moduleClass.getSimpleName());
    }

    /**
     * Hides a module from the HUD array list by its name.
     *
     * @param moduleName The name of the module to hide
     */
    public static void hideModule(String moduleName) {
        // Store it with a special prefix to distinguish from class names
        hiddenModules.add("name:" + moduleName.toLowerCase());
        Midnight.LOGGER.info("[Visibility] Hidden module by name: " + moduleName);
    }

    /**
     * Shows a previously hidden module in the HUD array list by its class.
     *
     * @param moduleClass The class of the module to show
     */
    public static void showModule(Class<? extends Module> moduleClass) {
        hiddenModules.remove(moduleClass.getName());
        Midnight.LOGGER.info("[Visibility] Shown module: " + moduleClass.getSimpleName());
    }

    /**
     * Shows a previously hidden module in the HUD array list by its name.
     *
     * @param moduleName The name of the module to show
     */
    public static void showModule(String moduleName) {
        hiddenModules.remove("name:" + moduleName.toLowerCase());
        Midnight.LOGGER.info("[Visibility] Shown module by name: " + moduleName);
    }

    /**
     * Checks if a module should be hidden from the HUD array list.
     *
     * @param module The module to check
     * @return true if the module should be hidden, false otherwise
     */
    public static boolean isHidden(Module module) {
        // Check by class name
        if (hiddenModules.contains(module.getClass().getName())) {
            return true;
        }

        // Check by module name (with special prefix)
        return hiddenModules.contains("name:" + module.getName().toLowerCase());
    }

    /**
     * Toggles the visibility of a module in the HUD array list.
     *
     * @param module The module to toggle visibility for
     * @return true if the module is now visible, false if it's now hidden
     */
    public static boolean toggleVisibility(Module module) {
        String className = module.getClass().getName();
        String namedKey = "name:" + module.getName().toLowerCase();

        // Check if it's hidden by class or name
        if (hiddenModules.contains(className)) {
            hiddenModules.remove(className);
            Midnight.LOGGER.info("[Visibility] Toggled visibility ON for: " + module.getName());
            return true;
        } else if (hiddenModules.contains(namedKey)) {
            hiddenModules.remove(namedKey);
            Midnight.LOGGER.info("[Visibility] Toggled visibility ON for: " + module.getName());
            return true;
        } else {
            // If not hidden, hide it by class
            hiddenModules.add(className);
            Midnight.LOGGER.info("[Visibility] Toggled visibility OFF for: " + module.getName());
            return false;
        }
    }
}