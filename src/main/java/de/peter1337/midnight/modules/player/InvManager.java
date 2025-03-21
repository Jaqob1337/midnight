package de.peter1337.midnight.modules.player;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.manager.InventoryManager;
import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import de.peter1337.midnight.utils.ItemType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.*;

public class InvManager extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // --- Operation Tracking and Loop Prevention ---
    private final Map<String, Long> recentOperations = new HashMap<>();
    private static final long BASE_OPERATION_COOLDOWN_MS = 500; // Reduced from 1000 for faster operation
    private static final long TRASH_COOLDOWN_MS = 400; // Reduced from 800
    private static final long ARMOR_COOLDOWN_MS = 600; // Reduced from 1200

    // Track recent swap pairs to prevent endless swapping
    private final Set<String> recentSwapPairs = new HashSet<>();
    private static final long SWAP_COOLDOWN_MS = 10000; // 10 seconds cooldown for the same pair

    // Short-term memory to prevent immediate reversals
    private final Queue<String> recentSwaps = new LinkedList<>();
    private static final int RECENT_SWAPS_MAX_SIZE = 5; // Remember the last 5 swaps

    // --- Settings ---
    private final Setting<Boolean> onlyWhenInventoryOpen = register(new Setting<>("OnlyWhenInventoryOpen", Boolean.FALSE, "Only work when inventory is open"));
    private final Setting<Float> delay = register(new Setting<>("Delay", 0.2f, 0.0f, 2.0f, "Delay between actions in seconds")); // Reduced default from 0.3
    private final Setting<Boolean> randomDelay = register(new Setting<>("RandomDelay", Boolean.TRUE, "Add slight variation to delay"));
    private final Setting<Boolean> autoArmor = register(new Setting<>("AutoArmor", Boolean.TRUE, "Automatically equip best armor"));
    private final Setting<Boolean> sortInventory = register(new Setting<>("SortInventory", Boolean.TRUE, "Sort inventory items (weapons, tools, food, blocks)"));
    private final Setting<Boolean> cleanInventory = register(new Setting<>("CleanInventory", Boolean.TRUE, "Drop trash items and inferior duplicates"));
    private final Setting<Boolean> humanPatterns = register(new Setting<>("HumanPatterns", Boolean.TRUE, "Mimic human-like interaction patterns"));

    // NEW: Safety checks and cursor handling setting
    private final Setting<Boolean> safeCursorHandling = register(
            new Setting<>("SafeCursorHandling", Boolean.TRUE, "Ensure no items get stuck in cursor by adding extra checks")
    );

    // --- Progress Tracking ---
    private long lastActionTime = 0;
    private ActionState currentState = ActionState.IDLE;

    // Track if there's an item on cursor
    private boolean itemOnCursor = false;
    private long cursorItemCheckTime = 0;
    private static final long CURSOR_CHECK_INTERVAL = 500; // Check cursor every 500ms

    // --- Improved Failure Handling ---
    private int consecutiveFailures = 0;
    private static final int MAX_FAILURES = 5;
    private int loopDetectionCounter = 0;
    private static final int LOOP_DETECTION_THRESHOLD = 10;

    // --- Action States ---
    private enum ActionState {
        IDLE,
        CLEANING_INVENTORY,
        EQUIPPING_ARMOR,
        SORTING_INVENTORY,
        FIXING_CURSOR // New state for fixing items stuck on cursor
    }

    public InvManager() {
        super("InvManager", "Automatically manages your inventory", Category.PLAYER, "y");
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
        // Make sure to clear cursor when disabling
        clearCursor();
    }

    private void resetState() {
        currentState = ActionState.IDLE;
        lastActionTime = 0;
        recentOperations.clear();
        recentSwaps.clear();
        consecutiveFailures = 0;
        loopDetectionCounter = 0;
        itemOnCursor = false;
    }

    // --- Improved Operation ID and Tracking ---
    private String getOperationId(String action, int slot1, int slot2, ItemStack stack) {
        return action + ":" + slot1 + ":" + slot2 + ":" + (stack.isEmpty() ? "empty" : stack.getItem().hashCode() + ":" + stack.getCount());
    }

    private long getOperationCooldown(String action) {
        switch (action) {
            case "trash":
                return TRASH_COOLDOWN_MS;
            case "armor":
                return ARMOR_COOLDOWN_MS;
            default:
                return BASE_OPERATION_COOLDOWN_MS;
        }
    }

    private boolean isRecentOperation(String operationId, String action) {
        if (recentOperations.containsKey(operationId)) {
            return (System.currentTimeMillis() - recentOperations.get(operationId)) < getOperationCooldown(action);
        }
        return false;
    }

    private void markOperation(String operationId) {
        recentOperations.put(operationId, System.currentTimeMillis());
        if (recentOperations.size() > 50) {
            long currentTime = System.currentTimeMillis();
            recentOperations.entrySet().removeIf(entry ->
                    currentTime - entry.getValue() > BASE_OPERATION_COOLDOWN_MS * 5);
        }
    }

    // --- Swap Pair Tracking for Loop Prevention ---
    private void cleanupSwapPairs() {
        if (recentSwapPairs.size() > 50) {
            recentSwapPairs.clear();
        }
    }

    private boolean hasRecentlySwapped(int slot1, int slot2) {
        String swapPair1 = slot1 + ":" + slot2;
        String swapPair2 = slot2 + ":" + slot1;

        return recentSwapPairs.contains(swapPair1) || recentSwapPairs.contains(swapPair2);
    }

    private void recordSwap(int slot1, int slot2) {
        String swapPair1 = slot1 + ":" + slot2;
        String swapPair2 = slot2 + ":" + slot1;

        recentSwapPairs.add(swapPair1);
        recentSwapPairs.add(swapPair2);

        // Schedule removal after cooldown
        new Thread(() -> {
            try {
                Thread.sleep(SWAP_COOLDOWN_MS);
                recentSwapPairs.remove(swapPair1);
                recentSwapPairs.remove(swapPair2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    // --- NEW: Cursor Handling Methods ---

    /**
     * Checks if there's an item on the cursor
     */
    private boolean isItemOnCursor() {
        if (mc.player == null) return false;
        return !mc.player.currentScreenHandler.getCursorStack().isEmpty();
    }

    /**
     * Attempts to clear the cursor by putting the item back in the original slot or any empty slot
     */
    private boolean clearCursor() {
        if (mc.player == null || mc.interactionManager == null) return false;
        if (!isItemOnCursor()) return false;

        // Try to find an empty slot to put the item
        int emptySlot = -1;
        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
            if (mc.player.getInventory().main.get(i).isEmpty()) {
                emptySlot = i < 9 ? i + 36 : i;
                break;
            }
        }

        // If found an empty slot, place item there
        if (emptySlot != -1) {
            mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    emptySlot,
                    0,
                    SlotActionType.PICKUP,
                    mc.player
            );

            Midnight.LOGGER.info("[InvManager] Cleared cursor to slot " + emptySlot);
            return true;
        }

        // If no empty slot found, try dropping the item
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                -999,
                0,
                SlotActionType.PICKUP,
                mc.player
        );

        Midnight.LOGGER.info("[InvManager] Cleared cursor by dropping item");
        return true;
    }

    // --- Delay and Human-Like Behavior ---
    private float getRealisticDelay() {
        float baseDelay = delay.getValue();
        if (randomDelay.getValue()) {
            Random random = new Random();
            float deviation = (float) (random.nextGaussian() * 0.1 * baseDelay);
            return Math.max(0.05f, baseDelay + deviation); // Ensure minimum delay of 50ms
        } else {
            return Math.max(0.05f, baseDelay); // Ensure minimum delay
        }
    }

    private void microDelay() {
        if (!humanPatterns.getValue()) return; // Skip delay if not using human patterns

        Random random = new Random();
        int delayMillis = random.nextInt(20) + 5; // Reduced from 50+10 for better performance
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean shouldMakeMistake() {
        if (!humanPatterns.getValue()) return false; // No mistakes if not using human patterns

        Random random = new Random();
        return random.nextInt(1000) < 5; // 0.5% chance
    }

    private List<Integer> getRandomizedInventoryIndices() {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            indices.add(i);
        }
        Collections.shuffle(indices);
        return indices;
    }

    // --- Main Update Loop ---
    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null) return;
        if (onlyWhenInventoryOpen.getValue() && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) return;

        // First, check for items on cursor periodically
        long currentTime = System.currentTimeMillis();
        if (currentTime - cursorItemCheckTime > CURSOR_CHECK_INTERVAL) {
            cursorItemCheckTime = currentTime;
            itemOnCursor = isItemOnCursor();

            // If there's an item on cursor and we're using safe cursor handling, prioritize fixing it
            if (itemOnCursor && safeCursorHandling.getValue()) {
                currentState = ActionState.FIXING_CURSOR;
            }
        }

        // Respect delay between actions
        if (currentTime - lastActionTime < getRealisticDelay() * 1000) return;

        // Loop detection
        if (loopDetectionCounter >= LOOP_DETECTION_THRESHOLD) {
            resetState();
            return;
        }

        boolean actionPerformed = false;

        switch (currentState) {
            case FIXING_CURSOR:
                // Handle items stuck on cursor
                if (itemOnCursor) {
                    actionPerformed = clearCursor();
                    if (actionPerformed) {
                        itemOnCursor = false;
                    }
                }
                currentState = ActionState.IDLE;
                break;

            case IDLE:
                if (itemOnCursor && safeCursorHandling.getValue()) {
                    currentState = ActionState.FIXING_CURSOR;
                } else if (cleanInventory.getValue()) {
                    currentState = ActionState.CLEANING_INVENTORY;
                } else if (autoArmor.getValue()) {
                    currentState = ActionState.EQUIPPING_ARMOR;
                } else if (sortInventory.getValue()) {
                    currentState = ActionState.SORTING_INVENTORY;
                }
                break;

            case CLEANING_INVENTORY:
                // First check for cursor items
                if (itemOnCursor && safeCursorHandling.getValue()) {
                    currentState = ActionState.FIXING_CURSOR;
                    break;
                }

                actionPerformed = cleanInventoryItems();
                if (autoArmor.getValue()) {
                    currentState = ActionState.EQUIPPING_ARMOR;
                } else if (sortInventory.getValue()) {
                    currentState = ActionState.SORTING_INVENTORY;
                } else {
                    currentState = ActionState.IDLE;
                }
                break;

            case EQUIPPING_ARMOR:
                // First check for cursor items
                if (itemOnCursor && safeCursorHandling.getValue()) {
                    currentState = ActionState.FIXING_CURSOR;
                    break;
                }

                actionPerformed = equipBestArmor();
                if (sortInventory.getValue()) {
                    currentState = ActionState.SORTING_INVENTORY;
                } else {
                    currentState = ActionState.IDLE;
                }
                break;

            case SORTING_INVENTORY:
                // First check for cursor items
                if (itemOnCursor && safeCursorHandling.getValue()) {
                    currentState = ActionState.FIXING_CURSOR;
                    break;
                }

                actionPerformed = sortItems();
                currentState = ActionState.IDLE;
                break;
        }

        if (actionPerformed) {
            lastActionTime = currentTime;
            consecutiveFailures = 0;
            loopDetectionCounter = 0;
        } else {
            consecutiveFailures++;
            loopDetectionCounter++;
        }

        // Always update cursor state after any action
        itemOnCursor = isItemOnCursor();
    }

    // --- Improved Slot Swap with Cursor Safety ---
    private void performSlotSwap(int sourceSlot, int targetSlot) {
        // Anti-Reversal Check
        String swapId = sourceSlot + ":" + targetSlot;
        String reverseSwapId = targetSlot + ":" + sourceSlot;

        if (recentSwaps.contains(reverseSwapId)) {
            return; // Don't immediately reverse the swap
        }

        // Check for items on cursor first
        if (isItemOnCursor() && safeCursorHandling.getValue()) {
            clearCursor();
            microDelay(); // Small delay after clearing cursor
        }

        // Normal human-like delay
        if (humanPatterns.getValue()) {
            long hoverTime = (long) (delay.getValue() * 250); // Reduced from 500ms for faster operation
            if (randomDelay.getValue()) {
                Random random = new Random();
                float variation = hoverTime * 0.2f;
                hoverTime += (long) (random.nextDouble() * variation * 2 - variation);
            }
            if(hoverTime > 0) {
                try {
                    Thread.sleep(hoverTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Use the safer SwapSlots method that ensures items don't get stuck on cursor
        InventoryManager.swapSlots(sourceSlot, targetSlot);

        // Verify items didn't get stuck
        if (safeCursorHandling.getValue() && isItemOnCursor()) {
            clearCursor();
        }

        // Update Recent Swaps
        recentSwaps.offer(swapId);
        if (recentSwaps.size() > RECENT_SWAPS_MAX_SIZE) {
            recentSwaps.poll(); // Remove the oldest swap
        }
    }

    // --- Improved Item Drop with Cursor Safety ---
    private void performItemDrop(int slotIndex, boolean isStack) {
        // Check for items on cursor first
        if (isItemOnCursor() && safeCursorHandling.getValue()) {
            clearCursor();
            microDelay(); // Small delay after clearing cursor
        }

        // Normal human-like delay
        if (humanPatterns.getValue()) {
            long hoverTime = (long) (delay.getValue() * 250); // Reduced from 500ms
            if (randomDelay.getValue()) {
                Random random = new Random();
                float variation = hoverTime * 0.2f;
                hoverTime += (long) (random.nextDouble() * variation * 2 - variation);
            }
            if(hoverTime > 0) {
                try {
                    Thread.sleep(hoverTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Perform the drop
        if (isStack) {
            InventoryManager.dropItemStack(slotIndex);
        } else {
            InventoryManager.dropItem(slotIndex);
        }

        // Verify items didn't get stuck
        if (safeCursorHandling.getValue() && isItemOnCursor()) {
            clearCursor();
        }
    }

    private boolean equipBestArmor() {
        if (!autoArmor.getValue() || mc.player == null) return false;

        // First check for cursor items
        if (isItemOnCursor() && safeCursorHandling.getValue()) {
            return clearCursor();
        }

        Map<EquipmentSlot, Integer> bestArmorSlots = new HashMap<>();
        Map<EquipmentSlot, Float> bestArmorValues = new HashMap<>();

        // Initialize maps with default values
        bestArmorSlots.put(EquipmentSlot.HEAD, -1);
        bestArmorSlots.put(EquipmentSlot.CHEST, -1);
        bestArmorSlots.put(EquipmentSlot.LEGS, -1);
        bestArmorSlots.put(EquipmentSlot.FEET, -1);
        bestArmorValues.put(EquipmentSlot.HEAD, 0.0f);
        bestArmorValues.put(EquipmentSlot.CHEST, 0.0f);
        bestArmorValues.put(EquipmentSlot.LEGS, 0.0f);
        bestArmorValues.put(EquipmentSlot.FEET, 0.0f);

        for (int i : getRandomizedInventoryIndices()) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            EquipmentSlot equipmentSlot = InventoryManager.getArmorEquipmentSlot(stack);
            if (equipmentSlot == null) continue;

            float armorValue = InventoryManager.calculateArmorValue(stack);
            Float currentBestValue = bestArmorValues.get(equipmentSlot);

            // Safe comparison with null check
            if (currentBestValue != null && armorValue > currentBestValue) {
                bestArmorValues.put(equipmentSlot, armorValue);
                bestArmorSlots.put(equipmentSlot, i);
            }
        }

        for (Map.Entry<EquipmentSlot, Integer> entry : bestArmorSlots.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            int slotIndex = entry.getValue();

            // Skip if no suitable armor was found
            if (slotIndex == -1) continue;

            ItemStack equippedItem = InventoryManager.getEquippedArmorItem(slot);
            ItemStack newItem = mc.player.getInventory().getStack(slotIndex);

            // Safe access to map values
            Float bestValue = bestArmorValues.get(slot);
            if (bestValue == null) continue;

            if (equippedItem.isEmpty() || InventoryManager.calculateArmorValue(equippedItem) < bestValue) {
                int actualSlot = slotIndex < 9 ? slotIndex + 36 : slotIndex;
                int targetSlot = InventoryManager.getArmorSlotIndex(slot);
                if (targetSlot == -1) continue; // Skip if invalid target slot

                // Check for recent swaps to prevent loops
                if (hasRecentlySwapped(actualSlot, targetSlot)) {
                    continue;
                }

                String operationId = getOperationId("armor", actualSlot, targetSlot, newItem);
                if (isRecentOperation(operationId, "armor")) continue;

                markOperation(operationId);
                recordSwap(actualSlot, targetSlot);

                if (shouldMakeMistake()) {
                    performSlotSwap(actualSlot, targetSlot);
                    microDelay();
                    performSlotSwap(targetSlot, actualSlot);
                    return true;
                }

                performSlotSwap(actualSlot, targetSlot);
                return true;
            }
        }
        return false;
    }

    private boolean sortItems() {
        // Clean up swap pairs occasionally to prevent bloat
        cleanupSwapPairs();

        if (!sortInventory.getValue() || mc.player == null) return false;

        // First check for cursor items
        if (isItemOnCursor() && safeCursorHandling.getValue()) {
            return clearCursor();
        }

        Map<ItemType.Category, List<SortableItem>> categorizedItems = new HashMap<>();
        for (ItemType.Category category : ItemType.Category.values()) {
            categorizedItems.put(category, new ArrayList<>());
        }
        for (int i : getRandomizedInventoryIndices()) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            ItemType.Category category = ItemType.categorizeItem(stack);
            categorizedItems.get(category).add(new SortableItem(i, stack, category));
        }

        //Sort
        for (ItemType.Category category : ItemType.Category.values()) {
            List<SortableItem> items = categorizedItems.get(category);
            if (category == ItemType.Category.SWORD) {
                items.sort(Comparator
                        .comparing((SortableItem item) -> ItemType.getMaterialTier(item.stack).getValue())
                        .thenComparing(item -> InventoryManager.calculateEnchantmentValue(item.stack))
                        .reversed());
            } else if (category == ItemType.Category.WEAPON) {
                items.sort(Comparator
                        .comparing((SortableItem item) -> ItemType.getMaterialTier(item.stack).getValue())
                        .thenComparing(item -> InventoryManager.calculateEnchantmentValue(item.stack))
                        .reversed());
            } else if (category == ItemType.Category.TOOL) {
                items.sort(Comparator
                        .comparing((SortableItem item) -> ItemType.getMaterialTier(item.stack).getValue())
                        .reversed());
            } else if (category == ItemType.Category.FOOD) {
                items.sort(Comparator
                        .comparing((SortableItem item) -> InventoryManager.calculateFoodValue(item.stack))
                        .reversed());
            }
        }

        List<SortableItem> swords = categorizedItems.get(ItemType.Category.SWORD);
        if (!swords.isEmpty()) {
            SortableItem bestSword = swords.get(0);
            int sourceSlot = bestSword.slotIndex < 9 ? bestSword.slotIndex + 36 : bestSword.slotIndex;
            int targetSlot = 36;
            if (sourceSlot != targetSlot) {
                // Check for recent swaps to prevent loops
                if (hasRecentlySwapped(sourceSlot, targetSlot)) {
                    // Skip this swap
                } else {
                    String operationId = getOperationId("sword", sourceSlot, targetSlot, bestSword.stack);
                    if (!isRecentOperation(operationId,"sword")) {
                        markOperation(operationId);
                        recordSwap(sourceSlot, targetSlot);

                        if (shouldMakeMistake()) {
                            performSlotSwap(sourceSlot, targetSlot);
                            microDelay();
                            performSlotSwap(targetSlot, sourceSlot);
                            return true;
                        }
                        performSlotSwap(sourceSlot, targetSlot);
                        return true;
                    }
                }
            }
        }

        List<SortableItem> weapons = new ArrayList<>(categorizedItems.get(ItemType.Category.WEAPON));
        for (int i = 0; i < Math.min(2, weapons.size()); i++) {
            int sourceSlot = weapons.get(i).slotIndex;
            int targetSlot = i + 1;
            sourceSlot = sourceSlot < 9 ? sourceSlot + 36 : sourceSlot;
            targetSlot = targetSlot + 36;
            if (sourceSlot != targetSlot) {
                // Check for recent swaps to prevent loops
                if (hasRecentlySwapped(sourceSlot, targetSlot)) {
                    continue; // Skip this swap
                }

                String operationId = getOperationId("weapon", sourceSlot, targetSlot, weapons.get(i).stack);
                if (!isRecentOperation(operationId,"weapon")) {
                    markOperation(operationId);
                    recordSwap(sourceSlot, targetSlot);

                    if (shouldMakeMistake()) {
                        performSlotSwap(sourceSlot, targetSlot);
                        microDelay();
                        performSlotSwap(targetSlot, sourceSlot);
                        return true;
                    }
                    performSlotSwap(sourceSlot, targetSlot);
                    return true;
                }
            }
        }

        List<SortableItem> tools = categorizedItems.get(ItemType.Category.TOOL);
        for (int i = 0; i < Math.min(3, tools.size()); i++) {
            int sourceSlot = tools.get(i).slotIndex;
            int targetSlot = i + 3;
            sourceSlot = sourceSlot < 9 ? sourceSlot + 36 : sourceSlot;
            targetSlot = targetSlot + 36;
            if (sourceSlot != targetSlot) {
                // Check for recent swaps to prevent loops
                if (hasRecentlySwapped(sourceSlot, targetSlot)) {
                    continue; // Skip this swap
                }

                String operationId = getOperationId("tool", sourceSlot, targetSlot, tools.get(i).stack);
                if (!isRecentOperation(operationId,"tool")) {
                    markOperation(operationId);
                    recordSwap(sourceSlot, targetSlot);

                    if (shouldMakeMistake()) {
                        performSlotSwap(sourceSlot, targetSlot);
                        microDelay();
                        performSlotSwap(targetSlot, sourceSlot);
                        return true;
                    }
                    performSlotSwap(sourceSlot, targetSlot);
                    return true;
                }
            }
        }

        List<SortableItem> blocks = categorizedItems.get(ItemType.Category.BLOCK);
        for (int i = 0; i < Math.min(2, blocks.size()); i++) {
            int sourceSlot = blocks.get(i).slotIndex;
            int targetSlot = i + 6;
            sourceSlot = sourceSlot < 9 ? sourceSlot + 36 : sourceSlot;
            targetSlot = targetSlot + 36;
            if (sourceSlot != targetSlot) {
                // Check for recent swaps to prevent loops
                if (hasRecentlySwapped(sourceSlot, targetSlot)) {
                    continue; // Skip this swap
                }

                String operationId = getOperationId("block", sourceSlot, targetSlot, blocks.get(i).stack);
                if (!isRecentOperation(operationId,"block")) {
                    markOperation(operationId);
                    recordSwap(sourceSlot, targetSlot);

                    if (shouldMakeMistake()) {
                        performSlotSwap(sourceSlot, targetSlot);
                        microDelay();
                        performSlotSwap(targetSlot, sourceSlot);
                        return true;
                    }
                    performSlotSwap(sourceSlot, targetSlot);
                    return true;
                }
            }
        }

        List<SortableItem> foods = categorizedItems.get(ItemType.Category.FOOD);
        if (!foods.isEmpty()) {
            int sourceSlot = foods.get(0).slotIndex;
            int targetSlot = 8;
            sourceSlot = sourceSlot < 9 ? sourceSlot + 36 : sourceSlot;
            targetSlot = targetSlot + 36;
            if (sourceSlot != targetSlot) {
                // Check for recent swaps to prevent loops
                if (hasRecentlySwapped(sourceSlot, targetSlot)) {
                    // Skip this swap
                } else {
                    String operationId = getOperationId("food", sourceSlot, targetSlot, foods.get(0).stack);
                    if (!isRecentOperation(operationId,"food")) {
                        markOperation(operationId);
                        recordSwap(sourceSlot, targetSlot);

                        if (shouldMakeMistake()) {
                            performSlotSwap(sourceSlot, targetSlot);
                            microDelay();
                            performSlotSwap(targetSlot, sourceSlot);
                            return true;
                        }
                        performSlotSwap(sourceSlot, targetSlot);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean cleanInventoryItems() {
        if (!cleanInventory.getValue() || mc.player == null) return false;

        // First check for cursor items
        if (isItemOnCursor() && safeCursorHandling.getValue()) {
            return clearCursor();
        }

        for (int i : getRandomizedInventoryIndices()) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

if (ItemType.isTrashItem(stack.getItem())) {


int slotIndex = i < 9 ? i + 36 : i;
String operationId = getOperationId("trash", slotIndex, 0, stack);
                if (isRecentOperation(operationId, "trash")) continue;
markOperation(operationId);
                if (shouldMakeMistake()) {
microDelay();
                    if(new Random().nextBoolean()) continue; //50% chance to just do nothing.
microDelay();
                    return true;
                            }
performItemDrop(slotIndex, stack.getCount() > 1);
        return true;
        }
        }

// --- Find Best Items ---
Map<String, ItemStack> bestTools = new HashMap<>();
Map<String, ItemStack> bestWeapons = new HashMap<>();
Map<EquipmentSlot, ItemStack> bestArmor = new HashMap<>();
        bestArmor.put(EquipmentSlot.HEAD, null);
        bestArmor.put(EquipmentSlot.CHEST, null);
        bestArmor.put(EquipmentSlot.LEGS, null);
        bestArmor.put(EquipmentSlot.FEET, null);
        for (int i : getRandomizedInventoryIndices()) {
ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
ItemType.Category category = ItemType.categorizeItem(stack);
            if (ItemType.isTrashItem(stack.getItem())) continue;

        if (category == ItemType.Category.TOOL) {
String toolType = ItemType.getToolType(stack).name();
ItemStack currentBest = bestTools.get(toolType);
                if (currentBest == null || InventoryManager.compareItems(stack, currentBest) > 0) {
        bestTools.put(toolType, stack);
                }
                        } else if (category == ItemType.Category.WEAPON || category == ItemType.Category.SWORD) {
String weaponType = ItemType.getWeaponType(stack).name();
ItemStack currentBest = bestWeapons.get(weaponType);
                if (currentBest == null || InventoryManager.compareItems(stack, currentBest) > 0) {
        bestWeapons.put(weaponType, stack);
                }
                        } else if (category == ItemType.Category.ARMOR) {
EquipmentSlot slot = InventoryManager.getArmorEquipmentSlot(stack);
                if (slot != null) {
ItemStack currentBest = bestArmor.get(slot);
                    if (currentBest == null || InventoryManager.calculateArmorValue(stack) > InventoryManager.calculateArmorValue(currentBest)) {
        bestArmor.put(slot, stack);
                    }
                            }
                            }
                            }

                            //Add equipped armor to comparison
                            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
ItemStack equippedItem = InventoryManager.getEquippedArmorItem(slot);
            if (!equippedItem.isEmpty()) {
        if (bestArmor.get(slot) == null || InventoryManager.calculateArmorValue(equippedItem) > InventoryManager.calculateArmorValue(bestArmor.get(slot))) {
        bestArmor.put(slot, equippedItem);
                }
                        }
                        }

                        // --- Drop Duplicates/Inferior Items ---
                        for (int i : getRandomizedInventoryIndices()) {
ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
        if (ItemType.isTrashItem(stack.getItem())) continue;

ItemType.Category category = ItemType.categorizeItem(stack);
int slotIndex = i < 9 ? i + 36 : i;

            if (category == ItemType.Category.TOOL) {
String toolType = ItemType.getToolType(stack).name();
ItemStack bestTool = bestTools.get(toolType);
                if (bestTool != null && !ItemStack.areEqual(stack, bestTool) && InventoryManager.compareItems(stack, bestTool) < 0) {
String operationId = getOperationId("dupTool", slotIndex, 0, stack);
                    if (isRecentOperation(operationId,"dupTool")) continue;
markOperation(operationId);
                    if (shouldMakeMistake()) {
microDelay();
                        if(new Random().nextBoolean()) continue;
microDelay();
                        return true;
                                }
performItemDrop(slotIndex, stack.getCount() > 1);
        return true;
        }
        } else if (category == ItemType.Category.WEAPON || category == ItemType.Category.SWORD) {
String weaponType = ItemType.getWeaponType(stack).name();
ItemStack bestWeapon = bestWeapons.get(weaponType);
                if (bestWeapon != null && !ItemStack.areEqual(stack, bestWeapon) && InventoryManager.compareItems(stack, bestWeapon) < 0) {
String operationId = getOperationId("dupWeapon", slotIndex, 0, stack);
                    if (isRecentOperation(operationId,"dupWeapon")) continue;
markOperation(operationId);
                    if (shouldMakeMistake()) {
microDelay();
                        if(new Random().nextBoolean()) continue;
microDelay();
                        return true;
                                }
performItemDrop(slotIndex, stack.getCount() > 1);
        return true;
        }
        } else if (category == ItemType.Category.ARMOR) {
EquipmentSlot slot = InventoryManager.getArmorEquipmentSlot(stack);
                if (slot != null) {
ItemStack bestArmorPiece = bestArmor.get(slot);
                    if (bestArmorPiece != null && !ItemStack.areEqual(stack, bestArmorPiece) && InventoryManager.calculateArmorValue(stack) < InventoryManager.calculateArmorValue(bestArmorPiece))
        {
String operationId = getOperationId("dupArmor", slotIndex, 0, stack);
                        if (isRecentOperation(operationId,"dupArmor")) continue;
markOperation(operationId);
                        if (shouldMakeMistake()) {
microDelay();
                            if(new Random().nextBoolean()) continue;
microDelay();
                            return true;
                                    }
performItemDrop(slotIndex, stack.getCount() > 1);
        return true;
        }
        }
        }
        }
        return false;
        }

private class SortableItem {
    public final int slotIndex;
    public final ItemStack stack;
    public final ItemType.Category category;
    public SortableItem(int slotIndex, ItemStack stack, ItemType.Category category) {
        this.slotIndex = slotIndex;
        this.stack = stack;
        this.category = category;
    }
}

public void addTrashItem(Item item) {
    ItemType.addTrashItem(item);
}

public void removeTrashItem(Item item) {
    ItemType.removeTrashItem(item);
}

public void clearTrashItems() {
    ItemType.clearTrashItems();
}
}