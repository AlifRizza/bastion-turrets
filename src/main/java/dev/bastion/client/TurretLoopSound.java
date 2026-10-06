package dev.bastion.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.function.DoubleSupplier;

/**
 * A looping turret sound whose volume and pitch follow live values (idle hum, Machine Gun spin, PLAN 5.7).
 * Stops itself once the volume has stayed at zero for a second or the turret is gone.
 */
public final class TurretLoopSound extends AbstractTickableSoundInstance {
    private final ClientTurret turret;
    private final DoubleSupplier volumeFn, pitchFn;
    private int silentTicks;

    public TurretLoopSound(SoundEvent sound, ClientTurret turret, DoubleSupplier volume, DoubleSupplier pitch) {
        super(sound, SoundSource.HOSTILE, RandomSource.create());
        this.turret = turret;
        this.volumeFn = volume;
        this.pitchFn = pitch;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = turret.pos.getX() + 0.5;
        this.y = turret.pos.getY() + 0.8;
        this.z = turret.pos.getZ() + 0.5;
        tick();
    }

    @Override
    public void tick() {
        volume = (float) volumeFn.getAsDouble();
        pitch = (float) pitchFn.getAsDouble();
        silentTicks = volume <= 0.001f ? silentTicks + 1 : 0;
        if (silentTicks > 20 || turret.removed) stop();
    }

    public boolean finished() {
        return isStopped();
    }
}
