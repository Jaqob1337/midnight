package de.peter1337.midnight.utils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility enum for categorizing items and determining their types
 */
public class ItemType {

    /**
     * Main item categories
     */
    public enum Category {
        WEAPON,
        SWORD,
        TOOL,
        ARMOR,
        BLOCK,
        FOOD,
        TRASH,
        OTHER
    }

    /**
     * Material tiers for equipment
     */
    public enum MaterialTier {
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

    /**
     * Tool types
     */
    public enum ToolType {
        PICKAXE,
        AXE,
        SHOVEL,
        HOE,
        SHEARS,
        FLINT_AND_STEEL,
        OTHER
    }

    /**
     * Weapon types
     */
    public enum WeaponType {
        SWORD,
        BOW,
        CROSSBOW,
        TRIDENT,
        OTHER
    }

    /**
     * Default trash items - expanded list
     */
    private static final Set<Item> trashItems = new HashSet<>(Arrays.asList(
            // Original trash items
            Items.ROTTEN_FLESH,
            Items.POISONOUS_POTATO,
            Items.STRING,
            Items.SPIDER_EYE,
            Items.STICK,
            Items.BONE,
            Items.GUNPOWDER,

            // Added trash items
            Items.REDSTONE,
            Items.REDSTONE_TORCH,
            Items.REDSTONE_BLOCK,
            Items.REPEATER,
            Items.COMPARATOR,
            Items.SAND,
            Items.RED_SAND,
            Items.GRAVEL,
            Items.LEATHER,
            Items.RABBIT_HIDE,
            Items.FEATHER,
            Items.WHEAT_SEEDS,
            Items.PUMPKIN_SEEDS,
            Items.MELON_SEEDS,
            Items.BEETROOT_SEEDS,
            Items.SUGAR_CANE,
            Items.BAMBOO,
            Items.CACTUS,
            Items.KELP,
            Items.SEAGRASS,
            Items.INK_SAC,
            Items.GLOW_INK_SAC,
            Items.CLAY_BALL,
            Items.FLINT,
            Items.LILY_PAD,
            Items.VINE,
            Items.NETHER_WART,
            Items.NETHERRACK,
            Items.COAL,
            Items.CHARCOAL,
            Items.FLOWER_POT,
            Items.BONE_MEAL,
            Items.GLOWSTONE_DUST,
            Items.BLAZE_POWDER,
            Items.PAPER,
            Items.BOOK,
            Items.GLASS_PANE,
            Items.ANDESITE,
            Items.DIORITE,
            Items.COBBLED_DEEPSLATE,
            Items.TUFF,
            Items.CALCITE,
            Items.AMETHYST_SHARD,
            Items.COPPER_INGOT
    ));

    /**
     * Categorizes an item
     *
     * @param stack The item to categorize
     * @return The item category
     */
    public static Category categorizeItem(ItemStack stack) {
        Item item = stack.getItem();

        // In 1.21.4, check component types rather than instanceof
        if (isWeapon(stack)) {
            return isSword(stack) ? Category.SWORD : Category.WEAPON;
        } else if (isTool(stack)) {
            return Category.TOOL;
        } else if (isArmor(stack)) {
            return Category.ARMOR;
        } else if (item instanceof BlockItem) {
            return Category.BLOCK;
        } else if (hasFood(stack)) {
            return Category.FOOD;
        } else if (isTrashItem(item)) {
            return Category.TRASH;
        } else {
            return Category.OTHER;
        }
    }

    /**
     * Checks if an item is considered trash
     *
     * @param item The item to check
     * @return true if it's a trash item
     */
    public static boolean isTrashItem(Item item) {
        return trashItems.contains(item);
    }

    /**
     * Adds an item to the trash items list
     *
     * @param item The item to add
     */
    public static void addTrashItem(Item item) {
        trashItems.add(item);
    }

    /**
     * Removes an item from the trash items list
     *
     * @param item The item to remove
     */
    public static void removeTrashItem(Item item) {
        trashItems.remove(item);
    }

    /**
     * Clears the trash items list
     */
    public static void clearTrashItems() {
        trashItems.clear();
    }

    /**
     * Gets the trash items list
     *
     * @return Set of trash items
     */
    public static Set<Item> getTrashItems() {
        return new HashSet<>(trashItems);
    }

    /**
     * Checks if an item is a weapon
     *
     * @param stack The item to check
     * @return true if it's a weapon
     */
    public static boolean isWeapon(ItemStack stack) {
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
     *
     * @param stack The item to check
     * @return true if it's a sword
     */
    public static boolean isSword(ItemStack stack) {
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
     *
     * @param stack The item to check
     * @return true if it's a tool
     */
    public static boolean isTool(ItemStack stack) {
        Item item = stack.getItem();
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
     *
     * @param stack The item to check
     * @return true if it's armor
     */
    public static boolean isArmor(ItemStack stack) {
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
     *
     * @param stack The item to check
     * @return true if it's food
     */
    public static boolean hasFood(ItemStack stack) {
        return stack.contains(DataComponentTypes.FOOD);
    }

    /**
     * Gets the tool type of an item
     *
     * @param stack The item to check
     * @return The tool type
     */
    public static ToolType getToolType(ItemStack stack) {
        String itemId = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase();

        if (itemId.contains("pickaxe")) return ToolType.PICKAXE;
        if (itemId.contains("axe") && !itemId.contains("pickaxe")) return ToolType.AXE;
        if (itemId.contains("shovel")) return ToolType.SHOVEL;
        if (itemId.contains("hoe")) return ToolType.HOE;
        if (itemId.contains("shears")) return ToolType.SHEARS;
        if (itemId.contains("flint_and_steel")) return ToolType.FLINT_AND_STEEL;

        return ToolType.OTHER;
    }

    /**
     * Gets the weapon type of an item
     *
     * @param stack The item to check
     * @return The weapon type
     */
    public static WeaponType getWeaponType(ItemStack stack) {
        String itemId = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase();

        if (itemId.contains("sword")) return WeaponType.SWORD;
        if (itemId.contains("bow") && !itemId.contains("crossbow")) return WeaponType.BOW;
        if (itemId.contains("crossbow")) return WeaponType.CROSSBOW;
        if (itemId.contains("trident")) return WeaponType.TRIDENT;

        return WeaponType.OTHER;
    }

    /**
     * Gets the material tier of an item
     *
     * @param stack The item to evaluate
     * @return The material tier
     */
    public static MaterialTier getMaterialTier(ItemStack stack) {
        String itemId = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase();

        if (itemId.contains("netherite")) return MaterialTier.NETHERITE;
        if (itemId.contains("diamond")) return MaterialTier.DIAMOND;
        if (itemId.contains("iron")) return MaterialTier.IRON;
        if (itemId.contains("golden") || itemId.contains("gold")) return MaterialTier.GOLDEN;
        if (itemId.contains("stone")) return MaterialTier.STONE;
        if (itemId.contains("wooden") || itemId.contains("wood")) return MaterialTier.WOODEN;
        if (itemId.contains("leather")) return MaterialTier.LEATHER;

        return MaterialTier.OTHER;
    }
}