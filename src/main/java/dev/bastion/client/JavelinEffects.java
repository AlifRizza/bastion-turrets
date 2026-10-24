package dev.bastion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import dev.bastion.client.overwatch.OverwatchClient;
import dev.bastion.client.render.BastionRenderTypes;
import dev.bastion.client.render.HologramRenderer;
import dev.bastion.client.render.SmokeTrailRenderer;
import dev.bastion.client.vfx.DynamicLightManager;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.projectile.JavelinMissileEntity;
import dev.bastion.registry.BastionSounds;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.types.JavelinWeapon;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

/**
 * The Javelin missile on the client (PLAN "Turret 3x3"): nothing while the gas pushes it half out of the silo and it
 * holds there; at ignition a burst of flame and smoke out of the silo mouth (its tail is still inside), then on the climb the engine's flame, light and a thick
 * smoke column, with a roar that rides it up; nothing while it waits out of sight at the top (its column hangs on); on
 * the dive a thin trail and a whistle that rises as it falls; and the mark on its target from launch to impact: a hologram chevron over the target's head and
 * a ring on the ground shrinking from the blast radius to the impact, cyan, then blinking red in the last second. The
 * cold launch is TurretFireEffects, the blast ClientPacketHandler.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class JavelinEffects {
    private static final ResourceLocation CHEVRON = Bastion.id("textures/vfx/chevron.png");
    private static final ResourceLocation ORB = Bastion.id("textures/vfx/orb.png");
    private static final int RING_SEGMENTS = 48;
    /** Half the width of the ring's glowing band, blocks. */
    private static final double BAND = 0.11;
    private static final int FLAME = 0xFFFF7A2E, RED = 0xFFFF3A30;
    /** The mark turns red and blinks this many ticks before impact. */
    private static final int LAST_SECOND = 20;
    private static final Vec3 UP = new Vec3(0, 1, 0), DOWN = new Vec3(0, -1, 0);
    /** Missiles seen held over their silo (they get the ignition burst), whose engine has lit (its roar started), whose dive whistle has started. */
    private static final Set<Integer> HELD = new HashSet<>(), ROARING = new HashSet<>(), WHISTLING = new HashSet<>();

    private JavelinEffects() {
    }

    /** The smoke trail id of the dive: the missile is moved at the top, so the climb's column must not join it. */
    public static int diveTrail(int id) {
        return -id - 1;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.phase != TickEvent.Phase.END || level == null || Minecraft.getInstance().isPaused()) return;
        Set<Integer> alive = new HashSet<>();
        long now = level.getGameTime();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof JavelinMissileEntity missile)) continue;
            int id = missile.getId();
            alive.add(id);
            VfxParams params = VfxParams.of().color(FLAME).seed(now * 31 + id);
            if (missile.phase() == JavelinMissileEntity.COLD) HELD.add(id);
            if (missile.phase() == JavelinMissileEntity.CLIMB) {
                Vec3 tail = missile.position();
                if (ROARING.add(id)) { // ignition: flame and smoke blast out of the silo mouth round it (seen held: not one met mid-climb)
                    if (HELD.contains(id)) {
                        VfxManager.play(VfxPresets.ROCKET_LAUNCH, tail.add(0, JavelinMissileEntity.HELD_DEPTH, 0), DOWN,
                                VfxParams.of().color(FLAME).seed(now * 31 + id).scale(1.8f));
                        level.playLocalSound(tail.x, tail.y, tail.z, BastionSounds.ROCKET_LAUNCH.get(), SoundSource.HOSTILE, 2f, 0.7f, false);
                    }
                    Minecraft.getInstance().getSoundManager().play(new Follow(missile, BastionSounds.JAVELIN_FLY.get(), 1.4f));
                }
                VfxManager.play(VfxPresets.JAVELIN_TRAIL, tail, DOWN, params);
                SmokeTrailRenderer.addColumn(id, tail, now);
                DynamicLightManager.add(missile, tail, 14, 2);
            } else if (missile.phase() == JavelinMissileEntity.DIVE) {
                Vec3 tail = missile.position().add(0, missile.getBbHeight(), 0);
                VfxManager.play(VfxPresets.MISSILE_TRAIL, tail, UP, params);
                SmokeTrailRenderer.add(diveTrail(id), tail, true, now);
                DynamicLightManager.add(missile, missile.position(), 10, 2);
                if (WHISTLING.add(id)) Minecraft.getInstance().getSoundManager().play(new Follow(missile, BastionSounds.JAVELIN_DIVE.get(), 1.6f));
            }
        }
        HELD.retainAll(alive);
        ROARING.retainAll(alive);
        WHISTLING.retainAll(alive);
    }

    /** The marks, once per frame (VfxManager, after particles); under Overwatch also where the crosshair would drop one. */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick, ClientLevel level) {
        float time = level.getGameTime() + partialTick;
        TurretBaseBlockEntity base = OverwatchClient.turret(level);
        WeaponData data = base == null ? null : base.inventory().weaponData();
        Vec3 aim = OverwatchClient.aimPoint();
        if (aim != null && data != null && BastionWeaponTypes.REGISTRY.get().getValue(data.type()) instanceof JavelinWeapon) {
            mark(poseStack, buffers, camera, aim, 2.4, data.param("blast_radius"), OverwatchClient.canFire() ? VfxPresets.CYAN : RED, time);
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof JavelinMissileEntity missile)) continue;
            int left = missile.ticksToImpact();
            boolean last = left <= LAST_SECOND;
            if (last && ((int) time / 2 & 1) == 1) continue; // blinking
            int colour = last ? RED : VfxPresets.CYAN;
            // Over the target while it stands where the missile will land, else over the drop point (it hid under a roof).
            Vec3 centre = missile.dropPoint();
            double head = 2.4;
            Entity target = missile.targetId() < 0 ? null : level.getEntity(missile.targetId());
            if (target != null && target.isAlive()) {
                Vec3 at = target.getPosition(partialTick);
                if (Math.hypot(at.x - centre.x, at.z - centre.z) < 1.5) {
                    centre = at;
                    head = target.getBbHeight() + 0.7;
                }
            }
            float progress = Mth.clamp((left - partialTick) / Math.max(1, missile.tickCount + left), 0, 1); // 1 at launch, 0 at impact
            mark(poseStack, buffers, camera, centre, head, 0.4 + (missile.blastRadius() - 0.4) * progress, colour, time);
        }
    }

    /** A ring of {@code radius} on the ground at {@code centre} and the chevron {@code head} blocks over it, bobbing. */
    private static void mark(PoseStack poseStack, MultiBufferSource buffers, Camera camera, Vec3 centre, double head, double radius, int colour, float time) {
        ring(buffers.getBuffer(BastionRenderTypes.translucentGlow(ORB)), poseStack.last().pose(), centre.add(0, 0.03, 0).subtract(camera.getPosition()),
                radius, colour);
        HologramRenderer.billboard(poseStack, buffers, camera, CHEVRON, centre.add(0, head + 0.12 * Mth.sin(time * 0.25f), 0),
                0.9f, 0, colour, 0.95f, time);
    }

    /**
     * A flat glowing band lying on the ground round {@code c} (camera-relative), alpha-blended so it shows on a bright
     * floor by day; solid, where camera-facing strips broke into dashes.
     */
    private static void ring(VertexConsumer buffer, Matrix4f pose, Vec3 c, double radius, int colour) {
        float r = (colour >> 16 & 255) / 255f, g = (colour >> 8 & 255) / 255f, b = (colour & 255) / 255f;
        double inner = Math.max(0, radius - BAND), outer = radius + BAND;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a0 = i * 2 * Math.PI / RING_SEGMENTS, a1 = (i + 1) * 2 * Math.PI / RING_SEGMENTS;
            corner(buffer, pose, c, inner, a0, r, g, b, 0);
            corner(buffer, pose, c, outer, a0, r, g, b, 1);
            corner(buffer, pose, c, outer, a1, r, g, b, 1);
            corner(buffer, pose, c, inner, a1, r, g, b, 0);
        }
    }

    /** The orb sprite's middle column: bright along the band's centre line, soft at its edges. */
    private static void corner(VertexConsumer buffer, Matrix4f pose, Vec3 c, double radius, double angle, float r, float g, float b, float v) {
        buffer.vertex(pose, (float) (c.x + Math.cos(angle) * radius), (float) c.y, (float) (c.z + Math.sin(angle) * radius))
                .color(r, g, b, 0.95f).uv(0.5f, v).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    public static void clear() {
        HELD.clear();
        ROARING.clear();
        WHISTLING.clear();
    }

    /**
     * A sound riding along with the missile for the phase it started in: the climb's roar (a loop), or the dive's whistle,
     * whose pitch rises as the missile comes down.
     */
    private static final class Follow extends AbstractTickableSoundInstance {
        private final JavelinMissileEntity missile;
        private final int phase, start;
        private int fading;

        Follow(JavelinMissileEntity missile, SoundEvent sound, float volume) {
            super(sound, SoundSource.HOSTILE, RandomSource.create());
            this.missile = missile;
            this.phase = missile.phase();
            this.start = Math.max(1, missile.ticksToImpact());
            this.looping = phase == JavelinMissileEntity.CLIMB;
            this.delay = 0;
            this.volume = volume;
            this.pitch = phase == JavelinMissileEntity.CLIMB ? 0.95f + random.nextFloat() * 0.1f : 0.7f;
            this.x = missile.getX();
            this.y = missile.getY();
            this.z = missile.getZ();
        }

        @Override
        public void tick() {
            if (missile.isRemoved() || missile.phase() != phase) {
                // Fade a couple of ticks first: stopping a sound the engine has not started yet makes OpenAL complain.
                volume = 0;
                if (++fading > 2) stop();
                return;
            }
            x = missile.getX();
            y = missile.getY();
            z = missile.getZ();
            if (phase == JavelinMissileEntity.DIVE) pitch = 0.7f + 0.8f * (1 - missile.ticksToImpact() / (float) start);
        }
    }
}
