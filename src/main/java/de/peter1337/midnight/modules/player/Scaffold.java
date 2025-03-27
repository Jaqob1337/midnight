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
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket; // Import needed for sprint packet (optional)
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult; // Import HitResult
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext; // Import RaycastContext
import net.minecraft.world.World;

import java.util.*;

public class Scaffold extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    // --- State Variables ---
    private BlockPos lastPlacedBlock = null;
    private long lastPlacementTime = 0;
    private Direction lastPlaceDirection = null;
    private int currentBlockSlot = -1;
    private int originalSlot = -1;
    private boolean isPlacing = false;
    private boolean placeAttemptedThisTick = false;
    private float targetYaw = 0f;
    private float targetPitch = 0f;
    private boolean rotationSet = false;
    private boolean isTowering = false;
    private int towerTicks = 0;
    private int placementFailureCounter = 0;
    private final List<BlockPos> recentFailedPositions = new ArrayList<>();
    private long lastSuccessfulPlaceTime = 0;

    // --- Constants ---
    private static final int ROTATION_PRIORITY = 80;
    private static final double MAX_REACH_DISTANCE = 4.5;
    private static final int MAX_PLACEMENT_FAILURES_BEFORE_EXPAND = 3;
    private static final int MAX_RECENT_FAILED_POSITIONS = 10;

    // --- Settings ---
    private final Setting<Boolean> rotations = register(
            new Setting<>("Rotations", Boolean.TRUE, "Look towards block placement position")
    );

    private final Setting<String> rotationMode = register(
            new Setting<>("RotationMode", "Silent",
                    Arrays.asList("Silent", "Client", "Body"),
                    "Silent: server-only, Client: visible, Body: shows on body only")
                    .dependsOn(rotations)
    );

    // New: MoveFix toggle for Scaffold
    private final Setting<Boolean> useMoveFixSetting = register(
            new Setting<>("MoveFix", Boolean.TRUE, "Correct movement direction during silent rotations")
                    .dependsOn(rotations) // Depends on rotations being enabled
    );

    private final Setting<Float> rotationSpeed = register(
            new Setting<>("RotationSpeed", 0.6f, 0.1f, 1.0f, "Speed of rotation (0.1 = slow, 1.0 = fast)")
                    .dependsOn(rotations)
    );

    private final Setting<Float> rotationRandom = register(
            new Setting<>("RotationRandom", 0.8f, 0.0f, 3.0f, "Randomness added to rotations (degrees)")
                    .dependsOn(rotations)
    );

    private final Setting<Boolean> tower = register(
            new Setting<>("Tower", Boolean.TRUE, "Jump automatically when holding space")
    );

    private final Setting<String> towerMode = register(
            new Setting<>("TowerMode", "NCP", Arrays.asList("NCP", "Vanilla"), "Towering method (NCP is safer)")
                    .dependsOn(tower)
    );

    private final Setting<Boolean> safeWalk = register(
            new Setting<>("SafeWalk", Boolean.TRUE, "Prevents falling off edges (can flag)")
    );

    private final Setting<Boolean> safeWalkOnlyMoving = register(
            new Setting<>("SafeWalkOnlyMoving", Boolean.TRUE, "Only apply SafeWalk when actively moving")
                    .dependsOn(safeWalk)
    );

    private final Setting<Boolean> sprint = register(
            new Setting<>("Sprint", Boolean.FALSE, "Allow sprinting (can interfere)")
    );

    private final Setting<Boolean> disableSprintOnPlace = register(
            new Setting<>("DisableSprintOnPlace", Boolean.TRUE, "Temporarily disable sprint when placing")
                    .dependsOn(sprint) // Only makes sense if sprinting is allowed
    );

    private final Setting<Boolean> autoSwitch = register(
            new Setting<>("AutoSwitch", Boolean.TRUE, "Switch to blocks automatically")
    );

    private final Setting<Float> minDelay = register(
            new Setting<>("MinDelay", 0.05f, 0.0f, 0.5f, "Minimum delay between placements (seconds)")
    );
    private final Setting<Float> maxDelay = register(
            new Setting<>("MaxDelay", 0.12f, 0.0f, 0.5f, "Maximum delay between placements (seconds)")
    );

    private final Setting<Boolean> dynamicDelay = register(
            new Setting<>("DynamicDelay", Boolean.TRUE, "Adjust delay based on placement success")
    );

    private final Setting<Boolean> intelligent = register(
            new Setting<>("Intelligent", Boolean.TRUE, "Smarter placement based on movement")
    );

    private final Setting<Float> placementRandom = register(
            new Setting<>("PlacementRandom", 0.3f, 0.0f, 0.5f, "Random click position on block face")
    );

    private final Setting<Boolean> strictCenter = register(
            new Setting<>("StrictCenter", Boolean.FALSE, "Prioritize placing near center")
    );

    private final Setting<Boolean> expandPlacement = register(
            new Setting<>("ExpandPlacement", Boolean.TRUE, "Check wider area if direct placement fails")
    );

    private final Setting<Integer> expandRange = register(
            new Setting<>("ExpandRange", 1, 1, 2, "How far to expand search (blocks)")
                    .dependsOn(expandPlacement)
    );

    private final Setting<Boolean> checkSupport = register(
            new Setting<>("CheckSupport", Boolean.TRUE, "Ensure the block being placed against is solid")
    );

    private final Setting<Float> maxYawDifference = register(
            new Setting<>("MaxYawDifference", 75f, 30f, 180f, "Max angle diff between facing and placement")
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
        lastSuccessfulPlaceTime = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);

        if (mc.player != null) {
            if (autoSwitch.getValue() && originalSlot != -1 && mc.player.getInventory().selectedSlot != originalSlot) {
                mc.player.getInventory().selectedSlot = originalSlot;
            }
            mc.options.sneakKey.setPressed(false); // Ensure sneak is off
            // Restore sprint if we disabled it? Complex - maybe just let it be.
        }
        originalSlot = -1;
        currentBlockSlot = -1;
        isPlacing = false;
        isTowering = false;
        rotationSet = false;
        placeAttemptedThisTick = false;
    }

    public void onPreMotion() { // Called from Mixin before movement calculations
        if (!isEnabled() || mc.player == null || mc.world == null) return;
        handleSafeWalk();
    }

    public boolean isSprintAllowed() {
        // If the module isn't enabled, it shouldn't block sprint.
        // If it is enabled, return the value of the sprint setting.
        // Note: The Sprint module already checks if Scaffold is enabled before calling this.
        // So simply returning the setting value is sufficient here.
        return sprint.getValue();
    }

    public void preUpdate() { // Handles finding placement, rotations, inventory switching
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        isPlacing = false;
        rotationSet = false;
        placeAttemptedThisTick = false; // Reset placement attempt flag

        currentBlockSlot = findBlockInHotbar();
        if (currentBlockSlot == -1) {
            // Consider displaying a warning or disabling temporarily
            return; // No blocks, nothing to do
        }

        if (autoSwitch.getValue() && mc.player.getInventory().selectedSlot != currentBlockSlot) {
            mc.player.getInventory().selectedSlot = currentBlockSlot;
        }

        PlacementInfo placementInfo = findPlacement();
        if (placementInfo == null) {
            if (rotations.getValue()) handleIdleRotation();
            restoreSprintState(); // Ensure sprint is not wrongly suppressed
            return;
        }

        if (rotations.getValue()) {
            handleRotations(placementInfo); // This sets rotationSet = true
        } else {
            RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
        }

        if (canAttemptPlacement(placementInfo)) {
            isPlacing = true;
            handleSprintDuringPlacement();
        } else {
            restoreSprintState();
        }
    }

    @Override
    public void onUpdate() { // Handles the actual block placement interaction
        if (!isEnabled() || mc.player == null || mc.world == null || !isPlacing || placeAttemptedThisTick) {
            return;
        }

        PlacementInfo placementInfo = findPlacement(); // Re-check placement validity
        if (placementInfo == null || currentBlockSlot == -1) {
            isPlacing = false;
            restoreSprintState();
            return;
        }

        // Final rotation check before placing
        if (rotations.getValue() && !isRotationAligned(placementInfo.getHitVec())) {
            // Maybe allow placement if close enough and moving fast? For now, require alignment.
            // isPlacing = false; // Keep isPlacing true maybe, just skip this tick?
            restoreSprintState(); // Sprint should be normal if not placing
            return; // Rotation not ready
        }

        if (performPlacement(placementInfo)) {
            lastPlacementTime = System.currentTimeMillis();
            lastSuccessfulPlaceTime = lastPlacementTime;
            lastPlacedBlock = placementInfo.getPlacePos();
            lastPlaceDirection = placementInfo.getPlaceAgainstDirection();
            placementFailureCounter = 0;
            recentFailedPositions.remove(placementInfo.getPlacePos());
            placeAttemptedThisTick = true; // Mark as placed this tick

            handleTowerJump(); // Jump after successful placement if towering

        } else { // Placement failed (interaction sent but likely server denied)
            placementFailureCounter++;
            if (!recentFailedPositions.contains(placementInfo.getPlacePos())) {
                recentFailedPositions.add(placementInfo.getPlacePos());
                if (recentFailedPositions.size() > MAX_RECENT_FAILED_POSITIONS) {
                    recentFailedPositions.remove(0);
                }
            }
            restoreSprintState(); // Ensure sprint is not wrongly suppressed
        }

        isPlacing = false; // Reset placing flag after attempt
    }

    // --- Core Logic Methods ---

    private PlacementInfo findPlacement() {
        if (mc.player == null || mc.world == null) return null;

        Vec3d playerPos = mc.player.getPos();
        BlockPos playerBlockPos = mc.player.getBlockPos();
        BlockPos basePos = playerBlockPos.down();
        BlockPos targetPos = basePos;

        if (intelligent.getValue() && isMoving()) {
            Vec3d moveVec = getMovementDirection(true);
            double predictionFactor = MathHelper.clamp(mc.player.getVelocity().horizontalLength() * 15.0, 0.4, 0.9); // Adjusted prediction
            Vec3d predictedPos = playerPos.add(moveVec.multiply(predictionFactor));
            // Use BlockPos.ofFloored for consistency
            BlockPos projectedPos = BlockPos.ofFloored(predictedPos.x, basePos.getY(), predictedPos.z);

            if (!projectedPos.equals(basePos) && mc.world.getBlockState(projectedPos).isReplaceable() && !recentFailedPositions.contains(projectedPos)) {
                targetPos = projectedPos;
            } else if (!mc.world.getBlockState(basePos).isReplaceable()) { // If below is solid, try front
                BlockPos frontPos = basePos.offset(mc.player.getHorizontalFacing());
                if(mc.world.getBlockState(frontPos).isReplaceable() && !recentFailedPositions.contains(frontPos)) {
                    targetPos = frontPos;
                }
            }
        }

        PlacementInfo primaryPlacement = findSpecificPlacement(targetPos);
        if (primaryPlacement != null) return primaryPlacement;

        if (expandPlacement.getValue() && (placementFailureCounter >= MAX_PLACEMENT_FAILURES_BEFORE_EXPAND || primaryPlacement == null) ) { // Expand if primary failed OR counter high
            List<BlockPos> expandedPositions = getExpandedPositions(targetPos, basePos);
            for (BlockPos expandedPos : expandedPositions) {
                PlacementInfo expandedPlacement = findSpecificPlacement(expandedPos);
                if (expandedPlacement != null) return expandedPlacement;
            }
        }

        // Towering fallback
        if (isTowering && mc.world.getBlockState(basePos).isReplaceable()) {
            return findSpecificPlacement(basePos);
        }

        return null;
    }

    private PlacementInfo findSpecificPlacement(BlockPos targetPlacePos) {
        if (mc.player == null || mc.world == null || !mc.world.getBlockState(targetPlacePos).isReplaceable() || recentFailedPositions.contains(targetPlacePos)) {
            return null;
        }

        Vec3d eyePos = mc.player.getEyePos();
        List<PlacementOption> possibleOptions = new ArrayList<>();

        for (Direction tryDir : Direction.values()) {
            BlockPos supportBlockPos = targetPlacePos.offset(tryDir);
            BlockState supportBlockState = mc.world.getBlockState(supportBlockPos);

            if (supportBlockState.isAir() || (checkSupport.getValue() && !supportBlockState.isSolidBlock(mc.world, supportBlockPos))) continue;

            Direction placeAgainstDir = tryDir.getOpposite();
            Vec3d centerHitVec = Vec3d.ofCenter(supportBlockPos).add(Vec3d.of(placeAgainstDir.getVector()).multiply(0.5));
            List<Vec3d> potentialHitVecs = new ArrayList<>();
            potentialHitVecs.add(getRandomizedHitVec(centerHitVec, placeAgainstDir));
            if (!strictCenter.getValue()) potentialHitVecs.addAll(getEdgeHitVecs(centerHitVec, placeAgainstDir));

            for(Vec3d hitVec : potentialHitVecs) {
                if (eyePos.squaredDistanceTo(hitVec) > MAX_REACH_DISTANCE * MAX_REACH_DISTANCE) continue;

                // Simple Line of Sight Check (Optional but recommended)
                RaycastContext context = new RaycastContext(eyePos, hitVec, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
                BlockHitResult blockHit = mc.world.raycast(context);
                if (blockHit.getType() != HitResult.Type.MISS && !blockHit.getBlockPos().equals(supportBlockPos)) {
                    continue; // Blocked
                }


                // Angle Check
                if (rotations.getValue()) {
                    float[] requiredRotations = RotationHandler.calculateLookAt(hitVec);
                    // Use current rotation, not necessarily server rotation, for initial check? Or server for consistency? Let's use server.
                    float yawDiff = MathHelper.wrapDegrees(requiredRotations[0] - RotationHandler.getServerYaw());
                    if (Math.abs(yawDiff) > maxYawDifference.getValue()) continue;
                }

                // If checks pass, add as a possible option
                BlockHitResult hitResult = new BlockHitResult(hitVec, placeAgainstDir, supportBlockPos, false);
                possibleOptions.add(new PlacementOption(targetPlacePos, supportBlockPos, placeAgainstDir, hitVec, hitResult));
            }
        }

        // Choose the best option (e.g., lowest distance, closest to center)
        return possibleOptions.stream()
                .min(Comparator.comparingDouble(opt -> opt.hitVec.squaredDistanceTo(eyePos))) // Prioritize closest reach?
                // Or prioritize center: .min(Comparator.comparingDouble(opt -> opt.hitVec.distanceTo(Vec3d.ofCenter(opt.supportBlockPos).add(Vec3d.of(opt.placeAgainstDir.getVector()).multiply(0.5)))))
                .map(opt -> new PlacementInfo(opt.targetPlacePos, opt.supportBlockPos, opt.placeAgainstDir, opt.hitVec, opt.hitResult))
                .orElse(null);
    }

    // Helper class for sorting options before creating final PlacementInfo
    private record PlacementOption(BlockPos targetPlacePos, BlockPos supportBlockPos, Direction placeAgainstDir, Vec3d hitVec, BlockHitResult hitResult) {}


    private boolean performPlacement(PlacementInfo placementInfo) {
        if (mc.player == null || mc.interactionManager == null || currentBlockSlot == -1) return false;
        if (mc.player.getInventory().selectedSlot != currentBlockSlot) {
            if(autoSwitch.getValue()) mc.player.getInventory().selectedSlot = currentBlockSlot;
            else return false;
        }
        ItemStack handStack = mc.player.getInventory().getStack(currentBlockSlot);
        if (!(handStack.getItem() instanceof BlockItem)) return false;

        try {
            Hand hand = Hand.MAIN_HAND;
            mc.interactionManager.interactBlock(mc.player, hand, placementInfo.getHitResult());
            mc.player.swingHand(hand); // Swing arm client-side
            return true;
        } catch (Exception e) { return false; }
    }

    private boolean canAttemptPlacement(PlacementInfo placementInfo) {
        if (placementInfo == null || currentBlockSlot == -1) return false;

        long currentTime = System.currentTimeMillis();
        float minDel = minDelay.getValue() * 1000f;
        float maxDel = Math.max(minDel, maxDelay.getValue() * 1000f);
        float calculatedDelay = minDel + random.nextFloat() * (maxDel - minDel);
        if (dynamicDelay.getValue() && placementFailureCounter > 0) {
            calculatedDelay *= (1.0f + Math.min(0.8f, placementFailureCounter * 0.15f)); // Slightly stronger penalty
        }

        return currentTime - lastPlacementTime >= calculatedDelay;
    }

    private void handleRotations(PlacementInfo placementInfo) {
        if (mc.player == null) return;

        float[] baseRotations = RotationHandler.calculateLookAt(placementInfo.getHitVec());
        float randomYawOffset = (random.nextFloat() - 0.5f) * 2f * rotationRandom.getValue();
        float randomPitchOffset = (random.nextFloat() - 0.5f) * 2f * rotationRandom.getValue() * 0.7f;
        targetYaw = MathHelper.wrapDegrees(baseRotations[0] + randomYawOffset);
        targetPitch = MathHelper.clamp(baseRotations[1] + randomPitchOffset, -90f, 90f);

        // Determine rotation mode parameters
        boolean silent;
        boolean bodyOnly;
        boolean moveFixRequired;

        switch (rotationMode.getValue()) {
            case "Silent":
                silent = true;
                bodyOnly = false;
                break;
            case "Client":
                silent = false;
                bodyOnly = false;
                break;
            case "Body":
                // For Body mode, rotations are sent to server but also applied to model
                silent = true; // Still "silent" from camera perspective
                bodyOnly = true; // Apply to body model
                // Important: We still need to send these to the server
                break;
            default:
                silent = true;
                bodyOnly = false;
                break;
        }

        moveFixRequired = (silent || bodyOnly) && useMoveFixSetting.getValue();

        float currentYaw = (silent || bodyOnly) ? RotationHandler.getServerYaw() : mc.player.getYaw();
        float currentPitch = (silent || bodyOnly) ? RotationHandler.getServerPitch() : mc.player.getPitch();

        float speed = rotationSpeed.getValue();
        float[] smoothedRotations = RotationHandler.smoothRotation(currentYaw, currentPitch, targetYaw, targetPitch, speed);

        // Request the rotation step with correct parameters
        RotationHandler.requestRotation(
                smoothedRotations[0],
                smoothedRotations[1],
                ROTATION_PRIORITY,
                60, // Duration slightly longer than a tick
                silent, // This keeps it server-side only for camera
                bodyOnly, // This makes it visible on body model
                moveFixRequired,
                null
        );
        rotationSet = true;
    }


    private void handleIdleRotation() {
        if (mc.player == null || !rotations.getValue() || rotationSet) return;

        float idleYaw = MathHelper.wrapDegrees(mc.player.getYaw() + 180f + (random.nextFloat() - 0.5f) * 10f);
        float idlePitch = 80f + random.nextFloat() * 5f; // Look down more consistently

        boolean silent = rotationMode.getValue().equals("Silent");
        boolean moveFixRequired = silent && useMoveFixSetting.getValue(); // Check setting HERE

        float currentYaw = silent ? RotationHandler.getServerYaw() : mc.player.getYaw();
        float currentPitch = silent ? RotationHandler.getServerPitch() : mc.player.getPitch();
        float speed = Math.max(0.1f, rotationSpeed.getValue() * 0.3f); // Slower idle rotation
        float[] smoothedRotations = RotationHandler.smoothRotation(currentYaw, currentPitch, idleYaw, idlePitch, speed);

        RotationHandler.requestRotation(
                smoothedRotations[0],
                smoothedRotations[1],
                ROTATION_PRIORITY - 10, // Lower priority
                60,
                silent,
                false,
                moveFixRequired, // <-- Pass the calculated moveFix flag
                null
        );
    }

    private boolean isRotationAligned(Vec3d targetHitVec) {
        if (!rotationSet) return false; // If no rotation was set this tick, it can't be aligned

        float[] requiredRotations = RotationHandler.calculateLookAt(targetHitVec);
        float serverYaw = RotationHandler.getServerYaw();
        float serverPitch = RotationHandler.getServerPitch();
        float yawDiff = Math.abs(MathHelper.wrapDegrees(serverYaw - requiredRotations[0]));
        float pitchDiff = Math.abs(serverPitch - requiredRotations[1]);

        // Dynamic tolerance based on speed? Lower speed = lower tolerance needed.
        float tolerance = Math.max(1.0f, 20.0f * (1.0f - rotationSpeed.getValue()) + 1.0f); // Example: 1 to 21 deg tolerance

        return yawDiff <= tolerance && pitchDiff <= tolerance;
    }

    // --- Helper & Utility Methods ---

    private int findBlockInHotbar() {
        if (mc.player == null) return -1;
        PlayerInventory inventory = mc.player.getInventory();
        int bestSlot = -1;
        int maxCount = -1; // Start at -1 to ensure any valid slot is chosen initially

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (isValidBlock(stack)) {
                if(stack.getCount() > maxCount) { // Prioritize slot with most blocks
                    maxCount = stack.getCount();
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    private boolean isValidBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        // Consider using tags if available, otherwise use Identifier path
        Identifier blockId = Registries.BLOCK.getId(block);
        if (blockId == null) return false; // Should not happen for registered blocks
        String path = blockId.getPath();

        // Enhanced Blacklist
        return !path.contains("chest") &&
                !path.contains("shulker_box") &&
                !path.contains("sign") &&
                !path.contains("banner") &&
                !path.contains("door") &&
                !path.contains("fence_gate") &&
                !path.contains("trapdoor") && // Often don't want to place these accidentally
                !path.contains("anvil") &&
                !path.contains("ender_chest") &&
                !(block instanceof FluidBlock) &&
                !(block instanceof PlantBlock) &&
                !(block instanceof TorchBlock) &&
                !(block instanceof LadderBlock) &&
                !(block instanceof FallingBlock) && // Avoid sand/gravel generally
                !(block instanceof AbstractPressurePlateBlock) &&
                !(block instanceof ButtonBlock) &&
                !(block instanceof FlowerPotBlock) &&
                block.getDefaultState().isSolid() && // Check solidity
                block.getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN); // Check for full cube shape
    }

    private boolean isMoving() {
        if (mc.player == null || mc.player.input == null) return false;
        return mc.player.input.movementForward != 0.0f || mc.player.input.movementSideways != 0.0f;
    }

    private Vec3d getMovementDirection(boolean predict) {
        if (mc.player == null) return Vec3d.ZERO;
        Input input = mc.player.input;
        double forward = input.movementForward;
        double strafe = input.movementSideways;
        float yaw = mc.player.getYaw();

        if (predict && mc.player.getVelocity().horizontalLengthSquared() > 0.001) {
            return new Vec3d(mc.player.getVelocity().x, 0, mc.player.getVelocity().z).normalize();
        }
        if (forward == 0.0 && strafe == 0.0) return Vec3d.ZERO;

        double angle = Math.toRadians(yaw);
        double motionX = -Math.sin(angle) * forward + Math.cos(angle) * strafe;
        double motionZ = Math.cos(angle) * forward + Math.sin(angle) * strafe;
        return new Vec3d(motionX, 0, motionZ).normalize();
    }

    public boolean isSprintBlockedByScaffold() {
        // Called by Sprint module to check if Scaffold should block sprinting
        if (!isEnabled()) return false; // Not enabled, don't block
        return !sprint.getValue() || (isPlacing && disableSprintOnPlace.getValue());
    }


    private List<BlockPos> getExpandedPositions(BlockPos centerPos, BlockPos basePos) {
        List<BlockPos> positions = new ArrayList<>();
        int range = expandRange.getValue();
        Vec3d moveDir = getMovementDirection(false);

        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                if (x == 0 && z == 0) continue;
                BlockPos offsetPos = centerPos.add(x, 0, z);
                if (mc.world.getBlockState(offsetPos).isReplaceable() && !recentFailedPositions.contains(offsetPos)) {
                    positions.add(offsetPos);
                }
                // Only check below offset if bridging/not placing directly below
                if (!centerPos.equals(basePos)) {
                    BlockPos belowOffsetPos = offsetPos.down();
                    if (mc.world.getBlockState(belowOffsetPos).isReplaceable() && !recentFailedPositions.contains(belowOffsetPos)) {
                        positions.add(belowOffsetPos);
                    }
                }
            }
        }

        positions.sort(Comparator.comparingDouble((BlockPos pos) -> {
            double distSq = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(pos));
            Vec3d dirToPos = Vec3d.ofCenter(pos).subtract(mc.player.getPos()).normalize();
            double alignment = moveDir.dotProduct(new Vec3d(dirToPos.x, 0, dirToPos.z).normalize());
            // Lower score is better: prioritize distance, heavily penalize anti-alignment
            return distSq - alignment * 10.0; // Increased alignment weight
        }));
        return positions;
    }

    private Vec3d getRandomizedHitVec(Vec3d centerHitVec, Direction face) {
        double randomFactor = placementRandom.getValue();
        if (randomFactor <= 0.0) return centerHitVec;

        Vec3d u, v;
        Vec3d normal = Vec3d.of(face.getVector());
        Vec3d nonParallel = Math.abs(normal.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        u = normal.crossProduct(nonParallel).normalize();
        v = normal.crossProduct(u).normalize();
        double offsetX = (random.nextDouble() - 0.5) * randomFactor;
        double offsetY = (random.nextDouble() - 0.5) * randomFactor;
        return centerHitVec.add(u.multiply(offsetX)).add(v.multiply(offsetY));
    }

    private List<Vec3d> getEdgeHitVecs(Vec3d centerHitVec, Direction face) {
        List<Vec3d> vecs = new ArrayList<>();
        double edgeOffset = 0.48; // Closer to edge
        Vec3d u, v;
        Vec3d normal = Vec3d.of(face.getVector());
        Vec3d nonParallel = Math.abs(normal.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        u = normal.crossProduct(nonParallel).normalize();
        v = normal.crossProduct(u).normalize();
        vecs.add(centerHitVec.add(u.multiply(edgeOffset)));
        vecs.add(centerHitVec.add(u.multiply(-edgeOffset)));
        vecs.add(centerHitVec.add(v.multiply(edgeOffset)));
        vecs.add(centerHitVec.add(v.multiply(-edgeOffset)));
        return vecs;
    }

    // --- Sub-Feature Handlers ---

    private void handleSafeWalk() {
        if (!safeWalk.getValue()) {
            // Ensure key is released if disabled
            if (mc.options.sneakKey.isPressed()) mc.options.sneakKey.setPressed(false);
            return;
        }

        boolean shouldApplySafeWalk = !safeWalkOnlyMoving.getValue() || isMoving();

        // Only sneak if on ground, over air, and not towering (prevents sneak jump issues)
        boolean safeWalkConditionMet = shouldApplySafeWalk && mc.player.isOnGround() && mc.world.isAir(mc.player.getBlockPos().down()) && !isTowering;

        // Use keybind state to avoid conflicts IF POSSIBLE
        // This logic assumes nothing else controls the sneak keybind state directly
        if (safeWalkConditionMet) {
            if (!mc.options.sneakKey.isPressed()) mc.options.sneakKey.setPressed(true);
        } else {
            if (mc.options.sneakKey.isPressed()) mc.options.sneakKey.setPressed(false);
        }
    }

    private void handleSprintDuringPlacement() {
        if (sprint.getValue() && disableSprintOnPlace.getValue() && mc.player.isSprinting()) {
            // Client-side only for simplicity, packet could be used for more forcefulness
            mc.player.setSprinting(false);
        }
    }

    private void restoreSprintState() {
        // This function is mainly to counteract DisableSprintOnPlace.
        // If sprinting is generally allowed (!disableSprintOnPlace OR sprint=false),
        // we don't need to do much, vanilla mechanics will handle resuming sprint.
        // If sprint is allowed AND disableSprintOnPlace is TRUE, we only stopped it *during* placing.
        // It should resume naturally if conditions (moving forward, hunger etc) are met after placement.
        // So, this function might not be strictly necessary unless we need to FORCE sprint back on.
    }

    private boolean isJumpKeyPressed() {
        return mc.options.jumpKey.isPressed();
    }

    private void handleTowerJump() {
        if (!tower.getValue() || !isJumpKeyPressed() || mc.player == null || !mc.player.isOnGround()) {
            isTowering = false; // Stop towering if conditions aren't met
            towerTicks = 0;
            return;
        }

        isTowering = true; // Mark as towering
        towerTicks++;

        if (towerMode.getValue().equals("NCP")) {
            // NCP: Jump normally, timing relies on placing block just before jump apex/next tick
            mc.player.jump();
        } else { // Vanilla
            mc.player.jump();
            // Potentially modify velocity for faster vanilla tower? Risky.
            // mc.player.setVelocity(mc.player.getVelocity().x, 0.42, mc.player.getVelocity().z); // Vanilla jump velocity
        }
        // Reset jump cooldown to allow consecutive jumps if needed by server checks
        // player.jumpCooldown = 0; // Check if needed, might cause flags
    }

    // --- Data Holder Class ---

    private static class PlacementInfo {
        private final BlockPos placePos;
        private final BlockPos placeAgainst;
        private final Direction placeAgainstDirection;
        private final Vec3d hitVec;
        private final BlockHitResult hitResult;

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