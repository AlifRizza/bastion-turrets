package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Camera-facing strips (PLAN 5.2): {@link #segment} draws one glowing streak between two points;
 * {@link #ribbon} joins a point history into a trail whose width and alpha shrink toward the tail.
 * Expects an {@link BastionRenderTypes#additiveGlow} buffer; u runs tail (0) to head (1).
 */
public final class TrailRenderer {
    private TrailRenderer() {
    }

    /** Streak from {@code tail} to {@code head}; widened with camera distance so far tracers stay visible. */
    public static void segment(VertexConsumer buffer, Matrix4f pose, Vec3 camera, Vec3 tail, Vec3 head,
                               float width, int argb, float alpha) {
        Vec3 side = side(camera, tail, head, width);
        if (side == null) return;
        Vec3 a = tail.subtract(camera), b = head.subtract(camera);
        vertex(buffer, pose, a.add(side), argb, alpha, 0, 0);
        vertex(buffer, pose, a.subtract(side), argb, alpha, 0, 1);
        vertex(buffer, pose, b.subtract(side), argb, alpha, 1, 1);
        vertex(buffer, pose, b.add(side), argb, alpha, 1, 0);
    }

    /** {@code points} newest first. */
    public static void ribbon(VertexConsumer buffer, Matrix4f pose, Vec3 camera, List<Vec3> points,
                              float width, int argb, float alpha) {
        int n = points.size();
        for (int i = 0; i + 1 < n; i++) {
            float f0 = 1 - (float) i / n, f1 = 1 - (float) (i + 1) / n;
            Vec3 p0 = points.get(i), p1 = points.get(i + 1);
            Vec3 s0 = side(camera, p1, p0, width * f0), s1 = side(camera, p1, p0, width * f1);
            if (s0 == null || s1 == null) continue;
            Vec3 a = p1.subtract(camera), b = p0.subtract(camera);
            vertex(buffer, pose, a.add(s1), argb, alpha * f1, 0.5f, 0);
            vertex(buffer, pose, a.subtract(s1), argb, alpha * f1, 0.5f, 1);
            vertex(buffer, pose, b.subtract(s0), argb, alpha * f0, 1, 1);
            vertex(buffer, pose, b.add(s0), argb, alpha * f0, 1, 0);
        }
    }

    private static Vec3 side(Vec3 camera, Vec3 from, Vec3 to, float width) {
        Vec3 along = to.subtract(from);
        Vec3 mid = from.add(to).scale(0.5);
        Vec3 side = along.cross(camera.subtract(mid));
        if (side.lengthSqr() < 1e-10) return null;
        float widen = (float) Math.max(1, mid.distanceTo(camera) / 12);
        return side.normalize().scale(width * 0.5 * widen);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vec3 p, int argb, float alpha, float u, float v) {
        buffer.vertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, alpha)
                .uv(u, v).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }
}
