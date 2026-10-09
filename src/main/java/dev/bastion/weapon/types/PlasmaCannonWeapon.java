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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/**
 * Plasma Cannon (Large Turret Base; PLAN "Ion Rifle, Plasma Repeater & Plasma Cannon"): charges, then a big slow plasma
 * ball (orb_speed, led) that stops at the first valid target or block and explodes there, after Vic's Point Blank BFG 9000
 * (user 2026-10-10): every valid target whose box is within blast_radius takes blast_damage at the centre, falling off to
 * half at the edge; the target struck also takes the hit itself. No line of sight, no knockback, no block damage. One
 * Plasma Cell per shot; never fires inside min_range.
 */
public class PlasmaCannonWeapon extends WeaponType {
    /** The explosion: reach and damage at its centre (half that at the edge). */
    public record Blast(double radius, float damage) {
    }

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
        return outsideMinRange(entity, turret);
    }

    @Override
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        return LeadSolver.lead(turret, target, point, stats, stats.data().param("orb_speed"));
    }

    @Override
    public void onFire(FireContext context) {
        StatSheet stats = context.stats();
        WeaponData data = stats.data();
        // Damage modules scale the blast like the hit.
        Blast blast = new Blast(data.param("blast_radius"), data.param("blast_damage") * stats.damage() / data.damage());
        Bullets.fire(context, this, 0, List.of(context.direction()), data.param("orb_speed"), 0, Bullets.validTargets(context, this),
                orb(context.level(), stats.damage(), blast, Bullets.filtered(context, this),
                        BastionDamageTypes.of(context.level(), BastionDamageTypes.TURRET_PLASMA)));
    }

    /** What the orb does where it stops: {@code damage} to the target struck, then the blast (public for the GameTests). */
    public static Bullets.Hit orb(ServerLevel level, float damage, Blast blast, Predicate<Entity> canHit, DamageSource source) {
        return new Bullets.Hit() {
            @Override
            public void hit(Bullets.Bullet bullet, EntityHitResult hit) {
                Hitscan.damage(hit.getEntity(), source, damage);
                explode(level, hit.getLocation(), blast, canHit, source);
            }

            @Override
            public void blocked(Bullets.Bullet bullet, Vec3 at) {
                explode(level, at, blast, canHit, source);
            }
        };
    }

    private static void explode(ServerLevel level, Vec3 at, Blast blast, Predicate<Entity> canHit, DamageSource source) {
        for (LivingEntity target : Splash.around(level, at, blast.radius(), canHit, null)) {
            double falloff = 1 - 0.5 * Splash.distance(at, target.getBoundingBox()) / blast.radius();
            Hitscan.damage(target, source, (float) (blast.damage() * falloff));
        }
    }
}
