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

    // Track the original movement inputs for the direct movement fix
    private float originalForward = 0f;
    private float originalSideways = 0f;
    private boolean movementModified = false;

    public Scaffold() {
        super("Scaffold", "Places blocks under you", Category.PLAYER, "m");
    }

    @Override
    public void onEnable() {
        lastPlacedPos = null;
        rotationsSet = false;
        movementModified = false;
    }

    @Override
    public void onDisable() {
        RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
        if (mc.player != null) {
            mc.options.sneakKey.setPressed(false); // Ensure sneak is off

            // Restore movement inputs if they were modified
            if (movementModified) {
                restoreMovement();
            }
        }
    }

    /**
     * Apply movement fix that DIRECTLY OVERRIDES movement based on key presses
     * This completely bypasses the normal movement system
     */
    private void applyMovementFix() {
        if (!moveFix.getValue() || mc.player == null) return;

        // Store original movement values if we haven't already
        if (!movementModified) {
            originalForward = mc.player.input.movementForward;
            originalSideways = mc.player.input.movementSideways;

            // Get the raw keyboard state directly from LWJGL/Minecraft options
            boolean wPressed = mc.options.forwardKey.isPressed();
            boolean sPressed = mc.options.backKey.isPressed();
            boolean aPressed = mc.options.leftKey.isPressed();
            boolean dPressed = mc.options.rightKey.isPressed();

            // COMPLETELY INVERT ALL DIRECTIONS:
            // - W key becomes BACKWARDS (negative value)
            // - S key becomes FORWARDS (positive value)
            // - A key becomes RIGHT (negative value)
            // - D key becomes LEFT (positive value)

            // Clear any existing movement values first
            mc.player.input.movementForward = 0.0f;
            mc.player.input.movementSideways = 0.0f;

            // Forward/backward movement
            if (wPressed && !sPressed) {
                // W key: force -1.0 (backward)
                mc.player.input.movementForward = -1.0f;
            } else if (sPressed && !wPressed) {
                // S key: force +1.0 (forward)
                mc.player.input.movementForward = 1.0f;
            }

            // Left/right movement
            if (aPressed && !dPressed) {
                // A key: force -1.0 (right)
                mc.player.input.movementSideways = -1.0f;
            } else if (dPressed && !aPressed) {
                // D key: force +1.0 (left)
                mc.player.input.movementSideways = 1.0f;
            }

            // Very important debug info
            System.out.println("[Scaffold] DIRECT KEY OVERRIDE: Keys W=" + wPressed + " S=" + sPressed +
                    " A=" + aPressed + " D=" + dPressed +
                    " → Set forward=" + mc.player.input.movementForward +
                    ", sideways=" + mc.player.input.movementSideways);

            // Tell the RotationHandler to completely skip any further transformations
            RotationHandler.setMoveFixContext("scaffold_direct_100");

            movementModified = true;
        }
    }

    /**
     * Restore original movement inputs
     */
    private void restoreMovement() {
        if (!movementModified || mc.player == null) return;

        mc.player.input.movementForward = originalForward;
        mc.player.input.movementSideways = originalSideways;
        movementModified = false;
    }

    /**
     * Called before player movement calculations
     */
    public void preUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // First, apply the movement fix for WASD keys - do this BEFORE any other logic
        // This makes W feel like forward even though we're building backward
        if (moveFix.getValue()) {
            applyMovementFix();
        }

        // Handle safe walk (sneaking at edges)
        handleSafeWalk();

        // Always set the bridging direction opposite to where the player is looking
        bridgeDirection = getHorizontalDirection(mc.player.getYaw());
        targetYaw = getYawFromDirection(bridgeDirection.getOpposite());

        // Try to find a placement position
        PlacementInfo placement = findPlacement();
        if (placement != null) {
            // Apply rotation for block placement - always facing backward
            if (rotations.getValue()) {
                float[] placeAngles = calculateRotation(placement.hitVec);
                targetPitch = placeAngles[1]; // Use calculated pitch for placement

                // Apply rotations for placement - always backward
                handleRotations(targetYaw, targetPitch);
            }

            // Check if we can place a block now
            if (canPlaceNow() && hasBlocks()) {
                // Perform the placement (no need to apply movement fix again)
                placeBlock(placement);

                // Handle tower jumping
                if (tower.getValue() && mc.options.jumpKey.isPressed() && mc.player.isOnGround()) {
                    mc.player.jump();
                }
            }
        } else if (rotations.getValue()) {
            // Even if no placement is needed, still apply the backward rotation
            // This is important for consistent behavior
            handleRotations(targetYaw, targetPitch);
        }
    }

    @Override
    public void onUpdate() {
        // Run another direct movement fix in the main update to ensure it sticks
        // This is a failsafe in case something else modifies movement values
        if (isEnabled() && moveFix.getValue() && mc.player != null && movementModified) {
            // Get the raw keyboard state again
            boolean wPressed = mc.options.forwardKey.isPressed();
            boolean sPressed = mc.options.backKey.isPressed();
            boolean aPressed = mc.options.leftKey.isPressed();
            boolean dPressed = mc.options.rightKey.isPressed();

            // Re-apply our overrides to ensure they stick
            if (wPressed && !sPressed) {
                mc.player.input.movementForward = -1.0f;
            } else if (sPressed && !wPressed) {
                mc.player.input.movementForward = 1.0f;
            } else {
                mc.player.input.movementForward = 0.0f;
            }

            if (aPressed && !dPressed) {
                mc.player.input.movementSideways = -1.0f;
            } else if (dPressed && !aPressed) {
                mc.player.input.movementSideways = 1.0f;
            } else {
                mc.player.input.movementSideways = 0.0f;
            }
        }
    }

    /**
     * Determines the direction the player is moving based on input
     * @return The direction of movement, or null if not moving
     */
    private Direction determineMovementDirection() {
        if (mc.player == null) return null;

        // Get the input values
        float forward = mc.player.input.movementForward;
        float sideways = mc.player.input.movementSideways;

        // If not moving, return null
        if (Math.abs(forward) < 0.1f && Math.abs(sideways) < 0.1f) {
            return null;
        }

        // Get the player's looking direction (yaw)
        float yaw = mc.player.getYaw();

        // Adjust yaw based on input direction
        // This is standard Minecraft movement logic
        if (forward < 0) {
            yaw += 180; // Backward = opposite direction
        }
        if (sideways > 0) { // Right
            yaw -= 90 * (forward == 0 ? 1 : Math.abs(forward) / forward * 0.5f);
        } else if (sideways < 0) { // Left
            yaw += 90 * (forward == 0 ? 1 : Math.abs(forward) / forward * 0.5f);
        }

        // Convert adjusted yaw to a direction
        return getHorizontalDirection(yaw);
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

        // Set the move fix context to "scaffold" for improved movement transformation
        if (useMoveFix) {
            try {
                // Try to use the new method, but handle if it's not implemented yet
                RotationHandler.setMoveFixContext("scaffold_reversed");
            } catch (Exception e) {
                // Silently ignore if the method doesn't exist yet
            }
        }

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