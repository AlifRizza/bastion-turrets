package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The smoke rockets and missiles leave behind (PLAN 5.2 ribbon): a camera-facing strip through the points each one
 * passed, widening, rising and fading as they age, so the trail hangs in the air for a moment after the rocket is
 * gone. Lit by the world (dark at night), the texture fixed in world space. Plus a hot exhaust glow at the nozzle.
 * Fed every tick by RocketEffects.
 */
public final class SmokeTrailRenderer {
    private static final ResourceLocation SMOKE = Bastion.id("textures/vfx/smoke_trail.png");
    private static final ResourceLocation GLOW = Bastion.id("textures/vfx/orb.png");
    /** Blocks of trail per texture repeat. */
    private static final double TILE = 3;
    private static final int MAX_TRAILS = 96;
    private static final Map<Integer, Trail> TRAILS = new HashMap<>();

    private record Point(Vec3 pos, long born, double distance) {
    }

    private static final class Trail {
        final ArrayDeque<Point> points = new ArrayDeque<>(); // newest first
        final int life;
        final float width, glow;
        final int argb;
        long fed;
        /** The rocket exploded: its last point is the blast, never draw it from the (vanishing) entity again. */
        boolean ended;

        Trail(int life, float width, float glow, int argb) {
            this.life = life;
            this.width = width;
            this.glow = glow;
            this.argb = argb;
        }
    }

    private SmokeTrailRenderer() {
    }

    /** The rocket {@code id} passed {@code tail} this tick; missiles leave a thinner, shorter trail. */
    public static void add(int id, Vec3 tail, boolean missile, long gameTime) {
        Trail trail = TRAILS.get(id);
        if (trail != null && trail.ended) return; // exploded: the entity may linger a tick on the client
        if (trail == null) {
            if (TRAILS.size() >= MAX_TRAILS) return;
            trail = missile ? new Trail(28, 0.32f, 0.2f, 0xE2E5E9) : new Trail(50, 0.62f, 0.32f, 0xA4A8AE);
            TRAILS.put(id, trail);
        }
        Point last = trail.points.peekFirst();
        double distance = last == null ? 0 : last.distance + last.pos.distanceTo(tail);
        trail.points.addFirst(new Point(tail, gameTime, distance));
        trail.fed = gameTime;
    }

    /**
     * Rocket {@code id} exploded at {@code at}. The server removes it a tick before it would reach that point, so its
     * trail runs on into the blast here (by id: in a salvo the nearest trail is often another missile's).
     */
    public static void end(int id, Vec3 at, long gameTime) {
        Trail trail = TRAILS.get(id);
        if (trail == null || trail.ended || trail.points.isEmpty()) return;
        Point last = trail.points.peekFirst();
        trail.points.addFirst(new Point(at, gameTime, last.distance + last.pos.distanceTo(at)));
        trail.ended = true;
    }

    public static void tick(long gameTime) {
        for (Iterator<Trail> it = TRAILS.values().iterator(); it.hasNext(); ) {
            Trail trail = it.next();
            while (!trail.points.isEmpty() && gameTime - trail.points.peekLast().born > trail.life) trail.points.removeLast();
            if (trail.points.isEmpty() && gameTime - trail.fed > 2) it.remove();
        }
    }

    public static void clear() {
        TRAILS.clear();
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick, ClientLevel level) {
        if (TRAILS.isEmpty()) return;
        Matrix4f pose = poseStack.last().pose();
        Vec3 cam = camera.getPosition();
        float now = level.getGameTime() + partialTick;
        VertexConsumer smoke = buffers.getBuffer(BastionRenderTypes.translucentGlow(SMOKE));
        Map<Vec3, Float> heads = new HashMap<>();
        for (Map.Entry<Integer, Trail> entry : TRAILS.entrySet()) {
            Trail trail = entry.getValue();
            if (trail.points.isEmpty()) continue;
            // A flying rocket is drawn between its last two tick positions: start the ribbon at its nozzle as drawn
            // and leave out this tick's point, which already lies ahead of it.
            Entity rocket = level.getEntity(entry.getKey());
            boolean flying = !trail.ended && rocket != null && rocket.isAlive();
            List<Point> points = new ArrayList<>(trail.points.size() + 1);
            if (flying) {
                Point newest = trail.points.peekFirst();
                Vec3 tail = rocket.getPosition(partialTick).add(newest.pos.subtract(rocket.position()));
                points.add(new Point(tail, (long) now, newest.distance - newest.pos.distanceTo(tail)));
                heads.put(tail, trail.glow);
            }
            for (Point point : trail.points) if (!flying || point.born < level.getGameTime()) points.add(point);
            ribbon(smoke, pose, cam, level, trail, points, now);
        }
        // Write the smoke pass completely before asking for the glow buffer (switching ends the batch).
        VertexConsumer glow = buffers.getBuffer(BastionRenderTypes.additiveGlow(GLOW));
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        heads.forEach((head, size) -> billboard(glow, pose, head.subtract(cam), left, up, size, 0xFFA048));
    }

    /**
     * The strip through {@code points} (newest first), each sized and faded by its own age. Neighbouring segments share
     * the edge at their common point (sideways from the averaged direction there), so the ribbon bends without seams.
     */
    private static void ribbon(VertexConsumer buffer, Matrix4f pose, Vec3 cam, ClientLevel level, Trail trail, List<Point> points, float now) {
        int n = points.size();
        if (n < 2) return;
        Vec3[] at = new Vec3[n], side = new Vec3[n];
        float[] age = new float[n];
        for (int i = 0; i < n; i++) {
            age[i] = Math.max(0, now - points.get(i).born);
            at[i] = rise(points.get(i).pos, age[i]);
        }
        for (int i = 0; i < n; i++) {
            Vec3 along = at[Math.max(i - 1, 0)].subtract(at[Math.min(i + 1, n - 1)]);
            Vec3 s = along.cross(cam.subtract(at[i]));
            side[i] = s.lengthSqr() < 1e-10 ? null : s.normalize().scale(width(trail, age[i]) * 0.5);
        }
        for (int i = 0; i + 1 < n; i++) {
            Vec3 a = at[i], b = at[i + 1], sa = side[i], sb = side[i + 1];
            if (sa == null || sb == null) continue;
            float uA = (float) (points.get(i).distance / TILE), uB = (float) (points.get(i + 1).distance / TILE);
            float alphaA = alpha(trail, age[i]), alphaB = alpha(trail, age[i + 1]);
            int lightA = LevelRenderer.getLightColor(level, BlockPos.containing(a)), lightB = LevelRenderer.getLightColor(level, BlockPos.containing(b));
            vertex(buffer, pose, b.add(sb).subtract(cam), trail.argb, alphaB, uB, 0, lightB);
            vertex(buffer, pose, b.subtract(sb).subtract(cam), trail.argb, alphaB, uB, 1, lightB);
            vertex(buffer, pose, a.subtract(sa).subtract(cam), trail.argb, alphaA, uA, 1, lightA);
            vertex(buffer, pose, a.add(sa).subtract(cam), trail.argb, alphaA, uA, 0, lightA);
        }
    }

    /** Smoke drifts up a little as it ages. */
    private static Vec3 rise(Vec3 pos, float age) {
        return pos.add(0, age * 0.012, 0);
    }

    /** Thin at the nozzle, billowing out to about two and a half times as wide. */
    private static float width(Trail trail, float age) {
        return trail.width * (0.7f + 1.8f * Mth.sqrt(Mth.clamp(age / trail.life, 0, 1)));
    }

    /** Fades in over the first two ticks (the flame is there), then thins out to nothing. */
    private static float alpha(Trail trail, float age) {
        float t = Mth.clamp(age / trail.life, 0, 1);
        return 0.8f * Mth.clamp(age / 2f, 0.25f, 1) * (1 - t) * (1 - t);
    }

    private static void billboard(VertexConsumer buffer, Matrix4f pose, Vec3 at, Vector3f left, Vector3f up, float size, int rgb) {
        float lx = left.x() * size, ly = left.y() * size, lz = left.z() * size;
        float ux = up.x() * size, uy = up.y() * size, uz = up.z() * size;
        vertex(buffer, pose, at.add(-lx - ux, -ly - uy, -lz - uz), rgb, 0.9f, 0, 1, LightTexture.FULL_BRIGHT);
        vertex(buffer, pose, at.add(lx - ux, ly - uy, lz - uz), rgb, 0.9f, 1, 1, LightTexture.FULL_BRIGHT);
        vertex(buffer, pose, at.add(lx + ux, ly + uy, lz + uz), rgb, 0.9f, 1, 0, LightTexture.FULL_BRIGHT);
        vertex(buffer, pose, at.add(-lx + ux, -ly + uy, -lz + uz), rgb, 0.9f, 0, 0, LightTexture.FULL_BRIGHT);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vec3 p, int rgb, float alpha, float u, float v, int light) {
        buffer.vertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, alpha)
                .uv(u, v).uv2(light).endVertex();
    }
}
