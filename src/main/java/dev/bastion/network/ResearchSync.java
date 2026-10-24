package dev.bastion.network;

import dev.bastion.config.ResearchConfig;
import dev.bastion.research.Research;
import dev.bastion.research.ResearchData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * S->C (PLAN 8 "Riset & Turret Codex"): whether research is on, the world's researched nodes and what each node costs. Sent
 * on login, after every research or admin command, and after the config file changes.
 */
public record ResearchSync(boolean enabled, Set<String> researched, Map<String, List<ItemStack>> costs) {
    public static ResearchSync current(MinecraftServer server) {
        Map<String, List<ItemStack>> costs = new LinkedHashMap<>();
        for (String id : ResearchConfig.COSTS.keySet()) costs.put(id, ResearchConfig.cost(id));
        return new ResearchSync(ResearchConfig.enabled(), Set.copyOf(ResearchData.get(server).all()), costs);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeCollection(researched, FriendlyByteBuf::writeUtf);
        // Item id + varint count: writeItem sends the count as one byte, and a cost may ask for up to 4096.
        buf.writeMap(costs, FriendlyByteBuf::writeUtf, (b, list) -> b.writeCollection(list, (bb, stack) -> {
            bb.writeResourceLocation(ForgeRegistries.ITEMS.getKey(stack.getItem()));
            bb.writeVarInt(stack.getCount());
        }));
    }

    public static ResearchSync decode(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        Set<String> researched = buf.readCollection(HashSet::new, FriendlyByteBuf::readUtf);
        Map<String, List<ItemStack>> costs = buf.readMap(FriendlyByteBuf::readUtf, b -> b.readList(bb -> {
            Item item = ForgeRegistries.ITEMS.getValue(bb.readResourceLocation());
            int count = bb.readVarInt();
            return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
        }));
        return new ResearchSync(enabled, researched, costs);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        Research.acceptClient(this);
        context.get().setPacketHandled(true);
    }
}
