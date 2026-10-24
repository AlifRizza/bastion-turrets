package dev.bastion.projectile;

import dev.bastion.damage.BastionExplosion;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.TurretImpactEvent;
import dev.bastion.registry.BastionEntities;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretHitboxEntity;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.Splash;
import dev.bastion.weapon.types.JavelinWeapon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * The Javelin Missile (PLAN "Turret 3x3"): COLD, the gas pushes it half out of the silo and it holds there for hold_ticks;
 * CLIMB, the engine lights and it climbs straight up for climb_ticks, creeping off the silo, then faster and faster;
 * HIDDEN, out of sight at the top (not drawn) for hang_ticks, over its drop point; DIVE, straight down for dive_ticks. Its
 * x/z follows the target while that stands under open sky, else it keeps the target's last open-sky spot. It bursts on the first valid target or
 * block it meets, or at the drop point: damage at the centre falling linearly to edgeDamage at the blast radius, only
 * on what the firing turret would shoot (plain hostiles once the turret is gone), no block damage. Target, drop point,
 * phase and ticks to impact are synced for the client's mark. Short-lived, so never saved.
 */
public class JavelinMissileEntity extends Projectile implements GeoEntity {
    public static final int COLD = 0, CLIMB = 1, HIDDEN = 2, DIVE = 3;
    /**
     * Ticks the gas takes to push it out of the silo, and how far: half out, its tail HELD_DEPTH under the silo mouth and
     * its 1.5-block body's nose as far over it (user 2026-10-24: all the way out it seemed to float).
     */
    private static final int POP_TICKS = 6;
    public static final double HELD_DEPTH = 0.75;
    private static final double POP_HEIGHT = JavelinWeapon.MOUTH - JavelinWeapon.SEAT - HELD_DEPTH;
    /** How sharply the climb speeds up: height grows as e^(CLIMB_CURVE t) - 1, ~1/50 of the top speed when it lights. */
    private static final double CLIMB_CURVE = 4;
    /** Most a dive moves sideways per tick to follow its target (a walking mob makes ~0.2). */
    private static final double MAX_SIDESTEP = 1.0;
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(JavelinMissileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DROP = SynchedEntityData.defineId(JavelinMissileEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(JavelinMissileEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TICKS_LEFT = SynchedEntityData.defineId(JavelinMissileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(JavelinMissileEntity.class, EntityDataSerializers.FLOAT);
    /** Drawn from this far (blocks): it climbs 60 blocks over a turret the player may watch from across the base. */
    private static final double RENDER_DISTANCE = 160;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private BlockPos turret;
    /** The Overwatch controller this was fired under (null: the turret's filter decides). */
    @Nullable
    private UUID controller;
    private float damage, edgeDamage, radius;
    @Nullable
    private LivingEntity target;
    private double launchY, apexY;
    private int holdTicks, climbTicks, hangTicks, diveTicks, age;
    /** Game time of the launch: a missile whose chunk was unloaded past its flight time goes without a blast. */
    private long launched;

    public JavelinMissileEntity(EntityType<? extends JavelinMissileEntity> type, Level level) {
        super(type, level);
    }

    public JavelinMissileEntity(Level level, BlockPos turret, float damage, float edgeDamage, float radius) {
        this(BastionEntities.TURRET_JAVELIN.get(), level);
        this.turret = turret.immutable();
        if (level.getBlockEntity(turret) instanceof TurretBaseBlockEntity base) this.controller = base.shooter();
        this.damage = damage;
        this.edgeDamage = edgeDamage;
        this.radius = radius;
        entityData.set(RADIUS, radius);
    }

    /**
     * Starts the flight at {@code from}: out of the silo and still for {@code holdTicks}, up {@code apex} blocks (less near
     * the build limit) in {@code climbTicks}, out of sight for {@code hangTicks}, then down onto {@code target} (or
     * {@code drop} once it is gone or hidden) in {@code diveTicks}.
     */
    public JavelinMissileEntity fly(Vec3 from, @Nullable LivingEntity target, Vec3 drop, float apex, int holdTicks, int climbTicks, int hangTicks, int diveTicks) {
        setPos(from);
        this.target = target;
        this.launchY = from.y;
        this.apexY = from.y + Math.max(0, Math.min(apex, level().getMaxBuildHeight() - 4 - from.y));
        this.holdTicks = Math.max(0, holdTicks);
        this.climbTicks = Math.max(1, climbTicks);
        this.hangTicks = hangTicks == 1 ? 2 : Math.max(0, hangTicks); // it moves over the drop point on its 2nd tick out of sight
        this.diveTicks = Math.max(1, diveTicks);
        this.launched = level().getGameTime();
        entityData.set(TARGET, target == null ? -1 : target.getId());
        entityData.set(DROP, drop.toVector3f());
        entityData.set(TICKS_LEFT, flightTicks());
        setDeltaMovement(0, 0.01, 0); // the spawn packet carries it: clients draw it nose up from the first frame
        setXRot(-90);
        xRotO = getXRot();
        return this;
    }

    /**
     * Clients keep the pitch the server gives (nose up from the spawn packet, down for the dive): vanilla turns a fresh
     * projectile onto its first velocity update, and from (0, 0.01, 0) that read as nose down, so a missile held still
     * over its silo was drawn upside down (RocketRenderer points a still one by its pitch).
     */
    @Override
    public void lerpMotion(double x, double y, double z) {
        setDeltaMovement(x, y, z);
    }

    /** COLD, CLIMB, HIDDEN or DIVE. */
    public int phase() {
        return entityData.get(PHASE);
    }

    /** Where it will come down (the target's feet while it stands under open sky). */
    public Vec3 dropPoint() {
        return new Vec3(entityData.get(DROP));
    }

    /** The entity id of the marked target, -1 for none (an Overwatch drop point). */
    public int targetId() {
        return entityData.get(TARGET);
    }

    public int ticksToImpact() {
        return entityData.get(TICKS_LEFT);
    }

    /** The blast radius (the client's mark ring starts this wide). */
    public float blastRadius() {
        return entityData.get(RADIUS);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double far = RENDER_DISTANCE * getViewScale();
        return distance < far * far;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Vec3 motion = getDeltaMovement(); // the server sends the velocity every tick; move along it in between
            setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
            return;
        }
        if (level().getGameTime() - launched > flightTicks() + 5) { // missed its flight (chunk unloaded and back): never an orphan
            discard();
            return;
        }
        age++;
        Vec3 drop = followTarget(); // in the climb too: the last open-sky spot is wherever the target last stood in the open
        int lights = POP_TICKS + holdTicks, top = lights + climbTicks, dives = top + hangTicks;
        double holdY = launchY + POP_HEIGHT;
        Vec3 next;
        if (age <= POP_TICKS) { // pushed out by the gas, slowing as it clears the mouth
            double t = (double) age / POP_TICKS;
            next = new Vec3(getX(), launchY + POP_HEIGHT * t * (2 - t), getZ());
        } else if (age <= lights) { // still over the silo
            next = new Vec3(getX(), holdY, getZ());
        } else if (age <= top) { // the engine: it creeps off the silo, then climbs faster and faster
            entityData.set(PHASE, (byte) CLIMB);
            double t = (double) (age - lights) / climbTicks;
            next = new Vec3(getX(), holdY + Math.max(0, apexY - holdY) * Math.expm1(CLIMB_CURVE * t) / Math.expm1(CLIMB_CURVE), getZ());
        } else if (age <= dives) { // out of sight at the top; from its second tick over the drop point, ready to come straight down
            if (phase() != HIDDEN) {
                entityData.set(PHASE, (byte) HIDDEN);
                setInvisible(true);
                setXRot(90);
                xRotO = getXRot();
            } else {
                setPos(drop.x, getY(), drop.z); // a tick after it is hidden: clients never see it jump
            }
            next = position();
        } else {
            if (phase() != DIVE) {
                entityData.set(PHASE, (byte) DIVE);
                setInvisible(false);
                setXRot(90);
                xRotO = getXRot();
                setPos(drop.x, getY(), drop.z); // hang_ticks 0: straight from the climb
            }
            int left = flightTicks() - age + 1;
            Vec3 side = new Vec3(drop.x - getX(), 0, drop.z - getZ());
            if (side.length() > MAX_SIDESTEP) side = side.normalize().scale(MAX_SIDESTEP);
            next = new Vec3(getX() + side.x, getY() - (getY() - drop.y) / left, getZ() + side.z);
        }
        setDeltaMovement(next.subtract(position()));
        hasImpulse = true; // velocity to clients every tick
        entityData.set(TICKS_LEFT, Math.max(0, flightTicks() - age));
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            explode(hit.getLocation());
            return;
        }
        setPos(next);
        if (age >= flightTicks()) explode(next);
    }

    private int flightTicks() {
        return POP_TICKS + holdTicks + climbTicks + hangTicks + diveTicks;
    }

    /** The target's feet while it is alive and under open sky, which become the drop point; else the last drop point. */
    private Vec3 followTarget() {
        if (target != null && target.isAlive() && JavelinWeapon.openSky(target)) entityData.set(DROP, target.position().toVector3f());
        return dropPoint();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && affects(entity);
    }

    /** What the firing turret would shoot; with the turret gone, plain hostiles. Never turrets or their parts. */
    private boolean affects(Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity instanceof TurretHitboxEntity || !living.isAlive()) return false;
        TurretBaseBlockEntity base = turret != null && level().getBlockEntity(turret) instanceof TurretBaseBlockEntity b ? b : null;
        return base == null ? entity instanceof Enemy : base.hits(living, controller);
    }

    private void explode(Vec3 at) {
        if (!(level() instanceof ServerLevel server)) return;
        DamageSource source = new DamageSource(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BastionExplosion.TURRET_BLAST), this);
        for (LivingEntity hit : Splash.around(server, at, radius, this::affects, null)) {
            float d = (float) (Splash.distance(at, hit.getBoundingBox()) / radius);
            Hitscan.damage(hit, source, Mth.lerp(d, damage, edgeDamage));
        }
        BastionNetwork.sendNear(server, BlockPos.containing(at), new TurretImpactEvent(at, TurretImpactEvent.JAVELIN_BLAST, radius / 3.5f, Vec3.ZERO, getId()));
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TARGET, -1);
        entityData.define(DROP, new Vector3f());
        entityData.define(PHASE, (byte) COLD);
        entityData.define(TICKS_LEFT, 0);
        entityData.define(RADIUS, 0f);
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
