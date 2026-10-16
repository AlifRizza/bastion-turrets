package dev.bastion.hub;

import dev.bastion.config.BastionConfig;
import dev.bastion.registry.BastionBlockEntities;
import dev.bastion.turret.FeedHubBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Hub Power Port: receive-only FE on every side, passed straight into its formed hub's Interface, at most
 * controlHubPortMaxInput per tick per port. A port in no hub takes nothing; the hub never gives FE back.
 */
public class HubPowerPortBlockEntity extends BlockEntity {
    private long tick = -1;
    private int received;
    private final IEnergyStorage storage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            HubInterfaceBlockEntity hub = hub();
            if (hub == null || level == null) return 0;
            long now = level.getGameTime();
            if (now != tick) {
                tick = now;
                received = 0;
            }
            int taken = hub.receive(Math.min(maxReceive, BastionConfig.CONTROL_HUB_PORT_MAX_INPUT.get() - received), simulate);
            if (!simulate) received += taken;
            return taken;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            HubInterfaceBlockEntity hub = hub();
            return hub == null ? 0 : hub.energy();
        }

        @Override
        public int getMaxEnergyStored() {
            HubInterfaceBlockEntity hub = hub();
            return hub == null ? 0 : hub.capacity();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return hub() != null;
        }
    };
    private LazyOptional<IEnergyStorage> capability = LazyOptional.of(() -> storage);

    public HubPowerPortBlockEntity(BlockPos pos, BlockState state) {
        super(BastionBlockEntities.HUB_POWER_PORT.get(), pos, state);
    }

    /** The formed hub this port is part of, or null. */
    @Nullable
    private HubInterfaceBlockEntity hub() {
        BlockState state = getBlockState();
        if (level == null || state.getValue(HubCasingBlock.X) == Part.ALONE) return null;
        return ControlHubStructure.hubAt(level, ControlHubStructure.partCenter(worldPosition, state));
    }

    @Override
    public <C> LazyOptional<C> getCapability(Capability<C> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return capability.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        capability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        capability = LazyOptional.of(() -> storage);
    }
}
