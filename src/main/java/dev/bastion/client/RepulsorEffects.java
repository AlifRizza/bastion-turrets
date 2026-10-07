package dev.bastion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import dev.bastion.client.render.BastionRenderTypes;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Repulsor and Repulsor Dome on the client (the server pushes: RepulsorWeapon, RepulsorDomeWeapon). The wave: a pop at
 * the emitter, four rings racing out along the barrel and widening, the air rippling; every pushed mob kicks up dust.
 * The dome (user reference: a white "almighty push" sphere): a translucent white sphere grows from the turret's middle
 * to the full range in GROW ticks, brightest at its rim, and fades; a dust ring rolls out on the ground, speed lines fly.
 */
public final class RepulsorEffects {
    private static final ResourceLocation SHELL = Bastion.id("textures/vfx/dome.png");
    private static final int GROW = 8, FADE = 8, MAX_DOMES = 16, RINGS = 4, STACKS = 12, SLICES = 24;
    private static final double FAR_SOUND_DISTANCE = 32;
    private static final List<Dome> DOMES = new ArrayList<>();
    private static final List<Wave> WAVES = new ArrayList<>();
    /** Ticks the Repulsor's air front takes to sweep out; its cone's half angle (repulsor.json cone_angle). */
    private static final int SWEEP = 6, MAX_WAVES = 32;
    private static final double CONE = Math.toRadians(35);
    /** Sand, like the dust in the user's reference. */
    private static final int DUST = 0xB49B78;

    /** The Repulsor's front of pushed air: a band of a cone running out from the emitter. */
    private static final class Wave {
        final Vec3 from, direction, u, v;
        final double range;
        final int color;
        int age;

        Wave(Vec3 from, Vec3 direction, double range, int color) {
            this.from = from;
            this.direction = direction;
            this.range = range;
            this.color = color;
            Vec3 side = direction.cross(new Vec3(0, 1, 0));
            this.u = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize(); // straight up or down
            this.v = direction.cross(u).normalize();
        }

        float progress(float partialTick) {
            return Mth.clamp((age + partialTick) / SWEEP, 0, 1);
        }
    }

    private static final class Dome {
        final Vec3 center;
        final float radius;
        final int color;
        /** The floor under the base, where the dust ring rolls. */
        final double groundY;
        /** Dust colour, darkened by the light where the dome stands (the render type is fullbright). */
        final int dust;
        int age;

        Dome(Vec3 center, float radius, int color, double groundY, int dust) {
            this.center = center;
            this.radius = radius;
            this.color = color;
            this.groundY = groundY;
            this.dust = dust;
        }

        /** Radius now: out fast, easing in as it reaches the edge. */
        float size(float partialTick) {
            float t = Mth.clamp((age + partialTick) / GROW, 0, 1);
            return radius * (1 - (1 - t) * (1 - t) * (1 - t));
        }

        /** 1 while growing, then down to 0 over FADE ticks. */
        float strength(float partialTick) {
            return Mth.clamp(1 - (age + partialTick - GROW) / FADE, 0, 1);
        }
    }

    private RepulsorEffects() {
    }

    /** The Repulsor's wave along {@code direction} from the emitter, as far as {@code range}. */
    public static void wave(ClientLevel level, TurretFireEvent event, ClientTurret client, Vec3 muzzle, Vec3 direction, double range, int color) {
        VfxManager.play(VfxPresets.REPULSOR_MUZZLE, muzzle, direction, VfxParams.of().color(color).seed(event.seed()));
        for (int i = 0; i < RINGS; i++) {
            Vec3 at = muzzle.add(direction.scale(range * (i + 1) / (RINGS + 1)));
            VfxParams params = VfxParams.of().color(color).scale(0.6f + 0.55f * i).seed(event.seed() + i);
            Runnable ring = () -> VfxManager.play(VfxPresets.REPULSOR_RING, at, direction, params);
            if (i == 0) ring.run();
            else client.after(i, ring);
        }
        VfxManager.play(VfxPresets.REPULSOR_HAZE, muzzle.add(direction.scale(range * 0.4)), direction, VfxParams.of().seed(event.seed()));
        if (WAVES.size() >= MAX_WAVES) WAVES.remove(0);
        WAVES.add(new Wave(muzzle, direction, range, color));
        pushed(level, event, direction);
        sound(event, muzzle, BastionSounds.REPULSOR_PUSH, BastionSounds.REPULSOR_PUSH_TAIL);
    }

    /** The Dome's burst around {@code center}, out to {@code radius}. */
    public static void dome(ClientLevel level, TurretFireEvent event, Vec3 center, float radius, int color) {
        if (DOMES.size() >= MAX_DOMES) DOMES.remove(0);
        BlockPos base = event.pos();
        float light = Math.max(level.getBrightness(LightLayer.BLOCK, base), level.getBrightness(LightLayer.SKY, base) - level.getSkyDarken()) / 15f;
        int dust = shade(DUST, Math.max(0.2f, light));
        DOMES.add(new Dome(center, radius, color, base.getY() + 0.04, dust));
        VfxManager.play(VfxPresets.DOME_BURST, center, new Vec3(0, 1, 0), VfxParams.of().color(color).seed(event.seed()));
        // On the floor under the base, at the middle of the 2x2 (the orb's x/z).
        BlockPos ground = BlockPos.containing(center.x, event.pos().getY() - 1, center.z);
        VfxManager.play(VfxPresets.DOME_DUST, new Vec3(center.x, event.pos().getY() + 0.05, center.z), new Vec3(0, 1, 0),
                VfxParams.of().scale(radius / 6f).blockColor(groundColor(level, ground)).seed(event.seed()));
        pushed(level, event, null);
        sound(event, center, BastionSounds.DOME_BURST, BastionSounds.DOME_BURST_TAIL);
    }

    /** Dust at the feet of every pushed mob (found by id, so it is where the mob is now). */
    private static void pushed(ClientLevel level, TurretFireEvent event, @Nullable Vec3 direction) {
        for (TurretFireEvent.Shot shot : event.shots()) {
            Entity mob = level.getEntity(shot.blockState());
            Vec3 feet = mob != null ? mob.position() : shot.end();
            Vec3 away = direction != null ? direction : shot.normal().reverse();
            VfxManager.play(VfxPresets.REPULSOR_HIT, feet.add(0, 0.05, 0), away,
                    VfxParams.of().blockColor(groundColor(level, BlockPos.containing(feet).below())).seed(event.seed() + shot.blockState()));
        }
    }

    private static int shade(int rgb, float light) {
        return (int) ((rgb >> 16 & 255) * light) << 16 | (int) ((rgb >> 8 & 255) * light) << 8 | (int) ((rgb & 255) * light);
    }

    private static int groundColor(ClientLevel level, BlockPos pos) {
        return 0xFF000000 | level.getBlockState(pos).getMapColor(level, pos).col;
    }

    /** Near players hear the push; past 32 blocks the muffled tail (PLAN 5.7). */
    private static void sound(TurretFireEvent event, Vec3 at, RegistryObject<SoundEvent> near, RegistryObject<SoundEvent> far) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean distant = player.position().distanceTo(at) > FAR_SOUND_DISTANCE;
        RandomSource random = RandomSource.create(event.seed());
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance((distant ? far : near).get(), SoundSource.HOSTILE,
                distant ? 1.6f : 1.1f, 0.92f + random.nextFloat() * 0.16f, random, at.x, at.y, at.z));
    }

    public static void tick() {
        DOMES.removeIf(d -> ++d.age >= GROW + FADE);
        WAVES.removeIf(w -> ++w.age >= SWEEP);
    }

    public static void clear() {
        DOMES.clear();
        WAVES.clear();
    }

    /** Each dome as a sphere of quads, brighter where it turns away from the camera (its rim), the texture swirling. */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick, ClientLevel level) {
        if (DOMES.isEmpty() && WAVES.isEmpty()) return;
        Matrix4f pose = poseStack.last().pose();
        Vec3 cam = camera.getPosition();
        float swirl = (level.getGameTime() + partialTick) * 0.02f;
        VertexConsumer buffer = buffers.getBuffer(BastionRenderTypes.translucentGlow(SHELL));
        for (Wave w : WAVES) {
            float p = w.progress(partialTick);
            double front = w.range * (1 - (1 - p) * (1 - p)), back = Math.max(0, front - 1.4);
            float alpha = 0.7f * (1 - p);
            for (int j = 0; j < SLICES; j++) { // the sleeve between back (clear) and front (brightest)
                sleeve(buffer, pose, cam, w, back, j, 0, swirl);
                sleeve(buffer, pose, cam, w, front, j, alpha, swirl);
                sleeve(buffer, pose, cam, w, front, j + 1, alpha, swirl);
                sleeve(buffer, pose, cam, w, back, j + 1, 0, swirl);
            }
        }
        for (Dome d : DOMES) {
            float r = d.size(partialTick), on = d.strength(partialTick);
            if (r <= 0.05f || on <= 0) continue;
            // The dust ring: where the sphere meets the floor, a band of sand rolling out with it (clear at both edges).
            double h = d.center.y - d.groundY, edge = r > h ? Math.sqrt(r * r - h * h) : 0;
            if (edge > 0.3) {
                for (int j = 0; j < SLICES * 2; j++) {
                    ground(buffer, pose, cam, d, Math.max(0, edge - 2.6), j, 0, swirl);
                    ground(buffer, pose, cam, d, edge, j, 0.95f * on, swirl);
                    ground(buffer, pose, cam, d, edge, j + 1, 0.95f * on, swirl);
                    ground(buffer, pose, cam, d, Math.max(0, edge - 2.6), j + 1, 0, swirl);
                    ground(buffer, pose, cam, d, edge, j, 0.95f * on, swirl);
                    ground(buffer, pose, cam, d, edge + 0.9, j, 0, swirl);
                    ground(buffer, pose, cam, d, edge + 0.9, j + 1, 0, swirl);
                    ground(buffer, pose, cam, d, edge, j + 1, 0.95f * on, swirl);
                }
            }
            for (int i = 0; i < STACKS; i++) {
                for (int j = 0; j < SLICES; j++) {
                    vertex(buffer, pose, cam, d, r, on, i, j, swirl);
                    vertex(buffer, pose, cam, d, r, on, i + 1, j, swirl);
                    vertex(buffer, pose, cam, d, r, on, i + 1, j + 1, swirl);
                    vertex(buffer, pose, cam, d, r, on, i, j + 1, swirl);
                }
            }
        }
    }

    /** A point of the dust ring, {@code radius} out from the dome's middle on the floor. */
    private static void ground(VertexConsumer buffer, Matrix4f pose, Vec3 cam, Dome d, double radius, int slice, float alpha, float swirl) {
        double theta = Math.PI * slice / SLICES;
        double x = d.center.x + Math.cos(theta) * radius, z = d.center.z + Math.sin(theta) * radius;
        buffer.vertex(pose, (float) (x - cam.x), (float) (d.groundY - cam.y), (float) (z - cam.z))
                .color((d.dust >> 16 & 255) / 255f, (d.dust >> 8 & 255) / 255f, (d.dust & 255) / 255f, alpha)
                .uv((float) slice / SLICES * 3, (float) radius * 0.4f - swirl).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    /** A point on the wave's cone, {@code along} blocks out, at slice {@code slice} around it. */
    private static void sleeve(VertexConsumer buffer, Matrix4f pose, Vec3 cam, Wave w, double along, int slice, float alpha, float swirl) {
        double theta = 2 * Math.PI * slice / SLICES, radius = 0.35 + along * Math.tan(CONE);
        Vec3 at = w.from.add(w.direction.scale(along)).add(w.u.scale(Math.cos(theta) * radius)).add(w.v.scale(Math.sin(theta) * radius));
        buffer.vertex(pose, (float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z))
                .color((w.color >> 16 & 255) / 255f, (w.color >> 8 & 255) / 255f, (w.color & 255) / 255f, alpha)
                .uv((float) slice / SLICES * 2 + swirl, (float) along * 0.5f - swirl * 3).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vec3 cam, Dome d, float r, float on, int stack, int slice, float swirl) {
        double phi = Math.PI * stack / STACKS, theta = 2 * Math.PI * slice / SLICES;
        Vec3 normal = new Vec3(Math.sin(phi) * Math.cos(theta), Math.cos(phi), Math.sin(phi) * Math.sin(theta));
        Vec3 at = d.center.add(normal.scale(r));
        Vec3 view = cam.subtract(at).normalize();
        float rim = (float) (1 - Math.abs(normal.dot(view)));
        float alpha = on * (0.22f + 0.75f * rim * rim);
        buffer.vertex(pose, (float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z))
                .color((d.color >> 16 & 255) / 255f, (d.color >> 8 & 255) / 255f, (d.color & 255) / 255f, alpha)
                .uv((float) slice / SLICES * 3 + swirl, (float) stack / STACKS * 2 - swirl).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }
}
