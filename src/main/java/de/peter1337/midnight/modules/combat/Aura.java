package de.peter1337.midnight.modules.combat;

import de.peter1337.midnight.handler.RotationHandler;
import de.peter1337.midnight.utils.RayCastUtil;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class Aura extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    // Current target entity
    private Entity primaryTarget = null;
    private Vec3d targetPoint = null;

    // The rotation priority for this module
    private static final int ROTATION_PRIORITY = 100;

    // Session-specific variations
    private final float sessionCpsVariation;
    private final float sessionRotationVariation;
    private final float sessionYawRandomization;
    private final float sessionPitchRandomization;

    // --- Settings ---
    private final Setting<Float> range = register(
            new Setting<>("Range", 3.5f, 1.0f, 6.0f, "Attack range in blocks")
    );

    private final Setting<Float> cps = register(
            new Setting<>("CPS", 8.0f, 1.0f, 20.0f, "Clicks per second")
    );

    private final Setting<Boolean> randomCps = register(
            new Setting<>("RandomCPS", Boolean.TRUE, "Add randomization to CPS")
    );

    private final Setting<String> targetMode = register(
            new Setting<>("TargetMode", "Closest",
                    List.of("Closest", "Health", "Angle"),
                    "Method used to prioritize targets")
    );

    private final Setting<Boolean> targetPlayers = register(
            new Setting<>("TargetPlayers", Boolean.TRUE, "Target other players")
    );

    private final Setting<Boolean> targetHostiles = register(
            new Setting<>("TargetHostiles", Boolean.TRUE, "Target hostile mobs")
    );

    private final Setting<Boolean> targetPassives = register(
            new Setting<>("TargetPassives", Boolean.FALSE, "Target passive mobs")
    );

    private final Setting<String> rotationMode = register(
            new Setting<>("RotationMode", "Silent",
                    List.of("Silent", "Client", "Body"),
                    "Silent: server-only, Client: visible, Body: shows on body only")
    );

    private final Setting<Float> rotationSpeed = register(
            new Setting<>("RotationSpeed", 0.8f, 0.0f, 1.0f, "Speed of rotation to targets (0 = smooth, 1 = instant)")
    );

    private final Setting<Boolean> useMoveFixSetting = register(
            new Setting<>("MoveFix", Boolean.TRUE, "Correct movement direction during silent/body rotations")
    );

    private final Setting<Boolean> throughWalls = register(
            new Setting<>("ThroughWalls", Boolean.FALSE, "Attack through walls")
    );

    private final Setting<Boolean> smartAttack = register(
            new Setting<>("SmartAttack", Boolean.TRUE, "Only attack when weapon is charged (>= 0.95)")
    );

    private final Setting<Boolean> raytraceCheck = register(
            new Setting<>("RaytraceCheck", Boolean.TRUE, "Verify rotation line-of-sight before attacking")
    );

    private final Setting<Boolean> autoBlock = register(
            new Setting<>("AutoBlock", Boolean.FALSE, "Automatically block with weapon")
    );

    private final Setting<String> blockMode = register(
            new Setting<>("BlockMode", "Real",
                    List.of("Real", "Fake"),
                    "Real: Actually block, Fake: Visual only")
    );

    // --- New Settings for Enhanced Behavior ---
    private final Setting<Boolean> postAttackRandomization = register(
            new Setting<>("PostAttackRandomization", Boolean.TRUE, "Add realistic post-hit behavior")
    );

    private final Setting<Boolean> improvedRotations = register(
            new Setting<>("ImprovedRotations", Boolean.TRUE, "Use advanced rotation patterns for better bypassing")
    );

    private final Setting<Boolean> dynamicCpsPatterns = register(
            new Setting<>("DynamicCPS", Boolean.TRUE, "Use more realistic click patterns")
    );

    private final Setting<Boolean> aggressiveMode = register(
            new Setting<>("AggressiveMode", Boolean.TRUE, "Use more aggressive, responsive rotations like top PVP players")
    );

    private final Setting<Float> aggressionFactor = register(
            new Setting<>("AggressionFactor", 0.95f, 0.5f, 1.0f, "How aggressive rotations should be (higher = faster)")
    );

    private final Setting<Boolean> targetPrediction = register(
            new Setting<>("TargetPrediction", Boolean.TRUE, "Predict target movement for more accurate attacks")
    );

    private final Setting<Float> predictionStrength = register(
            new Setting<>("PredictionStrength", 0.40f, 0.0f, 1.0f, "Strength of movement prediction")
                    .dependsOn(targetPrediction)
    );

    private final Setting<Boolean> sprintReset = register(
            new Setting<>("SprintReset", Boolean.TRUE, "Reset sprint timing for better hit registration")
    );

    private final Setting<Boolean> enhancedMovementTracking = register(
            new Setting<>("EnhancedMovementTracking", Boolean.TRUE, "Better tracking for fast-moving targets and player movement")
    );

    private final Setting<Float> highSpeedRotationFactor = register(
            new Setting<>("HighSpeedRotationFactor", 2.5f, 1.0f, 5.0f, "Rotation speed multiplier for high-speed situations")
                    .dependsOn(enhancedMovementTracking)
    );

    private final Setting<Boolean> instantMode = register(
            new Setting<>("InstantMode", Boolean.TRUE, "Use instant rotations for maximum effectiveness (less legitimate)")
    );

    private final Setting<Boolean> rayCastBypass = register(
            new Setting<>("RayCastBypass", Boolean.TRUE, "Bypass raycast checks for better hit rate")
    );

    private final Setting<Boolean> perfectAim = register(
            new Setting<>("PerfectAim", Boolean.TRUE, "Perfect aim with minimal humanization")
    );

    // --- State Properties ---
    private long lastAttackTime = 0;
    private boolean rotating = false;
    private boolean attackedThisTick = false;
    private boolean isBlocking = false;
    private int ticksToWaitBeforeBlock = -1; // Timer for blocking delay

    // Attack limiter to prevent multiple clicks
    private boolean attackedThisGameTick = false;
    private int lastAttackTick = 0;
    private int currentGameTick = 0;
    private int attackCooldownTicks = 0;

    // Sprint control for hit registration
    private boolean needsSprintReset = false;
    private boolean performingSprintReset = false;
    private int sprintResetTicks = 0;
    private static final int SPRINT_RESET_DURATION = 2; // ticks

    // For post-attack behavior
    private boolean inPostAttackPhase = false;
    private long postAttackEndTime = 0;
    private float postAttackYawOffset = 0f;
    private float postAttackPitchOffset = 0f;

    // For attack patterns
    private int attackComboCount = 0;
    private long comboStartTime = 0;
    private boolean inBurstMode = false;
    private long burstEndTime = 0;
    private boolean inPauseMode = false;
    private long pauseEndTime = 0;

    // Target prediction
    private Vec3d lastTargetPos = null;
    private Vec3d targetVelocity = null;
    private long lastPosUpdateTime = 0;
    private boolean isTargetMovingFast = false;
    private Vec3d playerLastPos = null;
    private Vec3d playerVelocity = null;
    private boolean isPlayerMovingFast = false;

    // Advanced rotation control
    private boolean fastRotationMode = false;
    private long fastRotationEndTime = 0;
    private float precisionMultiplier = 1.0f;

    public Aura() {
        super("Aura", "Automatically attacks nearby entities", Category.COMBAT, "r");

        // Initialize session-specific variations for bypassing pattern detection
        sessionCpsVariation = 0.8f + random.nextFloat() * 0.4f; // 0.8-1.2 multiplier
        sessionRotationVariation = 0.85f + random.nextFloat() * 0.3f; // 0.85-1.15 multiplier

        // Randomize rotation behavior slightly for this session
        sessionYawRandomization = 0.7f + random.nextFloat() * 0.6f; // 0.7-1.3 multiplier
        sessionPitchRandomization = 0.8f + random.nextFloat() * 0.4f; // 0.8-1.2 multiplier

        // Apply session variations to default settings but ensure combat effectiveness
        float defaultCps = cps.getValue();
        cps.setValue(defaultCps * sessionCpsVariation);

        float defaultRotSpeed = rotationSpeed.getValue();
        // For aggressive mode, we want faster rotations but still with variation
        if (aggressiveMode.getValue()) {
            rotationSpeed.setValue(Math.max(0.7f, defaultRotSpeed * sessionRotationVariation));
        } else {
            rotationSpeed.setValue(defaultRotSpeed * sessionRotationVariation);
        }

        // Initialize prediction systems
        lastTargetPos = null;
        targetVelocity = null;
    }

    @Override
    public void onEnable() {
        primaryTarget = null;
        targetPoint = null;
        rotating = false;
        isBlocking = false;
        attackedThisTick = false;
        ticksToWaitBeforeBlock = -1;
        lastAttackTime = 0;

        // Reset attack limiter
        attackedThisGameTick = false;
        lastAttackTick = 0;
        currentGameTick = 0;
        attackCooldownTicks = 0;

        // Reset post-attack state
        inPostAttackPhase = false;
        postAttackEndTime = 0;

        // Reset attack pattern state
        attackComboCount = 0;
        comboStartTime = 0;
        inBurstMode = false;
        burstEndTime = 0;
        inPauseMode = false;
        pauseEndTime = 0;

        // Reset movement tracking
        lastTargetPos = null;
        targetVelocity = null;
        isTargetMovingFast = false;
        playerLastPos = null;
        playerVelocity = null;
        isPlayerMovingFast = false;

        // Reset sprint control
        needsSprintReset = false;
        performingSprintReset = false;
        sprintResetTicks = 0;
    }

    @Override
    public void onDisable() {
        primaryTarget = null;
        targetPoint = null;
        rotating = false;
        attackedThisTick = false;

        RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);

        if (isBlocking) {
            stopBlocking();
        }
        ticksToWaitBeforeBlock = -1;

        // Reset post-attack state
        inPostAttackPhase = false;
        postAttackEndTime = 0;
    }

    // Use a pre-update hook if available (e.g., ClientTickEvent.START_CLIENT_TICK)
    public void preUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) {
            // Ensure state is clean if disabled mid-tick
            if (isBlocking) stopBlocking();
            if (rotating) RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
            rotating = false;
            primaryTarget = null;
            targetPoint = null;
            return;
        }

        // Update game tick counter for attack synchronization
        currentGameTick++;
        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
        }

        // Reset attack flag at the beginning of each tick
        attackedThisGameTick = false;

        handleBlockTimer(); // Handle block delay
        findTargets();      // Find targets early
        processAttack();    // Process logic

        attackedThisTick = true; // Mark logic as done for this tick
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Skip if logic was done in preUpdate
        if (attackedThisTick) {
            attackedThisTick = false; // Reset for next tick
            return;
        }

        // Update game tick counter if preUpdate isn't called
        currentGameTick++;
        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
        }

        // Reset attack flag at the beginning of each tick
        attackedThisGameTick = false;

        processAttack();
    }

    /** Handles the tick-based delay before re-blocking after an attack. */
    private void handleBlockTimer() {
        if (this.ticksToWaitBeforeBlock > 0) {
            this.ticksToWaitBeforeBlock--;
        } else if (this.ticksToWaitBeforeBlock == 0) {
            handleAutoBlock(this.primaryTarget != null); // Try to resume blocking if target exists
            this.ticksToWaitBeforeBlock = -1; // Reset timer
        }
    }

    /**
     * Processes the main attack and rotation logic.
     */
    private void processAttack() {
        long currentTime = System.currentTimeMillis();

        // Handle sprint reset if needed, more aggressive in cheat mode
        if (sprintReset.getValue()) {
            handleSprintReset();
        }

        // Update player velocity tracking for better rotation handling
        updatePlayerMovementTracking();

        // Handle post-attack behavior if active
        if (inPostAttackPhase) {
            if (currentTime >= postAttackEndTime) {
                inPostAttackPhase = false;
            } else if (!instantMode.getValue()) {
                // Only apply post-attack behavior if not in instant mode
                if (primaryTarget != null && improvedRotations.getValue()) {
                    applyPostAttackRotations();
                }
            }
        }

        // Handle attack pattern states
        if (inBurstMode && currentTime >= burstEndTime) {
            inBurstMode = false;
        }

        if (inPauseMode && currentTime >= pauseEndTime) {
            inPauseMode = false;
        }

        // Calculate dynamic attack delay based on current state and settings
        long attackDelay = calculateAttackDelay();

        // Check if we can attack based on the calculated delay
        boolean canAttack = currentTime - lastAttackTime >= attackDelay;

        // Also check game tick-based cooldown to prevent multiple attacks per tick
        boolean tickCooldownDone = attackCooldownTicks <= 0 && !attackedThisGameTick;

        // --- Target Handling ---
        if (primaryTarget != null && mc.player != null) {
            // Cooldown Check - in cheat mode, can be more aggressive
            float cooldownProgress = mc.player.getAttackCooldownProgress(0.0f);
            boolean cooldownReady = !smartAttack.getValue() || cooldownProgress >= 0.9f; // Lowered threshold

            // Target Point Calculation
            targetPoint = getBestTargetPoint(primaryTarget);
            if (targetPoint == null) { // Fallback if no point found
                targetPoint = primaryTarget.getEyePos(); // Fallback to eye position
            }

            // Visibility Check (optionally bypassed)
            boolean canSeeTarget = true;
            if (!throughWalls.getValue() && !rayCastBypass.getValue() && !isPointVisible(targetPoint)) {
                // No target point visible and vision check not bypassed
                RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
                rotating = false;
                handleAutoBlock(false); // Stop blocking
                return; // Don't rotate or attack
            }

            // Rotation Handling with improved patterns
            handleRotations(); // Requests rotation via RotationHandler

            // Rotation Line-of-Sight Check (can be bypassed)
            boolean canHitWithRotation = true;
            if (raytraceCheck.getValue() && !rayCastBypass.getValue() && RotationHandler.isRotationActive() && rotating) {
                canHitWithRotation = RayCastUtil.canSeeEntityFromRotation(
                        primaryTarget,
                        RotationHandler.getServerYaw(),
                        RotationHandler.getServerPitch(),
                        range.getValue() + 1.0 // Buffer
                );
            }

            // Auto-Blocking (attempt to block if conditions met)
            if (ticksToWaitBeforeBlock < 0) { // Only if not on post-attack cooldown
                handleAutoBlock(true); // Attempt to block if target exists
            }

            // Attack Execution - more aggressive in cheat mode
            if (canAttack && tickCooldownDone && cooldownReady &&
                    ((RotationHandler.isRotationActive() && rotating && canHitWithRotation) || instantMode.getValue() || rayCastBypass.getValue()) &&
                    ticksToWaitBeforeBlock < 0 && !inPostAttackPhase && !inPauseMode &&
                    !performingSprintReset) {  // Don't attack during sprint reset

                boolean wasBlocking = isBlocking;
                if (wasBlocking) {
                    stopBlocking();
                }

                // Check if we need to reset sprint before attacking
                if (sprintReset.getValue() && isSprinting() && !needsSprintReset && !performingSprintReset) {
                    needsSprintReset = true;
                    sprintResetTicks = 0;
                    if (instantMode.getValue()) {
                        // In instant mode, process sprint reset immediately
                        handleSprintReset();
                        // And continue with attack
                    } else {
                        return; // Skip this attack cycle in normal mode
                    }
                }

                // Execute the attack
                attack(primaryTarget);

                // Handle post-attack behavior
                if (postAttackRandomization.getValue() && !instantMode.getValue() && random.nextFloat() < 0.3f) {
                    setupPostAttackBehavior();
                }

                // Only update combo counter if we actually attacked
                if (attackedThisGameTick) {
                    // Update attack combo counter
                    attackComboCount++;
                    if (attackComboCount == 1) {
                        comboStartTime = currentTime;
                    }

                    // Reset combo if it's been too long since start
                    if (currentTime - comboStartTime > 2000) { // 2 seconds max for a combo
                        attackComboCount = 1;
                        comboStartTime = currentTime;
                    }

                    // Potentially enter burst mode based on combo
                    if (dynamicCpsPatterns.getValue()) {
                        if (attackComboCount >= 3 && random.nextFloat() < 0.35f && !inBurstMode && !inPauseMode) {
                            // In cheat mode, prefer burst mode
                            inBurstMode = true;
                            burstEndTime = currentTime + 300 + random.nextInt(400); // 300-700ms burst
                        }
                    }

                    // Always update the time after attacking to maintain accurate CPS
                    lastAttackTime = currentTime;

                    if (wasBlocking) {
                        this.ticksToWaitBeforeBlock = instantMode.getValue() ? 2 : 5; // Faster blocking in instant mode
                    }
                }
            }
        } else {
            // --- No Target ---
            targetPoint = null; // Clear target point
            RotationHandler.cancelRotationByPriority(ROTATION_PRIORITY);
            rotating = false;
            if (ticksToWaitBeforeBlock < 0) { // Only stop blocking if not on cooldown
                handleAutoBlock(false);
            }

            // Reset attack patterns when no target
            attackComboCount = 0;
            inBurstMode = false;
            inPauseMode = false;
            needsSprintReset = false;
            performingSprintReset = false;
        }
    }

    /**
     * Handles sprint reset timing for optimal hit registration
     */
    private void handleSprintReset() {
        if (!sprintReset.getValue()) {
            needsSprintReset = false;
            performingSprintReset = false;
            return;
        }

        if (needsSprintReset && !performingSprintReset) {
            // Start the sprint reset process
            setSprinting(false);
            performingSprintReset = true;
            sprintResetTicks = 0;
        }

        if (performingSprintReset) {
            sprintResetTicks++;
            if (sprintResetTicks >= SPRINT_RESET_DURATION) {
                // Re-enable sprint after the reset duration
                setSprinting(true);
                performingSprintReset = false;
                needsSprintReset = false;
            }
        }
    }

    /**
     * Updates tracking of the player's movement for better rotations
     */
    private void updatePlayerMovementTracking() {
        if (!enhancedMovementTracking.getValue() || mc.player == null) return;

        Vec3d currentPos = mc.player.getPos();
        long currentTime = System.currentTimeMillis();

        if (playerLastPos != null) {
            // Calculate time delta in seconds
            float deltaTime = (currentTime - lastPosUpdateTime) / 1000.0f;
            if (deltaTime > 0 && deltaTime < 0.5f) { // Ignore long time gaps
                // Calculate player velocity
                Vec3d newVelocity = currentPos.subtract(playerLastPos).multiply(1.0f / deltaTime);

                if (playerVelocity == null) {
                    playerVelocity = newVelocity;
                } else {
                    // Smooth velocity updates
                    playerVelocity = playerVelocity.multiply(0.7).add(newVelocity.multiply(0.3));
                }

                // Check if player is moving fast
                double playerSpeed = playerVelocity.horizontalLength();
                isPlayerMovingFast = playerSpeed > 3.5; // ~walking speed is around 4.3
            }
        }

        playerLastPos = currentPos;
    }

    /**
     * Checks if the player is currently sprinting
     */
    private boolean isSprinting() {
        return mc.player != null && mc.player.isSprinting();
    }

    /**
     * Sets the player's sprint state safely
     */
    private void setSprinting(boolean sprint) {
        if (mc.player != null && mc.options != null) {
            mc.options.sprintKey.setPressed(sprint);
        }
    }

    /**
     * Sets up post-attack behavior like slight head movement
     */
    private void setupPostAttackBehavior() {
        inPostAttackPhase = true;

        // Random duration between 150-450ms
        int duration = 150 + random.nextInt(300);
        postAttackEndTime = System.currentTimeMillis() + duration;

        // Calculate random offset for "recoil" effect
        postAttackYawOffset = (random.nextFloat() - 0.5f) * 8.0f;
        postAttackPitchOffset = (random.nextFloat() - 0.5f) * 4.0f;

        // Sometimes add a slight upward movement (like recoil)
        if (random.nextFloat() < 0.6f) {
            postAttackPitchOffset = -Math.abs(postAttackPitchOffset); // Make negative (upward)
        }
    }

    /**
     * Applies post-attack rotation effects, simulating recoil/reaction
     */
    private void applyPostAttackRotations() {
        if (!inPostAttackPhase || primaryTarget == null) return;

        float progress = 1.0f - (float)(postAttackEndTime - System.currentTimeMillis()) / 300f;
        progress = MathHelper.clamp(progress, 0.0f, 1.0f);

        // Apply more at start, then gradually return to normal
        float factor = (float) (1.0f - Math.pow(progress, 2));
        float currentYawOffset = postAttackYawOffset * factor;
        float currentPitchOffset = postAttackPitchOffset * factor;

        float currentServerYaw = RotationHandler.getServerYaw();
        float currentServerPitch = RotationHandler.getServerPitch();

        // Apply the post-attack offsets as a separate rotation request with higher priority
        RotationHandler.requestRotation(
                currentServerYaw + currentYawOffset,
                currentServerPitch + currentPitchOffset,
                ROTATION_PRIORITY + 1, // Higher priority to override normal rotations
                20, // Very short duration, will be refreshed next tick if needed
                rotationMode.getValue().equals("Silent"),
                rotationMode.getValue().equals("Body"),
                useMoveFixSetting.getValue(),
                null
        );
    }

    /**
     * Calculates the dynamic attack delay based on current patterns and settings
     * Optimized for cheat-like behavior with extremely fast attacks
     */
    private long calculateAttackDelay() {
        float targetCPS = cps.getValue();
        float baseDelayMs = 1000.0f / targetCPS;

        // In instant mode, maximize CPS
        if (instantMode.getValue()) {
            // Use the highest possible CPS that won't get instantly flagged
            return Math.max(45, (long)(baseDelayMs * 0.5f)); // Much faster clicks
        }

        // Check for burst mode (faster clicks)
        if (inBurstMode) {
            return (long)(baseDelayMs * 0.5f); // Much faster in burst mode
        }

        // Check for pause mode (no clicks)
        if (inPauseMode) {
            return Long.MAX_VALUE; // Effectively no attacks during pause
        }

        // Dynamic CPS patterns
        if (dynamicCpsPatterns.getValue()) {
            // Aggressive clicking patterns
            if (random.nextFloat() < 0.15f) { // More frequent quick clicks
                return (long)(baseDelayMs * 0.6f);
            } else if (random.nextFloat() < 0.04f) { // Less frequent pauses
                return (long)(baseDelayMs * 1.2f);
            } else if (randomCps.getValue()) {
                // Skewed toward faster clicks
                float stdDev = baseDelayMs * 0.1f;
                float randomFactor = (float)((random.nextGaussian() * stdDev) / baseDelayMs);
                // Bias toward faster clicks (-0.1 offset)
                return (long)(baseDelayMs * (0.9f + MathHelper.clamp(randomFactor, -0.25f, 0.1f)));
            }
        } else if (randomCps.getValue()) {
            // Simpler randomization with bias toward faster clicks
            float randomFactor = 0.15f;
            float minDelayMs = baseDelayMs * (1.0f - randomFactor);
            float maxDelayMs = baseDelayMs * (1.0f + randomFactor * 0.5f); // Asymmetric range
            return (long)(minDelayMs + random.nextFloat() * (maxDelayMs - minDelayMs));
        }

        // Ensure we never go below a minimum delay to prevent server-side rejection
        return Math.max(45, (long)baseDelayMs); // Minimum 45ms (less than 3 game ticks)
    }

    /** Handles starting/stopping blocking based on settings and conditions. */
    private void handleAutoBlock(boolean shouldBlock) {
        if (!autoBlock.getValue()) {
            if (isBlocking) stopBlocking();
            return;
        }
        if (ticksToWaitBeforeBlock >= 0) { // Check block cooldown timer
            if(isBlocking) stopBlocking(); // Ensure we stop if on cooldown
            return;
        }

        boolean canCurrentlyBlock = shouldBlock && canBlockWithCurrentItem();

        if (canCurrentlyBlock && !isBlocking) { // Start blocking
            if (blockMode.getValue().equals("Real")) startRealBlocking();
            else if (blockMode.getValue().equals("Fake")) startFakeBlocking();
        } else if (!canCurrentlyBlock && isBlocking) { // Stop blocking
            stopBlocking();
        }
    }

    /** Starts real blocking - sends actual block commands to server */
    private void startRealBlocking() {
        if (mc.player == null || mc.interactionManager == null || isBlocking) return;
        mc.options.useKey.setPressed(true);
        // Optional: Check which hand has shield/sword and use that hand?
        if (!mc.player.isUsingItem()) { // Avoid spamming interact packet if holding key is enough
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND); // Adjust hand if needed
        }
        isBlocking = true;
    }

    /** Stops real blocking */
    private void stopRealBlocking() {
        if (mc.player == null || !isBlocking) return;
        mc.options.useKey.setPressed(false);
        // If interactItem was needed, maybe send stop action? Needs testing.
        isBlocking = false;
    }

    /** Starts fake blocking - client-side only */
    private void startFakeBlocking() {
        if (mc.player == null || isBlocking) return;
        if (!mc.player.isUsingItem()) { // Only start if not already using
            mc.player.setCurrentHand(Hand.MAIN_HAND); // Adjust hand if needed
        }
        isBlocking = true;
    }

    /** Stops fake blocking */
    private void stopFakeBlocking() {
        if (mc.player == null || !isBlocking) return;
        // Only clear if we were the one using the item (check hand?)
        if (mc.player.getActiveHand() == Hand.MAIN_HAND) { // Adjust hand if needed
            mc.player.clearActiveItem();
        }
        isBlocking = false;
    }

    /** Stops blocking based on current mode */
    private void stopBlocking() {
        if (!isBlocking) return;
        if (blockMode.getValue().equals("Real")) stopRealBlocking();
        else if (blockMode.getValue().equals("Fake")) stopFakeBlocking();
        isBlocking = false; // Ensure flag is always false after calling stop
    }

    /** Checks if the player can block with the current item in main or offhand */
    private boolean canBlockWithCurrentItem() {
        if (mc.player == null) return false;
        ItemStack mainHandItem = mc.player.getMainHandStack();
        ItemStack offHandItem = mc.player.getOffHandStack();
        return (!mainHandItem.isEmpty() && (mainHandItem.getItem() instanceof SwordItem || mainHandItem.getItem() instanceof ShieldItem)) ||
                (!offHandItem.isEmpty() && (offHandItem.getItem() instanceof ShieldItem));
    }

    /** Finds the best point on an entity's hitbox to target, with prediction if enabled. */
    private Vec3d getBestTargetPoint(Entity entity) {
        if (entity == null || mc.player == null) return entity != null ? entity.getEyePos() : null;

        // Update velocity tracking for prediction
        if (targetPrediction.getValue() && entity == primaryTarget) {
            updateTargetPrediction(entity);
        }

        // Get current position (possibly with prediction)
        Vec3d targetPos;
        if (targetPrediction.getValue() && targetVelocity != null && entity == primaryTarget) {
            // Base prediction strength - increased for cheat mode
            float pingFactor = predictionStrength.getValue();

            // Enhanced prediction for fast-moving targets
            if (isTargetMovingFast) {
                pingFactor *= 1.75f; // Increased for better prediction
            }

            // Calculate predicted position with aggressive prediction
            Vec3d predictedOffset = targetVelocity.multiply(pingFactor);
            targetPos = entity.getPos().add(predictedOffset);

            // Calculate optimal hit point
            if (entity instanceof PlayerEntity) {
                // Target the perfect hit point for maximum hit registration
                float targetHeight = entity.getEyeHeight(entity.getPose()) * 0.7f; // Lower for better hit reg
                targetPos = targetPos.add(0, targetHeight, 0);

                // If sprinting, aim slightly lower for better hit registration
                if (isSprinting() && !performingSprintReset) {
                    targetPos = targetPos.add(0, -0.15, 0);
                }
            } else {
                // For non-players, aim precisely at vital areas
                targetPos = targetPos.add(0, entity.getEyeHeight(entity.getPose()) * 0.8f, 0);
            }
        } else {
            // Standard targeting - straight at optimal hit point
            if (entity instanceof PlayerEntity) {
                Vec3d basePos = entity.getPos();
                float targetHeight = entity.getEyeHeight(entity.getPose()) * 0.75f;

                // If sprinting, aim slightly lower for better hit registration
                if (isSprinting() && !performingSprintReset) {
                    targetHeight -= 0.12f;
                }

                return basePos.add(0, targetHeight, 0);
            } else if (throughWalls.getValue() || rayCastBypass.getValue()) {
                // For mobs through walls, aim at center
                Box box = entity.getBoundingBox();
                return new Vec3d((box.minX + box.maxX) / 2.0,
                        (box.minY + box.maxY) * 0.55, // Aim at 55% height for better hits
                        (box.minZ + box.maxZ) / 2.0);
            } else {
                // Try to find visible point
                Vec3d visiblePoint = RayCastUtil.getVisiblePoint(entity);
                if (visiblePoint != null) return visiblePoint;

                // Fallback to eye position
                return entity.getEyePos();
            }
        }

        return targetPos;
    }

    /**
     * Updates target velocity prediction
     */
    private void updateTargetPrediction(Entity entity) {
        long currentTime = System.currentTimeMillis();
        Vec3d currentPos = entity.getPos();

        if (lastTargetPos != null && entity == primaryTarget) {
            // Calculate time delta in seconds
            float deltaTime = (currentTime - lastPosUpdateTime) / 1000.0f;
            if (deltaTime > 0 && deltaTime < 0.5f) { // Ignore long time gaps
                // Calculate velocity
                Vec3d newVelocity = currentPos.subtract(lastTargetPos).multiply(1.0f / deltaTime);

                if (targetVelocity == null) {
                    targetVelocity = newVelocity;
                } else {
                    // Use more responsive velocity tracking for fast-moving targets
                    // When target is moving fast, trust newer measurements more
                    double currentSpeed = newVelocity.horizontalLength();
                    float newDataWeight = currentSpeed > 4.0 ? 0.5f : 0.3f;

                    // Smooth velocity updates with dynamic weighting
                    targetVelocity = targetVelocity.multiply(1.0 - newDataWeight).add(newVelocity.multiply(newDataWeight));
                }

                // Check if target is moving fast
                double targetSpeed = targetVelocity.horizontalLength();
                isTargetMovingFast = targetSpeed > 3.5; // Threshold for "fast movement"

                // Cap maximum prediction velocity to avoid extreme predictions
                double speedSq = targetVelocity.lengthSquared();
                if (speedSq > 36.0) { // Max speed ~6 blocks/sec (increased from 5)
                    targetVelocity = targetVelocity.multiply(6.0 / Math.sqrt(speedSq));
                }
            }
        }

        lastTargetPos = currentPos;
        lastPosUpdateTime = currentTime;
    }

    /** Check if a specific point is visible to the player using RayCastUtil. */
    private boolean isPointVisible(Vec3d point) {
        if (mc.player == null || mc.world == null || point == null) return false;
        // Assumes RayCastUtil.canSeePosition(Vec3d) is correctly implemented
        return RayCastUtil.canSeePosition(point);
    }

    /** Finds and prioritizes potential targets */
    private void findTargets() {
        primaryTarget = null; // Reset target each tick
        if (mc.player == null || mc.world == null) return;

        List<Entity> potentialTargets = new ArrayList<>();
        float currentRange = range.getValue();
        double rangeSq = currentRange * currentRange; // Use squared range for box check

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || entity.isRemoved() || !(entity instanceof LivingEntity livingEntity)) continue;
            if (livingEntity.isDead() || livingEntity.getHealth() <= 0) continue;

            // Rough distance check
            double roughRangeCheck = Math.pow(currentRange + entity.getWidth() + 2.0, 2);
            if (mc.player.squaredDistanceTo(entity) > roughRangeCheck) continue;


            // Type filters
            if (entity instanceof PlayerEntity player) {
                if (!targetPlayers.getValue() || player.isSpectator() || player.isCreative()) continue;
                // Add team/friend checks if needed
            } else if (entity instanceof HostileEntity) {
                if (!targetHostiles.getValue()) continue;
            } else if (entity instanceof PassiveEntity) {
                if (!targetPassives.getValue()) continue;
            } else {
                continue; // Skip other types
            }

            // Precise range check (distance to closest point on bounding box)
            if (!isEntityInRange(entity, currentRange)) continue;

            // Visibility check (if not attacking through walls)
            // Check if ANY part is visible for targeting purposes
            if (!throughWalls.getValue() && !RayCastUtil.canSeeEntity(entity)) {
                continue;
            }

            potentialTargets.add(entity);
        }

        // Sort and select target
        if (!potentialTargets.isEmpty()) {
            switch (targetMode.getValue()) {
                case "Closest":
                    potentialTargets.sort(Comparator.comparingDouble(mc.player::squaredDistanceTo));
                    break;
                case "Health":
                    potentialTargets.sort(Comparator.comparingDouble(e -> ((LivingEntity) e).getHealth()));
                    break;
                case "Angle":
                    final float playerYaw = mc.player.getYaw();
                    final float playerPitch = mc.player.getPitch();
                    potentialTargets.sort(Comparator.comparingDouble(entity -> {
                        float[] rotations = RotationHandler.calculateLookAt(entity.getEyePos());
                        float yawDiff = Math.abs(MathHelper.wrapDegrees(rotations[0] - playerYaw));
                        float pitchDiff = Math.abs(rotations[1] - playerPitch);
                        return yawDiff + pitchDiff; // Simple sum or sqrt(yaw^2 + pitch^2)
                    }));
                    break;
                default:
                    potentialTargets.sort(Comparator.comparingDouble(mc.player::squaredDistanceTo));
                    break;
            }
            primaryTarget = potentialTargets.get(0); // Pick the best one
        }
    }

    /** Checks if any part of an entity's hitbox is within range using bounding box distance. */
    private boolean isEntityInRange(Entity entity, float range) {
        if (mc.player == null || entity == null) return false;
        Vec3d playerEyePos = mc.player.getEyePos();
        Box entityBox = entity.getBoundingBox();
        double rangeSq = range * range;
        double closestX = MathHelper.clamp(playerEyePos.x, entityBox.minX, entityBox.maxX);
        double closestY = MathHelper.clamp(playerEyePos.y, entityBox.minY, entityBox.maxY);
        double closestZ = MathHelper.clamp(playerEyePos.z, entityBox.minZ, entityBox.maxZ);
        return playerEyePos.squaredDistanceTo(closestX, closestY, closestZ) <= rangeSq;
    }

    /**
     * Handles rotations to the primary target.
     * Now optimized for maximum effectiveness without legitimacy concerns.
     */
    private void handleRotations() {
        if (mc.player == null || targetPoint == null || primaryTarget == null) return;

        // Calculate base rotations to target
        float[] baseRotations;

        // Always fast mode
        fastRotationMode = true;

        // Direct targeting for maximum effectiveness
        if (perfectAim.getValue()) {
            // Perfect aim - target the most optimal hit point
            float targetHeight;
            if (primaryTarget instanceof PlayerEntity) {
                // Target slightly below eye level for optimal hit registration
                targetHeight = primaryTarget.getEyeHeight(primaryTarget.getPose()) * 0.75f;
            } else {
                targetHeight = primaryTarget.getEyeHeight(primaryTarget.getPose()) * 0.9f;
            }

            Vec3d perfectTargetPos = primaryTarget.getPos().add(0, targetHeight, 0);
            baseRotations = RotationHandler.calculateLookAt(perfectTargetPos);
        } else {
            // Standard targeting
            baseRotations = RotationHandler.calculateLookAt(targetPoint);
        }

        // Skip humanization in instant mode
        if (improvedRotations.getValue() && !instantMode.getValue()) {
            // Only apply minimal humanization
            applyMinimalHumanization(baseRotations);
        }

        // Rotation parameters
        boolean silent = true;
        boolean bodyOnly = rotationMode.getValue().equals("Body");
        boolean moveFixRequired = useMoveFixSetting.getValue();

        // Always use server rotations in cheat mode for consistency
        float currentYaw = RotationHandler.getServerYaw();
        float currentPitch = RotationHandler.getServerPitch();

        // Max speed for cheat mode
        float speed = 1.0f;

        if (instantMode.getValue()) {
            // Skip smooth rotation completely - just set exact rotations
            RotationHandler.requestRotation(
                    baseRotations[0],
                    baseRotations[1],
                    ROTATION_PRIORITY,
                    20, // Extremely short duration for instant updates
                    silent,
                    bodyOnly,
                    moveFixRequired,
                    null
            );
        } else {
            // Use very high speed but still apply smoothing
            speed = Math.min(1.0f, Math.max(0.95f, rotationSpeed.getValue() * 2.0f * aggressionFactor.getValue()));

            // Apply enhanced speed for movement if enabled
            if (enhancedMovementTracking.getValue()) {
                speed *= highSpeedRotationFactor.getValue();
            }

            // Clamp to valid range (keep high)
            speed = MathHelper.clamp(speed, 0.9f, 1.0f);

            // Apply smooth rotation with high speed
            float[] smoothedRotations = RotationHandler.smoothRotation(
                    currentYaw,
                    currentPitch,
                    baseRotations[0],
                    baseRotations[1],
                    speed
            );

            // Very frequent rotation updates
            RotationHandler.requestRotation(
                    smoothedRotations[0],
                    smoothedRotations[1],
                    ROTATION_PRIORITY,
                    15, // Extremely frequent updates for more responsive aiming
                    silent,
                    bodyOnly,
                    moveFixRequired,
                    null
            );
        }

        rotating = true;
    }

    /**
     * Checks if we should enter fast rotation mode (when we need to quickly snap to targets)
     */
    private void checkForFastRotationMode() {
        if (!aggressiveMode.getValue()) {
            fastRotationMode = false;
            return;
        }

        long currentTime = System.currentTimeMillis();

        // If already in fast mode, check if it should end
        if (fastRotationMode && currentTime > fastRotationEndTime) {
            fastRotationMode = false;
        }

        if (primaryTarget == null || mc.player == null) {
            fastRotationMode = false;
            return;
        }

        // Check conditions for entering fast rotation mode:

        // 1. Target is moving quickly relative to us
        boolean targetMovingQuickly = isTargetMovingFast;

        // 2. Player is moving quickly
        boolean playerMovingQuickly = isPlayerMovingFast;

        // 3. We're ready to attack and not already looking at target
        boolean readyToAttack = false;
        float cooldownProgress = mc.player.getAttackCooldownProgress(0.0f);
        if (cooldownProgress >= 0.92f) {
            readyToAttack = true;
        }

        // 4. Target is not in our field of view
        float[] targetRotations = RotationHandler.calculateLookAt(targetPoint);
        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetRotations[0] - RotationHandler.getServerYaw()));
        float pitchDiff = Math.abs(targetRotations[1] - RotationHandler.getServerPitch());
        boolean notInFov = yawDiff > 30.0f || pitchDiff > 20.0f; // Reduced thresholds for faster reactions

        // 5. We're very close to the target (need fast reactions)
        boolean veryClose = mc.player.squaredDistanceTo(primaryTarget) < 9.0; // Within 3 blocks

        // 6. Sprint is active (need faster rotations during sprint)
        boolean sprinting = isSprinting();

        // Enter fast rotation mode if any critical condition is met
        if ((targetMovingQuickly && readyToAttack) ||
                (playerMovingQuickly && notInFov) ||
                (notInFov && readyToAttack) ||
                (veryClose && notInFov) ||
                (sprinting && notInFov && veryClose)) {

            fastRotationMode = true;
            // Stay in fast rotation mode for a short time
            fastRotationEndTime = currentTime + 350 + random.nextInt(150);
        }
    }

    /**
     * Applies minimal humanization during fast rotations
     */
    private void applyMinimalHumanization(float[] rotations) {
        // Even in cheat mode, a TINY bit of humanization helps bypass some basic checks
        // But much less than before
        if (!perfectAim.getValue()) {
            if (random.nextFloat() < 0.03f) {
                rotations[0] += (random.nextFloat() - 0.5f) * 0.2f;
                rotations[1] += (random.nextFloat() - 0.5f) * 0.1f;
            }
        }
    }

    /**
     * Applies humanization to rotations to make them appear more natural
     */
    private void applyHumanization(float[] rotations, float speed) {
        // Sometimes add slight "overshoot" that humans naturally do
        if (random.nextFloat() < 0.15f) {
            rotations[0] += (random.nextFloat() - 0.5f) * (1.0f - speed) * 2.2f * sessionYawRandomization;
            rotations[1] += (random.nextFloat() - 0.5f) * (1.0f - speed) * 1.0f * sessionPitchRandomization;
        }

        // Add micro-stutters that happen due to mouse sensor imperfections
        if (random.nextFloat() < 0.08f) {
            rotations[0] += (random.nextInt(2) * 2 - 1) * 0.05f * sessionYawRandomization;
        }

        // Sometimes slightly under-rotate to target to simulate human imprecision
        if (random.nextFloat() < 0.2f) {
            float underRotateFactor = 0.95f + random.nextFloat() * 0.05f;
            float currentYaw = RotationHandler.getServerYaw();
            float yawDiff = MathHelper.wrapDegrees(rotations[0] - currentYaw);
            rotations[0] = currentYaw + yawDiff * underRotateFactor;
        }

        // Add very slight time-based oscillation
        long time = System.currentTimeMillis() % 1000; // 1 second cycle
        float oscillation = (float) Math.sin(time / 1000.0 * Math.PI * 2) * 0.15f;
        rotations[0] += oscillation * sessionYawRandomization * (1.0f - speed);
    }

    /** Attacks the target entity with attack-limiter */
    private void attack(Entity target) {
        if (mc.player == null || mc.interactionManager == null || target == null) return;

        // Check if we've already attacked this game tick
        if (attackedThisGameTick) {
            return;
        }

        // Check if the attack cooldown has expired
        if (attackCooldownTicks > 0) {
            return;
        }

        // Check if we're at the minimum CPS interval since last attack
        // (Extra safeguard against multiple clicks)
        long minAttackInterval = instantMode.getValue() ? 45 : 50; // In milliseconds
        if (System.currentTimeMillis() - lastAttackTime < minAttackInterval) {
            return;
        }

        // Check for range
        if (isEntityInRange(target, range.getValue())) {
            // Execute the attack
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);

            // Mark that we've attacked this tick
            attackedThisGameTick = true;
            lastAttackTick = currentGameTick;

            // Set cooldown for next attack (measured in game ticks)
            if (instantMode.getValue()) {
                attackCooldownTicks = 1; // One tick cooldown in instant mode
            } else {
                float targetCPS = cps.getValue();
                // Convert CPS to ticks (20 ticks per second)
                attackCooldownTicks = Math.max(1, Math.round(20.0f / targetCPS));
            }
        }
    }
}