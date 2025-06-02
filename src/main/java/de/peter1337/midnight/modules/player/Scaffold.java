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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

/**
 * Legit Scaffold Module: Places a single block where the server-side crosshair is aimed.
 */
public class Scaffold extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // --- State & Cooldown ---
    private boolean placedThisTick = false; // Prevent multiple placements per tick
    private long lastPlaceTime = 0;
    private static final long PLACE_COOLDOWN = 50; // Minimum ms between placements
    private static final double PLACEMENT_REACH = 4.5; // Max reach for placement

    // --- Grim Bypass Related --- (Kept if needed for placement itself)
    private long lastAirTime = 0;
    private static final long GROUND_TRANSITION_COOLDOWN = 250;
    private double lastVerticalVelocity = 0;
    private int placementsThisTickCycle = 0; // For bypass limit setting
    private long lastPlacementReset = 0;

    // --- Settings ---
    // Removed: rotations, moveFix, constantBackward, autoWalk
    private final Setting<Boolean> tower = register(new Setting<>("Tower", Boolean.TRUE, "Jump automatically when holding space and looking down"));
    private final Setting<Boolean> autoSwitch = register(new Setting<>("AutoSwitch", Boolean.TRUE, "Switch to blocks automatically"));
    private final Setting<Boolean> safeWalk = register(new Setting<>("SafeWalk", Boolean.TRUE, "Prevents falling off edges by sneaking"));
    private final Setting<Boolean> sprint = register(new Setting<>("AllowSprint", Boolean.FALSE, "Allow sprinting while active"));
    private final Setting<Boolean> grimBypass = register(new Setting<>("GrimBypass", Boolean.TRUE, "Apply GrimAC bypasses to placement"));
    private final Setting<Boolean> antiPreFlying = register(new Setting<>("AntiPreFlying", Boolean.TRUE, "Prevents placing blocks during jumps").dependsOn(grimBypass));
    private final Setting<Boolean> placementLimit = register(new Setting<>("PlacementLimit", Boolean.TRUE, "Limits placements per tick cycle").dependsOn(grimBypass));
    private final Setting<Float> delay = register(new Setting<>("Delay", 0.05f, 0.0f, 0.5f, "Min delay between placements (seconds)"));

    public Scaffold() {
        super("Scaffold", "Places single blocks legitly", Category.PLAYER, "m");
    }

    @Override
    public void onEnable() {
        placedThisTick = false;
        lastPlaceTime = 0;
        lastAirTime = 0;
        lastVerticalVelocity = 0;
        placementsThisTickCycle = 0;
        lastPlacementReset = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        // Release keys if needed
        if (mc.player != null) {
            if (safeWalk.getValue() && mc.options.sneakKey.isPressed()) {
                mc.options.sneakKey.setPressed(false);
            }
        }
        // No rotations to cancel as this module doesn't force them
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        placedThisTick = false; // Reset placement flag each tick

        // --- Tracking & Setup ---
        if (!mc.player.isOnGround()) lastAirTime = System.currentTimeMillis();
        lastVerticalVelocity = mc.player.getVelocity().y;
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPlacementReset > 50) { // Reset placement cycle counter every 50ms
            placementsThisTickCycle = 0;
            lastPlacementReset = currentTime;
        }

        // --- Movement Controls ---
        boolean isTowering = tower.getValue() && mc.options.jumpKey.isPressed() && mc.player.getPitch() > 70; // Tower only if looking down
        if (safeWalk.getValue() && !isTowering) {
            BlockPos safePos = mc.player.getBlockPos().down();
            boolean blockBelowSolid = !mc.world.getBlockState(safePos).isAir() && mc.world.getBlockState(safePos).isSolidBlock(mc.world, safePos);
            mc.options.sneakKey.setPressed(!blockBelowSolid);
        } else if (safeWalk.getValue() && mc.options.sneakKey.isPressed()) {
            mc.options.sneakKey.setPressed(false);
        }
        if (!sprint.getValue() && mc.player.isSprinting()) mc.player.setSprinting(false);

        // --- Towering Jump --- (Simple version, might need refinement)
        if (isTowering && mc.player.isOnGround()) {
            long timeSinceAir = System.currentTimeMillis() - lastAirTime;
            if (!grimBypass.getValue() || timeSinceAir > GROUND_TRANSITION_COOLDOWN) {
                mc.player.jump();
                lastAirTime = System.currentTimeMillis();
            }
        }

        // --- Legit Placement Logic ---
        if (!canPlaceBlock()) return; // Check cooldown based on delay setting

        // Perform Raycast using SERVER-SIDE rotations from RotationHandler
        Vec3d eyePos = mc.player.getEyePos();
        float serverYaw = RotationHandler.getServerYaw();
        float serverPitch = RotationHandler.getServerPitch();
        Vec3d lookVec = RotationHandler.getVectorForRotation(serverPitch, serverYaw); // Assumes method exists in RotationHandler
        Vec3d endPos = eyePos.add(lookVec.multiply(PLACEMENT_REACH));

        RaycastContext context = new RaycastContext(eyePos, endPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        BlockHitResult raycastResult = mc.world.raycast(context);

        // Check if raycast hit a valid block face
        if (raycastResult.getType() == HitResult.Type.BLOCK) {
            BlockPos hitBlock = raycastResult.getBlockPos();
            Direction hitFace = raycastResult.getSide();
            BlockPos placePos = hitBlock.offset(hitFace); // Position where the new block will go

            // Check if the spot is valid to place in (replaceable, not inside player)
            if (isValidPlacementPosition(placePos)) {
                // Check GrimAC bypasses related to placement action
                if (grimBypass.getValue()) {
                    if (placementLimit.getValue() && placementsThisTickCycle >= 1) return; // Check placement limit
                    if (antiPreFlying.getValue() && (lastVerticalVelocity > 0.1 || lastVerticalVelocity < -0.4) && !mc.player.isOnGround()) return;
                    long timeSinceAir = System.currentTimeMillis() - lastAirTime;
                    if (timeSinceAir < GROUND_TRANSITION_COOLDOWN && !mc.player.isOnGround() && !isTowering) return;
                }

                // Check inventory
                int blockSlot = findBlockInHotbar();
                if (blockSlot != -1) {
                    // Attempt to place the block using the direct raycast result
                    placeBlockLegit(raycastResult, blockSlot);
                    placedThisTick = true; // Mark placement happened this tick
                    placementsThisTickCycle++; // Increment bypass counter
                }
            }
        }
    } // End onUpdate

    /**
     * Checks if a position is valid for block placement (replaceable and not inside player)
     */
    private boolean isValidPlacementPosition(BlockPos pos) {
        if (mc.world == null || mc.player == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isReplaceable()) return false;
        Box playerBox = mc.player.getBoundingBox().expand(-0.1); // Slightly shrink player box for check
        Box blockBox = new Box(pos);
        if (playerBox.intersects(blockBox)) return false; // Simpler intersection check
        return true;
    }

    /**
     * Place a block using the direct BlockHitResult from the raycast.
     */
    private void placeBlockLegit(BlockHitResult raycastResult, int blockSlot) {
        if (mc.player == null || mc.interactionManager == null) return;

        // --- Find Block & Switch ---
        int originalSlot = mc.player.getInventory().selectedSlot;
        boolean switched = false;
        if (autoSwitch.getValue()) {
            if (originalSlot != blockSlot) {
                mc.player.getInventory().selectedSlot = blockSlot;
                switched = true;
            }
        } else { // Check if current item is valid if not auto-switching
            ItemStack currentStack = mc.player.getMainHandStack();
            if (!(currentStack.getItem() instanceof BlockItem) || !isSuitableBlock(((BlockItem) currentStack.getItem()).getBlock())) {
                return; // Cannot place with current item and not switching
            }
        }

        // --- Optional Delay (Grim) ---
        if (grimBypass.getValue() && Math.random() < 0.2) {
            try { Thread.sleep((long)(Math.random() * 15 + 5)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        // --- Interact ---
        Hand hand = Hand.MAIN_HAND;
        mc.interactionManager.interactBlock(mc.player, hand, raycastResult); // Use direct raycast result
        mc.player.swingHand(hand);

        // --- Post-Interaction ---
        lastPlaceTime = System.currentTimeMillis(); // Update cooldown timer
        if (switched) {
            mc.player.getInventory().selectedSlot = originalSlot; // Switch back slot
        }
    }

    /**
     * Check if enough time has passed since the last placement based on delay setting.
     */
    private boolean canPlaceBlock() {
        long currentTime = System.currentTimeMillis();
        long cooldown = (long)(delay.getValue() * 1000);
        cooldown = Math.max(PLACE_COOLDOWN, cooldown); // Enforce minimum cooldown
        // Grim bypass variation removed, simple delay check now
        return currentTime - lastPlaceTime >= cooldown;
    }

    /**
     * Checks if a block is suitable for scaffolding (basic blacklist).
     */
    private boolean isSuitableBlock(Block block) {
        // Simplified blacklist - expand if needed
        if (block == Blocks.AIR || block == Blocks.WATER || block == Blocks.LAVA ||
                block.getDefaultState().hasBlockEntity() || // Most tile entities are not placeable quickly
                block == Blocks.SAND || block == Blocks.RED_SAND || block == Blocks.GRAVEL) // Falling blocks
        {
            return false;
        }
        // Check if it's a BlockItem that is actually placeable (e.g., not slabs placed weirdly)
        // More complex checks could go here if needed
        return true;
    }

    /**
     * Find the best slot in the hotbar containing suitable blocks.
     */
    private int findBlockInHotbar() {
        if (mc.player == null) return -1;
        PlayerInventory inventory = mc.player.getInventory();
        int bestSlot = -1; int maxCount = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                Block block = ((BlockItem) stack.getItem()).getBlock();
                if (isSuitableBlock(block)) {
                    if (stack.getCount() > maxCount) { maxCount = stack.getCount(); bestSlot = i; }
                }
            }
        }
        return bestSlot;
    }

    // --- Other Module Interaction Methods ---
    // Removed: isMovingFixEnabled (as moveFix setting is removed)
    // Keep isSprintAllowed
    public boolean isSprintAllowed() {
        return isEnabled() && sprint.getValue();
    }

    // Removed unused methods like getTargetBlockPos, findPlacementDirection, getHorizontalDirection, getYawFromDirection, applyRotations etc.
}