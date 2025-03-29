package de.peter1337.midnight.modules.player;

import de.peter1337.midnight.handler.RotationHandler;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.utils.RayCastUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Optional;

public class Scaffold extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // Last placed block for reference
    private BlockPos lastPlacedPos = null;

    // Rotation management
    private static final int ROTATION_PRIORITY = 80;
    private boolean rotationsSet = false;

    // Walking direction tracking
    private Direction bridgeDirection = null;
    private float targetYaw = 0;
    private float targetPitch = 70f; // Default looking down angle for bridging

    // Simple timer for block placement
    private long lastPlaceTime = 0;

    // Settings
    private final Setting<Boolean> rotations = register(
            new Setting<>("Rotations", Boolean.TRUE, "Look towards block placement position")
    );

    private final Setting<String> rotationMode = register(
            new Setting<>("RotationMode", "Silent",
                    Arrays.asList("Silent", "Client", "Body"),
                    "Silent: server-only, Client: visible, Body: shows on body only")
                    .dependsOn(rotations)
    );

    private final Setting<Boolean> moveFix = register(
            new Setting<>("MoveFix", Boolean.TRUE, "Corrects rotation and movement direction during scaffolding (always reverses movement)")
                    .dependsOn(rotations)
    );

    private final Setting<Boolean> tower = register(
            new Setting<>("Tower", Boolean.TRUE, "Jump automatically when holding space")
    );

    private final Setting<Boolean> safeWalk = register(
            new Setting<>("SafeWalk", Boolean.TRUE, "Prevents falling off edges")
    );

    private final Setting<Float> delay = register(
            new Setting<>("Delay", 0.0f, 0.0f, 0.5f, "Delay between placements (seconds)")
    );

    private final Setting<Boolean> autoSwitch = register(
            new Setting<>("AutoSwitch", Boolean.TRUE, "Switch to blocks automatically")
    );

    private final Setting<Boolean> strictCenter = register(
            new Setting<>("StrictCenter", Boolean.FALSE, "Place blocks exactly at center (more stable but slower)")
    );

    private final Setting<Float> maxReach = register(
            new Setting<>("MaxReach", 4.5f, 3.0f, 6.0f, "Maximum reach distance for block placement")
    );

    public Scaffold() {
        super("Scaffold", "Places blocks under you", Category.PLAYER, "m");
    }

    @Override
    public void onEnable() {
        lastPlacedPos = null;
        rotationsSet = false;

        // Initialize bridge direction based on player's current yaw
        if (mc.player != null) {
            bridgeDirection = getHorizontalDirection(mc.player.getYaw());
            targetYaw = getYawFromDirection(bridgeDirection.getOpposite());

            // Apply initial backward rotation
            applyBackwardRotation();
        }
    }

    @Override
    public void onDisable() {
        RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
        if (mc.player != null) {
            mc.options.sneakKey.setPressed(false); // Ensure sneak is off
        }
    }

    /**
     * Called before player movement calculations
     */
    public void preUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Handle safe walk (sneaking at edges)
        handleSafeWalk();

        // Always apply backward rotation - this happens regardless of whether a block is being placed
        applyBackwardRotation();

        // Try to find a placement position
        PlacementInfo placement = findPlacement();
        if (placement != null) {
            // Optionally update bridge direction based on placement
            Direction newDirection = getBridgeDirectionFromPlacement(placement);
            if (newDirection != null) {
                bridgeDirection = newDirection;
                targetYaw = getYawFromDirection(bridgeDirection.getOpposite());
            }

            // Apply rotation for block placement
            if (rotations.getValue()) {
                float[] placeAngles = calculateRotation(placement.hitVec);
                targetPitch = placeAngles[1]; // Use calculated pitch for placement

                // Apply rotations with looking down for placement but backward for movement
                handleRotations(targetYaw, targetPitch);
            }

            // Check if we can place a block now
            if (canPlaceNow() && hasBlocks()) {
                // Perform the placement
                placeBlock(placement);

                // Handle tower jumping
                if (tower.getValue() && mc.options.jumpKey.isPressed() && mc.player.isOnGround()) {
                    mc.player.jump();
                }
            }
        } else if (rotations.getValue()) {
            // Even if no placement is needed, still apply backward rotation to ensure
            // continuous movement correction while scaffolding
            handleRotations(targetYaw, targetPitch);
        }
    }

    /**
     * Main update method, called every tick
     */
    @Override
    public void onUpdate() {
        // Most functionality is in preUpdate to handle rotations before movement
    }

    /**
     * Applies backward rotation for natural bridging appearance
     */
    private void applyBackwardRotation() {
        if (mc.player == null || !rotations.getValue()) return;

        // If bridge direction isn't set yet, initialize it
        if (bridgeDirection == null) {
            bridgeDirection = getHorizontalDirection(mc.player.getYaw());
            targetYaw = getYawFromDirection(bridgeDirection.getOpposite());
        }

        // Apply backward-facing rotations (opposite to bridge direction)
        handleRotations(targetYaw, targetPitch);
    }

    /**
     * Determines the bridge direction based on block placement
     */
    private Direction getBridgeDirectionFromPlacement(PlacementInfo placement) {
        if (mc.player == null) return Direction.SOUTH;

        // Extract placement direction vector
        Vec3d placeVec = new Vec3d(
                placement.targetPos.getX() - mc.player.getX(),
                0,
                placement.targetPos.getZ() - mc.player.getZ()
        );

        // If the vector is too small, use player's facing
        if (placeVec.lengthSquared() < 0.01) {
            return getHorizontalDirection(mc.player.getYaw());
        }

        // Normalize the vector
        if (placeVec.lengthSquared() > 0) {
            placeVec = placeVec.normalize();
        }

        // Convert vector to direction
        if (Math.abs(placeVec.x) > Math.abs(placeVec.z)) {
            return placeVec.x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return placeVec.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    /**
     * Gets a horizontal direction from a yaw angle
     */
    private Direction getHorizontalDirection(float yaw) {
        yaw = MathHelper.wrapDegrees(yaw);
        if (yaw >= -45 && yaw < 45) {
            return Direction.SOUTH; // 0 degrees
        } else if (yaw >= 45 && yaw < 135) {
            return Direction.WEST; // 90 degrees
        } else if (yaw >= 135 || yaw < -135) {
            return Direction.NORTH; // 180 degrees
        } else {
            return Direction.EAST; // 270 degrees
        }
    }

    /**
     * Gets the yaw angle for a specific direction
     */
    private float getYawFromDirection(Direction dir) {
        switch (dir) {
            case SOUTH: return 0;
            case WEST: return 90;
            case NORTH: return 180;
            case EAST: return -90;
            default: return 0;
        }
    }

    /**
     * Prevents falling off edges by sneaking automatically
     */
    private void handleSafeWalk() {
        if (!safeWalk.getValue()) return;

        BlockPos pos = mc.player.getBlockPos().down();
        boolean isSafeToWalk = !mc.world.getBlockState(pos).isAir();

        // Only enable sneaking when over air
        mc.options.sneakKey.setPressed(!isSafeToWalk);
    }

    /**
     * Checks if we are allowed to place a block now based on the delay
     */
    private boolean canPlaceNow() {
        long currentTime = System.currentTimeMillis();
        float placeDelay = delay.getValue() * 1000; // Convert to milliseconds

        return currentTime - lastPlaceTime >= placeDelay;
    }

    /**
     * Finds a suitable block placement position using RayCast utilities
     */
    private PlacementInfo findPlacement() {
        if (mc.player == null || mc.world == null) return null;

        // The position directly under the player
        BlockPos basePos = new BlockPos(
                MathHelper.floor(mc.player.getX()),
                MathHelper.floor(mc.player.getY() - 0.5),
                MathHelper.floor(mc.player.getZ())
        );

        // First check if we need to place a block
        if (!mc.world.getBlockState(basePos).isAir()) {
            return null; // Already a block here
        }

        // Try using RayCastUtil to find the best placement
        Optional<RayCastUtil.BlockPlacementInfo> rayCastPlacement =
                RayCastUtil.findBestBlockPlacement(basePos, strictCenter.getValue(), maxReach.getValue());

        if (rayCastPlacement.isPresent()) {
            RayCastUtil.BlockPlacementInfo info = rayCastPlacement.get();
            return new PlacementInfo(
                    info.getTargetPos(),
                    info.getPlaceAgainst(),
                    info.getPlaceDir(),
                    info.getHitVec()
            );
        }

        // If raycast placement failed, try to place ahead based on movement
        Vec3d velocity = mc.player.getVelocity();
        if (velocity.horizontalLengthSquared() > 0.02) {
            // Look ahead a little bit based on movement
            double lookAheadFactor = 1.0;
            BlockPos lookAheadPos = new BlockPos(
                    MathHelper.floor(mc.player.getX() + velocity.x * lookAheadFactor),
                    basePos.getY(),
                    MathHelper.floor(mc.player.getZ() + velocity.z * lookAheadFactor)
            );

            // If it's a different position and it needs a block
            if (!lookAheadPos.equals(basePos) && mc.world.getBlockState(lookAheadPos).isAir()) {
                rayCastPlacement = RayCastUtil.findBestBlockPlacement(lookAheadPos, strictCenter.getValue(), maxReach.getValue());

                if (rayCastPlacement.isPresent()) {
                    RayCastUtil.BlockPlacementInfo info = rayCastPlacement.get();
                    return new PlacementInfo(
                            info.getTargetPos(),
                            info.getPlaceAgainst(),
                            info.getPlaceDir(),
                            info.getHitVec()
                    );
                }
            }
        }

        // If RayCastUtil fails, fall back to the original method
        return findPlacementLegacy(basePos);
    }

    /**
     * Legacy method for finding placement if RayCast fails
     */
    private PlacementInfo findPlacementLegacy(BlockPos pos) {
        World world = mc.world;

        // Make sure the position is valid and needs a block
        if (!world.getBlockState(pos).isAir()) {
            return null;
        }

        // Try each direction to place against
        for (Direction dir : Direction.values()) {
            BlockPos againstPos = pos.offset(dir);
            BlockState againstState = world.getBlockState(againstPos);

            // Check if we can place against this block
            if (againstState.isAir() || !againstState.isSolidBlock(world, againstPos)) {
                continue;
            }

            // Calculate placement hit vector
            Direction placeDirection = dir.getOpposite();
            Vec3d hitVec = Vec3d.ofCenter(againstPos)
                    .add(Vec3d.of(placeDirection.getVector()).multiply(0.5));

            // Check if player can reach this position
            if (mc.player.getEyePos().squaredDistanceTo(hitVec) <= 25.0) { // 5 blocks reach
                return new PlacementInfo(pos, againstPos, placeDirection, hitVec);
            }
        }

        return null;
    }

    /**
     * Returns the hotbar slot with a valid block, or -1 if none
     */
    private int findBlockInHotbar() {
        PlayerInventory inventory = mc.player.getInventory();

        // Find blocks in hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                Block block = ((BlockItem) stack.getItem()).getBlock();

                // Skip certain blocks that shouldn't be used for bridging
                if (shouldAvoidBlock(block)) {
                    continue;
                }

                return i;
            }
        }

        return -1; // No suitable blocks found
    }

    /**
     * Checks if we have blocks to place
     */
    private boolean hasBlocks() {
        return findBlockInHotbar() != -1;
    }

    /**
     * Returns blocks that should be avoided for scaffolding
     */
    private boolean shouldAvoidBlock(Block block) {
        // Blocks that shouldn't be used for scaffolding
        return block == Blocks.TNT ||
                block == Blocks.CHEST ||
                block == Blocks.TRAPPED_CHEST ||
                block == Blocks.ENDER_CHEST ||
                block == Blocks.ANVIL ||
                block == Blocks.ENCHANTING_TABLE ||
                block == Blocks.SAND ||
                block == Blocks.GRAVEL;
    }

    /**
     * Handles the rotation to place a block
     */
    private void handleRotations(float yaw, float pitch) {
        boolean isSilent = rotationMode.getValue().equals("Silent");
        boolean isBody = rotationMode.getValue().equals("Body");
        boolean useMoveFix = (isSilent || isBody) && moveFix.getValue();

        if (isSilent) {
            // Silent rotations - send to server but don't show on client
            RotationHandler.requestRotation(
                    yaw, pitch, ROTATION_PRIORITY, 100, true, false, useMoveFix, null
            );
        } else if (isBody) {
            // Body rotations - send to server and show on player's body model
            RotationHandler.requestRotation(
                    yaw, pitch, ROTATION_PRIORITY, 100, true, true, useMoveFix, null
            );
        } else if (rotationMode.getValue().equals("Client")) {
            // Visible client rotations - no need for MoveFix
            RotationHandler.requestRotation(
                    yaw, pitch, ROTATION_PRIORITY, 100, false, false, false, null
            );
        }

        rotationsSet = true;
    }

    /**
     * Calculates rotation angles to look at a position using RayCastUtil
     */
    private float[] calculateRotation(Vec3d pos) {
        // Use RayCastUtil's calculation which has better accuracy
        return RayCastUtil.calculateLookAt(pos);
    }

    /**
     * Places a block at the given position
     */
    private void placeBlock(PlacementInfo placement) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Find and select a block
        int blockSlot = findBlockInHotbar();
        if (blockSlot == -1) return;

        int prevSlot = mc.player.getInventory().selectedSlot;

        // Switch to the block slot if needed
        if (autoSwitch.getValue()) {
            mc.player.getInventory().selectedSlot = blockSlot;
        }

        // Create the block hit result
        BlockHitResult hitResult = new BlockHitResult(
                placement.hitVec,
                placement.placeDirection,
                placement.supportPos,
                false
        );

        // Place the block
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);

        // Reset slot if needed
        if (autoSwitch.getValue() && prevSlot != blockSlot) {
            mc.player.getInventory().selectedSlot = prevSlot;
        }

        // Update timer and last placed position
        lastPlaceTime = System.currentTimeMillis();
        lastPlacedPos = placement.targetPos;
    }

    /**
     * Check if sprint is allowed (for integration with Sprint module)
     */
    public boolean isSprintAllowed() {
        return false; // Disable sprint while scaffolding for more legit movement
    }

    /**
     * Data class for block placement information
     */
    private static class PlacementInfo {
        private final BlockPos targetPos;      // Where the block will be placed
        private final BlockPos supportPos;     // The block we're placing against
        private final Direction placeDirection; // The face of the support block
        private final Vec3d hitVec;            // The exact point to click

        public PlacementInfo(BlockPos targetPos, BlockPos supportPos,
                             Direction placeDirection, Vec3d hitVec) {
            this.targetPos = targetPos;
            this.supportPos = supportPos;
            this.placeDirection = placeDirection;
            this.hitVec = hitVec;
        }
    }
}