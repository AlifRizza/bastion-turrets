package dev.bastion.projectile;

import dev.bastion.registry.BastionEntities;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretHitboxEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * An Arrow Launcher arrow (PLAN "Turret early game"): a vanilla arrow drawn by the vanilla arrow renderer, the line's one
 * exception to PLAN 1. It hits only what the firing turret may shoot (its filter, or the controller it was fired under, and
 * the weapon's own rule: on the ground, past min_range) and flies through everything else, deals the weapon's damage
 * whatever its speed, carries a tipped arrow's effects (a spectral one makes the target glow), and is never picked up or
 * saved. Stuck in a block it goes after the weapon's stuck_ticks (vanilla keeps an arrow 60 s). It thunks and splashes as
 * vanilla's arrow does (user 10-27, a second exception; it was silent). No vanilla particles: the client mixins ArrowMixin
 * and AbstractArrowMixin stop them, and EarlyEffects draws our own (a wind streak, a tipped arrow's mist, dust where it
 * sticks).
 */
public class TurretArrowEntity extends Arrow {
    /** What vanilla's AbstractArrow.tick applies every tick in the air (as floats, as it does). */
    private static final float VANILLA_DRAG = 0.99f, VANILLA_GRAVITY = 0.05f;
    /** Made from a spectral arrow: drawn with the spectral texture, so the client needs it too. */
    private static final EntityDataAccessor<Boolean> SPECTRAL = SynchedEntityData.defineId(TurretArrowEntity.class, EntityDataSerializers.BOOLEAN);
    @Nullable
    private BlockPos turret;
    @Nullable
    private UUID controller;
    private float damage, gravity = VANILLA_GRAVITY, drag = VANILLA_DRAG;
    /** Ticks it may stay stuck in a block; vanilla's own 1200-tick despawn still applies past that. */
    private int stuckTicks = 1200;
    /** Ticks of Glowing a spectral arrow gives (the weapon's glow_ticks). */
    private int glowTicks = 200;

    public TurretArrowEntity(EntityType<? extends TurretArrowEntity> type, Level level) {
        super(type, level);
    }

    /** An arrow of the turret at {@code turret}, made from one {@code ammo} item (Creative Ammo or any other: plain). */
    public TurretArrowEntity(Level level, BlockPos turret, ItemStack ammo, float damage) {
        this(BastionEntities.TURRET_ARROW.get(), level);
        this.turret = turret.immutable();
        if (level.getBlockEntity(turret) instanceof TurretBaseBlockEntity base) this.controller = base.shooter();
        this.damage = damage;
        this.pickup = Pickup.DISALLOWED;
        setEffectsFromItem(ammo);
        entityData.set(SPECTRAL, ammo.is(Items.SPECTRAL_ARROW));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SPECTRAL, false);
    }

    /**
     * Flies off at {@code velocity} on the arc the turret aimed with: slowed by {@code drag} and pulled down by
     * {@code gravity} each tick (the weapon's numbers; vanilla's own are 0.99 and 0.05); stuck in a block, it goes after
     * {@code stuckTicks}; a spectral one gives {@code glowTicks} of Glowing.
     */
    public TurretArrowEntity launch(Vec3 velocity, float gravity, float drag, int stuckTicks, int glowTicks) {
        setDeltaMovement(velocity);
        this.gravity = gravity;
        this.drag = drag;
        this.stuckTicks = stuckTicks;
        this.glowTicks = glowTicks;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && inGround && inGroundTime >= stuckTicks) {
            discard();
            return;
        }
        // Vanilla just applied v * 0.99 - 0.05; swap that for the weapon's own numbers (a config may change them).
        // ponytail: clients keep predicting with vanilla's numbers between position syncs; they drift only when the config differs.
        if (!level().isClientSide && !inGround && !isRemoved() && (gravity != VANILLA_GRAVITY || drag != VANILLA_DRAG)) {
            setDeltaMovement(getDeltaMovement().add(0, VANILLA_GRAVITY, 0).scale(drag / VANILLA_DRAG).subtract(0, gravity, 0));
        }
    }

    /** The turret's valid targets only (Bullets.filtered's rule); a client copy, which has no turret, hits nothing. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity instanceof LivingEntity living && !(entity instanceof TurretHitboxEntity)
                && !living.isInvulnerable() && turret != null && level().getBlockEntity(turret) instanceof TurretBaseBlockEntity base
                && base.hits(living, controller) && BastionWeaponTypes.ARROW_LAUNCHER.get().canTarget(living, base);
    }

    /**
     * The weapon's damage at any speed: vanilla deals ceil(speed * base damage), so the base is damage / speed (a hair under,
     * so the float speed cannot round 3 up to 4). The target's hurt cooldown is cleared, else the five arrows after the first
     * would bounce off.
     */
    @Override
    protected void onHitEntity(EntityHitResult hit) {
        hit.getEntity().invulnerableTime = 0;
        setCritArrow(false);
        setBaseDamage((damage - 0.01) / Math.max(getDeltaMovement().length(), 1e-3));
        super.onHitEntity(hit);
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        if (spectral()) target.addEffect(new MobEffectInstance(MobEffects.GLOWING, glowTicks, 0), getEffectSource());
    }

    public boolean spectral() {
        return entityData.get(SPECTRAL);
    }

    /** Stuck in a block (the client's own copy knows too: it runs the same collision). */
    public boolean stuck() {
        return inGround;
    }

    /**
     * Entering water: no vanilla bubbles and splash on the client (PLAN 1, user rule; AbstractArrowMixin cannot reach this
     * one, it is Entity's). The server still runs it: the SPLASH game event and vanilla's splash sound.
     */
    @Override
    protected void doWaterSplashEffect() {
        if (!level().isClientSide) super.doWaterSplashEffect();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
