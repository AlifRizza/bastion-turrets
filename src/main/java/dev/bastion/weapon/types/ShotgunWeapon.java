package dev.bastion.weapon.types;

import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.Bullets;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.LeadSolver;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shotgun Turret, PLAN 7.3: `pellets` bullets in a cone of `spread` degrees from one seed, flying bullet_speed blocks
 * per tick. Pellet damage falls linearly from 100% at falloff_start to falloff_min at falloff_end; knockback is applied
 * once per target and tick, scaled by the pellets that hit it.
 */
public class ShotgunWeapon extends WeaponType {
    @Override
    public FireProfile fireProfile() {
        return FireProfile.PROJECTILE;
    }

    @Override
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        return LeadSolver.lead(turret, target, point, stats, stats.data().param("bullet_speed"));
    }

    @Override
    public void onFire(FireContext context) {
        WeaponData data = context.stats().data();
        RandomSource random = RandomSource.create(context.seed());
        int pellets = Math.round(data.param("pellets"));
        List<Vec3> directions = new ArrayList<>(pellets);
        for (int i = 0; i < pellets; i++) directions.add(Hitscan.spread(context.direction(), context.stats().spread(), random));
        Bullets.fire(context, this, 0, directions, data.param("bullet_speed"), context.stats().pierce(),
                Bullets.validTargets(context, this), new Blast(context.level(), data, context.stats().damage(), pellets, context.direction()));
    }

    /** One shot's pellets: damage per pellet with falloff, knockback summed per target once the tick's pellets landed. */
    private static final class Blast implements Bullets.Hit {
        private final ServerLevel level;
        private final float damage, start, end, min, knockback;
        private final int pellets;
        private final Vec3 push;
        private final Map<Entity, Integer> pelletsPerTarget = new HashMap<>();

        Blast(ServerLevel level, WeaponData data, float damage, int pellets, Vec3 push) {
            this.level = level;
            this.damage = damage;
            this.start = data.param("falloff_start");
            this.end = data.param("falloff_end");
            this.min = data.param("falloff_min");
            this.knockback = data.param("knockback");
            this.pellets = pellets;
            this.push = push;
        }

        @Override
        public void hit(Bullets.Bullet bullet, EntityHitResult hit) {
            double distance = hit.getLocation().distanceTo(bullet.origin);
            float falloff = (float) Mth.clampedLerp(1, min, (distance - start) / (end - start));
            Hitscan.damage(hit.getEntity(), level, damage * falloff);
            pelletsPerTarget.merge(hit.getEntity(), 1, Integer::sum);
        }

        @Override
        public void endTick() {
            pelletsPerTarget.forEach((entity, hits) -> {
                if (entity instanceof LivingEntity living) living.knockback(knockback * hits / pellets, -push.x, -push.z);
            });
            pelletsPerTarget.clear();
        }
    }
}
