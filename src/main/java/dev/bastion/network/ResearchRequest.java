package dev.bastion.network;

import dev.bastion.research.Research;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C->S from the Codex's Research button (PLAN 8 "Riset & Turret Codex"); the server checks everything again. */
public record ResearchRequest(String node) {
    private static final int MAX_ID = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(node, MAX_ID);
    }

    public static ResearchRequest decode(FriendlyByteBuf buf) {
        return new ResearchRequest(buf.readUtf(MAX_ID));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) apply(player);
        context.get().setPacketHandled(true);
    }

    public Research.Outcome apply(ServerPlayer player) {
        return Research.research(player, node);
    }
}
