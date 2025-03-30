// --- EntityAccessor.java ---
package de.peter1337.midnight.mixins;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor interface to get/set the yaw field of an Entity.
 * Used by MovementFixMixin to access and modify entity rotation directly.
 */
@Mixin(Entity.class)
public interface EntityAccessor {

    /**
     * Provides access to the yaw field.
     * Note: Use the correct mapped field name based on your Minecraft version.
     * For 1.21.4, this may be "yRot", "yaw", or another mapped name. Check your mappings.
     *
     * @return The value of the yaw field.
     */
    @Accessor("yaw") // Make sure this matches the correct mapped field name in your environment (e.g., "yRot")
    float getYawField();

    /**
     * Allows setting the yaw field.
     * Note: Use the correct mapped field name based on your Minecraft version.
     *
     * @param yaw The new value for the yaw field.
     */
    @Accessor("yaw") // Make sure this matches the correct mapped field name in your environment (e.g., "yRot")
    void setYawField(float yaw);
}