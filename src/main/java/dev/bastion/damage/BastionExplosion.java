package dev.bastion.damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import dev.bastion.Bastion;
import dev.bastion.turret.TurretHitboxEntity;

/**
 * Bastion's own explosion (PLAN 1, NO-VANILLA): damage and knockback computed here, no block damage and no
 * vanilla particles or sounds; the visuals come from the VFX framework. Damage and push fall off linearly
 * to zero at {@code radius}. ponytail: walls do not shield targets; add a LineOfSight check per target if
 * that ever matters.
 */
public final class BastionExplosion {
    public static final ResourceKey<DamageType> TURRET_BLAST = ResourceKey.create(Registries.DAMAGE_TYPE, Bastion.id("turret_blast"));

    private BastionExplosion() {
    }

    public static void detonate(ServerLevel level, Vec3 center, float radius, float damage, float knockback) {
        detonate(level, center, radius, damage, knockback, e -> true, null);
    }

    /**
     * @param affects which entities the blast may touch (rockets: only the firing turret's valid targets)
     * @param direct  the projectile that exploded, for death messages; null for a turret blowing up
     */
    public static void detonate(ServerLevel level, Vec3 center, float radius, float damage, float knockback,
                                Predicate<Entity> affects, @Nullable Entity direct) {
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TURRET_BLAST), direct);
        for (Entity entity : level.getEntities((Entity) null, new AABB(center, center).inflate(radius),
                e -> e.isAlive() && !(e instanceof TurretHitboxEntity) && !(e instanceof ItemEntity) && affects.test(e))) {
            Vec3 offset = entity.getBoundingBox().getCenter().subtract(center);
            double distance = offset.length();
            if (distance > radius) continue;
            float falloff = (float) (1 - distance / radius);
            entity.invulnerableTime = 0; // a direct rocket hit a moment ago must not swallow the splash
            entity.hurt(source, damage * falloff);
            if (entity instanceof LivingEntity living && distance > 1e-3) {
                living.knockback(knockback * falloff, -offset.x / distance, -offset.z / distance);
            }
        }
    }
}
