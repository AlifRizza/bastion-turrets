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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Gun Turret, PLAN 7.1: one accurate bullet per shot (bullet_speed blocks per tick, led onto moving targets).
 * Precision Lock: the first shot after holding the same target for precision_lock_ticks deals x
 * precision_lock_multiplier to the first target it hits, and a headshot on it x headshot_multiplier more.
 */
public class GunWeapon extends WeaponType {
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
        Vec3 direction = Hitscan.spread(context.direction(), context.stats().spread(), RandomSource.create(context.seed()));
        float damage = context.stats().damage();
        boolean lock = context.precisionLock();
        Bullets.fire(context, this, 0, List.of(direction), data.param("bullet_speed"), context.stats().pierce(),
                Bullets.validTargets(context, this), (bullet, hit) -> {
                    float amount = damage;
                    if (lock && bullet.hits == 0) { // the lock bonus belongs to the target that was locked
                        amount *= data.param("precision_lock_multiplier");
                        if (isHeadshot(hit)) amount *= data.param("headshot_multiplier");
                    }
                    Hitscan.damage(hit.getEntity(), context.level(), amount);
                });
    }

    /** Top quarter of the target's box counts as the head. */
    static boolean isHeadshot(EntityHitResult hit) {
        AABB box = hit.getEntity().getBoundingBox();
        return hit.getLocation().y >= box.maxY - box.getYsize() * 0.25;
    }
}
