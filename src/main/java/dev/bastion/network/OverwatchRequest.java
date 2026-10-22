package dev.bastion.network;

import dev.bastion.hub.OverwatchSessions;
import dev.bastion.menu.ControlHubMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C->S Overwatch (PLAN "Overwatch Link"): take control of a turret from the open hub screen, switch to the next or previous
 * one, flip a launcher's fire mode, or let go. The server re-checks everything (OverwatchSessions).
 */
public record OverwatchRequest(Kind kind, BlockPos turret, int step) {
    public enum Kind {START, CYCLE, EXIT, MODE}

    public static OverwatchRequest start(BlockPos turret) {
        return new OverwatchRequest(Kind.START, turret, 0);
    }

    public static OverwatchRequest cycle(int step) {
        return new OverwatchRequest(Kind.CYCLE, BlockPos.ZERO, step);
    }

    public static OverwatchRequest exit() {
        return new OverwatchRequest(Kind.EXIT, BlockPos.ZERO, 0);
    }

    public static OverwatchRequest mode() {
        return new OverwatchRequest(Kind.MODE, BlockPos.ZERO, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeBlockPos(turret);
        buf.writeByte(step);
    }

    public static OverwatchRequest decode(FriendlyByteBuf buf) {
        return new OverwatchRequest(buf.readEnum(Kind.class), buf.readBlockPos(), Mth.clamp(buf.readByte(), -1, 1));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            switch (kind) {
                case START -> {
                    if (player.containerMenu instanceof ControlHubMenu menu && menu.stillValid(player)) {
                        Component refusal = OverwatchSessions.start(player, menu.hubPos(), turret);
                        if (refusal != null) player.displayClientMessage(refusal, true);
                    }
                }
                case CYCLE -> OverwatchSessions.cycle(player, step);
                case EXIT -> OverwatchSessions.stop(player);
                case MODE -> OverwatchSessions.toggleMode(player);
            }
        }
        context.get().setPacketHandled(true);
    }
}
