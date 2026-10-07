package dev.bastion.weapon.types;

import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.damage.BastionExplosion;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.Bullets;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.LeadSolver;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Railgun (Large Turret Base, user decision 2026-10-07): the anti-boss gun. Each shot takes one Rail Slug and
 * energy_per_shot FE, charges for charge_ticks, then fires a slug at slug_speed blocks per tick (Bullets, led onto
 * moving targets). It ignores armor (turret_rail is in minecraft:bypasses_armor), deals boss_multiplier against
 * forge:bosses, and wherever it lands, a mob or a block, a shockwave throws every valid target within shock_radius
 * outward. It always picks the biggest valid target in range (highest max health) and never aims inside min_range.
 */
public class RailgunWeapon extends WeaponType {
    @Override
    public FireProfile fireProfile() {
        return FireProfile.CHARGED;
    }

    @Override
    public int chargeTicks(StatSheet stats) {
        return Math.round(stats.data().param("charge_ticks"));
    }

    @Override
    public boolean canTarget(Entity entity, TurretBaseBlockEntity turret) {
        WeaponData data = turret.inventory().weaponData();
        if (data == null) return false;
        double min = data.param("min_range");
        return entity.distanceToSqr(turret.pivot(data)) >= min * min;
    }

    /** Boss first: the highest max health in range; the GUI priority only breaks ties. */
    @Override
    public Comparator<LivingEntity> targetOrder(Comparator<LivingEntity> byFilter) {
        return Comparator.<LivingEntity>comparingDouble(e -> -e.getMaxHealth()).thenComparing(byFilter);
    }

    @Override
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        return LeadSolver.lead(turret, target, point, stats, stats.data().param("slug_speed"));
    }

    /** Needs the FE for a shot as well as a slug (the base checks the slug). */
    @Override
    public boolean outOfAmmo(TurretBaseBlockEntity turret, WeaponState state) {
        return !turret.hasEnergy(cost(turret));
    }

    @Override
    public void onFire(FireContext context) {
        if (!context.turret().useEnergy(cost(context.turret()))) return;
        StatSheet stats = context.stats();
        WeaponData data = stats.data();
        Predicate<Entity> valid = Bullets.validTargets(context, this);
        DamageSource rail = BastionDamageTypes.of(context.level(), BastionDamageTypes.TURRET_RAIL);
        float radius = data.param("shock_radius"), knockback = data.param("shock_knockback"), bossMultiplier = data.param("boss_multiplier");
        Bullets.fire(context, this, 0, List.of(context.direction()), data.param("slug_speed"), stats.pierce(), valid, new Bullets.Hit() {
            @Override
            public void hit(Bullets.Bullet bullet, EntityHitResult hit) {
                Entity target = hit.getEntity();
                Hitscan.damage(target, rail, stats.damage() * (target.getType().is(Tags.EntityTypes.BOSSES) ? bossMultiplier : 1));
                BastionExplosion.detonate(context.level(), hit.getLocation(), radius, 0, knockback, valid, null);
            }

            @Override
            public void blocked(Bullets.Bullet bullet, Vec3 at) {
                BastionExplosion.detonate(context.level(), at, radius, 0, knockback, valid, null);
            }
        });
    }

    private static int cost(TurretBaseBlockEntity turret) {
        WeaponData data = turret.inventory().weaponData();
        return data == null ? Integer.MAX_VALUE : Math.round(data.param("energy_per_shot"));
    }
}
