package dev.bastion.unit;

import dev.bastion.config.BastionConfig;
import dev.bastion.damage.BastionExplosion;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.TurretImpactEvent;
import dev.bastion.registry.BastionEntities;
import dev.bastion.registry.BastionSounds;
import dev.bastion.turret.TurretHitboxEntity;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.Splash;
import dev.bastion.weapon.types.MortarWeapon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A Brood Cannon spider (PLAN "Turret 3x3"): a small robot hexapod that climbs walls like a vanilla spider. It hunts what its turret's
 * filter (copied when its capsule left) allows, on the ground and within reach, never a unit or a turret, the one fewest
 * other spiders are on first (they spread over a crowd); bites every biteInterval ticks with a short pounce; with nothing
 * to hunt for selfDestructTicks it bursts by itself, freeing its place (user 2026-10-24: idle spiders used to walk home
 * and fill the cap there, out of sight of what the turret shot at), the wait starting over whenever it has a target.
 * Prey 2 to leapRange blocks off it leaps at (steered onto it in the air, on any ground) and bites on arrival, every
 * leapCooldown ticks (user 2026-10-26).
 * Its owner and trusted players hurt it only with trustedCanDamage, like a turret. At 0 HP its eyes blink red and it
 * leaps at the nearest valid target within leapRange to burst on it (user 2026-10-26), else bursts where it is after half
 * a second: deathDamage to valid targets within deathRadius, no block damage. An idle self-destruct bursts where it is.
 * Never saved, never despawned (TurretUnits: gone with its chunk).
 */
public class BroodSpiderEntity extends PathfinderMob implements TurretUnit, GeoEntity {
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.brood_spider.walk"),
            IDLE = RawAnimation.begin().thenLoop("animation.brood_spider.idle"), BITE = RawAnimation.begin().thenPlay("animation.brood_spider.bite"),
            EMERGE = RawAnimation.begin().thenPlay("animation.brood_spider.emerge"),
            LEAP = RawAnimation.begin().thenPlay("animation.brood_spider.leap");
    /** Against a wall (vanilla spider climbing), and in a leap (the leap clip): set by the server, read by both sides. */
    private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(BroodSpiderEntity.class, EntityDataSerializers.BOOLEAN),
            LEAPING = SynchedEntityData.defineId(BroodSpiderEntity.class, EntityDataSerializers.BOOLEAN);
    /**
     * Blocks sideways from its prey a path may stop and still count: the rest is a climb up what the prey stands on (a
     * pillar, a wall); a moat or a gap it cannot climb leaves the path end farther off.
     */
    private static final double CLIMB_REACH = 2.0;
    /** Nearest prey it leaps at (spec: 2 to 5 blocks): nearer it walks up and bites (review M4). */
    private static final double LEAP_MIN = 2.0;
    /** Least upward speed of a leap off the ground: a leap down would otherwise push into the ground and slide (review M5). */
    private static final double LIFT = 0.1;
    /** Ticks it keeps a target out of sight: climbing a wall hides what stands on top (vanilla: 60). */
    private static final int UNSEEN_TICKS = 200;
    /** Ticks a fresh spider spends unfolding its legs (the emerge clip). */
    private static final int EMERGE_TICKS = 10;
    /** Ticks from death to the burst (the eyes blink red meanwhile). */
    public static final int BURST_DELAY = 10;
    /** Ticks between new paths to its target (PLAN Detail builder: at most every 20). */
    private static final int REPATH = 20;
    /** Most path searches one target search makes (nearest candidates first). */
    private static final int REACH_CHECKS = 3;
    /** New paths in a row that fall short of the target before the spider gives it up. */
    private static final int GIVE_UP = 2;
    private BlockPos home = BlockPos.ZERO;
    private TargetFilter filter = new TargetFilter();
    @Nullable
    private UUID owner;
    private SpiderStats stats = new SpiderStats(16, 0.27f, 8, 20, 16, 15, 4, 200, 5, 8, 60);
    /** Ticks in a row with nothing to hunt. */
    private int biteCooldown, idle, leapCooldown;
    /** Ticks since a leap left the ground (counts while LEAPING); a dying leap bursts on landing. */
    private int leapTicks;
    /** Where the current leap lands. */
    private Vec3 leapTo = Vec3.ZERO;
    /** Killed by its own idle timer: it bursts where it is, no leap. */
    private boolean selfDestruct;
    /** At 0 HP: it leapt at an enemy and bursts on it (touching one or landing). */
    private boolean dyingLeap;
    /** Ticks a target its chase gave up on (out of reach) is passed over in searches. */
    private static final int UNREACHABLE_TICKS = 100;
    /** Entity id → game time a target its chase gave up on may be tried again. */
    private final Map<Integer, Long> unreachable = new HashMap<>();
    /**
     * Where in the ranked candidates the next search starts its REACH_CHECKS path tries: past the ones the last search
     * found no path to, so a few it can never reach (fewest spiders on them, so first) do not starve it; 0 after a find.
     */
    private int probe;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BroodSpiderEntity(EntityType<? extends BroodSpiderEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 16).add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    /** A spider at {@code at}, not yet in the level. */
    public static BroodSpiderEntity create(ServerLevel level, Vec3 at, BlockPos home, TargetFilter filter, @Nullable UUID owner, SpiderStats stats) {
        BroodSpiderEntity spider = new BroodSpiderEntity(BastionEntities.BROOD_SPIDER.get(), level);
        spider.home = home.immutable();
        spider.filter = filter.copy();
        spider.owner = owner;
        spider.stats = stats;
        spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(stats.health());
        spider.setHealth(stats.health());
        spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(stats.speed());
        spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(stats.bite());
        spider.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(stats.sight());
        spider.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
        return spider;
    }

    public static BroodSpiderEntity spawn(ServerLevel level, Vec3 at, BlockPos home, TargetFilter filter, @Nullable UUID owner, SpiderStats stats) {
        BroodSpiderEntity spider = create(level, at, home, filter, owner, stats);
        level.addFreshEntity(spider);
        TurretUnits.ticked(spider); // counted from now: a turret ticking before the spider's first tick must not see room
        return spider;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(CLIMBING, false);
        entityData.define(LEAPING, false);
    }

    /**
     * Paths along the ground, then straight at its prey and up any wall in the way (vanilla spider navigation), but only
     * while it hunts: vanilla keeps steering at the last thing it made a path to, a candidate its search turned down or a
     * target it gave up on (a husk across a moat) included (final review 2026-10-26); never in a leap (aiStep steers it).
     */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level) {
            @Override
            public void tick() {
                if (!leaping() && (!isDone() || BroodSpiderEntity.this.getTarget() != null)) super.tick();
            }
        };
    }

    @Override
    public boolean onClimbable() {
        return entityData.get(CLIMBING);
    }

    public boolean leaping() {
        return entityData.get(LEAPING);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new Chase());
        goalSelector.addGoal(2, new RandomStrollGoal(this, 0.7));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<LivingEntity>(this, LivingEntity.class, 10, true, false, this::canAttack) {
            /** Prey high up a wall or tower too (vanilla looks only 4 blocks up or down): it climbs any height. */
            @Override
            protected AABB getTargetSearchArea(double distance) {
                return mob.getBoundingBox().inflate(distance);
            }

            /**
             * The candidate fewest other spiders are on, the nearest of those, that a path reaches, at most REACH_CHECKS
             * path searches a search (the next search tries the next ones): 1.20.1's findTarget ignores mustReach, so a
             * husk across lava or up on a ledge would hold a spider at the edge for ever.
             */
            @Override
            protected void findTarget() {
                AABB area = getTargetSearchArea(getFollowDistance());
                long now = level().getGameTime();
                unreachable.values().removeIf(until -> until <= now);
                List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, area, e -> targetConditions.test(mob, e) && !unreachable.containsKey(e.getId()));
                Map<LivingEntity, Integer> taken = taken(level(), area.inflate(8), BroodSpiderEntity.this);
                near.sort(Comparator.<LivingEntity>comparingInt(e -> taken.getOrDefault(e, 0)).thenComparingDouble(e -> e.distanceToSqr(mob)));
                target = null;
                int from = near.isEmpty() ? 0 : probe % near.size();
                for (int i = 0; i < Math.min(REACH_CHECKS, near.size()); i++) {
                    LivingEntity candidate = near.get((from + i) % near.size());
                    if (reachable(candidate)) {
                        target = candidate;
                        probe = 0;
                        return;
                    }
                }
                probe = from + REACH_CHECKS;
            }
        }.setUnseenMemoryTicks(UNSEEN_TICKS));
    }

    /** How many spiders in {@code area} (but {@code except}) are on each creature: the Brood spreads over a crowd. */
    public static Map<LivingEntity, Integer> taken(Level level, AABB area, @Nullable BroodSpiderEntity except) {
        Map<LivingEntity, Integer> taken = new IdentityHashMap<>();
        for (BroodSpiderEntity spider : level.getEntitiesOfClass(BroodSpiderEntity.class, area, s -> s != except && s.isAlive())) {
            if (spider.getTarget() != null) taken.merge(spider.getTarget(), 1, Integer::sum);
        }
        return taken;
    }

    /** What its blast may hit: its turret's valid targets, never a unit, a turret or a dead thing. */
    public boolean affects(Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() && !(entity instanceof TurretUnit) && !(entity instanceof TurretHitboxEntity)
                && filter.test(living, owner);
    }

    /** What it hunts: a valid target on the ground (it cannot catch flyers). */
    @Override
    public boolean canAttack(LivingEntity entity) {
        return super.canAttack(entity) && affects(entity) && MortarWeapon.grounded(entity);
    }

    /** No path reached it: skip it in searches for UNREACHABLE_TICKS. */
    private void passOver(LivingEntity entity) {
        unreachable.put(entity.getId(), level().getGameTime() + UNREACHABLE_TICKS);
    }

    /**
     * A path gets all the way to it, or to the foot of what it stands on (the prey at least a block higher), and the
     * spider climbs the rest; prey at its own level past a gap or a lava trench does not count.
     */
    private boolean reachable(LivingEntity entity) {
        return reaches(getNavigation().createPath(entity, 0), entity);
    }

    private static boolean reaches(@Nullable Path path, LivingEntity entity) {
        if (path == null) return false;
        if (path.canReach()) return true;
        Node end = path.getEndNode();
        return end != null && entity.getY() >= end.y + 1 && Math.abs(end.x + 0.5 - entity.getX()) <= CLIMB_REACH
                && Math.abs(end.z + 0.5 - entity.getZ()) <= CLIMB_REACH;
    }

    /**
     * Launch velocity that carries it from {@code from} to {@code to} in {@code ticks} ticks of vanilla flight: each tick it
     * moves, then gravity 0.08 and drag 0.98 up and down, 0.91 sideways (the ground's 0.546 after a take-off from the ground).
     * Off the ground it rises at least LIFT.
     */
    static Vec3 leapVelocity(Vec3 from, Vec3 to, int ticks, boolean grounded) {
        double side = 0, keep = 1, up0 = 0, up1 = 0, v0 = 0, v1 = 1;
        for (int i = 0; i < ticks; i++) {
            side += keep;
            keep *= i == 0 && grounded ? 0.546 : 0.91;
            up0 += v0;
            up1 += v1;
            v0 = (v0 - 0.08) * 0.98;
            v1 = (v1 - 0.08) * 0.98;
        }
        Vec3 d = to.subtract(from);
        double vy = (d.y - up0) / (up1 - up0);
        return new Vec3(d.x / side, grounded ? Math.max(vy, LIFT) : vy, d.z / side);
    }

    /** Off the ground at {@code to} in leapTicks: the leap clip plays until it lands. */
    private void leapAt(Vec3 to) {
        leapTo = to;
        setDeltaMovement(leapVelocity(position(), to, stats.leapTicks(), onGround()));
        hasImpulse = true;
        leapTicks = 0;
        entityData.set(LEAPING, true);
        entityData.set(CLIMBING, false); // climbing, its first tick would clamp it to a hop off the wall (review M1)
        getNavigation().stop();
        playSound(BastionSounds.SPIDER_LEAP.get(), 0.8f, 0.9f + random.nextFloat() * 0.2f);
    }

    /** The nearest valid target it can see within leapRange, for a dying leap. */
    @Nullable
    private LivingEntity nearestPrey() {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(stats.leapRange()), e -> affects(e) && hasLineOfSight(e)
                        && e.distanceTo(this) <= stats.leapRange()).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(this))).orElse(null);
    }

    @Override
    public BlockPos home() {
        return home;
    }

    /**
     * In the air on a leap it is steered at where the leap lands, on time (past it: straight down onto it): ice, slime,
     * soul sand, a leap down or a bite on the way throw the take-off's sum off (review M5).
     */
    @Override
    public void aiStep() {
        if (!level().isClientSide && leaping() && leapTicks > 0 && !onGround()) {
            Vec3 v = leapVelocity(position(), leapTo, Math.max(1, stats.leapTicks() - leapTicks), false);
            setDeltaMovement(v.x, getDeltaMovement().y, v.z);
        }
        super.aiStep();
    }

    @Override
    public void tick() {
        if (!level().isClientSide && !TurretUnits.ticked(this)) {
            discard(); // back from an unloaded chunk: gone, no blast
            return;
        }
        super.tick();
        if (biteCooldown > 0) biteCooldown--;
        if (leapCooldown > 0) leapCooldown--;
        if (level().isClientSide) return;
        entityData.set(CLIMBING, horizontalCollision);
        // Landed, or against a wall (it climbs) or in water: the leap is over (review M9).
        if (leaping() && ++leapTicks > 2 && (onGround() || horizontalCollision || isInWater() || isInLava())) entityData.set(LEAPING, false);
        if (!isAlive()) return;
        LivingEntity target = getTarget();
        idle = target != null && target.isAlive() ? 0 : idle + 1;
        if (idle >= stats.selfDestructTicks()) { // nothing to hunt for a while: it bursts where it is (tickDeath), freeing its place
            selfDestruct = true;
            kill();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        TurretUnits.forget(this);
        super.remove(reason);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // As a turret (canBeDamagedBy): its owner and trusted players only with trustedCanDamage.
        if (source.getEntity() instanceof Player player && !BastionConfig.TRUSTED_CAN_DAMAGE.get() && owner != null && filter.isTrusted(player, owner)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /**
     * No tip-over and no vanilla poof: the eyes blink (client) while it leaps or for BURST_DELAY ticks, then the burst. On
     * its first death tick it leaps at the nearest valid target in reach (none, or its idle timer: it stays); here, not in
     * die(): knockback lands after the killing blow, and a death a mod cancels never ticks (review M3, M8). A dying leap
     * bursts the moment it touches a valid target or lands, at the latest a few ticks after its flight time.
     */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level().isClientSide || isRemoved()) return;
        LivingEntity prey = deathTime == 1 && !selfDestruct ? nearestPrey() : null;
        if (prey != null) {
            dyingLeap = true;
            leapAt(prey.position());
        }
        boolean burst = dyingLeap
                ? !level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.3), this::affects).isEmpty()
                        || deathTime > 2 && onGround() || deathTime >= stats.leapTicks() + 6
                : deathTime >= BURST_DELAY;
        if (burst) {
            burst((ServerLevel) level());
            remove(RemovalReason.KILLED);
        }
    }

    private void burst(ServerLevel level) {
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BastionExplosion.TURRET_BLAST), this);
        Vec3 at = getBoundingBox().getCenter();
        for (LivingEntity hit : Splash.around(level, at, stats.deathRadius(), this::affects, this)) Hitscan.damage(hit, source, stats.deathDamage());
        BastionNetwork.sendNear(level, blockPosition(), new TurretImpactEvent(at, TurretImpactEvent.SPIDER_BURST, stats.deathRadius(), Vec3.ZERO, getId()));
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** A portal would recreate it without its home, filter and numbers (never saved, never cloned). */
    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    /** A lead would be lost with it when its chunk unloads. */
    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    @Nullable
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        return null;
    }

    /** Its legs skitter, walking or climbing (user 2026-10-27): quiet, since a dozen may run at once. */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(BastionSounds.SPIDER_STEP.get(), 0.35f, 0.9f + random.nextFloat() * 0.2f);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 3, state -> {
            if (tickCount < EMERGE_TICKS) return state.setAndContinue(EMERGE);
            if (leaping()) return state.setAndContinue(LEAP);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "bite", 0, state -> {
            if (swinging) return state.setAndContinue(BITE);
            state.getController().forceAnimationReset();
            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /**
     * Runs at its target, a new path at most every REPATH ticks; in reach and off cooldown it pounces and bites. A target
     * that GIVE_UP paths in a row fall short of (it climbed out of reach) is dropped and passed over for a while.
     */
    private final class Chase extends Goal {
        private int repath, misses;
        /** Its height at the last repath: risen since, off the ground, it is climbing (no ground path then). */
        private double lastY;

        Chase() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive() && canAttack(target);
        }

        @Override
        public void start() {
            repath = 0;
            misses = 0;
            lastY = getY();
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30, 30);
            if (--repath <= 0) {
                repath = REPATH;
                Path path = getNavigation().createPath(target, 0);
                // Vanilla makes no path off the ground: halfway up a wall that is a climb, not a miss (it keeps going up
                // after the target, createPath having updated where to). Stuck under an overhang it does not rise, so it
                // still gives up.
                boolean climbing = path == null && !onGround() && getY() > lastY + 0.5;
                lastY = getY();
                if (!climbing) {
                    misses = reaches(path, target) ? 0 : misses + 1;
                    if (misses >= GIVE_UP) {
                        passOver(target);
                        setTarget(null);
                        return;
                    }
                    getNavigation().moveTo(path, 1.2);
                }
            }
            double reach = getBbWidth() * 2 * getBbWidth() * 2 + target.getBbWidth();
            double off = distanceTo(target);
            if (leapCooldown <= 0 && onGround() && !leaping() && off >= LEAP_MIN && off <= stats.leapRange() && hasLineOfSight(target)) {
                leapCooldown = stats.leapCooldown(); // it bites on arrival, the bite cooldown permitting (review M4: no extra bite)
                leapAt(target.position());
                repath = REPATH; // no new path mid-air
            }
            if (biteCooldown <= 0 && distanceToSqr(target) <= reach) {
                biteCooldown = stats.biteInterval();
                Vec3 lunge = target.position().subtract(position()).multiply(1, 0, 1);
                if (lunge.lengthSqr() > 1e-4) setDeltaMovement(lunge.normalize().scale(0.3).add(0, 0.25, 0));
                swing(InteractionHand.MAIN_HAND);
                doHurtTarget(target);
                playSound(BastionSounds.SPIDER_BITE.get(), 0.7f, 0.9f + random.nextFloat() * 0.2f);
            }
        }
    }
}
