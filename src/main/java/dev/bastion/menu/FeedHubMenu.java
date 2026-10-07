package dev.bastion.menu;

import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionMenus;
import dev.bastion.turret.FeedHubBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feed Hub screen (PLAN "Feed Hub", 2b): Summary (size, turrets, ammo totals, FE; numbers over ContainerData) and
 * Storage, 4 rows of the structure's slots that scroll. The server's slots look into the structure from the scrolled
 * row (clickMenuButton sets it); the client's are a plain mirror of what the server sends, so a scroll never shows
 * stale client copies. Shift-click from the inventory fills the whole structure, not only the rows on screen.
 */
public class FeedHubMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 9, ROWS = 4, VISIBLE = COLUMNS * ROWS;
    public static final int STORAGE_X = 8, STORAGE_Y = 18, PLAYER_INV_X = 8, PLAYER_INV_Y = 112;
    /** Ammo types the summary lists. */
    public static final int TYPES = 8;
    // Data slots, each int as two 16-bit halves (ContainerData goes out as shorts).
    private static final int DATA_ENERGY = 0, DATA_CAPACITY = 2, DATA_TYPES = 4, DATA_COUNT = DATA_TYPES + TYPES * 4;

    /** One ammo type in the structure and how much of it there is. */
    public record AmmoTotal(ItemStack item, int count) {
    }

    @Nullable
    private final FeedHubBlockEntity hub;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    /** Where shift-clicked items go: the structure on the server, the visible rows on the client (the server's result syncs back). */
    private final IItemHandler storage;
    /** First storage row shown. */
    private int scroll;
    /** Client: false on the Summary tab, which hides every slot. */
    public boolean slotsVisible = true;

    public FeedHubMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos());
    }

    private FeedHubMenu(int id, Inventory playerInventory, BlockPos pos) {
        this(id, playerInventory, playerInventory.player.level().getBlockEntity(pos) instanceof FeedHubBlockEntity hub ? hub : null, pos);
    }

    public FeedHubMenu(int id, Inventory playerInventory, FeedHubBlockEntity hub) {
        this(id, playerInventory, hub, hub.getBlockPos());
    }

    private FeedHubMenu(int id, Inventory playerInventory, @Nullable FeedHubBlockEntity hub, BlockPos pos) {
        super(BastionMenus.FEED_HUB.get(), id);
        this.hub = hub;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        IItemHandlerModifiable shown;
        if (hub != null && !playerInventory.player.level().isClientSide) {
            storage = hub.storage();
            shown = new Window(hub.storage());
            data = new Summary(hub);
        } else {
            ItemStackHandler mirror = new ItemStackHandler(VISIBLE) {
                @Override
                public boolean isItemValid(int slot, ItemStack stack) {
                    return hub != null && hub.takes(stack); // refuses on the client what the server would refuse
                }
            };
            storage = mirror;
            shown = mirror;
            data = new SimpleContainerData(DATA_COUNT);
        }
        for (int i = 0; i < VISIBLE; i++) addSlot(new StorageSlot(shown, i, STORAGE_X + i % COLUMNS * 18, STORAGE_Y + i / COLUMNS * 18));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new PlayerSlot(playerInventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new PlayerSlot(playerInventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        addDataSlots(data);
    }

    /** The hub this screen was opened on (null if its block entity was not loaded on this client). */
    @Nullable
    public FeedHubBlockEntity hub() {
        return hub;
    }

    /** First storage row shown; follows the structure when it shrinks under an open screen. */
    public int scroll() {
        return Math.min(scroll, maxScroll());
    }

    /** Slots in the structure (0 if its block entity is not loaded here). */
    private int totalSlots() {
        return hub == null ? 0 : hub.group().items().getSlots();
    }

    public int maxScroll() {
        return Math.max(0, Mth.positiveCeilDiv(totalSlots(), COLUMNS) - ROWS);
    }

    /** Client: moves the scrollbar at once; the screen then tells the server the same row as a button id. */
    public void setScroll(int row) {
        scroll = Mth.clamp(row, 0, maxScroll());
    }

    @Override
    public boolean clickMenuButton(Player player, int row) {
        setScroll(row);
        return true; // the server then broadcasts the slots of the new rows
    }

    public int energy() {
        return read(DATA_ENERGY);
    }

    public int capacity() {
        return read(DATA_CAPACITY);
    }

    /** Ammo in the structure, the most first (at most TYPES kinds). */
    public List<AmmoTotal> ammo() {
        List<AmmoTotal> ammo = new ArrayList<>();
        for (int k = 0; k < TYPES; k++) {
            int id = read(DATA_TYPES + k * 4);
            if (id > 0) ammo.add(new AmmoTotal(new ItemStack(BuiltInRegistries.ITEM.byId(id - 1)), read(DATA_TYPES + k * 4 + 2)));
        }
        return ammo;
    }

    private int read(int index) {
        return data.get(index) & 0xFFFF | (data.get(index + 1) & 0xFFFF) << 16;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < VISIBLE) {
            if (!moveItemStackTo(stack, VISIBLE, slots.size(), true)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.set(stack); // setChanged alone never reaches the hub: the stack it shrank would come back on reload
            return original;
        }
        ItemStack rest = ItemHandlerHelper.insertItem(storage, stack, false); // refuses what no mounted turret takes
        if (rest.getCount() == stack.getCount()) return ItemStack.EMPTY;
        slot.setByPlayer(rest);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, BastionBlocks.FEED_HUB.get());
    }

    /** Hidden on the Summary tab and past the structure's last slot. */
    private class StorageSlot extends SlotItemHandler {
        StorageSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean isActive() {
            return slotsVisible && scroll() * COLUMNS + getSlotIndex() < totalSlots();
        }
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

    /** Server: the visible rows of the structure's slots, from the scrolled row on. */
    private final class Window implements IItemHandlerModifiable {
        private final IItemHandlerModifiable all;

        Window(IItemHandlerModifiable all) {
            this.all = all;
        }

        private int index(int slot) {
            return scroll() * COLUMNS + slot;
        }

        private boolean exists(int slot) {
            return index(slot) < all.getSlots();
        }

        @Override
        public int getSlots() {
            return VISIBLE;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return exists(slot) ? all.getStackInSlot(index(slot)) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return exists(slot) ? all.insertItem(index(slot), stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return exists(slot) ? all.extractItem(index(slot), amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return exists(slot) ? all.getSlotLimit(index(slot)) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return exists(slot) && all.isItemValid(index(slot), stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (exists(slot)) all.setStackInSlot(index(slot), stack);
        }
    }

    /** Server: the summary numbers, worked out once per tick. */
    private static final class Summary implements ContainerData {
        private final FeedHubBlockEntity hub;
        private final int[] values = new int[DATA_COUNT];
        private long tick = -1;

        Summary(FeedHubBlockEntity hub) {
            this.hub = hub;
        }

        @Override
        public int get(int index) {
            long now = hub.getLevel().getGameTime();
            if (now != tick) {
                tick = now;
                refresh();
            }
            return values[index];
        }

        private void refresh() {
            FeedHubBlockEntity.Group group = hub.group();
            put(DATA_ENERGY, group.energy());
            put(DATA_CAPACITY, group.capacity());
            Map<Item, Integer> totals = new HashMap<>();
            IItemHandler items = group.items();
            for (int i = 0; i < items.getSlots(); i++) {
                ItemStack stack = items.getStackInSlot(i);
                if (!stack.isEmpty()) totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
            List<Map.Entry<Item, Integer>> most = totals.entrySet().stream()
                    .sorted(Map.Entry.<Item, Integer>comparingByValue().reversed()).limit(TYPES).toList();
            for (int k = 0; k < TYPES; k++) {
                boolean shown = k < most.size();
                put(DATA_TYPES + k * 4, shown ? BuiltInRegistries.ITEM.getId(most.get(k).getKey()) + 1 : 0);
                put(DATA_TYPES + k * 4 + 2, shown ? most.get(k).getValue() : 0);
            }
        }

        private void put(int index, int value) {
            values[index] = value & 0xFFFF;
            values[index + 1] = value >>> 16;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
