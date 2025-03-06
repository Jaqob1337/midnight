package de.peter1337.midnight.modules.misc;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Setting;

/**
 * Module to block various in-game notifications such as achievements and toast notifications.
 */
public class NotificationBlocker extends Module {

    private final Setting<Boolean> blockToasts = register(
            new Setting<>("BlockToasts", Boolean.TRUE, "Block toast notifications (advancements, recipes, etc.)")
    );

    private final Setting<Boolean> blockAchievements = register(
            new Setting<>("BlockAchievements", Boolean.TRUE, "Block achievement notifications")
    );

    private final Setting<Boolean> blockSystemMessages = register(
            new Setting<>("BlockSystemMessages", Boolean.TRUE, "Block system chat messages (e.g. server join messages)")
    );

    private final Setting<Boolean> blockStatusEffects = register(
            new Setting<>("BlockStatusEffects", Boolean.TRUE, "Block status effect icons (potions) in the top-right corner")
    );

    public NotificationBlocker() {
        super("NotificationBlocker", "Blocks achievement and other notifications", Category.MISC, "n");
        Midnight.LOGGER.info("NotificationBlocker module initialized");

        // Enable the module by default when it's created
        if (!isEnabled()) {
            toggle();
            Midnight.LOGGER.info("NotificationBlocker enabled by default");
        }
    }

    @Override
    public void onEnable() {
        // The actual blocking is handled by mixins
        Midnight.LOGGER.info("NotificationBlocker enabled - notifications will be blocked");
    }

    @Override
    public void onDisable() {
        // Nothing to do when disabled, as mixins will check enabled state
    }

    /**
     * Gets whether toast notifications should be blocked.
     *
     * @return true if toasts should be blocked
     */
    public boolean shouldBlockToasts() {
        return isEnabled() && blockToasts.getValue();
    }

    /**
     * Gets whether achievement notifications should be blocked.
     *
     * @return true if achievements should be blocked
     */
    public boolean shouldBlockAchievements() {
        return isEnabled() && blockAchievements.getValue();
    }

    /**
     * Gets whether system messages should be blocked.
     *
     * @return true if system messages should be blocked
     */
    public boolean shouldBlockSystemMessages() {
        return isEnabled() && blockSystemMessages.getValue();
    }

    /**
     * Gets whether status effect icons (potions) should be blocked.
     *
     * @return true if status effect icons should be blocked
     */
    public boolean shouldBlockStatusEffects() {
        return isEnabled() && blockStatusEffects.getValue();
    }
}