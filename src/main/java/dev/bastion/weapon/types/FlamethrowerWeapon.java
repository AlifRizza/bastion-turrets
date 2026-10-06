package dev.bastion.weapon.types;

import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretBurn;
import dev.bastion.turret.targeting.LineOfSight;
import dev.bastion.turret.targeting.TargetSelector;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Flamethrower Turret: a short cone of fire, cone_angle degrees either side of the nozzle. Every pulse (fire_interval)
 * hits every valid target in the cone that the flames can reach (blocks stop them) and sets it burning (TurretBurn:
 * burn_damage per second for burn_ticks). Fire-immune targets are never engaged; the fire would not hurt them.
 */
public class FlamethrowerWeapon extends WeaponType {
    @Override
    public FireProfile fireProfile() {
        return FireProfile.CONE;
    }

    @Override
    public boolean canTarget(Entity entity, TurretBaseBlockEntity turret) {
        return !TurretBurn.immune(entity);
    }

    @Override
    public void onFire(FireContext context) {
        StatSheet stats = context.stats();
        WeaponData data = stats.data();
        Vec3 from = context.muzzle(), direction = context.direction();
        double range = stats.range(), cone = Math.toRadians(data.param("cone_angle"));
        DamageSource flame = BastionDamageTypes.of(context.level(), BastionDamageTypes.TURRET_FLAME);
        int burnTicks = Math.round(data.param("burn_ticks"));
        float burnDamage = data.param("burn_damage");

        List<LivingEntity> inRange = context.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(range),
                e -> TargetSelector.isCandidate(context.turret(), e, stats, this));
        for (LivingEntity target : inRange) {
            Vec3 center = target.getBoundingBox().getCenter(), to = center.subtract(from);
            double distance = to.length();
            if (distance > range + target.getBbWidth() / 2) continue;
            // A wide target is hit when any part of it is in the cone, not just its centre.
            double allowance = Math.atan2(target.getBbWidth() / 2, Math.max(distance, 0.5));
            double angle = distance < 1e-3 ? 0 : Math.acos(Math.min(1, to.dot(direction) / distance));
            if (angle > cone + allowance || !LineOfSight.clear(context.level(), from, center)) continue;
            Hitscan.damage(target, flame, stats.damage());
            TurretBurn.ignite(target, burnTicks, burnDamage);
        }
        // Where the stream ends (a wall, the floor, or full range): the client splashes the flames there.
        var end = context.level().clip(new ClipContext(from, from.add(direction.scale(range)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, null));
        ShotReport.send(context, this, 0, List.of(ShotReport.of(context.level(), end, direction)));
    }
}
