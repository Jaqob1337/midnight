package de.peter1337.midnight.mixins; // Ensure this package exists

import net.minecraft.entity.Entity; // Target Entity class where yaw field is likely declared
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor interface to get/set the yaw field of an Entity.
 * Used by MovementFixMixin to bypass potential @Shadow resolution issues.
 */
@Mixin(Entity.class) // Target the class where the yaw field is actually declared
public interface EntityAccessor {

    /**
     * Provides access to the yaw field.
     * !!! REPLACE "yaw" with the correct mapped field name for your setup (e.g., yRot) !!!
     * @return The value of the yaw field.
     */
    @Accessor("yaw") // <--- !!! REPLACE "yaw" WITH THE CORRECT MAPPED FIELD NAME !!!
    float getYawField(); // Method name can be anything, annotation links it

    /**
     * Allows setting the yaw field.
     * !!! REPLACE "yaw" with the correct mapped field name for your setup (e.g., yRot) !!!
     * @param yaw The new value for the yaw field.
     */
    @Accessor("yaw") // <--- !!! REPLACE "yaw" WITH THE CORRECT MAPPED FIELD NAME !!!
    void setYawField(float yaw); // Method name can be anything, annotation links it
}