package dev.bastion.weapon.types;

import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.Bullets;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.LeadSolver;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Sniper Turret: holds aim for charge_ticks (laser sight on the client), then one heavy, fast bullet (bullet_speed,
 * led onto moving targets) that passes through base_pierce extra targets (Penetrator modules add more). No spread,
 * long range, slow cycle.
 */
public class SniperWeapon extends WeaponType {
    @Override
    public FireProfile fireProfile() {
        return FireProfile.CHARGED;
    }

    @Override
    public int chargeTicks(StatSheet stats) {
        return Math.round(stats.data().param("charge_ticks"));
    }

    @Override
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        return LeadSolver.lead(turret, target, point, stats, stats.data().param("bullet_speed"));
    }

    @Override
    public void onFire(FireContext context) {
        StatSheet stats = context.stats();
        int pierce = stats.pierce() + Math.round(stats.data().param("base_pierce"));
        float damage = stats.damage();
        Bullets.fire(context, this, 0, List.of(context.direction()), stats.data().param("bullet_speed"), pierce,
                Bullets.validTargets(context, this), (bullet, hit) -> Hitscan.damage(hit.getEntity(), context.level(), damage));
    }
}
