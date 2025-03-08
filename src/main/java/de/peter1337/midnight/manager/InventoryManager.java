package de.peter1337.midnight.manager;

import de.peter1337.midnight.Midnight;
import de.peter1337.midnight.utils.ItemType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for handling inventory operations
 */
public class InventoryManager {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /**
     * Swaps items between two slots in the inventory
     *
     * @param slot1 First slot index
     * @param slot2 Second slot index
     */
    public static void swapSlots(int slot1, int slot2) {
        if (mc.player == null || !(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) return;

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
     * Drops an item from a specific inventory slot
     *
     * @param slotIndex The slot to drop from
     */
    public static void dropItem(int slotIndex) {
        if (mc.player == null || !(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) return;

        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slotIndex,
                0,
                SlotActionType.THROW,
                mc.player
        );
    }

    /**
     * Checks if the player has space for an item in their inventory
     *
     * @param item The item to check space for
     * @return true if there is space for the item
     */
    public static boolean hasSpaceForItem(Item item) {
        if (mc.player == null) return false;

        // Check if player has an empty slot
        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
            if (mc.player.getInventory().main.get(i).isEmpty()) {
                return true;
            }
        }

        // Check if player has the same item with space
        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
            ItemStack stack = mc.player.getInventory().main.get(i);
            if (stack.getItem() == item && stack.getCount() < stack.getMaxCount()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the currently equipped armor item in the specified slot
     *
     * @param slot The equipment slot
     * @return The item in that slot
     */
    public static ItemStack getEquippedArmorItem(EquipmentSlot slot) {
        if (mc.player == null) return ItemStack.EMPTY;

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
     * Determines which equipment slot an item belongs to
     *
     * @param stack The item to check
     * @return The equipment slot or null if not armor
     */
    public static EquipmentSlot getArmorEquipmentSlot(ItemStack stack) {
        if (stack.isEmpty() || !stack.contains(DataComponentTypes.EQUIPPABLE)) {
            return null;
        }
        return stack.getComponents().get(DataComponentTypes.EQUIPPABLE).slot();
    }

    /**
     * Gets the actual slot index for a given armor equipment slot
     *
     * @param slot The equipment slot
     * @return The inventory slot index
     */
    public static int getArmorSlotIndex(EquipmentSlot slot) {
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
     *
     * @param stack The armor item stack
     * @return The calculated value score
     */
    public static float calculateArmorValue(ItemStack stack) {
        if (stack.isEmpty()) return 0.0f;

        // Start with the base material tier value
        float value = ItemType.getMaterialTier(stack).getValue() * 100.0f;

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
     *
     * @param stack The item to check
     * @return true if it has protection enchantments
     */
    public static boolean hasProtectionEnchantment(ItemStack stack) {
        ItemEnchantmentsComponent enchantments = stack.getEnchantments();
        if (enchantments == null || enchantments.isEmpty()) return false;

        // In Minecraft 1.21.4, check using the string representation
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
     * Drops an entire item stack from a specific inventory slot
     *
     * @param slotIndex The slot to drop from
     */
    /**
     * Drops an entire item stack using Ctrl + Q
     *
     * @param slotIndex The slot to drop from
     */
    public static void dropItemStack(int slotIndex) {
        if (mc.player == null || !(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) return;

        // Simulate Ctrl + Q for dropping entire stack
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slotIndex,
                1, // Use modifier key (Ctrl)
                SlotActionType.THROW,
                mc.player
        );
    }

    /**
     * Calculate food value based on hunger and saturation
     *
     * @param stack The food item
     * @return The calculated food value
     */
    public static float calculateFoodValue(ItemStack stack) {
        if (!stack.contains(DataComponentTypes.FOOD)) return 0;

        var foodComp = stack.getComponents().get(DataComponentTypes.FOOD);
        return foodComp.nutrition() + foodComp.saturation();
    }

    /**
     * Calculate a value representing the enchantment quality of an item
     *
     * @param stack The item to evaluate
     * @return The enchantment value score
     */
    public static float calculateEnchantmentValue(ItemStack stack) {
        ItemEnchantmentsComponent enchantments = stack.getEnchantments();
        if (enchantments == null || enchantments.isEmpty()) return 0;

        // Simple algorithm: count the number of enchantments
        return enchantments.getSize() * 10.0f;
    }

    /**
     * Compare two items for sorting purposes
     *
     * @return positive if first is better, negative if second is better, 0 if equal
     */
    public static int compareItems(ItemStack stack1, ItemStack stack2) {
        // First compare material tier
        int tierDiff = ItemType.getMaterialTier(stack1).getValue() - ItemType.getMaterialTier(stack2).getValue();
        if (tierDiff != 0) return tierDiff;

        // Then compare enchantment value
        return Float.compare(calculateEnchantmentValue(stack1), calculateEnchantmentValue(stack2));
    }
}