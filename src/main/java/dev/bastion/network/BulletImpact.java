package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S->C where a turret bullet ended (dev.bastion.weapon.Bullets): the client stops the bullet's tracer there and plays
 * the impact. A bullet that just runs out of range sends nothing. Travels in TurretEvents.
 *
 * @param hit TurretFireEvent.BLOCK or ENTITY; {@code blockState} as in TurretFireEvent.Shot
 */
public record BulletImpact(int bullet, Vec3 end, Vec3 normal, int hit, int blockState) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(bullet);
        buf.writeDouble(end.x);
        buf.writeDouble(end.y);
        buf.writeDouble(end.z);
        buf.writeByte((int) Math.round(normal.x * 127));
        buf.writeByte((int) Math.round(normal.y * 127));
        buf.writeByte((int) Math.round(normal.z * 127));
        buf.writeByte(hit);
        buf.writeVarInt(blockState);
    }

    public static BulletImpact decode(FriendlyByteBuf buf) {
        int bullet = buf.readVarInt();
        Vec3 end = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 normal = new Vec3(buf.readByte() / 127.0, buf.readByte() / 127.0, buf.readByte() / 127.0);
        return new BulletImpact(bullet, end, normal, buf.readByte(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.bulletImpact(this));
        context.get().setPacketHandled(true);
    }
}
