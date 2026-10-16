package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import dev.bastion.menu.ControlHubMenu;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** S->C the Control Hub screen's data (PLAN "Turret Control Hub"): header, reachable turrets, changed targeting. */
public record HubRoster(int containerId, ControlHubMenu.Header header, List<ControlHubMenu.Entry> turrets, Map<BlockPos, CompoundTag> filters) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        header.write(buf);
        buf.writeCollection(turrets, (b, entry) -> entry.write(b));
        buf.writeMap(filters, FriendlyByteBuf::writeBlockPos, FriendlyByteBuf::writeNbt);
    }

    public static HubRoster decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        ControlHubMenu.Header header = ControlHubMenu.Header.read(buf);
        int count = buf.readVarInt();
        if (count > ControlHubMenu.MAX_TURRETS) throw new DecoderException("too many turrets: " + count);
        List<ControlHubMenu.Entry> turrets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) turrets.add(ControlHubMenu.Entry.read(buf));
        return new HubRoster(id, header, turrets, buf.readMap(FriendlyByteBuf::readBlockPos, FriendlyByteBuf::readNbt));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.hubRoster(this));
        context.get().setPacketHandled(true);
    }
}
