package dev.bastion.compat.jade;

import dev.bastion.Bastion;
import dev.bastion.compat.BlockInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** One Jade provider per kind of Bastion block, each with its own switch in Jade's config; BlockInfo decides the lines. */
public enum BastionJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    TURRET("turret"), WORKSTATION("workstation"), FEED_HUB("feed_hub"), CONTROL_HUB("control_hub");

    private final ResourceLocation uid;

    BastionJadeProvider(String name) {
        uid = Bastion.id(name);
    }

    @Override
    public ResourceLocation getUid() {
        return uid;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        data.put(BlockInfo.TAG, BlockInfo.write(accessor.getBlockEntity()));
    }

    /** A server without Jade sends no data: no Bastion lines then, only Jade's own. */
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockInfo.lines(accessor.getServerData().getCompound(BlockInfo.TAG), accessor.showDetails()).forEach(tooltip::add);
    }
}
