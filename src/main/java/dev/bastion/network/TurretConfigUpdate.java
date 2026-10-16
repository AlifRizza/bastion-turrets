package dev.bastion.network;

import dev.bastion.menu.TurretMenu;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.targeting.TargetFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C->S settings from the turret GUI (PLAN 4.7, 4.8). Always the full absolute state, never a toggle, so a
 * duplicated or reordered packet cannot flip anything. The server checks that the sender's open turret GUI is still valid
 * and owner/trusted access.
 */
public record TurretConfigUpdate(BlockPos pos, TargetFilter filter, boolean enabled, boolean redstoneInverted, boolean salvo) {
    public static final int MAX_TRUSTED = 32, MAX_RULES = 128;

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
        if (player != null) apply(player);
        context.get().setPacketHandled(true);
    }

    /**
     * Applies the settings when the sender has this turret's GUI open and can still use it, at the turret or through a
     * Control Hub (TurretMenu#stillValid); the reach check that used to live here is the GUI's own.
     */
    public boolean apply(ServerPlayer player) {
        if (!(player.containerMenu instanceof TurretMenu menu) || !menu.pos().equals(pos) || !menu.stillValid(player)) return false;
        if (!(player.level().getBlockEntity(pos) instanceof TurretBaseBlockEntity turret) || !turret.canConfigure(player)) return false;
        if (filter.trusted.size() > MAX_TRUSTED || filter.rules.size() > MAX_RULES) return false;
        turret.setFilter(filter);
        turret.setEnabled(enabled);
        turret.setRedstoneInverted(redstoneInverted);
        turret.setSalvo(salvo);
        return true;
    }
}
