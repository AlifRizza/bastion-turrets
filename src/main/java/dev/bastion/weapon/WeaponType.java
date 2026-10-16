package dev.bastion.weapon;

import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.targeting.TargetSelector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/** Per-weapon fire logic, entries of registry bastion:weapon_type (PLAN 4.3). Server side only. */
public abstract class WeaponType {
    /**
     * World position of a point on the weapon model, in model pixels around the pitch pivot (y up, -z forward), as the
     * turret aims now: launcher tube mouths. Model +X renders on the weapon's left (GeckoLib mirrors X), so the
     * right-hand offset is -x.
     */
    public static Vec3 modelPoint(TurretBaseBlockEntity turret, WeaponData data, Vec3 px) {
        Vec3 forward = Vec3.directionFromRotation(turret.pitch(), turret.yaw());
        Vec3 up = Vec3.directionFromRotation(turret.pitch() - 90, turret.yaw());
        Vec3 right = forward.cross(up);
        Vec3 local = right.scale(-px.x / 16).add(up.scale(px.y / 16)).add(forward.scale(-px.z / 16));
        return turret.pivot(data).add(turret.toWorld(local));
    }

    public abstract FireProfile fireProfile();

    /** Performs one shot: raycast(s), damage, and the TurretFireEvent for clients. */
    public abstract void onFire(FireContext context);

    /** Ticks spent in CHARGING before a shot; 0 skips the state. */
    public int chargeTicks(StatSheet stats) {
        return 0;
    }

    /**
     * True when {@code entity} is at least the mounted weapon's min_range from its pivot (the Rocket, Missile, Railgun, Mortar
     * and Plasma Cannon filters). Read live, so a shot still flying after its module was swapped out sees the new weapon:
     * one without min_range counts as 0, never an exception inside the level tick (review 2026-10-10).
     */
    protected static boolean outsideMinRange(Entity entity, TurretBaseBlockEntity turret) {
        WeaponData data = turret.inventory().weaponData();
        if (data == null) return false;
        double min = data.params().getOrDefault("min_range", 0f);
        return entity.distanceToSqr(turret.pivot(data)) >= min * min;
    }

    /** Extra per-weapon target filter on top of the GUI filter. */
    public boolean canTarget(Entity entity, TurretBaseBlockEntity turret) {
        return true;
    }

    /** Called every server tick while the weapon is mounted; {@code engaged} = has a target and ammo. */
    public void tick(TurretBaseBlockEntity turret, WeaponState state, StatSheet stats, boolean engaged) {
    }

    /**
     * True when the time between shots is the reload itself (Rocket Launcher: the next rocket is loaded after the last
     * left), so reload_speed modules shorten the fire interval too.
     */
    public boolean firesAsItReloads() {
        return false;
    }

    /** Order of valid targets, best first; {@code byFilter} is the GUI priority (Railgun: the biggest target first). */
    public Comparator<LivingEntity> targetOrder(Comparator<LivingEntity> byFilter) {
        return byFilter;
    }

    /** Ticks until the next shot; Machine Gun shortens it with spin. */
    public float fireInterval(StatSheet stats, WeaponState state) {
        return stats.fireInterval();
    }

    /**
     * Where the turret points for this target: {@code point} is the visible head/body point. Projectile weapons
     * lead it so the shot meets a moving target.
     */
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        return point;
    }

    /**
     * The point to engage on a target, or null when this weapon cannot engage it from here. Default: head or body
     * within the pitch limits and in line of sight from the muzzle.
     */
    @Nullable
    public Vec3 sight(TurretBaseBlockEntity turret, LivingEntity target, StatSheet stats) {
        return TargetSelector.aimPoint(turret, target, stats);
    }

    /** True when the weapon cannot fire again until refilled; drives the NO_AMMO state for weapons that load ahead (Missile Launcher). */
    public boolean outOfAmmo(TurretBaseBlockEntity turret, WeaponState state) {
        return false;
    }

    /** Whether the weapon may fire this tick (Machine Gun: barrels spun up enough). */
    public boolean ready(StatSheet stats, WeaponState state) {
        return true;
    }
}
