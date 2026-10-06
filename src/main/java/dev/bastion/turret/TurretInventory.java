package dev.bastion.turret;

import dev.bastion.Bastion;
import dev.bastion.modifier.ModifierEffect;
import dev.bastion.modifier.ModifierItem;
import dev.bastion.registry.BastionItems;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponModuleItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Weapon / ammo / modifier slots of a base, PLAN 4.2. Always sized for T3; slots above the
 * current tier are locked, so upgrading a tier never has to move or resize anything.
 */
public class TurretInventory extends ItemStackHandler {
    /** Every Bastion ammo; accepted while no weapon is mounted, so a base can be stocked in advance (PLAN 4.2). */
    public static final TagKey<Item> AMMO = ItemTags.create(Bastion.id("ammo"));

    public static final int WEAPON = 0;
    public static final int AMMO_START = 1;
    public static final int MODIFIER_START = AMMO_START + TurretTier.MAX_AMMO_SLOTS;
    public static final int SIZE = MODIFIER_START + TurretTier.MAX_MODIFIER_SLOTS;

    private final Supplier<TurretTier> tier;
    /** True on a large (2x2) base: only big weapon modules fit, and only big ones fit there. */
    private final BooleanSupplier large;
    private final IntConsumer onChanged;

    public TurretInventory(Supplier<TurretTier> tier, BooleanSupplier large, IntConsumer onChanged) {
        super(SIZE);
        this.tier = tier;
        this.large = large;
        this.onChanged = onChanged;
    }

    public int ammoSlots() {
        return tier.get().ammoSlots;
    }

    @Nullable
    public WeaponData weaponData() {
        return getStackInSlot(WEAPON).getItem() instanceof WeaponModuleItem weapon ? weapon.data() : null;
    }

    /**
     * Ammo the slots accept: the mounted weapon's tag, or any Bastion ammo without a weapon. Ammo already
     * stored that no longer matches stays put (automation can still pull it out); it is just never fired.
     */
    public TagKey<Item> ammoTag() {
        WeaponData data = weaponData();
        return data != null ? data.ammo() : AMMO;
    }

    /** Ammo for the mounted weapon; Creative Ammo fits every weapon. */
    public boolean isAmmo(ItemStack stack) {
        return stack.is(ammoTag()) || stack.is(BastionItems.CREATIVE_AMMO.get());
    }

    /** First unlocked ammo slot holding ammo for the mounted weapon, or -1. */
    public int findAmmoSlot() {
        for (int i = 0; i < ammoSlots(); i++) {
            if (isAmmo(getStackInSlot(AMMO_START + i))) return AMMO_START + i;
        }
        return -1;
    }

    /** Whether the current tier unlocks this slot. */
    public boolean isActive(int slot) {
        if (slot < AMMO_START) return true;
        if (slot < MODIFIER_START) return slot - AMMO_START < tier.get().ammoSlots;
        return slot - MODIFIER_START < tier.get().modifierSlots;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (!isActive(slot)) return false;
        if (slot == WEAPON) return stack.getItem() instanceof WeaponModuleItem module && module.large() == large.getAsBoolean();
        if (slot < MODIFIER_START) return isAmmo(stack);
        return stack.getItem() instanceof ModifierItem && !hasModifierElsewhere(stack.getItem(), slot);
    }

    /** Each modifier counts once per base (PLAN 4.6). */
    private boolean hasModifierElsewhere(Item item, int slot) {
        for (int i = MODIFIER_START; i < SIZE; i++) {
            if (i != slot && getStackInSlot(i).is(item)) return true;
        }
        return false;
    }

    /** Sum of the modifiers in unlocked slots. */
    public ModifierEffect modifierEffect() {
        ModifierEffect total = ModifierEffect.NONE;
        for (int i = 0; i < tier.get().modifierSlots; i++) {
            if (getStackInSlot(MODIFIER_START + i).getItem() instanceof ModifierItem modifier) total = total.plus(modifier.effect());
        }
        return total;
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot >= AMMO_START && slot < MODIFIER_START ? super.getSlotLimit(slot) : 1;
    }

    public boolean isEmpty() {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChanged.accept(slot);
    }
}
