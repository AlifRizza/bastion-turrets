package dev.bastion.menu;

import dev.bastion.registry.BastionMenus;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.turret.TurretTier;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.weapon.WeaponData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Turret container, PLAN 4.7. Menu slot i is TurretInventory slot i for the first SIZE slots. Live numbers
 * (HP, heat, state) ride on {@link #data}; settings arrive once in the open data and leave via TurretConfigUpdate.
 */
public class TurretMenu extends AbstractContainerMenu {
    // GUI-space slot positions; the frames are drawn by TurretScreen and tools/gen_textures.py turret_gui().
    public static final int WEAPON_X = 18, WEAPON_Y = 44;
    public static final int AMMO_X = 67, AMMO_Y = 37;
    public static final int MODIFIER_X = 151, MODIFIER_Y = 37;
    public static final int PLAYER_INV_X = 28, PLAYER_INV_Y = 115;

    // Indices into data; HP and heat are sent x10 and energy in hundreds of FE, because data slots carry 16-bit integers.
    public static final int DATA_HEALTH = 0, DATA_MAX_HEALTH = 1, DATA_HEAT = 2, DATA_MAX_HEAT = 3, DATA_STATE = 4,
            DATA_ENERGY = 5, DATA_MAX_ENERGY = 6, DATA_COUNT = 7;

    private final ContainerLevelAccess access;
    /** The base block it was opened on (standard or large), for the reach check. */
    private final Block block;
    private final TurretTier tier;
    private final BlockPos pos;
    private final TurretInventory turret;
    private final ContainerData data;
    // Settings as opened; the screen edits these copies and sends them back whole.
    public final TargetFilter filter;
    public boolean enabled, redstoneInverted, salvo;
    /** Client only: the Targeting and Info tabs hide every slot. */
    public boolean slotsVisible = true;

    public static void writeOpenData(FriendlyByteBuf buf, TurretBaseBlockEntity base) {
        buf.writeBlockPos(base.getBlockPos());
        buf.writeEnum(base.tier());
        buf.writeBoolean(base.large());
        base.filter().write(buf);
        buf.writeBoolean(base.enabled());
        buf.writeBoolean(base.redstoneInverted());
        buf.writeBoolean(base.salvo());
    }

    /** Client side; slot contents and data arrive through the normal menu sync. */
    public TurretMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), buf.readEnum(TurretTier.class), buf.readBoolean(), buf);
    }

    public TurretMenu(int id, Inventory playerInventory, TurretBaseBlockEntity base) {
        this(id, playerInventory, base.inventory(), base.getBlockPos(), base.tier(), base.filter().copy(), base.enabled(),
                base.redstoneInverted(), base.salvo(), serverData(base));
    }

    private TurretMenu(int id, Inventory playerInventory, BlockPos pos, TurretTier tier, boolean large, FriendlyByteBuf buf) {
        this(id, playerInventory, new TurretInventory(() -> tier, () -> large, slot -> {}), pos, tier, TargetFilter.read(buf),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), new SimpleContainerData(DATA_COUNT));
    }

    private TurretMenu(int id, Inventory playerInventory, TurretInventory turret, BlockPos pos, TurretTier tier, TargetFilter filter,
                       boolean enabled, boolean redstoneInverted, boolean salvo, ContainerData data) {
        super(BastionMenus.TURRET.get(), id);
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        this.block = playerInventory.player.level().getBlockState(pos).getBlock();
        this.salvo = salvo;
        this.tier = tier;
        this.pos = pos;
        this.turret = turret;
        this.filter = filter;
        this.enabled = enabled;
        this.redstoneInverted = redstoneInverted;
        this.data = data;
        addDataSlots(data);

        addSlot(new TurretSlot(turret, TurretInventory.WEAPON, WEAPON_X, WEAPON_Y));
        for (int i = 0; i < TurretTier.MAX_AMMO_SLOTS; i++) {
            addSlot(new TurretSlot(turret, TurretInventory.AMMO_START + i, AMMO_X + i % 3 * 18, AMMO_Y + i / 3 * 18));
        }
        for (int i = 0; i < TurretTier.MAX_MODIFIER_SLOTS; i++) {
            addSlot(new TurretSlot(turret, TurretInventory.MODIFIER_START + i, MODIFIER_X + i % 2 * 18, MODIFIER_Y + i / 2 * 18));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new PlayerSlot(playerInventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new PlayerSlot(playerInventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        }
    }

    private static ContainerData serverData(TurretBaseBlockEntity base) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_HEALTH -> Math.round(base.health() * 10);
                    case DATA_MAX_HEALTH -> Math.round(base.maxHealth() * 10);
                    case DATA_HEAT -> Math.round(base.heat() * 10);
                    case DATA_MAX_HEAT -> {
                        WeaponData weapon = base.inventory().weaponData();
                        yield weapon == null ? 0 : Math.round(weapon.heat().max() * 10);
                    }
                    case DATA_STATE -> base.state().ordinal();
                    case DATA_ENERGY -> base.energy() / 100;
                    case DATA_MAX_ENERGY -> base.energyCapacity() / 100;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    public TurretTier tier() {
        return tier;
    }

    public BlockPos pos() {
        return pos;
    }

    public TurretInventory turret() {
        return turret;
    }

    public float health() {
        return data.get(DATA_HEALTH) / 10f;
    }

    public float maxHealth() {
        return data.get(DATA_MAX_HEALTH) / 10f;
    }

    public float heat() {
        return data.get(DATA_HEAT) / 10f;
    }

    public float maxHeat() {
        return data.get(DATA_MAX_HEAT) / 10f;
    }

    public TurretState state() {
        return TurretState.byId(data.get(DATA_STATE));
    }

    /** FE in the capacitor, to the nearest hundred; max 0 for weapons that use ammo. */
    public int energy() {
        return data.get(DATA_ENERGY) * 100;
    }

    public int maxEnergy() {
        return data.get(DATA_MAX_ENERGY) * 100;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean fromTurret = index < TurretInventory.SIZE;
        boolean moved = fromTurret
                ? moveItemStackTo(stack, TurretInventory.SIZE, slots.size(), true)
                : moveItemStackTo(stack, 0, TurretInventory.SIZE, false); // slot validity routes weapon / ammo / modifier
        if (!moved) return ItemStack.EMPTY;

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, block);
    }

    private class PlayerSlot extends Slot {
        PlayerSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isActive() {
            return slotsVisible;
        }
    }

    /** Hides itself (no render, no clicks) while the base tier keeps it locked or another tab is open. */
    private class TurretSlot extends SlotItemHandler {
        private final TurretInventory turret;

        TurretSlot(TurretInventory turret, int index, int x, int y) {
            super(turret, index, x, y);
            this.turret = turret;
        }

        @Override
        public boolean isActive() {
            return slotsVisible && turret.isActive(getSlotIndex());
        }

        /**
         * Shift-click merges into or shrinks a stack in place and only calls this; the base must hear it or the change is
         * not saved. The weapon slot holds one item and never changes in place (and a call there would reset the weapon).
         */
        @Override
        public void setChanged() {
            super.setChanged();
            if (getSlotIndex() != TurretInventory.WEAPON) turret.changedInPlace(getSlotIndex());
        }
    }
}
