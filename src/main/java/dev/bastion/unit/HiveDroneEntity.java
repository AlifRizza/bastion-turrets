package dev.bastion.unit;

import dev.bastion.config.BastionConfig;
import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.damage.BastionExplosion;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.TurretImpactEvent;
import dev.bastion.registry.BastionEntities;
import dev.bastion.turret.TurretHitboxEntity;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.Splash;
import dev.bastion.weapon.types.HiveCarrierWeapon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
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
import java.util.List;
import java.util.UUID;

/**
 * A Hive Carrier drone (PLAN "Turret 3x3"): a quadcopter with no gravity and no pathfinding. Each tick its state picks a
 * point to fly to and it steers there through vanilla movement (which keeps it out of blocks), climbing over what it
 * runs into and keeping clear of the ground. LAUNCH: out of its bay level through its hole, then straight up clear of the
 * hive; PATROL: round its carrier; ENGAGE:
 * hovering off its target, shooting it ammo bullets at shotInterval (hitscan, valid targets only, flyers too); RETURN:
 * high over a point out in front of its hole (blocked: the hole's mouth, else straight over its bay), straight down to it
 * (clear of the wall), then level in through the hole to its bay, where it docks (HiveCarrierWeapon counts it) — after idleTicks with nothing to shoot, or empty;
 * KAMIKAZE: out of bullets or below kamikazeHealth with a target left, it dives into it and bursts (kamikazeDamage within
 * kamikazeRadius, valid targets only). Its carrier gone, it keeps fighting and dives when empty. Never saved
 * (TurretUnits: gone with its chunk). Shot down with a valid target in sight within reach, it makes that dive anyway
 * and bursts on it (user 2026-10-27: a big hit used to drop most of them short); with none, it falls and is gone without
 * a blast.
 */
public class HiveDroneEntity extends PathfinderMob implements TurretUnit, GeoEntity {
    public enum State {LAUNCH, PATROL, ENGAGE, RETURN, KAMIKAZE}

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(HiveDroneEntity.class, EntityDataSerializers.BYTE);
    private static final RawAnimation HOVER = RawAnimation.begin().thenLoop("animation.hive_drone.hover");
    /**
     * Blocks from its bay out to its hole's mouth, away from its carrier's middle: past the hive's wall (the bays stand 0.75
     * from the middle, the wall 1.3; HiveCarrierWeapon.BAYS). A drone over its home's middle goes this far straight up.
     */
    public static final double EXIT = 1.4;
    /**
     * Blocks from its bay to where it comes down on its way home, out in front of its hole; and how high it climbs out of
     * the hive and comes in on its way home (over the hive's top, ~2 blocks).
     */
    private static final double APPROACH = EXIT + 1.5, APPROACH_HEIGHT = 3;
    /** Height over its carrier it patrols at, and its least height over the ground. */
    private static final double PATROL_HEIGHT = 8, CLEARANCE = 3;
    /** Ticks it tries to get out through its hole; it gives up sooner on running into a block (it then patrols, idles, docks). */
    private static final int LAUNCH_TICKS = 40;
    /**
     * A drone home this close (blocks, sideways) that gets no nearer its bay for STUCK_TICKS docks anyway: back over its
     * carrier's roof, or a ceiling, it may have no way down into the bay.
     */
    private static final double NEAR_HOME = 4;
    private static final int STUCK_TICKS = 40;
    /** How close to its bay counts as docked, and to its target as a hit for the dive. */
    private static final double DOCK = 0.6, DIVE_HIT = 1.2;
    /** Ticks a shot-down drone's last dive may take before it bursts wherever it is. */
    private static final int DYING_DIVE_TICKS = 60;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private BlockPos home = BlockPos.ZERO;
    /** Its bay, its hole's mouth (LAUNCH), the point out in front of the hole it comes home by, and where it comes down this tick (RETURN, homeBy). */
    private Vec3 bay = Vec3.ZERO, mouth = Vec3.ZERO, approach = Vec3.ZERO, down = Vec3.ZERO;
    private TargetFilter filter = new TargetFilter();
    @Nullable
    private UUID owner;
    private DroneStats stats = new DroneStats(20, 2, 120, 5, 16, 32, 0.25f, 15, 4.5f, 0.45f, 100);
    private int ammo, idle, shotCooldown, stuck;
    /** Shot down: the target of its last dive, or null (it drops). */
    @Nullable
    private LivingEntity dyingDive;
    private double lastReturnDistance = Double.MAX_VALUE;
    /** In LAUNCH: it is out through its hole and climbs. In RETURN: it came down in front of its hole and flies in to the bay. */
    private boolean cleared, entering;

    public HiveDroneEntity(EntityType<? extends HiveDroneEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.FOLLOW_RANGE, 16);
    }

    /**
     * Whether a drone can use the doorway behind {@code bay} of the carrier at {@code home}: room for it at the doorway's
     * mouth, at the point out in front it comes home by, and over both where it climbs out and comes in.
     */
    public static boolean doorwayClear(Level level, Vec3 bay, BlockPos home) {
        Vec3 away = away(bay, home);
        EntityType<HiveDroneEntity> type = BastionEntities.HIVE_DRONE.get();
        for (Vec3 p : List.of(bay.add(away.scale(EXIT)), bay.add(away.scale(APPROACH)))) {
            if (!level.noCollision(type.getAABB(p.x, p.y, p.z).expandTowards(0, APPROACH_HEIGHT, 0))) return false;
        }
        return true;
    }

    /** Out of its bay away from its carrier's middle, level; straight up for a bay over the middle. */
    private static Vec3 away(Vec3 bay, BlockPos home) {
        Vec3 away = new Vec3(bay.x - (home.getX() + 0.5), 0, bay.z - (home.getZ() + 0.5));
        return away.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : away.normalize();
    }

    /** A drone leaving its bay at {@code at} with {@code ammo} bullets. */
    public static HiveDroneEntity spawn(ServerLevel level, Vec3 at, BlockPos home, TargetFilter filter, @Nullable UUID owner, DroneStats stats, int ammo) {
        HiveDroneEntity drone = new HiveDroneEntity(BastionEntities.HIVE_DRONE.get(), level);
        drone.home = home.immutable();
        drone.bay = at;
        Vec3 away = away(at, home);
        drone.mouth = at.add(away.scale(EXIT));
        drone.approach = at.add(away.scale(APPROACH));
        drone.filter = filter.copy();
        drone.owner = owner;
        drone.stats = stats;
        drone.ammo = ammo;
        drone.getAttribute(Attributes.MAX_HEALTH).setBaseValue(stats.health());
        drone.setHealth(stats.health());
        drone.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(stats.shotRange());
        drone.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
        level.addFreshEntity(drone);
        TurretUnits.ticked(drone); // counted from now
        return drone;
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, this::canShoot) {
            /** Its whole reach up and down too: vanilla looks only 4 blocks up or down, and a drone patrols 8 over its carrier. */
            @Override
            protected AABB getTargetSearchArea(double distance) {
                return mob.getBoundingBox().inflate(distance);
            }
        });
    }

    /** What it shoots and what its dive hurts: its carrier's valid targets, flyers too; never a unit or a turret. */
    public boolean canShoot(LivingEntity entity) {
        return entity.isAlive() && !(entity instanceof TurretUnit) && !(entity instanceof TurretHitboxEntity) && filter.test(entity, owner);
    }

    private boolean affects(Entity entity) {
        return entity instanceof LivingEntity living && canShoot(living);
    }

    public int ammo() {
        return ammo;
    }

    public State state() {
        return State.values()[entityData.get(STATE)];
    }

    private void setState(State state) {
        entityData.set(STATE, (byte) state.ordinal());
    }

    @Override
    public BlockPos home() {
        return home;
    }

    /** Its carrier still stands: a Hive Carrier at home (another module there counts as the carrier gone). */
    private boolean homeStands() {
        return HiveCarrierWeapon.isCarrier(level(), home);
    }

    @Override
    public void tick() {
        if (!level().isClientSide && !TurretUnits.ticked(this)) {
            discard(); // back from an unloaded chunk: gone
            return;
        }
        super.tick();
    }

    @Override
    protected void customServerAiStep() {
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || !canShoot(target))) {
            setTarget(null);
            target = null;
        }
        idle = target == null ? idle + 1 : 0;
        if (shotCooldown > 0) shotCooldown--;
        if (position().distanceTo(mouth) < 0.5) cleared = true;
        State previous = state(), state = next(previous, target);
        setState(state);
        down = state == State.RETURN ? homeBy() : approach;
        if (state != State.RETURN) { // a fresh return measures its progress afresh
            stuck = 0;
            lastReturnDistance = Double.MAX_VALUE;
            entering = false;
        } else if (position().distanceTo(down) < 0.6 && Math.abs(getY() - down.y) < 0.15 && Math.abs(getDeltaMovement().y) < 0.03) {
            entering = true; // settled at the hole's height first: still sinking, it would catch the base's edge on the way in
        }
        Vec3 point = switch (state) {
            case LAUNCH -> cleared ? mouth.add(0, APPROACH_HEIGHT, 0) : mouth;
            case PATROL -> patrolPoint();
            case ENGAGE -> standOff(target);
            case RETURN -> entering ? bay : horizontal(position(), down) > 0.6
                    ? new Vec3(down.x, Math.max(down.y + APPROACH_HEIGHT, getY()), down.z) : down;
            case KAMIKAZE -> target.getBoundingBox().getCenter();
        };
        steer(point, state);
        if (target != null) getLookControl().setLookAt(target, 30, 30);
        if (state == State.ENGAGE) shoot(target);
        // A collision counts only when the last move was a dive: one left over from patrolling against a ceiling or in
        // leaves would burst it far from its target.
        boolean diveHitSomething = previous == State.KAMIKAZE && (horizontalCollision || verticalCollision);
        if (state == State.KAMIKAZE && (distanceToSqr(target.getBoundingBox().getCenter()) < DIVE_HIT * DIVE_HIT || diveHitSomething)) {
            burst((ServerLevel) level());
        } else if (state == State.RETURN && (distanceToSqr(bay) < DOCK * DOCK || stuckNearHome())) {
            dock();
        }
    }

    /**
     * Where it comes down on its way home: the point out in front of its hole; with something built there, the hole's
     * mouth; with that blocked too, straight over its bay (down through the hive's roof: the last resort).
     */
    private Vec3 homeBy() {
        for (Vec3 p : List.of(approach, mouth)) {
            if (level().noCollision(this, getType().getAABB(p.x, p.y, p.z))) return p;
        }
        return bay;
    }

    /** In RETURN: no nearer its bay for STUCK_TICKS while within NEAR_HOME of it sideways. */
    private boolean stuckNearHome() {
        double distance = position().distanceTo(bay);
        stuck = distance < lastReturnDistance - 0.05 ? 0 : stuck + 1;
        lastReturnDistance = Math.min(lastReturnDistance, distance);
        return stuck >= STUCK_TICKS && horizontal(position(), bay) < NEAR_HOME;
    }

    private State next(State state, @Nullable LivingEntity target) {
        // Out through its hole and up clear of the hive first, even with a target: straight at it would take it through the
        // hive's wall or roof.
        if (state == State.LAUNCH && (!cleared || getY() < mouth.y + APPROACH_HEIGHT - 0.5) && tickCount < LAUNCH_TICKS && !horizontalCollision
                && !verticalCollision) {
            return State.LAUNCH;
        }
        if (target != null && (ammo <= 0 || getHealth() < getMaxHealth() * stats.kamikazeHealth())) return State.KAMIKAZE;
        if (target != null) return State.ENGAGE;
        if ((ammo <= 0 || idle >= stats.idleTicks() || state == State.RETURN) && homeStands()) return State.RETURN;
        return State.PATROL;
    }

    /** A point on a circle of half its patrol radius round its carrier, PATROL_HEIGHT over it, moving round with time. */
    private Vec3 patrolPoint() {
        if (!homeStands() && idle > stats.idleTicks()) return position(); // no carrier: it hovers where it is
        double angle = level().getGameTime() * 0.02 + getId() * 2.1;
        double r = stats.patrolRadius() / 2;
        return Vec3.atBottomCenterOf(home).add(Math.cos(angle) * r, PATROL_HEIGHT, Math.sin(angle) * r);
    }

    /** Off its target at 60 % of its reach, 4 blocks over it, on the side it came from. */
    private Vec3 standOff(LivingEntity target) {
        Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
        return target.position().add(away.scale(stats.shotRange() * 0.6)).add(0, 4, 0);
    }

    /** Toward {@code point}, slowing as it gets there; climbing over what it runs into and keeping clear of the ground. */
    private void steer(Vec3 point, State state) {
        Vec3 to = point.subtract(position());
        double distance = to.length();
        double speed = stats.speed() * (state == State.KAMIKAZE ? 2 : 1);
        Vec3 want = distance < 1e-3 ? Vec3.ZERO : to.scale(Math.min(speed, distance * 0.3) / distance);
        // Through its hole (out, or down to it and in) it flies low over the base: no climbing clear of the ground then.
        boolean landing = state == State.KAMIKAZE || state == State.LAUNCH && !cleared || state == State.RETURN && (entering || horizontal(position(), down) <= 0.6);
        if (horizontalCollision) want = new Vec3(want.x * 0.3, speed, want.z * 0.3);
        else if (!landing && level().clip(new ClipContext(position(), position().subtract(0, CLEARANCE, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, this)).getType() != HitResult.Type.MISS) {
            want = new Vec3(want.x, Math.max(want.y, speed * 0.5), want.z);
        }
        setDeltaMovement(getDeltaMovement().lerp(want, 0.25));
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** One bullet at its target when off cooldown and in sight: hitscan, valid targets only, flyers too. */
    private void shoot(LivingEntity target) {
        if (shotCooldown > 0 || ammo <= 0 || !hasLineOfSight(target)) return;
        Vec3 from = position().add(0, getBbHeight() * 0.3, 0), aim = target.getBoundingBox().getCenter();
        if (from.distanceTo(aim) > stats.shotRange()) return;
        shotCooldown = stats.shotInterval();
        ammo--;
        HitResult hit = Hitscan.trace(level(), from, aim.subtract(from).normalize(), stats.shotRange(), e -> Hitscan.canHit(e) && affects(e));
        boolean struck = hit instanceof EntityHitResult entityHit && Hitscan.damage(entityHit.getEntity(), BastionDamageTypes.turretShot(level()), stats.bullet());
        Vec3 normal = hit instanceof BlockHitResult block && hit.getType() != HitResult.Type.MISS ? Vec3.atLowerCornerOf(block.getDirection().getNormal()) : Vec3.ZERO;
        BastionNetwork.sendNear((ServerLevel) level(), blockPosition(), new TurretImpactEvent(hit.getLocation(), TurretImpactEvent.DRONE_SHOT, struck ? 1 : 0, normal, getId()));
    }

    private void burst(ServerLevel level) {
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BastionExplosion.TURRET_BLAST), this);
        Vec3 at = getBoundingBox().getCenter();
        for (LivingEntity hit : Splash.around(level, at, stats.kamikazeRadius(), this::affects, this)) Hitscan.damage(hit, source, stats.kamikazeDamage());
        BastionNetwork.sendNear(level, blockPosition(), new TurretImpactEvent(at, TurretImpactEvent.DRONE_KAMIKAZE, stats.kamikazeRadius(), Vec3.ZERO, getId()));
        discard();
    }

    /** Home in its bay: gone from the world, counted as docked by its carrier (HiveCarrierWeapon). */
    private void dock() {
        HiveCarrierWeapon.dock((ServerLevel) level(), home);
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) TurretUnits.forget(this);
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
     * Shot down: on its first death tick it picks the nearest valid target it sees within shot range and dives into it as
     * KAMIKAZE (bursting on a hit, on running into something, or, the target gone or DYING_DIVE_TICKS up, where it is);
     * with none it drops (gravity back on) and is gone after a second, no vanilla poof and no blast. Here, not in die(),
     * as the Brood spider's dying leap.
     */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level().isClientSide || isRemoved()) return;
        if (deathTime == 1) dyingDive = nearestPrey();
        if (dyingDive != null) {
            Vec3 at = dyingDive.getBoundingBox().getCenter();
            setState(State.KAMIKAZE);
            steer(at, State.KAMIKAZE);
            if (!dyingDive.isAlive() || deathTime > DYING_DIVE_TICKS || distanceToSqr(at) < DIVE_HIT * DIVE_HIT
                    || deathTime > 2 && (horizontalCollision || verticalCollision)) {
                burst((ServerLevel) level());
            }
            return;
        }
        setNoGravity(false);
        if (deathTime >= 20) remove(RemovalReason.KILLED);
    }

    @Nullable
    private LivingEntity nearestPrey() {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(stats.shotRange()), e -> canShoot(e) && hasLineOfSight(e)
                        && e.distanceTo(this) <= stats.shotRange()).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(this))).orElse(null);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
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
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STATE, (byte) State.LAUNCH.ordinal());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "hover", 0, state -> state.setAndContinue(HOVER)));
        // The client plays it on each DRONE_SHOT (HiveEffects): the gun kicks back.
        controllers.add(new AnimationController<>(this, "shot", 0, state -> PlayState.STOP)
                .triggerableAnim("fire", RawAnimation.begin().thenPlay("animation.hive_drone.fire")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
