package de.peter1337.midnight.modules.player;

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

import java.util.*;

public class InvManager extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // Simplified tracking to prevent loops
    private final Map<String, Long> recentOperations = new HashMap<>();
    private static final long OPERATION_COOLDOWN_MS = 1500; // 1.5 seconds cooldown

    // Mode settings
    private final Setting<Boolean> onlyWhenInventoryOpen = register(
            new Setting<>("OnlyWhenInventoryOpen", Boolean.FALSE, "Only work when inventory is open")
    );

    // Delay settings
    private final Setting<Float> delay = register(
            new Setting<>("Delay", 0.3f, 0.0f, 2.0f, "Delay between actions in seconds")
    );

    private final Setting<Boolean> randomDelay = register(
            new Setting<>("RandomDelay", Boolean.TRUE, "Add slight variation to delay")
    );

    // Auto armor settings
    private final Setting<Boolean> autoArmor = register(
            new Setting<>("AutoArmor", Boolean.TRUE, "Automatically equip best armor")
    );

    // Sort inventory settings
    private final Setting<Boolean> sortInventory = register(
            new Setting<>("SortInventory", Boolean.TRUE, "Sort inventory items (weapons, tools, food, blocks)")
    );

    // Combined trash and duplicates setting
    private final Setting<Boolean> cleanInventory = register(
            new Setting<>("CleanInventory", Boolean.TRUE, "Drop trash items and inferior duplicates")
    );

    // Human patterns setting
    private final Setting<Boolean> humanPatterns = register(
            new Setting<>("HumanPatterns", Boolean.TRUE, "Mimic human-like interaction patterns")
    );

    // Progress tracking
    private long lastActionTime = 0;
    private ActionState currentState = ActionState.IDLE;

    // Fail counter to prevent getting stuck
    private int consecutiveFailures = 0;
    private static final int MAX_FAILURES = 5;

    // Action states (simplified by removing dropping duplicates as separate state)
    private enum ActionState {
        IDLE,
        EQUIPPING_ARMOR,
        SORTING_INVENTORY,
        CLEANING_INVENTORY
    }

    public InvManager() {
        super("InvManager", "Automatically manages your inventory", Category.PLAYER, "y");
    }

    @Override
    public void onEnable() {
        currentState = ActionState.IDLE;
        lastActionTime = 0;
        recentOperations.clear();
        consecutiveFailures = 0;
    }

    @Override
    public void onDisable() {
        currentState = ActionState.IDLE;
        recentOperations.clear();
    }

    /**
     * Create a unique identifier for an operation to prevent loops
     */
    private String getOperationId(String action, int slot1, int slot2) {
        return action + ":" + slot1 + ":" + slot2;
    }

    /**
     * Check if an operation was performed recently
     */
    private boolean isRecentOperation(String operationId) {
        if (recentOperations.containsKey(operationId)) {
            return (System.currentTimeMillis() - recentOperations.get(operationId)) < OPERATION_COOLDOWN_MS;
        }
        return false;
    }

    /**
     * Mark an operation as performed
     */
    private void markOperation(String operationId) {
        recentOperations.put(operationId, System.currentTimeMillis());

        // Clean up old operations occasionally
        if (recentOperations.size() > 20) {
            long currentTime = System.currentTimeMillis();
            recentOperations.entrySet().removeIf(entry ->
                    currentTime - entry.getValue() > OPERATION_COOLDOWN_MS);
        }
    }

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null) return;

        // Check if inventory is open if the setting requires it
        if (onlyWhenInventoryOpen.getValue() && !(mc.currentScreen instanceof InventoryScreen)) {
            return;
        }

        // Don't run if player is in a container/chest
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) return;

        // Calculate delay
        float actualDelay;
        if (randomDelay.getValue()) {
            float variation = delay.getValue() * 0.5f;
            actualDelay = delay.getValue() + (float)(Math.random() * variation * 2 - variation);
        } else {
            actualDelay = delay.getValue();
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastActionTime < actualDelay * 1000) {
            return;
        }

        // Handle too many consecutive failures
        if (consecutiveFailures >= MAX_FAILURES) {
            // Reset state
            currentState = ActionState.IDLE;
            consecutiveFailures = 0;
            recentOperations.clear();
            return;
        }

        // Rotate between different tasks
        boolean actionPerformed = false;

        switch (currentState) {
            case IDLE:
                if (autoArmor.getValue()) {
                    currentState = ActionState.EQUIPPING_ARMOR;
                } else if (sortInventory.getValue()) {
                    currentState = ActionState.SORTING_INVENTORY;
                } else if (cleanInventory.getValue()) {
                    currentState = ActionState.CLEANING_INVENTORY;
                }
                break;

            case EQUIPPING_ARMOR:
                actionPerformed = equipBestArmor();
                moveToNextState();
                break;

            case SORTING_INVENTORY:
                actionPerformed = sortItems();
                moveToNextState();
                break;

            case CLEANING_INVENTORY:
                actionPerformed = cleanInventoryItems();
                currentState = ActionState.IDLE;
                break;
        }

        if (actionPerformed) {
            lastActionTime = currentTime;
            consecutiveFailures = 0;
        } else {
            consecutiveFailures++;
        }
    }

    /**
     * Move to the next state based on enabled settings
     */
    private void moveToNextState() {
        if (sortInventory.getValue() && currentState == ActionState.EQUIPPING_ARMOR) {
            currentState = ActionState.SORTING_INVENTORY;
        } else if (cleanInventory.getValue() &&
                (currentState == ActionState.EQUIPPING_ARMOR || currentState == ActionState.SORTING_INVENTORY)) {
            currentState = ActionState.CLEANING_INVENTORY;
        } else {
            currentState = ActionState.IDLE;
        }
    }

    /**
     * Perform a slot swap with human pattern if enabled
     */
    private void performSlotSwap(int sourceSlot, int targetSlot) {
        if (humanPatterns.getValue()) {
            // Apply hover delay based on half the delay value
            long hoverTime = (long)(delay.getValue() * 500); // Half of delay in milliseconds

            // Add some slight randomization to the hover time (±20%)
            if (randomDelay.getValue()) {
                float variation = hoverTime * 0.2f;
                hoverTime += (long)(Math.random() * variation * 2 - variation);
            }

            if (hoverTime > 0) {
                try {
                    Thread.sleep(hoverTime);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        }

        // Perform the actual swap
        InventoryManager.swapSlots(sourceSlot, targetSlot);
    }

    /**
     * Drops an item with human pattern if enabled
     */
    private void performItemDrop(int slotIndex, boolean isStack) {
        if (humanPatterns.getValue()) {
            // Apply hover delay based on half the delay value
            long hoverTime = (long)(delay.getValue() * 500); // Half of delay in milliseconds

            // Add some slight randomization to the hover time (±20%)
            if (randomDelay.getValue()) {
                float variation = hoverTime * 0.2f;
                hoverTime += (long)(Math.random() * variation * 2 - variation);
            }

            if (hoverTime > 0) {
                try {
                    Thread.sleep(hoverTime);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        }

        // Perform the drop
        if (isStack) {
            InventoryManager.dropItemStack(slotIndex);
        } else {
            InventoryManager.dropItem(slotIndex);
        }
    }

    /**
     * Equips the best armor pieces found in inventory
     * @return true if an action was performed
     */
    private boolean equipBestArmor() {
        if (!autoArmor.getValue() || mc.player == null) return false;

        // Check each armor slot
        Map<EquipmentSlot, Integer> bestArmorSlots = new HashMap<>();
        Map<EquipmentSlot, Float> bestArmorValues = new HashMap<>();

        // Initialize with default values
        bestArmorSlots.put(EquipmentSlot.HEAD, -1);
        bestArmorSlots.put(EquipmentSlot.CHEST, -1);
        bestArmorSlots.put(EquipmentSlot.LEGS, -1);
        bestArmorSlots.put(EquipmentSlot.FEET, -1);

        bestArmorValues.put(EquipmentSlot.HEAD, 0.0f);
        bestArmorValues.put(EquipmentSlot.CHEST, 0.0f);
        bestArmorValues.put(EquipmentSlot.LEGS, 0.0f);
        bestArmorValues.put(EquipmentSlot.FEET, 0.0f);

        // Search inventory for armor
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            EquipmentSlot equipmentSlot = InventoryManager.getArmorEquipmentSlot(stack);
            if (equipmentSlot == null) continue;

            float armorValue = InventoryManager.calculateArmorValue(stack);

            if (armorValue > bestArmorValues.get(equipmentSlot)) {
                bestArmorValues.put(equipmentSlot, armorValue);
                bestArmorSlots.put(equipmentSlot, i);
            }
        }

        // Equip best armor if found
        for (Map.Entry<EquipmentSlot, Integer> entry : bestArmorSlots.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            int slotIndex = entry.getValue();

            if (slotIndex == -1) continue;

            // Get currently equipped item
            ItemStack equippedItem = InventoryManager.getEquippedArmorItem(slot);
            ItemStack newItem = mc.player.getInventory().getStack(slotIndex);

            // If no item is equipped or found item is better
            if (equippedItem.isEmpty() || InventoryManager.calculateArmorValue(equippedItem) < bestArmorValues.get(slot)) {
                // Find the actual inventory slot index
                int actualSlot = slotIndex < 9 ? slotIndex + 36 : slotIndex;
                // Get the target slot (where armor goes in the armor slots)
                int targetSlot = InventoryManager.getArmorSlotIndex(slot);

                // Check if we recently did this operation
                String operationId = getOperationId("armor", actualSlot, targetSlot);
                if (isRecentOperation(operationId)) continue;

                // Mark this operation
                markOperation(operationId);

                // Perform the swap with human patterns
                performSlotSwap(actualSlot, targetSlot);
                return true;
            }
        }

        return false;
    }

    /**
     * Sorts inventory items according to preferences
     * @return true if an action was performed
     */
    private boolean sortItems() {
        if (!sortInventory.getValue() || mc.player == null) return false;

        // Scan inventory and categorize items
        Map<ItemType.Category, List<SortableItem>> categorizedItems = new HashMap<>();

        // Initialize categories
        for (ItemType.Category category : ItemType.Category.values()) {
            categorizedItems.put(category, new ArrayList<>());
        }

        // Collect and categorize inventory items
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            ItemType.Category category = ItemType.categorizeItem(stack);
            SortableItem sortableItem = new SortableItem(i, stack, category);
            categorizedItems.get(category).add(sortableItem);
        }

        // Sort each category
        for (ItemType.Category category : ItemType.Category.values()) {
            List<SortableItem> items = categorizedItems.get(category);

            if (category == ItemType.Category.SWORD) {
                // Sort swords by tier and enchantments, best first
                items.sort(Comparator
                        .comparing((SortableItem item) -> ItemType.getMaterialTier(item.stack).getValue())
                        .thenComparing(item -> InventoryManager.calculateEnchantmentValue(item.stack))
                        .reversed());
            } else if (category == ItemType.Category.WEAPON) {
                // Sort other weapons
                items.sort(Comparator
                        .comparing((SortableItem item) -> ItemType.getMaterialTier(item.stack).getValue())
                        .thenComparing(item -> InventoryManager.calculateEnchantmentValue(item.stack))
                        .reversed());
            } else if (category == ItemType.Category.TOOL) {
                // Sort tools by tier
                items.sort(Comparator
                        .comparing((SortableItem item) -> ItemType.getMaterialTier(item.stack).getValue())
                        .reversed());
            } else if (category == ItemType.Category.FOOD) {
                // Sort food by saturation/hunger value
                items.sort(Comparator
                        .comparing((SortableItem item) -> InventoryManager.calculateFoodValue(item.stack))
                        .reversed());
            }
        }

        // Place best sword in slot 0
        List<SortableItem> swords = categorizedItems.get(ItemType.Category.SWORD);
        if (!swords.isEmpty()) {
            SortableItem bestSword = swords.get(0);
            int sourceSlot = bestSword.slotIndex < 9 ? bestSword.slotIndex + 36 : bestSword.slotIndex;
            int targetSlot = 36; // Hotbar slot 0

            // Skip if already in the right slot
            if (sourceSlot != targetSlot) {
                // Check if we recently did this operation
                String operationId = getOperationId("sword", sourceSlot, targetSlot);
                if (!isRecentOperation(operationId)) {
                    markOperation(operationId);
                    performSlotSwap(sourceSlot, targetSlot);
                    return true;
                }
            }
        }

        // Sort rest of weapons to hotbar (1-2)
        List<SortableItem> weapons = new ArrayList<>(categorizedItems.get(ItemType.Category.WEAPON));
        for (int i = 0; i < Math.min(2, weapons.size()); i++) {
            int sourceSlot = weapons.get(i).slotIndex;
            int targetSlot = i + 1;

            // Convert to actual slot indices
            sourceSlot = sourceSlot < 9 ? sourceSlot + 36 : sourceSlot;
            targetSlot = targetSlot + 36;

            // Skip if already in the right slot
            if (sourceSlot != targetSlot) {
                // Check if we recently did this operation
                String operationId = getOperationId("weapon", sourceSlot, targetSlot);
                if (!isRecentOperation(operationId)) {
                    markOperation(operationId);
                    performSlotSwap(sourceSlot, targetSlot);
                    return true;
                }
            }
        }

        // Sort tools to hotbar (3-5)
        List<SortableItem> tools = categorizedItems.get(ItemType.Category.TOOL);
        for (int i = 0; i < Math.min(3, tools.size()); i++) {
            int sourceSlot = tools.get(i).slotIndex;
            int targetSlot = i + 3;

            // Convert to actual slot indices
            sourceSlot = sourceSlot < 9 ? sourceSlot + 36 : sourceSlot;
            targetSlot = targetSlot + 36;

            // Skip if already in the right slot
            if (sourceSlot != targetSlot) {
                // Check if we recently did this operation
                String operationId = getOperationId("tool", sourceSlot, targetSlot);
                if (!isRecentOperation(operationId)) {
                    markOperation(operationId);
                    performSlotSwap(sourceSlot, targetSlot);
                    return true;
                }
            }
        }

        // Sort blocks to hotbar (6-7)
        List<SortableItem> blocks = categorizedItems.get(ItemType.Category.BLOCK);
        for (int i = 0; i < Math.min(2, blocks.size()); i++) {
            int sourceSlot = blocks.get(i).slotIndex;
            int targetSlot = i + 6;

            // Convert to actual slot indices
            sourceSlot = sourceSlot < 9 ? sourceSlot + 36 : sourceSlot;
            targetSlot = targetSlot + 36;

            // Skip if already in the right slot
            if (sourceSlot != targetSlot) {
                // Check if we recently did this operation
                String operationId = getOperationId("block", sourceSlot, targetSlot);
                if (!isRecentOperation(operationId)) {
                    markOperation(operationId);
                    performSlotSwap(sourceSlot, targetSlot);
                    return true;
                }
            }
        }

        // Sort food to hotbar (8)
        List<SortableItem> foods = categorizedItems.get(ItemType.Category.FOOD);
        if (!foods.isEmpty()) {
            int sourceSlot = foods.get(0).slotIndex;
            int targetSlot = 8;

            // Convert to actual slot indices
            sourceSlot = sourceSlot < 9 ? sourceSlot + 36 : sourceSlot;
            targetSlot = targetSlot + 36;

            // Skip if already in the right slot
            if (sourceSlot != targetSlot) {
                // Check if we recently did this operation
                String operationId = getOperationId("food", sourceSlot, targetSlot);
                if (!isRecentOperation(operationId)) {
                    markOperation(operationId);
                    performSlotSwap(sourceSlot, targetSlot);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Cleans inventory by dropping trash items and duplicates
     * @return true if an action was performed
     */
    private boolean cleanInventoryItems() {
        if (!cleanInventory.getValue() || mc.player == null) return false;

        // Calculate precise delay
        float actualDelay;
        if (randomDelay.getValue()) {
            float variation = delay.getValue() * 0.5f;
            actualDelay = delay.getValue() + (float)(Math.random() * variation * 2 - variation);
        } else {
            actualDelay = delay.getValue();
        }

        // First, immediately drop any trash items
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            // Prioritize dropping trash items immediately
            if (ItemType.isTrashItem(stack.getItem())) {
                int slotIndex = i < 9 ? i + 36 : i;

                // Check if we recently tried to drop this item
                String operationId = getOperationId("trash", slotIndex, 0);
                if (isRecentOperation(operationId)) continue;

                // Mark operation
                markOperation(operationId);

                // Drop with human patterns
                performItemDrop(slotIndex, stack.getCount() > 1);
                return true;
            }
        }

        // Maps to track the best items by category and type
        Map<String, ItemStack> bestTools = new HashMap<>();
        Map<String, ItemStack> bestWeapons = new HashMap<>();
        Map<EquipmentSlot, ItemStack> bestArmor = new HashMap<>();

        // Initialize armor slots
        bestArmor.put(EquipmentSlot.HEAD, null);
        bestArmor.put(EquipmentSlot.CHEST, null);
        bestArmor.put(EquipmentSlot.LEGS, null);
        bestArmor.put(EquipmentSlot.FEET, null);

        // First pass: find best items
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            ItemType.Category category = ItemType.categorizeItem(stack);

            // Skip trash items as they're already handled
            if (ItemType.isTrashItem(stack.getItem())) continue;

            // Find best tools
            if (category == ItemType.Category.TOOL) {
                String toolType = ItemType.getToolType(stack).name();
                ItemStack currentBest = bestTools.get(toolType);

                if (currentBest == null || InventoryManager.compareItems(stack, currentBest) > 0) {
                    bestTools.put(toolType, stack);
                }
            }
            // Find best weapons
            else if (category == ItemType.Category.WEAPON || category == ItemType.Category.SWORD) {
                String weaponType = ItemType.getWeaponType(stack).name();
                ItemStack currentBest = bestWeapons.get(weaponType);

                if (currentBest == null || InventoryManager.compareItems(stack, currentBest) > 0) {
                    bestWeapons.put(weaponType, stack);
                }
            }
            // Find best armor
            else if (category == ItemType.Category.ARMOR) {
                EquipmentSlot slot = InventoryManager.getArmorEquipmentSlot(stack);
                if (slot != null) {
                    ItemStack currentBest = bestArmor.get(slot);

                    if (currentBest == null || InventoryManager.calculateArmorValue(stack) >
                            InventoryManager.calculateArmorValue(currentBest)) {
                        bestArmor.put(slot, stack);
                    }
                }
            }
        }

        // Add currently equipped armor to the best armor map
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack equippedItem = InventoryManager.getEquippedArmorItem(slot);
            if (!equippedItem.isEmpty()) {
                ItemStack currentBest = bestArmor.get(slot);

                if (currentBest == null || InventoryManager.calculateArmorValue(equippedItem) >
                        InventoryManager.calculateArmorValue(currentBest)) {
                    bestArmor.put(slot, equippedItem);
                }
            }
        }

        // Second pass: drop duplicate/inferior items
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            // Skip trash items as they're already handled
            if (ItemType.isTrashItem(stack.getItem())) continue;

            ItemType.Category category = ItemType.categorizeItem(stack);
            int slotIndex = i < 9 ? i + 36 : i;

            if (category == ItemType.Category.TOOL) {
                String toolType = ItemType.getToolType(stack).name();
                ItemStack bestTool = bestTools.get(toolType);

                // If this isn't the best tool of its type
                if (InventoryManager.compareItems(stack, bestTool) < 0) {
                    // Check if we recently tried to drop this item
                    String operationId = getOperationId("dupTool", slotIndex, 0);
                    if (isRecentOperation(operationId)) continue;

                    // Mark operation
                    markOperation(operationId);

                    // Drop with human patterns
                    performItemDrop(slotIndex, stack.getCount() > 1);
                    return true;
                }
            }
            else if (category == ItemType.Category.WEAPON || category == ItemType.Category.SWORD) {
                String weaponType = ItemType.getWeaponType(stack).name();
                ItemStack bestWeapon = bestWeapons.get(weaponType);

                // If this is not the best weapon of its type
                if (InventoryManager.compareItems(stack, bestWeapon) < 0) {
                    // Check if we recently tried to drop this item
                    String operationId = getOperationId("dupWeapon", slotIndex, 0);
                    if (isRecentOperation(operationId)) continue;

                    // Mark operation
                    markOperation(operationId);

                    // Drop with human patterns
                    performItemDrop(slotIndex, stack.getCount() > 1);
                    return true;
                }
            }
            else if (category == ItemType.Category.ARMOR) {
                EquipmentSlot slot = InventoryManager.getArmorEquipmentSlot(stack);
                if (slot != null) {
                    ItemStack bestArmorPiece = bestArmor.get(slot);

                    // If this isn't the best armor piece for this slot
                    if (bestArmorPiece != null && !ItemStack.areEqual(stack, bestArmorPiece) &&
                            InventoryManager.calculateArmorValue(stack) <
                                    InventoryManager.calculateArmorValue(bestArmorPiece)) {

                        // Check if we recently tried to drop this item
                        String operationId = getOperationId("dupArmor", slotIndex, 0);
                        if (isRecentOperation(operationId)) continue;

                        // Mark operation
                        markOperation(operationId);

                        // Drop with human patterns
                        performItemDrop(slotIndex, stack.getCount() > 1);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Helper class for sortable items with metadata
     */
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

    /**
     * Adds an item to the trash list
     */
    public void addTrashItem(Item item) {
        ItemType.addTrashItem(item);
    }

    /**
     * Removes an item from the trash list
     */
    public void removeTrashItem(Item item) {
        ItemType.removeTrashItem(item);
    }

    /**
     * Clears the trash list
     */
    public void clearTrashItems() {
        ItemType.clearTrashItems();
    }
}