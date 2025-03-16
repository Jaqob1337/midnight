package de.peter1337.midnight.modules.player;

import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Module;

public class NoSlow extends Module {
    public NoSlow() {
        super("NoSlow", "Prevents slowdown when using items", Category.PLAYER, "n");
    }
}