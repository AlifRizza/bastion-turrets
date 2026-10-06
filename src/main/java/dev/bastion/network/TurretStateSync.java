package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import dev.bastion.turret.TurretState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S->C turret state, PLAN 4.8. Sent only when something changed, at most every 2 ticks per turret. */
public record TurretStateSync(BlockPos pos, TurretState state, float yaw, float pitch, int targetId, float heat, float health) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeByte(state.ordinal());
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeVarInt(targetId);
        buf.writeFloat(heat);
        buf.writeFloat(health);
    }

    public static TurretStateSync decode(FriendlyByteBuf buf) {
        return new TurretStateSync(buf.readBlockPos(), TurretState.byId(buf.readByte()), buf.readFloat(), buf.readFloat(),
                buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.turretState(this));
        context.get().setPacketHandled(true);
    }
}
