package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S->C one-off events with their own VFX (PLAN 4.8): turret destruction, tier upgrade, rocket explosions.
 *
 * @param normal the face a rocket hit (zero when it burst in the air or hit a mob), for the scorch mark
 * @param source the rocket or missile that exploded (entity id, -1 for other events), whose smoke trail ends here
 */
public record TurretImpactEvent(Vec3 pos, int kind, float scale, Vec3 normal, int source) {
    public TurretImpactEvent(Vec3 pos, int kind, float scale) {
        this(pos, kind, scale, Vec3.ZERO, -1);
    }

    /**
     * FIRE_PATCH: a Mortar's fire patch centred on pos (on the ground); scale = seconds it burns, source = its width in blocks.
     * JAVELIN_BLAST: a Javelin missile landing; scale = blast radius / 3.5 as for rockets. BROOD_LAND / BROOD_OPEN: a
     * Brood capsule coming to rest / opening (source = the capsule); SPIDER_BURST: a Brood spider's death burst, scale = its radius.
     * DRONE_SHOT: a Hive drone's bullet ending at pos (source = the drone, scale 1 on a hit else 0, normal = the face hit);
     * DRONE_KAMIKAZE: a drone's dive bursting at pos, scale = its radius.
     */
    public static final int DESTROYED = 0, TIER_UP = 1, ROCKET_BLAST = 2, MISSILE_BLAST = 3, MORTAR_BLAST = 4, FIRE_PATCH = 5, JAVELIN_BLAST = 6,
            BROOD_LAND = 7, BROOD_OPEN = 8, SPIDER_BURST = 9, DRONE_SHOT = 10, DRONE_KAMIKAZE = 11;

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeByte(kind);
        buf.writeFloat(scale);
        buf.writeByte((int) Math.round(normal.x)).writeByte((int) Math.round(normal.y)).writeByte((int) Math.round(normal.z));
        buf.writeVarInt(source + 1);
    }

    public static TurretImpactEvent decode(FriendlyByteBuf buf) {
        return new TurretImpactEvent(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readByte(), buf.readFloat(),
                new Vec3(buf.readByte(), buf.readByte(), buf.readByte()), buf.readVarInt() - 1);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.turretImpact(this));
        context.get().setPacketHandled(true);
    }
}
