package de.peter1337.midnight.modules.player;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.*;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.Registries;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

import java.util.*;

public class InvManager extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public InvManager() {
        super("InvManager", "Automatically manages your inventory", Category.PLAYER, "y");

        // Initialize default trash items
        trashItems.add(Items.ROTTEN_FLESH);
        trashItems.add(Items.POISONOUS_POTATO);
        trashItems.add(Items.STRING);
        trashItems.add(Items.SPIDER_EYE);
        trashItems.add(Items.STICK);
        trashItems.add(Items.BONE);
        trashItems.add(Items.GUNPOWDER);
    }

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

    // Drop duplicates/lower tier settings
    private final Setting<Boolean> dropDuplicates = register(
            new Setting<>("DropDuplicates", Boolean.TRUE, "Drop duplicate and lower tier items")
    );

    // Trash items settings
    private final Setting<Boolean> dropTrash = register(
            new Setting<>("DropTrash", Boolean.TRUE, "Drop unwanted items")
    );

    // Additional setting for keeping extra tools
    private final Setting<Integer> keepToolCount = register(
            new Setting<>("KeepToolCount", 1, 1, 5, "Number of each tool type to keep")
    );

    // Items that are considered trash and will be dropped if dropTrash is enabled
    private final List<Item> trashItems = new ArrayList<>();

    // Progress tracking
    private long lastActionTime = 0;
    private ActionState currentState = ActionState.IDLE;

    // Item categories for sorting
    private enum ItemCategory {
        WEAPON,
        SWORD, // Specific for swords
        TOOL,
        ARMOR,
        BLOCK,
        FOOD,
        TRASH,
        OTHER
    }

    // Material tiers for equipment
    private enum MaterialTier {
        NETHERITE(6),
        DIAMOND(5),
        IRON(4),
        GOLDEN(3),
        STONE(2),
        WOODEN(1),
        LEATHER(0),
        OTHER(0);

        private final int value;

        MaterialTier(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // Action states
    private enum ActionState {
        IDLE,
        EQUIPPING_ARMOR,
        SORTING_INVENTORY,
        DROPPING_TRASH,
        DROPPING_DUPLICATES
    }


    @Override
    public void onEnable() {
        currentState = ActionState.IDLE;
        lastActionTime = 0;
    }

    @Override
    public void onDisable() {
        currentState = ActionState.IDLE;
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
            float variation = delay.getValue() * 1.8f;
            actualDelay = delay.getValue() + (float)(Math.random() * variation * 2 - variation);
        } else {
            actualDelay = delay.getValue();
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastActionTime < actualDelay * 1000) {
            return;
        }

        // Rotate between different tasks
        switch (currentState) {
            case IDLE:
                if (autoArmor.getValue()) {
                    currentState = ActionState.EQUIPPING_ARMOR;
                } else if (sortInventory.getValue()) {
                    currentState = ActionState.SORTING_INVENTORY;
                } else if (dropTrash.getValue()) {
                    currentState = ActionState.DROPPING_TRASH;
                } else if (dropDuplicates.getValue()) {
                    currentState = ActionState.DROPPING_DUPLICATES;
                }
                break;

            case EQUIPPING_ARMOR:
                if (equipBestArmor()) {
                    lastActionTime = currentTime;
                }
                if (sortInventory.getValue()) {
                    currentState = ActionState.SORTING_INVENTORY;
                } else if (dropTrash.getValue()) {
                    currentState = ActionState.DROPPING_TRASH;
                } else if (dropDuplicates.getValue()) {
                    currentState = ActionState.DROPPING_DUPLICATES;
                } else {
                    currentState = ActionState.IDLE;
                }
                break;

            case SORTING_INVENTORY:
                if (sortItems()) {
                    lastActionTime = currentTime;
                }
                if (dropTrash.getValue()) {
                    currentState = ActionState.DROPPING_TRASH;
                } else if (dropDuplicates.getValue()) {
                    currentState = ActionState.DROPPING_DUPLICATES;
                } else {
                    currentState = ActionState.IDLE;
                }
                break;

            case DROPPING_TRASH:
                if (dropTrashItems()) {
                    lastActionTime = currentTime;
                }
                if (dropDuplicates.getValue()) {
                    currentState = ActionState.DROPPING_DUPLICATES;
                } else {
                    currentState = ActionState.IDLE;
                }
                break;

            case DROPPING_DUPLICATES:
                if (dropDuplicateItems()) {
                    lastActionTime = currentTime;
                }
                currentState = ActionState.IDLE;
                break;
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

            // In 1.21.4, we need to check for equipment type rather than directly instanceof ArmorItem
            EquipmentSlot equipmentSlot = getArmorEquipmentSlot(stack);
            if (equipmentSlot == null) continue;

            float armorValue = calculateArmorValue(stack);

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
            ItemStack equippedItem = getEquippedArmorItem(slot);

            // If no item is equipped or found item is better
            if (equippedItem.isEmpty() || calculateArmorValue(equippedItem) < bestArmorValues.get(slot)) {
                // Find the actual inventory slot index
                int actualSlot = slotIndex < 9 ? slotIndex + 36 : slotIndex;

                // Get the target slot (where armor goes in the armor slots)
                int targetSlot = getArmorSlotIndex(slot);

                // Perform the swap
                swapSlots(actualSlot, targetSlot);
                return true;
            }
        }

        return false;
    }

    /**
     * Determines which equipment slot an item belongs to
     */
    private EquipmentSlot getArmorEquipmentSlot(ItemStack stack) {
        // Check if the stack has an equippable component
        if (stack.contains(DataComponentTypes.EQUIPPABLE)) {
            // Get the equipment slot from the equippable component
            return stack.getComponents().get(DataComponentTypes.EQUIPPABLE).slot();
        }
        return null;
    }

    /**
     * Gets the currently equipped armor item in the specified slot
     */
    private ItemStack getEquippedArmorItem(EquipmentSlot slot) {
        switch (slot) {
            case HEAD:
                return mc.player.getInventory().getArmorStack(3);
            case CHEST:
                return mc.player.getInventory().getArmorStack(2);
            case LEGS:
                return mc.player.getInventory().getArmorStack(1);
            case FEET:
                return mc.player.getInventory().getArmorStack(0);
            default:
                return ItemStack.EMPTY;
        }
    }

    /**
     * Gets the actual slot index for a given armor equipment slot
     */
    private int getArmorSlotIndex(EquipmentSlot slot) {
        switch (slot) {
            case HEAD:
                return 5;  // Helmet slot
            case CHEST:
                return 6;  // Chestplate slot
            case LEGS:
                return 7;  // Leggings slot
            case FEET:
                return 8;  // Boots slot
            default:
                return -1;
        }
    }

    /**
     * Calculates the value of an armor piece considering its material, enchantments, etc.
     */
    private float calculateArmorValue(ItemStack stack) {
        if (stack.isEmpty()) return 0.0f;

        // Start with the base material tier value (this will prioritize diamond over iron, etc.)
        float value = getMaterialTier(stack).getValue() * 100.0f;

        // In 1.21.4, armor values are in the attribute modifiers component
        if (stack.contains(DataComponentTypes.ATTRIBUTE_MODIFIERS)) {
            // Add a base value for having attribute modifiers
            value += 20.0f;
        }

        // Add value for enchantments
        ItemEnchantmentsComponent enchantments = stack.getEnchantments();
        if (enchantments != null && !enchantments.isEmpty()) {
            // Each enchantment adds value based on level
            value += enchantments.getSize() * 10.0f;

            // Extra value for protection enchantments
            if (hasProtectionEnchantment(stack)) {
                value += 15.0f;
            }
        }

        return value;
    }

    /**
     * Check if an item stack has any protection enchantment
     */
    /**
     * Simplified version that doesn't require registry access
     */
    private boolean hasProtectionEnchantment(ItemStack stack) {
        ItemEnchantmentsComponent enchantments = stack.getEnchantments();
        if (enchantments == null || enchantments.isEmpty()) return false;

        // In Minecraft 1.21.4, we'll check by using the string representation
        // of each enchantment entry to look for protection-related enchantments
        for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
            // Convert the entry to string and check if it contains "protection"
            String enchantmentString = entry.toString().toLowerCase();
            if (enchantmentString.contains("protection")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the material tier of an item
     */
    private MaterialTier getMaterialTier(ItemStack stack) {
        Item item = stack.getItem();
        String itemId = Registries.ITEM.getId(item).toString();

        if (itemId.contains("netherite")) return MaterialTier.NETHERITE;
        if (itemId.contains("diamond")) return MaterialTier.DIAMOND;
        if (itemId.contains("iron")) return MaterialTier.IRON;
        if (itemId.contains("golden") || itemId.contains("gold")) return MaterialTier.GOLDEN;
        if (itemId.contains("stone")) return MaterialTier.STONE;
        if (itemId.contains("wooden") || itemId.contains("wood")) return MaterialTier.WOODEN;
        if (itemId.contains("leather")) return MaterialTier.LEATHER;

        return MaterialTier.OTHER;
    }

    /**
     * Sorts inventory items according to preferences
     * @return true if an action was performed
     */
    private boolean sortItems() {
        if (!sortInventory.getValue() || mc.player == null) return false;

        // Scan inventory and categorize items
        Map<ItemCategory, List<SortableItem>> categorizedItems = new HashMap<>();

        // Initialize categories
        for (ItemCategory category : ItemCategory.values()) {
            categorizedItems.put(category, new ArrayList<>());
        }

        // Collect and categorize inventory items
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            ItemCategory category = categorizeItem(stack);

            // Special case for swords - separate from other weapons
            if (category == ItemCategory.WEAPON && isSword(stack)) {
                category = ItemCategory.SWORD;
            }

            SortableItem sortableItem = new SortableItem(i, stack, category);
            categorizedItems.get(category).add(sortableItem);
        }

        // Sort each category
        for (ItemCategory category : ItemCategory.values()) {
            List<SortableItem> items = categorizedItems.get(category);

            if (category == ItemCategory.SWORD) {
                // Sort swords by tier and enchantments, best first
                items.sort(Comparator
                        .comparing((SortableItem item) -> getMaterialTier(item.stack).getValue())
                        .thenComparing(item -> calculateEnchantmentValue(item.stack))
                        .reversed());
            } else if (category == ItemCategory.WEAPON) {
                // Sort other weapons
                items.sort(Comparator
                        .comparing((SortableItem item) -> getMaterialTier(item.stack).getValue())
                        .thenComparing(item -> calculateEnchantmentValue(item.stack))
                        .reversed());
            } else if (category == ItemCategory.TOOL) {
                // Sort tools by tier
                items.sort(Comparator
                        .comparing((SortableItem item) -> getMaterialTier(item.stack).getValue())
                        .reversed());
            } else if (category == ItemCategory.FOOD) {
                // Sort food by saturation/hunger value
                items.sort(Comparator
                        .comparing((SortableItem item) -> calculateFoodValue(item.stack))
                        .reversed());
            }
        }

        // Place best sword in slot 0
        List<SortableItem> swords = categorizedItems.get(ItemCategory.SWORD);
        if (!swords.isEmpty()) {
            SortableItem bestSword = swords.get(0);
            int sourceSlot = bestSword.slotIndex < 9 ? bestSword.slotIndex + 36 : bestSword.slotIndex;
            int targetSlot = 36; // Hotbar slot 0

            if (sourceSlot != targetSlot) {
                swapSlots(sourceSlot, targetSlot);
                return true;
            }
        }

        // Sort rest of weapons to hotbar (1-2)
        List<SortableItem> weapons = new ArrayList<>(categorizedItems.get(ItemCategory.WEAPON));
        for (int i = 0; i < Math.min(2, weapons.size()); i++) {
            int sourceSlot = weapons.get(i).slotIndex;
            int targetSlot = i + 1;

            // Convert to actual slot indices
            sourceSlot = sourceSlot < 9 ? sourceSlot + 36 : sourceSlot;
            targetSlot = targetSlot + 36;

            if (sourceSlot != targetSlot) {
                swapSlots(sourceSlot, targetSlot);
                return true;
            }
        }

        // Sort tools to hotbar (3-5)
        List<SortableItem> tools = categorizedItems.get(ItemCategory.TOOL);
        for (int i = 0; i < Math.min(3, tools.size()); i++) {
            int sourceSlot = tools.get(i).slotIndex;
            int targetSlot = i + 3;

            // Convert to actual slot indices
            sourceSlot = sourceSlot < 9 ? sourceSlot + 36 : sourceSlot;
            targetSlot = targetSlot + 36;

            if (sourceSlot != targetSlot) {
                swapSlots(sourceSlot, targetSlot);
                return true;
            }
        }

        // Sort blocks to hotbar (6-7)
        List<SortableItem> blocks = categorizedItems.get(ItemCategory.BLOCK);
        for (int i = 0; i < Math.min(2, blocks.size()); i++) {
            int sourceSlot = blocks.get(i).slotIndex;
            int targetSlot = i + 6;

            // Convert to actual slot indices
            sourceSlot = sourceSlot < 9 ? sourceSlot + 36 : sourceSlot;
            targetSlot = targetSlot + 36;

            if (sourceSlot != targetSlot) {
                swapSlots(sourceSlot, targetSlot);
                return true;
            }
        }

        // Sort food to hotbar (8)
        List<SortableItem> foods = categorizedItems.get(ItemCategory.FOOD);
        if (!foods.isEmpty()) {
            int sourceSlot = foods.get(0).slotIndex;
            int targetSlot = 8;

            // Convert to actual slot indices
            sourceSlot = sourceSlot < 9 ? sourceSlot + 36 : sourceSlot;
            targetSlot = targetSlot + 36;

            if (sourceSlot != targetSlot) {
                swapSlots(sourceSlot, targetSlot);
                return true;
            }
        }

        return false;
    }

    /**
     * Drops items that are considered trash
     * @return true if an action was performed
     */
    private boolean dropTrashItems() {
        if (!dropTrash.getValue() || mc.player == null) return false;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (isTrashItem(stack)) {
                // Convert inventory index to container slot index
                int slotIndex = i < 9 ? i + 36 : i;

                // Drop the item (Q button behavior)
                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        slotIndex,
                        0,
                        SlotActionType.THROW,
                        mc.player
                );
                return true;
            }
        }

        return false;
    }

    /**
     * Drops duplicate items and lower tier items of the same type
     * @return true if an action was performed
     */
    private boolean dropDuplicateItems() {
        if (!dropDuplicates.getValue() || mc.player == null) return false;

        // Maps to track the best items by category and type
        Map<String, ItemStack> bestTools = new HashMap<>();
        Map<String, ItemStack> bestWeapons = new HashMap<>();
        Map<String, Integer> toolCounts = new HashMap<>();

        // First pass: find best items of each type
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            ItemCategory category = categorizeItem(stack);

            if (category == ItemCategory.TOOL) {
                String toolType = getToolType(stack);
                ItemStack currentBest = bestTools.get(toolType);

                if (currentBest == null || compareItems(stack, currentBest) > 0) {
                    bestTools.put(toolType, stack);
                }

                // Count this tool
                toolCounts.put(toolType, toolCounts.getOrDefault(toolType, 0) + 1);
            }
            else if (category == ItemCategory.WEAPON || category == ItemCategory.SWORD) {
                String weaponType = getWeaponType(stack);
                ItemStack currentBest = bestWeapons.get(weaponType);

                if (currentBest == null || compareItems(stack, currentBest) > 0) {
                    bestWeapons.put(weaponType, stack);
                }
            }
        }

        // Second pass: drop duplicates and lower tier items
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            ItemCategory category = categorizeItem(stack);

            if (category == ItemCategory.TOOL) {
                String toolType = getToolType(stack);
                ItemStack bestTool = bestTools.get(toolType);
                int count = toolCounts.getOrDefault(toolType, 0);

                // If we have more of this tool than we want to keep and this isn't the best one
                if (count > keepToolCount.getValue() && compareItems(stack, bestTool) < 0) {
                    // Convert inventory index to container slot index
                    int slotIndex = i < 9 ? i + 36 : i;

                    // Drop the item
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId,
                            slotIndex,
                            0,
                            SlotActionType.THROW,
                            mc.player
                    );

                    // Decrement the count
                    toolCounts.put(toolType, count - 1);
                    return true;
                }
            }
            else if (category == ItemCategory.WEAPON || category == ItemCategory.SWORD) {
                String weaponType = getWeaponType(stack);
                ItemStack bestWeapon = bestWeapons.get(weaponType);

                // If this is not the best weapon of its type
                if (compareItems(stack, bestWeapon) < 0) {
                    // Convert inventory index to container slot index
                    int slotIndex = i < 9 ? i + 36 : i;

                    // Drop the item
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId,
                            slotIndex,
                            0,
                            SlotActionType.THROW,
                            mc.player
                    );
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Compare two items for sorting purposes
     * @return positive if first is better, negative if second is better, 0 if equal
     */
    private int compareItems(ItemStack stack1, ItemStack stack2) {
        // First compare material tier
        int tierDiff = getMaterialTier(stack1).getValue() - getMaterialTier(stack2).getValue();
        if (tierDiff != 0) return tierDiff;

        // Then compare enchantment value
        return Float.compare(calculateEnchantmentValue(stack1), calculateEnchantmentValue(stack2));
    }

    /**
     * Calculate a value representing the enchantment quality of an item
     */
    private float calculateEnchantmentValue(ItemStack stack) {
        ItemEnchantmentsComponent enchantments = stack.getEnchantments();
        if (enchantments == null || enchantments.isEmpty()) return 0;

        // Simple algorithm: count the number of enchantments
        return enchantments.getSize() * 10.0f;
    }

    /**
     * Calculate food value based on hunger and saturation
     */
    private float calculateFoodValue(ItemStack stack) {
        if (!stack.contains(DataComponentTypes.FOOD)) return 0;

        FoodComponent foodComp = stack.getComponents().get(DataComponentTypes.FOOD);
        return foodComp.nutrition() + foodComp.saturation();
    }

    /**
     * Gets the tool type as a string identifier (pickaxe, axe, etc.)
     */
    private String getToolType(ItemStack stack) {
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();

        if (itemId.contains("pickaxe")) return "pickaxe";
        if (itemId.contains("axe") && !itemId.contains("pickaxe")) return "axe";
        if (itemId.contains("shovel")) return "shovel";
        if (itemId.contains("hoe")) return "hoe";

        return itemId; // Fallback to the full id
    }

    /**
     * Gets the weapon type as a string identifier (sword, bow, etc.)
     */
    private String getWeaponType(ItemStack stack) {
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();

        if (itemId.contains("sword")) return "sword";
        if (itemId.contains("bow") && !itemId.contains("crossbow")) return "bow";
        if (itemId.contains("crossbow")) return "crossbow";
        if (itemId.contains("trident")) return "trident";

        return itemId; // Fallback to the full id
    }

    /**
     * Determines if an item is considered trash
     */
    private boolean isTrashItem(ItemStack stack) {
        return trashItems.contains(stack.getItem());
    }

    /**
     * Categorizes an item
     */
    private ItemCategory categorizeItem(ItemStack stack) {
        Item item = stack.getItem();

        // In 1.21.4, we need to check component types rather than instanceof
        if (isWeapon(stack)) {
            return ItemCategory.WEAPON;
        } else if (isTool(stack)) {
            return ItemCategory.TOOL;
        } else if (isArmor(stack)) {
            return ItemCategory.ARMOR;
        } else if (item instanceof BlockItem) {
            return ItemCategory.BLOCK;
        } else if (hasFood(stack)) {
            return ItemCategory.FOOD;
        } else if (isTrashItem(stack)) {
            return ItemCategory.TRASH;
        } else {
            return ItemCategory.OTHER;
        }
    }

    /**
     * Checks if an item is a weapon
     */
    private boolean isWeapon(ItemStack stack) {
        Item item = stack.getItem();
        // Check for weapons using identifiers since instanceof is no longer reliable
        return item == Items.WOODEN_SWORD ||
                item == Items.STONE_SWORD ||
                item == Items.IRON_SWORD ||
                item == Items.GOLDEN_SWORD ||
                item == Items.DIAMOND_SWORD ||
                item == Items.NETHERITE_SWORD ||
                item == Items.BOW ||
                item == Items.CROSSBOW ||
                item == Items.TRIDENT;
    }

    /**
     * Checks if an item is specifically a sword
     */
    private boolean isSword(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.WOODEN_SWORD ||
                item == Items.STONE_SWORD ||
                item == Items.IRON_SWORD ||
                item == Items.GOLDEN_SWORD ||
                item == Items.DIAMOND_SWORD ||
                item == Items.NETHERITE_SWORD;
    }

    /**
     * Checks if an item is a tool
     * For Minecraft 1.21.4, we identify tools by comparing against specific Items instances
     */
    private boolean isTool(ItemStack stack) {
        Item item = stack.getItem();
        // Check for tools by comparing with known tool items
        return item == Items.WOODEN_PICKAXE ||
                item == Items.STONE_PICKAXE ||
                item == Items.IRON_PICKAXE ||
                item == Items.GOLDEN_PICKAXE ||
                item == Items.DIAMOND_PICKAXE ||
                item == Items.NETHERITE_PICKAXE ||
                item == Items.WOODEN_AXE ||
                item == Items.STONE_AXE ||
                item == Items.IRON_AXE ||
                item == Items.GOLDEN_AXE ||
                item == Items.DIAMOND_AXE ||
                item == Items.NETHERITE_AXE ||
                item == Items.WOODEN_SHOVEL ||
                item == Items.STONE_SHOVEL ||
                item == Items.IRON_SHOVEL ||
                item == Items.GOLDEN_SHOVEL ||
                item == Items.DIAMOND_SHOVEL ||
                item == Items.NETHERITE_SHOVEL ||
                item == Items.WOODEN_HOE ||
                item == Items.STONE_HOE ||
                item == Items.IRON_HOE ||
                item == Items.GOLDEN_HOE ||
                item == Items.DIAMOND_HOE ||
                item == Items.NETHERITE_HOE ||
                item == Items.SHEARS ||
                item == Items.FLINT_AND_STEEL;
    }

    /**
     * Checks if an item is armor
     */
    private boolean isArmor(ItemStack stack) {
        // Check if the stack has an equippable component with an armor slot
        if (stack.contains(DataComponentTypes.EQUIPPABLE)) {
            EquipmentSlot slot = stack.getComponents().get(DataComponentTypes.EQUIPPABLE).slot();
            return slot == EquipmentSlot.HEAD ||
                    slot == EquipmentSlot.CHEST ||
                    slot == EquipmentSlot.LEGS ||
                    slot == EquipmentSlot.FEET;
        }

        // Fallback: check item ID for armor keywords
        String itemId = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase();
        return itemId.contains("helmet") ||
                itemId.contains("chestplate") ||
                itemId.contains("leggings") ||
                itemId.contains("boots");
    }

    /**
     * Checks if an item is food
     */
    private boolean hasFood(ItemStack stack) {
        // Check if the item has a food component
        return stack.contains(DataComponentTypes.FOOD);
    }

    /**
     * Swaps items between two slots
     */
    private void swapSlots(int slot1, int slot2) {
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slot1,
                0,
                SlotActionType.PICKUP,
                mc.player
        );

        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slot2,
                0,
                SlotActionType.PICKUP,
                mc.player
        );

        // If there's still an item on cursor (from slot1), put it back
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slot1,
                0,
                SlotActionType.PICKUP,
                mc.player
        );
    }

    /**
     * Helper class for sortable items with metadata
     */
    private class SortableItem {
        public final int slotIndex;
        public final ItemStack stack;
        public final ItemCategory category;

        public SortableItem(int slotIndex, ItemStack stack, ItemCategory category) {
            this.slotIndex = slotIndex;
            this.stack = stack;
            this.category = category;
        }
    }

    /**
     * Adds an item to the trash list
     */
    public void addTrashItem(Item item) {
        if (!trashItems.contains(item)) {
            trashItems.add(item);
        }
    }

    /**
     * Removes an item from the trash list
     */
    public void removeTrashItem(Item item) {
        trashItems.remove(item);
    }

    /**
     * Clears the trash list
     */
    public void clearTrashItems() {
        trashItems.clear();
    }

    /**
     * Helper method to get display information about items in the inventory
     * Can be used for debugging
     */
    public String getInventoryStatus() {
        StringBuilder status = new StringBuilder();

        // Count equipped armor pieces
        status.append("Equipped Armor:\n");
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armorStack = getEquippedArmorItem(slot);
            status.append("  ").append(slot.name()).append(": ");
            if (!armorStack.isEmpty()) {
                status.append(armorStack.getItem().toString())
                        .append(" (Tier: ").append(getMaterialTier(armorStack))
                        .append(", Value: ").append(calculateArmorValue(armorStack)).append(")\n");
            } else {
                status.append("Empty\n");
            }
        }

        // List hotbar items
        status.append("\nHotbar Items:\n");
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            status.append("  Slot ").append(i).append(": ");
            if (!stack.isEmpty()) {
                ItemCategory category = categorizeItem(stack);
                status.append(stack.getItem().toString())
                        .append(" (").append(category).append(")\n");
            } else {
                status.append("Empty\n");
            }
        }

        return status.toString();
    }
}
