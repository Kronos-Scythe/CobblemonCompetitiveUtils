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
 * 3-row chest; every click is cancelled and handed to a {@link ClickListener},
 * so nothing in it can be taken, moved or duplicated.
 */
public class MenuScreenHandler extends GenericContainerScreenHandler {
    public static final int ROWS = 3;
    public static final int SIZE = ROWS * 9;

    @FunctionalInterface
    public interface ClickListener {
        void onClick(int slot, int button, SlotActionType actionType);
    }

    private final ClickListener listener;

    public MenuScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory menu, ClickListener listener) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, menu, ROWS);
        this.listener = listener;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < SIZE) {
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
