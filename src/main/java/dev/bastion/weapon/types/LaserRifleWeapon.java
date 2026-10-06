package dev.bastion.weapon.types;

import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.targeting.TargetSelector;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Laser Rifle: holds aim for charge_ticks while light gathers at the emitter (client), then one beam that passes
 * through every valid target on its line, however many, and stops at the first block. Players, pets and passive mobs
 * on the line are untouched. One Laser Cell per shot.
 */
public class LaserRifleWeapon extends WeaponType {
    /** Effectively unlimited: every valid target the beam crosses before a block. */
    private static final int PIERCE_ALL = 255;

    @Override
    public FireProfile fireProfile() {
        return FireProfile.CHARGED;
    }

    @Override
    public int chargeTicks(StatSheet stats) {
        return Math.round(stats.data().param("charge_ticks"));
    }

    /** Body first, then head: a beam aimed at the body stays at body height through the targets lined up behind. */
    @Override
    @Nullable
    public Vec3 sight(TurretBaseBlockEntity turret, LivingEntity target, StatSheet stats) {
        return TargetSelector.firstVisible(turret, stats, target.getBoundingBox().getCenter(), target.getEyePosition());
    }

    @Override
    public void onFire(FireContext context) {
        StatSheet stats = context.stats();
        Hitscan.Trace trace = Hitscan.trace(context.level(), context.muzzle(), context.direction(), stats.range(),
                e -> Hitscan.canHit(e) && e instanceof LivingEntity living && TargetSelector.isCandidate(context.turret(), living, stats, this),
                PIERCE_ALL);
        DamageSource laser = BastionDamageTypes.of(context.level(), BastionDamageTypes.TURRET_LASER);
        // First shot: where the beam ends (a block or full range); then one per target it went through, for the impacts.
        List<TurretFireEvent.Shot> shots = new ArrayList<>();
        shots.add(ShotReport.of(context.level(), trace.end(), context.direction()));
        for (EntityHitResult hit : trace.entities()) {
            Hitscan.damage(hit.getEntity(), laser, stats.damage());
            shots.add(ShotReport.of(context.level(), hit, context.direction()));
        }
        ShotReport.send(context, this, 0, shots);
    }
}
