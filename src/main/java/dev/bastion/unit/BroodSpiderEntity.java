package dev.bastion.unit;

import dev.bastion.config.BastionConfig;
import dev.bastion.damage.BastionExplosion;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.TurretImpactEvent;
import dev.bastion.registry.BastionSounds;
import dev.bastion.registry.BastionEntities;
import dev.bastion.turret.TurretHitboxEntity;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.Splash;
import dev.bastion.weapon.types.MortarWeapon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
 * A Brood Cannon spider (PLAN "Turret 3x3"): a small robot hexapod on vanilla ground navigation. It hunts what its turret's
 * filter (copied when its capsule left) allows, on the ground and within reach, never a unit or a turret, the one fewest
 * other spiders are on first (they spread over a crowd); bites every biteInterval ticks with a short pounce; with nothing
 * to hunt for selfDestructTicks it bursts by itself, freeing its place (user 2026-10-24: idle spiders used to walk home
 * and fill the cap there, out of sight of what the turret shot at), the wait starting over whenever it has a target.
 * Its owner and trusted players hurt it only with trustedCanDamage, like a turret. When it dies its eyes blink red for
 * half a second, then it bursts: deathDamage to valid targets within deathRadius, no block damage.
 * Never saved, never despawned (TurretUnits: gone with its chunk).
 */
public class BroodSpiderEntity extends PathfinderMob implements TurretUnit, GeoEntity {
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.brood_spider.walk"),
            IDLE = RawAnimation.begin().thenLoop("animation.brood_spider.idle"), BITE = RawAnimation.begin().thenPlay("animation.brood_spider.bite"),
            EMERGE = RawAnimation.begin().thenPlay("animation.brood_spider.emerge");
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
    private SpiderStats stats = new SpiderStats(16, 0.27f, 8, 20, 16, 15, 4, 200);
    /** Ticks in a row with nothing to hunt. */
    private int biteCooldown, idle;
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
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new Chase());
        goalSelector.addGoal(2, new RandomStrollGoal(this, 0.7));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, this::canAttack) {
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
        });
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

    /** A path gets all the way to it. */
    private boolean reachable(LivingEntity entity) {
        Path path = getNavigation().createPath(entity, 0);
        return path != null && path.canReach();
    }

    @Override
    public BlockPos home() {
        return home;
    }

    @Override
    public void tick() {
        if (!level().isClientSide && !TurretUnits.ticked(this)) {
            discard(); // back from an unloaded chunk: gone, no blast
            return;
        }
        super.tick();
        if (biteCooldown > 0) biteCooldown--;
        if (level().isClientSide || !isAlive()) return;
        LivingEntity target = getTarget();
        idle = target != null && target.isAlive() ? 0 : idle + 1;
        if (idle >= stats.selfDestructTicks()) kill(); // nothing to hunt for a while: it bursts (tickDeath), freeing its place
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

    /** No tip-over and no vanilla poof: the eyes blink (client) for BURST_DELAY ticks, then the burst. */
    @Override
    protected void tickDeath() {
        if (++deathTime >= BURST_DELAY && !level().isClientSide && !isRemoved()) {
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

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 3, state -> {
            if (tickCount < EMERGE_TICKS) return state.setAndContinue(EMERGE);
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
                misses = path != null && path.canReach() ? 0 : misses + 1;
                if (misses >= GIVE_UP) {
                    passOver(target);
                    setTarget(null);
                    return;
                }
                getNavigation().moveTo(path, 1.2);
            }
            double reach = getBbWidth() * 2 * getBbWidth() * 2 + target.getBbWidth();
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
