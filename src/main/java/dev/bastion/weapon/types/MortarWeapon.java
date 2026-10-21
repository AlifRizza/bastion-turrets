package dev.bastion.weapon.types;

import dev.bastion.projectile.TurretRocketEntity;
import dev.bastion.registry.BastionEntities;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.targeting.LineOfSight;
import dev.bastion.weapon.Ballistics;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FirePatches;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Mortar (Large Turret Base, user decision 2026-10-07): lobs a shell on a high arc (45-86 degrees up) at shell_speed,
 * pulled down by gravity, onto targets on the ground; flyers are never targeted. It fires over walls: only the arc
 * itself must be clear. No lead and no homing (user: it shells the spot where the target stood when it fired; a mob that
 * walks on is missed, it is meant for crowds). The shell bursts (damage within blast_radius, valid targets only) and
 * leaves a fire patch (FirePatches) that burns everyone standing in it.
 */
public class MortarWeapon extends WeaponType {
    /** Ticks between re-checks of the arc to the current target (a check is ~30 ray casts). */
    private static final int ARC_RECHECK = 10;

    @Override
    public FireProfile fireProfile() {
        return FireProfile.PROJECTILE;
    }

    @Override
    public boolean canTarget(Entity entity, TurretBaseBlockEntity turret) {
        return grounded(entity) && outsideMinRange(entity, turret);
    }

    /** Walking, swimming or between two steps; never a flyer, which a shell falling from above cannot catch. */
    static boolean grounded(Entity entity) {
        if (entity instanceof FlyingMob || entity instanceof FlyingAnimal || entity instanceof AmbientCreature || entity instanceof Blaze
                || entity instanceof Vex || entity instanceof Allay || entity instanceof WitherBoss || entity instanceof EnderDragon
                || entity.isNoGravity()) return false;
        return entity.onGround() || entity.isInWater() || entity.fallDistance < 2;
    }

    /** The target's feet, when a shell can reach them over a clear arc. */
    @Override
    @Nullable
    public Vec3 sight(TurretBaseBlockEntity turret, LivingEntity target, StatSheet stats) {
        Vec3 feet = target.position();
        Ballistics.Launch launch = Ballistics.solve(turret, stats.data(), feet);
        if (launch == null) return null;
        WeaponState state = turret.weaponState();
        long now = turret.getLevel().getGameTime();
        if (state.arcTarget != target.getId() || now - state.arcCheckedAt >= ARC_RECHECK) {
            state.arcTarget = target.getId();
            state.arcCheckedAt = now;
            state.arcClear = arcClear(turret.getLevel(), launch, stats.data().param("gravity"));
        }
        return state.arcClear ? feet : null;
    }

    /** Points the barrel along the launch that comes down where the target stands now. */
    @Override
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        Ballistics.Launch launch = Ballistics.solve(turret, stats.data(), point);
        return launch == null ? point : turret.pivot(stats.data()).add(launch.velocity().normalize().scale(8));
    }

    /** Overwatch: yaw toward the spot, elevation from the high arc; no aim inside min_range or past the range. */
    @Override
    @Nullable
    public Vec3 manualAim(TurretBaseBlockEntity turret, Vec3 point, StatSheet stats) {
        WeaponData data = stats.data();
        double distance = point.subtract(turret.pivot(data)).horizontalDistance();
        if (distance > stats.range() || distance < data.params().getOrDefault("min_range", 0f)) return null;
        Ballistics.Launch launch = Ballistics.solve(turret, data, point);
        return launch == null ? null : turret.pivot(data).add(launch.velocity().normalize().scale(8));
    }

    @Override
    public boolean ballistic() {
        return true;
    }

    @Override
    public void onFire(FireContext context) {
        StatSheet stats = context.stats();
        WeaponData data = stats.data();
        double speed = data.param("shell_speed"), gravity = data.param("gravity");
        // From the real muzzle at the moment of firing, so the barrel's aim tolerance costs no accuracy.
        // At the creature it was fired at, else (Overwatch) the spot under the controller's crosshair.
        Vec3 to = context.target() != null ? context.target().position() : context.manualPoint();
        Vec3 velocity = to != null ? Ballistics.highArc(context.muzzle(), to, speed, gravity) : null;
        if (velocity == null) velocity = context.direction().scale(speed);
        double flight = 2 * Math.max(velocity.y, 0.1) / gravity;
        TurretRocketEntity shell = new TurretRocketEntity(BastionEntities.TURRET_MORTAR_SHELL.get(), context.level(), context.turret().getBlockPos(),
                0, stats.damage(), data.param("blast_radius"), data.param("knockback"), (int) flight + 60)
                .ballistic((float) gravity, new FirePatches.Patch(Math.round(data.param("patch_size")), Math.round(data.param("fire_ticks")),
                        Math.round(data.param("burn_ticks")), data.param("burn_damage")));
        shell.setPos(context.muzzle());
        shell.launch(velocity);
        context.level().addFreshEntity(shell);
        ShotReport.send(context, this, 0, List.of());
    }

    /** Nothing solid on the arc, in steps of two ticks, up to just before it comes down on the target. */
    private static boolean arcClear(Level level, Ballistics.Launch launch, double gravity) {
        Vec3 last = launch.muzzle();
        for (double t = 2; t < launch.ticks() - 1.5; t += 2) {
            Vec3 next = launch.at(t, gravity);
            if (!LineOfSight.clear(level, last, next)) return false;
            last = next;
        }
        return true;
    }
}
