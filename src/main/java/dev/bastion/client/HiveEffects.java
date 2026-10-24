package dev.bastion.client;

import dev.bastion.Bastion;
import dev.bastion.client.render.TracerRenderer;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.network.TurretImpactEvent;
import dev.bastion.registry.BastionSounds;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.unit.HiveDroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Hive Carrier drones on the client (PLAN "Turret 3x3"): each shot a chin flash, a thin amber tracer to where it ended and
 * sparks on a hit (DRONE_SHOT); the kamikaze's red smoke trail and its blast (DRONE_KAMIKAZE); the fans' hum riding along
 * with the nearest few drones. The carrier's launch is TurretFireEffects (WeaponFx.HIVE_CARRIER).
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class HiveEffects {
    private static final int AMBER = 0xFFFFC94F, RED = 0xFFFF3A30, FLAME = 0xFFFF7A2E;
    /** Most fan hums playing at once: a full carrier is nine drones. */
    private static final int MAX_HUMS = 3;
    private static final Set<Integer> HUMMING = new HashSet<>();
    private static final Vec3 UP = new Vec3(0, 1, 0);
    /** A welder's blue-white; and how far out a pad is along its doorway (HiveCarrierWeapon.BAYS 12 px, muzzle 34.4 px). */
    private static final int WELD = 0xFFBFE8FF;
    static final double PAD = 12 / 34.4;

    private HiveEffects() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.phase != TickEvent.Phase.END || level == null || Minecraft.getInstance().isPaused()) return;
        Set<Integer> alive = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof HiveDroneEntity drone) || drone.isDeadOrDying()) continue;
            alive.add(drone.getId());
            if (drone.state() == HiveDroneEntity.State.KAMIKAZE) {
                VfxManager.play(VfxPresets.DRONE_TRAIL, drone.getBoundingBox().getCenter(), drone.getDeltaMovement().normalize().reverse(),
                        VfxParams.of().color(RED).seed(level.getGameTime() * 31 + drone.getId()));
            }
            if (HUMMING.size() < MAX_HUMS && HUMMING.add(drone.getId())) Minecraft.getInstance().getSoundManager().play(new Hum(drone));
        }
        HUMMING.retainAll(alive);
    }

    /**
     * Sparks off the new drones being welded together on the hive's empty pads between waves (ClientTurret): pad p from
     * the model's muzzle_p, the mouth of its doorway (so where the model draws it, whichever way the geo is mirrored),
     * back in to the pad (12 of its 34.4 px from the middle) and down to the drone's middle. Not every pad every time.
     */
    public static void weld(ClientLevel level, ClientTurret client, TurretBaseBlockEntity turret, int parked) {
        Vec3 centre = turret.alongMount(TurretBaseBlockEntity.BASE_HEIGHT);
        for (int p = Math.max(0, parked); p < 3; p++) {
            Vec3 door = client.muzzles[p];
            if (door == null || level.random.nextInt(3) == 0) continue;
            Vec3 pad = new Vec3(centre.x + (door.x - centre.x) * PAD, door.y - 0.06, centre.z + (door.z - centre.z) * PAD);
            VfxManager.play(VfxPresets.HIVE_WELD, pad, UP, VfxParams.of().color(WELD).seed(level.getGameTime() * 31 + p));
        }
    }

    /** DRONE_SHOT and DRONE_KAMIKAZE (ClientPacketHandler). */
    public static void impact(ClientLevel level, TurretImpactEvent event) {
        Vec3 at = event.pos();
        if (event.kind() == TurretImpactEvent.DRONE_SHOT) {
            if (!(level.getEntity(event.source()) instanceof HiveDroneEntity drone)) return;
            Vec3 from = drone.position().add(0, drone.getBbHeight() * 0.3, 0);
            drone.triggerAnim("shot", "fire");
            VfxManager.play(VfxPresets.DRONE_MUZZLE, from, at.subtract(from).normalize(), VfxParams.of().color(AMBER).seed(at.hashCode()));
            // A lean impact, and only where the bullet met something: four shots a second from up to nine drones must stay
            // inside the particle budget (GUN_IMPACT was ~12 particles and a decal a shot, also on misses in mid-air).
            boolean hit = event.scale() > 0, block = event.normal().lengthSqr() > 0.5;
            Vec3 normal = block ? event.normal() : from.subtract(at).normalize();
            TracerRenderer.spawn(from, at, AMBER, 0.05f, 2, !hit && !block ? null : () -> VfxManager.play(block ? VfxPresets.DRONE_IMPACT_BLOCK
                    : VfxPresets.DRONE_IMPACT, at, normal.reverse(), VfxParams.of().color(AMBER).normal(normal).seed(at.hashCode())));
            level.playLocalSound(from.x, from.y, from.z, BastionSounds.DRONE_SHOT.get(), SoundSource.HOSTILE, 0.5f, 0.92f + level.random.nextFloat() * 0.16f, false);
        } else if (event.kind() == TurretImpactEvent.DRONE_KAMIKAZE) {
            BlockPos below = BlockPos.containing(at.subtract(0, 0.5, 0));
            int blockColor = 0xFF000000 | level.getBlockState(below).getMapColor(level, below).col;
            VfxManager.play(VfxPresets.DRONE_KAMIKAZE, at, UP, VfxParams.of().color(FLAME).blockColor(blockColor).scale(event.scale() / 3f).seed(at.hashCode()));
            level.playLocalSound(at.x, at.y, at.z, BastionSounds.DRONE_KAMIKAZE.get(), SoundSource.HOSTILE, 1.4f, 0.95f + level.random.nextFloat() * 0.1f, false);
        }
    }

    public static void clear() {
        HUMMING.clear();
    }

    /** The fans' hum riding along with a drone until it docks, dives or falls. */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final HiveDroneEntity drone;
        private int fading;

        Hum(HiveDroneEntity drone) {
            super(BastionSounds.DRONE_HUM.get(), SoundSource.HOSTILE, RandomSource.create());
            this.drone = drone;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.5f;
            this.pitch = 0.95f + random.nextFloat() * 0.1f;
            this.x = drone.getX();
            this.y = drone.getY();
            this.z = drone.getZ();
        }

        @Override
        public void tick() {
            if (drone.isRemoved() || drone.isDeadOrDying()) {
                volume = 0; // fade a couple of ticks first: stopping a sound the engine has not started makes OpenAL complain
                if (++fading > 2) stop();
                return;
            }
            x = drone.getX();
            y = drone.getY();
            z = drone.getZ();
        }
    }
}
