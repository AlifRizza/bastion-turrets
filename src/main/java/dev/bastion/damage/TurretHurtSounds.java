package dev.bastion.damage;

import dev.bastion.Bastion;
import dev.bastion.config.BastionConfig;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hurt sounds of turret hits (PLAN 5.7). Turret damage clears a mob's damage cooldown (Hitscan.damage: fast weapons
 * would lose most hits), so every hit is a fresh one and played the mob's hurt sound: twelve groans a second from one
 * Machine Gun (user 10-10, "too loud"). While a turret hit is applied, the mob's hurt sound plays at most once per
 * turretHurtSoundInterval ticks and at turretHurtSoundVolume; its death sound always. Server thread only.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID)
public final class TurretHurtSounds {
    /** The mob a turret hit is being applied to, else null. */
    @Nullable
    private static Entity hitting;
    private static final Map<Entity, Long> LAST = new WeakHashMap<>();

    private TurretHurtSounds() {
    }

    /** {@code target.hurt}, with the sounds it plays meanwhile limited as above. */
    public static boolean hurt(Entity target, DamageSource source, float amount) {
        Entity outer = hitting;
        hitting = target;
        try {
            return target.hurt(source, amount);
        } finally {
            hitting = outer;
        }
    }

    @SubscribeEvent
    public static void onSound(PlayLevelSoundEvent.AtPosition event) {
        if (!(hitting instanceof LivingEntity mob) || mob.isDeadOrDying() || event.getLevel().isClientSide()) return;
        long now = mob.level().getGameTime();
        Long last = LAST.get(mob);
        if (last != null && now - last < BastionConfig.TURRET_HURT_SOUND_INTERVAL.get()) {
            event.setCanceled(true);
            return;
        }
        LAST.put(mob, now);
        event.setNewVolume(event.getNewVolume() * BastionConfig.TURRET_HURT_SOUND_VOLUME.get().floatValue());
    }
}
