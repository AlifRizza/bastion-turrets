package dev.bastion.turret;

import dev.bastion.Bastion;
import dev.bastion.hub.HubConnectorItem;
import dev.bastion.modifier.ModifierEffect;
import dev.bastion.modifier.ModifierItem;
import dev.bastion.registry.BastionItems;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponModuleItem;
import dev.bastion.weapon.WeaponType;
import net.minecraft.resources.ResourceLocation;
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
 * Weapon / ammo / modifier slots of a base (PLAN 4.2) plus its Hub Connector slot. Always sized for T3; slots above the
 * current tier are locked, so upgrading a tier never has to move or resize anything.
 */
public class TurretInventory extends ItemStackHandler {
    /** Every Bastion ammo; accepted while no weapon is mounted, so a base can be stocked in advance (PLAN 4.2). */
    public static final TagKey<Item> AMMO = ItemTags.create(Bastion.id("ammo"));

    /** What an empty makeshift base accepts: the early ammo, which never joins {@link #AMMO}. */
    public static final TagKey<Item> AMMO_EARLY = ItemTags.create(Bastion.id("ammo_early"));
    /** Arrow Launcher ammo: refused by an empty makeshift base off the floor, where no Arrow Launcher can mount. */
    private static final TagKey<Item> ARROWS = ItemTags.create(Bastion.id("ammo/arrow"));

    public static final int WEAPON = 0;
    public static final int AMMO_START = 1;
    public static final int MODIFIER_START = AMMO_START + TurretTier.MAX_AMMO_SLOTS;
    /** The Hub Connector (PLAN "Hub Connector"): links the turret to a Turret Control Hub; every tier has it. */
    public static final int CONNECTOR = MODIFIER_START + TurretTier.MAX_MODIFIER_SLOTS;
    public static final int SIZE = CONNECTOR + 1;

    private final Supplier<TurretTier> tier;
    /** The base kind: a weapon module fits only the kind it is made for. */
    private final Supplier<BaseKind> kind;
    /** Mounted on a floor (FACING UP): a makeshift base takes an arc-lobbing module (Arrow Launcher) only there. */
    private final BooleanSupplier upright;
    private final IntConsumer onChanged;

    public TurretInventory(Supplier<TurretTier> tier, Supplier<BaseKind> kind, BooleanSupplier upright, IntConsumer onChanged) {
        super(SIZE);
        this.tier = tier;
        this.kind = kind;
        this.upright = upright;
        this.onChanged = onChanged;
    }

    public BaseKind kind() {
        return kind.get();
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
        return data != null ? data.ammo() : kind.get() == BaseKind.MAKESHIFT ? AMMO_EARLY : AMMO;
    }

    /** Ammo for the mounted weapon; Creative Ammo fits every weapon. */
    public boolean isAmmo(ItemStack stack) {
        if (weaponData() == null && kind.get() == BaseKind.MAKESHIFT && !upright.getAsBoolean() && stack.is(ARROWS)) return false;
        return stack.is(ammoTag()) || stack.is(BastionItems.CREATIVE_AMMO.get());
    }

    /** First unlocked ammo slot holding ammo for the mounted weapon, or -1. */
    public int findAmmoSlot() {
        for (int i = 0; i < ammoSlots(); i++) {
            if (isAmmo(getStackInSlot(AMMO_START + i))) return AMMO_START + i;
        }
        return -1;
    }

    /** Whether the current tier unlocks this slot (the Connector slot is always open, except on a makeshift base). */
    public boolean isActive(int slot) {
        if (slot >= MODIFIER_START && kind.get() == BaseKind.MAKESHIFT) return false; // no modifier slots, no Connector
        if (slot == CONNECTOR) return true;
        if (slot < AMMO_START) return true;
        if (slot < MODIFIER_START) return slot - AMMO_START < tier.get().ammoSlots;
        return slot - MODIFIER_START < tier.get().modifierSlots;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (!isActive(slot)) return false;
        if (slot == CONNECTOR) return stack.getItem() instanceof HubConnectorItem;
        if (slot == WEAPON) return stack.getItem() instanceof WeaponModuleItem module && module.kind() == kind.get()
                && (kind.get() != BaseKind.MAKESHIFT || upright.getAsBoolean() || !ballistic(module));
        if (slot < MODIFIER_START) return isAmmo(stack);
        return stack.getItem() instanceof ModifierItem modifier && !hasModifierElsewhere(stack.getItem(), slot)
                && modifier.effect().fits(weaponId());
    }

    /**
     * Lobs on an arc (Arrow Launcher, Mortar): its arrows would fly sideways off a wall base, so on a makeshift base it fits
     * the floor only (user 2026-10-22). The large base, the Mortar's, is floor-only anyway.
     */
    private static boolean ballistic(WeaponModuleItem module) {
        WeaponData data = module.data();
        WeaponType type = data == null ? null : BastionWeaponTypes.REGISTRY.get().getValue(data.type());
        return type != null && type.ballistic();
    }

    /** Id of the mounted weapon's data, or null without a weapon. */
    @Nullable
    public ResourceLocation weaponId() {
        return getStackInSlot(WEAPON).getItem() instanceof WeaponModuleItem weapon ? weapon.weaponId() : null;
    }

    /** Each modifier kind counts once per base, whatever its Mk (PLAN 4.6). */
    private boolean hasModifierElsewhere(Item item, int slot) {
        if (!(item instanceof ModifierItem modifier)) return false;
        for (int i = MODIFIER_START; i < CONNECTOR; i++) {
            if (i != slot && getStackInSlot(i).getItem() instanceof ModifierItem other && other.family().equals(modifier.family())) return true;
        }
        return false;
    }

    /** Sum of the modifiers in unlocked slots; a weapon-specific one only counts with its weapon mounted. */
    public ModifierEffect modifierEffect() {
        ModifierEffect total = ModifierEffect.NONE;
        ResourceLocation weapon = weaponId();
        for (int i = 0; i < tier.get().modifierSlots; i++) {
            if (isActive(MODIFIER_START + i) && getStackInSlot(MODIFIER_START + i).getItem() instanceof ModifierItem modifier && modifier.effect().fits(weapon)) {
                total = total.plus(modifier.effect());
            }
        }
        return total;
    }

    /** A module in this slot that does nothing with the weapon now mounted (left in after a weapon swap). */
    public boolean inactiveModifier(int slot) {
        return getStackInSlot(slot).getItem() instanceof ModifierItem modifier && !modifier.effect().fits(weaponId());
    }

    /** Saves from before the Connector slot hold 14 slots: loading one must not shrink the handler below SIZE. */
    @Override
    public void setSize(int size) {
        super.setSize(Math.max(size, SIZE));
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

    /** A menu grew or shrank the stack in {@code slot} in place (shift-click): tell the owner so it gets saved. */
    public void changedInPlace(int slot) {
        onChanged.accept(slot);
    }
}
