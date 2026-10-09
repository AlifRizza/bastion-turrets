package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * S->C an Ion Rifle core (IonRifleWeapon): where bolt {@code bullet} stopped, its colour, how long the beams show and the
 * targets it zapped, so clients draw a beam to each once the bolt's tracer arrives. Travels in TurretEvents, queued before
 * the bolt's own BulletImpact.
 */
public record IonField(int bullet, Vec3 core, int color, int ticks, int[] targets) {
    /** Most beams on the wire; a bigger crowd is all damaged, only these get a beam. */
    public static final int MAX_TARGETS = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(bullet);
        buf.writeDouble(core.x).writeDouble(core.y).writeDouble(core.z);
        buf.writeInt(color);
        buf.writeByte(ticks);
        buf.writeVarIntArray(targets.length > MAX_TARGETS ? Arrays.copyOf(targets, MAX_TARGETS) : targets);
    }

    public static IonField decode(FriendlyByteBuf buf) {
        return new IonField(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readInt(), buf.readByte(),
                buf.readVarIntArray(MAX_TARGETS));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.ionField(this));
        context.get().setPacketHandled(true);
    }
}
