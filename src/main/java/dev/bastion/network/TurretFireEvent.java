package dev.bastion.network;

import dev.bastion.client.TurretFireEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S->C one weapon discharge, PLAN 4.8: which muzzle fired, the seed for VFX randomness, and every shot's end point
 * with what it hit. Bullets ({@code speed} > 0, blocks per tick): each shot is a bullet's whole path (id in
 * {@code blockState}, hit MISS); the tracer flies it at that speed until a BulletImpact says where the bullet ended.
 */
public record TurretFireEvent(BlockPos pos, ResourceLocation weaponType, int muzzle, long seed, boolean precisionLock,
                              float speed, List<Shot> shots) {
    public static final int MISS = 0, BLOCK = 1, ENTITY = 2;

    /**
     * @param blockState for BLOCK hits: the block-state id, so debris takes the block's colour; for ENTITY hits of the Tesla
     *                   Coil: the entity id, so its bolt follows the target; for bullets: the bullet id
     */
    public record Shot(Vec3 end, Vec3 normal, int hit, int blockState) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeResourceLocation(weaponType);
        buf.writeByte(muzzle);
        buf.writeLong(seed);
        buf.writeBoolean(precisionLock);
        buf.writeFloat(speed);
        buf.writeVarInt(shots.size());
        for (Shot shot : shots) {
            buf.writeDouble(shot.end.x);
            buf.writeDouble(shot.end.y);
            buf.writeDouble(shot.end.z);
            buf.writeByte((int) Math.round(shot.normal.x * 127));
            buf.writeByte((int) Math.round(shot.normal.y * 127));
            buf.writeByte((int) Math.round(shot.normal.z * 127));
            buf.writeByte(shot.hit);
            buf.writeVarInt(shot.blockState);
        }
    }

    public static TurretFireEvent decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        ResourceLocation type = buf.readResourceLocation();
        int muzzle = buf.readByte();
        long seed = buf.readLong();
        boolean lock = buf.readBoolean();
        float speed = buf.readFloat();
        int count = Math.min(buf.readVarInt(), 64);
        List<Shot> shots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Vec3 end = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3 normal = new Vec3(buf.readByte() / 127.0, buf.readByte() / 127.0, buf.readByte() / 127.0);
            shots.add(new Shot(end, normal, buf.readByte(), buf.readVarInt()));
        }
        return new TurretFireEvent(pos, type, muzzle, seed, lock, speed, shots);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TurretFireEffects.onFire(this));
        context.get().setPacketHandled(true);
    }
}
