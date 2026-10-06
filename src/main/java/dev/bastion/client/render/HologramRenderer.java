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
import org.joml.Quaternionf;

/**
 * Holograms (PLAN 5.3, 7.5): additive camera-facing quads with a thin scrolling scanline overlay and a
 * subtle flicker. Used for the lock-on reticle, ammo readout and status ring.
 */
public final class HologramRenderer {
    public static final ResourceLocation RETICLE = Bastion.id("textures/vfx/reticle.png");
    private static final ResourceLocation SCANLINES = Bastion.id("textures/vfx/scanlines.png");

    private HologramRenderer() {
    }

    /**
     * @param poseStack world pose with only the camera rotation applied (positions are camera-relative)
     * @param roll      spin around the view axis, radians
     * @param time      client ticks + partial tick, drives flicker and scanline scroll
     */
    public static void billboard(PoseStack poseStack, MultiBufferSource buffers, Camera camera, ResourceLocation texture,
                                 Vec3 at, float size, float roll, int argb, float alpha, float time) {
        Vec3 rel = at.subtract(camera.getPosition());
        poseStack.pushPose();
        poseStack.translate(rel.x, rel.y, rel.z);
        poseStack.mulPose(camera.rotation());
        poseStack.mulPose(new Quaternionf().rotateZ(roll));
        Matrix4f pose = poseStack.last().pose();
        float flicker = 0.88f + 0.12f * Mth.sin(time * 1.9f) * Mth.sin(time * 0.37f + 1.3f);
        quad(buffers.getBuffer(BastionRenderTypes.additiveGlow(texture)), pose, size, argb, alpha * flicker, 0, 1);
        float scroll = (time * 0.04f) % 1f;
        quad(buffers.getBuffer(BastionRenderTypes.additiveGlow(SCANLINES)), pose, size, argb, alpha * 0.25f * flicker, scroll, scroll + 2);
        poseStack.popPose();
    }

    private static void quad(VertexConsumer buffer, Matrix4f pose, float size, int argb, float alpha, float v0, float v1) {
        float r = (argb >> 16 & 255) / 255f, g = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f, s = size / 2;
        buffer.vertex(pose, -s, -s, 0).color(r, g, b, alpha).uv(0, v1).uv2(LightTexture.FULL_BRIGHT).endVertex();
        buffer.vertex(pose, s, -s, 0).color(r, g, b, alpha).uv(1, v1).uv2(LightTexture.FULL_BRIGHT).endVertex();
        buffer.vertex(pose, s, s, 0).color(r, g, b, alpha).uv(1, v0).uv2(LightTexture.FULL_BRIGHT).endVertex();
        buffer.vertex(pose, -s, s, 0).color(r, g, b, alpha).uv(0, v0).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }
}
