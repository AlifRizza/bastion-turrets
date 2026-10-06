package dev.bastion.turret;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * The only item capability a base exposes, from every side (PLAN 4.2): hoppers, Create
 * funnels/belts and pipes see just the unlocked ammo slots, never weapon or modifier slots.
 * Item validity comes from {@link TurretInventory#isItemValid}, so non-ammo is refused.
 */
public class AmmoOnlyItemHandler implements IItemHandler {
    private final TurretInventory inventory;

    public AmmoOnlyItemHandler(TurretInventory inventory) {
        this.inventory = inventory;
    }

    private static int slot(int index) {
        return TurretInventory.AMMO_START + index;
    }

    @Override
    public int getSlots() {
        return inventory.ammoSlots();
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return inventory.getStackInSlot(slot(index));
    }

    @Override
    public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
        return inventory.insertItem(slot(index), stack, simulate);
    }

    @Override
    public ItemStack extractItem(int index, int amount, boolean simulate) {
        return inventory.extractItem(slot(index), amount, simulate);
    }

    @Override
    public int getSlotLimit(int index) {
        return inventory.getSlotLimit(slot(index));
    }

    @Override
    public boolean isItemValid(int index, ItemStack stack) {
        return inventory.isItemValid(slot(index), stack);
    }
}
