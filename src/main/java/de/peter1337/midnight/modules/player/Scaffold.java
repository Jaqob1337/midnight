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
import java.util.Random;

public class Scaffold extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    // Last placed block for reference
    private BlockPos lastPlacedPos = null;

    // Rotation management
    private static final int ROTATION_PRIORITY = 80;
    private boolean rotationsSet = false;

    // Walking direction tracking
    private Direction bridgeDirection = null;
    private float targetYaw = 0;
    private float lastTargetYaw = 0;
    private float targetPitch = 75f; // Default looking down angle for bridging

    // Rotation tracking to avoid duplicate patterns
    private float lastPlacementPitch = 0f;
    private float lastPlacementYaw = 0f;
    private long lastYawUpdateTime = 0;
    private long lastPitchUpdateTime = 0;
    private boolean isTowering = false;

    // Simple timer for block placement
    private long lastPlaceTime = 0;
    private long blockPlacementJitter = 0;

    // Pattern disruption timers
    private long lastRandomAdjustment = 0;
    private static final long PATTERN_BREAK_INTERVAL = 2500; // ms
    private int consecutivePlacements = 0;

    // NEW: Placement attempt tracking
    private boolean lastPlacementSuccessful = false;
    private boolean placementAttempted = false;
    private long failedPlacementTime = 0;
    private static final long FAILED_PLACEMENT_COOLDOWN = 500; // 500ms cooldown after failed placement
    private int placementFailCount = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    // Track rotation timing for server sync
    private long lastRotationTime = 0;

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
            new Setting<>("MoveFix", Boolean.TRUE, "Corrects rotation and movement direction during scaffolding")
                    .dependsOn(rotations)
    );

    private final Setting<Float> rotationSpeed = register(
            new Setting<>("RotationSpeed", 0.65f, 0.3f, 0.9f, "Speed of rotations (lower is smoother)")
                    .dependsOn(rotations)
    );

    private final Setting<Float> randomPitchRange = register(
            new Setting<>("PitchVariation", 3.0f, 0.0f, 8.0f, "Random pitch variation to avoid detection")
                    .dependsOn(rotations)
    );

    private final Setting<Float> randomYawRange = register(
            new Setting<>("YawVariation", 1.5f, 0.0f, 5.0f, "Random yaw variation to avoid detection")
                    .dependsOn(rotations)
    );

    private final Setting<Boolean> humanPatterns = register(
            new Setting<>("HumanPatterns", Boolean.TRUE, "Add human-like inconsistencies to placement")
    );

    private final Setting<Boolean> tower = register(
            new Setting<>("Tower", Boolean.TRUE, "Jump automatically when holding space")
    );

    private final Setting<Float> towerSpeed = register(
            new Setting<>("TowerSpeed", 0.6f, 0.1f, 1.0f, "Speed of tower jumping")
                    .dependsOn(tower)
    );

    private final Setting<Boolean> safeWalk = register(
            new Setting<>("SafeWalk", Boolean.TRUE, "Prevents falling off edges")
    );

    private final Setting<Float> delay = register(
            new Setting<>("Delay", 0.25f, 0.15f, 1.0f, "Delay between placements (seconds)")
    );

    private final Setting<Float> delayRandomization = register(
            new Setting<>("DelayRandom", 0.1f, 0.0f, 0.5f, "Randomize delay between placements")
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

    private final Setting<Boolean> graceTimer = register(
            new Setting<>("GraceTimer", Boolean.TRUE, "Occasionally pause placement to appear more human")
    );

    private final Setting<Boolean> avoidAir = register(
            new Setting<>("AvoidAir", Boolean.TRUE, "Reduces flags by avoiding jumps over air blocks")
    );

    private final Setting<Boolean> antiDuplicate = register(
            new Setting<>("AntiDuplicate", Boolean.TRUE, "Prevent duplicate rotations to avoid detection")
    );

    private final Setting<Boolean> preventSpamPlace = register(
            new Setting<>("PreventSpamPlace", Boolean.TRUE, "Prevents attempting to place blocks repeatedly in the same spot")
    );

    // NEW: Added reliable placement setting
    private final Setting<Boolean> reliablePlacement = register(
            new Setting<>("ReliablePlacement", Boolean.TRUE, "Prioritizes reliable placements over speed")
    );

    // Track the original movement inputs for the direct movement fix
    private float originalForward = 0f;
    private float originalSideways = 0f;
    private boolean movementModified = false;

    // Grace period tracking
    private boolean inGracePeriod = false;
    private long graceEndTime = 0;

    public Scaffold() {
        super("Scaffold", "Places blocks under you", Category.PLAYER, "m");
    }

    @Override
    public void onEnable() {
        lastPlacedPos = null;
        rotationsSet = false;
        movementModified = false;
        consecutivePlacements = 0;
        lastRandomAdjustment = System.currentTimeMillis();
        lastYawUpdateTime = 0;
        lastPitchUpdateTime = 0;
        endGracePeriod();
        lastPlacementSuccessful = false;
        placementAttempted = false;
        failedPlacementTime = 0;
        placementFailCount = 0;
        lastRotationTime = 0;
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
     *
     * @return true if movement fix is enabled
     */
    public boolean isMovingFixEnabled() {
        return isEnabled() && moveFix.getValue();
    }

    /**
     * Apply movement fix that transforms movement based on rotation difference
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

            // Use a more natural transformation instead of direct inversion
            float lookYaw = mc.player.getYaw();
            float placeYaw = targetYaw;

            // Add slight randomization to make it look more human
            if (humanPatterns.getValue() && random.nextFloat() > 0.7f) {
                placeYaw += (random.nextFloat() * 0.6f - 0.3f);
            }

            float rotationDiff = MathHelper.wrapDegrees(placeYaw - lookYaw);

            // Calculate transformation using rotation matrix
            float sinYaw = MathHelper.sin(rotationDiff * ((float)Math.PI / 180F));
            float cosYaw = MathHelper.cos(rotationDiff * ((float)Math.PI / 180F));

            // Base transformation - using rotational matrix approach
            float newForward = originalForward * cosYaw - originalSideways * sinYaw;
            float newSideways = originalSideways * cosYaw + originalForward * sinYaw;

            // Apply occasional tiny imperfections to movement to look human
            if (humanPatterns.getValue() && random.nextFloat() > 0.85) {
                newForward *= 0.98f + random.nextFloat() * 0.04f;
                newSideways *= 0.98f + random.nextFloat() * 0.04f;
            }

            // Apply the transformed movement
            mc.player.input.movementForward = newForward;
            mc.player.input.movementSideways = newSideways;

            // Use appropriate move fix context
            if (isTowering) {
                // For tower, use direct context for more predictable movement
                RotationHandler.setMoveFixContext("scaffold_direct_100");
            } else {
                // For regular scaffolding, use standard context
                RotationHandler.setMoveFixContext("scaffold");
            }

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

        // Check for grace period (occasional human-like pause)
        if (inGracePeriod) {
            if (System.currentTimeMillis() >= graceEndTime) {
                endGracePeriod();
            } else {
                return; // Skip processing during grace period
            }
        }

        // First, apply the movement fix - do this BEFORE any other logic
        // This applies a matrix transformation to movement rather than inverting it
        if (moveFix.getValue() && !movementModified) {
            applyMovementFix();
        }

        // Track rotation timing for synchronization
        long currentTime = System.currentTimeMillis();
        boolean hasStableRotation = (currentTime - lastRotationTime > 50);

        // Detect towering (building straight up)
        isTowering = tower.getValue() && mc.options.jumpKey.isPressed() && mc.player.isOnGround();

        // Set bridging direction based on whether we're towering or not
        if (isTowering) {
            // When towering, use a fixed direction, and more stable pitch
            targetYaw = mc.player.getYaw(); // Use current yaw for tower
            targetPitch = 89.5f; // Very precise look down for reliability
        } else {
            // Normal scaffolding
            bridgeDirection = getHorizontalDirection(mc.player.getYaw());
            targetYaw = getYawFromDirection(bridgeDirection.getOpposite());

            // Add occasional random jitter to targetYaw to break patterns
            if (humanPatterns.getValue() && System.currentTimeMillis() - lastRandomAdjustment > PATTERN_BREAK_INTERVAL) {
                // Small random adjustment
                targetYaw += (random.nextFloat() - 0.5f) * 2f;
                lastRandomAdjustment = System.currentTimeMillis();

                // Try to avoid having identical rotations
                if (Math.abs(targetYaw - lastTargetYaw) < 0.01f) {
                    targetYaw += 0.25f;
                }
                lastTargetYaw = targetYaw;
            }
        }

        // Handle safe walk (sneaking at edges)
        handleSafeWalk();

        // Try to find a placement position
        PlacementInfo placement = findPlacement();

        // Try specialized tower placement if in tower mode and normal placement failed or returned a block that's already placed
        if (isTowering && (placement == null || (placement != null &&
                !mc.world.getBlockState(placement.targetPos).isAir()))) {
            // Try direct placement below player for tower
            placement = findDirectPlacementUnder();
        }

        // Check if the target position already has a block
        if (placement != null && preventSpamPlace.getValue()) {
            BlockPos targetPos = placement.targetPos;
            BlockState state = mc.world.getBlockState(targetPos);
            if (!state.isAir()) {
                // There's already a block here, no need to place
                lastPlacementSuccessful = true;
                placementAttempted = false;
                lastPlacedPos = targetPos;
                placementFailCount = 0;
                resetGracePeriod();
                return;
            }
        }

        if (placement != null) {
            // Apply rotation for block placement - using calculations with randomization
            if (rotations.getValue()) {
                // Use different rotation speeds and strategies based on tower mode
                float effectiveRotationSpeed;
                float effectivePitch;

                if (isTowering) {
                    // Use slower, more precise rotations for tower mode
                    effectiveRotationSpeed = reliablePlacement.getValue() ? 1.0f : rotationSpeed.getValue() * 0.9f;
                    effectivePitch = targetPitch; // Use stable pitch for tower

                    // Very minimal randomization for tower
                    if (!reliablePlacement.getValue() && random.nextFloat() > 0.9f) {
                        effectivePitch += (random.nextFloat() - 0.5f) * 0.5f;
                    }
                } else {
                    // Normal bridge mode - can be more variable
                    effectiveRotationSpeed = rotationSpeed.getValue();
                    float[] placeAngles = calculateRotation(placement.hitVec);
                    effectivePitch = placeAngles[1];

                    // Add randomized pitch
                    if (!reliablePlacement.getValue()) {
                        effectivePitch = getRandomizedPitch(effectivePitch);
                    }
                }

                // Apply rotations for placement
                handleRotations(targetYaw, effectivePitch, effectiveRotationSpeed);

                // Important: Track when we set rotations
                lastRotationTime = System.currentTimeMillis();
            }

            // Check if we've attempted too many failed placements in a row
            if (preventSpamPlace.getValue() && placementFailCount >= MAX_CONSECUTIVE_FAILURES) {
                // Force a longer cooldown period for anti-spam measure
                long cooldownTime = System.currentTimeMillis();
                if (cooldownTime - failedPlacementTime < FAILED_PLACEMENT_COOLDOWN * 3) {
                    return; // Skip placement until the extended cooldown passes
                } else {
                    // Reset fail count and try again after extended cooldown
                    placementFailCount = 0;
                }
            }

            // In tower or reliable mode, ensure rotation is stable before placing
            if (isTowering || reliablePlacement.getValue()) {
                // Wait for rotation to stabilize if it was recently changed
                if (!hasStableRotation && rotations.getValue()) {
                    return;
                }
            }

            // Check if we can place a block now - with human timing patterns
            if (canPlaceNow() && hasBlocks() && !inGracePeriod) {
                // Perform the placement
                placeBlock(placement);

                // Handle tower jumping
                if (tower.getValue() && mc.options.jumpKey.isPressed() && mc.player.isOnGround()) {
                    // Add small delay for more human-like behavior
                    if (!humanPatterns.getValue() || random.nextFloat() > 0.2f) {
                        // Only jump if the placement was successful
                        if (lastPlacementSuccessful) {
                            // Variable jump height for towering
                            mc.player.jump();

                            // Apply a small boost based on tower speed
                            if (towerSpeed.getValue() > 0.2f) {
                                float boost = towerSpeed.getValue() * 0.05f;
                                if (humanPatterns.getValue()) {
                                    boost *= 0.9f + random.nextFloat() * 0.2f;
                                }
                                mc.player.addVelocity(0, boost, 0);
                            }

                            // With avoidAir, we want to place another block quickly after jump
                            if (avoidAir.getValue()) {
                                blockPlacementJitter = -100; // Negative to make next placement happen sooner
                            }
                        } else if (isTowering && placementFailCount > 0) {
                            // Add extra delay after failed placement in tower mode
                            blockPlacementJitter = 150; // Add extra delay after failed placement
                        }
                    } else {
                        // Occasional short delay before jumping to appear more human
                        new Thread(() -> {
                            try {
                                Thread.sleep(30 + random.nextInt(50));
                                mc.execute(() -> {
                                    if (isEnabled() && mc.player != null &&
                                            tower.getValue() && mc.options.jumpKey.isPressed() && lastPlacementSuccessful) {
                                        mc.player.jump();
                                    }
                                });
                            } catch (Exception e) {
                                // Ignore
                            }
                        }).start();
                    }
                }

                // Increment consecutive placement counter and check for pattern breaking
                consecutivePlacements++;
                if (graceTimer.getValue() && consecutivePlacements > 8 + random.nextInt(7) && random.nextFloat() > 0.7f) {
                    startGracePeriod();
                }
            }
        } else if (rotations.getValue()) {
            // Even if no placement is needed, still apply the rotations
            // This is important for consistent behavior
            handleRotations(targetYaw, getRandomizedPitch(targetPitch), rotationSpeed.getValue());
        }
    }

    /**
     * Special method to find a direct placement under the player for tower mode
     */
    private PlacementInfo findDirectPlacementUnder() {
        if (mc.player == null || mc.world == null) return null;

        // Get the position directly under the player
        BlockPos pos = new BlockPos(
                MathHelper.floor(mc.player.getX()),
                MathHelper.floor(mc.player.getY() - 0.5),
                MathHelper.floor(mc.player.getZ())
        );

        // Check if the position is valid
        if (!mc.world.getBlockState(pos).isAir()) {
            return null; // Already a block here
        }

        // Check the block below to place against
        BlockPos supportPos = pos.down();
        BlockState supportState = mc.world.getBlockState(supportPos);
        if (supportState.isAir() || !supportState.isSolidBlock(mc.world, supportPos)) {
            return null; // Can't place against air or non-solid block
        }

        // Create direct placement info
        Vec3d hitVec = new Vec3d(
                pos.getX() + 0.5,
                pos.getY(),
                pos.getZ() + 0.5
        );

        return new PlacementInfo(pos, supportPos, Direction.UP, hitVec);
    }

    /**
     * Gets a random horizontal direction to break patterns
     */
    private Direction getRandomHorizontalDirection() {
        Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        return directions[random.nextInt(directions.length)];
    }

    /**
     * Gets a randomized yaw angle to avoid duplicate rotations
     */
    private float getRandomizedYaw() {
        // Only update every 300-600ms to avoid rapid changes that look unnatural
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastYawUpdateTime < 300 + random.nextInt(300)) {
            return lastPlacementYaw;
        }

        // Generate a yaw that's at least 15 degrees different from last time
        float newYaw;
        do {
            newYaw = random.nextFloat() * 360f - 180f;
        } while (Math.abs(MathHelper.wrapDegrees(newYaw - lastPlacementYaw)) < 15f);

        lastYawUpdateTime = currentTime;
        return newYaw;
    }

    /**
     * Start a brief human-like pause in block placement
     */
    private void startGracePeriod() {
        inGracePeriod = true;
        // Random grace period between 200-700ms
        long graceDuration = 200 + random.nextInt(500);
        graceEndTime = System.currentTimeMillis() + graceDuration;
        consecutivePlacements = 0;
    }

    /**
     * End the grace period
     */
    private void endGracePeriod() {
        inGracePeriod = false;
        consecutivePlacements = 0;
    }

    /**
     * Reset the grace period timer without activating it
     */
    private void resetGracePeriod() {
        inGracePeriod = false;
        consecutivePlacements = 0;
        graceEndTime = 0;
    }

    /**
     * Get a randomized pitch value to make rotations less predictable
     */
    private float getRandomizedPitch(float basePitch) {
        // Only update periodically to avoid rapid changes
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPitchUpdateTime < 200 + random.nextInt(300)) {
            return lastPlacementPitch;
        }

        // If in tower mode, use steeper pitch
        if (isTowering) {
            basePitch = 85f + random.nextFloat() * 4f;
        }

        if (randomPitchRange.getValue() <= 0) return basePitch;

        float variation = randomPitchRange.getValue();
        // Weighted towards middle for more human-like distribution
        float randomFactor = (random.nextFloat() + random.nextFloat()) / 2.0f - 0.5f;
        float newPitch = basePitch + randomFactor * variation;

        // If antiDuplicate is on, ensure we don't repeat the same pitch
        if (antiDuplicate.getValue()) {
            if (Math.abs(newPitch - lastPlacementPitch) < 1.5f) {
                newPitch += 1.5f + random.nextFloat() * 1.0f;
            }
        }

        lastPitchUpdateTime = currentTime;
        return MathHelper.clamp(newPitch, 60f, 89f);
    }

    @Override
    public void onUpdate() {
        // Run movement fix again in the main update to ensure consistent application
        if (isEnabled() && moveFix.getValue() && mc.player != null && !movementModified) {
            applyMovementFix();
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
     * Implements human-like timing variations
     */
    private boolean canPlaceNow() {
        long currentTime = System.currentTimeMillis();

        // If we recently had a failed placement and preventSpamPlace is enabled, apply a cooldown
        if (preventSpamPlace.getValue() && !lastPlacementSuccessful &&
                placementAttempted && currentTime - failedPlacementTime < FAILED_PLACEMENT_COOLDOWN) {
            return false;
        }

        float baseDelay = delay.getValue() * 1000; // Convert to milliseconds

        // Add extra delay if towering
        if (isTowering) {
            baseDelay += 50 + random.nextInt(30);
        }

        // Apply delay randomization to appear more human-like
        float actualDelay = baseDelay;
        if (delayRandomization.getValue() > 0) {
            // Apply randomization based on setting
            float randomFactor = delayRandomization.getValue();
            // Use triangular distribution for more natural timing
            float randomValue = (random.nextFloat() + random.nextFloat()) / 2.0f;
            actualDelay += (randomValue * 2.0f - 1.0f) * randomFactor * baseDelay;
        }

        // Apply per-placement jitter for human-like variations (+/- 40ms)
        actualDelay += blockPlacementJitter;
        blockPlacementJitter = (long)(random.nextFloat() * 80) - 40;

        // Make sure delay isn't negative, enforce a minimum of 150ms for anti-cheat safety
        actualDelay = Math.max(150, actualDelay);

        // For reliable placement, use a more consistent delay with tower mode
        if (reliablePlacement.getValue() && isTowering) {
            actualDelay = Math.max(actualDelay, 200); // Minimum 200ms for tower mode
        }

        return currentTime - lastPlaceTime >= actualDelay;
    }

    /**
     * Improved placement finding with better reliability
     * Uses both RayCastUtil and direct methods for better results
     */
    private PlacementInfo findPlacement() {
        if (mc.player == null || mc.world == null) return null;

        // The position directly under the player
        Vec3d playerPos = mc.player.getPos();
        BlockPos basePos = new BlockPos(
                MathHelper.floor(playerPos.x),
                MathHelper.floor(playerPos.y - 0.5),
                MathHelper.floor(playerPos.z)
        );

        // First check if we need to place a block
        if (!mc.world.getBlockState(basePos).isAir()) {
            return null; // Already a block here
        }

        // Use additional checks for tower mode
        if (isTowering && tower.getValue()) {
            // In tower mode, first try direct placement for stability
            PlacementInfo directInfo = getDirectPlacement(basePos);
            if (directInfo != null) {
                return directInfo;
            }
        }

        // Try using RayCastUtil to find the best placement
        Optional<RayCastUtil.BlockPlacementInfo> rayCastPlacement =
                RayCastUtil.findBestBlockPlacement(basePos, strictCenter.getValue(), maxReach.getValue());

        if (rayCastPlacement.isPresent()) {
            RayCastUtil.BlockPlacementInfo info = rayCastPlacement.get();
            Vec3d hitVec = info.getHitVec();

            // Add small randomization to hit vector occasionally to look more human
            if (humanPatterns.getValue() && random.nextFloat() > 0.6f) {
                hitVec = hitVec.add(
                        (random.nextDouble() - 0.5) * 0.005,
                        (random.nextDouble() - 0.5) * 0.005,
                        (random.nextDouble() - 0.5) * 0.005
                );
            }

            return new PlacementInfo(
                    info.getTargetPos(),
                    info.getPlaceAgainst(),
                    info.getPlaceDir(),
                    hitVec
            );
        }

        // If raycast placement failed, try to place ahead based on movement
        Vec3d velocity = mc.player.getVelocity();
        boolean hasHorizontalMovement = velocity.horizontalLengthSquared() > 0.02;

        // AvoidAir check - place ahead of player when moving
        if (hasHorizontalMovement || (avoidAir.getValue() && mc.player.fallDistance > 0.5)) {
            // Look ahead based on movement or fall direction
            double lookAheadFactor = hasHorizontalMovement ? 1.0 : 0.2;
            BlockPos lookAheadPos;

            if (hasHorizontalMovement) {
                lookAheadPos = new BlockPos(
                        MathHelper.floor(playerPos.x + velocity.x * lookAheadFactor),
                        basePos.getY(),
                        MathHelper.floor(playerPos.z + velocity.z * lookAheadFactor)
                );
            } else {
                // When falling, try to place towards the opposite of the bridge direction
                Direction placeDir = bridgeDirection.getOpposite();
                Vec3d dirVec = Vec3d.of(placeDir.getVector());
                lookAheadPos = new BlockPos(
                        MathHelper.floor(playerPos.x + dirVec.x * lookAheadFactor),
                        basePos.getY(),
                        MathHelper.floor(playerPos.z + dirVec.z * lookAheadFactor)
                );
            }

            // If it's a different position and it needs a block
            if (!lookAheadPos.equals(basePos) && mc.world.getBlockState(lookAheadPos).isAir()) {
                rayCastPlacement = RayCastUtil.findBestBlockPlacement(lookAheadPos, strictCenter.getValue(), maxReach.getValue());

                if (rayCastPlacement.isPresent()) {
                    RayCastUtil.BlockPlacementInfo info = rayCastPlacement.get();
                    Vec3d hitVec = info.getHitVec();

                    // Add small randomization to hit vector occasionally
                    if (humanPatterns.getValue() && random.nextFloat() > 0.6f) {
                        hitVec = hitVec.add(
                                (random.nextDouble() - 0.5) * 0.005,
                                (random.nextDouble() - 0.5) * 0.005,
                                (random.nextDouble() - 0.5) * 0.005
                        );
                    }

                    return new PlacementInfo(
                            info.getTargetPos(),
                            info.getPlaceAgainst(),
                            info.getPlaceDir(),
                            hitVec
                    );
                }
            }
        }

        // If RayCastUtil fails, fall back to the original method
        return findPlacementLegacy(basePos);
    }

    /**
     * Direct placement method for tower mode
     */
    private PlacementInfo getDirectPlacement(BlockPos pos) {
        if (mc.world.getBlockState(pos).isAir()) {
            BlockPos downPos = pos.down();
            BlockState downState = mc.world.getBlockState(downPos);

            if (!downState.isAir() && downState.isSolidBlock(mc.world, downPos)) {
                // Calculate a stable hit vector for direct placement
                Vec3d hitVec = Vec3d.ofCenter(downPos).add(0, 0.5, 0);
                return new PlacementInfo(pos, downPos, Direction.UP, hitVec);
            }
        }
        return null;
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

            // Add small humanizing offset occasionally
            if (humanPatterns.getValue() && random.nextFloat() > 0.7f) {
                hitVec = hitVec.add(
                        (random.nextDouble() - 0.5) * 0.01,
                        (random.nextDouble() - 0.5) * 0.01,
                        (random.nextDouble() - 0.5) * 0.01
                );
            }

            // Check if player can reach this position
            if (mc.player.getEyePos().squaredDistanceTo(hitVec) <= maxReach.getValue() * maxReach.getValue()) {
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
     * Handles the rotation to place a block with improved humanization
     */
    private void handleRotations(float yaw, float pitch, float speed) {
        boolean isSilent = rotationMode.getValue().equals("Silent");
        boolean isBody = rotationMode.getValue().equals("Body");
        boolean useMoveFix = (isSilent || isBody) && moveFix.getValue();

        // Add rotation noise if enabled and not in reliable placement mode
        if (randomYawRange.getValue() > 0 && !reliablePlacement.getValue() && !isTowering) {
            float yawNoise = (random.nextFloat() * 2.0f - 1.0f) * randomYawRange.getValue();
            yaw += yawNoise;
        }

        // When towering, make sure we never use the same rotation twice in a row
        if (isTowering && antiDuplicate.getValue()) {
            // Ensure our pitch and yaw are different enough from last time
            if (Math.abs(MathHelper.wrapDegrees(yaw - lastPlacementYaw)) < 15f) {
                yaw += 15f + random.nextFloat() * 10f;
            }

            if (Math.abs(pitch - lastPlacementPitch) < 3f) {
                pitch += (random.nextFloat() * 4f - 2f);
                pitch = MathHelper.clamp(pitch, 70f, 89f);
            }
        }

        // Set the move fix context for improved movement transformation
        if (useMoveFix) {
            // Improved context handling for tower mode vs normal bridging
            if (isTowering) {
                RotationHandler.setMoveFixContext("scaffold_direct_100");
            } else {
                RotationHandler.setMoveFixContext("scaffold");
            }
        }

        // Variable hold time with randomization for human-like behavior (100-220ms)
        long holdTime = 100 + (humanPatterns.getValue() ? random.nextInt(120) : 0);

        // For tower mode or reliable placement, use longer hold time to ensure server gets the rotation
        if (isTowering || reliablePlacement.getValue()) {
            holdTime += 50; // Add 50ms to hold time for tower mode
        }

        // Use proper rotation speed parameter for smoothness
        if (humanPatterns.getValue() && !reliablePlacement.getValue() && random.nextFloat() > 0.85) {
            // Occasionally vary rotation speed slightly
            speed *= 0.95f + random.nextFloat() * 0.1f;
        }

        // Send rotation request with appropriate parameters
        if (isSilent) {
            // Silent rotations - send to server but don't show on client
            RotationHandler.requestRotation(
                    yaw, pitch, ROTATION_PRIORITY, holdTime, true, false, useMoveFix, speed, null
            );
        } else if (isBody) {
            // Body rotations - send to server and show on player's body model
            RotationHandler.requestRotation(
                    yaw, pitch, ROTATION_PRIORITY, holdTime, true, true, useMoveFix, speed, null
            );
        } else if (rotationMode.getValue().equals("Client")) {
            // Visible client rotations - no need for MoveFix
            RotationHandler.requestRotation(
                    yaw, pitch, ROTATION_PRIORITY, holdTime, false, false, false, speed, null
            );
        }

        rotationsSet = true;
    }

    /**
     * Calculates rotation angles to look at a position
     */
    private float[] calculateRotation(Vec3d pos) {
        // Default to a reasonable pitch if null or error
        if (pos == null) return new float[] { targetYaw, targetPitch };

        try {
            // Use improved rotation calculation from RayCastUtil
            return RayCastUtil.calculateLookAt(pos);
        } catch (Exception e) {
            // Fallback in case of error
            return new float[] { targetYaw, targetPitch };
        }
    }

    /**
     * Places a block at the given position with human-like timing
     * and improved verification
     */
    private void placeBlock(PlacementInfo placement) {
        if (mc.player == null || mc.interactionManager == null) return;

        // IMPORTANT FIX: Check if server rotation is set and stable before placing
        if (!RotationHandler.isRotationActive()) {
            placementFailCount++;
            return;
        }

        // When in tower mode or reliable placement mode, be extra careful with timing
        if (isTowering || reliablePlacement.getValue()) {
            long timeSinceRotation = System.currentTimeMillis() - lastRotationTime;
            if (timeSinceRotation < 50) { // Wait at least 50ms for rotation to take effect
                return;
            }
        }

        // Store the rotations used for placement
        lastPlacementYaw = RotationHandler.getServerYaw();
        lastPlacementPitch = RotationHandler.getServerPitch();

        // Find and select a block
        int blockSlot = findBlockInHotbar();
        if (blockSlot == -1) {
            // Update placement attempt tracking
            lastPlacementSuccessful = false;
            placementAttempted = true;
            failedPlacementTime = System.currentTimeMillis();
            placementFailCount++;
            return;
        }

        int prevSlot = mc.player.getInventory().selectedSlot;

        // Switch to the block slot if needed
        if (autoSwitch.getValue()) {
            mc.player.getInventory().selectedSlot = blockSlot;
        }

        // TOWER MODE IMPROVEMENT: Small delay before placement when towering
        if ((isTowering || reliablePlacement.getValue()) && placementFailCount > 0) {
            try {
                Thread.sleep(random.nextInt(15) + 10); // 10-25ms delay for better reliability
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        // Create the block hit result with small randomization to appear more human
        Vec3d hitVec = placement.hitVec;
        if (humanPatterns.getValue() && !reliablePlacement.getValue() && random.nextFloat() > 0.8) {
            // Small random variations in hit position
            hitVec = hitVec.add(
                    (random.nextDouble() - 0.5) * 0.001,
                    (random.nextDouble() - 0.5) * 0.001,
                    (random.nextDouble() - 0.5) * 0.001
            );
        }

        BlockHitResult hitResult = new BlockHitResult(
                hitVec,
                placement.placeDirection,
                placement.supportPos,
                false
        );

        // IMPORTANT FIX: Store the current state for verification after placement
        BlockState beforeState = mc.world.getBlockState(placement.targetPos);

        // Place the block
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);

        // Reset slot if needed
        if (autoSwitch.getValue() && prevSlot != blockSlot) {
            // Add small humanizing delay occasionally before switching back
            if (humanPatterns.getValue() && !reliablePlacement.getValue() && random.nextFloat() > 0.85) {
                try {
                    Thread.sleep(random.nextInt(20) + 5);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
            mc.player.getInventory().selectedSlot = prevSlot;
        }

        // Update placement attempt tracking
        lastPlaceTime = System.currentTimeMillis();
        placementAttempted = true;

        // IMPORTANT FIX: Wait a short time and verify the placement was successful
        try {
            // Small delay to allow the world to update (10-20ms)
            int verifyDelay = isTowering || reliablePlacement.getValue() ? 15 : 10;
            Thread.sleep(verifyDelay);

            // Check if the block place was successful by comparing states
            BlockState afterState = mc.world.getBlockState(placement.targetPos);
            boolean success = !afterState.equals(beforeState) && !afterState.isAir();

            if (success) {
                placementFailCount = 0;
                lastPlacementSuccessful = true;
                lastPlacedPos = placement.targetPos;
            } else {
                lastPlacementSuccessful = false;
                failedPlacementTime = System.currentTimeMillis();
                placementFailCount++;

                // For tower mode, more aggressive handling of failures
                if (isTowering && placementFailCount > 1) {
                    // Add extra delay after consecutive failures in tower mode
                    blockPlacementJitter = 150; // 150ms extra delay
                }
            }
        } catch (InterruptedException e) {
            // Just ignore interruption
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