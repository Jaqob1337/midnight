package de.peter1337.midnight.modules.player;

import de.peter1337.midnight.modules.Module;
import de.peter1337.midnight.modules.Category;
import de.peter1337.midnight.modules.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class Stealer extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public Stealer() {
        super("Stealer", "Automatically takes items from containers", Category.PLAYER, "x");

        // Default whitelist/blacklist items
        itemFilter.add(Items.DIAMOND);
        itemFilter.add(Items.NETHERITE_INGOT);
        itemFilter.add(Items.ENCHANTED_GOLDEN_APPLE);
    }

    private final Setting<String> mode = register(
            new Setting<>("Mode", "All", Arrays.asList("All", "Whitelist", "Blacklist"), "Item selection mode")
    );

    private final Setting<Float> delay = register(
            new Setting<>("Delay", 0.2f, 0.0f, 1.0f, "Delay between taking items (in seconds)")
    );

    private final Setting<Boolean> randomDelay = register(
            new Setting<>("RandomDelay", Boolean.TRUE, "Add slight variation to delay")
    );

    private final Setting<Boolean> autoClose = register(
            new Setting<>("AutoClose", Boolean.TRUE, "Automatically close the container when finished")
    );

    private final Setting<Float> closeDelay = register(
            new Setting<>("CloseDelay", 0.2f, 0.0f, 2.0f, "Delay before closing container (in seconds)")
                    .dependsOn(autoClose)
    );

    private final Setting<Boolean> humanPatterns = register(
            new Setting<>("HumanPatterns", Boolean.TRUE, "Mimic human-like interaction patterns")
    );

    private final Setting<Boolean> smartStealer = register(
            new Setting<>("SmartStealer", Boolean.TRUE, "Takes items only if you have space for them")
    );

    private final Setting<Boolean> chestsOnly = register(
            new Setting<>("ChestsOnly", Boolean.FALSE, "Only steal from containers named 'Chest'")
    );

    // Add a list for whitelisted/blacklisted items
    private final List<Item> itemFilter = new ArrayList<>();

    private long lastStealTime = 0;
    private boolean isCurrentlyWorking = false;

    // Track the last slot we stole from, to avoid always going in order (which is detectable)
    private int lastSlotIndex = -1;

    // Keeps track of how many consecutive items we've taken to avoid constant patterns
    private int consecutiveItemsTaken = 0;

    @Override
    public void onEnable() {
        isCurrentlyWorking = false;
        lastSlotIndex = -1;
        containerOpenTime = 0;
        consecutiveItemsTaken = 0;
    }

    @Override
    public void onDisable() {
        isCurrentlyWorking = false;
        lastSlotIndex = -1;
        containerOpenTime = 0;
        consecutiveItemsTaken = 0;
    }

    private long containerOpenTime = 0;

    @Override
    public void onUpdate() {
        if (!isEnabled() || mc.player == null || mc.currentScreen == null) return;

        // Check if we're in a container
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            GenericContainerScreenHandler handler = (GenericContainerScreenHandler) mc.player.currentScreenHandler;

            // Check if ChestsOnly is enabled and if the container is not a chest
            if (chestsOnly.getValue() && !isChestContainer(handler)) {
                return;
            }

            // If this is the first time seeing this container, record the time
            if (containerOpenTime == 0) {
                containerOpenTime = System.currentTimeMillis();

                // Add a small initial delay before stealing to appear more natural
                if (humanPatterns.getValue() && Math.random() < 0.8) {
                    // Wait 100-300ms before starting to steal
                    lastStealTime = System.currentTimeMillis() + (long)(Math.random() * 200) + 100;
                    return;
                }
            }

            stealItems();
        } else {
            // Reset container open time when no container is open
            containerOpenTime = 0;
        }
    }

    private boolean isChestContainer(GenericContainerScreenHandler handler) {
        // Check if the screen handler type matches chest types
        ScreenHandlerType<?> type = handler.getType();

        // Most chests use GENERIC_9X3 (single chest) or GENERIC_9X6 (double chest)
        // We can check if it's one of these types
        return type == ScreenHandlerType.GENERIC_9X3 || type == ScreenHandlerType.GENERIC_9X6;
    }

    private void stealItems() {
        GenericContainerScreenHandler handler = (GenericContainerScreenHandler) mc.player.currentScreenHandler;
        ClientPlayerEntity player = mc.player;

        // Delay mechanism with slight randomization
        long currentTime = System.currentTimeMillis();
        float actualDelay;

        if (randomDelay.getValue()) {
            // Add slight variation (±10% of the base delay)
            float variation = delay.getValue() * 1.8f;
            actualDelay = delay.getValue() + (float)(Math.random() * variation * 2 - variation);
        } else {
            actualDelay = delay.getValue();
        }

        if (currentTime - lastStealTime < actualDelay * 1000) {
            return;
        }

        isCurrentlyWorking = false;
        boolean foundItemToSteal = false;

        // Only look at the container slots, not the player inventory
        int containerSize = handler.getRows() * 9;

        // Occasionally skip items or change the order to appear more human-like
        List<Integer> slotOrder = new ArrayList<>();
        for (int i = 0; i < containerSize; i++) {
            slotOrder.add(i);
        }

        // Randomize slot order sometimes to avoid predictable patterns
        if (humanPatterns.getValue() && Math.random() < 0.3) {
            Collections.shuffle(slotOrder);
        }

        // Sometimes start from where we left off
        if (lastSlotIndex >= 0 && lastSlotIndex < containerSize && Math.random() < 0.5) {
            List<Integer> reordered = new ArrayList<>();
            for (int i = lastSlotIndex; i < containerSize; i++) {
                reordered.add(i);
            }
            for (int i = 0; i < lastSlotIndex; i++) {
                reordered.add(i);
            }
            slotOrder = reordered;
        }

        // Force occasional pauses to look more natural
        if (humanPatterns.getValue() && consecutiveItemsTaken > 3 && Math.random() < 0.3) {
            // Skip this tick to add a natural pause
            consecutiveItemsTaken = 0;
            return;
        }

        for (int slotNum : slotOrder) {
            int i = slotNum;
            Slot slot = handler.slots.get(i);

            if (!slot.hasStack()) continue;

            // Check if the item should be stolen based on the mode
            boolean shouldStealItem = shouldStealItem(slot.getStack().getItem());
            if (!shouldStealItem) continue;

            // If SmartStealer is enabled, check if the player has space
            if (smartStealer.getValue() && !hasSpaceForItem(player, slot.getStack().getItem())) {
                continue;
            }

            // Perform the quick move (shift-click) with modified human patterns
            if (humanPatterns.getValue()) {
                // Hover over the item for half the delay value before taking it
                long hoverTime = (long)(delay.getValue() * 500); // Half of delay in milliseconds

                // Add some slight randomization to the hover time (±20%)
                if (randomDelay.getValue()) {
                    float variation = hoverTime * 0.5f;
                    hoverTime += (long)(Math.random() * variation * 2 - variation);
                }

                if (hoverTime > 0) {
                    try {
                        Thread.sleep(hoverTime);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                // Regular shift-click (removed the alternate click method)
                mc.interactionManager.clickSlot(
                        handler.syncId,
                        i,
                        0,
                        net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,
                        player
                );
            } else {
                // Regular shift-click if human patterns disabled
                mc.interactionManager.clickSlot(
                        handler.syncId,
                        i,
                        0,
                        net.minecraft.screen.slot.SlotActionType.QUICK_MOVE,
                        player
                );
            }

            lastStealTime = currentTime;
            lastSlotIndex = i;
            isCurrentlyWorking = true;
            foundItemToSteal = true;
            consecutiveItemsTaken++;
            break;  // Only steal one item per tick
        }

        // Check if we're done stealing
        if (!foundItemToSteal && autoClose.getValue()) {
            // Add delay before closing to appear more natural
            long closeDelayMs = (long)(closeDelay.getValue() * 1000);

            if (closeDelayMs > 0) {
                // Use a separate thread for delayed closing to avoid blocking the game
                new Thread(() -> {
                    try {
                        Thread.sleep(closeDelayMs);
                        // Make sure we're still in same screen
                        if (mc.player != null && mc.player.currentScreenHandler == handler) {
                            mc.execute(() -> {
                                mc.player.closeHandledScreen();
                            });
                        }
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }).start();
            } else {
                // Close immediately if delay is 0
                mc.player.closeHandledScreen();
            }
        }
    }

    private boolean shouldStealItem(Item item) {
        switch (mode.getValue()) {
            case "All":
                return true;
            case "Whitelist":
                return itemFilter.contains(item);
            case "Blacklist":
                return !itemFilter.contains(item);
            default:
                return true;
        }
    }

    private boolean hasSpaceForItem(ClientPlayerEntity player, Item item) {
        // Check if player has an empty slot
        for (int i = 0; i < player.getInventory().main.size(); i++) {
            if (player.getInventory().main.get(i).isEmpty()) {
                return true;
            }
        }

        // Check if player has the same item with space
        for (int i = 0; i < player.getInventory().main.size(); i++) {
            if (player.getInventory().main.get(i).getItem() == item &&
                    player.getInventory().main.get(i).getCount() < player.getInventory().main.get(i).getMaxCount()) {
                return true;
            }
        }

        return false;
    }

    // Method to add an item to the filter list (could be called from a command or UI)
    public void addItemToFilter(Item item) {
        if (!itemFilter.contains(item)) {
            itemFilter.add(item);
        }
    }

    // Method to remove an item from the filter list
    public void removeItemFromFilter(Item item) {
        itemFilter.remove(item);
    }

    // Method to clear the filter list
    public void clearItemFilter() {
        itemFilter.clear();
    }
}