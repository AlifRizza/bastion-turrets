package dev.bastion.client;

import dev.bastion.client.render.BeamRenderer;
import dev.bastion.client.vfx.DynamicLightManager;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Laser Rifle on the client. While charging, sparks of light stream into the emitter (the orb itself is
 * LaserChargeRenderer). The shot: the beam to where it stopped (BeamRenderer), a flash at the emitter, an impact on every
 * target it passed through, a glowing burn where it hit a block, smoke lingering along its path, a crack.
 */
public final class LaserEffects {
    private static final double FAR_SOUND_DISTANCE = 32;

    private LaserEffects() {
    }

    /** Each tick while charging: light streaming in from around the emitter, more of it as the charge fills. */
    public static void gathering(ClientLevel level, ClientTurret client, float progress, int color) {
        Vec3 emitter = client.muzzles[0];
        if (emitter == null) return;
        VfxManager.play(VfxPresets.LASER_GATHER, emitter, new Vec3(0, 1, 0),
                VfxParams.of().color(color).scale(0.6f + 0.6f * progress).seed(level.random.nextLong()));
    }

    /** shots[0] is where the beam ended; the rest are the targets it went through. */
    public static void fire(ClientLevel level, TurretFireEvent event, Vec3 emitter, int color) {
        if (event.shots().isEmpty()) return;
        TurretFireEvent.Shot end = event.shots().get(0);
        Vec3 direction = end.end().subtract(emitter).normalize();
        BeamRenderer.spawn(emitter, end.end(), color, 0.24f, 16);
        VfxParams params = VfxParams.of().color(color).seed(event.seed());
        VfxManager.play(VfxPresets.LASER_MUZZLE, emitter, direction, params);
        for (int i = 1; i < event.shots().size(); i++) {
            TurretFireEvent.Shot hit = event.shots().get(i);
            VfxManager.play(VfxPresets.LASER_IMPACT, hit.end(), hit.normal().reverse(), VfxParams.of().color(color).normal(hit.normal()).seed(event.seed() + i));
        }
        if (end.hit() == TurretFireEvent.BLOCK) {
            int blockColor = 0xFF000000 | Block.stateById(end.blockState()).getMapColor(level, net.minecraft.core.BlockPos.containing(end.end())).col;
            VfxManager.play(VfxPresets.LASER_END, end.end(), end.normal().reverse(),
                    VfxParams.of().color(color).normal(end.normal()).blockColor(blockColor).seed(event.seed() + 99));
        }
        double length = end.end().distanceTo(emitter);
        for (int k = 1; k <= 4; k++) { // the air along the beam smokes for a moment, and the beam lights its path
            Vec3 at = emitter.add(direction.scale(length * k / 5));
            VfxManager.play(VfxPresets.LASER_TRAIL, at, direction, VfxParams.of().seed(event.seed() + k));
            if (k % 2 == 0) DynamicLightManager.add(at, 12, 4);
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean far = player.position().distanceTo(emitter) > FAR_SOUND_DISTANCE;
        RandomSource random = RandomSource.create(event.seed());
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance((far ? BastionSounds.LASER_FIRE_TAIL : BastionSounds.LASER_FIRE).get(),
                SoundSource.HOSTILE, far ? 1.6f : 1.1f, 0.94f + random.nextFloat() * 0.12f, random, emitter.x, emitter.y, emitter.z));
    }
}
