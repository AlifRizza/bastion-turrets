package dev.bastion.weapon.types;

import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponType;
import net.minecraft.world.phys.EntityHitResult;

import java.util.List;

/**
 * Sniper Turret: holds aim for charge_ticks (laser sight on the client), then one heavy hitscan shot that passes
 * through base_pierce extra targets (Penetrator modules add more). No spread, long range, slow cycle.
 */
public class SniperWeapon extends WeaponType {
    @Override
    public FireProfile fireProfile() {
        return FireProfile.CHARGED;
    }

    @Override
    public int chargeTicks(StatSheet stats) {
        return Math.round(stats.data().param("charge_ticks"));
    }

    @Override
    public void onFire(FireContext context) {
        StatSheet stats = context.stats();
        int pierce = stats.pierce() + Math.round(stats.data().param("base_pierce"));
        Hitscan.Trace trace = Hitscan.trace(context.level(), context.muzzle(), context.direction(), stats.range(), Hitscan::canHit, pierce);
        for (EntityHitResult hit : trace.entities()) Hitscan.damage(hit.getEntity(), context.level(), stats.damage());
        ShotReport.send(context, this, 0, List.of(ShotReport.of(context.level(), trace.end(), context.direction())));
    }
}
