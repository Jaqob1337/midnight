package de.peter1337.midnight.modules.player;

import de.peter1337.midnight.handler.RotationHandler;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.utils.RayCastUtil;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class Scaffold extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    // Placement State
    private BlockPos lastPlacedBlock = null;
    private long lastPlacementTime = 0;
    private Direction lastPlaceDirection = null; // Keep track of the side we last placed against
    private int currentBlockSlot = -1; // Track the selected block slot
    private int originalSlot = -1; // Original slot before switching
    private boolean isPlacing = false; // Are we attempting to place a block this tick?
    private boolean placeAttemptedThisTick = false; // Flag to prevent multiple placements per tick

    // Rotation State
    private float targetYaw = 0f;
    private float targetPitch = 0f;
    private boolean rotationSet = false; // Flag if a rotation target is set for this tick

    // Tower State
    private boolean isTowering = false;
    private int towerTicks = 0; // Ticks spent towering

    // Anti-Cheat related State
    private int placementFailureCounter = 0; // Count consecutive placement failures
    private final List<BlockPos> recentFailedPositions = new ArrayList<>(); // Track recently failed positions
    private long lastSuccessfulPlaceTime = 0; // Time of last successful placement for dynamic delay

    // Constants
    private static final int ROTATION_PRIORITY = 80; // Lower than Aura
    private static final double MAX_REACH_DISTANCE = 4.5; // Standard reach
    private static final int MAX_PLACEMENT_FAILURES_BEFORE_EXPAND = 3; // Lower threshold for trying expansion
    private static final int MAX_RECENT_FAILED_POSITIONS = 10; // Limit memory of failed positions

    // Settings
    private final Setting<Boolean> rotations = register(
            new Setting<>("Rotations", Boolean.TRUE, "Look towards block placement position")
    );

    private final Setting<String> rotationMode = register(
            new Setting<>("RotationMode", "Silent",
                    Arrays.asList("Silent", "Client"), // Removed 'Body' as it's often less useful/stable for Scaffold
                    "How to handle rotation (Silent recommended)")
                    .dependsOn(rotations)
    );

    // New: Rotation Speed setting for Scaffold specifically
    private final Setting<Float> rotationSpeed = register(
            new Setting<>("RotationSpeed", 0.6f, 0.1f, 1.0f, "Speed of rotation (0.1 = slow, 1.0 = fast)")
                    .dependsOn(rotations)
    );

    // New: Rotation Randomization
    private final Setting<Float> rotationRandom = register(
            new Setting<>("RotationRandom", 0.8f, 0.0f, 3.0f, "Randomness added to rotations (degrees)")
                    .dependsOn(rotations)
    );

    private final Setting<Boolean> tower = register(
            new Setting<>("Tower", Boolean.TRUE, "Jump automatically when holding space (requires ground)")
    );

    private final Setting<String> towerMode = register(
            new Setting<>("TowerMode", "NCP", Arrays.asList("NCP", "Vanilla"), "Towering method (NCP is safer)")
                    .dependsOn(tower)
    );

    private final Setting<Boolean> safeWalk = register(
            new Setting<>("SafeWalk", Boolean.TRUE, "Prevents falling off edges (can flag)")
    );

    // New: Option to only safewalk when moving
    private final Setting<Boolean> safeWalkOnlyMoving = register(
            new Setting<>("SafeWalkOnlyMoving", Boolean.TRUE, "Only apply SafeWalk when actively moving")
                    .dependsOn(safeWalk)
    );

    private final Setting<Boolean> sprint = register(
            new Setting<>("Sprint", Boolean.FALSE, "Allow sprinting (can interfere with placement/flags)") // Default false is safer
    );

    // New: Option to disable sprint *while* placing
    private final Setting<Boolean> disableSprintOnPlace = register(
            new Setting<>("DisableSprintOnPlace", Boolean.TRUE, "Temporarily disable sprint when placing a block")
                    .dependsOn(sprint)
    );

    private final Setting<Boolean> autoSwitch = register(
            new Setting<>("AutoSwitch", Boolean.TRUE, "Switch to blocks automatically")
    );

    // Changed: Variable delay range
    private final Setting<Float> minDelay = register(
            new Setting<>("MinDelay", 0.05f, 0.0f, 0.5f, "Minimum delay between placements (seconds)")
    );
    private final Setting<Float> maxDelay = register(
            new Setting<>("MaxDelay", 0.12f, 0.0f, 0.5f, "Maximum delay between placements (seconds)")
    );

    // New: Placement timing randomization style
    private final Setting<Boolean> dynamicDelay = register(
            new Setting<>("DynamicDelay", Boolean.TRUE, "Adjust delay based on placement success")
    );

    private final Setting<Boolean> intelligent = register(
            new Setting<>("Intelligent", Boolean.TRUE, "Smarter block placement based on movement")
    );

    // New: Placement Point Randomization
    private final Setting<Float> placementRandom = register(
            new Setting<>("PlacementRandom", 0.3f, 0.0f, 0.5f, "Randomness added to click position on block face (relative)")
    );

    private final Setting<Boolean> strictCenter = register(
            new Setting<>("StrictCenter", Boolean.FALSE, "Try to place blocks closer to the center of the block below (can be less reliable)")
    );

    private final Setting<Boolean> expandPlacement = register(
            new Setting<>("ExpandPlacement", Boolean.TRUE, "Check wider area if direct placement fails")
    );

    private final Setting<Integer> expandRange = register(
            new Setting<>("ExpandRange", 1, 1, 2, "How far to expand search (blocks)")
                    .dependsOn(expandPlacement)
    );

    // New: Check if supporting block is solid
    private final Setting<Boolean> checkSupport = register(
            new Setting<>("CheckSupport", Boolean.TRUE, "Ensure the block being placed against is solid")
    );

    // New: Limit placement angle (prevents placing from weird angles)
    private final Setting<Float> maxYawDifference = register(
            new Setting<>("MaxYawDifference", 75f, 30f, 180f, "Max angle difference between facing and placement")
                    .dependsOn(rotations)
    );


    public Scaffold() {
        super("Scaffold", "Places blocks under you safely", Category.PLAYER, "m");
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        originalSlot = mc.player.getInventory().selectedSlot;
        lastPlacedBlock = null;
        lastPlaceDirection = null;
        currentBlockSlot = -1;
        isPlacing = false;
        isTowering = false;
        towerTicks = 0;
        placementFailureCounter = 0;
        recentFailedPositions.clear();
        rotationSet = false;
        placeAttemptedThisTick = false;
        lastSuccessfulPlaceTime = System.currentTimeMillis(); // Initialize
    }

    @Override
    public void onDisable() {
        RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);

        if (mc.player != null) {
            // Restore original slot if switched
            if (autoSwitch.getValue() && originalSlot != -1 && mc.player.getInventory().selectedSlot != originalSlot) {
                mc.player.getInventory().selectedSlot = originalSlot;
            }
            // Ensure sneak is turned off if we forced it (use keybind state)
            mc.options.sneakKey.setPressed(false);
        }

        originalSlot = -1;
        currentBlockSlot = -1;
        isPlacing = false;
        isTowering = false;
        rotationSet = false;
        placeAttemptedThisTick = false;
    }

    /**
     * Called from Mixin before movement calculations.
     * Used for SafeWalk implementation.
     */
    public void onPreMotion() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        handleSafeWalk();
    }

    /**
     * Called every client tick (PRE). Handles finding placement, rotations, and inventory switching.
     */
    public void preUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Reset flags for this tick
        isPlacing = false;
        rotationSet = false;
        placeAttemptedThisTick = false;

        // --- Inventory Management ---
        currentBlockSlot = findBlockInHotbar();
        if (currentBlockSlot == -1) {
            // No blocks found, maybe disable or show warning?
            // For now, just stop processing.
            // Consider adding a chat message here.
            // RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY); // Cancel rotation if no blocks
            return;
        }

        // Switch slot if needed (do this early)
        if (autoSwitch.getValue() && mc.player.getInventory().selectedSlot != currentBlockSlot) {
            mc.player.getInventory().selectedSlot = currentBlockSlot;
        }

        // --- Placement Logic ---
        PlacementInfo placementInfo = findPlacement();
        if (placementInfo == null) {
            // No valid placement found, maybe just maintain backward rotation?
            if (rotations.getValue()) {
                handleIdleRotation(); // Look backwards/down slightly when not placing
            }
            return; // Stop processing if no placement is possible
        }

        // --- Rotation Handling ---
        if (rotations.getValue()) {
            handleRotations(placementInfo);
        } else {
            // If rotations are off, ensure no rotation request is active from this module
            RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
        }

        // --- Mark for Placement ---
        // Decide if we *should* attempt placement in the POST tick, based on timing and rotation stability.
        if (canAttemptPlacement(placementInfo)) {
            isPlacing = true;
            // Sprint management based on settings
            handleSprintDuringPlacement();
        } else {
            // If we can't place yet (e.g., waiting for rotation/delay), ensure sprint is normal
            restoreSprintState();
        }
    }


    /**
     * Called every client tick (POST). Handles the actual block placement interaction.
     */
    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null || !isPlacing || placeAttemptedThisTick) {
            // Don't proceed if not enabled, player/world missing, not marked for placing, or already tried this tick
            return;
        }

        PlacementInfo placementInfo = findPlacement(); // Re-fetch placement info to ensure it's still valid
        if (placementInfo == null || currentBlockSlot == -1) {
            isPlacing = false; // Cancel placement if info became invalid or no blocks
            restoreSprintState();
            return;
        }

        // Final check for rotation alignment if rotations are enabled
        if (rotations.getValue() && !isRotationAligned(placementInfo.getHitVec())) {
            isPlacing = false; // Rotation not ready, wait for next tick
            restoreSprintState();
            return;
        }

        // --- Perform Placement ---
        if (performPlacement(placementInfo)) {
            // Successful placement
            lastPlacementTime = System.currentTimeMillis();
            lastSuccessfulPlaceTime = lastPlacementTime;
            lastPlacedBlock = placementInfo.getPlacePos();
            lastPlaceDirection = placementInfo.getPlaceAgainstDirection();
            placementFailureCounter = 0; // Reset failure counter
            recentFailedPositions.remove(placementInfo.getPlacePos()); // Remove from failed list if successful
            placeAttemptedThisTick = true; // Mark that we tried placing this tick

            // Handle tower jump *after* successful placement
            handleTowerJump();

        } else {
            // Placement failed
            placementFailureCounter++;
            if (!recentFailedPositions.contains(placementInfo.getPlacePos())) {
                recentFailedPositions.add(placementInfo.getPlacePos());
                // Keep the list size manageable
                if (recentFailedPositions.size() > MAX_RECENT_FAILED_POSITIONS) {
                    recentFailedPositions.remove(0);
                }
            }
            // Maybe slightly increase delay after failure? (part of dynamic delay)
        }

        isPlacing = false; // Reset placing flag after attempt
        // Sprint state is already handled/restored in preUpdate or after placement attempt fails/succeeds
    }

    // --- Core Logic Methods ---

    /**
     * Finds the best possible block placement position and details.
     * @return PlacementInfo object or null if no valid placement found.
     */
    private PlacementInfo findPlacement() {
        if (mc.player == null || mc.world == null) return null;

        Vec3d playerPos = mc.player.getPos();
        BlockPos playerBlockPos = mc.player.getBlockPos();
        BlockPos basePos = playerBlockPos.down(); // Start with the block directly below

        // --- Determine Target Position ---
        BlockPos targetPos = basePos;

        // Intelligent placement based on movement
        if (intelligent.getValue() && isMoving()) {
            Vec3d moveVec = getMovementDirection(true); // Get predicted movement vector
            // Predict slightly ahead based on speed
            double predictionFactor = Math.max(0.4, Math.min(0.8, mc.player.getVelocity().horizontalLength() * 20.0)); // Adjust based on speed
            Vec3d predictedPos = playerPos.add(moveVec.multiply(predictionFactor));
            BlockPos projectedPos = new BlockPos((int)Math.floor(predictedPos.x), basePos.getY(), (int)Math.floor(predictedPos.z));

            // Only use projected position if it's different and empty
            if (!projectedPos.equals(basePos) && mc.world.getBlockState(projectedPos).isReplaceable() && !recentFailedPositions.contains(projectedPos)) {
                targetPos = projectedPos;
            }
            // Fallback to base position if projected is invalid or already solid
            else if (!mc.world.getBlockState(basePos).isReplaceable()) {
                // If block below is solid, try placing in front only if needed
                BlockPos frontPos = basePos.offset(mc.player.getHorizontalFacing());
                if(mc.world.getBlockState(frontPos).isReplaceable() && !recentFailedPositions.contains(frontPos)) {
                    targetPos = frontPos;
                } else {
                    // Cannot find a good intelligent position, may need expansion later
                }
            }
        }

        // --- Find Placement Details (Support Block, Direction, Hit Vector) ---
        PlacementInfo primaryPlacement = findSpecificPlacement(targetPos);

        if (primaryPlacement != null) {
            return primaryPlacement;
        }

        // --- Expansion Logic ---
        if (expandPlacement.getValue() && placementFailureCounter >= MAX_PLACEMENT_FAILURES_BEFORE_EXPAND) {
            List<BlockPos> expandedPositions = getExpandedPositions(targetPos, basePos);
            for (BlockPos expandedPos : expandedPositions) {
                PlacementInfo expandedPlacement = findSpecificPlacement(expandedPos);
                if (expandedPlacement != null) {
                    // Found a placement via expansion
                    // placementFailureCounter = 0; // Reset counter if expansion works? Maybe not, keep pressure high.
                    return expandedPlacement;
                }
            }
        }

        // --- Towering Placement (Directly Below) ---
        // Check if we need to place below for towering, even if horizontal placement failed
        if (isTowering && mc.world.getBlockState(basePos).isReplaceable()) {
            PlacementInfo towerPlacement = findSpecificPlacement(basePos);
            if (towerPlacement != null) {
                return towerPlacement;
            }
        }


        return null; // No placement found
    }

    /**
     * Tries to find valid placement details (support block, direction, hit vector) for a specific target block position.
     * @param targetPlacePos The block position where we want to place a block.
     * @return PlacementInfo or null if no valid way to place at targetPlacePos is found.
     */
    private PlacementInfo findSpecificPlacement(BlockPos targetPlacePos) {
        if (mc.player == null || mc.world == null || !mc.world.getBlockState(targetPlacePos).isReplaceable() || recentFailedPositions.contains(targetPlacePos)) {
            return null;
        }

        Vec3d eyePos = mc.player.getEyePos();

        // Iterate through possible supporting faces/blocks
        for (Direction tryDir : Direction.values()) {
            BlockPos supportBlockPos = targetPlacePos.offset(tryDir);
            BlockState supportBlockState = mc.world.getBlockState(supportBlockPos);

            // Check if the support block is valid
            if (supportBlockState.isAir() || (checkSupport.getValue() && !supportBlockState.isSolidBlock(mc.world, supportBlockPos))) {
                continue; // Cannot place against air or non-solid blocks if checkSupport is enabled
            }

            Direction placeAgainstDir = tryDir.getOpposite();

            // Calculate potential hit vectors on the face of the support block
            // Start with the center of the face
            Vec3d centerHitVec = Vec3d.ofCenter(supportBlockPos).add(
                    Vec3d.of(placeAgainstDir.getVector()).multiply(0.5)
            );

            // Try multiple points on the face, including randomized center
            List<Vec3d> potentialHitVecs = new ArrayList<>();
            potentialHitVecs.add(getRandomizedHitVec(centerHitVec, placeAgainstDir)); // Randomized center first

            // Add edge/corner points if needed (more reliable for some ACs) - optional based on strictCenter
            if (!strictCenter.getValue()) {
                potentialHitVecs.addAll(getEdgeHitVecs(centerHitVec, placeAgainstDir));
            }


            for(Vec3d hitVec : potentialHitVecs) {
                // Check reachability
                if (eyePos.distanceTo(hitVec) > MAX_REACH_DISTANCE) {
                    continue;
                }

                // Check line of sight (raycast) - Optional but good
                // BlockHitResult rayTraceResult = mc.world.raycast(new RaycastContext(eyePos, hitVec, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, mc.player));
                // if (rayTraceResult.getType() != HitResult.Type.MISS && !rayTraceResult.getBlockPos().equals(supportBlockPos)) {
                //     continue; // Blocked by another block
                // }


                // Check angle difference if rotations are enabled
                if (rotations.getValue()) {
                    float[] requiredRotations = RotationHandler.calculateLookAt(hitVec);
                    float yawDiff = MathHelper.wrapDegrees(requiredRotations[0] - RotationHandler.getServerYaw()); // Check against server yaw
                    if (Math.abs(yawDiff) > maxYawDifference.getValue()) {
                        continue; // Angle too steep, likely flagged
                    }
                }


                // If all checks pass, create PlacementInfo
                BlockHitResult hitResult = new BlockHitResult(hitVec, placeAgainstDir, supportBlockPos, false);
                return new PlacementInfo(targetPlacePos, supportBlockPos, placeAgainstDir, hitVec, hitResult);
            }
        }

        return null; // No valid support found for this target position
    }

    /**
     * Attempts to place the block based on the PlacementInfo.
     * @param placementInfo Details about the placement.
     * @return True if the interaction packet was sent, false otherwise.
     */
    private boolean performPlacement(PlacementInfo placementInfo) {
        if (mc.player == null || mc.interactionManager == null || currentBlockSlot == -1) {
            return false;
        }

        // Ensure the correct slot is selected *just before* interacting
        if (mc.player.getInventory().selectedSlot != currentBlockSlot) {
            // This should ideally not happen if preUpdate logic is correct, but double-check
            if(autoSwitch.getValue()) {
                mc.player.getInventory().selectedSlot = currentBlockSlot;
            } else {
                return false; // Cannot place if wrong slot is selected and autoSwitch is off
            }
        }

        // Ensure holding a block item
        ItemStack handStack = mc.player.getInventory().getStack(currentBlockSlot);
        if (!(handStack.getItem() instanceof BlockItem)) {
            return false;
        }

        // Perform the interaction
        try {
            Hand hand = Hand.MAIN_HAND; // Always use main hand for scaffold
            mc.interactionManager.interactBlock(mc.player, hand, placementInfo.getHitResult());

            // Swing arm client-side for visuals
            mc.player.swingHand(hand);

            return true; // Interaction packet sent (doesn't guarantee server success)
        } catch (Exception e) {
            // Log error potentially
            return false;
        }
    }

    /**
     * Checks if conditions are met to attempt placing a block in the current tick.
     * @param placementInfo The potential placement details.
     * @return True if placement should be attempted.
     */
    private boolean canAttemptPlacement(PlacementInfo placementInfo) {
        if (placementInfo == null || currentBlockSlot == -1) {
            return false;
        }

        // Check Timing / Delay
        long currentTime = System.currentTimeMillis();
        float minDel = minDelay.getValue() * 1000;
        float maxDel = Math.max(minDel, maxDelay.getValue() * 1000); // Ensure max >= min
        float calculatedDelay = minDel + random.nextFloat() * (maxDel - minDel);

        // Dynamic Delay: Increase delay slightly after failures
        if (dynamicDelay.getValue() && placementFailureCounter > 0) {
            // Increase delay proportionally to failures, up to a limit
            calculatedDelay *= (1.0f + Math.min(0.5f, placementFailureCounter * 0.1f));
        }


        if (currentTime - lastPlacementTime < calculatedDelay) {
            return false; // Too soon since last placement attempt
        }

        // Check Rotation Stability (if rotations enabled)
        // This check is now primarily done *just before* placement in onUpdate for accuracy
        // We can do a preliminary check here if needed, but the final one is more important.
        // if (rotations.getValue() && !isRotationReady(placementInfo.getHitVec())) {
        //     return false; // Rotations not settled enough yet
        // }


        return true;
    }

    // --- Rotation Methods ---

    /**
     * Handles setting the target rotation based on the placement info.
     * @param placementInfo Placement details containing the hit vector.
     */
    private void handleRotations(PlacementInfo placementInfo) {
        if (mc.player == null) return;

        // Calculate target rotations towards the randomized hit vector
        float[] baseRotations = RotationHandler.calculateLookAt(placementInfo.getHitVec());

        // Add randomization
        float randomYawOffset = (random.nextFloat() - 0.5f) * 2f * rotationRandom.getValue();
        float randomPitchOffset = (random.nextFloat() - 0.5f) * 2f * rotationRandom.getValue() * 0.7f; // Less pitch random

        targetYaw = MathHelper.wrapDegrees(baseRotations[0] + randomYawOffset);
        targetPitch = MathHelper.clamp(baseRotations[1] + randomPitchOffset, -90f, 90f);

        // --- Apply Rotation Smoothing ---
        float currentYaw = RotationHandler.getServerYaw(); // Use server yaw for silent
        float currentPitch = RotationHandler.getServerPitch(); // Use server pitch for silent
        if (rotationMode.getValue().equals("Client")) {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
        }


        float speed = rotationSpeed.getValue();
        float[] smoothedRotations = RotationHandler.smoothRotation(currentYaw, currentPitch, targetYaw, targetPitch, speed);

        // Apply GCD fix within RotationHandler's smoothRotation or apply it here
        // smoothedRotations[0] = RotationHandler.applyGCD(smoothedRotations[0], currentYaw); // Assuming applyGCD is public or called internally
        // smoothedRotations[1] = RotationHandler.applyGCD(smoothedRotations[1], currentPitch); // Assuming applyGCD is public or called internally

        // Determine rotation mode parameters
        boolean silent = rotationMode.getValue().equals("Silent");
        boolean bodyOnly = false; // Body rotation generally not ideal for scaffold precision

        // Request the rotation step
        RotationHandler.requestRotation(
                smoothedRotations[0],
                smoothedRotations[1],
                ROTATION_PRIORITY,
                60, // Duration slightly longer than a tick to ensure it holds
                silent,
                bodyOnly,
                true, // Enable MoveFix for silent rotations
                null // No specific callback needed here
        );
        rotationSet = true;
    }

    /**
     * Applies a gentle backward/downward rotation when not actively placing.
     */
    private void handleIdleRotation() {
        if (mc.player == null || !rotations.getValue() || rotationSet) return; // Don't interfere if placing rotation is set

        float idleYaw = MathHelper.wrapDegrees(mc.player.getYaw() + 180f + (random.nextFloat() - 0.5f) * 10f); // Look vaguely behind
        float idlePitch = 75f + random.nextFloat() * 10f; // Look mostly down, but vary it

        float currentYaw = RotationHandler.getServerYaw();
        float currentPitch = RotationHandler.getServerPitch();
        if (rotationMode.getValue().equals("Client")) {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
        }

        float speed = Math.max(0.1f, rotationSpeed.getValue() * 0.5f); // Slower idle rotation
        float[] smoothedRotations = RotationHandler.smoothRotation(currentYaw, currentPitch, idleYaw, idlePitch, speed);

        boolean silent = rotationMode.getValue().equals("Silent");

        RotationHandler.requestRotation(
                smoothedRotations[0],
                smoothedRotations[1],
                ROTATION_PRIORITY - 10, // Lower priority for idle rotation
                60,
                silent,
                false,
                true, // MoveFix is good for idle too
                null
        );
    }


    /**
     * Checks if the current server rotation is close enough to the target hit vector.
     * @param targetHitVec The point the placement aims for.
     * @return True if rotation is considered aligned.
     */
    private boolean isRotationAligned(Vec3d targetHitVec) {
        if (!rotationSet) return false; // No target rotation set this tick

        float[] requiredRotations = RotationHandler.calculateLookAt(targetHitVec);
        float serverYaw = RotationHandler.getServerYaw();
        float serverPitch = RotationHandler.getServerPitch();

        float yawDiff = Math.abs(MathHelper.wrapDegrees(serverYaw - requiredRotations[0]));
        float pitchDiff = Math.abs(serverPitch - requiredRotations[1]);

        // Allow a small tolerance (e.g., 5-10 degrees), maybe dependent on rotation speed?
        float tolerance = Math.max(1.0f, 15.0f * (1.0f - rotationSpeed.getValue())); // More tolerance for slower rotations

        return yawDiff <= tolerance && pitchDiff <= tolerance;
    }

    // --- Helper & Utility Methods ---

    /**
     * Finds a valid block item in the player's hotbar.
     * @return The hotbar slot index (0-8) or -1 if no suitable block is found.
     */
    private int findBlockInHotbar() {
        if (mc.player == null) return -1;
        PlayerInventory inventory = mc.player.getInventory();
        int bestSlot = -1;
        int maxCount = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (isValidBlock(stack)) {
                // Prioritize stack with more blocks? Or just first found?
                // Let's prioritize larger stacks slightly.
                if(stack.getCount() > maxCount) {
                    maxCount = stack.getCount();
                    bestSlot = i;
                }
                // Fallback to first valid if no stack has > 0 count somehow
                if(bestSlot == -1) {
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    /**
     * Checks if an ItemStack is a valid, placeable block for scaffolding.
     * @param stack The ItemStack to check.
     * @return True if it's a valid block.
     */
    private boolean isValidBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        Block block = blockItem.getBlock();
        Identifier blockId = Registries.BLOCK.getId(block); // <-- Correct way to get ID

        // Basic blacklist - expand this as needed
        String path = blockId.getPath(); // Get the string path (e.g., "oak_planks")
        return !path.contains("shulker_box") && // Avoid placing shulkers
                !path.contains("sign") &&
                !path.contains("banner") &&
                !(block instanceof FluidBlock) &&
                !(block instanceof PlantBlock) &&
                // Consider keeping FallingBlock check, might be okay sometimes but often bad for bridging
                !(block instanceof FallingBlock) &&
                block.getDefaultState().isSolid() && // Check if it's generally solid
                // Using isFullCube might be too strict for some blocks like slabs/stairs if needed later,
                // but for basic scaffold, it's usually fine.
                block.getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN);
    }
    /**
     * Determines if the player is actively moving based on input.
     * @return True if moving forward, backward, or sideways.
     */
    private boolean isMoving() {
        if (mc.player == null || mc.player.input == null) return false;
        Input input = mc.player.input;
        return input.movementForward != 0.0f || input.movementSideways != 0.0f;
    }

    /**
     * Calculates the player's intended movement direction vector based on input and yaw.
     * @param predict Use player velocity for slight prediction?
     * @return A Vec3d representing the horizontal movement direction.
     */
    private Vec3d getMovementDirection(boolean predict) {
        if (mc.player == null || mc.player.input == null) return Vec3d.ZERO;

        Input input = mc.player.input;
        double forward = input.movementForward;
        double strafe = input.movementSideways;
        float yaw = mc.player.getYaw();

        // Use velocity for prediction if requested and moving
        if (predict && (forward != 0 || strafe != 0) && mc.player.getVelocity().horizontalLengthSquared() > 0.001) {
            Vec3d velocity = mc.player.getVelocity();
            // Normalize horizontal velocity to get direction
            return new Vec3d(velocity.x, 0, velocity.z).normalize();
        }


        // Calculate direction from input if not predicting or prediction failed
        if (forward == 0.0 && strafe == 0.0) {
            return Vec3d.ZERO;
        }

        double angle = Math.toRadians(yaw);
        double motionX = -Math.sin(angle) * forward + Math.cos(angle) * strafe;
        double motionZ = Math.cos(angle) * forward + Math.sin(angle) * strafe;

        return new Vec3d(motionX, 0, motionZ).normalize();
    }

    public boolean isSprintAllowed() {
        // If the module isn't enabled, it shouldn't block sprint.
        // If it is enabled, return the value of the sprint setting.
        // Note: The Sprint module already checks if Scaffold is enabled before calling this.
        // So simply returning the setting value is sufficient here.
        return sprint.getValue();
    }


    /**
     * Generates a list of potential expanded positions around a central target.
     * @param centerPos The primary target position.
     * @param basePos The position directly under the player.
     * @return A list of candidate BlockPos for expansion.
     */
    private List<BlockPos> getExpandedPositions(BlockPos centerPos, BlockPos basePos) {
        List<BlockPos> positions = new ArrayList<>();
        int range = expandRange.getValue();
        Vec3d moveDir = getMovementDirection(false); // Use input direction

        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                if (x == 0 && z == 0) continue; // Skip center

                BlockPos offsetPos = centerPos.add(x, 0, z);

                // Check if valid and not recently failed
                if (mc.world.getBlockState(offsetPos).isReplaceable() && !recentFailedPositions.contains(offsetPos)) {
                    positions.add(offsetPos);
                }

                // Also check below the offset position if the centerPos wasn't the basePos (bridging diagonally)
                if (!centerPos.equals(basePos)) {
                    BlockPos belowOffsetPos = offsetPos.down();
                    if (mc.world.getBlockState(belowOffsetPos).isReplaceable() && !recentFailedPositions.contains(belowOffsetPos)) {
                        positions.add(belowOffsetPos);
                    }
                }
            }
        }

        // Sort by distance and alignment with movement
        positions.sort(Comparator.comparingDouble((BlockPos pos) -> {
            double distSq = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(pos));
            // Calculate alignment score (dot product with move direction)
            Vec3d dirToPos = Vec3d.ofCenter(pos).subtract(mc.player.getPos()).normalize();
            double alignment = moveDir.dotProduct(new Vec3d(dirToPos.x, 0, dirToPos.z).normalize());
            // Prioritize closer and more aligned positions (lower score is better)
            return distSq - alignment * 5; // Weight alignment significantly
        }));

        return positions;
    }

    /**
     * Adds randomization to the hit vector on a block face.
     * @param centerHitVec The center of the block face.
     * @param face The direction of the face.
     * @return A slightly randomized Vec3d on the face.
     */
    private Vec3d getRandomizedHitVec(Vec3d centerHitVec, Direction face) {
        double randomFactor = placementRandom.getValue();
        if (randomFactor <= 0.0) return centerHitVec;

        // Get vectors orthogonal to the face normal
        Vec3d u, v;
        Vec3d normal = Vec3d.of(face.getVector());

        // Find a non-parallel vector (usually Y-axis)
        Vec3d nonParallel = new Vec3d(0, 1, 0);
        if (Math.abs(normal.y) > 0.9) {
            nonParallel = new Vec3d(1, 0, 0); // Use X-axis if face is mostly vertical
        }

        u = normal.crossProduct(nonParallel).normalize();
        v = normal.crossProduct(u).normalize();

        // Apply random offsets along u and v, scaled by randomFactor (relative to half block size)
        double offsetX = (random.nextDouble() - 0.5) * randomFactor;
        double offsetY = (random.nextDouble() - 0.5) * randomFactor;

        return centerHitVec.add(u.multiply(offsetX)).add(v.multiply(offsetY));
    }

    /**
     * Gets hit vectors near the edges of a block face.
     */
    private List<Vec3d> getEdgeHitVecs(Vec3d centerHitVec, Direction face) {
        List<Vec3d> vecs = new ArrayList<>();
        double edgeOffset = 0.45; // Slightly inside the edge

        Vec3d u, v;
        Vec3d normal = Vec3d.of(face.getVector());
        Vec3d nonParallel = new Vec3d(0, 1, 0);
        if (Math.abs(normal.y) > 0.9) nonParallel = new Vec3d(1, 0, 0);

        u = normal.crossProduct(nonParallel).normalize();
        v = normal.crossProduct(u).normalize();

        vecs.add(centerHitVec.add(u.multiply(edgeOffset)));
        vecs.add(centerHitVec.add(u.multiply(-edgeOffset)));
        vecs.add(centerHitVec.add(v.multiply(edgeOffset)));
        vecs.add(centerHitVec.add(v.multiply(-edgeOffset)));

        return vecs;
    }


    // --- Sub-Feature Handlers (SafeWalk, Sprint, Tower) ---

    /**
     * Manages SafeWalk logic based on settings.
     * Called from onPreMotion before movement calculations.
     */
    private void handleSafeWalk() {
        if (safeWalk.getValue()) {
            boolean shouldApplySafeWalk = true;
            if (safeWalkOnlyMoving.getValue() && !isMoving()) {
                shouldApplySafeWalk = false;
            }

            // Check if player is on ground and over air
            if (shouldApplySafeWalk && mc.player.isOnGround() && mc.world.isAir(mc.player.getBlockPos().down())) {
                // Use keybind press for potentially safer interaction
                mc.options.sneakKey.setPressed(true);
            } else {
                // Release sneak key if conditions aren't met
                // Only release if *we* pressed it (tricky to track reliably without complex state)
                // Safest bet: always release if conditions fail. Other modules might conflict.
                mc.options.sneakKey.setPressed(false);
            }
        } else {
            // Ensure sneak key is released if safewalk is disabled
            mc.options.sneakKey.setPressed(false);
        }
    }

    /**
     * Manages player sprinting based on module settings, especially during placement.
     */
    private void handleSprintDuringPlacement() {
        if (sprint.getValue() && disableSprintOnPlace.getValue() && mc.player.isSprinting()) {
            // Temporarily stop sprinting
            // Sending a packet is more reliable than just setting client state
            // mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            // Simpler client-side only for now:
            mc.player.setSprinting(false);
        }
    }

    /** Restores sprint state if it was modified. */
    private void restoreSprintState() {
        if (sprint.getValue() && !disableSprintOnPlace.getValue()) {
            // If sprint is allowed and not disabled on place, try re-enabling if needed
            // Careful not to force sprint if user stopped manually
            if (mc.player.input.movementForward > 0 && !mc.player.isSprinting() && !mc.player.isSneaking() && !mc.player.isUsingItem() && mc.player.getHungerManager().getFoodLevel() > 6) {
                mc.player.setSprinting(true);
            }
        }
        // If DisableSprintOnPlace was true, sprint will naturally resume if conditions allow
        // (forward movement, hunger, not sneaking/using item) after setSprinting(false) was called.
    }


    /** Checks if the jump key is pressed for towering */
    private boolean isJumpKeyPressed() {
        return mc.options.jumpKey.isPressed();
    }

    /** Handles the jumping aspect of towering */
    private void handleTowerJump() {
        if (!tower.getValue() || !isJumpKeyPressed() || mc.player == null || !mc.player.isOnGround()) {
            isTowering = false;
            towerTicks = 0;
            return;
        }

        // Start or continue towering state
        if (!isTowering) {
            isTowering = true;
            towerTicks = 0;
        } else {
            towerTicks++;
        }


        // Perform jump based on tower mode
        if (towerMode.getValue().equals("NCP")) {
            // NCP safe tower: jump normally, let server handle vertical motion update
            // Ensure block placement happens *before* the jump effect takes place server-side
            // The placement is done, now we jump.
            mc.player.jump();
            // Some NCP checks look for vertical speed changes, so avoid modifying velocity directly.
        } else {
            // Vanilla tower: jump normally, potentially faster
            mc.player.jump();
        }
    }


    // --- Data Holder Class ---

    /** Simple class to hold placement details. */
    private static class PlacementInfo {
        private final BlockPos placePos; // Where the new block will be
        private final BlockPos placeAgainst; // The block being clicked
        private final Direction placeAgainstDirection; // The face of placeAgainst being clicked
        private final Vec3d hitVec; // The precise point being clicked
        private final BlockHitResult hitResult; // Pre-calculated hit result

        PlacementInfo(BlockPos placePos, BlockPos placeAgainst, Direction placeAgainstDirection, Vec3d hitVec, BlockHitResult hitResult) {
            this.placePos = placePos;
            this.placeAgainst = placeAgainst;
            this.placeAgainstDirection = placeAgainstDirection;
            this.hitVec = hitVec;
            this.hitResult = hitResult;
        }

        public BlockPos getPlacePos() { return placePos; }
        public BlockPos getPlaceAgainst() { return placeAgainst; }
        public Direction getPlaceAgainstDirection() { return placeAgainstDirection; }
        public Vec3d getHitVec() { return hitVec; }
        public BlockHitResult getHitResult() { return hitResult; }
    }
}