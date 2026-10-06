package dev.bastion.turret;

import dev.bastion.config.BastionConfig;
import dev.bastion.damage.BastionExplosion;
import dev.bastion.menu.TurretMenu;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.TurretImpactEvent;
import dev.bastion.network.TurretStateSync;
import dev.bastion.registry.BastionBlockEntities;
import dev.bastion.registry.BastionEntities;
import dev.bastion.registry.BastionItems;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.turret.targeting.TargetSelector;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * Turret base: inventory, targeting, rotation, the fire cycle, HP and ownership (PLAN 4.1-4.7). The server
 * decides everything; clients receive state through {@link TurretStateSync} and only interpolate and animate.
 * Serves both the one-block base and the core block of a 2x2 {@link LargeTurretBaseBlock}.
 */
public class TurretBaseBlockEntity extends BlockEntity implements GeoBlockEntity, MenuProvider {
    /** Height of the base: the weapon mounts this far out from the attached face. */
    public static final double BASE_HEIGHT = 1.0;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.turret_base.idle");
    private static final int SEARCH_INTERVAL = 10;
    /** Ticks after a shot during which the burst counts as still going (Aim.sweep_tolerance applies). */
    private static final int SWEEP_GRACE = 10;
    private static final int SYNC_INTERVAL = 2;
    /** Below this fraction the base shows its damaged state (PLAN 4.1). */
    public static final float DAMAGED_FRACTION = 0.3f;
    /** Fraction of the turn speed gained or lost per tick: the servo inertia. */
    private static final float SERVO_ACCELERATION = 0.3f;

    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);
    /** Tier for the chunk-meshed base model (BakedTurretBaseModel): armour and fins are part of the mesh. */
    public static final ModelProperty<TurretTier> MODEL_TIER = new ModelProperty<>();

    private TurretTier tier = TurretTier.T1;
    private final TurretInventory inventory = new TurretInventory(() -> tier, this::large, this::onSlotChanged);
    private final AmmoOnlyItemHandler ammo = new AmmoOnlyItemHandler(inventory);
    private LazyOptional<IItemHandler> ammoCapability = LazyOptional.of(() -> ammo);
    /** Forge Energy for energy weapons (Tesla Coil); the capacitor's size comes from the weapon's energy_capacity param. */
    private int energy;
    private final IEnergyStorage energyStorage = new TurretEnergy();
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> energyStorage);

    // Server state (yaw/pitch use Minecraft conventions: yaw 0 = south, pitch > 0 = down)
    private TurretState state = TurretState.DISABLED;
    private float yaw, pitch, yawVelocity, pitchVelocity;
    private float heat, ammoCredit, fireCooldown;
    private int searchTimer, aimLockTicks, overheatTimer, chargeTimer;
    private int sinceShot = SWEEP_GRACE + 1;
    @Nullable
    private LivingEntity target;
    private WeaponState weaponState = new WeaponState();

    // Ownership and settings (PLAN 4.7)
    @Nullable
    private UUID owner;
    private TargetFilter filter = new TargetFilter();
    private boolean enabled = true, redstoneInverted;
    /** Fire mode for weapons that support it (Missile Launcher): all tubes at once instead of one by one. */
    private boolean salvo;
    // Health (PLAN 4.1); negative = full, so a fresh base needs no config lookup at construction.
    private float health = -1;
    @Nullable
    private TurretHitboxEntity hitbox;
    private int syncTimer;
    @Nullable
    private TurretStateSync lastSync;

    // Client mirror of the last TurretStateSync
    private TurretState clientState = TurretState.DISABLED;
    private float clientYaw, clientPitch, clientHeat;
    private float clientHealth = 1;
    private int clientTargetId = -1;
    /** Loaded launcher tubes as a bit mask (Missile Launcher), from RackSync and the update tag. */
    private int clientTubes;
    // Client render smoothing (PLAN 6.4): exponential follow + idle scan sweep
    private float renderYaw, renderPitch, prevRenderYaw, prevRenderPitch, scanCenterYaw;
    private int scanTicks;

    public TurretBaseBlockEntity(BlockPos pos, BlockState state) {
        super(BastionBlockEntities.TURRET_BASE.get(), pos, state);
    }

    public TurretInventory inventory() {
        return inventory;
    }

    /** A 2x2 large base (big weapon modules) rather than the standard one-block base. */
    public boolean large() {
        return getBlockState().getBlock() instanceof LargeTurretBaseBlock;
    }

    public TurretTier tier() {
        return tier;
    }

    public TurretState state() {
        return state;
    }

    @Nullable
    public LivingEntity target() {
        return target;
    }

    public WeaponState weaponState() {
        return weaponState;
    }

    /** True for an empty T1 base, the state that drops as a plain stackable item. */
    public boolean isDefault() {
        return tier == TurretTier.T1 && inventory.isEmpty();
    }

    /** Comparator output 0-15 from how full the unlocked ammo slots are (PLAN 4.2), or the capacitor for energy weapons. */
    public int comparatorLevel() {
        int capacity = energyCapacity();
        if (capacity > 0) return energy() == 0 ? 0 : 1 + 14 * energy() / capacity;
        return ItemHandlerHelper.calcRedstoneFromInventory(ammo);
    }

    // --- energy (energy weapons only) ---------------------------------------------------------

    /** FE the mounted weapon's capacitor holds; 0 for ammo weapons, so the base then accepts no energy. */
    public int energyCapacity() {
        WeaponData data = inventory.weaponData();
        return data == null ? 0 : Math.round(data.params().getOrDefault("energy_capacity", 0f));
    }

    public int energy() {
        return Math.min(energy, energyCapacity());
    }

    /** Whether one shot costing {@code amount} FE can be paid; Creative Ammo in an ammo slot always pays. */
    public boolean hasEnergy(int amount) {
        return hasCreativeAmmo() || energy() >= amount;
    }

    /** Pays for one shot from the capacitor (free with Creative Ammo); false when it holds too little. */
    public boolean useEnergy(int amount) {
        if (hasCreativeAmmo()) return true;
        if (energy() < amount) return false;
        energy = energy() - amount;
        setChanged();
        return true;
    }

    private boolean hasCreativeAmmo() {
        int slot = inventory.findAmmoSlot();
        return slot >= 0 && inventory.getStackInSlot(slot).is(BastionItems.CREATIVE_AMMO.get());
    }

    /** Receive-only FE storage, at most the weapon's max_input per call; exposed on every side (and every block of a large base). */
    private class TurretEnergy implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            WeaponData data = inventory.weaponData();
            int limit = data == null ? 0 : Math.round(data.params().getOrDefault("max_input", 0f));
            int accepted = Math.max(0, Math.min(energyCapacity() - energy(), Math.min(limit, maxReceive)));
            if (accepted > 0 && !simulate) {
                energy = energy() + accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy();
        }

        @Override
        public int getMaxEnergyStored() {
            return energyCapacity();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return energyCapacity() > 0;
        }
    }

    public float heat() {
        return heat;
    }

    // --- ownership & settings -----------------------------------------------------------------

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public TargetFilter filter() {
        return filter;
    }

    public void setFilter(TargetFilter filter) {
        this.filter = filter.copy();
        target = null;
        setChanged();
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        setChanged();
    }

    public boolean redstoneInverted() {
        return redstoneInverted;
    }

    public void setRedstoneInverted(boolean inverted) {
        this.redstoneInverted = inverted;
        setChanged();
    }

    public boolean salvo() {
        return salvo;
    }

    public void setSalvo(boolean salvo) {
        this.salvo = salvo;
        setChanged();
    }

    /** Owner, trusted players and ops may open, reconfigure and disarm the turret (PLAN 4.7; off by config). */
    public boolean canConfigure(Player player) {
        return !BastionConfig.OWNER_PROTECTION.get() || owner == null || player.hasPermissions(2) || filter.isTrusted(player, owner);
    }

    /** Owner and trusted players cannot hurt their own turret unless the config allows it. */
    public boolean canBeDamagedBy(Player player) {
        return BastionConfig.TRUSTED_CAN_DAMAGE.get() || owner == null || !filter.isTrusted(player, owner);
    }

    // --- health (PLAN 4.1) -------------------------------------------------------------------

    public float maxHealth() {
        float size = large() ? BastionConfig.LARGE_HEALTH_MULTIPLIER.get().floatValue() : 1;
        return BastionConfig.maxHealth(tier) * size * Math.max(0.1f, 1 + inventory.modifierEffect().maxHealth());
    }

    public float health() {
        return health < 0 ? maxHealth() : Math.min(health, maxHealth());
    }

    /** Forwarded by TurretHitboxEntity. Destroys the turret at 0 HP. */
    public boolean takeDamage(DamageSource source, float amount) {
        if (!(level instanceof ServerLevel server) || amount <= 0) return false;
        if (source.getEntity() instanceof Player player && !canBeDamagedBy(player)) return false;
        health = health() - amount;
        setChanged();
        if (health <= 0) destroy(server);
        return true;
    }

    public void repair(float amount) {
        health = Math.min(maxHealth(), health() + amount);
        setChanged();
    }

    /** One tier up, never down (PLAN 4.1). Full health after the upgrade. */
    public boolean upgradeTo(TurretTier next) {
        if (next.ordinal() != tier.ordinal() + 1 || !(level instanceof ServerLevel server)) return false;
        tier = next;
        health = -1;
        setChanged();
        server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        BastionNetwork.sendNear(server, worldPosition, new TurretImpactEvent(alongMount(0.6), TurretImpactEvent.TIER_UP, 1f));
        return true;
    }

    /**
     * 0 HP (PLAN 4.1): a custom blast without block damage, weapon and contents drop, the base drops one
     * tier lower (a damaged base at T1) and the block is removed (a large base takes its parts with it).
     */
    private void destroy(ServerLevel server) {
        Vec3 center = alongMount(0.6);
        BastionExplosion.detonate(server, center, 3.5f, 6f, 1.2f);
        BastionNetwork.sendNear(server, worldPosition, new TurretImpactEvent(center, TurretImpactEvent.DESTROYED, 1f));
        for (int i = 0; i < inventory.getSlots(); i++) {
            Containers.dropItemStack(server, center.x, center.y, center.z, inventory.getStackInSlot(i));
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
        ItemStack base = tier != TurretTier.T1 ? new ItemStack(getBlockState().getBlock())
                : new ItemStack(large() ? BastionItems.DAMAGED_LARGE_TURRET_BASE.get() : BastionItems.DAMAGED_TURRET_BASE.get());
        if (tier == TurretTier.T3) {
            base.getOrCreateTagElement("BlockEntityTag").putString("Tier", TurretTier.T2.name());
        }
        Containers.dropItemStack(server, center.x, center.y, center.z, base);
        server.removeBlock(worldPosition, false);
    }

    private void regenerate() {
        float regen = BastionConfig.regenPerSecond(tier) / 20f;
        if (regen > 0 && health >= 0 && health < maxHealth()) {
            health = Math.min(maxHealth(), health + regen);
            if (health >= maxHealth()) health = -1;
        }
    }

    /** Keeps exactly one hit receiver standing on the base; it is never saved, so it is re-created after loads. */
    private void maintainHitbox(ServerLevel server) {
        if (hitbox != null && hitbox.isAlive()) return;
        if (server.getGameTime() % 20 != Math.floorMod(worldPosition.asLong(), 20)) return;
        TurretHitboxEntity entity = BastionEntities.TURRET_HITBOX.get().create(server);
        if (entity == null) return;
        entity.bind(worldPosition, mountCenter(), facing(), large());
        server.addFreshEntity(entity);
        hitbox = entity;
    }

    /** Disabled by its own switch or by redstone (PLAN 4.4); inverted mode needs a signal instead. Any block of a large base counts. */
    private boolean active(ServerLevel server) {
        boolean powered = large()
                ? LargeTurretBaseBlock.footprint(LargeTurretBaseBlock.footprintMin(getBlockState(), worldPosition)).stream().anyMatch(server::hasNeighborSignal)
                : server.hasNeighborSignal(worldPosition);
        return enabled && powered == redstoneInverted;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (hitbox != null) hitbox.discard();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (hitbox != null) hitbox.discard();
    }

    /** World position of the weapon's yaw/pitch pivot. */
    public Vec3 pivot(WeaponData data) {
        return alongMount(BASE_HEIGHT + data.aim().pivotHeight());
    }

    // --- mount frame: floor, wall or ceiling --------------------------------------------------
    // Turret space has the mount axis as +Y (a floor turret's world space). Direction#getRotation turns +Y onto
    // the facing; TurretBaseRenderer applies the very same rotation, so server aim and client model agree.

    /** Where the base's top points: UP on a floor, DOWN under a ceiling, the wall's normal on a wall. */
    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(TurretBaseBlock.FACING) ? state.getValue(TurretBaseBlock.FACING) : Direction.UP;
    }

    public Vec3 toWorld(Vec3 local) {
        Vector3f v = new Vector3f((float) local.x, (float) local.y, (float) local.z);
        facing().getRotation().transform(v);
        return new Vec3(v.x, v.y, v.z);
    }

    public Vec3 toLocal(Vec3 world) {
        Vector3f v = new Vector3f((float) world.x, (float) world.y, (float) world.z);
        facing().getRotation().conjugate().transform(v);
        return new Vec3(v.x, v.y, v.z);
    }

    /** A model-space point (blocks, origin at the centre of the attached face) in world space. */
    public Vec3 modelToWorld(double x, double y, double z) {
        return mountCenter().add(toWorld(new Vec3(x, y - 0.5, z)));
    }

    /** Centre of the base the turret turns around: this block's centre, or the middle of a 2x2 large base. */
    public Vec3 mountCenter() {
        return large() ? LargeTurretBaseBlock.center(getBlockState(), worldPosition) : Vec3.atCenterOf(worldPosition);
    }

    /** The point {@code height} blocks out along the mount axis from the attached face. */
    public Vec3 alongMount(double height) {
        return modelToWorld(0, height, 0);
    }

    // --- server tick -------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, TurretBaseBlockEntity turret) {
        turret.tickServer((ServerLevel) level);
        turret.syncIfChanged((ServerLevel) level);
    }

    private void tickServer(ServerLevel level) {
        maintainHitbox(level);
        regenerate();
        WeaponData data = inventory.weaponData();
        WeaponType type = data == null ? null : BastionWeaponTypes.REGISTRY.get().getValue(data.type());
        if (data != null && data.aim().fixedPitch()) { // fixed-elevation weapons (Missile Launcher) keep their angle, even switched off
            pitch = -data.aim().minPitch();
            pitchVelocity = 0;
        }
        if (data == null || type == null || !active(level)) {
            state = TurretState.DISABLED;
            target = null;
            return;
        }
        StatSheet stats = StatSheet.of(data, tier, inventory.modifierEffect());
        heat = Math.max(0, heat - stats.heatDissipationPerTick());
        if (sinceShot <= SWEEP_GRACE) sinceShot++;
        // Carries up to one tick of remainder, so fractional intervals (Machine Gun 12/s) average out exactly.
        fireCooldown = Math.max(fireCooldown - 1, -1);

        if (state == TurretState.OVERHEAT) {
            type.tick(this, weaponState, stats, false);
            if (--overheatTimer > 0) return;
            state = TurretState.IDLE;
        }

        updateTarget(stats, type);
        boolean armed = hasAmmo(stats) && !type.outOfAmmo(this, weaponState);
        type.tick(this, weaponState, stats, target != null && armed);
        trackLock();
        if (target == null) {
            fireCooldown = Math.max(fireCooldown, 0);
            aimLockTicks = chargeTimer = 0;
            yawVelocity = pitchVelocity = 0;
            state = armed ? TurretState.IDLE : TurretState.NO_AMMO;
            return;
        }

        Vec3 aimPoint = type.aimPoint(this, target, type.sight(this, target, stats), stats);
        Vec3 toTarget = toLocal(aimPoint.subtract(pivot(data)));
        float desiredPitch = -Mth.clamp(TargetSelector.elevation(toTarget), data.aim().minPitch(), data.aim().maxPitch());
        rotateToward(TargetSelector.yaw(toTarget), desiredPitch, stats.turnSpeed());

        if (!armed) {
            state = TurretState.NO_AMMO;
            return;
        }
        float error = Math.max(Math.abs(Mth.wrapDegrees(TargetSelector.yaw(toTarget) - yaw)), Math.abs(desiredPitch - pitch));
        // Mid-burst, Flamethrower and Machine Gun keep firing as they swing onto the next target (sweep_tolerance).
        boolean aligned = error <= (sinceShot <= SWEEP_GRACE ? data.aim().sweep() : data.aim().tolerance());
        if (!aligned && chargeTimer == 0) {
            aimLockTicks = 0;
            state = TurretState.ACQUIRING;
            return;
        }
        // A charge under way (Sniper, Laser Rifle) survives the target moving: the turret keeps tracking while it
        // charges, and once full the shot waits until the aim lines up again. Only losing the target resets it.
        if (chargeTimer == 0) {
            aimLockTicks++;
            if (fireCooldown > 0) {
                state = TurretState.COOLDOWN;
                return;
            }
            if (aimLockTicks < data.aim().lockTicks() || !type.ready(stats, weaponState)) {
                fireCooldown = Math.max(fireCooldown, 0);
                state = TurretState.AIMING;
                return;
            }
        }
        int charge = type.chargeTicks(stats);
        if (charge > 0 && chargeTimer < charge) {
            chargeTimer++;
            state = TurretState.CHARGING;
            return;
        }
        if (!aligned) { // fully charged, holding for the aim
            state = TurretState.CHARGING;
            return;
        }
        chargeTimer = 0;
        fire(level, stats, type);
    }

    /** Precision Lock bookkeeping (PLAN 7.1): ticks held on the same target, reset when the target changes. */
    private void trackLock() {
        int id = target == null ? -1 : target.getId();
        if (id != weaponState.lockTarget) {
            weaponState.lockTarget = id;
            weaponState.lockTicks = 0;
            weaponState.lockSpent = false;
        } else if (id != -1) {
            weaponState.lockTicks++;
        }
    }

    private void updateTarget(StatSheet stats, WeaponType type) {
        if (target != null && (!TargetSelector.isCandidate(this, target, stats, type) || type.sight(this, target, stats) == null)) {
            target = null;
            searchTimer = 0; // lost it mid-fight: pick the next one this tick instead of waiting for the next search
        }
        if (target == null && --searchTimer <= 0) {
            searchTimer = SEARCH_INTERVAL;
            target = TargetSelector.find(this, stats, type);
        }
    }

    /**
     * Servo motion: accelerate toward the aim at a fraction of the turn speed, brake so the turret stops
     * on target instead of overshooting, snap once within one step (reads as weighty but precise).
     */
    private void rotateToward(float desiredYaw, float desiredPitch, float turnSpeed) {
        yawVelocity = servoVelocity(yawVelocity, Mth.wrapDegrees(desiredYaw - yaw), turnSpeed);
        pitchVelocity = servoVelocity(pitchVelocity, desiredPitch - pitch, turnSpeed);
        yaw = Mth.wrapDegrees(yaw + yawVelocity);
        pitch += pitchVelocity;
    }

    private static float servoVelocity(float velocity, float delta, float maxSpeed) {
        float acceleration = maxSpeed * SERVO_ACCELERATION;
        float stoppable = (float) Math.sqrt(2 * acceleration * Math.abs(delta));
        float wanted = Math.signum(delta) * Math.min(maxSpeed, stoppable);
        float next = velocity + Mth.clamp(wanted - velocity, -acceleration, acceleration);
        return Math.abs(delta) <= Math.abs(next) ? delta : next;
    }

    /**
     * Takes one ammo item for a weapon that loads ahead of firing (Missile Launcher tubes): Creative Ammo is never used
     * up and Ammo Recycler sometimes makes it free. False when there is none.
     */
    public boolean takeOneAmmo(StatSheet stats, ServerLevel level) {
        int slot = inventory.findAmmoSlot();
        if (slot < 0) return false;
        if (stats.ammoSaveChance() > 0 && level.random.nextFloat() < stats.ammoSaveChance()) return true;
        if (!inventory.getStackInSlot(slot).is(BastionItems.CREATIVE_AMMO.get())) inventory.extractItem(slot, 1, false);
        return true;
    }

    private boolean hasAmmo(StatSheet stats) {
        return ammoCredit + 1e-4f >= stats.ammoPerShot() || inventory.findAmmoSlot() >= 0;
    }

    /**
     * Ammo is paid per item but spent per shot, so 0.5 per shot means one item every two shots.
     * Ammo Recycler (PLAN 4.6) makes some shots free.
     */
    private void consumeAmmo(StatSheet stats, ServerLevel level) {
        if (stats.ammoSaveChance() > 0 && level.random.nextFloat() < stats.ammoSaveChance()) return;
        if (ammoCredit + 1e-4f < stats.ammoPerShot()) {
            int slot = inventory.findAmmoSlot();
            if (slot < 0) return;
            // Creative Ammo pays for the shot without being used up.
            if (!inventory.getStackInSlot(slot).is(BastionItems.CREATIVE_AMMO.get())) inventory.extractItem(slot, 1, false);
            ammoCredit += 1;
        }
        ammoCredit -= stats.ammoPerShot();
    }

    private void fire(ServerLevel level, StatSheet stats, WeaponType type) {
        WeaponData data = stats.data();
        boolean lock = data.params().containsKey("precision_lock_ticks") && !weaponState.lockSpent
                && weaponState.lockTicks >= data.param("precision_lock_ticks");
        if (lock) weaponState.lockSpent = true;
        consumeAmmo(stats, level);
        Vec3 direction = toWorld(Vec3.directionFromRotation(pitch, yaw));
        Vec3 muzzle = pivot(data).add(direction.scale(data.aim().muzzleLength()));
        type.onFire(new FireContext(level, this, muzzle, direction, target, stats, weaponState, level.random.nextLong(), lock));
        sinceShot = 0;
        fireCooldown += type.fireInterval(stats, weaponState);
        heat += stats.heatPerShot();
        state = TurretState.FIRING;
        if (heat >= stats.maxHeat()) {
            state = TurretState.OVERHEAT;
            overheatTimer = stats.data().heat().overheatTicks();
        }
        setChanged();
    }

    /** Server yaw and pitch of the weapon (Minecraft conventions), for weapons that place their own muzzles. */
    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    private void syncIfChanged(ServerLevel level) {
        if (--syncTimer > 0) return;
        TurretStateSync sync = new TurretStateSync(worldPosition, state, yaw, pitch, target == null ? -1 : target.getId(), heat,
                health() / maxHealth());
        if (lastSync != null && lastSync.state() == sync.state() && lastSync.targetId() == sync.targetId()
                && Math.abs(Mth.wrapDegrees(lastSync.yaw() - yaw)) < 0.25f && Math.abs(lastSync.pitch() - pitch) < 0.25f
                && Math.abs(lastSync.heat() - heat) < 1f && Math.abs(lastSync.health() - sync.health()) < 0.01f) {
            return;
        }
        BastionNetwork.sendNear(level, worldPosition, sync);
        lastSync = sync;
        syncTimer = SYNC_INTERVAL;
    }

    private void onSlotChanged(int slot) {
        setChanged();
        if (slot == TurretInventory.WEAPON && level != null && !level.isClientSide) {
            energy = energy(); // the charge is the weapon's: a smaller (or no) capacitor keeps only what fits
            target = null;
            weaponState = new WeaponState();
            lastSync = null;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- client --------------------------------------------------------------------------------

    public void applySync(TurretStateSync sync) {
        clientState = sync.state();
        clientYaw = sync.yaw();
        clientPitch = sync.pitch();
        clientTargetId = sync.targetId();
        clientHeat = sync.heat();
        clientHealth = sync.health();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState blockState, TurretBaseBlockEntity turret) {
        turret.tickClient();
    }

    private void tickClient() {
        prevRenderYaw = renderYaw;
        prevRenderPitch = renderPitch;
        float wantedYaw = clientYaw, wantedPitch = clientPitch;
        WeaponData data = inventory.weaponData();
        if (clientState == TurretState.IDLE && (data == null || data.aim().turnSpeed() > 0)) {
            // Scanning sweep, client-only (PLAN 4.5): +-45 deg around where the turret last looked. A fixed-elevation
            // weapon keeps its angle; everything else levels out. Weapons that never turn (Tesla Coil) do not sweep.
            scanTicks++;
            wantedYaw = scanCenterYaw + 45f * Mth.sin(scanTicks * 0.03f);
            wantedPitch = data != null && data.aim().fixedPitch() ? -data.aim().minPitch() : 0;
        } else {
            scanTicks = 0;
            scanCenterYaw = clientYaw;
        }
        renderYaw += Mth.wrapDegrees(wantedYaw - renderYaw) * 0.35f;
        renderPitch += (wantedPitch - renderPitch) * 0.35f;
    }

    public float renderYaw(float partialTick) {
        return Mth.rotLerp(partialTick, prevRenderYaw, renderYaw);
    }

    public float renderPitch(float partialTick) {
        return Mth.lerp(partialTick, prevRenderPitch, renderPitch);
    }

    public TurretState clientState() {
        return clientState;
    }

    public float clientHeat() {
        return clientHeat;
    }

    /** Health as a 0-1 fraction of max, client side. */
    public float clientHealth() {
        return clientHealth;
    }

    /** Below this fraction the base shows its damaged state (PLAN 4.1). */
    public boolean clientDamaged() {
        return clientHealth < DAMAGED_FRACTION;
    }

    public int clientTargetId() {
        return clientTargetId;
    }

    public int clientTubes() {
        return clientTubes;
    }

    public void setClientTubes(int tubes) {
        clientTubes = tubes;
    }

    /** Barrels reach past the block; keep rendering while any part of the turret is on screen. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(large() ? 4 : 2.5);
    }

    // --- persistence & sync --------------------------------------------------------------------

    @Override
    public <C> LazyOptional<C> getCapability(Capability<C> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return ammoCapability.cast();
        if (cap == ForgeCapabilities.ENERGY) return energyCapability.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        ammoCapability.invalidate();
        energyCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        ammoCapability = LazyOptional.of(() -> ammo);
        energyCapability = LazyOptional.of(() -> energyStorage);
    }

    // Defaults are left out so an empty T1 base drops (via the loot table's copy_nbt) without NBT.
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!inventory.isEmpty()) tag.put("Inventory", inventory.serializeNBT());
        if (tier != TurretTier.T1) tag.putString("Tier", tier.name());
        if (yaw != 0) tag.putFloat("Yaw", yaw);
        if (pitch != 0) tag.putFloat("Pitch", pitch);
        if (heat > 0) tag.putFloat("Heat", heat);
        if (ammoCredit > 0) tag.putFloat("AmmoCredit", ammoCredit);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.put("Filter", filter.save());
        if (!enabled) tag.putBoolean("Disabled", true);
        if (redstoneInverted) tag.putBoolean("RedstoneInverted", true);
        if (health >= 0) tag.putFloat("Health", health);
        if (salvo) tag.putBoolean("Salvo", true);
        if (weaponState.tubes != 0) tag.putInt("Tubes", weaponState.tubes);
        if (energy > 0) tag.putInt("Energy", energy);
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(MODEL_TIER, tier).build();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        TurretTier previous = tier;
        tier = TurretTier.byName(tag.getString("Tier"));
        if (level != null && level.isClientSide && tier != previous) { // re-mesh the base with the new tier parts
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        yaw = tag.getFloat("Yaw");
        pitch = tag.getFloat("Pitch");
        heat = tag.getFloat("Heat");
        ammoCredit = tag.getFloat("AmmoCredit");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        filter = TargetFilter.load(tag.getCompound("Filter"));
        enabled = !tag.getBoolean("Disabled");
        redstoneInverted = tag.getBoolean("RedstoneInverted");
        health = tag.contains("Health") ? tag.getFloat("Health") : -1;
        salvo = tag.getBoolean("Salvo");
        energy = tag.getInt("Energy");
        weaponState.tubes = weaponState.syncedTubes = clientTubes = tag.getInt("Tubes");
        if (tag.contains("State")) { // only in the client update tag
            clientState = TurretState.byId(tag.getByte("State"));
            clientYaw = renderYaw = prevRenderYaw = scanCenterYaw = yaw;
            clientPitch = renderPitch = prevRenderPitch = pitch;
        }
    }

    /** What a client gets on chunk load and weapon changes: the saved data plus the live state. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = saveWithoutMetadata();
        tag.putByte("State", (byte) state.ordinal());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new TurretMenu(id, playerInventory, this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animations;
    }
}
