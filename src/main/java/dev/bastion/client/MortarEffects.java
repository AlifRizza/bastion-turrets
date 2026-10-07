package dev.bastion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import dev.bastion.client.render.BastionRenderTypes;
import dev.bastion.client.vfx.DynamicLightManager;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Mortar fire patches on the client (the server burns whoever stands in them, FirePatches). Every block of the square
 * burns visibly (user: the whole 3x3 must read as on fire): the ground of exactly that square glows with embers, and on
 * each block four sheets of flame (two corner to corner, two through the middle) climb and flicker, in three layers (an
 * orange body and a yellow inner flame, alpha-blended so they hold up by day, and a hot core). It flares up, burns, and
 * dies down over its last quarter, with smoke, embers, a flickering light, a crackle and a scorch left on the ground.
 * One FIRE_PATCH event starts it; nothing else is synced.
 */
public final class MortarEffects {
    private static final ResourceLocation SHEET = Bastion.id("textures/vfx/fire_sheet.png"), ORB = Bastion.id("textures/vfx/orb.png");
    private static final int FIRE = 0xFFFF8F3A, MAX_PATCHES = 48, FLARE_TICKS = 6;
    private static final List<Patch> PATCHES = new ArrayList<>();

    private static final class Patch {
        /** Centre of the middle block, on the ground. */
        final Vec3 center;
        final int size, ticks, seed;
        int age;

        Patch(Vec3 center, int size, int ticks) {
            this.center = center;
            this.size = size;
            this.ticks = ticks;
            this.seed = center.hashCode();
        }

        /** 0..1: flares up over FLARE_TICKS, burns, dies down over the last quarter. */
        float strength(float partialTick) {
            float age = this.age + partialTick;
            return Mth.clamp(Math.min(age / FLARE_TICKS, (ticks - age) / (ticks * 0.25f)), 0, 1);
        }
    }

    private MortarEffects() {
    }

    public static void firePatch(ClientLevel level, Vec3 center, int size, int ticks) {
        if (PATCHES.size() >= MAX_PATCHES) PATCHES.remove(0);
        PATCHES.add(new Patch(center, Math.max(1, size), ticks));
        VfxManager.play(VfxPresets.MORTAR_SCORCH, center, new Vec3(0, 1, 0),
                VfxParams.of().color(FIRE).normal(new Vec3(0, 1, 0)).scale(size / 3f).seed(center.hashCode()));
        level.playLocalSound(center.x, center.y, center.z, BastionSounds.FLAMETHROWER_IGNITE.get(), SoundSource.HOSTILE, 0.9f, 0.8f, false);
    }

    public static void tick(ClientLevel level) {
        RandomSource random = level.random;
        PATCHES.removeIf(p -> {
            float on = p.strength(0);
            if (random.nextFloat() < on * 0.6f) { // a tongue licking higher than the sheets now and then
                Vec3 at = p.center.add((random.nextDouble() - 0.5) * p.size, 0.3, (random.nextDouble() - 0.5) * p.size);
                VfxManager.play(VfxPresets.FIRE_PATCH, at, new Vec3(0, 1, 0), VfxParams.of().color(FIRE).seed(random.nextLong()));
            }
            if (random.nextFloat() < on * 0.2f) {
                Vec3 at = p.center.add((random.nextDouble() - 0.5) * p.size, 0.8, (random.nextDouble() - 0.5) * p.size);
                VfxManager.play(VfxPresets.FIRE_PATCH_SMOKE, at, new Vec3(0, 1, 0), VfxParams.of().color(FIRE).seed(random.nextLong()));
            }
            DynamicLightManager.add(p, p.center.add(0, 0.6, 0), Math.round(9 + 5 * on), 2);
            if (p.age % 20 == 0 && on > 0.3f) {
                level.playLocalSound(p.center.x, p.center.y, p.center.z, BastionSounds.BURNING.get(), SoundSource.HOSTILE, 0.8f,
                        0.9f + random.nextFloat() * 0.2f, false);
            }
            return ++p.age >= p.ticks;
        });
    }

    public static void clear() {
        PATCHES.clear();
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick, ClientLevel level) {
        if (PATCHES.isEmpty()) return;
        Matrix4f pose = poseStack.last().pose();
        Vec3 cam = camera.getPosition();
        float time = level.getGameTime() + partialTick;
        VertexConsumer body = buffers.getBuffer(BastionRenderTypes.translucentGlow(SHEET));
        for (Patch p : PATCHES) ground(body, pose, cam, p, time, partialTick);
        for (Patch p : PATCHES) flames(body, pose, cam, p, time, partialTick, 1.2f, 0xFF6A14, 0.95f, 0);
        for (Patch p : PATCHES) flames(body, pose, cam, p, time, partialTick, 0.75f, 0xFFB347, 0.9f, 0.37f);
        VertexConsumer core = buffers.getBuffer(BastionRenderTypes.additiveGlow(SHEET));
        for (Patch p : PATCHES) flames(core, pose, cam, p, time, partialTick, 0.45f, 0xFFE8B0, 0.6f, 0.71f);
        VertexConsumer bed = buffers.getBuffer(BastionRenderTypes.additiveGlow(ORB));
        for (Patch p : PATCHES) embers(bed, pose, cam, p, time, partialTick);
    }

    /** The burning ground itself: exactly the patch's square, blotched with glowing embers that drift slowly. */
    private static void ground(VertexConsumer buffer, Matrix4f pose, Vec3 cam, Patch p, float time, float partialTick) {
        float on = p.strength(partialTick);
        if (on <= 0) return;
        double half = p.size / 2.0, y = p.center.y + 0.025;
        double x0 = p.center.x - half, x1 = p.center.x + half, z0 = p.center.z - half, z1 = p.center.z + half;
        float drift = time * 0.004f, span = p.size * 0.5f, alpha = 0.8f * on;
        vertex(buffer, pose, cam, x0, y, z0, 0xFF4A10, alpha, drift, drift);
        vertex(buffer, pose, cam, x0, y, z1, 0xFF4A10, alpha, drift, drift + span);
        vertex(buffer, pose, cam, x1, y, z1, 0xFF4A10, alpha, drift + span, drift + span);
        vertex(buffer, pose, cam, x1, y, z0, 0xFF4A10, alpha, drift + span, drift);
    }

    /**
     * One layer of flame: on every block of the patch four sheets (two corner to corner, two through the middle),
     * {@code height} tall at full strength (each block flickering on its own), solid at the bottom and fading out at the
     * tips, texture climbing.
     */
    private static void flames(VertexConsumer buffer, Matrix4f pose, Vec3 cam, Patch p, float time, float partialTick,
                               float height, int rgb, float alpha, float phase) {
        float on = p.strength(partialTick);
        if (on <= 0) return;
        int r = p.size / 2;
        float climb = time * 0.045f + phase;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int seed = p.seed + dx * 7919 + dz * 104729;
                float h = height * on * (0.8f + 0.25f * Mth.sin(time * 0.37f + (seed & 63))) * (dx == 0 && dz == 0 ? 1.15f : 1);
                double x0 = p.center.x - 0.5 + dx, z0 = p.center.z - 0.5 + dz, y = p.center.y + 0.02;
                float u = (seed & 255) / 255f;
                sheet(buffer, pose, cam, x0, z0, x0 + 1, z0 + 1, y, h, u, climb, rgb, alpha * on);
                sheet(buffer, pose, cam, x0, z0 + 1, x0 + 1, z0, y, h, u + 0.5f, climb, rgb, alpha * on);
                sheet(buffer, pose, cam, x0, z0 + 0.5, x0 + 1, z0 + 0.5, y, h * 0.9f, u + 0.25f, climb + 0.3f, rgb, alpha * on);
                sheet(buffer, pose, cam, x0 + 0.5, z0, x0 + 0.5, z0 + 1, y, h * 0.9f, u + 0.75f, climb + 0.6f, rgb, alpha * on);
            }
        }
    }

    private static void sheet(VertexConsumer buffer, Matrix4f pose, Vec3 cam, double xa, double za, double xb, double zb, double y,
                              float height, float u, float climb, int rgb, float alpha) {
        vertex(buffer, pose, cam, xa, y, za, rgb, alpha, u, climb + 0.9f);
        vertex(buffer, pose, cam, xb, y, zb, rgb, alpha, u + 0.6f, climb + 0.9f);
        vertex(buffer, pose, cam, xb, y + height, zb, rgb, 0, u + 0.6f, climb);
        vertex(buffer, pose, cam, xa, y + height, za, rgb, 0, u, climb);
    }

    /** A glowing bed of embers under the flames, a little wider than the patch, pulsing slowly. */
    private static void embers(VertexConsumer buffer, Matrix4f pose, Vec3 cam, Patch p, float time, float partialTick) {
        float on = p.strength(partialTick);
        if (on <= 0) return;
        double half = p.size / 2.0 + 0.4, y = p.center.y + 0.03;
        float alpha = on * (0.55f + 0.1f * Mth.sin(time * 0.21f + (p.seed & 31)));
        double x0 = p.center.x - half, x1 = p.center.x + half, z0 = p.center.z - half, z1 = p.center.z + half;
        vertex(buffer, pose, cam, x0, y, z0, 0xFF6A1E, alpha, 0, 0);
        vertex(buffer, pose, cam, x0, y, z1, 0xFF6A1E, alpha, 0, 1);
        vertex(buffer, pose, cam, x1, y, z1, 0xFF6A1E, alpha, 1, 1);
        vertex(buffer, pose, cam, x1, y, z0, 0xFF6A1E, alpha, 1, 0);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vec3 cam, double x, double y, double z, int rgb, float alpha,
                               float u, float v) {
        buffer.vertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, alpha)
                .uv(u, v).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }
}
