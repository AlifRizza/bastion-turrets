package dev.bastion.weapon.types;

import dev.bastion.network.TurretFireEvent;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shotgun Turret, PLAN 7.3: `pellets` rays in a cone of `spread` degrees from one seed (clients get every end
 * point, so tracers match server hits exactly). Pellet damage falls linearly from 100% at falloff_start
 * to falloff_min at falloff_end; knockback is applied once per target, scaled by the pellets that hit it.
 */
public class ShotgunWeapon extends WeaponType {
    @Override
    public FireProfile fireProfile() {
        return FireProfile.HITSCAN_SPREAD;
    }

    @Override
    public void onFire(FireContext context) {
        WeaponData data = context.stats().data();
        RandomSource random = RandomSource.create(context.seed());
        int pellets = Math.round(data.param("pellets"));
        float start = data.param("falloff_start"), end = data.param("falloff_end"), min = data.param("falloff_min");
        Map<Entity, Integer> pelletsPerTarget = new HashMap<>();
        List<TurretFireEvent.Shot> shots = new ArrayList<>(pellets);

        for (int i = 0; i < pellets; i++) {
            Vec3 direction = Hitscan.spread(context.direction(), context.stats().spread(), random);
            Hitscan.Trace trace = Hitscan.trace(context.level(), context.muzzle(), direction, context.stats().range(), Hitscan::canHit,
                    context.stats().pierce());
            for (EntityHitResult entityHit : trace.entities()) {
                double distance = entityHit.getLocation().distanceTo(context.muzzle());
                float falloff = (float) Mth.clampedLerp(1, min, (distance - start) / (end - start));
                Hitscan.damage(entityHit.getEntity(), context.level(), context.stats().damage() * falloff);
                pelletsPerTarget.merge(entityHit.getEntity(), 1, Integer::sum);
            }
            shots.add(ShotReport.of(context.level(), trace.end(), direction));
        }

        float knockback = data.param("knockback");
        Vec3 push = context.direction();
        pelletsPerTarget.forEach((entity, hits) -> {
            if (entity instanceof LivingEntity living) living.knockback(knockback * hits / pellets, -push.x, -push.z);
        });
        ShotReport.send(context, this, 0, shots);
    }
}
