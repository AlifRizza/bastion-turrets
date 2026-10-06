package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Ejected casings and shells (PLAN 5.3): tiny modelled boxes that fly out with spin, fall, bounce once,
 * glow with the energy colour and fade. At most {@link #MAX_PER_TURRET} per turret; oldest dropped first.
 */
public final class CasingRenderer {
    public static final int MAX_PER_TURRET = 12;
    private static final int LIFETIME = 40;
    private static final ResourceLocation TEXTURE = Bastion.id("textures/vfx/casing.png");
    private static final Map<Long, Deque<Casing>> BY_TURRET = new HashMap<>();

    private CasingRenderer() {
    }

    /**
     * @param turret owner key (turret block pos), for the per-turret cap
     * @param shell  true for the Shotgun's big shell, false for a small casing
     */
    public static void eject(BlockPos turret, Vec3 at, Vec3 velocity, int argb, boolean shell, long seed) {
        Deque<Casing> list = BY_TURRET.computeIfAbsent(turret.asLong(), k -> new ArrayDeque<>());
        if (list.size() >= MAX_PER_TURRET) list.pollFirst();
        RandomSource random = RandomSource.create(seed);
        Vector3f spinAxis = new Vector3f(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).normalize();
        list.addLast(new Casing(at, velocity, argb, shell ? 0.05f : 0.028f, shell ? 0.11f : 0.07f, spinAxis, 0.6f + random.nextFloat() * 0.6f));
    }

    public static void tick(ClientLevel level) {
        BY_TURRET.values().forEach(list -> list.removeIf(c -> c.tick(level)));
        BY_TURRET.values().removeIf(Deque::isEmpty);
    }

    public static void clear() {
        BY_TURRET.clear();
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick) {
        if (BY_TURRET.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        ClientLevel level = (ClientLevel) camera.getEntity().level();
        VertexConsumer solid = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        for (Deque<Casing> list : BY_TURRET.values()) {
            for (Casing c : list) {
                float alpha = Mth.clamp((LIFETIME - c.age - partialTick) / 10f, 0, 1);
                int light = LevelRenderer.getLightColor(level, BlockPos.containing(c.pos));
                c.box(solid, poseStack, cam, partialTick, 0.8f, 0.82f, 0.86f, alpha, light, true);
            }
        }
        VertexConsumer glow = buffers.getBuffer(BastionRenderTypes.additiveGlow(TEXTURE));
        for (Deque<Casing> list : BY_TURRET.values()) {
            for (Casing c : list) {
                float heat = Mth.clamp(1 - (c.age + partialTick) / 15f, 0, 1); // glows, then cools
                if (heat <= 0) continue;
                int a = c.argb;
                c.box(glow, poseStack, cam, partialTick, (a >> 16 & 255) / 255f, (a >> 8 & 255) / 255f, (a & 255) / 255f, heat, LightTexture.FULL_BRIGHT, false);
            }
        }
    }

    private static final class Casing {
        Vec3 pos, prevPos, velocity;
        final int argb;
        final float radius, halfLength, spin;
        final Vector3f spinAxis;
        float angle, prevAngle;
        int age, bounces;

        Casing(Vec3 pos, Vec3 velocity, int argb, float radius, float halfLength, Vector3f spinAxis, float spin) {
            this.pos = this.prevPos = pos;
            this.velocity = velocity;
            this.argb = argb;
            this.radius = radius;
            this.halfLength = halfLength;
            this.spinAxis = spinAxis;
            this.spin = spin;
        }

        /** @return true when expired */
        boolean tick(ClientLevel level) {
            prevPos = pos;
            prevAngle = angle;
            if (bounces < 2) {
                velocity = velocity.add(0, -0.04, 0).scale(0.98);
                Vec3 next = pos.add(velocity);
                BlockPos below = BlockPos.containing(next);
                if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                    bounces++;
                    velocity = bounces == 1 ? new Vec3(velocity.x * 0.5, -velocity.y * 0.35, velocity.z * 0.5) : Vec3.ZERO;
                    next = new Vec3(next.x, Math.floor(next.y) + 1 + radius, next.z);
                }
                pos = next;
                angle += spin * (bounces == 0 ? 1 : 0.3f);
            }
            return ++age >= LIFETIME;
        }

        void box(VertexConsumer buffer, PoseStack poseStack, Vec3 cam, float partialTick, float r, float g, float b, float a,
                 int light, boolean lit) {
            Vec3 p = prevPos.lerp(pos, partialTick).subtract(cam);
            poseStack.pushPose();
            poseStack.translate(p.x, p.y, p.z);
            poseStack.mulPose(new Quaternionf().fromAxisAngleRad(spinAxis, Mth.lerp(partialTick, prevAngle, angle)));
            Matrix4f pose = poseStack.last().pose();
            Matrix3f normal = poseStack.last().normal();
            float x = radius, y = radius, z = halfLength;
            float[][] faces = { // 6 faces x 4 corners (x,y,z) and face normal
                    {-x, -y, -z, -x, y, -z, x, y, -z, x, -y, -z, 0, 0, -1}, {x, -y, z, x, y, z, -x, y, z, -x, -y, z, 0, 0, 1},
                    {-x, -y, z, -x, y, z, -x, y, -z, -x, -y, -z, -1, 0, 0}, {x, -y, -z, x, y, -z, x, y, z, x, -y, z, 1, 0, 0},
                    {-x, y, -z, -x, y, z, x, y, z, x, y, -z, 0, 1, 0}, {-x, -y, z, -x, -y, -z, x, -y, -z, x, -y, z, 0, -1, 0}};
            float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
            for (float[] f : faces) {
                for (int i = 0; i < 4; i++) {
                    buffer.vertex(pose, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(r, g, b, a).uv(uv[i][0], uv[i][1]);
                    if (lit) buffer.overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, f[12], f[13], f[14]).endVertex();
                    else buffer.uv2(light).endVertex();
                }
            }
            poseStack.popPose();
        }
    }
}
