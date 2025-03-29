package de.peter1337.midnight.mixins;

import de.peter1337.midnight.manager.ModuleManager;
import de.peter1337.midnight.modules.player.Scaffold;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin implements the "SafeWalk" feature for Scaffold
 * It prevents the player from falling off edges when Scaffold is active
 */
@Mixin(ClientPlayerEntity.class)
public class ScaffoldSafeWalkMixin {

    @Shadow
    private Input input;

    /**
     * Injects at the beginning of the move method to implement SafeWalk
     * This will prevent the player from walking off edges when active
     */
    @Inject(method = "move", at = @At("HEAD"))
    private void onMove(MovementType type, Vec3d movement, CallbackInfo ci) {
        // Only apply for regular movement
        if (type != MovementType.SELF) return;

        // Get the Scaffold module from ModuleManager
        Scaffold scaffold = (Scaffold) ModuleManager.getModule("Scaffold");

        // Check if Scaffold is active and SafeWalk is enabled
        if (scaffold != null && scaffold.isEnabled()) {
            ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;

            // If player is flying, riding, or in creative flight mode, don't apply SafeWalk
            if (player.getAbilities().flying || player.hasVehicle() || player.isSpectator()) return;

            // Check if we're on the ground and actually trying to move
            if (player.isOnGround() && (input.movementSideways != 0 || input.movementForward != 0)) {
                // Determine if we'd fall off an edge with this movement
                if (wouldFallOffEdge(player, movement)) {
                    // Zero out the horizontal movement components to prevent falling
                    double originalY = movement.y;
                    movement = new Vec3d(0, originalY, 0);
                }
            }
        }
    }

    /**
     * Checks if the player would fall off an edge with the given movement
     */
    private boolean wouldFallOffEdge(ClientPlayerEntity player, Vec3d movement) {
        // Calculate new position after movement
        double newX = player.getX() + movement.x;
        double newZ = player.getZ() + movement.z;

        // Check block below new position
        BlockPos newPos = new BlockPos((int) Math.floor(newX), (int) Math.floor(player.getY() - 0.5), (int) Math.floor(newZ));

        // If the block below is air, we would fall
        return player.clientWorld.getBlockState(newPos).isAir();
    }
}