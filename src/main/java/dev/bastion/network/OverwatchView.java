package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * S->C an Overwatch session began (the turret and the hub it goes through) or ended (both null), with the server's range
 * multiplier under control (its config is not the client's), so the crosshair and the Mortar arc reach as far as the shots.
 */
public record OverwatchView(@Nullable BlockPos turret, @Nullable BlockPos hub, float rangeScale) {
    public static final OverwatchView NONE = new OverwatchView(null, null, 1);

    public void encode(FriendlyByteBuf buf) {
        buf.writeNullable(turret, FriendlyByteBuf::writeBlockPos);
        buf.writeNullable(hub, FriendlyByteBuf::writeBlockPos);
        buf.writeFloat(rangeScale);
    }

    public static OverwatchView decode(FriendlyByteBuf buf) {
        return new OverwatchView(buf.readNullable(FriendlyByteBuf::readBlockPos), buf.readNullable(FriendlyByteBuf::readBlockPos), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.overwatchView(this));
        context.get().setPacketHandled(true);
    }
}
