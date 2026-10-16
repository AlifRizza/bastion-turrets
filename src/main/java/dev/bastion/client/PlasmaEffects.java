package dev.bastion.client;

import dev.bastion.client.render.TracerRenderer;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.client.vfx.WeaponFx;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Plasma Repeater and Plasma Cannon (PLAN "Ion Rifle, Plasma Repeater & Plasma Cannon"): glowing balls instead of tracers and a burst where each lands. */
public final class PlasmaEffects {
    /** Ball radii, well above a bullet tracer's width so plasma reads as balls of energy (user 2026-10-10). */
    private static final float BOLT = 0.28f, ORB = 0.6f;

    private PlasmaEffects() {
    }

    /** {@code blast} = the Cannon's blast radius (its explosion is sized to it), 0 for the Repeater's small splash. */
    public static void fire(ClientLevel level, TurretFireEvent event, WeaponFx fx, Vec3 muzzle, Vec3 direction, int color, float blast) {
        boolean cannon = blast > 0;
        VfxManager.play(fx.muzzle(), muzzle, direction, VfxParams.of().color(color).seed(event.seed()));
        for (TurretFireEvent.Shot shot : event.shots()) {
            TracerRenderer.spawnOrb(shot.blockState(), muzzle, shot.end(), event.speed(), color, cannon ? ORB : BOLT, cannon ? 3f : 1.5f,
                    (end, normal, hit, blockState) -> {
                        if (cannon) blast(level, end, normal, hit, blockState, color, blast);
                        else splash(level, end, normal, hit, blockState, color);
                    });
        }
        TurretFireEffects.playShot(level, event, muzzle, fx, RandomSource.create(event.seed()));
    }

    /** A Repeater ball landing: a small teal-green splash, a little burn on blocks. */
    private static void splash(ClientLevel level, Vec3 end, Vec3 normal, int hit, int blockState, int color) {
        VfxParams params = VfxParams.of().color(color).normal(normal).blockColor(blockColor(level, end, hit, blockState)).seed(end.hashCode());
        VfxManager.play(VfxPresets.PLASMA_HIT, end, normal.reverse(), params);
        if (hit == TurretFireEvent.BLOCK) VfxManager.play(VfxPresets.PLASMA_SCORCH, end, normal.reverse(), params);
    }

    /**
     * The Cannon's explosion (after Vic's Point Blank BFG 9000), sized to its radius, and a big green burn on the face it hit
     * or, when it burst on a mob, on the ground below.
     */
    private static void blast(ClientLevel level, Vec3 end, Vec3 normal, int hit, int blockState, int color, float radius) {
        float scale = radius / 4f;
        VfxManager.play(VfxPresets.PLASMA_BURST, end, new Vec3(0, 1, 0),
                VfxParams.of().color(color).scale(scale).blockColor(blockColor(level, end, hit, blockState)).seed(end.hashCode()));
        Vec3 surface = end, face = normal;
        if (hit != TurretFireEvent.BLOCK) {
            BlockHitResult down = level.clip(new ClipContext(end, end.add(0, -2.5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
            surface = down.getType() == HitResult.Type.BLOCK ? down.getLocation() : null;
            face = new Vec3(0, 1, 0);
        }
        if (surface != null) {
            VfxManager.play(VfxPresets.PLASMA_SCORCH_BIG, surface, face.reverse(), VfxParams.of().color(color).normal(face).scale(scale).seed(end.hashCode() + 1));
        }
        level.playLocalSound(end.x, end.y, end.z, BastionSounds.PLASMA_CANNON_HIT.get(), SoundSource.HOSTILE, 1.6f,
                0.92f + level.random.nextFloat() * 0.16f, false);
    }

    private static int blockColor(ClientLevel level, Vec3 end, int hit, int blockState) {
        return hit == TurretFireEvent.BLOCK ? 0xFF000000 | Block.stateById(blockState).getMapColor(level, BlockPos.containing(end)).col : 0xFF888888;
    }
}
