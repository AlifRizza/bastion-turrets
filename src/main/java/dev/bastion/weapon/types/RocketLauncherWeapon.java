package dev.bastion.weapon.types;

import dev.bastion.network.BastionNetwork;
import dev.bastion.network.RackSync;
import dev.bastion.projectile.TurretRocketEntity;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.LeadSolver;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Rocket Launcher Turret: dumb-fire rockets (a real projectile, not hitscan) that fly straight at rocket_speed,
 * lead moving targets and explode on contact. The blast only hurts what this turret may target. It never fires
 * at targets inside min_range, so it cannot catch itself in its own blast.
 * <p>
 * Four tubes. Single mode fires one rocket per fire interval and reloads the spent tube right after the shot (the
 * fire interval is the limit). Salvo mode (user 10-14) waits until all four rockets sit in their tubes, fires them in
 * the same tick, side by side, at the current target, then reloads one tube per fire interval: a salvo every four
 * intervals, the same damage per second as single mode, delivered at once. A reloaded rocket slides into its tube over
 * SLIDE_TICKS (the client animates exactly that and clanks when it seats); a salvo also waits for that, and the first
 * reload after a salvo comes early by the same amount, so the cycle stays four intervals.
 */
public class RocketLauncherWeapon extends WeaponType {
    public static final int TUBES = 4;
    private static final int ALL = (1 << TUBES) - 1;
    /** Single mode: ticks from a shot to its tube's reload (the tube animation's own pause before the rocket slides in). */
    private static final int SINGLE_RELOAD = 8;
    /** Ticks a reloaded rocket takes to slide into its tube (the client's tube animation runs exactly as long). */
    public static final int SLIDE_TICKS = 14;
    /** Salvo: ticks between the last rocket seating (its clank) and the four leaving. */
    private static final int SALVO_HOLD = 6;
    /** Tube mouths in model pixels around the pitch pivot, muzzle order (geo bones muzzle_0..3). */
    private static final Vec3[] TUBE_MOUTHS = {new Vec3(-2.15, 2.15, -10.8), new Vec3(2.15, 2.15, -10.8),
            new Vec3(2.15, -2.15, -10.8), new Vec3(-2.15, -2.15, -10.8)};

    @Override
    public FireProfile fireProfile() {
        return FireProfile.PROJECTILE;
    }

    @Override
    public boolean firesPerClick() {
        return true;
    }

    @Override
    public boolean firesAsItReloads() {
        return true;
    }

    /** Reloads spent tubes and keeps clients' view of them current (they slide each rocket in as its tube reloads). */
    @Override
    public void tick(TurretBaseBlockEntity turret, WeaponState state, StatSheet stats, boolean engaged) {
        if (!(turret.getLevel() instanceof ServerLevel level)) return;
        state.salvoMode = turret.salvo();
        if (state.seatTimer > 0) state.seatTimer--;
        for (int tube = 0; tube < TUBES; tube++) if (state.slideTicks[tube] > 0) state.slideTicks[tube]--;
        int interval = Math.max(1, Math.round(stats.fireInterval()));
        if (state.spentTubes == 0) { // primed for the next shot
            state.reloadTimer = state.salvoMode ? Math.max(1, interval - SLIDE_TICKS - SALVO_HOLD) : SINGLE_RELOAD;
        } else if (--state.reloadTimer <= 0) {
            int tube = Integer.numberOfTrailingZeros(state.spentTubes); // the lowest spent tube: its rocket starts sliding in
            state.spentTubes &= ~(1 << tube);
            state.slideTicks[tube] = SLIDE_TICKS;
            state.seatTimer = SLIDE_TICKS + SALVO_HOLD;
            state.reloadTimer = !state.salvoMode && Integer.bitCount(state.spentTubes) <= 1 ? SINGLE_RELOAD : interval;
        }
        if (state.spentTubes != state.syncedTubes) {
            state.syncedTubes = state.spentTubes;
            BastionNetwork.sendNear(level, turret.getBlockPos(), new RackSync(turret.getBlockPos(), state.spentTubes));
        }
    }

    /** Single: a loaded tube whose rocket has slid home (review 2026-10-22: clicks fired a half-seated one). Salvo: all four, the last one slid home. */
    @Override
    public boolean ready(StatSheet stats, WeaponState state) {
        if (!state.salvoMode) return seated(state) != 0;
        return state.spentTubes == 0 && state.seatTimer == 0;
    }

    /** Loaded tubes whose rocket has finished sliding in. */
    private static int seated(WeaponState state) {
        int seated = ~state.spentTubes & ALL;
        for (int tube = 0; tube < TUBES; tube++) if (state.slideTicks[tube] > 0) seated &= ~(1 << tube);
        return seated;
    }

    @Override
    public boolean canTarget(Entity entity, TurretBaseBlockEntity turret) {
        return outsideMinRange(entity, turret);
    }

    /** Lead: Targeting AI modules (lead_accuracy) close the gap to a perfect intercept. */
    @Override
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        return LeadSolver.lead(turret, target, point, stats, stats.data().param("rocket_speed"));
    }

    @Override
    public void onFire(FireContext context) {
        WeaponState state = context.state();
        if (state.spentTubes == ALL) return; // ready() keeps this from happening
        if (!state.salvoMode) {
            // Tubes fire in turn; after a mode switch the next one may still be empty (or its rocket still sliding in),
            // then the first seated one goes.
            int seated = seated(state);
            if (seated == 0) return;
            int tube = (seated >> state.muzzle & 1) != 0 ? state.muzzle : Integer.numberOfTrailingZeros(seated);
            launch(context, tube);
            state.muzzle = (tube + 1) % TUBES;
            return;
        }
        // Salvo: every tube at once, side by side, at the current target. The turret paid for the first rocket; each of
        // the others takes its own, and the salvo stops short when the ammo does.
        for (int tube = 0; tube < TUBES; tube++) {
            if (tube > 0 && !context.turret().takeOneAmmo(context.stats(), context.level())) break;
            launch(context, tube);
        }
    }

    /** One rocket out of {@code tube}'s mouth, flying the way the turret aims. */
    private void launch(FireContext context, int tube) {
        WeaponData data = context.stats().data();
        TurretRocketEntity rocket = new TurretRocketEntity(context.level(), context.turret().getBlockPos(), context.stats().damage(),
                data.param("splash_damage") * context.stats().damageScale(), data.param("splash_radius"), data.param("knockback"),
                Math.round(context.stats().range() / data.param("rocket_speed")) + 10);
        rocket.setPos(modelPoint(context.turret(), data, TUBE_MOUTHS[tube]));
        rocket.launch(context.direction().scale(data.param("rocket_speed")));
        context.level().addFreshEntity(rocket);
        context.state().spentTubes |= 1 << tube;
        ShotReport.send(context, this, tube, List.of());
    }
}
