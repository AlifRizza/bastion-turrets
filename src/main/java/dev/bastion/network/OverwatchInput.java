package dev.bastion.network;

import dev.bastion.hub.OverwatchSessions;
import dev.bastion.turret.ManualControl;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C->S every client tick under Overwatch: the spot under the crosshair, the creature on it (-1: none), the trigger, and
 * the attack and use presses since the last one (launchers fire or lock per press).
 */
public record OverwatchInput(BlockPos turret, Vec3 point, int entity, boolean trigger, int clicks, int unlocks) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(turret);
        buf.writeDouble(point.x);
        buf.writeDouble(point.y);
        buf.writeDouble(point.z);
        buf.writeVarInt(entity);
        buf.writeBoolean(trigger);
        buf.writeVarInt(clicks);
        buf.writeVarInt(unlocks);
    }

    public static OverwatchInput decode(FriendlyByteBuf buf) {
        return new OverwatchInput(buf.readBlockPos(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt(), buf.readBoolean(),
                Mth.clamp(buf.readVarInt(), 0, ManualControl.MAX_PENDING), Mth.clamp(buf.readVarInt(), 0, ManualControl.MAX_PENDING));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) OverwatchSessions.input(player, turret, point, entity, trigger, clicks, unlocks);
        context.get().setPacketHandled(true);
    }
}
