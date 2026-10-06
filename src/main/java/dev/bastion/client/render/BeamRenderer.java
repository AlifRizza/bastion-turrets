package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Laser Rifle's beam: a straight shaft from the emitter to where it stopped, wide and white-hot the moment it fires,
 * then narrowing and fading over its life while energy streams along it. Three passes like ArcRenderer: an alpha-blended
 * halo in a deeper shade (so it reads by day), an additive glow with the streaming texture, a white core.
 */
public final class BeamRenderer {
    private static final ResourceLocation SHAFT = Bastion.id("textures/vfx/arc.png"), ENERGY = Bastion.id("textures/vfx/beam.png");
    private static final int MAX_ACTIVE = 64;
    private static final List<Beam> ACTIVE = new ArrayList<>();

    private BeamRenderer() {
    }

    public static void spawn(Vec3 from, Vec3 to, int argb, float width, int life) {
        if (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.remove(0);
        ACTIVE.add(new Beam(from, to, argb, width, Math.max(2, life)));
    }

    public static void tick() {
        for (Iterator<Beam> it = ACTIVE.iterator(); it.hasNext(); ) {
            Beam beam = it.next();
            if (++beam.age > beam.life) it.remove();
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick) {
        if (ACTIVE.isEmpty()) return;
        Matrix4f pose = poseStack.last().pose();
        Vec3 cam = camera.getPosition();
        VertexConsumer halo = buffers.getBuffer(BastionRenderTypes.translucentGlow(SHAFT));
        for (Beam b : ACTIVE) quad(halo, pose, cam, b, partialTick, 4.5f, ArcRenderer.deepen(b.argb), 0.35f, 0);
        VertexConsumer energy = buffers.getBuffer(BastionRenderTypes.additiveGlow(ENERGY));
        for (Beam b : ACTIVE) quad(energy, pose, cam, b, partialTick, 2.2f, b.argb, 0.85f, 0.6f);
        VertexConsumer core = buffers.getBuffer(BastionRenderTypes.additiveGlow(SHAFT));
        for (Beam b : ACTIVE) quad(core, pose, cam, b, partialTick, 0.6f, 0xFFFFFF, 1f, 0);
    }

    /** One pass: {@code widthScale} x the beam's current width; {@code flow} = texture repeats per tick toward the end. */
    private static void quad(VertexConsumer buffer, Matrix4f pose, Vec3 cam, Beam b, float partialTick, float widthScale, int rgb,
                             float alpha, float flow) {
        float t = Mth.clamp((b.age + partialTick) / b.life, 0, 1);
        float width = b.width * widthScale * (t < 0.1f ? 1.4f - t * 4 : 1.0f - 0.75f * (t - 0.1f) / 0.9f); // flash, then narrows
        float a = alpha * (t < 0.4f ? 1 : 1 - (t - 0.4f) / 0.6f);
        Vec3 along = b.to.subtract(b.from), mid = b.from.add(along.scale(0.5));
        Vec3 side = along.cross(cam.subtract(mid));
        if (side.lengthSqr() < 1e-10 || a <= 0) return;
        side = side.normalize().scale(width * 0.5 * Math.max(1, mid.distanceTo(cam) / 16));
        float time = b.age + partialTick, length = (float) along.length();
        float u0 = -time * flow, u1 = length * 0.25f - time * flow;
        Vec3 p0 = b.from.subtract(cam), p1 = b.to.subtract(cam);
        vertex(buffer, pose, p0.add(side), rgb, a, u0, 0);
        vertex(buffer, pose, p0.subtract(side), rgb, a, u0, 1);
        vertex(buffer, pose, p1.subtract(side), rgb, a, u1, 1);
        vertex(buffer, pose, p1.add(side), rgb, a, u1, 0);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vec3 p, int rgb, float alpha, float u, float v) {
        buffer.vertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, alpha)
                .uv(u, v).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    private static final class Beam {
        final Vec3 from, to;
        final int argb, life;
        final float width;
        int age;

        Beam(Vec3 from, Vec3 to, int argb, float width, int life) {
            this.from = from;
            this.to = to;
            this.argb = argb;
            this.width = width;
            this.life = life;
        }
    }
}
