package dev.bastion.projectile;

import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.damage.BastionExplosion;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.TurretImpactEvent;
import dev.bastion.registry.BastionEntities;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretHitboxEntity;
import dev.bastion.weapon.LeadSolver;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Rocket from a Rocket Launcher Turret, or homing missile from a Missile Launcher Turret (same class, two entity types).
 * A rocket flies straight; a missile launches slanting up, then steers toward its target with a limited turn rate,
 * picking a new valid target if its own dies. Both explode on the first block or valid target they meet, or in the
 * air when the fuel runs out. Valid = what the firing turret's filter would shoot; they fly past everything else, and
 * the blast spares it too. Short-lived, so never saved.
 */
public class TurretRocketEntity extends Projectile implements GeoEntity {
    /** How far a missile looks for a new target when its own dies. */
    private static final double RETARGET_RANGE = 10;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private BlockPos turret;
    private float damage, splashDamage, splashRadius, knockback;
    private int fuel = 100;
    // Homing (missiles only): turn rate in radians per tick, 0 = dumb-fire rocket.
    private float turnRate, cruiseSpeed, acceleration;
    private int homingDelay, age;
    @Nullable
    private LivingEntity homingTarget;
    private int blastKind = TurretImpactEvent.ROCKET_BLAST;

    public TurretRocketEntity(EntityType<? extends TurretRocketEntity> type, Level level) {
        super(type, level);
    }

    public TurretRocketEntity(Level level, BlockPos turret, float damage, float splashDamage, float splashRadius, float knockback, int fuel) {
        this(BastionEntities.TURRET_ROCKET.get(), level, turret, damage, splashDamage, splashRadius, knockback, fuel);
    }

    public TurretRocketEntity(EntityType<? extends TurretRocketEntity> type, Level level, BlockPos turret, float damage, float splashDamage,
                              float splashRadius, float knockback, int fuel) {
        this(type, level);
        this.turret = turret.immutable();
        this.damage = damage;
        this.splashDamage = splashDamage;
        this.splashRadius = splashRadius;
        this.knockback = knockback;
        this.fuel = fuel;
    }

    /**
     * Turns this into a homing missile: after {@code delay} ticks of straight flight it steers toward {@code target}
     * at up to {@code turnDegrees} per tick, speeding up by {@code acceleration} per tick to {@code cruiseSpeed}.
     */
    public TurretRocketEntity homing(@Nullable LivingEntity target, float turnDegrees, float cruiseSpeed, float acceleration, int delay) {
        this.homingTarget = target;
        this.turnRate = turnDegrees * Mth.DEG_TO_RAD;
        this.cruiseSpeed = cruiseSpeed;
        this.acceleration = acceleration;
        this.homingDelay = delay;
        this.blastKind = TurretImpactEvent.MISSILE_BLAST;
        return this;
    }

    public void launch(Vec3 velocity) {
        setDeltaMovement(velocity);
        double horizontal = velocity.horizontalDistance();
        setYRot((float) (Math.atan2(velocity.x, velocity.z) * 180 / Math.PI));
        setXRot((float) (Math.atan2(velocity.y, horizontal) * 180 / Math.PI));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && turnRate > 0) steer();
        Vec3 motion = getDeltaMovement();
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 normal = hit instanceof BlockHitResult block ? Vec3.atLowerCornerOf(block.getDirection().getNormal()) : Vec3.ZERO;
                if (hit instanceof EntityHitResult entityHit) {
                    explode(impactPoint(entityHit.getEntity(), motion), entityHit.getEntity(), normal);
                } else {
                    explode(hit.getLocation(), null, normal);
                }
                return;
            }
            if (--fuel <= 0) {
                explode(position(), null, Vec3.ZERO);
                return;
            }
        }
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
    }

    /**
     * Where this tick's flight meets {@code target}: an EntityHitResult only carries the target's feet, which made the
     * blast (and the smoke trail running into it) dip to the ground. Falls back to the point of the flight path
     * nearest the target's middle when the rocket only grazed the box.
     */
    private Vec3 impactPoint(Entity target, Vec3 motion) {
        Vec3 from = position(), to = from.add(motion);
        return target.getBoundingBox().inflate(0.3).clip(from, to).orElseGet(() -> {
            double length = motion.length();
            if (length < 1e-6) return from;
            Vec3 dir = motion.scale(1 / length);
            double t = Mth.clamp(target.getBoundingBox().getCenter().subtract(from).dot(dir), 0, length);
            return from.add(dir.scale(t));
        });
    }

    /** Proportional homing: lead the target, turn the velocity toward it by at most turnRate, accelerate to cruise. */
    private void steer() {
        age++;
        Vec3 velocity = getDeltaMovement();
        double speed = Math.min(cruiseSpeed, velocity.length() + acceleration);
        Vec3 dir = velocity.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : velocity.normalize();
        if (age > homingDelay) {
            if (homingTarget == null || !homingTarget.isAlive()) homingTarget = retarget();
            if (homingTarget != null) {
                Vec3 aim = homingTarget.getBoundingBox().getCenter();
                double flight = aim.distanceTo(position()) / Math.max(speed, 0.1);
                aim = aim.add(LeadSolver.velocity(homingTarget).scale(flight));
                dir = turnToward(dir, aim.subtract(position()).normalize(), turnRate);
            }
        }
        setDeltaMovement(dir.scale(speed));
        hasImpulse = true; // velocity to clients every tick, so the model banks along the curve
    }

    /** The nearest other valid target close to the missile, once its own is gone. */
    @Nullable
    private LivingEntity retarget() {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(RETARGET_RANGE), this::affects)) {
            double distance = entity.distanceToSqr(this);
            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Rotates unit vector {@code from} toward unit vector {@code to} by at most {@code maxAngle} radians (slerp). */
    static Vec3 turnToward(Vec3 from, Vec3 to, double maxAngle) {
        double angle = Math.acos(Mth.clamp(from.dot(to), -1, 1));
        if (angle <= maxAngle) return to;
        double sin = Math.sin(angle);
        if (sin < 1e-4) return from; // exactly behind: keep flying, the turn starts next tick
        double t = maxAngle / angle;
        return from.scale(Math.sin((1 - t) * angle) / sin).add(to.scale(Math.sin(t * angle) / sin)).normalize();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && affects(entity);
    }

    /** What the firing turret would shoot; with the turret gone, plain hostiles. Never turrets or their parts. */
    private boolean affects(Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity instanceof TurretHitboxEntity || !living.isAlive()) return false;
        TurretBaseBlockEntity base = turret != null && level().getBlockEntity(turret) instanceof TurretBaseBlockEntity b ? b : null;
        return base == null ? entity instanceof Enemy : base.filter().test(living, base.owner());
    }

    private void explode(Vec3 at, @Nullable Entity direct, Vec3 normal) {
        if (!(level() instanceof ServerLevel server)) return;
        if (direct != null) {
            direct.invulnerableTime = 0;
            direct.hurt(BastionDamageTypes.turretShot(server), damage);
        }
        BastionExplosion.detonate(server, at, splashRadius, splashDamage, knockback, this::affects, this);
        BastionNetwork.sendNear(server, BlockPos.containing(at), new TurretImpactEvent(at, blastKind, splashRadius / 3.5f, normal));
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
