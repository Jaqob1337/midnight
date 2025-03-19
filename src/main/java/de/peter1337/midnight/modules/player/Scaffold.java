package de.peter1337.midnight.modules.player;

import de.peter1337.midnight.handler.RotationHandler;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.utils.RayCastUtil;
import de.peter1337.midnight.utils.RayCastUtil.BlockPlacementInfo;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Scaffold extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private BlockPos lastPlacedBlock = null;
    private long lastPlacementTime = 0;
    private Direction lastDirection = Direction.DOWN;
    private boolean forcedSneak = false;
    private boolean isPlacing = false;

    private static final int ROTATION_PRIORITY = 80;
    private static final double MAX_REACH_DISTANCE = 4.5;

    // Settings
    private final Setting<Boolean> rotations = register(
            new Setting<>("Rotations", Boolean.TRUE, "Look at block placement position")
    );

    private final Setting<String> rotationMode = register(
            new Setting<>("RotationMode", "Silent",
                    Arrays.asList("Silent", "Client", "Body"),
                    "How to handle rotation")
                    .dependsOn(rotations)
    );

    private final Setting<Boolean> tower = register(
            new Setting<>("Tower", Boolean.TRUE, "Automatically jump when holding space")
    );

    private final Setting<Boolean> safeWalk = register(
            new Setting<>("SafeWalk", Boolean.TRUE, "Prevents falling off edges")
    );

    private final Setting<Boolean> sprint = register(
            new Setting<>("Sprint", Boolean.TRUE, "Continue sprinting while scaffolding")
    );

    private final Setting<Boolean> autoSwitch = register(
            new Setting<>("AutoSwitch", Boolean.TRUE, "Switch to blocks automatically")
    );

    private final Setting<Float> delay = register(
            new Setting<>("Delay", 0.0f, 0.0f, 1.0f, "Delay between block placements (seconds)")
    );

    private final Setting<Boolean> intelligent = register(
            new Setting<>("Intelligent", Boolean.TRUE, "Intelligent block placement")
    );

    private final Setting<Boolean> strictCenter = register(
            new Setting<>("StrictCenter", Boolean.FALSE, "Place blocks more precisely in the center")
    );

    private final Setting<Boolean> advancedRaycast = register(
            new Setting<>("AdvancedRaycast", Boolean.TRUE, "Use advanced raycasting for better placements")
    );

    // Rotation tracking
    private boolean rotating = false;
    private BlockPlacementInfo currentPlacement = null;

    public Scaffold() {
        super("Scaffold", "Automatically places blocks under you", Category.PLAYER, "m");
    }

    @Override
    public void onEnable() {
        lastDirection = Direction.DOWN;
        lastPlacedBlock = null;
        currentPlacement = null;
        isPlacing = false;
    }

    @Override
    public void onDisable() {
        // Cancel rotations when disabling
        RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);

        // Reset player sneak state if we modified it
        if (forcedSneak && mc.player != null) {
            mc.player.setSneaking(false);
            forcedSneak = false;
        }

        currentPlacement = null;
        isPlacing = false;
    }

    /**
     * Checks if sprinting is allowed by this module
     * @return true if sprinting is allowed, false if sprinting should be disabled
     */
    public boolean isSprintAllowed() {
        return sprint.getValue();
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        ClientPlayerEntity player = mc.player;

        // Handle safeWalk - simulate sneaking to prevent falling off edges
        if (safeWalk.getValue()) {
            if (!player.isSneaking()) {
                if (player.input != null) {
                    // Try different methods to force sneaking based on the API version
                    try {
                        player.setSneaking(true);
                        forcedSneak = true;
                    } catch (Exception e) {
                        // Fallback if that method doesn't exist
                    }
                }
            }
        } else if (forcedSneak) {
            player.setSneaking(false);
            forcedSneak = false;
        }

        // Keep sprinting if enabled
        if (sprint.getValue()) {
            player.setSprinting(true);
        }

        // Handle tower (vertical movement when pressing space)
        handleTower();

        // Reset current placement if we're not actively placing
        if (!isPlacing) {
            currentPlacement = null;
        }
        isPlacing = false;

        // Get the block position where we want to place
        BlockPos targetPos = getPlacementPosition();
        if (targetPos == null) return;

        // Apply placement delay if configured
        long currentTime = System.currentTimeMillis();
        float delayMs = delay.getValue() * 1000;
        if (currentTime - lastPlacementTime < delayMs) return;

        // Check if we can place a block
        if (canPlaceBlock(targetPos)) {
            // Select a block in the hotbar if needed
            int blockSlot = findBlockInHotbar();
            if (blockSlot == -1) return; // No blocks available

            // Switch to the block slot if enabled
            int originalSlot = player.getInventory().selectedSlot;
            if (autoSwitch.getValue()) {
                player.getInventory().selectedSlot = blockSlot;
            }

            // Check if we're holding a block now
            ItemStack handStack = player.getMainHandStack();
            if (!(handStack.getItem() instanceof BlockItem)) return;

            // Find the best way to place this block
            if (advancedRaycast.getValue()) {
                // Use advanced raycasting from RayCastUtil
                placeBlockWithAdvancedRaycast(targetPos, player);
            } else {
                // Use the original placement method
                placeBlockWithSimpleRaycast(targetPos, player);
            }

            // Switch back to original slot if needed
            if (autoSwitch.getValue()) {
                player.getInventory().selectedSlot = originalSlot;
            }
        }

        // Always apply backward rotation even if we're not placing blocks
        if (rotations.getValue() && !isPlacing) {
            handleConstantRotation();
        }
    }

    /**
     * Places a block using the advanced raycasting from RayCastUtil
     */
    private void placeBlockWithAdvancedRaycast(BlockPos targetPos, ClientPlayerEntity player) {
        // Find the best placement using RayCastUtil
        Optional<BlockPlacementInfo> placementOpt = RayCastUtil.findBestBlockPlacement(
                targetPos, strictCenter.getValue(), MAX_REACH_DISTANCE);

        if (placementOpt.isEmpty()) {
            // Fallback to simple raycast if advanced fails
            placeBlockWithSimpleRaycast(targetPos, player);
            return;
        }

        BlockPlacementInfo placement = placementOpt.get();
        currentPlacement = placement;

        // Apply rotations if enabled
        if (rotations.getValue()) {
            float[] rotationAngles = placement.getRotations();
            applyRotation(rotationAngles[0], rotationAngles[1]);
        }

        // Create hit result for the placement
        BlockHitResult hitResult = new BlockHitResult(
                placement.getHitVec(),
                placement.getPlaceDir(),
                placement.getPlaceAgainst(),
                false
        );

        // Double-check that we can actually place here
        if (mc.world.getBlockState(placement.getPlaceAgainst()).isAir()) {
            // The supporting block is air, can't place against it
            // Try simple method as fallback
            placeBlockWithSimpleRaycast(targetPos, player);
            return;
        }

        // Interact with the block
        try {
            mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
            player.swingHand(Hand.MAIN_HAND);

            // Record placement information
            lastPlacedBlock = targetPos;
            lastPlacementTime = System.currentTimeMillis();
            lastDirection = placement.getPlaceDir();
            isPlacing = true;
        } catch (Exception e) {
            // If anything goes wrong, try the simple method
            placeBlockWithSimpleRaycast(targetPos, player);
        }
    }

    /**
     * Places a block using the original simple raycasting method
     */
    private void placeBlockWithSimpleRaycast(BlockPos targetPos, ClientPlayerEntity player) {
        // Determine best placement direction
        BlockHitResult hitResult = findBestPlacement(targetPos);
        if (hitResult == null) return;

        // Apply rotations if needed
        if (rotations.getValue()) {
            // Calculate rotations to look at the hit position
            float[] rotations = RayCastUtil.calculateLookAt(hitResult.getPos());
            applyRotation(rotations[0], rotations[1]);
        }

        try {
            // Place the block
            mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
            player.swingHand(Hand.MAIN_HAND);

            // Record placement information
            lastPlacedBlock = targetPos;
            lastPlacementTime = System.currentTimeMillis();
            lastDirection = hitResult.getSide();
            isPlacing = true;
        } catch (Exception e) {
            // Just in case of any error
        }
    }

    /**
     * Applies the appropriate rotation based on the selected rotation mode
     */
    private void applyRotation(float yaw, float pitch) {
        // Determine rotation mode parameters
        boolean silent;
        boolean bodyOnly;

        switch (rotationMode.getValue()) {
            case "Silent":
                // Server-only rotation, camera doesn't move
                silent = true;
                bodyOnly = false;
                break;

            case "Client":
                // Full visible rotation, camera moves
                silent = false;
                bodyOnly = false;
                break;

            case "Body":
                // Body rotation but camera stays still
                silent = true;
                bodyOnly = true;
                break;

            default:
                // Default to silent mode
                silent = true;
                bodyOnly = false;
                break;
        }

        // Request the rotation
        RotationHandler.requestRotation(
                yaw,
                pitch,
                ROTATION_PRIORITY,
                100, // Keep rotations active longer
                silent,
                bodyOnly,
                false, // No move fix
                state -> rotating = true
        );
    }

    /**
     * Handles tower feature (vertical movement when space is pressed)
     */
    private void handleTower() {
        if (!tower.getValue() || mc.player == null) return;

        if (mc.options.jumpKey.isPressed() && mc.player.isOnGround()) {
            // Simply jump when on ground
            mc.player.jump();
        }
    }

    /**
     * Handles constant backwards rotation regardless of block placement
     */
    private void handleConstantRotation() {
        // Calculate backwards rotation (180 degrees from player's current yaw)
        float backwardsYaw = mc.player.getYaw() + 180f;
        float downPitch = 80f; // Looking almost straight down

        // Apply the rotation
        applyRotation(backwardsYaw, downPitch);
    }

    /**
     * Gets the position where a block should be placed
     */
    private BlockPos getPlacementPosition() {
        if (mc.player == null) return null;

        Vec3d pos = mc.player.getPos();

        // Determine placement position based on player's movement direction
        if (intelligent.getValue() && isMoving()) {
            // Calculate player movement direction vectors
            Vec3d motion = getMovementDirection().normalize().multiply(0.45);

            // Project movement onto the XZ plane and find the position in front of player
            Vec3d projectedPos = pos.add(motion.x, 0, motion.z);
            BlockPos blockPos = new BlockPos((int)Math.floor(projectedPos.x), (int)Math.floor(pos.y) - 1, (int)Math.floor(projectedPos.z));

            // If the forward position is already occupied, try the current position
            if (!mc.world.getBlockState(blockPos).isAir()) {
                return new BlockPos((int)Math.floor(pos.x), (int)Math.floor(pos.y) - 1, (int)Math.floor(pos.z));
            }

            return blockPos;
        } else {
            // Simple placement directly under the player
            return new BlockPos((int)Math.floor(pos.x), (int)Math.floor(pos.y) - 1, (int)Math.floor(pos.z));
        }
    }

    /**
     * Determines if the player is moving
     */
    private boolean isMoving() {
        Input input = mc.player.input;
        return input.movementForward != 0 || input.movementSideways != 0;
    }

    /**
     * Gets the player's movement direction as a vector
     */
    private Vec3d getMovementDirection() {
        Input input = mc.player.input;

        // Get movement input values
        float forward = input.movementForward;
        float sideways = input.movementSideways;
        float yaw = mc.player.getYaw();

        // Calculate sin and cos of yaw (direction player is facing)
        float sinYaw = MathHelper.sin(yaw * 0.017453292F);
        float cosYaw = MathHelper.cos(yaw * 0.017453292F);

        // Calculate the movement vector components
        double motionX = (double)(sideways * cosYaw - forward * sinYaw);
        double motionZ = (double)(forward * cosYaw + sideways * sinYaw);

        return new Vec3d(motionX, 0, motionZ);
    }

    /**
     * Checks if a block can be placed at the given position
     */
    private boolean canPlaceBlock(BlockPos pos) {
        // Check if position is valid
        if (pos == null || mc.world == null) return false;

        // Check if the block at the position is replaceable (air, water, etc.)
        return mc.world.getBlockState(pos).isReplaceable();
    }

    /**
     * Finds the best direction to place a block against (simple method)
     */
    private BlockHitResult findBestPlacement(BlockPos pos) {
        // Try all possible placement directions
        Direction[] tryOrder = new Direction[]{
                Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP
        };

        // Prioritize last successful direction first for better continuity
        if (lastDirection != null) {
            tryOrder = reorderDirections(tryOrder, lastDirection);
        }

        Vec3d eyePos = mc.player.getEyePos();
        BlockHitResult bestHit = null;
        double bestDist = Double.MAX_VALUE;

        // Try each direction
        for (Direction dir : tryOrder) {
            BlockPos offsetPos = pos.offset(dir);

            // Check if we can place against this adjacent block
            BlockState neighborState = mc.world.getBlockState(offsetPos);
            if (!neighborState.isAir() && neighborState.isSolid()) {
                // Try multiple points on the face for better placement chance
                Direction placeDir = dir.getOpposite();

                // Calculate center of face
                Vec3d faceCenter = new Vec3d(
                        offsetPos.getX() + 0.5 + placeDir.getOffsetX() * 0.5,
                        offsetPos.getY() + 0.5 + placeDir.getOffsetY() * 0.5,
                        offsetPos.getZ() + 0.5 + placeDir.getOffsetZ() * 0.5
                );

                // Try center and 4 offset points
                double[] offsets = {0.0, 0.3};
                for (double offsetX : offsets) {
                    for (double offsetY : offsets) {
                        // Skip duplicate center point
                        if (offsetX == 0.0 && offsetY == 0.0) {
                            // Calculate target with offsets
                            Vec3d target = getOffsetPoint(faceCenter, placeDir, offsetX, offsetY);
                            double dist = eyePos.distanceTo(target);

                            // Check if this is within reach
                            if (dist <= MAX_REACH_DISTANCE) {
                                BlockHitResult hit = new BlockHitResult(
                                        target,
                                        placeDir,
                                        offsetPos,
                                        false
                                );

                                // Keep track of closest hit
                                if (bestHit == null || dist < bestDist) {
                                    bestHit = hit;
                                    bestDist = dist;
                                }
                            }
                        } else {
                            // Try all 4 corners by combining positive and negative offsets
                            for (int signX = -1; signX <= 1; signX += 2) {
                                for (int signY = -1; signY <= 1; signY += 2) {
                                    // Calculate target with offsets
                                    Vec3d target = getOffsetPoint(faceCenter, placeDir,
                                            offsetX * signX, offsetY * signY);
                                    double dist = eyePos.distanceTo(target);

                                    // Check if this is within reach
                                    if (dist <= MAX_REACH_DISTANCE) {
                                        BlockHitResult hit = new BlockHitResult(
                                                target,
                                                placeDir,
                                                offsetPos,
                                                false
                                        );

                                        // Keep track of closest hit
                                        if (bestHit == null || dist < bestDist) {
                                            bestHit = hit;
                                            bestDist = dist;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return bestHit;
    }

    /**
     * Calculates an offset point on a block face
     */
    private Vec3d getOffsetPoint(Vec3d faceCenter, Direction face, double offsetX, double offsetY) {
        // Get orthogonal vectors for the face
        Vec3d xVec, yVec;

        // Different offset vectors depending on which face we're dealing with
        switch (face.getAxis()) {
            case X:
                xVec = new Vec3d(0, 1, 0);
                yVec = new Vec3d(0, 0, 1);
                break;
            case Y:
                xVec = new Vec3d(1, 0, 0);
                yVec = new Vec3d(0, 0, 1);
                break;
            case Z:
                xVec = new Vec3d(1, 0, 0);
                yVec = new Vec3d(0, 1, 0);
                break;
            default:
                xVec = new Vec3d(0, 1, 0);
                yVec = new Vec3d(0, 0, 1);
        }

        // Apply the offsets
        return faceCenter.add(
                xVec.multiply(offsetX).add(yVec.multiply(offsetY))
        );
    }

    /**
     * Reorders the direction array to prioritize a specific direction
     */
    private Direction[] reorderDirections(Direction[] directions, Direction priorityDir) {
        Direction[] result = new Direction[directions.length];
        result[0] = priorityDir;

        int index = 1;
        for (Direction dir : directions) {
            if (dir != priorityDir) {
                result[index++] = dir;
            }
        }

        return result;
    }

    /**
     * Finds a block in the player's hotbar
     */
    private int findBlockInHotbar() {
        if (mc.player == null) return -1;

        PlayerInventory inventory = mc.player.getInventory();

        // Check current slot first
        if (isValidBlock(inventory.getStack(inventory.selectedSlot))) {
            return inventory.selectedSlot;
        }

        // Check the rest of the hotbar
        for (int i = 0; i < 9; i++) {
            if (isValidBlock(inventory.getStack(i))) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Checks if the given ItemStack is a valid block for scaffolding
     */
    private boolean isValidBlock(ItemStack stack) {
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();
        if (!(item instanceof BlockItem)) return false;

        Block block = ((BlockItem) item).getBlock();

        // List of blocks that shouldn't be used for scaffolding
        List<Block> blacklist = Arrays.asList(
                Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.TRAPPED_CHEST,
                Blocks.CRAFTING_TABLE, Blocks.ANVIL, Blocks.SAND, Blocks.GRAVEL,
                Blocks.WATER, Blocks.LAVA, Blocks.FURNACE, Blocks.OAK_SIGN,
                Blocks.ENCHANTING_TABLE
        );

        return !blacklist.contains(block);
    }
}