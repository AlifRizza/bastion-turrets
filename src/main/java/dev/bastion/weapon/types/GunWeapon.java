package dev.bastion.weapon.types;

import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Gun Turret, PLAN 7.1: one accurate ray per shot. Precision Lock: the first shot after holding the same
 * target for precision_lock_ticks deals x precision_lock_multiplier, and a headshot on that shot x
 * headshot_multiplier more (the bonuses stack).
 */
public class GunWeapon extends WeaponType {
    @Override
    public FireProfile fireProfile() {
        return FireProfile.HITSCAN_SINGLE;
    }

    @Override
    public void onFire(FireContext context) {
        WeaponData data = context.stats().data();
        Vec3 direction = Hitscan.spread(context.direction(), context.stats().spread(), RandomSource.create(context.seed()));
        Hitscan.Trace trace = Hitscan.trace(context.level(), context.muzzle(), direction, context.stats().range(), Hitscan::canHit,
                context.stats().pierce());
        boolean first = true;
        for (EntityHitResult entityHit : trace.entities()) {
            float damage = context.stats().damage();
            if (first && context.precisionLock()) { // the lock bonus belongs to the target that was locked
                damage *= data.param("precision_lock_multiplier");
                if (isHeadshot(entityHit)) damage *= data.param("headshot_multiplier");
            }
            Hitscan.damage(entityHit.getEntity(), context.level(), damage);
            first = false;
        }
        ShotReport.send(context, this, 0, List.of(ShotReport.of(context.level(), trace.end(), direction)));
    }

    /** Top quarter of the target's box counts as the head. */
    static boolean isHeadshot(EntityHitResult hit) {
        AABB box = hit.getEntity().getBoundingBox();
        return hit.getLocation().y >= box.maxY - box.getYsize() * 0.25;
    }
}
