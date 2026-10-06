package dev.bastion.network;

import dev.bastion.client.ClientTurret;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S->C Machine Gun barrel spin 0..1 (PLAN 4.8), only sent on a significant change. */
public record SpinSync(BlockPos pos, float spin) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeFloat(spin);
    }

    public static SpinSync decode(FriendlyByteBuf buf) {
        return new SpinSync(buf.readBlockPos(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientTurret.of(pos).spinTarget = spin);
        context.get().setPacketHandled(true);
    }
}
