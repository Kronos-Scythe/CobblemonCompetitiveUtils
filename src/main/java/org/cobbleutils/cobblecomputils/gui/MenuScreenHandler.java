package org.cobbleutils.cobblecomputils.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;

/**
 * A server-side, read-only chest menu. Vanilla clients render it as a normal
 * chest (3 or 6 rows); every click is cancelled and handed to a
 * {@link ClickListener}, so nothing in it can be taken, moved or duplicated.
 */
public class MenuScreenHandler extends GenericContainerScreenHandler {
    @FunctionalInterface
    public interface ClickListener {
        void onClick(int slot, int button, SlotActionType actionType);
    }

    private final int size;
    private final ClickListener listener;

    public MenuScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory menu, int rows,
                             ClickListener listener) {
        super(typeFor(rows), syncId, playerInventory, menu, rows);
        this.size = rows * 9;
        this.listener = listener;
    }

    private static ScreenHandlerType<GenericContainerScreenHandler> typeFor(int rows) {
        return switch (rows) {
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 6 -> ScreenHandlerType.GENERIC_9X6;
            default -> throw new IllegalArgumentException("Unsupported menu rows: " + rows);
        };
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < size) {
            listener.onClick(slotIndex, button, actionType);
        }
        // The client predicts vanilla behaviour (picking the item up, etc.); undo that.
        if (player.currentScreenHandler == this) {
            syncState();
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }
}
