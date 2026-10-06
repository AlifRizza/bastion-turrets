package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import dev.bastion.client.vfx.VfxManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Visual bullets for hitscan shots (PLAN 5.3): a glowing stretched streak travels from the muzzle to the
 * server's hit point in a few ticks, trailing a short ribbon, then fires {@code onArrive} (the impact).
 */
public final class TracerRenderer {
    private static final ResourceLocation TEXTURE = Bastion.id("textures/vfx/tracer.png");
    private static final int MAX_ACTIVE = 512;
    private static final int FADE_TICKS = 3;
    private static final List<Tracer> ACTIVE = new ArrayList<>();

    private TracerRenderer() {
    }

    public static void spawn(Vec3 from, Vec3 to, int argb, float width, int durationTicks, @Nullable Runnable onArrive) {
        if (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.remove(0);
        ACTIVE.add(new Tracer(from, to, argb, width, Math.max(1, durationTicks), onArrive));
    }

    public static void tick() {
        for (Iterator<Tracer> it = ACTIVE.iterator(); it.hasNext(); ) {
            Tracer t = it.next();
            t.age++;
            t.trail.addFirst(t.head(Math.min(t.age, t.duration)));
            while (t.trail.size() > VfxManager.trailLength()) t.trail.removeLast();
            if (!t.arrived && t.age >= t.duration) {
                t.arrived = true;
                if (t.onArrive != null) t.onArrive.run();
            }
            if (t.age > t.duration + FADE_TICKS) it.remove();
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick) {
        if (ACTIVE.isEmpty()) return;
        VertexConsumer buffer = buffers.getBuffer(BastionRenderTypes.additiveGlow(TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        Vec3 cam = camera.getPosition();
        for (Tracer t : ACTIVE) {
            float time = t.age + partialTick;
            float alpha = time <= t.duration ? 1 : Mth.clamp(1 - (time - t.duration) / FADE_TICKS, 0, 1);
            Vec3 head = t.head(Math.min(time, t.duration));
            // Streak length ~ one tick of travel; never longer than the path already flown (no streak behind the gun).
            double length = Math.min(t.length / t.duration * 0.85, head.distanceTo(t.from));
            Vec3 tail = head.subtract(t.direction.scale(length));
            TrailRenderer.segment(buffer, pose, cam, tail, head, t.width * 2.4f, t.argb, alpha * 0.55f); // glow
            TrailRenderer.segment(buffer, pose, cam, tail, head, t.width * 0.6f, 0xFFFFFF, alpha);       // white-hot core
            if (t.trail.size() > 1) TrailRenderer.ribbon(buffer, pose, cam, List.copyOf(t.trail), t.width * 1.2f, t.argb, alpha * 0.35f);
        }
    }

    private static final class Tracer {
        final Vec3 from, to, direction;
        final double length;
        final int argb, duration;
        final float width;
        @Nullable
        final Runnable onArrive;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();
        int age;
        boolean arrived;

        Tracer(Vec3 from, Vec3 to, int argb, float width, int duration, @Nullable Runnable onArrive) {
            this.from = from;
            this.to = to;
            this.length = from.distanceTo(to);
            this.direction = length < 1e-6 ? new Vec3(0, 0, 1) : to.subtract(from).scale(1 / length);
            this.argb = argb;
            this.width = width;
            this.duration = duration;
            this.onArrive = onArrive;
        }

        Vec3 head(float time) {
            return from.lerp(to, time / duration);
        }
    }
}
