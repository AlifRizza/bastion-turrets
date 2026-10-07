package dev.bastion.client;

import dev.bastion.Bastion;
import dev.bastion.client.render.SmokeTrailRenderer;
import dev.bastion.client.vfx.DynamicLightManager;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.projectile.TurretRocketEntity;
import dev.bastion.registry.BastionEntities;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Per-tick client effects of every rocket, missile and mortar shell in flight: exhaust trail, a moving light, the
 * rocket's flight roar and the shell's whistle once it starts to fall.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class RocketEffects {
    private static final int EXHAUST = 0xFFFF7A2E;
    private static final Set<Integer> ROARING = new HashSet<>();

    private RocketEffects() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.phase != TickEvent.Phase.END || level == null || Minecraft.getInstance().isPaused()) return;
        Set<Integer> alive = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof TurretRocketEntity rocket)) continue;
            alive.add(rocket.getId());
            Vec3 velocity = rocket.getDeltaMovement();
            if (velocity.lengthSqr() < 1e-6) continue;
            Vec3 back = velocity.normalize().reverse();
            boolean missile = rocket.getType() == BastionEntities.TURRET_MISSILE.get();
            boolean shell = rocket.getType() == BastionEntities.TURRET_MORTAR_SHELL.get();
            Vec3 tail = rocket.getBoundingBox().getCenter().add(back.scale(missile ? 0.2 : 0.3));
            VfxParams params = VfxParams.of().color(EXHAUST).seed(level.getGameTime() * 31 + rocket.getId());
            VfxManager.play(shell ? VfxPresets.MORTAR_TRAIL : missile ? VfxPresets.MISSILE_TRAIL : VfxPresets.ROCKET_TRAIL, tail, back, params);
            SmokeTrailRenderer.add(rocket.getId(), tail, missile || shell, level.getGameTime());
            DynamicLightManager.add(rocket, tail, shell ? 8 : missile ? 9 : 12, 2);
            // Twelve roars at once would drown everything: missiles are heard at launch and impact only. A shell
            // whistles on the way down.
            if (shell ? velocity.y < 0 && ROARING.add(rocket.getId()) : !missile && ROARING.add(rocket.getId())) {
                Minecraft.getInstance().getSoundManager().play(new Roar(rocket, shell));
            }
        }
        ROARING.retainAll(alive);
    }

    /** Motor roar (or a shell's falling whistle) that rides along with it and stops when it explodes. */
    private static final class Roar extends AbstractTickableSoundInstance {
        private final TurretRocketEntity rocket;
        private int fading;

        Roar(TurretRocketEntity rocket, boolean whistle) {
            super((whistle ? BastionSounds.MORTAR_WHISTLE : BastionSounds.ROCKET_FLY).get(), SoundSource.HOSTILE, RandomSource.create());
            this.rocket = rocket;
            this.looping = !whistle;
            this.delay = 0;
            this.volume = 0.9f;
            this.x = rocket.getX();
            this.y = rocket.getY();
            this.z = rocket.getZ();
        }

        @Override
        public void tick() {
            if (rocket.isRemoved()) {
                // Fade a couple of ticks first: stopping a sound the engine has not started yet makes OpenAL complain.
                volume = 0;
                if (++fading > 2) stop();
                return;
            }
            x = rocket.getX();
            y = rocket.getY();
            z = rocket.getZ();
        }
    }
}
