package dev.bastion.weapon;

import dev.bastion.Bastion;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.BulletImpact;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.turret.targeting.TargetSelector;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Bullets with travel time for the Gun, Machine Gun, Shotgun and Sniper (user decision 2026-10-06): plain data the
 * server moves {@code speed} blocks every tick and traces like a short hitscan ray, no entities, so a base full of
 * turrets costs about what hitscan did. They hit only the turret's valid targets and stop at blocks. Clients get the
 * shot once (TurretFireEvent with the speed) and fly the tracer themselves; where each bullet ended follows as a
 * BulletImpact, in the same per-tick TurretEvents packet.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID)
public final class Bullets {
    /** Hard cap per level; a new bullet beyond it ends the oldest. */
    private static final int MAX_PER_LEVEL = 4096;
    private static final Map<ResourceKey<Level>, List<Bullet>> ACTIVE = new HashMap<>();
    private static final Set<Hit> TOUCHED = new LinkedHashSet<>();
    private static int nextId;

    private Bullets() {
    }

    /** What a weapon does to each entity a bullet hits; one instance per shot, shared by its pellets. */
    public interface Hit {
        void hit(Bullet bullet, EntityHitResult hit);

        /** After every tick in which this shot hit something (Shotgun: knockback summed over its pellets). */
        default void endTick() {
        }
    }

    public static final class Bullet {
        public final int id;
        /** Where the shot left the muzzle (Shotgun falloff measures from here). */
        public final Vec3 origin;
        /** Entities this bullet already hit before (0 for the first, Gun's Precision Lock applies to that one). */
        public int hits;
        private final ServerLevel level;
        private final Vec3 direction;
        private final double speed;
        private final Predicate<Entity> canHit;
        private final Hit onHit;
        private final Set<Integer> hitIds = new HashSet<>();
        private Vec3 pos;
        private double left;
        private int pierce;

        private Bullet(ServerLevel level, Vec3 origin, Vec3 direction, double speed, double range, int pierce, Predicate<Entity> canHit, Hit onHit) {
            this.id = nextId++;
            this.level = level;
            this.origin = origin;
            this.pos = origin;
            this.direction = direction;
            this.speed = speed;
            this.left = range;
            this.pierce = pierce;
            this.canHit = canHit;
            this.onHit = onHit;
        }
    }

    /**
     * Fires one bullet per direction from the context's muzzle and reports the shot. Each bullet moves once right
     * away, so a target at point blank is hit this tick, as with hitscan.
     */
    public static void fire(FireContext context, WeaponType type, int muzzle, List<Vec3> directions, double speed, int pierce,
                            Predicate<Entity> canHit, Hit onHit) {
        ServerLevel level = context.level();
        double range = context.stats().range();
        List<Bullet> active = ACTIVE.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        List<TurretFireEvent.Shot> shots = new ArrayList<>(directions.size());
        List<Bullet> fired = new ArrayList<>(directions.size());
        for (Vec3 direction : directions) {
            Bullet bullet = new Bullet(level, context.muzzle(), direction, speed, range, pierce, canHit, onHit);
            fired.add(bullet);
            // The path's far end; clients stop the tracer early when the impact for this id arrives.
            shots.add(new TurretFireEvent.Shot(context.muzzle().add(direction.scale(range)), direction.reverse(), TurretFireEvent.MISS, bullet.id));
        }
        ShotReport.send(context, type, muzzle, (float) speed, shots);
        for (Bullet bullet : fired) {
            if (!step(bullet)) continue;
            if (active.size() >= MAX_PER_LEVEL) end(active.remove(0), null);
            active.add(bullet);
        }
    }

    /** Moves a bullet one tick; false once it has ended (hit, blocked, out of range or into an unloaded chunk). */
    private static boolean step(Bullet bullet) {
        double length = Math.min(bullet.speed, bullet.left);
        Vec3 to = bullet.pos.add(bullet.direction.scale(length));
        if (!bullet.level.isLoaded(BlockPos.containing(to))) {
            end(bullet, null);
            return false;
        }
        Hitscan.Trace trace = Hitscan.trace(bullet.level, bullet.pos, bullet.direction, length,
                bullet.canHit.and(e -> !bullet.hitIds.contains(e.getId())), bullet.pierce);
        for (EntityHitResult hit : trace.entities()) {
            bullet.hitIds.add(hit.getEntity().getId());
            bullet.onHit.hit(bullet, hit);
            bullet.hits++;
            TOUCHED.add(bullet.onHit);
        }
        bullet.pierce -= trace.entities().size();
        if (bullet.pierce < 0 || trace.end().getType() == HitResult.Type.BLOCK) {
            end(bullet, trace.end());
            return false;
        }
        bullet.pos = to;
        bullet.left -= length;
        if (bullet.left > 1e-3) return true;
        end(bullet, null);
        return false;
    }

    /** Tells nearby clients where the bullet ended; null = it just ran out (the tracer flies its full path). */
    private static void end(Bullet bullet, HitResult hit) {
        if (hit == null) return;
        TurretFireEvent.Shot shot = ShotReport.of(bullet.level, hit, bullet.direction);
        BastionNetwork.sendNear(bullet.level, shot.end(), new BulletImpact(bullet.id, shot.end(), shot.normal(), shot.hit(), shot.blockState()));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!(event.level instanceof ServerLevel level)) return;
        if (event.phase == TickEvent.Phase.START) {
            List<Bullet> active = ACTIVE.get(level.dimension());
            if (active != null) active.removeIf(bullet -> !step(bullet));
            return;
        }
        // END: after this tick's turrets fired (Shotgun knockback summed over its pellets).
        for (Hit hit : TOUCHED) hit.endTick();
        TOUCHED.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.clear();
        TOUCHED.clear();
    }

    /** The turret's valid targets: bullets fly through everything else (villagers, pets, trusted players). */
    public static Predicate<Entity> validTargets(FireContext context, WeaponType type) {
        return e -> Hitscan.canHit(e) && e instanceof LivingEntity living
                && TargetSelector.isCandidate(context.turret(), living, context.stats(), type);
    }

    /** Active bullets in a level, for tests and the stress scene. */
    public static int active(ServerLevel level) {
        List<Bullet> active = ACTIVE.get(level.dimension());
        return active == null ? 0 : active.size();
    }
}
