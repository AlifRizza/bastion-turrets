package dev.bastion.turret;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Invisible stand-in that takes hits for a turret base (PLAN 4.1): blocks have no HP, so mobs, projectiles and
 * explosions strike this entity, which forwards the damage to the base's block entity. It is a LivingEntity so
 * hostile mobs can target it (see TurretAggro), and it mirrors the turret's HP so health displays show it.
 * Never saved: the base spawns it on load and it removes itself as soon as its base is gone.
 * The crosshair passes through it, so players click and mine the block itself.
 */
public class TurretHitboxEntity extends LivingEntity implements IEntityAdditionalSpawnData {
    /** How far out from the attached face the target point sits: just past the armed shape (1.5 blocks). */
    private static final double REACH = 1.6;
    /** A large base: 2x2 across and its launcher stands higher. */
    private static final float LARGE_WIDTH = 1.95f, LARGE_REACH = 2.3f;

    @Nullable
    private BlockPos base;
    private Direction facing = Direction.UP;
    private boolean large;

    public TurretHitboxEntity(EntityType<? extends TurretHitboxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        // Invisible, so it must never refuse a block: funnels and the like reach into the base's space.
        this.blocksBuilding = false;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    /**
     * Covers the turret along its mount axis. Its eye (what mobs aim at and need line of sight to) sits just past
     * the armed shape, so the turret's own collision never hides it: on top for a floor turret, at the bottom for
     * a ceiling one, in front of the gun for a wall one.
     */
    public void bind(BlockPos base, Vec3 center, Direction facing, boolean large) {
        this.base = base.immutable();
        this.facing = facing;
        this.large = large;
        refreshDimensions();
        Vec3 eye = center.add(Vec3.atLowerCornerOf(facing.getNormal()).scale((large ? LARGE_REACH : REACH) - 0.5));
        setPos(eye.x, eye.y - getEyeHeight(), eye.z);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (large) return EntityDimensions.fixed(LARGE_WIDTH, LARGE_REACH); // floor only
        return facing == null || facing.getAxis().isVertical() ? EntityDimensions.fixed(0.9f, (float) REACH) : EntityDimensions.fixed(1.2f, 1.0f);
    }

    @Nullable
    public BlockPos base() {
        return base;
    }

    @Nullable
    private TurretBaseBlockEntity turret() {
        return base != null && level().getBlockEntity(base) instanceof TurretBaseBlockEntity turret ? turret : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        TurretBaseBlockEntity turret = turret();
        if (turret == null) {
            discard();
            return;
        }
        // Mirror the turret's HP (synced to clients like any mob's) so damage numbers and health bars show it.
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(turret.maxHealth());
        setHealth(Math.max(0.01f, turret.health()));
    }

    /**
     * Only hits from something forward to the turret: mobs, players, projectiles and explosions. Environmental
     * damage (being inside the base block, falling, drowning, fire) is the hitbox's own business and ignored.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isInvulnerableTo(source)) return false;
        boolean fromSomething = source.getEntity() != null || source.getDirectEntity() != null || source.is(DamageTypeTags.IS_EXPLOSION);
        TurretBaseBlockEntity turret = turret();
        return fromSomething && turret != null && turret.takeDamage(source, amount);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        // facing is still null while the super constructor sizes the entity
        if (facing == null || facing == Direction.UP) return dimensions.height;
        return facing == Direction.DOWN ? 0 : dimensions.height / 2;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return isAlive();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return NonNullList.withSize(4, ItemStack.EMPTY);
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeBoolean(base != null);
        if (base != null) buffer.writeBlockPos(base);
        buffer.writeEnum(facing);
        buffer.writeBoolean(large);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        if (buffer.readBoolean()) base = buffer.readBlockPos();
        facing = buffer.readEnum(Direction.class);
        large = buffer.readBoolean();
        refreshDimensions();
    }

    /** Entity's default name would read "entity.bastion.turret_hitbox"; health displays show the base's name. */
    @Override
    protected Component getTypeName() {
        return Component.translatable("block.bastion.turret_base");
    }
}
