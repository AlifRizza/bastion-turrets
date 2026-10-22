package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import dev.bastion.turret.ManualControl;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S->C the controlled Missile Launcher's salvo locks (creature ids in press order) whenever they change, for the HUD's brackets. */
public record OverwatchLocks(int[] ids) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarIntArray(ids);
    }

    public static OverwatchLocks decode(FriendlyByteBuf buf) {
        return new OverwatchLocks(buf.readVarIntArray(ManualControl.MAX_LOCKS));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.overwatchLocks(this));
        context.get().setPacketHandled(true);
    }
}
