package dev.bastion.weapon.types;

import dev.bastion.network.BastionNetwork;
import dev.bastion.network.SpinSync;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.Bullets;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.LeadSolver;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Machine Gun Turret, PLAN 7.2: rotary barrels spin up over spin_up_ticks (fire rate rises linearly to
 * 1/fire_interval) and down over spin_down_ticks, so a target that reappears before the barrels stop is
 * engaged at once. Bullets fly bullet_speed blocks per tick, led onto moving targets. Spread widens to hot_spread
 * above hot_spread_threshold heat; hits apply Slowness.
 */
public class MachineGunWeapon extends WeaponType {
    @Override
    public FireProfile fireProfile() {
        return FireProfile.PROJECTILE;
    }

    @Override
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        return LeadSolver.lead(turret, target, point, stats, stats.data().param("bullet_speed"));
    }

    @Override
    public void tick(TurretBaseBlockEntity turret, WeaponState state, StatSheet stats, boolean engaged) {
        WeaponData data = stats.data();
        state.spin = engaged ? Math.min(1, state.spin + 1 / data.param("spin_up_ticks"))
                : Math.max(0, state.spin - 1 / data.param("spin_down_ticks"));
        boolean significant = Math.abs(state.spin - state.syncedSpin) >= 0.1f
                || (state.spin == 0) != (state.syncedSpin == 0) || (state.spin == 1) != (state.syncedSpin == 1);
        if (significant && turret.getLevel() instanceof ServerLevel level) {
            state.syncedSpin = state.spin;
            BastionNetwork.sendNear(level, turret.getBlockPos(), new SpinSync(turret.getBlockPos(), state.spin));
        }
    }

    @Override
    public boolean ready(StatSheet stats, WeaponState state) {
        return state.spin >= stats.data().param("min_spin_to_fire");
    }

    /** Rate = spin x full rate, so the interval is the full-rate interval divided by spin. */
    @Override
    public float fireInterval(StatSheet stats, WeaponState state) {
        return stats.fireInterval() / Math.max(state.spin, 0.05f);
    }

    @Override
    public void onFire(FireContext context) {
        WeaponData data = context.stats().data();
        boolean hot = context.turret().heat() >= context.stats().maxHeat() * data.param("hot_spread_threshold");
        float spread = hot ? data.param("hot_spread") : context.stats().spread();
        Vec3 direction = Hitscan.spread(context.direction(), spread, RandomSource.create(context.seed()));
        float damage = context.stats().damage();
        // The barrel at the top fires: muzzle_0..3 in turn as the cluster rotates.
        context.state().muzzle = (context.state().muzzle + 1) % 4;
        Bullets.fire(context, this, context.state().muzzle, List.of(direction), data.param("bullet_speed"), context.stats().pierce(),
                Bullets.validTargets(context, this), (bullet, hit) -> {
                    Hitscan.damage(hit.getEntity(), context.level(), damage);
                    if (hit.getEntity() instanceof LivingEntity living) {
                        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) data.param("slowness_ticks"),
                                (int) data.param("slowness_amplifier"), false, false));
                    }
                });
    }
}
