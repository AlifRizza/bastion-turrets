package dev.bastion.turret;

import dev.bastion.config.BastionConfig;
import dev.bastion.menu.FeedHubMenu;
import dev.bastion.registry.BastionBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Feed Hub (user idea 2026-10-07, a simple stand-in for the postponed Base Extender): standard turret bases mount on its
 * faces (their back on the hub), and ammo piped, belted or armed into it goes to those turrets whose weapon takes it,
 * always the one with the least of it first, so they fill evenly. It only takes ammo one of its armed turrets can use.
 * Hubs placed together merge (FeedHubStructure, PLAN "Feed Hub" multiblock): the structure shares its slots, its FE
 * and its turrets, large bases standing on top included, through any of its blocks; each block still keeps its own 9
 * slots and FE, so breaking one drops only its share (its FE is lost). FE comes in from cables on any side and goes out
 * only to the energy turrets.
 */
public class FeedHubBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOTS = 9;

    private final ItemStackHandler buffer = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return takes(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandlerModifiable storage = new StorageView();
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> storage);
    /** FE in this block; the structure fills and drains its blocks in slot order. */
    private int energy;
    private final IEnergyStorage energyStorage = new EnergyView();
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> energyStorage);
    /** This tick's structure, shared with the controller's (null until first asked). */
    @Nullable
    private Group group;

    /** A turret mounted on the structure: the handler that reaches its ammo slots and its FE storage (null if none). */
    public record Feed(TurretBaseBlockEntity turret, IItemHandler ammo, @Nullable IEnergyStorage energy) {
        static Optional<Feed> of(TurretBaseBlockEntity turret, Direction side) {
            return turret.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve()
                    .map(ammo -> new Feed(turret, ammo, turret.getCapability(ForgeCapabilities.ENERGY, side).resolve().orElse(null)));
        }

        /** Its weapon fires this (a turret without a weapon takes nothing from the hub). */
        public boolean takes(ItemStack stack) {
            return turret.inventory().weaponData() != null && turret.inventory().isAmmo(stack);
        }

        /** How much ammo for its weapon it holds now. */
        int stock() {
            int total = 0;
            for (int i = 0; i < ammo.getSlots(); i++) {
                ItemStack stack = ammo.getStackInSlot(i);
                if (turret.inventory().isAmmo(stack)) total += stack.getCount();
            }
            return total;
        }
    }

    /**
     * One structure as of one tick: its loaded blocks in slot order, their slots as one handler, the turrets on it.
     * Built by the structure's lowest block and shared by all; rebuilt the next tick or once a block is gone.
     */
    public static final class Group {
        private final FeedHubStructure.Box box;
        private final List<FeedHubBlockEntity> members = new ArrayList<>();
        private final IItemHandlerModifiable items;
        private final List<Feed> feeds;
        private final long tick;
        /** FE accepted this tick (a Group lives one tick), against the structure's per-tick input. */
        private int received;

        private Group(Level level, FeedHubStructure.Box box, FeedHubBlockEntity owner, long tick) {
            this.box = box;
            this.tick = tick;
            for (BlockPos pos : box.positions()) {
                if (pos.equals(owner.worldPosition)) members.add(owner);
                else if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof FeedHubBlockEntity hub) members.add(hub);
            }
            items = new CombinedInvWrapper(members.stream().map(hub -> hub.buffer).toArray(IItemHandlerModifiable[]::new));
            feeds = findFeeds(level, box, members);
        }

        public FeedHubStructure.Box box() {
            return box;
        }

        public List<FeedHubBlockEntity> members() {
            return members;
        }

        public IItemHandlerModifiable items() {
            return items;
        }

        public List<Feed> feeds() {
            return feeds;
        }

        public int energy() {
            long sum = 0;
            for (FeedHubBlockEntity member : members) sum += member.energy;
            return (int) Math.min(Integer.MAX_VALUE, sum);
        }

        public int capacity() {
            return (int) Math.min(Integer.MAX_VALUE, (long) members.size() * BastionConfig.FEED_HUB_ENERGY_CAPACITY.get());
        }

        private int receive(int max, boolean simulate) {
            long input = (long) members.size() * BastionConfig.FEED_HUB_MAX_INPUT.get() - received;
            int accepted = (int) Math.max(0, Math.min(Math.min(max, input), (long) capacity() - energy()));
            if (!simulate && accepted > 0) {
                received += accepted;
                fill(accepted);
            }
            return accepted;
        }

        private void fill(int amount) {
            int cap = BastionConfig.FEED_HUB_ENERGY_CAPACITY.get();
            for (FeedHubBlockEntity member : members) {
                int put = Math.min(amount, Math.max(0, cap - member.energy));
                if (put == 0) continue;
                member.energy += put;
                member.setChanged();
                amount -= put;
                if (amount == 0) return;
            }
        }

        private void drain(int amount) {
            for (FeedHubBlockEntity member : members) {
                int take = Math.min(amount, member.energy);
                if (take == 0) continue;
                member.energy -= take;
                member.setChanged();
                amount -= take;
                if (amount == 0) return;
            }
        }

        /** Hands stored FE to the energy turrets, the least charged first, each within its weapon's max_input. */
        private void feedEnergy() {
            int left = energy();
            if (left == 0) return;
            List<Feed> powered = feeds.stream().filter(feed -> feed.energy() != null && feed.energy().canReceive())
                    .sorted(Comparator.comparingDouble(feed -> feed.energy().getEnergyStored() / (double) Math.max(1, feed.energy().getMaxEnergyStored())))
                    .toList();
            for (Feed feed : powered) {
                int given = feed.energy().receiveEnergy(left, false);
                drain(given);
                left -= given;
                if (left == 0) return;
            }
        }

        private boolean stale(long now) {
            return tick != now || members.stream().anyMatch(BlockEntity::isRemoved) || feeds.stream().anyMatch(feed -> feed.turret().isRemoved());
        }

        /**
         * Moves stored ammo one item at a time into the turret that takes it and holds the least of it. Ammo nobody has
         * room for is remembered for the tick, so a full structure costs one check per kind, not per stack and turret.
         */
        private void feedAmmo(int budget) {
            int[] stock = null;
            List<ItemStack> noRoom = new ArrayList<>();
            for (int slot = 0; slot < items.getSlots() && budget > 0; slot++) {
                while (budget > 0 && !items.getStackInSlot(slot).isEmpty()) {
                    ItemStack stored = items.getStackInSlot(slot);
                    if (noRoom.stream().anyMatch(stuck -> ItemStack.isSameItemSameTags(stuck, stored))) break;
                    if (stock == null) stock = feeds.stream().mapToInt(Feed::stock).toArray();
                    ItemStack one = stored.copyWithCount(1);
                    int best = -1;
                    for (int i = 0; i < feeds.size(); i++) {
                        Feed feed = feeds.get(i);
                        if ((best < 0 || stock[i] < stock[best]) && feed.takes(one)
                                && ItemHandlerHelper.insertItem(feed.ammo(), one, true).isEmpty()) best = i;
                    }
                    if (best < 0) { // nobody has room for this one now: it waits
                        noRoom.add(one);
                        break;
                    }
                    ItemHandlerHelper.insertItem(feeds.get(best).ammo(), items.extractItem(slot, 1, false), false);
                    stock[best]++;
                    budget--;
                }
            }
        }
    }

    public FeedHubBlockEntity(BlockPos pos, BlockState state) {
        super(BastionBlockEntities.FEED_HUB.get(), pos, state);
    }

    /** This block's own 9 slots (what drops when it breaks). */
    public ItemStackHandler buffer() {
        return buffer;
    }

    /** All the structure's slots, through this block (capability and GUI). */
    public IItemHandlerModifiable storage() {
        return storage;
    }

    /**
     * This hub's structure this tick. A block runs alone while its structure is not whole and loaded (a block broken
     * this tick, part of it in an unloaded chunk): that never loads a chunk or reaches into another structure.
     */
    public Group group() {
        long now = level.getGameTime();
        if (group == null || group.stale(now)) group = findGroup(now);
        return group;
    }

    private Group findGroup(long now) {
        FeedHubStructure.Box box = FeedHubStructure.Box.of(level, worldPosition, getBlockState());
        if (!box.intact(level)) return new Group(level, FeedHubStructure.Box.single(worldPosition), this, now);
        if (box.min().equals(worldPosition)) return new Group(level, box, this, now);
        // The controller's min is its own position, and each hop goes strictly down-left: no cycle even on odd states.
        if (level.isLoaded(box.min()) && level.getBlockEntity(box.min()) instanceof FeedHubBlockEntity controller) return controller.group();
        return new Group(level, FeedHubStructure.Box.single(worldPosition), this, now);
    }

    /** Standard bases on the structure's outer faces (back on it) and large bases standing with all four blocks on top. */
    private static List<Feed> findFeeds(Level level, FeedHubStructure.Box box, List<FeedHubBlockEntity> members) {
        List<Feed> feeds = new ArrayList<>();
        Set<TurretBaseBlockEntity> large = new HashSet<>();
        for (FeedHubBlockEntity member : members) {
            for (Direction side : Direction.values()) {
                BlockPos at = member.worldPosition.relative(side);
                if (box.contains(at) || !level.isLoaded(at)) continue;
                BlockState state = level.getBlockState(at);
                if (state.getBlock() instanceof LargeTurretBaseBlock) {
                    boolean onTop = side == Direction.UP && LargeTurretBaseBlock.footprint(LargeTurretBaseBlock.footprintMin(state, at)).stream()
                            .allMatch(p -> box.contains(p.below()));
                    TurretBaseBlockEntity core = onTop ? TurretBaseBlock.core(level, at) : null;
                    if (core != null && large.add(core)) Feed.of(core, Direction.DOWN).ifPresent(feeds::add);
                } else if (state.getBlock() instanceof TurretBaseBlock && state.getValue(TurretBaseBlock.FACING) == side
                        && level.getBlockEntity(at) instanceof TurretBaseBlockEntity turret) {
                    Feed.of(turret, side.getOpposite()).ifPresent(feeds::add);
                }
            }
        }
        return feeds;
    }

    /** The turrets this hub's structure feeds (the GUI lists their weapons). */
    public List<Feed> feeds() {
        return group().feeds;
    }

    /** Some armed turret on the structure fires this. */
    public boolean takes(ItemStack stack) {
        return level != null && group().feeds.stream().anyMatch(feed -> feed.takes(stack));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FeedHubBlockEntity hub) {
        Group group = hub.group();
        if (group.members.get(0) != hub) return; // the structure's lowest block runs it for all
        group.feedAmmo(BastionConfig.FEED_HUB_ITEMS_PER_TICK.get() * group.members.size());
        group.feedEnergy();
    }

    @Override
    public <C> LazyOptional<C> getCapability(Capability<C> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
        if (cap == ForgeCapabilities.ENERGY) return energyCapability.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
        energyCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemCapability = LazyOptional.of(() -> storage);
        energyCapability = LazyOptional.of(() -> energyStorage);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Buffer", buffer.serializeNBT());
        if (energy > 0) tag.putInt("Energy", energy);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        buffer.deserializeNBT(tag.getCompound("Buffer"));
        energy = tag.getInt("Energy");
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new FeedHubMenu(id, playerInventory, this);
    }

    /** Delegates to this tick's structure, so a handler cached by a pipe stays right when hubs merge or split. */
    private final class StorageView implements IItemHandlerModifiable {
        private IItemHandlerModifiable items() {
            return group().items;
        }

        @Override
        public int getSlots() {
            return items().getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items().getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return items().insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return items().extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items().getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return items().isItemValid(slot, stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            items().setStackInSlot(slot, stack);
        }
    }

    /** Receive-only FE for the whole structure through this block; the hub never hands FE back to cables. */
    private final class EnergyView implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return group().receive(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return group().energy();
        }

        @Override
        public int getMaxEnergyStored() {
            return group().capacity();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
