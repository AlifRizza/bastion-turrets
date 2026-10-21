package dev.bastion.network;

import dev.bastion.hub.OverwatchSessions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C->S every client tick under Overwatch: the spot under the crosshair, the creature on it (-1: none), the trigger. */
public record OverwatchInput(BlockPos turret, Vec3 point, int entity, boolean trigger) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(turret);
        buf.writeDouble(point.x);
        buf.writeDouble(point.y);
        buf.writeDouble(point.z);
        buf.writeVarInt(entity);
        buf.writeBoolean(trigger);
    }

    public static OverwatchInput decode(FriendlyByteBuf buf) {
        return new OverwatchInput(buf.readBlockPos(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) OverwatchSessions.input(player, turret, point, entity, trigger);
        context.get().setPacketHandled(true);
    }
}
