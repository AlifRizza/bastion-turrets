package dev.bastion.weapon.types;

import dev.bastion.projectile.TurretRocketEntity;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.LeadSolver;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Rocket Launcher Turret: dumb-fire rockets (a real projectile, not hitscan) that fly straight at rocket_speed,
 * lead moving targets and explode on contact. The blast only hurts what this turret may target. It never fires
 * at targets inside min_range, so it cannot catch itself in its own blast. Homing missiles come later.
 */
public class RocketLauncherWeapon extends WeaponType {
    private static final int TUBES = 4;

    @Override
    public FireProfile fireProfile() {
        return FireProfile.PROJECTILE;
    }

    @Override
    public boolean firesAsItReloads() {
        return true;
    }

    @Override
    public boolean canTarget(Entity entity, TurretBaseBlockEntity turret) {
        WeaponData data = turret.inventory().weaponData();
        if (data == null) return false;
        double min = data.param("min_range");
        return entity.distanceToSqr(turret.pivot(data)) >= min * min;
    }

    /** Lead: Targeting AI modules (lead_accuracy) close the gap to a perfect intercept. */
    @Override
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        return LeadSolver.lead(turret, target, point, stats, stats.data().param("rocket_speed"));
    }

    @Override
    public void onFire(FireContext context) {
        WeaponData data = context.stats().data();
        TurretRocketEntity rocket = new TurretRocketEntity(context.level(), context.turret().getBlockPos(), context.stats().damage(),
                data.param("splash_damage") * context.stats().damage() / data.damage(), data.param("splash_radius"), data.param("knockback"),
                Math.round(context.stats().range() / data.param("rocket_speed")) + 10);
        rocket.setPos(context.muzzle());
        rocket.launch(context.direction().scale(data.param("rocket_speed")));
        context.level().addFreshEntity(rocket);
        int tube = context.state().muzzle;
        context.state().muzzle = (tube + 1) % TUBES;
        ShotReport.send(context, this, tube, List.of());
    }
}
