package dev.bastion.projectile;

import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.TurretImpactEvent;
import dev.bastion.registry.BastionEntities;
import dev.bastion.turret.TurretHitboxEntity;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.unit.BroodSpiderEntity;
import dev.bastion.unit.SpiderStats;
import dev.bastion.unit.TurretUnit;
import dev.bastion.unit.TurretUnits;
import dev.bastion.weapon.Hitscan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
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

import java.util.UUID;

/**
 * A Brood Cannon capsule (PLAN "Turret 3x3"): flies the Mortar's arc (half the gravity before the move and half after,
 * the parabola the cannon aimed with), passes through creatures (the first valid target it comes down on takes its
 * hitDamage, that one alone: no area; user 2026-10-24), and comes to rest on the first floor or water surface it meets
 * (off a wall it drops straight down). openTicks later it opens and releases its spiders, as many as its turret's
 * cap and the dimension's total still allow, carrying the filter and numbers it left with; the empty pod goes LINGER
 * ticks after. Until it opens it holds its spiders' places in the cap. Never saved (TurretUnits: gone with its chunk).
 */
public class BroodCapsuleEntity extends Projectile implements TurretUnit, GeoEntity {
    private static final RawAnimation OPENING = RawAnimation.begin().thenPlayAndHold("animation.turret_brood_capsule.open");
    /** Ticks the empty pod stays after opening. */
    private static final int LINGER = 40;
    private static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(BroodCapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    private BlockPos home = BlockPos.ZERO;
    private TargetFilter filter = new TargetFilter();
    @Nullable
    private UUID owner;
    private SpiderStats stats = new SpiderStats(16, 0.27f, 8, 20, 16, 15, 4, 200, 5, 8, 60);
    private int units, cap, openTicks;
    private float gravity;
    /** Ticks since it came to rest; -1 while it flies. */
    private int landedFor = -1;
    private boolean released;
    /** The creature it was fired at (null: an Overwatch point), and what it deals to one it comes down on. */
    @Nullable
    private UUID aimedAt;
    private float hitDamage;
    /** It has hit a creature on the way down (once only). */
    private boolean struck;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BroodCapsuleEntity(EntityType<? extends BroodCapsuleEntity> type, Level level) {
        super(type, level);
    }

    public BroodCapsuleEntity(Level level, BlockPos home, TargetFilter filter, @Nullable UUID owner, SpiderStats stats, int units, int cap, int openTicks,
                              float gravity) {
        this(BastionEntities.TURRET_BROOD_CAPSULE.get(), level);
        this.home = home.immutable();
        this.filter = filter.copy();
        this.owner = owner;
        this.stats = stats;
        this.units = units;
        this.cap = cap;
        this.openTicks = openTicks;
        this.gravity = gravity;
    }

    /** Fired at {@code target} (the cannon spreads its next shots over others); {@code damage} to a creature it lands on. */
    public BroodCapsuleEntity aim(@Nullable LivingEntity target, float damage) {
        this.aimedAt = target == null ? null : target.getUUID();
        this.hitDamage = damage;
        return this;
    }

    /** The creature it was fired at, until it opens. */
    @Nullable
    public UUID aimedAt() {
        return released ? null : aimedAt;
    }

    public void launch(Vec3 velocity) {
        setDeltaMovement(velocity);
        hasImpulse = true;
    }

    /** Opened (the client opens the petals). */
    public boolean isOpen() {
        return entityData.get(OPEN);
    }

    @Override
    public BlockPos home() {
        return home;
    }

    @Override
    public int unitWeight() {
        return released ? 0 : units;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Vec3 motion = getDeltaMovement(); // the server sends the velocity every tick; move along it in between
            setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
            return;
        }
        if (!TurretUnits.ticked(this)) {
            discard(); // back from an unloaded chunk: gone
            return;
        }
        if (landedFor >= 0) {
            landedFor++;
            if (!released && landedFor >= openTicks) open((ServerLevel) level()); // >=: open_ticks 0 opens at once
            if (landedFor >= openTicks + LINGER) discard();
            return;
        }
        setDeltaMovement(getDeltaMovement().add(0, -gravity / 2, 0));
        Vec3 motion = getDeltaMovement(), to = position().add(motion);
        BlockHitResult hit = level().clip(new ClipContext(position(), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (hitDamage > 0 && !struck && motion.y < 0) { // on a head on the way down, above what it stops on: that creature alone
            Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
            EntityHitResult head = ProjectileUtil.getEntityHitResult(level(), this, position(), end, getBoundingBox().expandTowards(end.subtract(position())).inflate(0.3), this::hits);
            if (head != null) {
                struck = true;
                Hitscan.damage(head.getEntity(), BastionDamageTypes.turretShot(level()), hitDamage,
                        TurretRocketEntity.impactPoint(head.getEntity(), position(), end.subtract(position())), motion, EntityType.getKey(getType()));
            }
        }
        hasImpulse = true; // velocity to clients every tick, so they follow the arc
        if (hit.getType() != HitResult.Type.MISS) {
            if (hit.getDirection() == Direction.UP) { // a floor or a water surface: it rests there
                setPos(hit.getLocation());
                setDeltaMovement(Vec3.ZERO);
                landedFor = 0;
                BastionNetwork.sendNear((ServerLevel) level(), blockPosition(), new TurretImpactEvent(position(), TurretImpactEvent.BROOD_LAND, 1, Vec3.ZERO, getId()));
            } else { // a wall or a ceiling: off it, straight down
                setPos(hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.05)));
                setDeltaMovement(0, Math.min(motion.y, 0), 0);
            }
            return;
        }
        setPos(to);
        setDeltaMovement(motion.add(0, -gravity / 2, 0));
        if (getY() < level().getMinBuildHeight() - 16) discard();
    }

    /** What it may hurt on the way down: its turret's valid targets, never a unit or a turret. */
    private boolean hits(Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() && !(entity instanceof TurretUnit) && !(entity instanceof TurretHitboxEntity)
                && filter.test(living, owner);
    }

    /** The petals open and the spiders climb out round it (on the pod itself where a wall is in the way). */
    private void open(ServerLevel level) {
        entityData.set(OPEN, true);
        BastionNetwork.sendNear(level, blockPosition(), new TurretImpactEvent(position(), TurretImpactEvent.BROOD_OPEN, 1, Vec3.ZERO, getId()));
        released = true; // its places pass to the spiders: the room below no longer counts them twice
        int n = Math.min(units, TurretUnits.room(level, home, cap));
        double start = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double angle = start + i * Math.PI * 2 / n;
            Vec3 at = position().add(Math.cos(angle) * 0.9, 0, Math.sin(angle) * 0.9);
            if (!level.noCollision(BastionEntities.BROOD_SPIDER.get().getAABB(at.x, at.y, at.z))) at = position();
            BroodSpiderEntity.spawn(level, at, home, filter, owner, stats);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        TurretUnits.forget(this);
        super.remove(reason);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** A portal would recreate it without its home, filter, numbers and gravity (never saved, never cloned). */
    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OPEN, false);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "open", 0, state -> isOpen() ? state.setAndContinue(OPENING) : PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
