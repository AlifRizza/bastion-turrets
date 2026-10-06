package dev.bastion.client;

import dev.bastion.client.vfx.DynamicLightManager;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Flamethrower on the client: every pulse (each fire event, 2 ticks apart) feeds the flame stream from the nozzle and
 * splashes fire where it meets a surface, burning a scorch there that grows rather than stacks. The first pulse of a
 * burst plays the ignition; the roar is a loop in ClientTurret. A blue pilot light burns at the nozzle while it waits.
 */
public final class FlameEffects {
    private FlameEffects() {
    }

    public static void pulse(ClientLevel level, TurretFireEvent event, ClientTurret client, Vec3 nozzle, Vec3 direction, float range, int color) {
        VfxManager.play(VfxPresets.FLAME_STREAM, nozzle, direction, VfxParams.of().color(color).scale(range / 8f).seed(event.seed()));
        DynamicLightManager.add(nozzle.add(direction.scale(range * 0.4)), 13, 3);
        for (TurretFireEvent.Shot shot : event.shots()) {
            if (shot.hit() != TurretFireEvent.BLOCK) continue;
            VfxParams params = VfxParams.of().color(color).normal(shot.normal()).seed(event.seed() + 1);
            VfxManager.play(VfxPresets.FLAME_SPLASH, shot.end(), shot.normal().reverse(), params);
            VfxManager.play(VfxPresets.FLAME_SCORCH, shot.end(), shot.normal(), params); // renews and widens the mark already there
        }
        if (client.streak == 1) {
            level.playLocalSound(nozzle.x, nozzle.y, nozzle.z, BastionSounds.FLAMETHROWER_IGNITE.get(), SoundSource.HOSTILE, 1f,
                    0.95f + level.random.nextFloat() * 0.1f, false);
        }
    }

    /** Each tick while armed and not firing: the pilot light flickers at the nozzle. */
    public static void pilot(ClientLevel level, ClientTurret client) {
        Vec3 nozzle = client.muzzles[0];
        if (nozzle == null || level.getGameTime() % 3 != 0) return;
        VfxManager.play(VfxPresets.PILOT_LIGHT, nozzle, nozzle.subtract(client.muzzles[1] != null ? client.muzzles[1] : nozzle.add(0, -1, 0)),
                VfxParams.of().seed(level.random.nextLong()));
    }
}
