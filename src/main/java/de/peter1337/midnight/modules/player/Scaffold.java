package de.peter1337.midnight.modules.player;

import de.peter1337.midnight.handler.RotationHandler;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Setting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public class Scaffold extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // Rotation management
    private static final int ROTATION_PRIORITY = 80;
    private boolean rotationsSet = false;
    private Direction bridgeDirection = null;
    private boolean isTowering = false;

    // Settings
    private final Setting<Boolean> rotations = register(
            new Setting<>("Rotations", Boolean.TRUE, "Look towards block placement position")
    );

    private final Setting<String> rotationMode = register(
            new Setting<>("RotationMode", "Silent",
                    java.util.Arrays.asList("Silent", "Client", "Body"),
                    "Silent: server-only, Client: visible, Body: shows on body only")
                    .dependsOn(rotations)
    );

    private final Setting<Boolean> moveFix = register(
            new Setting<>("MoveFix", Boolean.TRUE, "Corrects rotation and movement direction during scaffolding")
                    .dependsOn(rotations)
    );

    private final Setting<Boolean> tower = register(
            new Setting<>("Tower", Boolean.TRUE, "Jump automatically when holding space")
    );

    private final Setting<Boolean> autoSwitch = register(
            new Setting<>("AutoSwitch", Boolean.TRUE, "Switch to blocks automatically")
    );

    private final Setting<Boolean> safeWalk = register(
            new Setting<>("SafeWalk", Boolean.TRUE, "Prevents falling off edges")
    );

    // Track the original movement inputs for the movement fix
    private float originalForward = 0f;
    private float originalSideways = 0f;
    private boolean movementModified = false;

    // Cooldown system
    private long lastPlaceTime = 0;
    private static final long PLACE_COOLDOWN = 150; // Balanced cooldown

    // Track ground state transitions
    private long lastInAirTime = 0;
    private static final long GROUND_TRANSITION_COOLDOWN = 300; // Cooldown after being in air (ms)

    public Scaffold() {
        super("Scaffold", "Places blocks under you", Category.PLAYER, "m");
    }

    @Override
    public void onEnable() {
        rotationsSet = false;
        movementModified = false;
        lastInAirTime = 0;
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
     * Public accessor method for the movement fix setting
     * Used by ScaffoldInputMixin to check if movement fix should be applied
     */
    public boolean isMovingFixEnabled() {
        return isEnabled() && moveFix.getValue();
    }

    /**
     * Apply movement fix with anti-detection improvements
     */
    private void applyMovementFix() {
        if (!moveFix.getValue() || mc.player == null || mc.player.input == null) return;

        // Store original movement values if we haven't already
        if (!movementModified) {
            originalForward = mc.player.input.movementForward;
            originalSideways = mc.player.input.movementSideways;

            // Early exit if not moving
            if (Math.abs(originalForward) < 0.01f && Math.abs(originalSideways) < 0.01f) {
                movementModified = true;
                return;
            }

            // Get player's facing direction
            bridgeDirection = getHorizontalDirection(mc.player.getYaw());
            float targetYaw = getYawFromDirection(bridgeDirection.getOpposite());

            // Calculate rotation difference with small variation to avoid detection
            float lookYaw = mc.player.getYaw();
            float rotationDiff = MathHelper.wrapDegrees(targetYaw - lookYaw);

            // Add tiny variation to rotationDiff to avoid pattern detection
            if (Math.random() > 0.7) {
                rotationDiff += (float)(Math.random() * 0.2f - 0.1f);
            }

            // Apply transformation using rotation matrix
            float sinYaw = MathHelper.sin(rotationDiff * ((float)Math.PI / 180F));
            float cosYaw = MathHelper.cos(rotationDiff * ((float)Math.PI / 180F));

            float newForward = originalForward * cosYaw - originalSideways * sinYaw;
            float newSideways = originalSideways * cosYaw + originalForward * sinYaw;

            // Apply small imperfections to movement occasionally to appear more human-like
            if (Math.random() > 0.9) {
                newForward *= 0.98f + (float)(Math.random() * 0.04f);
                newSideways *= 0.98f + (float)(Math.random() * 0.04f);
            }

            // Apply the transformed movement
            mc.player.input.movementForward = newForward;
            mc.player.input.movementSideways = newSideways;

            // Set the movefix context for RotationHandler
            // Using regular "scaffold" context for both to reduce SimulationFail flags
            RotationHandler.setMoveFixContext("scaffold");

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

        // Track when player was last in air (not on ground)
        if (!mc.player.isOnGround()) {
            lastInAirTime = System.currentTimeMillis();
        }

        // Detect towering (building straight up)
        isTowering = tower.getValue() && mc.options.jumpKey.isPressed();

        // First, apply the movement fix - do this BEFORE any other logic
        if (moveFix.getValue() && !movementModified) {
            applyMovementFix();
        }

        // Handle safe walk (sneaking at edges)
        handleSafeWalk();

        // Apply rotations for block placement
        if (rotations.getValue()) {
            float targetYaw;
            float targetPitch;

            if (isTowering) {
                // When towering, look straight down
                targetYaw = mc.player.getYaw();
                targetPitch = 90.0f;
            } else {
                // Normal scaffolding, look behind player
                bridgeDirection = getHorizontalDirection(mc.player.getYaw());
                targetYaw = getYawFromDirection(bridgeDirection.getOpposite());
                targetPitch = 75.0f; // Looking down
            }

            handleRotations(targetYaw, targetPitch);
        }

        // Find placement position and place block
        PlacementInfo placement = findPlacement();
        if (placement != null && canPlace() && hasBlocks()) {
            placeBlock(placement);

            // Handle tower jumping with anticheat-friendly approach
            if (isTowering && mc.player.isOnGround()) {
                mc.player.jump();

                // Add a slightly stronger boost to ensure consistent towering
                // This is still well below what most anticheats will detect
                if (tower.getValue()) {
                    mc.player.addVelocity(0, 0.02f, 0);
                }
            }
        }
    }

    @Override
    public void onUpdate() {
        // Run movement fix again in the main update to ensure consistent application
        if (isEnabled() && moveFix.getValue() && mc.player != null && !movementModified) {
            applyMovementFix();
        }
    }

    /**
     * Handle rotations for block placement with better anti-detection
     */
    private void handleRotations(float yaw, float pitch) {
        boolean isSilent = rotationMode.getValue().equals("Silent");
        boolean isBody = rotationMode.getValue().equals("Body");
        boolean useMoveFix = (isSilent || isBody) && moveFix.getValue();

        // IMPORTANT: Fix for AimModulo360 - ensure rotation is not exactly modulo 360
        // Make sure rotations don't fall on exact degrees
        float randomYaw = yaw;
        float randomPitch = pitch;

        // Intentionally make all rotations non-integer values with subtle randomization
        if (Math.abs(randomYaw - Math.round(randomYaw)) < 0.05f) {
            randomYaw += 0.13f;
        }

        if (Math.abs(randomPitch - Math.round(randomPitch)) < 0.05f) {
            randomPitch += 0.17f;
        }

        // Make sure pitch stays in valid range
        randomPitch = MathHelper.clamp(randomPitch, -90.0f, 90.0f);

        // Use a moderate hold time and speed
        long holdTime = 100;
        float speed = 0.6f;

        if (isSilent) {
            RotationHandler.requestRotation(
                    randomYaw, randomPitch, ROTATION_PRIORITY, holdTime, true, false, useMoveFix, speed, null
            );
        } else if (isBody) {
            RotationHandler.requestRotation(
                    randomYaw, randomPitch, ROTATION_PRIORITY, holdTime, true, true, useMoveFix, speed, null
            );
        } else {
            RotationHandler.requestRotation(
                    randomYaw, randomPitch, ROTATION_PRIORITY, holdTime, false, false, false, speed, null
            );
        }

        rotationsSet = true;
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
     * Find place to put a block
     */
    private PlacementInfo findPlacement() {
        if (mc.player == null || mc.world == null) return null;

        // Get the position directly under the player
        Vec3d playerPos = mc.player.getPos();
        BlockPos basePos = new BlockPos(
                MathHelper.floor(playerPos.x),
                MathHelper.floor(playerPos.y - 0.5),
                MathHelper.floor(playerPos.z)
        );

        // Check if we need to place a block
        if (!mc.world.getBlockState(basePos).isAir()) {
            return null; // Already a block here
        }

        // Try all directions to place against
        for (Direction dir : Direction.values()) {
            BlockPos placeAgainst = basePos.offset(dir);

            // Check if we can place against this block
            if (!mc.world.getBlockState(placeAgainst).isAir() &&
                    mc.world.getBlockState(placeAgainst).isSolidBlock(mc.world, placeAgainst)) {

                Direction placeDir = dir.getOpposite();

                // Calculate hit vector
                Vec3d hitVec = Vec3d.ofCenter(placeAgainst)
                        .add(Vec3d.of(placeDir.getVector()).multiply(0.5));

                // Check if player can reach
                if (mc.player.getEyePos().squaredDistanceTo(hitVec) <= 25.0) { // ~5 block reach
                    return new PlacementInfo(basePos, placeAgainst, placeDir, hitVec);
                }
            }
        }

        return null;
    }

    /**
     * Simple block placement with ground state transition fix
     */
    private void placeBlock(PlacementInfo placement) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Make sure rotation is active before placing
        if (rotations.getValue() && !RotationHandler.isRotationActive()) {
            return;
        }

        // Special handling for towering - much more permissive to ensure it works
        boolean isToweringNow = tower.getValue() && mc.options.jumpKey.isPressed();

        if (isToweringNow) {
            // FOR TOWERING: Simplified approach - place block in almost all cases
            // Only avoid placing when moving upward very quickly, as that's when anticheat is most sensitive
            double verticalSpeed = mc.player.getVelocity().y;
            if (verticalSpeed > 0.2) { // Only skip during the fastest part of the upward jump
                return;
            }

            // Place block in all other cases, including:
            // - On the ground
            // - At jump peak
            // - During fall
            // This ensures towering always works
        } else {
            // NORMAL SCAFFOLDING (not towering)
            // Regular anti-cheat protections for horizontal bridging

            // 1. Never place blocks while in the air
            if (!mc.player.isOnGround()) {
                lastInAirTime = System.currentTimeMillis();
                return;
            }

            // 2. Don't place blocks right after landing
            long timeSinceInAir = System.currentTimeMillis() - lastInAirTime;
            if (timeSinceInAir < GROUND_TRANSITION_COOLDOWN) {
                return;
            }

            // 3. Check vertical motion to detect jumps/falls
            double verticalSpeed = mc.player.getVelocity().y;

            // "pre-flying" - about to jump or already moving upward
            if (verticalSpeed > 0.03) {
                return;
            }

            // "post-flying" - just landed or falling
            if (verticalSpeed < -0.08) {
                return;
            }

            // 4. Special creative mode checks
            if (mc.player.getAbilities().allowFlying) {
                // Strict ground check for creative mode
                if (!mc.player.isOnGround() || Math.abs(verticalSpeed) > 0.02) {
                    return;
                }
            }
        }

        // Cancel if sprint is active
        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

        // Find a block to place
        int blockSlot = findBlockInHotbar();
        if (blockSlot == -1) return;

        int prevSlot = mc.player.getInventory().selectedSlot;

        // Switch to the block slot if needed
        if (autoSwitch.getValue()) {
            mc.player.getInventory().selectedSlot = blockSlot;
        }

        // Small randomization to hit vector to avoid DuplicateRotPlace
        Vec3d randomizedHitVec = placement.hitVec.add(
                (Math.random() - 0.5) * 0.002,
                (Math.random() - 0.5) * 0.002,
                (Math.random() - 0.5) * 0.002
        );

        // Create the block hit result
        BlockHitResult hitResult = new BlockHitResult(
                randomizedHitVec,
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

        // Set cooldown
        lastPlaceTime = System.currentTimeMillis();
    }

    /**
     * Find a block in the hotbar
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
     * Check if we have blocks to place
     */
    private boolean hasBlocks() {
        return findBlockInHotbar() != -1;
    }

    /**
     * Simple cooldown check
     */
    private boolean canPlace() {
        return System.currentTimeMillis() - lastPlaceTime >= PLACE_COOLDOWN;
    }

    /**
     * Returns blocks that should be avoided for scaffolding
     */
    private boolean shouldAvoidBlock(Block block) {
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
     * Check if sprint is allowed (for integration with Sprint module)
     */
    public boolean isSprintAllowed() {
        return false; // Disable sprint while scaffolding for more stable movement
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