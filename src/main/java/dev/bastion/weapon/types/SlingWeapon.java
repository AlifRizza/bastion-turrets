package dev.bastion.weapon.types;

import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.Bullets;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.LeadSolver;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The early flingers (Cobblestone Turret, Snowball Repeater, Potato Shotgun): `pellets` (default 1) slow bullets in a
 * cone of `spread` degrees, each dealing `damage` to the first valid target it meets. One type, three JSONs.
 */
public class SlingWeapon extends WeaponType {
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
        float damage = context.stats().damage();
        Bullets.fire(context, this, 0, directions(context), context.stats().data().param("bullet_speed"), context.stats().pierce(),
                Bullets.validTargets(context, this), (bullet, hit) -> Hitscan.damage(bullet, hit, BastionDamageTypes.turretShot(context.level()), damage));
    }

    /** One direction per pellet, all from the shot's seed. */
    public static List<Vec3> directions(FireContext context) {
        int pellets = Math.round(context.stats().data().params().getOrDefault("pellets", 1f));
        RandomSource random = RandomSource.create(context.seed());
        List<Vec3> directions = new ArrayList<>(pellets);
        for (int i = 0; i < pellets; i++) directions.add(Hitscan.spread(context.direction(), context.stats().spread(), random));
        return directions;
    }
}
