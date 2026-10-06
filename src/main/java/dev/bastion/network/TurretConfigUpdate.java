package dev.bastion.network;

import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.targeting.TargetFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C->S settings from the turret GUI (PLAN 4.7, 4.8). Always the full absolute state, never a toggle, so a
 * duplicated or reordered packet cannot flip anything. The server checks distance and owner/trusted access.
 */
public record TurretConfigUpdate(BlockPos pos, TargetFilter filter, boolean enabled, boolean redstoneInverted, boolean salvo) {
    private static final double MAX_DISTANCE_SQR = 8 * 8;
    private static final int MAX_TRUSTED = 32, MAX_RULES = 128;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        filter.write(buf);
        buf.writeBoolean(enabled);
        buf.writeBoolean(redstoneInverted);
        buf.writeBoolean(salvo);
    }

    public static TurretConfigUpdate decode(FriendlyByteBuf buf) {
        return new TurretConfigUpdate(buf.readBlockPos(), TargetFilter.read(buf), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.distanceToSqr(pos.getCenter()) <= MAX_DISTANCE_SQR
                && player.level().getBlockEntity(pos) instanceof TurretBaseBlockEntity turret && turret.canConfigure(player)
                && filter.trusted.size() <= MAX_TRUSTED && filter.rules.size() <= MAX_RULES) {
            turret.setFilter(filter);
            turret.setEnabled(enabled);
            turret.setRedstoneInverted(redstoneInverted);
            turret.setSalvo(salvo);
        }
        context.get().setPacketHandled(true);
    }
}
