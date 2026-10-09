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
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Visual bullets (PLAN 5.3): a glowing stretched streak (or a plasma ball) that travels at a fixed speed, trailing a
 * short ribbon, then fires {@code onArrive} (the impact). Turret bullets (dev.bastion.weapon.Bullets) fly their whole
 * path until the server's impact for their id cuts them short ({@link #land}).
 */
public final class TracerRenderer {
    private static final ResourceLocation TEXTURE = Bastion.id("textures/vfx/tracer.png"), ORB = Bastion.id("textures/vfx/orb.png");
    private static final int MAX_ACTIVE = 512;
    private static final int FADE_TICKS = 3;
    private static final List<Tracer> ACTIVE = new ArrayList<>();
    private static final Map<Integer, Tracer> BULLETS = new HashMap<>();

    /** Where a bullet ended, from the server: plays its impact. */
    @FunctionalInterface
    public interface Landing {
        void land(Vec3 end, Vec3 normal, int hit, int blockState);

        /** The end is known (the impact packet came), before the tracer arrives: visuals that must not overshoot it trim now. */
        default void known(Vec3 end) {
        }
    }

    private TracerRenderer() {
    }

    /** A streak from {@code from} to {@code to} taking {@code durationTicks}. */
    public static void spawn(Vec3 from, Vec3 to, int argb, float width, int durationTicks, @Nullable Runnable onArrive) {
        add(new Tracer(-1, from, to, argb, width, from.distanceTo(to) / Math.max(1, durationTicks), onArrive, null, true, 0, -1));
    }

    /** Bullet {@code id} flying {@code speed} blocks per tick along its whole path to {@code end}. */
    public static void spawnBullet(int id, Vec3 from, Vec3 end, double speed, int argb, float width, Landing landing) {
        track(new Tracer(id, from, end, argb, width, speed, null, landing, true, 0, -1));
    }

    /**
     * An energy bolt (Ion Rifle): a thick streak whose ribbon trails {@code trailBlocks} behind a glowing ball of
     * {@code radius} at its head, so it reads nothing like a bullet's thin tracer.
     */
    public static void spawnBolt(int id, Vec3 from, Vec3 end, double speed, int argb, float width, float radius, float trailBlocks,
                                 Landing landing) {
        track(new Tracer(id, from, end, argb, width, speed, null, landing, true, radius, points(trailBlocks, speed)));
    }

    /** A glowing ball of {@code radius} instead of a streak (Plasma Repeater, Plasma Cannon and its fragments). */
    public static void spawnOrb(int id, Vec3 from, Vec3 end, double speed, int argb, float radius, float trailBlocks, Landing landing) {
        track(new Tracer(id, from, end, argb, radius, speed, null, landing, false, radius, points(trailBlocks, speed)));
    }

    /**
     * Runs {@code task} when bullet {@code id}'s tracer lands (the Ion Rifle's beams), or now when
     * it has no tracer (dropped over the cap, never spawned). The server sends these before the bullet's own impact.
     */
    public static void onLanding(int id, Runnable task) {
        Tracer tracer = BULLETS.get(id);
        if (tracer == null) task.run();
        else tracer.then.add(task);
    }

    /** The server's impact for bullet {@code id}: its tracer stops there and plays the impact on arrival. */
    public static void land(int id, Vec3 end, Vec3 normal, int hit, int blockState) {
        Tracer tracer = BULLETS.remove(id);
        if (tracer == null) return; // already gone (dropped over the cap)
        tracer.setEnd(end);
        tracer.landing.known(end);
        tracer.onArrive = () -> {
            tracer.landing.land(end, normal, hit, blockState);
            tracer.then.forEach(Runnable::run);
        };
    }

    private static int points(float blocks, double speed) {
        return Math.max(2, (int) Math.ceil(blocks / Math.max(speed, 1e-3)));
    }

    private static void track(Tracer tracer) {
        add(tracer);
        BULLETS.put(tracer.id, tracer);
    }

    private static void add(Tracer tracer) {
        if (ACTIVE.size() >= MAX_ACTIVE) {
            Tracer old = ACTIVE.remove(0);
            remove(old);
            if (!old.arrived) old.then.forEach(Runnable::run); // a dropped ion bolt still shows its beams
        }
        ACTIVE.add(tracer);
    }

    private static void remove(Tracer tracer) {
        if (tracer.id >= 0) BULLETS.remove(tracer.id, tracer);
    }

    public static void tick() {
        List<Runnable> arrivals = new ArrayList<>();
        for (Iterator<Tracer> it = ACTIVE.iterator(); it.hasNext(); ) {
            Tracer t = it.next();
            t.age++;
            t.trail.addFirst(t.head(t.age));
            int max = t.trailPoints > 0 ? t.trailPoints : VfxManager.trailLength();
            while (t.trail.size() > max) t.trail.removeLast();
            if (!t.arrived && t.age >= t.arrival()) {
                t.arrived = true;
                if (t.onArrive != null) arrivals.add(t.onArrive);
            }
            if (t.arrived && t.age > t.arrival() + FADE_TICKS) {
                it.remove();
                remove(t);
            }
        }
        // After the loop: an impact may spawn tracers of its own.
        arrivals.forEach(Runnable::run);
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
        boolean orbs = false;
        for (Tracer t : ACTIVE) {
            float alpha = alpha(t, partialTick);
            Vec3 head = t.head(t.age + partialTick);
            if (t.orb > 0) orbs = true;
            if (t.streak) {
                // Streak length ~ one tick of travel; never longer than the path already flown (no streak behind the gun).
                double length = Math.min(t.speed * 0.85, head.distanceTo(t.from));
                Vec3 tail = head.subtract(t.direction.scale(length));
                TrailRenderer.segment(buffer, pose, cam, tail, head, t.width * 2.4f, t.argb, alpha * 0.55f); // glow
                TrailRenderer.segment(buffer, pose, cam, tail, head, t.width * 0.6f, 0xFFFFFF, alpha);       // white-hot core
            }
            if (t.trail.size() > 1) TrailRenderer.ribbon(buffer, pose, cam, List.copyOf(t.trail), t.width * 1.2f, t.argb, alpha * 0.35f);
        }
        if (!orbs) return;
        // Energy bolts (Ion Rifle): a deep halo round the streak and its ribbon, alpha-blended so it reads against a bright sky.
        VertexConsumer deep = buffers.getBuffer(BastionRenderTypes.translucentGlow(TEXTURE));
        for (Tracer t : ACTIVE) {
            if (!t.streak || t.orb <= 0) continue;
            float alpha = alpha(t, partialTick);
            Vec3 head = t.head(t.age + partialTick);
            double length = Math.min(t.speed * 0.85, head.distanceTo(t.from));
            int shade = ArcRenderer.deepen(t.argb);
            TrailRenderer.segment(deep, pose, cam, head.subtract(t.direction.scale(length)), head, t.width * 3.2f, shade, alpha * 0.45f);
            if (t.trail.size() > 1) TrailRenderer.ribbon(deep, pose, cam, List.copyOf(t.trail), t.width * 1.8f, shade, alpha * 0.3f);
        }
        // Plasma balls: a deep halo (reads by day), a coloured glow and a white core, breathing a little.
        Quaternionf facing = camera.rotation();
        VertexConsumer halo = buffers.getBuffer(BastionRenderTypes.translucentGlow(ORB));
        for (Tracer t : ACTIVE) {
            if (t.orb <= 0) continue;
            float r = pulse(t, partialTick);
            LaserChargeRenderer.billboard(halo, pose, cam, facing, t.head(t.age + partialTick), r * 2.4f, r * 2.4f,
                    ArcRenderer.deepen(t.argb), 0.45f * alpha(t, partialTick));
        }
        VertexConsumer glow = buffers.getBuffer(BastionRenderTypes.additiveGlow(ORB));
        for (Tracer t : ACTIVE) {
            if (t.orb <= 0) continue;
            float r = pulse(t, partialTick), a = alpha(t, partialTick);
            Vec3 head = t.head(t.age + partialTick);
            LaserChargeRenderer.billboard(glow, pose, cam, facing, head, r * 1.5f, r * 1.5f, t.argb, a);
            LaserChargeRenderer.billboard(glow, pose, cam, facing, head, r * 0.65f, r * 0.65f, 0xFFFFFF, a);
        }
    }

    private static float alpha(Tracer t, float partialTick) {
        float time = t.age + partialTick, arrival = t.arrival();
        return time <= arrival ? 1 : Mth.clamp(1 - (time - arrival) / FADE_TICKS, 0, 1);
    }

    /** A plasma ball's radius, breathing ~8 %. */
    private static float pulse(Tracer t, float partialTick) {
        return t.orb * (1 + 0.08f * Mth.sin((t.age + partialTick) * 0.9f));
    }

    private static final class Tracer {
        final int id;
        final Vec3 from;
        final double speed;
        final int argb;
        final float width;
        /** Draws the stretched streak (bullets, ion bolts); plasma balls have only the ball and the ribbon. */
        final boolean streak;
        /** Radius of the glowing ball at the head (plasma, ion bolt); 0 = none. */
        final float orb;
        /** Ribbon length in ticks of travel; 0 or less = the quality preset's. */
        final int trailPoints;
        @Nullable
        final Landing landing;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();
        /** Tasks chained to the landing (TracerRenderer#onLanding). */
        final List<Runnable> then = new ArrayList<>();
        Vec3 direction;
        double length;
        @Nullable
        Runnable onArrive;
        int age;
        boolean arrived;

        Tracer(int id, Vec3 from, Vec3 to, int argb, float width, double speed, @Nullable Runnable onArrive, @Nullable Landing landing,
               boolean streak, float orb, int trailPoints) {
            this.id = id;
            this.from = from;
            this.argb = argb;
            this.width = width;
            this.speed = Math.max(speed, 1e-3);
            this.onArrive = onArrive;
            this.landing = landing;
            this.streak = streak;
            this.orb = orb;
            this.trailPoints = trailPoints;
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
