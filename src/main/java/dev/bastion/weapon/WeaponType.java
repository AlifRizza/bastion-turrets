package dev.bastion.weapon;

import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.targeting.TargetSelector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Per-weapon fire logic, entries of registry bastion:weapon_type (PLAN 4.3). Server side only. */
public abstract class WeaponType {
    public abstract FireProfile fireProfile();

    /** Performs one shot: raycast(s), damage, and the TurretFireEvent for clients. */
    public abstract void onFire(FireContext context);

    /** Ticks spent in CHARGING before a shot; 0 skips the state. */
    public int chargeTicks(StatSheet stats) {
        return 0;
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
