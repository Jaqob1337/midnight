package de.peter1337.midnight.mixins;

import de.peter1337.midnight.manager.ModuleManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPlayerEntity.class)
public class NoSlowMixin {
    /**
     * This redirect completely bypasses the item-use slowdown logic in Minecraft
     * by making the game think we're not using an item when the NoSlow module is enabled.
     */
    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean redirectIsUsingItem(ClientPlayerEntity player) {
        // Get the NoSlow module from the module manager
        de.peter1337.midnight.modules.Module noSlowModule = ModuleManager.getModule("NoSlow");

        // Check if the module exists and is enabled
        if (noSlowModule != null && noSlowModule.isEnabled() && player.isUsingItem()) {
            // Make the game think we're not using an item, which prevents the slowdown
            return false;
        }

        // Otherwise, return the actual value
        return player.isUsingItem();
    }
}