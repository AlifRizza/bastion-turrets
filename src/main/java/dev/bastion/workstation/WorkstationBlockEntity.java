package dev.bastion.workstation;

import dev.bastion.client.WorkstationEffects;
import dev.bastion.config.BastionConfig;
import dev.bastion.registry.BastionBlockEntities;
import dev.bastion.registry.BastionRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The core of a workstation (PLAN Fase 9): six input slots and an output, an FE buffer, the selected recipe and the
 * progress on it. Processing: while the inputs hold the selected recipe's ingredients, the output has room and there is
 * energy, it draws FE every tick and finishes after the recipe's time. Instant stations also craft at once for a player
 * (inputs first, then the player's inventory). Automation (any side, any block of the station) may only insert
 * ingredients of the selected recipe and only take from the output.
 */
public class WorkstationBlockEntity extends BlockEntity implements GeoBlockEntity, MenuProvider {
    public static final int INPUTS = 6, OUTPUT = 6, SLOTS = 7;
    /** Block event id: a craft just finished (clients play the craft clip, sound and VFX). */
    private static final int CRAFTED = 1;

    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);
    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler automation = new Automation();
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> automation);
    private final IEnergyStorage energyStorage = new Energy();
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> energyStorage);
    private int energy, progress;
    @Nullable
    private ResourceLocation recipeId;
    /** Processing this tick (server), as last told (client). */
    private boolean working, syncedWorking, clientWorking;

    public WorkstationBlockEntity(BlockPos pos, BlockState state) {
        super(BastionBlockEntities.WORKSTATION.get(), pos, state);
    }

    public WorkstationType type() {
        return getBlockState().getBlock() instanceof WorkstationBlock block ? block.type() : WorkstationType.PART_WORKSTATION;
    }

    public ItemStackHandler items() {
        return items;
    }

    public int energy() {
        return energy;
    }

    public static int capacity() {
        return BastionConfig.WORKSTATION_ENERGY_CAPACITY.get();
    }

    public int progress() {
        return progress;
    }

    /** Processing right now; on the client, as last synced. */
    public boolean working() {
        return level != null && level.isClientSide ? clientWorking : working;
    }

    @Nullable
    public ResourceLocation recipeId() {
        return recipeId;
    }

    /** The selected recipe, if it still exists and belongs to this station. */
    @Nullable
    public WorkstationRecipe recipe() {
        if (recipeId == null || level == null) return null;
        return level.getRecipeManager().byKey(recipeId).filter(r -> r instanceof WorkstationRecipe w && w.station() == type())
                .map(r -> (WorkstationRecipe) r).orElse(null);
    }

    /** Every recipe of one station, by tab (category) then by id. */
    public static List<WorkstationRecipe> recipes(Level level, WorkstationType type) {
        return level.getRecipeManager().getAllRecipesFor(BastionRecipes.WORKSTATION.get()).stream()
                .filter(r -> r.station() == type)
                .sorted(Comparator.comparing((WorkstationRecipe r) -> r.category().toString()).thenComparing(r -> r.id().toString()))
                .toList();
    }

    public void selectRecipe(ResourceLocation id) {
        if (id.equals(recipeId) || level == null) return;
        if (!(level.getRecipeManager().byKey(id).orElse(null) instanceof WorkstationRecipe recipe) || recipe.station() != type()) return;
        recipeId = id;
        progress = 0;
        setChanged();
        sync();
    }

    // --- processing ---------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, WorkstationBlockEntity station) {
        station.tickServer();
    }

    private void tickServer() {
        WorkstationRecipe recipe = recipe();
        boolean ready = recipe != null && available(recipe, inputs()) && outputFits(recipe.result());
        if (!ready) progress = 0;
        working = ready && energy >= recipe.energyPerTick();
        if (working) {
            energy -= recipe.energyPerTick();
            if (++progress >= recipe.time()) {
                take(recipe, inputs());
                items.insertItem(OUTPUT, recipe.result().copy(), false);
                progress = 0;
                crafted();
            }
            setChanged();
        }
        if (working != syncedWorking) {
            syncedWorking = working;
            sync();
        }
    }

    /**
     * Instant stations: crafts the selected recipe up to {@code times} times for the player, taking ingredients from the
     * input slots first and then the player's inventory, paying the full FE each time. The result goes to the player.
     */
    public void instantCraft(ServerPlayer player, int times) {
        WorkstationRecipe recipe = recipe();
        if (recipe == null || !type().instant) return;
        int done = 0;
        while (done < times && energy >= recipe.energy() && available(recipe, sources(player))) {
            take(recipe, sources(player));
            energy -= recipe.energy();
            ItemStack result = recipe.result().copy();
            if (!player.getInventory().add(result)) player.drop(result, false);
            done++;
        }
        if (done > 0) {
            setChanged();
            crafted();
        }
    }

    /** Input slot stacks, live (taking from them changes the station). */
    private List<ItemStack> inputs() {
        List<ItemStack> stacks = new ArrayList<>(INPUTS);
        for (int i = 0; i < INPUTS; i++) stacks.add(items.getStackInSlot(i));
        return stacks;
    }

    /** Inputs, then the player's main inventory. */
    private List<ItemStack> sources(Player player) {
        List<ItemStack> stacks = inputs();
        stacks.addAll(player.getInventory().items);
        return stacks;
    }

    /** How many items matching {@code counted} the stacks hold. */
    public static int count(WorkstationRecipe.Counted counted, List<ItemStack> stacks) {
        int n = 0;
        for (ItemStack stack : stacks) if (counted.ingredient().test(stack)) n += stack.getCount();
        return n;
    }

    public static boolean available(WorkstationRecipe recipe, List<ItemStack> stacks) {
        return recipe.ingredients().stream().allMatch(c -> count(c, stacks) >= c.count());
    }

    private void take(WorkstationRecipe recipe, List<ItemStack> stacks) {
        for (WorkstationRecipe.Counted counted : recipe.ingredients()) {
            int left = counted.count();
            for (ItemStack stack : stacks) {
                if (left <= 0) break;
                if (!counted.ingredient().test(stack)) continue;
                int n = Math.min(left, stack.getCount());
                stack.shrink(n);
                left -= n;
            }
        }
        setChanged();
    }

    private boolean outputFits(ItemStack result) {
        return items.insertItem(OUTPUT, result.copy(), true).isEmpty();
    }

    /** Whether automation may put this into the inputs: an ingredient of the selected recipe. */
    public boolean accepts(ItemStack stack) {
        WorkstationRecipe recipe = recipe();
        return recipe != null && recipe.ingredients().stream().anyMatch(c -> c.ingredient().test(stack));
    }

    private void crafted() {
        if (level != null) level.blockEvent(worldPosition, getBlockState().getBlock(), CRAFTED, 0);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != CRAFTED) return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) {
            triggerAnim("action", "craft");
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WorkstationEffects.crafted(this));
        }
        return true;
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, WorkstationBlockEntity station) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WorkstationEffects.tick(station));
    }

    // --- capabilities -------------------------------------------------------------------------

    private class Automation implements IItemHandler {
        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return slot < INPUTS && accepts(stack) ? items.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == OUTPUT ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot < INPUTS && accepts(stack);
        }
    }

    /** Receive-only, at most workstationMaxInput per transfer. */
    private class Energy implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = Math.max(0, Math.min(capacity() - energy, Math.min(BastionConfig.WORKSTATION_MAX_INPUT.get(), maxReceive)));
            if (accepted > 0 && !simulate) {
                energy += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity();
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
        itemCapability = LazyOptional.of(() -> automation);
        energyCapability = LazyOptional.of(() -> energyStorage);
    }

    // --- persistence & sync -------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        if (energy > 0) tag.putInt("Energy", energy);
        if (progress > 0) tag.putInt("Progress", progress);
        if (recipeId != null) tag.putString("Recipe", recipeId.toString());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.deserializeNBT(tag.getCompound("Items"));
        energy = tag.getInt("Energy");
        progress = tag.getInt("Progress");
        recipeId = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
        if (tag.contains("Working")) clientWorking = tag.getBoolean("Working"); // only in the client update tag
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = saveWithoutMetadata();
        tag.putBoolean("Working", working);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The model spans the whole multiblock. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.5);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new WorkstationMenu(id, playerInventory, this);
    }

    // --- animation ----------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String prefix = "animation." + type().id() + ".";
        RawAnimation idle = RawAnimation.begin().thenLoop(prefix + "idle"), work = RawAnimation.begin().thenLoop(prefix + "working");
        controllers.add(new AnimationController<>(this, "main", 6, state -> state.setAndContinue(working() ? work : idle)));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("craft", RawAnimation.begin().thenPlay(prefix + "craft")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animations;
    }
}
