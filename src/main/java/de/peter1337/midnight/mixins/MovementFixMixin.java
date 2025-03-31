package de.peter1337.midnight.mixins;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.handler.RotationHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for LivingEntity to implement an improved MovementFix that considers
 * both client-side camera rotations and server-side rotations.
 */
@Mixin(LivingEntity.class)
public abstract class MovementFixMixin {

    @Unique
    private float midnight_originalYaw = 0f;
    @Unique
    private boolean midnight_modifiedYaw = false;
    @Unique
    private float midnight_originalForward = 0f;
    @Unique
    private float midnight_originalSideways = 0f;
    @Unique
    private boolean midnight_modifiedMovement = false;

    @Inject(
            method = "travel(Lnet/minecraft/util/math/Vec3d;)V",
            at = @At("HEAD"),
            remap = true
    )
    private void midnight_beforeTravel(Vec3d movementInput, CallbackInfo ci) {
        // Get a reference to the current entity and client instance
        LivingEntity self = (LivingEntity)(Object)this;
        MinecraftClient mc = MinecraftClient.getInstance();

        // Only apply to the local player
        if (mc.player == null || self != mc.player) {
            return;
        }

        // Check if rotation handler is active and move fix is enabled
        if (!RotationHandler.isRotationActive() ||
                RotationHandler.isRotatingClient() ||
                !RotationHandler.isMoveFixEnabled()) {
            return;
        }

        try {
            // Store original yaw
            midnight_originalYaw = self.getYaw();
            midnight_modifiedYaw = true;

            // Get the local player and input
            ClientPlayerEntity player = mc.player;
            Input input = player.input;

            // Store original movement values
            midnight_originalForward = input.movementForward;
            midnight_originalSideways = input.movementSideways;
            midnight_modifiedMovement = true;

            // Get client and server rotation
            float clientYaw = player.getYaw();
            float serverYaw = RotationHandler.getServerYaw();

            // Apply the server-side rotation to the entity for movement calculations
            player.setYaw(serverYaw);
            player.bodyYaw = serverYaw;
            player.headYaw = serverYaw;

            // Get the move fix context
            String moveFixContext = RotationHandler.getMoveFixContext();

            // For 100% guaranteed direct movement control
            if (moveFixContext != null && moveFixContext.equalsIgnoreCase("scaffold_direct_100")) {
                // CRITICAL: USE THE VALUES EXACTLY AS-IS
                // Do not apply any transformations at all
                float newForward = midnight_originalForward;
                float newSideways = midnight_originalSideways;

                // Debug log to confirm we're using this special case
                Midnight.LOGGER.info("[Scaffold] 100% DIRECT MOVEMENT CONTROL ACTIVE: " +
                        "forward=" + newForward + ", sideways=" + newSideways);

                // IMPORTANT: Make sure these values aren't modified anywhere else by skipping any further processing
                input.movementForward = newForward;
                input.movementSideways = newSideways;

                // Skip all other processing by using the move fix flag
                RotationHandler.setUsingMoveFix(true);
                return;
            }

            // Calculate new forward and sideways values based on context
            float newForward;
            float newSideways;

            // Special handling for scaffold_direct context
            if (moveFixContext != null && moveFixContext.equalsIgnoreCase("scaffold_direct")) {
                // For scaffold_direct, Scaffold module has directly set the movement values
                // based on which keys are pressed, so we use them exactly as they are
                newForward = midnight_originalForward;
                newSideways = midnight_originalSideways;

                // Debug output for verification
                Midnight.LOGGER.debug("Using scaffold_direct context: using direct key-based values");
            }
            // Special handling for scaffold_reversed context
            else if (moveFixContext != null && moveFixContext.equalsIgnoreCase("scaffold_reversed")) {
                // For scaffold_reversed, the Scaffold module has already inverted the inputs
                // So we don't need to transform the movement any further
                newForward = midnight_originalForward;
                newSideways = midnight_originalSideways;

                // Debug output for verification
                Midnight.LOGGER.debug("Using scaffold_reversed context: keeping inverted values");
            }
            // For scaffold, use a special case with custom handling
            else if (moveFixContext != null && moveFixContext.equalsIgnoreCase("scaffold")) {
                // For scaffolding, we need a more specific approach
                // We want to prioritize the client's visual direction for movement

                // Instead of simple reversal, let's use a modified rotation calculation
                // This helps maintain the "forward = bridge forward" behavior players expect

                // Get the direction the player is visually looking
                float clientDirection = clientYaw;
                // Get the direction the player is placed blocks towards
                float serverDirection = serverYaw;

                // Calculate the angle needed to make W always go forward in client view
                float angleToForward = MathHelper.wrapDegrees(serverDirection - clientDirection);

                // Use a modified rotation matrix with this angle
                float sin = MathHelper.sin(angleToForward * ((float)Math.PI / 180F));
                float cos = MathHelper.cos(angleToForward * ((float)Math.PI / 180F));

                newForward = midnight_originalForward * cos + midnight_originalSideways * sin;
                newSideways = midnight_originalSideways * cos - midnight_originalForward * sin;

                // Debug log for scaffold
                if (Math.abs(midnight_originalForward) > 0.1f || Math.abs(midnight_originalSideways) > 0.1f) {
                    Midnight.LOGGER.debug("Enhanced Scaffold MoveFix: angle=" + angleToForward +
                            "°, fwd=" + midnight_originalForward + "->" + newForward +
                            ", side=" + midnight_originalSideways + "->" + newSideways);
                }
            } else {
                // Standard movement transformation using rotation matrices
                float rotationDiff = MathHelper.wrapDegrees(serverYaw - clientYaw);
                float sinYaw = MathHelper.sin(rotationDiff * ((float)Math.PI / 180F));
                float cosYaw = MathHelper.cos(rotationDiff * ((float)Math.PI / 180F));

                // Apply rotation matrix to transform movement according to angle difference
                newForward = midnight_originalForward * cosYaw + midnight_originalSideways * sinYaw;
                newSideways = midnight_originalSideways * cosYaw - midnight_originalForward * sinYaw;

                // Debug log for significant rotation differences
                if (Math.abs(rotationDiff) > 30 &&
                        (Math.abs(midnight_originalForward) > 0.1f || Math.abs(midnight_originalSideways) > 0.1f)) {
                    Midnight.LOGGER.debug("Standard MoveFix: diff=" + rotationDiff +
                            "°, fwd=" + midnight_originalForward + "->" + newForward +
                            ", side=" + midnight_originalSideways + "->" + newSideways);
                }
            }

            // Apply transformed movement
            input.movementForward = newForward;
            input.movementSideways = newSideways;

            // Set the flag that we're using the move fix
            RotationHandler.setUsingMoveFix(true);

        } catch (Exception e) {
            // Log any errors so the game doesn't crash
            Midnight.LOGGER.error("Error in MovementFixMixin: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Inject(
            method = "travel(Lnet/minecraft/util/math/Vec3d;)V",
            at = @At("RETURN"),
            remap = true
    )
    private void midnight_afterTravel(Vec3d movementInput, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        LivingEntity self = (LivingEntity)(Object)this;

        // Only apply to the local player
        if (mc.player == null || self != mc.player) {
            return;
        }

        try {
            // Restore yaw if it was modified
            if (midnight_modifiedYaw) {
                self.setYaw(midnight_originalYaw);
                midnight_modifiedYaw = false;
            }

            // Restore movement inputs if they were modified
            if (midnight_modifiedMovement) {
                Input input = mc.player.input;
                input.movementForward = midnight_originalForward;
                input.movementSideways = midnight_originalSideways;
                midnight_modifiedMovement = false;
            }

            // Reset the flag
            RotationHandler.setUsingMoveFix(false);

        } catch (Exception e) {
            // Log any errors
            Midnight.LOGGER.error("Error in MovementFixMixin (restore): " + e.getMessage());
            e.printStackTrace();
        }
    }
}