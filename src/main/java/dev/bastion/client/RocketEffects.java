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

/** Per-tick client effects of every rocket and missile in flight: exhaust trail, a moving light and (rockets) the flight roar. */
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
            Vec3 tail = rocket.getBoundingBox().getCenter().add(back.scale(missile ? 0.2 : 0.3));
            VfxParams params = VfxParams.of().color(EXHAUST).seed(level.getGameTime() * 31 + rocket.getId());
            VfxManager.play(missile ? VfxPresets.MISSILE_TRAIL : VfxPresets.ROCKET_TRAIL, tail, back, params);
            SmokeTrailRenderer.add(rocket.getId(), tail, missile, level.getGameTime());
            DynamicLightManager.add(rocket, tail, missile ? 9 : 12, 2);
            // Twelve roars at once would drown everything: missiles are heard at launch and impact only.
            if (!missile && ROARING.add(rocket.getId())) Minecraft.getInstance().getSoundManager().play(new Roar(rocket));
        }
        ROARING.retainAll(alive);
    }

    /** Motor roar that rides along with its rocket and stops when it explodes. */
    private static final class Roar extends AbstractTickableSoundInstance {
        private final TurretRocketEntity rocket;
        private int fading;

        Roar(TurretRocketEntity rocket) {
            super(BastionSounds.ROCKET_FLY.get(), SoundSource.HOSTILE, RandomSource.create());
            this.rocket = rocket;
            this.looping = true;
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
