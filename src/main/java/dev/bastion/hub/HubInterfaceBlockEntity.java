package dev.bastion.hub;

import dev.bastion.client.ControlHubEffects;
import dev.bastion.config.BastionConfig;
import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.menu.ControlHubMenu;
import dev.bastion.registry.BastionBlockEntities;
import dev.bastion.registry.BastionSounds;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Control Hub's state (PLAN "Turret Control Hub"): its FE, the satellite's tier, and the status kept in its block
 * state. Formed, it pays its tier's upkeep from its FE every tick and goes OFFLINE without it.
 */
public class HubInterfaceBlockEntity extends BlockEntity implements MenuProvider {
    /** Ticks between safety re-checks: a part in a chunk that loaded later, a change nothing reported. */
    private static final int RECHECK_INTERVAL = 20;
    private int energy;
    /** The satellite's tier (1-3) when it last formed. */
    private int tier = 1;
    private int recheckTimer;
    /** Client: the status one tick ago (null before the first tick). */
    @Nullable
    private Status seen;

    public HubInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(BastionBlockEntities.HUB_INTERFACE.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(HubInterfaceBlock.FACING);
    }

    public Status status() {
        return getBlockState().getValue(HubInterfaceBlock.STATUS);
    }

    public boolean formed() {
        return status() != Status.UNFORMED;
    }

    public boolean online() {
        return status() == Status.ONLINE;
    }

    public int tier() {
        return tier;
    }

    /** The cube's middle block: the satellite stands on the cube above it and the reach is counted from its chunk. */
    public BlockPos center() {
        return ControlHubStructure.center(worldPosition, facing());
    }

    /** Side of the reach in chunks (1, 3, 7 by default). */
    public int side() {
        return BastionConfig.controlHubRange(tier);
    }

    public int energy() {
        return energy;
    }

    public int capacity() {
        return BastionConfig.CONTROL_HUB_ENERGY_CAPACITY.get();
    }

    /** FE per tick while formed. */
    public int upkeep() {
        return BastionConfig.controlHubUpkeep(tier);
    }

    /** Sets the stored FE (tests and dev scenes; Power Ports go through {@code receive}). */
    public void setEnergy(int amount) {
        energy = Mth.clamp(amount, 0, capacity());
        setChanged();
    }

    /** FE from a Power Port: as much as fits; returns what was taken. */
    public int receive(int amount, boolean simulate) {
        int accepted = Math.max(0, Math.min(amount, capacity() - energy));
        if (!simulate && accepted > 0) {
            energy += accepted;
            setChanged();
        }
        return accepted;
    }

    /** The player may use this hub's screen: formed, and within 8 blocks of the Interface (as for turret GUIs). */
    public boolean usableBy(Player player) {
        return !isRemoved() && formed() && player.level() == level && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64;
    }

    /** Whether {@code pos} is in this hub's reach. */
    public boolean covers(BlockPos pos) {
        return ControlHubStructure.covers(center(), side(), pos);
    }

    /** Whether the turret holds a Hub Connector linked to this hub (PLAN "Hub Connector"). */
    public boolean connected(TurretBaseBlockEntity turret) {
        return level != null && HubConnectorItem.linkedTo(turret.inventory().getStackInSlot(TurretInventory.CONNECTOR), level, worldPosition);
    }

    /**
     * The turret at {@code pos} if the player can reach it through this hub now: online, in reach, loaded, connected to this
     * hub, theirs to open.
     */
    @Nullable
    public TurretBaseBlockEntity reachable(Player player, BlockPos pos) {
        if (!online() || !usableBy(player) || !covers(pos) || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof TurretBaseBlockEntity turret && !turret.isRemoved() && connected(turret)
                && turret.canConfigure(player) ? turret : null;
    }

    /**
     * Every turret in reach connected to this hub that the player may open, in loaded chunks only, nearest the satellite
     * first; none unless online.
     */
    public List<TurretBaseBlockEntity> visibleTurrets(Player player) {
        if (!online() || level == null) return List.of();
        BlockPos center = center();
        int r = side() / 2, cx = center.getX() >> 4, cz = center.getZ() >> 4;
        List<TurretBaseBlockEntity> found = new ArrayList<>();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof TurretBaseBlockEntity turret && !turret.isRemoved() && connected(turret) && turret.canConfigure(player)) found.add(turret);
                }
            }
        }
        found.sort(Comparator.comparingDouble(turret -> turret.getBlockPos().distSqr(center)));
        return found;
    }

    /** Opens the hub's screen; the menu learns the Interface's position from the open data. */
    public void openMenu(ServerPlayer player) {
        NetworkHooks.openScreen(player, this, buf -> buf.writeBlockPos(worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.bastion.control_hub.title");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ControlHubMenu(id, inventory, this);
    }

    /** Checks the layout and forms the hub or breaks it up (a part nearby changed, every second, right after loading). */
    public void recheck() {
        if (!(level instanceof ServerLevel server)) return;
        ControlHubStructure.Result result = ControlHubStructure.check(server, worldPosition, facing());
        if (result.formed()) {
            tier = result.tier();
            ControlHubStructure.form(server, worldPosition, facing());
            if (!formed()) setStatus(energy >= upkeep() ? Status.ONLINE : Status.OFFLINE); // once formed, payUpkeep decides
            ControlHubStructure.setSatellite(server, center(), status()); // a satellite swapped in keeps up
        } else if (formed()) {
            ControlHubStructure.unform(server, worldPosition, facing());
            setStatus(Status.UNFORMED);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HubInterfaceBlockEntity hub) {
        if (--hub.recheckTimer <= 0) {
            hub.recheckTimer = RECHECK_INTERVAL;
            hub.recheck();
        }
        hub.payUpkeep();
    }

    /** Client: a hub seen forming gets its scan and dish flash (not one already formed when its chunk loaded). */
    public static void clientTick(Level level, BlockPos pos, BlockState state, HubInterfaceBlockEntity hub) {
        Status now = state.getValue(HubInterfaceBlock.STATUS);
        if (hub.seen == Status.UNFORMED && now != Status.UNFORMED) {
            Direction facing = state.getValue(HubInterfaceBlock.FACING);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ControlHubEffects.formed(pos, facing));
        }
        hub.seen = now;
    }

    private void payUpkeep() {
        if (!formed()) return;
        int cost = upkeep();
        // OFFLINE waits until it holds a few seconds of upkeep: fed less than its upkeep, it would flip every tick.
        long restart = Math.min(capacity(), (long) cost * BastionConfig.CONTROL_HUB_RESTART_TICKS.get());
        if (energy < cost || (status() == Status.OFFLINE && energy < restart)) {
            setStatus(Status.OFFLINE);
            return;
        }
        if (cost > 0) {
            energy -= cost;
            setChanged();
        }
        setStatus(Status.ONLINE);
    }

    /** Into the block state (model, clients) and onto the satellite; ONLINE and OFFLINE get their sounds. */
    private void setStatus(Status next) {
        BlockState state = getBlockState();
        if (level == null || state.getValue(HubInterfaceBlock.STATUS) == next) return;
        level.setBlock(worldPosition, state.setValue(HubInterfaceBlock.STATUS, next), Block.UPDATE_CLIENTS);
        ControlHubStructure.setSatellite(level, center(), next);
        if (next == Status.ONLINE) level.playSound(null, worldPosition, BastionSounds.CONTROL_HUB_ONLINE.get(), SoundSource.BLOCKS, 1f, 1f);
        else if (next == Status.OFFLINE) level.playSound(null, worldPosition, BastionSounds.CONTROL_HUB_OFFLINE.get(), SoundSource.BLOCKS, 1f, 1f);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (energy > 0) tag.putInt("Energy", energy);
        tag.putInt("Tier", tier);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy = tag.getInt("Energy");
        tier = Mth.clamp(tag.getInt("Tier"), 1, 3);
    }
}
