package dev.bastion.client;

import dev.bastion.client.render.BeamRenderer;
import dev.bastion.client.render.TracerRenderer;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.client.vfx.WeaponFx;
import dev.bastion.network.IonField;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Ion Rifle (PLAN "Ion Rifle, Plasma Repeater & Plasma Cannon"): a blue streak to where the bolt stops, and there the core's beams (IonField). */
public final class IonEffects {
    /** Width ~5x the Gun's tracer plus a glowing head: an energy bolt, not a bullet (user 2026-10-10). */
    private static final float BOLT_WIDTH = 0.4f, BOLT_HEAD = 0.35f, TRAIL_BLOCKS = 6f, BEAM_WIDTH = 0.42f;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private IonEffects() {
    }

    public static void fire(ClientLevel level, TurretFireEvent event, WeaponFx fx, Vec3 muzzle, Vec3 direction, int color) {
        VfxManager.play(fx.muzzle(), muzzle, direction, VfxParams.of().color(color).seed(event.seed()));
        for (TurretFireEvent.Shot shot : event.shots()) {
            // The impact is the core's (IonField, chained to this tracer's landing); a bolt out of range just fades.
            TracerRenderer.spawnBolt(shot.blockState(), muzzle, shot.end(), event.speed(), color, BOLT_WIDTH, BOLT_HEAD, TRAIL_BLOCKS,
                    (end, normal, hit, blockState) -> {});
        }
        TurretFireEffects.playShot(level, event, muzzle, fx, RandomSource.create(event.seed()));
    }

    /** The core: a flash and ring where the bolt stopped, a straight beam to each target it zapped for {@code ticks}. */
    public static void field(ClientLevel level, IonField field) {
        int color = 0xFF000000 | field.color();
        Vec3 core = field.core();
        VfxManager.play(VfxPresets.ION_HIT, core, UP, VfxParams.of().color(color).normal(UP).seed(field.bullet()));
        for (int id : field.targets()) {
            Entity target = level.getEntity(id);
            if (target == null) continue;
            Vec3 at = target.getBoundingBox().getCenter();
            Vec3 back = core.subtract(at).normalize();
            BeamRenderer.spawn(core, at, color, BEAM_WIDTH, field.ticks());
            VfxManager.play(VfxPresets.ION_ZAP, at, back.reverse(), VfxParams.of().color(color).normal(back).seed(id));
        }
        level.playLocalSound(core.x, core.y, core.z, BastionSounds.ION_HIT.get(), SoundSource.HOSTILE, 1f,
                0.94f + level.random.nextFloat() * 0.12f, false);
    }
}
