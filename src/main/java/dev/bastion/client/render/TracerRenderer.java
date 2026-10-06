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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Visual bullets (PLAN 5.3): a glowing stretched streak that travels at a fixed speed, trailing a short ribbon, then
 * fires {@code onArrive} (the impact). Turret bullets (dev.bastion.weapon.Bullets) fly their whole path until the
 * server's impact for their id cuts them short ({@link #land}).
 */
public final class TracerRenderer {
    private static final ResourceLocation TEXTURE = Bastion.id("textures/vfx/tracer.png");
    private static final int MAX_ACTIVE = 512;
    private static final int FADE_TICKS = 3;
    private static final List<Tracer> ACTIVE = new ArrayList<>();
    private static final Map<Integer, Tracer> BULLETS = new HashMap<>();

    /** Where a bullet ended, from the server: plays its impact. */
    @FunctionalInterface
    public interface Landing {
        void land(Vec3 end, Vec3 normal, int hit, int blockState);
    }

    private TracerRenderer() {
    }

    /** A streak from {@code from} to {@code to} taking {@code durationTicks}. */
    public static void spawn(Vec3 from, Vec3 to, int argb, float width, int durationTicks, @Nullable Runnable onArrive) {
        add(new Tracer(-1, from, to, argb, width, from.distanceTo(to) / Math.max(1, durationTicks), onArrive, null));
    }

    /** Bullet {@code id} flying {@code speed} blocks per tick along its whole path to {@code end}. */
    public static void spawnBullet(int id, Vec3 from, Vec3 end, double speed, int argb, float width, Landing landing) {
        Tracer tracer = new Tracer(id, from, end, argb, width, speed, null, landing);
        add(tracer);
        BULLETS.put(id, tracer);
    }

    /** The server's impact for bullet {@code id}: its tracer stops there and plays the impact on arrival. */
    public static void land(int id, Vec3 end, Vec3 normal, int hit, int blockState) {
        Tracer tracer = BULLETS.remove(id);
        if (tracer == null) return; // already gone (dropped over the cap)
        tracer.setEnd(end);
        tracer.onArrive = () -> tracer.landing.land(end, normal, hit, blockState);
    }

    private static void add(Tracer tracer) {
        if (ACTIVE.size() >= MAX_ACTIVE) remove(ACTIVE.remove(0));
        ACTIVE.add(tracer);
    }

    private static void remove(Tracer tracer) {
        if (tracer.id >= 0) BULLETS.remove(tracer.id, tracer);
    }

    public static void tick() {
        for (Iterator<Tracer> it = ACTIVE.iterator(); it.hasNext(); ) {
            Tracer t = it.next();
            t.age++;
            t.trail.addFirst(t.head(t.age));
            while (t.trail.size() > VfxManager.trailLength()) t.trail.removeLast();
            if (!t.arrived && t.age >= t.arrival()) {
                t.arrived = true;
                if (t.onArrive != null) t.onArrive.run();
            }
            if (t.arrived && t.age > t.arrival() + FADE_TICKS) {
                it.remove();
                remove(t);
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
        BULLETS.clear();
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick) {
        if (ACTIVE.isEmpty()) return;
        VertexConsumer buffer = buffers.getBuffer(BastionRenderTypes.additiveGlow(TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        Vec3 cam = camera.getPosition();
        for (Tracer t : ACTIVE) {
            float time = t.age + partialTick;
            float arrival = t.arrival();
            float alpha = time <= arrival ? 1 : Mth.clamp(1 - (time - arrival) / FADE_TICKS, 0, 1);
            Vec3 head = t.head(time);
            // Streak length ~ one tick of travel; never longer than the path already flown (no streak behind the gun).
            double length = Math.min(t.speed * 0.85, head.distanceTo(t.from));
            Vec3 tail = head.subtract(t.direction.scale(length));
            TrailRenderer.segment(buffer, pose, cam, tail, head, t.width * 2.4f, t.argb, alpha * 0.55f); // glow
            TrailRenderer.segment(buffer, pose, cam, tail, head, t.width * 0.6f, 0xFFFFFF, alpha);       // white-hot core
            if (t.trail.size() > 1) TrailRenderer.ribbon(buffer, pose, cam, List.copyOf(t.trail), t.width * 1.2f, t.argb, alpha * 0.35f);
        }
    }

    private static final class Tracer {
        final int id;
        final Vec3 from;
        final double speed;
        final int argb;
        final float width;
        @Nullable
        final Landing landing;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();
        Vec3 direction;
        double length;
        @Nullable
        Runnable onArrive;
        int age;
        boolean arrived;

        Tracer(int id, Vec3 from, Vec3 to, int argb, float width, double speed, @Nullable Runnable onArrive, @Nullable Landing landing) {
            this.id = id;
            this.from = from;
            this.argb = argb;
            this.width = width;
            this.speed = Math.max(speed, 1e-3);
            this.onArrive = onArrive;
            this.landing = landing;
            setEnd(to);
        }

        void setEnd(Vec3 to) {
            length = from.distanceTo(to);
            direction = length < 1e-6 ? new Vec3(0, 0, 1) : to.subtract(from).scale(1 / length);
        }

        /** Ticks until the head reaches the end. */
        float arrival() {
            return (float) Math.max(1, length / speed);
        }

        Vec3 head(float time) {
            return from.add(direction.scale(Math.min(time * speed, length)));
        }
    }
}
