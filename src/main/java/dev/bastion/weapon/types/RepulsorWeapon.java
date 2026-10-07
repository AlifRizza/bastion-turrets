package dev.bastion.weapon.types;

import dev.bastion.network.TurretFireEvent;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.targeting.LineOfSight;
import dev.bastion.turret.targeting.TargetSelector;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Repulsion;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Repulsor (PLAN "Repulsor & Repulsor Dome", standard base): no damage. It turns to its target (no pitch limits, so a
 * mob at its feet is fine), charges, then a wave pushes every valid target in a cone (cone_angle either side of the
 * barrel's line from the pivot, as far as range; blocks stop it) knockback blocks away. Runs on FE like the Tesla Coil.
 */
public class RepulsorWeapon extends WeaponType {
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
        return !turret.hasEnergy(cost(turret));
    }

    /** Heavy mobs (config repulsorHeavyMobs) are too much for it: never fired at, never pushed (the Dome can). */
    @Override
    public boolean canTarget(Entity entity, TurretBaseBlockEntity turret) {
        return !(entity instanceof LivingEntity living && Repulsion.heavy(living));
    }

    @Override
    public void onFire(FireContext context) {
        TurretBaseBlockEntity turret = context.turret();
        if (!turret.useEnergy(cost(turret))) return;
        StatSheet stats = context.stats();
        WeaponData data = stats.data();
        // The cone opens from the pivot, not the horn's mouth: from the mouth, mobs crowding the turret's sides fell outside it.
        Vec3 from = turret.pivot(data), direction = context.direction();
        double range = stats.range(), cone = Math.toRadians(data.param("cone_angle"));
        // Pushed away from where the base is attached, not from the pivot: on a wall the pivot stands out from the wall,
        // and a mob hugging the wall below it would be pushed into the wall. Right on the axis: away from the wall.
        Vec3 origin = turret.alongMount(0), facing = Vec3.atLowerCornerOf(turret.facing().getNormal());
        Vec3 fallback = Math.abs(facing.y) < 0.5 ? facing : direction;
        List<TurretFireEvent.Shot> pushed = new ArrayList<>();
        for (LivingEntity target : context.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(range),
                e -> TargetSelector.isCandidate(turret, e, stats, this))) {
            Vec3 center = target.getBoundingBox().getCenter();
            if (!FlamethrowerWeapon.inCone(from, direction, range, cone, target) || !LineOfSight.clear(context.level(), from, center)) continue;
            if (Repulsion.push(target, origin, fallback, data.param("knockback"), data.param("lift"))) {
                // The client follows each pushed mob by id (dust at its feet).
                pushed.add(new TurretFireEvent.Shot(center, direction.reverse(), TurretFireEvent.ENTITY, target.getId()));
            }
        }
        ShotReport.send(context, this, 0, pushed); // sent even when nobody moved: the wave still shows
    }

    /** FE per push, from the mounted weapon's energy_per_shot (Repulsor Dome too). */
    static int cost(TurretBaseBlockEntity turret) {
        WeaponData data = turret.inventory().weaponData();
        return data == null ? Integer.MAX_VALUE : Math.round(data.param("energy_per_shot"));
    }
}
