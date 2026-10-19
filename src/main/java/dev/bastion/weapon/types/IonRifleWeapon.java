package dev.bastion.weapon.types;

import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.IonField;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.Bullets;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.LeadSolver;
import dev.bastion.weapon.Splash;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Ion Rifle (PLAN "Ion Rifle, Plasma Repeater & Plasma Cannon", after Crimsonland's Ion Rifle): a fast ion bolt
 * (bolt_speed, led onto moving targets) that stops at the first valid target or block. Where it stops an ion core forms:
 * the target struck takes damage, every other valid target whose box is within field_radius takes field_damage once,
 * through walls, and clients draw a straight beam from the core to each (IonField). Runs on FE (energy_per_shot).
 */
public class IonRifleWeapon extends WeaponType {
    /** The core: reach, damage to each zapped target, colour and how long clients show the beams. */
    public record Field(double radius, float damage, int color, int ticks) {
    }

    @Override
    public FireProfile fireProfile() {
        return FireProfile.PROJECTILE;
    }

    @Override
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        return LeadSolver.lead(turret, target, point, stats, stats.data().param("bolt_speed"));
    }

    @Override
    public boolean outOfAmmo(TurretBaseBlockEntity turret, WeaponState state) {
        return !turret.hasEnergy(cost(turret));
    }

    @Override
    public void onFire(FireContext context) {
        if (!context.turret().useEnergy(cost(context.turret()))) return;
        StatSheet stats = context.stats();
        WeaponData data = stats.data();
        // Damage modules scale the field like the bolt.
        Field field = new Field(data.param("field_radius"), data.param("field_damage") * stats.damageScale(), data.energyColor(),
                Math.round(data.param("field_ticks")));
        Bullets.fire(context, this, 0, List.of(context.direction()), data.param("bolt_speed"), 0, Bullets.validTargets(context, this),
                bolt(context.level(), stats.damage(), field, Bullets.filtered(context, this),
                        BastionDamageTypes.of(context.level(), BastionDamageTypes.TURRET_SHOCK)));
    }

    /** What a bolt does where it stops: {@code damage} to the target struck, then the core (public for the GameTests). */
    public static Bullets.Hit bolt(ServerLevel level, float damage, Field field, Predicate<Entity> canHit, DamageSource source) {
        return new Bullets.Hit() {
            @Override
            public void hit(Bullets.Bullet bullet, EntityHitResult hit) {
                Hitscan.damage(hit.getEntity(), source, damage);
                zap(level, bullet.id, hit.getLocation(), field, canHit, hit.getEntity(), source);
            }

            @Override
            public void blocked(Bullets.Bullet bullet, Vec3 at) {
                zap(level, bullet.id, at, field, canHit, null, source);
            }
        };
    }

    /** Field damage to every target in reach but the one struck; clients get the core and who to draw beams to. */
    private static void zap(ServerLevel level, int bullet, Vec3 core, Field field, Predicate<Entity> canHit, @Nullable Entity struck,
                            DamageSource source) {
        List<LivingEntity> zapped = Splash.around(level, core, field.radius(), canHit, struck);
        for (LivingEntity target : zapped) Hitscan.damage(target, source, field.damage());
        BastionNetwork.sendNear(level, core, new IonField(bullet, core, field.color(), field.ticks(),
                zapped.stream().mapToInt(Entity::getId).toArray()));
    }

    private static int cost(TurretBaseBlockEntity turret) {
        WeaponData data = turret.inventory().weaponData();
        return data == null ? Integer.MAX_VALUE : Math.round(data.param("energy_per_shot"));
    }
}
