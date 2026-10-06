package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lightning arcs (Tesla Coil), Bastion's own instead of vanilla lightning (PLAN 1): a jagged bolt between two points,
 * re-struck into a new shape every few ticks so it crackles, with forks splitting off it. Each strike is drawn as a
 * continuous camera-facing strip: a coloured halo, a glow and a thin white-hot core (see render).
 * Points come from midpoint displacement seeded per strike, so every frame of one strike agrees.
 */
public final class ArcRenderer {
    private static final ResourceLocation TEXTURE = Bastion.id("textures/vfx/arc.png");
    private static final int MAX_ACTIVE = 256, FADE_TICKS = 3;
    private static final List<Arc> ACTIVE = new ArrayList<>();

    private ArcRenderer() {
    }

    /**
     * @param from     where the bolt starts, read every frame (a moving entity's surface for crawling sparks)
     * @param to       where it ends, read every frame, so a bolt stays on a moving target
     * @param restrike ticks between new bolt shapes
     * @param forks    branches splitting off the main bolt
     * @param jag      sideways displacement as a fraction of the bolt's length
     */
    public static void spawn(Supplier<Vec3> from, Supplier<Vec3> to, int argb, float width, int life, int restrike, int forks,
                             float jag, long seed) {
        if (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.remove(0);
        ACTIVE.add(new Arc(from, to, argb, width, Math.max(1, life), Math.max(1, restrike), forks, jag, seed));
    }

    public static void tick() {
        for (Iterator<Arc> it = ACTIVE.iterator(); it.hasNext(); ) {
            Arc arc = it.next();
            if (++arc.age > arc.life + FADE_TICKS) it.remove();
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    /**
     * Three passes over the same strikes (separate buffers, so each pass is its own batch): a wide alpha-blended halo in a
     * deeper shade of the arc colour (what reads as blue against a daytime sky), an additive glow, a white-hot core.
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick) {
        if (ACTIVE.isEmpty()) return;
        List<Strike> strikes = new ArrayList<>();
        for (Arc arc : ACTIVE) strike(arc, partialTick, strikes);
        Matrix4f pose = poseStack.last().pose();
        Vec3 cam = camera.getPosition();
        VertexConsumer halo = buffers.getBuffer(BastionRenderTypes.translucentGlow(TEXTURE));
        for (Strike s : strikes) strip(halo, pose, cam, s.points, s.width * 5f, deepen(s.argb), s.alpha * 0.32f);
        VertexConsumer glow = buffers.getBuffer(BastionRenderTypes.additiveGlow(TEXTURE));
        for (Strike s : strikes) strip(glow, pose, cam, s.points, s.width * 3.2f, s.argb, s.alpha * 0.55f);
        for (Strike s : strikes) strip(glow, pose, cam, s.points, s.width * 0.85f, 0xFFFFFF, s.alpha);
    }

    /** The current shape of one arc: its bolt and forks, re-struck every {@code restrike} ticks. */
    private static void strike(Arc arc, float partialTick, List<Strike> out) {
        float time = arc.age + partialTick;
        float fade = time <= arc.life ? 1 : Mth.clamp(1 - (time - arc.life) / FADE_TICKS, 0, 1);
        RandomSource random = RandomSource.create(arc.seed * 0x9E3779B97F4A7C15L + arc.age / arc.restrike);
        float alpha = fade * (0.7f + 0.3f * random.nextFloat()); // flicker per strike
        Vec3 a = arc.from.get(), b = arc.to.get();
        List<Vec3> main = bolt(a, b, arc.jag, 5, random);
        out.add(new Strike(main, arc.width, arc.argb, alpha));
        for (int k = 0; k < arc.forks; k++) {
            // A fork leaves from somewhere in the middle of the bolt, angled off the bolt's line, and fizzles out.
            Vec3 start = main.get(main.size() / 5 + random.nextInt(Math.max(1, main.size() * 3 / 5)));
            double rest = start.distanceTo(b);
            Vec3 off = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            Vec3 end = start.add(b.subtract(start).normalize().add(off.scale(0.9)).normalize().scale(rest * (0.25 + 0.3 * random.nextFloat())));
            out.add(new Strike(bolt(start, end, arc.jag * 1.3f, 3, random), arc.width * 0.55f, arc.argb, alpha * 0.75f));
        }
    }

    /** A deeper, more saturated shade: alpha-blended over a bright sky it still shows as colour. */
    static int deepen(int argb) {
        return (int) ((argb >> 16 & 255) * 0.45f) << 16 | (int) ((argb >> 8 & 255) * 0.55f) << 8 | (argb & 255);
    }

    private record Strike(List<Vec3> points, float width, int argb, float alpha) {
    }

    /** Midpoint displacement: each pass splits every segment and pushes the midpoint sideways, half as far each pass. */
    static List<Vec3> bolt(Vec3 a, Vec3 b, float jag, int passes, RandomSource random) {
        Vec3 axis = b.subtract(a);
        double length = axis.length();
        List<Vec3> points = new ArrayList<>(List.of(a, b));
        if (length < 1e-4) return points;
        Vec3 dir = axis.scale(1 / length);
        Vec3 u = dir.cross(Math.abs(dir.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize(), w = dir.cross(u);
        double offset = length * jag;
        for (int pass = 0; pass < passes; pass++) {
            List<Vec3> next = new ArrayList<>(points.size() * 2);
            for (int i = 0; i + 1 < points.size(); i++) {
                Vec3 p = points.get(i), q = points.get(i + 1);
                next.add(p);
                next.add(p.add(q).scale(0.5).add(u.scale(offset * (random.nextFloat() * 2 - 1))).add(w.scale(offset * (random.nextFloat() * 2 - 1))));
            }
            next.add(points.get(points.size() - 1));
            points = next;
            offset *= 0.5;
        }
        return points;
    }

    /** One unbroken strip through the points, each joint's width turned to face the camera; tapers at both tips. */
    private static void strip(VertexConsumer buffer, Matrix4f pose, Vec3 cam, List<Vec3> points, float width, int argb, float alpha) {
        int n = points.size();
        Vec3[] sides = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 p = points.get(i);
            Vec3 tangent = points.get(Math.min(i + 1, n - 1)).subtract(points.get(Math.max(i - 1, 0)));
            Vec3 side = tangent.cross(cam.subtract(p));
            if (side.lengthSqr() < 1e-10) side = new Vec3(0, 1, 0);
            float taper = Math.min(1f, Math.min(i, n - 1 - i) / 2f + 0.35f);
            float widen = (float) Math.max(1, p.distanceTo(cam) / 16); // far bolts stay a few pixels wide
            sides[i] = side.normalize().scale(width * 0.5 * taper * widen);
        }
        for (int i = 0; i + 1 < n; i++) {
            Vec3 a = points.get(i).subtract(cam), b = points.get(i + 1).subtract(cam);
            vertex(buffer, pose, a.add(sides[i]), argb, alpha, 0);
            vertex(buffer, pose, a.subtract(sides[i]), argb, alpha, 1);
            vertex(buffer, pose, b.subtract(sides[i + 1]), argb, alpha, 1);
            vertex(buffer, pose, b.add(sides[i + 1]), argb, alpha, 0);
        }
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vec3 p, int argb, float alpha, float v) {
        buffer.vertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, alpha)
                .uv(0.5f, v).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    private static final class Arc {
        final Supplier<Vec3> from, to;
        final int argb, life, restrike, forks;
        final float width, jag;
        final long seed;
        int age;

        Arc(Supplier<Vec3> from, Supplier<Vec3> to, int argb, float width, int life, int restrike, int forks, float jag, long seed) {
            this.from = from;
            this.to = to;
            this.argb = argb;
            this.width = width;
            this.life = life;
            this.restrike = restrike;
            this.forks = forks;
            this.jag = jag;
            this.seed = seed;
        }
    }
}
