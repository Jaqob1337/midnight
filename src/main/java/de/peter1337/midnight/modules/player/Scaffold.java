package de.peter1337.midnight.modules.player;

import de.peter1337.midnight.handler.RotationHandler;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Setting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/**
 * Clean and working Scaffold module - places blocks under the player while moving
 */
public class Scaffold extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // Settings
    private final Setting<Boolean> tower = register(new Setting<>("Tower", Boolean.TRUE, "Jump when holding space and looking down"));
    private final Setting<Boolean> autoSwitch = register(new Setting<>("AutoSwitch", Boolean.TRUE, "Switch to blocks automatically"));
    private final Setting<Boolean> safeWalk = register(new Setting<>("SafeWalk", Boolean.TRUE, "Prevent falling off edges"));
    private final Setting<Boolean> sprint = register(new Setting<>("AllowSprint", Boolean.FALSE, "Allow sprinting while scaffolding"));
    private final Setting<Float> delay = register(new Setting<>("Delay", 0.1f, 0.0f, 0.5f, "Delay between block placements (seconds)"));
    private final Setting<Boolean> rotations = register(new Setting<>("Rotations", Boolean.TRUE, "Use smooth rotations"));
    private final Setting<Float> rotationSpeed = register(new Setting<>("RotationSpeed", 0.8f, 0.1f, 1.0f, "Speed of rotations").dependsOn(rotations));

    // State
    private long lastPlaceTime = 0;
    private int originalSlot = -1;
    private boolean wasSneak = false;

    public Scaffold() {
        super("Scaffold", "Places blocks under you while moving", Category.PLAYER, "c");
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            originalSlot = mc.player.getInventory().selectedSlot;
            wasSneak = mc.options.sneakKey.isPressed();
        }
        lastPlaceTime = 0;
    }

    @Override
    public void onDisable() {
        // Reset sneak state
        if (wasSneak != mc.options.sneakKey.isPressed()) {
            mc.options.sneakKey.setPressed(wasSneak);
        }

        // Reset sprint if needed
        if (mc.player != null && !sprint.getValue() && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

        // Cancel any active rotations
        if (rotations.getValue()) {
            RotationHandler.resetRotations();
        }
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Handle movement restrictions
        handleMovement();

        // Check if we can place (delay check)
        if (!canPlace()) return;

        // Find the position to place a block
        BlockPos placePos = getPlacePosition();
        if (placePos == null) return;

        // Find block to place
        int blockSlot = findBlockSlot();
        if (blockSlot == -1) return;

        // Find where to place against
        BlockPlacement placement = findPlacement(placePos);
        if (placement == null) return;

        // Switch to block if needed
        boolean switched = false;
        if (autoSwitch.getValue() && mc.player.getInventory().selectedSlot != blockSlot) {
            mc.player.getInventory().selectedSlot = blockSlot;
            switched = true;
        }

        // Apply rotations if enabled
        if (rotations.getValue()) {
            float[] targetRotations = calculateLookAt(placement.hitVec);
            RotationHandler.requestRotation(
                    targetRotations[0], targetRotations[1],
                    100, // priority
                    200, // duration
                    true, // silent
                    false, // bodyOnly
                    true, // moveFix
                    rotationSpeed.getValue(),
                    null // callback
            );
        }

        // Place the block
        placeBlock(placement);
        lastPlaceTime = System.currentTimeMillis();

        // Switch back if needed
        if (switched) {
            mc.player.getInventory().selectedSlot = originalSlot;
        }
    }

    /**
     * Handle movement-related logic (tower, sprint, safewalk)
     */
    private void handleMovement() {
        if (mc.player == null) return;

        // Tower logic
        if (tower.getValue() && mc.options.jumpKey.isPressed() && mc.player.getPitch() > 60) {
            if (mc.player.isOnGround()) {
                mc.player.jump();
            }
        }

        // Sprint control
        if (!sprint.getValue() && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

        // SafeWalk logic
        if (safeWalk.getValue()) {
            boolean shouldSneak = false;

            // Check if we're at an edge
            if (mc.player.isOnGround()) {
                Vec3d playerPos = mc.player.getPos();
                BlockPos belowPos = new BlockPos((int) Math.floor(playerPos.x),
                        (int) Math.floor(playerPos.y) - 1,
                        (int) Math.floor(playerPos.z));

                if (mc.world.getBlockState(belowPos).isAir()) {
                    shouldSneak = true;
                }
            }

            mc.options.sneakKey.setPressed(shouldSneak);
        }
    }

    /**
     * Check if enough time has passed to place another block
     */
    private boolean canPlace() {
        long currentTime = System.currentTimeMillis();
        long delayMs = (long) (delay.getValue() * 1000);
        return currentTime - lastPlaceTime >= delayMs;
    }

    /**
     * Find the position where we need to place a block
     */
    private BlockPos getPlacePosition() {
        if (mc.player == null) return null;

        Vec3d playerPos = mc.player.getPos();

        // Look ahead based on movement direction
        Vec3d velocity = mc.player.getVelocity();
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        // If not moving much, just check directly below
        if (speed < 0.1) {
            BlockPos belowPos = new BlockPos((int) Math.floor(playerPos.x),
                    (int) Math.floor(playerPos.y) - 1,
                    (int) Math.floor(playerPos.z));

            if (mc.world.getBlockState(belowPos).isAir()) {
                return belowPos;
            }
            return null;
        }

        // Look ahead in movement direction
        Vec3d lookAhead = playerPos.add(velocity.multiply(0.5)); // Look 0.5 blocks ahead
        BlockPos lookPos = new BlockPos((int) Math.floor(lookAhead.x),
                (int) Math.floor(playerPos.y) - 1,
                (int) Math.floor(lookAhead.z));

        // Check if we need a block there
        if (mc.world.getBlockState(lookPos).isAir()) {
            return lookPos;
        }

        return null;
    }

    /**
     * Find a suitable block in the hotbar
     */
    private int findBlockSlot() {
        if (mc.player == null) return -1;

        PlayerInventory inventory = mc.player.getInventory();

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                Block block = ((BlockItem) stack.getItem()).getBlock();
                if (isValidBlock(block)) {
                    return i;
                }
            }
        }

        return -1;
    }

    /**
     * Check if a block is suitable for scaffolding
     */
    private boolean isValidBlock(Block block) {
        return block != Blocks.AIR &&
                block != Blocks.WATER &&
                block != Blocks.LAVA &&
                block != Blocks.SAND &&
                block != Blocks.RED_SAND &&
                block != Blocks.GRAVEL &&
                !block.getDefaultState().hasBlockEntity();
    }

    /**
     * Find where to place the block (against which block and which face)
     */
    private BlockPlacement findPlacement(BlockPos placePos) {
        if (mc.world == null || mc.player == null) return null;

        // Check all adjacent blocks to find one we can place against
        Direction[] directions = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};

        for (Direction dir : directions) {
            BlockPos neighborPos = placePos.offset(dir);
            BlockState neighborState = mc.world.getBlockState(neighborPos);

            if (!neighborState.isAir() && neighborState.isSolidBlock(mc.world, neighborPos)) {
                // Found a solid block to place against
                Vec3d hitVec = Vec3d.ofCenter(neighborPos).add(Vec3d.of(dir.getOpposite().getVector()).multiply(0.5));

                // Make sure we can reach this position
                if (mc.player.getEyePos().distanceTo(hitVec) <= 4.5) {
                    return new BlockPlacement(neighborPos, dir.getOpposite(), hitVec);
                }
            }
        }

        return null;
    }

    /**
     * Calculate rotations to look at a specific position
     */
    private float[] calculateLookAt(Vec3d pos) {
        if (mc.player == null) return new float[]{0, 0};

        Vec3d eyePos = mc.player.getEyePos();
        double diffX = pos.x - eyePos.x;
        double diffY = pos.y - eyePos.y;
        double diffZ = pos.z - eyePos.z;

        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));

        return new float[]{
                MathHelper.wrapDegrees(yaw),
                MathHelper.clamp(pitch, -90.0f, 90.0f)
        };
    }

    /**
     * Place the block
     */
    private void placeBlock(BlockPlacement placement) {
        if (mc.interactionManager == null || mc.player == null) return;

        BlockHitResult hitResult = new BlockHitResult(
                placement.hitVec,
                placement.face,
                placement.pos,
                false
        );

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /**
     * Simple class to hold block placement data
     */
    private static class BlockPlacement {
        final BlockPos pos;
        final Direction face;
        final Vec3d hitVec;

        BlockPlacement(BlockPos pos, Direction face, Vec3d hitVec) {
            this.pos = pos;
            this.face = face;
            this.hitVec = hitVec;
        }
    }

    // Methods for other modules to check scaffold state
    public boolean isSprintAllowed() {
        return isEnabled() && sprint.getValue();
    }
}