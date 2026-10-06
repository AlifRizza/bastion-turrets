package dev.bastion.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import dev.bastion.client.render.BastionRenderTypes;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Impact marks (PLAN 5.3): a scorch lies on the hit face, glowing in the energy colour, then cooling to
 * dark and fading within a few seconds. At most {@link #MAX_ACTIVE} at once; the oldest go first.
 */
public final class DecalRenderer {
    public static final int MAX_ACTIVE = 256;
    private static final ResourceLocation TEXTURE = Bastion.id("textures/vfx/decal.png");
    /** Rocket blasts: a big burn with streaks thrown outward. */
    static final ResourceLocation SCORCH = Bastion.id("textures/vfx/scorch.png");
    private static final Deque<Decal> ACTIVE = new ArrayDeque<>();

    private DecalRenderer() {
    }

    static void add(Vec3 at, Vec3 normal, int argb, float size, int lifetime) {
        add(at, normal, argb, size, lifetime, TEXTURE);
    }

    static void add(Vec3 at, Vec3 normal, int argb, float size, int lifetime, ResourceLocation texture) {
        if (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.pollFirst();
        Vec3 n = normal.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : normal.normalize();
        // Attached to the block behind the hit point, so the mark disappears when that block does.
        BlockPos block = BlockPos.containing(at.subtract(n.scale(0.05)));
        ACTIVE.addLast(new Decal(at.add(n.scale(0.01)), n, block, argb, size, Math.max(20, lifetime),
                (float) (Math.random() * Math.PI * 2), texture));
    }

    /**
     * A mark that keeps being hit in the same place (the Flamethrower's stream on a wall): a hit landing on an existing
     * mark of this texture on the same face renews it (hot again, full time left) and widens it by {@code grow}, up to
     * {@code maxSize}, instead of stacking another one on top. Elsewhere it starts a new mark of {@code size}.
     */
    static void addOrGrow(Vec3 at, Vec3 normal, int argb, float size, int lifetime, ResourceLocation texture, float grow, float maxSize) {
        Vec3 n = normal.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : normal.normalize();
        for (Decal d : ACTIVE) {
            if (d.texture != texture || d.normal.dot(n) < 0.95) continue;
            Vec3 offset = at.subtract(d.pos);
            if (Math.abs(offset.dot(n)) > 0.1 || offset.lengthSqr() > d.size * d.size) continue; // another face, or off this mark
            d.age = 0;
            d.size = Math.min(maxSize, d.size + grow);
            return;
        }
        add(at, n, argb, size, lifetime, texture);
    }

    static void tick(ClientLevel level) {
        ACTIVE.removeIf(d -> ++d.age > d.lifetime || level.getBlockState(d.block).isAir());
    }

    static void clear() {
        ACTIVE.clear();
    }

    static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick) {
        if (ACTIVE.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();
        ClientLevel level = (ClientLevel) camera.getEntity().level();

        for (ResourceLocation texture : new ResourceLocation[]{TEXTURE, SCORCH}) renderLayer(texture, buffers, pose, normalMatrix, cam, partialTick, level);
    }

    private static void renderLayer(ResourceLocation texture, MultiBufferSource buffers, Matrix4f pose, Matrix3f normalMatrix, Vec3 cam,
                                    float partialTick, ClientLevel level) {
        VertexConsumer scorch = buffers.getBuffer(RenderType.entityTranslucent(texture));
        for (Decal d : ACTIVE) {
            if (d.texture != texture) continue;
            float t = (d.age + partialTick) / d.lifetime;
            float fade = Mth.clamp((1 - t) / 0.3f, 0, 1);
            d.quad(scorch, pose, normalMatrix, cam, 0.13f, 0.11f, 0.1f, 0.85f * fade, LevelRenderer.getLightColor(level, d.block.relative(
                    net.minecraft.core.Direction.getNearest(d.normal.x, d.normal.y, d.normal.z))));
        }
        VertexConsumer glow = buffers.getBuffer(BastionRenderTypes.additiveGlow(texture));
        for (Decal d : ACTIVE) {
            if (d.texture != texture) continue;
            float t = (d.age + partialTick) / d.lifetime;
            float heat = Mth.clamp(1 - t / 0.4f, 0, 1); // glowing while hot, cools in the first 40%
            if (heat <= 0) continue;
            int c = d.argb;
            d.quad(glow, pose, null, cam, (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, heat * heat, LightTexture.FULL_BRIGHT);
        }
    }

    private static final class Decal {
        final Vec3 pos, normal;
        final BlockPos block;
        final int argb, lifetime;
        final float rotation;
        float size;
        final ResourceLocation texture;
        int age;

        Decal(Vec3 pos, Vec3 normal, BlockPos block, int argb, float size, int lifetime, float rotation, ResourceLocation texture) {
            this.texture = texture;
            this.pos = pos;
            this.normal = normal;
            this.block = block;
            this.argb = argb;
            this.size = size;
            this.lifetime = lifetime;
            this.rotation = rotation;
        }

        /** {@code normalMatrix == null} writes the additive (position-colour-uv-light) format. */
        void quad(VertexConsumer buffer, Matrix4f pose, Matrix3f normalMatrix, Vec3 cam, float r, float g, float b, float a, int light) {
            Vec3 helper = Math.abs(normal.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 u = normal.cross(helper).normalize(), w = normal.cross(u);
            double cos = Math.cos(rotation), sin = Math.sin(rotation);
            Vec3 ur = u.scale(cos).add(w.scale(sin)).scale(size), wr = w.scale(cos).subtract(u.scale(sin)).scale(size);
            Vec3 c = pos.subtract(cam);
            Vec3[] corners = {c.subtract(ur).subtract(wr), c.subtract(ur).add(wr), c.add(ur).add(wr), c.add(ur).subtract(wr)};
            float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
            for (int i = 0; i < 4; i++) {
                Vec3 p = corners[i];
                if (normalMatrix == null) {
                    buffer.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).uv(uv[i][0], uv[i][1]).uv2(light).endVertex();
                } else {
                    buffer.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).uv(uv[i][0], uv[i][1])
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                            .normal(normalMatrix, (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
                }
            }
        }
    }
}
