package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S->C which launcher tubes hold a missile (Missile Launcher, bit i = tube i), sent when one loads or fires. */
public record RackSync(BlockPos pos, int tubes) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeShort(tubes);
    }

    public static RackSync decode(FriendlyByteBuf buf) {
        return new RackSync(buf.readBlockPos(), buf.readShort() & 0xFFFF);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.rack(this));
        context.get().setPacketHandled(true);
    }
}
