package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S->C an entity burns from turret flames for {@code ticks} more ticks (0 = put out); clients draw the fire (TurretBurn). */
public record BurnSync(int entityId, int ticks) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(ticks);
    }

    public static BurnSync decode(FriendlyByteBuf buf) {
        return new BurnSync(buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.burn(this));
        context.get().setPacketHandled(true);
    }
}
