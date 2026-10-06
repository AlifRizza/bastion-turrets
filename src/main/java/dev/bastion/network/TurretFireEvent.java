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
 * S->C one weapon discharge, PLAN 4.8: which muzzle fired, the seed for VFX randomness, and every shot's
 * end point (1 for Gun/MG, 8 for Shotgun) with what it hit. Clients animate tracers to these points and
 * play the impact when each tracer arrives.
 */
public record TurretFireEvent(BlockPos pos, ResourceLocation weaponType, int muzzle, long seed, boolean precisionLock,
                              List<Shot> shots) {
    public static final int MISS = 0, BLOCK = 1, ENTITY = 2;

    /**
     * @param blockState for BLOCK hits: the block-state id, so debris takes the block's colour; for ENTITY hits of the Tesla
     *                   Coil: the entity id, so its bolt follows the target
     */
    public record Shot(Vec3 end, Vec3 normal, int hit, int blockState) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeResourceLocation(weaponType);
        buf.writeByte(muzzle);
        buf.writeLong(seed);
        buf.writeBoolean(precisionLock);
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
        int count = Math.min(buf.readVarInt(), 64);
        List<Shot> shots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Vec3 end = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3 normal = new Vec3(buf.readByte() / 127.0, buf.readByte() / 127.0, buf.readByte() / 127.0);
            shots.add(new Shot(end, normal, buf.readByte(), buf.readVarInt()));
        }
        return new TurretFireEvent(pos, type, muzzle, seed, lock, shots);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TurretFireEffects.onFire(this));
        context.get().setPacketHandled(true);
    }
}
