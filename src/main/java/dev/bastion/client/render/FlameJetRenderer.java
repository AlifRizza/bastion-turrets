package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import dev.bastion.client.ClientTurret;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.WeaponData;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Flamethrower's jet: while a turret fires, a continuous tongue of fire leaves the nozzle (muzzle_0) along the
 * barrel (from muzzle_1), widening and wavering as it goes, its fire texture streaming outward. It keeps the first
 * blocks of the stream solid where particles alone leave gaps; FLAME_STREAM particles give the volume further out.
 * Three layers: an orange body and a yellow inner flame (alpha-blended, so they hold up in daylight) and a hot core.
 */
public final class FlameJetRenderer {
    private static final ResourceLocation TEXTURE = Bastion.id("textures/vfx/flame_jet.png");
    private static final int SEGMENTS = 12;
    /** Jet length as a fraction of the weapon's range. */
    private static final float REACH = 0.55f;

    private FlameJetRenderer() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick, ClientLevel level) {
        float time = level.getGameTime() + partialTick;
        List<Jet> jets = new ArrayList<>();
        for (ClientTurret client : ClientTurret.all()) {
            if (client.muzzles[0] == null || client.muzzles[1] == null) continue;
            if (!(level.getBlockEntity(client.pos) instanceof TurretBaseBlockEntity turret)) continue;
            WeaponData data = turret.inventory().weaponData();
            if (data == null || !data.type().equals(BastionWeaponTypes.FLAMETHROWER.getId())) continue;
            // Full while pulses keep coming (every 2 ticks), grows over the first pulses, dies out quickly after the last.
            float on = Mth.clamp(1 - (time - client.lastShotTick - 2) / 3f, 0, 1) * Math.min(1f, client.streak / 3f);
            if (on <= 0) continue;
            Vec3 nozzle = client.muzzles[0], dir = nozzle.subtract(client.muzzles[1]);
            if (dir.lengthSqr() < 1e-6) continue;
            jets.add(new Jet(nozzle, dir.normalize(), data.range() * REACH * on, on, client.pos.asLong()));
        }
        if (jets.isEmpty()) return;
        Matrix4f pose = poseStack.last().pose();
        Vec3 cam = camera.getPosition();
        VertexConsumer fire = buffers.getBuffer(BastionRenderTypes.translucentGlow(TEXTURE));
        for (Jet jet : jets) {
            layer(fire, pose, cam, jet, time, jet.length, 0.3f, 1.8f, 0xFF7A1E, 0.8f, 0.30f, 0);
            layer(fire, pose, cam, jet, time, jet.length * 0.8f, 0.2f, 1.05f, 0xFFC266, 0.85f, 0.42f, 0.37f);
        }
        VertexConsumer core = buffers.getBuffer(BastionRenderTypes.additiveGlow(TEXTURE));
        for (Jet jet : jets) layer(core, pose, cam, jet, time, jet.length * 0.55f, 0.13f, 0.5f, 0xFFF2C8, 0.7f, 0.55f, 0.71f);
    }

    /**
     * One camera-facing strip along the jet, {@code w0} wide at the nozzle to {@code w1} at its end, fading in over the
     * first tenth and out over the last 40%; the texture streams outward at {@code flow} repeats per tick.
     */
    private static void layer(VertexConsumer buffer, Matrix4f pose, Vec3 cam, Jet jet, float time, float length, float w0, float w1,
                              int rgb, float alpha, float flow, float phase) {
        Vec3 helper = Math.abs(jet.dir.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = jet.dir.cross(helper).normalize(), w = jet.dir.cross(u);
        float wobbleSeed = (jet.seed & 255) * 0.1f + phase;
        Vec3[] points = new Vec3[SEGMENTS + 1];
        float[] widths = new float[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            float f = (float) i / SEGMENTS;
            // The flame wavers more the further it gets from the nozzle.
            float wob = f * f * 0.18f;
            points[i] = jet.nozzle.add(jet.dir.scale(length * f))
                    .add(u.scale(wob * Mth.sin(time * 0.9f + f * 7 + wobbleSeed)))
                    .add(w.scale(wob * Mth.sin(time * 1.1f + f * 5 + wobbleSeed * 2)));
            widths[i] = Mth.lerp((float) Math.sqrt(f), w0, w1) * (0.9f + 0.1f * Mth.sin(time * 2.3f + f * 11 + phase));
        }
        for (int i = 0; i < SEGMENTS; i++) {
            float f0 = (float) i / SEGMENTS, f1 = (float) (i + 1) / SEGMENTS;
            Vec3 s0 = side(points, i, cam, widths[i]), s1 = side(points, i + 1, cam, widths[i + 1]);
            float a0 = alpha * jet.on * fade(f0), a1 = alpha * jet.on * fade(f1);
            float tex0 = f0 * length * 0.6f - time * flow + phase, tex1 = f1 * length * 0.6f - time * flow + phase;
            Vec3 p0 = points[i].subtract(cam), p1 = points[i + 1].subtract(cam);
            vertex(buffer, pose, p0.add(s0), rgb, a0, tex0, 0);
            vertex(buffer, pose, p0.subtract(s0), rgb, a0, tex0, 1);
            vertex(buffer, pose, p1.subtract(s1), rgb, a1, tex1, 1);
            vertex(buffer, pose, p1.add(s1), rgb, a1, tex1, 0);
        }
    }

    private static float fade(float f) {
        return Math.min(1, f / 0.1f + 0.35f) * (f < 0.6f ? 1 : 1 - (f - 0.6f) / 0.4f);
    }

    private static Vec3 side(Vec3[] points, int i, Vec3 cam, float width) {
        Vec3 tangent = points[Math.min(i + 1, points.length - 1)].subtract(points[Math.max(i - 1, 0)]);
        Vec3 side = tangent.cross(cam.subtract(points[i]));
        return side.lengthSqr() < 1e-10 ? new Vec3(0, width * 0.5, 0) : side.normalize().scale(width * 0.5);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vec3 p, int rgb, float alpha, float u, float v) {
        buffer.vertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, alpha)
                .uv(u, v).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    private record Jet(Vec3 nozzle, Vec3 dir, float length, float on, long seed) {
    }
}
