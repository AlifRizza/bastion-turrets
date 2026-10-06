package dev.bastion.client;

import dev.bastion.client.render.SmokeTrailRenderer;
import dev.bastion.client.render.TracerRenderer;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.network.BulletImpact;
import dev.bastion.network.BurnSync;
import dev.bastion.network.RackSync;
import dev.bastion.network.TurretImpactEvent;
import dev.bastion.network.TurretStateSync;
import dev.bastion.registry.BastionSounds;
import dev.bastion.turret.TurretBaseBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Client-side packet effects; only ever reached through DistExecutor from the packet classes. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void turretState(TurretStateSync message) {
        if (Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(message.pos()) instanceof TurretBaseBlockEntity turret) {
            turret.applySync(message);
        }
    }

    /**
     * The blast itself, then (when there is a surface: the face hit, else the ground just below) its scorch mark
     * and, on floors, the dust ring. Debris and dust take the colour of the block that was hit.
     */
    private static void rocketBlast(ClientLevel level, TurretImpactEvent event, boolean missile) {
        Vec3 at = event.pos();
        Vec3 surface = null, normal = event.normal();
        if (normal.lengthSqr() > 0.5) {
            surface = at;
        } else {
            BlockHitResult down = level.clip(new ClipContext(at, at.add(0, -1.8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
            if (down.getType() == HitResult.Type.BLOCK) {
                surface = down.getLocation();
                normal = new Vec3(0, 1, 0);
            }
        }
        int blockColor = 0xFF6A6560;
        if (surface != null) {
            BlockPos block = BlockPos.containing(surface.subtract(normal.scale(0.1)));
            blockColor = 0xFF000000 | level.getBlockState(block).getMapColor(level, block).col;
        }
        VfxParams params = VfxParams.of().scale(event.scale()).color(0xFFFF7A2E).blockColor(blockColor).seed(at.hashCode());
        VfxManager.play(missile ? VfxPresets.MISSILE_EXPLOSION : VfxPresets.ROCKET_EXPLOSION, at, new Vec3(0, 1, 0), params);
        if (surface != null) {
            VfxParams onSurface = VfxParams.of().scale(event.scale()).color(0xFFFF7A2E).blockColor(blockColor).normal(normal).seed(at.hashCode() + 1);
            VfxManager.play(VfxPresets.ROCKET_SCORCH, surface, normal, onSurface);
            if (normal.y > 0.7 && !missile) VfxManager.play(VfxPresets.ROCKET_DUST, surface.add(0, 0.06, 0), normal, onSurface);
        }
    }

    public static void burn(BurnSync message) {
        BurnEffects.set(message.entityId(), message.ticks());
    }

    public static void rack(RackSync message) {
        if (Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(message.pos()) instanceof TurretBaseBlockEntity turret) {
            turret.setClientTubes(message.tubes());
        }
    }

    public static void bulletImpact(BulletImpact impact) {
        TracerRenderer.land(impact.bullet(), impact.end(), impact.normal(), impact.hit(), impact.blockState());
    }

    public static void turretImpact(TurretImpactEvent event) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 at = event.pos();
        if (event.kind() == TurretImpactEvent.ROCKET_BLAST || event.kind() == TurretImpactEvent.MISSILE_BLAST) {
            boolean missile = event.kind() == TurretImpactEvent.MISSILE_BLAST;
            SmokeTrailRenderer.end(event.source(), at, level.getGameTime());
            rocketBlast(level, event, missile);
            level.playLocalSound(at.x, at.y, at.z, (missile ? BastionSounds.MISSILE_EXPLODE : BastionSounds.ROCKET_EXPLODE).get(),
                    SoundSource.HOSTILE, missile ? 1.4f : 2.2f, 0.94f + level.random.nextFloat() * 0.12f, false);
            return;
        }
        boolean destroyed = event.kind() == TurretImpactEvent.DESTROYED;
        VfxManager.play(destroyed ? VfxPresets.TURRET_DESTROYED : VfxPresets.TIER_UP, at, new Vec3(0, 1, 0),
                VfxParams.of().scale(event.scale()).color(destroyed ? VfxPresets.AMBER : VfxPresets.CYAN).seed(at.hashCode()));
        level.playLocalSound(at.x, at.y, at.z, destroyed ? BastionSounds.TURRET_DESTROYED.get() : BastionSounds.TURRET_TIER_UP.get(),
                SoundSource.BLOCKS, destroyed ? 1.6f : 1f, 1f, false);
    }
}
