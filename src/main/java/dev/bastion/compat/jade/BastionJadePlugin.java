package dev.bastion.compat.jade;

import dev.bastion.Bastion;
import dev.bastion.compat.BlockInfo;
import dev.bastion.hub.HubInterfaceBlock;
import dev.bastion.hub.HubInterfaceBlockEntity;
import dev.bastion.turret.FeedHubBlock;
import dev.bastion.turret.FeedHubBlockEntity;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.workstation.WorkstationBlock;
import dev.bastion.workstation.WorkstationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Jade block info for Bastion (PLAN "Compat JEI & Jade"). Jade finds this class by its annotation; nothing else refers to it. */
@WailaPlugin(Bastion.MOD_ID)
public class BastionJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(BastionJadeProvider.TURRET, TurretBaseBlockEntity.class);
        registration.registerBlockDataProvider(BastionJadeProvider.WORKSTATION, WorkstationBlockEntity.class);
        registration.registerBlockDataProvider(BastionJadeProvider.FEED_HUB, FeedHubBlockEntity.class);
        registration.registerBlockDataProvider(BastionJadeProvider.CONTROL_HUB, HubInterfaceBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(BastionJadeProvider.TURRET, TurretBaseBlock.class);
        registration.registerBlockComponent(BastionJadeProvider.WORKSTATION, WorkstationBlock.class);
        registration.registerBlockComponent(BastionJadeProvider.FEED_HUB, FeedHubBlock.class);
        registration.registerBlockComponent(BastionJadeProvider.CONTROL_HUB, HubInterfaceBlock.class);
        // Looking at a part of a multiblock shows its core (the turret, station or hub Interface), with the core's FE and slots.
        registration.addRayTraceCallback((hit, accessor, original) -> core(registration, accessor));
    }

    private static Accessor<?> core(IWailaClientRegistration registration, @Nullable Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor block)) return accessor;
        Level level = block.getLevel();
        BlockPos core = BlockInfo.corePos(level, block.getPosition(), block.getBlockState());
        if (core == null || core.equals(block.getPosition())) return accessor;
        return registration.blockAccessor().from(block).blockState(level.getBlockState(core)).blockEntity(level.getBlockEntity(core))
                .hit(block.getHitResult().withPosition(core)).build();
    }
}
