package dev.bastion.workstation;

import dev.bastion.registry.BastionMenus;
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
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Workstation container: menu slots 0-5 are the inputs, 6 the output (take only), then the player inventory. Progress,
 * the recipe's time and energy ride on {@link #data}; the selected recipe comes with the block entity's update tag.
 */
public class WorkstationMenu extends AbstractContainerMenu {
    // GUI-space positions, drawn by WorkstationScreen and tools/gen_textures.py workstation_gui().
    public static final int INPUT_X = 9, INPUT_Y = 131, OUTPUT_X = 155, OUTPUT_Y = 131, PLAYER_X = 48, PLAYER_Y = 156;
    // Energy in hundreds of FE: data slots carry 16-bit values.
    public static final int DATA_PROGRESS = 0, DATA_TIME = 1, DATA_ENERGY = 2, DATA_CAPACITY = 3, DATA_COUNT = 4;

    private final BlockPos pos;
    private final WorkstationType type;
    private final ContainerLevelAccess access;
    private final Block block;
    private final ContainerData data;

    public static void writeOpenData(FriendlyByteBuf buf, WorkstationBlockEntity station) {
        buf.writeBlockPos(station.getBlockPos());
        buf.writeEnum(station.type());
    }

    /** Client side; contents and data arrive through the normal menu sync. */
    public WorkstationMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, new ItemStackHandler(WorkstationBlockEntity.SLOTS), buf.readBlockPos(), buf.readEnum(WorkstationType.class),
                new SimpleContainerData(DATA_COUNT));
    }

    public WorkstationMenu(int id, Inventory playerInventory, WorkstationBlockEntity station) {
        this(id, playerInventory, station.items(), station.getBlockPos(), station.type(), serverData(station));
    }

    private WorkstationMenu(int id, Inventory playerInventory, IItemHandler items, BlockPos pos, WorkstationType type, ContainerData data) {
        super(BastionMenus.WORKSTATION.get(), id);
        this.pos = pos;
        this.type = type;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        this.block = playerInventory.player.level().getBlockState(pos).getBlock();
        this.data = data;
        addDataSlots(data);
        for (int i = 0; i < WorkstationBlockEntity.INPUTS; i++) addSlot(new SlotItemHandler(items, i, INPUT_X + i * 18, INPUT_Y));
        addSlot(new SlotItemHandler(items, WorkstationBlockEntity.OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col + row * 9 + 9, PLAYER_X + col * 18, PLAYER_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, PLAYER_X + col * 18, PLAYER_Y + 58));
    }

    private static ContainerData serverData(WorkstationBlockEntity station) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                WorkstationRecipe recipe = station.recipe();
                return switch (index) {
                    case DATA_PROGRESS -> station.progress();
                    case DATA_TIME -> recipe == null ? 0 : recipe.time();
                    case DATA_ENERGY -> station.energy() / 100;
                    case DATA_CAPACITY -> WorkstationBlockEntity.capacity() / 100;
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

    public BlockPos pos() {
        return pos;
    }

    public WorkstationType type() {
        return type;
    }

    /** 0..1 through the current craft. */
    public float progress() {
        int time = data.get(DATA_TIME);
        return time <= 0 ? 0 : Math.min(1f, (float) data.get(DATA_PROGRESS) / time);
    }

    public int energy() {
        return data.get(DATA_ENERGY) * 100;
    }

    public int capacity() {
        return data.get(DATA_CAPACITY) * 100;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int stationSlots = WorkstationBlockEntity.SLOTS;
        boolean moved = index < stationSlots
                ? moveItemStackTo(stack, stationSlots, slots.size(), true)
                : moveItemStackTo(stack, 0, WorkstationBlockEntity.INPUTS, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, block);
    }
}
