package dev.bastion.weapon.types;

import dev.bastion.network.TurretFireEvent;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.targeting.TargetSelector;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Repulsion;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Repulsor Dome (PLAN "Repulsor & Repulsor Dome", Large Turret Base): no damage, never turns. With a valid target
 * within range it charges, then a dome pushes every valid target within range knockback blocks out from its middle.
 * A field: walls do not stop it. Runs on FE like the Tesla Coil.
 */
public class RepulsorDomeWeapon extends WeaponType {
    @Override
    public FireProfile fireProfile() {
        return FireProfile.AREA_PULSE;
    }

    @Override
    public int chargeTicks(StatSheet stats) {
        return Math.round(stats.data().param("charge_ticks"));
    }

    @Override
    public boolean outOfAmmo(TurretBaseBlockEntity turret, WeaponState state) {
        return !turret.hasEnergy(RepulsorWeapon.cost(turret));
    }

    /** No line of sight needed (TargetSelector already checked the range). */
    @Override
    public Vec3 sight(TurretBaseBlockEntity turret, LivingEntity target, StatSheet stats) {
        return target.getBoundingBox().getCenter();
    }

    @Override
    public void onFire(FireContext context) {
        TurretBaseBlockEntity turret = context.turret();
        if (!turret.useEnergy(RepulsorWeapon.cost(turret))) return;
        StatSheet stats = context.stats();
        WeaponData data = stats.data();
        Vec3 middle = turret.pivot(data);
        List<TurretFireEvent.Shot> pushed = new ArrayList<>();
        for (LivingEntity target : TargetSelector.findAll(turret, stats, this, Math.round(data.param("max_targets")))) {
            if (Repulsion.push(target, middle, new Vec3(1, 0, 0), data.param("knockback"), data.param("lift"))) {
                Vec3 center = target.getBoundingBox().getCenter();
                pushed.add(new TurretFireEvent.Shot(center, middle.subtract(center).normalize(), TurretFireEvent.ENTITY, target.getId()));
            }
        }
        ShotReport.send(context, this, 0, pushed);
    }
}
