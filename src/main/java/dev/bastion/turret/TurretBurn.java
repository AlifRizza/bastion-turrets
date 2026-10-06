package dev.bastion.turret;

import dev.bastion.Bastion;
import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.BurnSync;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Burning left by the Flamethrower: Bastion's own fire, not vanilla's (whose look is the vanilla flame overlay). Each
 * second the entity takes turret_burn damage until the time runs out; water, rain and bubbles put it out. The damage
 * type is in minecraft:is_fire, so fire-immune mobs and Fire Resistance ignore it. Server side; clients only learn who
 * burns and for how long (BurnSync) and draw the flames themselves.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID)
public final class TurretBurn {
    private static final int DAMAGE_INTERVAL = 20;
    /** Refreshes only reach clients once their copy would run this much short. */
    private static final int RESYNC_MARGIN = 20;
    private static final Map<LivingEntity, Burn> BURNING = new WeakHashMap<>();

    private static final class Burn {
        int ticks, synced, timer = DAMAGE_INTERVAL;
        float damagePerSecond;
    }

    private TurretBurn() {
    }

    /** Sets the entity burning for at least {@code ticks}; a fresh fire deals its first damage a second later. */
    public static void ignite(LivingEntity entity, int ticks, float damagePerSecond) {
        if (immune(entity) || entity.isInWaterRainOrBubble()) return;
        Burn burn = BURNING.computeIfAbsent(entity, e -> new Burn());
        burn.ticks = Math.max(burn.ticks, ticks);
        burn.damagePerSecond = Math.max(burn.damagePerSecond, damagePerSecond);
        if (burn.ticks - burn.synced > RESYNC_MARGIN) sync(entity, burn.ticks);
        burn.synced = Math.max(burn.synced, burn.ticks);
    }

    public static boolean isBurning(LivingEntity entity) {
        Burn burn = BURNING.get(entity);
        return burn != null && burn.ticks > 0;
    }

    /** Fire would not hurt it: fire-immune (blazes, striders...) or under Fire Resistance. */
    public static boolean immune(Entity entity) {
        return entity.fireImmune() || entity instanceof LivingEntity living && living.hasEffect(MobEffects.FIRE_RESISTANCE);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BURNING.isEmpty()) return;
        for (Iterator<Map.Entry<LivingEntity, Burn>> it = BURNING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<LivingEntity, Burn> entry = it.next();
            LivingEntity entity = entry.getKey();
            Burn burn = entry.getValue();
            if (entity.isRemoved() || !entity.isAlive()) {
                it.remove();
                continue;
            }
            if (immune(entity) || entity.isInWaterRainOrBubble() || --burn.ticks <= 0) {
                it.remove();
                sync(entity, 0);
                continue;
            }
            burn.synced--;
            if (--burn.timer <= 0) {
                burn.timer = DAMAGE_INTERVAL;
                entity.invulnerableTime = 0;
                entity.hurt(BastionDamageTypes.of(entity.level(), BastionDamageTypes.TURRET_BURN), burn.damagePerSecond);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BURNING.clear();
    }

    private static void sync(LivingEntity entity, int ticks) {
        BastionNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), new BurnSync(entity.getId(), ticks));
    }
}
