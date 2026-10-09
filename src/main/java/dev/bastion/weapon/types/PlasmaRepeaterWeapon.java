package dev.bastion.weapon.types;

import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.Bullets;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.LeadSolver;
import dev.bastion.weapon.Splash;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/**
 * Plasma Repeater (PLAN "Ion Rifle, Plasma Repeater & Plasma Cannon"): rapid plasma balls, slow enough to see
 * (bolt_speed), led onto moving targets. Each stops at the first valid target or block and splashes splash_damage on
 * every other valid target within splash_radius. Heats up like the Machine Gun (top-level heat fields). Plasma Cells,
 * ammo_per_shot 0.1.
 */
public class PlasmaRepeaterWeapon extends WeaponType {
    @Override
    public FireProfile fireProfile() {
        return FireProfile.PROJECTILE;
    }

    @Override
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        return LeadSolver.lead(turret, target, point, stats, stats.data().param("bolt_speed"));
    }

    @Override
    public void onFire(FireContext context) {
        StatSheet stats = context.stats();
        WeaponData data = stats.data();
        Vec3 direction = Hitscan.spread(context.direction(), stats.spread(), RandomSource.create(context.seed()));
        Bullets.fire(context, this, 0, List.of(direction), data.param("bolt_speed"), 0, Bullets.validTargets(context, this),
                bolt(context.level(), stats.damage(), data.param("splash_damage") * stats.damage() / data.damage(), data.param("splash_radius"),
                        Bullets.filtered(context, this), BastionDamageTypes.of(context.level(), BastionDamageTypes.TURRET_PLASMA)));
    }

    /** What a ball does where it lands: {@code damage} to the target struck, {@code splash} to the others in {@code radius}. */
    public static Bullets.Hit bolt(ServerLevel level, float damage, float splash, double radius, Predicate<Entity> canHit, DamageSource source) {
        return new Bullets.Hit() {
            @Override
            public void hit(Bullets.Bullet bullet, EntityHitResult hit) {
                Hitscan.damage(hit.getEntity(), source, damage);
                for (LivingEntity e : Splash.around(level, hit.getLocation(), radius, canHit, hit.getEntity())) Hitscan.damage(e, source, splash);
            }

            @Override
            public void blocked(Bullets.Bullet bullet, Vec3 at) {
                for (LivingEntity e : Splash.around(level, at, radius, canHit, null)) Hitscan.damage(e, source, splash);
            }
        };
    }
}
