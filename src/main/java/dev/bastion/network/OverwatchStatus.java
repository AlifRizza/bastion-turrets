package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import dev.bastion.menu.ControlHubMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S->C the controlled turret as the hub lists it, every half second: the HUD's ammo and FE (the client has no live count). */
public record OverwatchStatus(ControlHubMenu.Entry entry) {
    public void encode(FriendlyByteBuf buf) {
        entry.write(buf);
    }

    public static OverwatchStatus decode(FriendlyByteBuf buf) {
        return new OverwatchStatus(ControlHubMenu.Entry.read(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.overwatchStatus(this));
        context.get().setPacketHandled(true);
    }
}
