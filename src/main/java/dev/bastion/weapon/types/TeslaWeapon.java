package dev.bastion.weapon.types;

import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.targeting.LineOfSight;
import dev.bastion.turret.targeting.TargetSelector;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Tesla Coil (Large Turret Base): runs on Forge Energy instead of ammo (energy_per_shot from the base's capacitor, see
 * TurretBaseBlockEntity). It charges for charge_ticks, then throws `bolts` lightning bolts at random valid targets in
 * range, at most bolts_per_target per target (a lone target takes that many). The coil never turns: it only needs a
 * clear line from its terminal (the pivot, pivot_height above the base) to the target.
 */
public class TeslaWeapon extends WeaponType {
    /** Most targets a discharge picks from. */
    private static final int CANDIDATES = 16;

    @Override
    public FireProfile fireProfile() {
        return FireProfile.ARC;
    }

    @Override
    public int chargeTicks(StatSheet stats) {
        return Math.round(stats.data().param("charge_ticks"));
    }

    /** No pitch limits: a bolt bends to wherever the target is, as long as nothing solid is in between. */
    @Override
    @Nullable
    public Vec3 sight(TurretBaseBlockEntity turret, LivingEntity target, StatSheet stats) {
        Vec3 terminal = turret.pivot(stats.data());
        for (Vec3 point : new Vec3[]{target.getBoundingBox().getCenter(), target.getEyePosition()}) {
            if (LineOfSight.clear(turret.getLevel(), terminal, point)) return point;
        }
        return null;
    }

    @Override
    public boolean outOfAmmo(TurretBaseBlockEntity turret, WeaponState state) {
        return !turret.hasEnergy(cost(turret));
    }

    @Override
    public void onFire(FireContext context) {
        TurretBaseBlockEntity turret = context.turret();
        if (!turret.useEnergy(cost(turret))) return;
        List<LivingEntity> targets = new ArrayList<>(TargetSelector.findAll(turret, context.stats(), this, CANDIDATES));
        if (targets.isEmpty() && context.target() instanceof LivingEntity target) targets.add(target);
        if (targets.isEmpty()) return;
        RandomSource random = RandomSource.create(context.seed());
        for (int i = targets.size() - 1; i > 0; i--) targets.set(i, targets.set(random.nextInt(i + 1), targets.get(i))); // shuffle

        Vec3 terminal = turret.pivot(context.stats().data());
        DamageSource shock = BastionDamageTypes.of(context.level(), BastionDamageTypes.TURRET_SHOCK);
        // Fewer targets than bolts: they take the rest, at most bolts_per_target each; the leftover bolts are not thrown.
        int bolts = Math.min(Math.round(context.stats().data().param("bolts")),
                Math.round(context.stats().data().param("bolts_per_target")) * targets.size());
        List<TurretFireEvent.Shot> shots = new ArrayList<>(bolts);
        for (int i = 0; i < bolts; i++) {
            LivingEntity target = targets.get(i % targets.size());
            Vec3 at = target.getBoundingBox().getCenter();
            Hitscan.damage(target, shock, context.stats().damage());
            // The client follows the bolt's target by id, so it stays on a moving mob.
            shots.add(new TurretFireEvent.Shot(at, terminal.subtract(at).normalize(), TurretFireEvent.ENTITY, target.getId()));
        }
        ShotReport.send(context, this, 0, shots);
    }

    private static int cost(TurretBaseBlockEntity turret) {
        var data = turret.inventory().weaponData();
        return data == null ? Integer.MAX_VALUE : Math.round(data.param("energy_per_shot"));
    }
}
