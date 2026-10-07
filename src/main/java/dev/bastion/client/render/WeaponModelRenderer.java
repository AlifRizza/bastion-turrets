package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.client.anim.WeaponAnimatable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/** A mounted weapon in the world (PLAN 6.1), drawn by WeaponMountLayer at the base's weapon_mount bone. */
public class WeaponModelRenderer extends GeoObjectRenderer<WeaponAnimatable> {
    private static final int OVERHEAT_RED = 0xFF3A1E;

    public WeaponModelRenderer() {
        super(new WeaponModel<>());
        addRenderLayer(new EmissiveLayer<>(this, WeaponModelRenderer::glowColor));
    }

    /**
     * The shared model glow, shifting to red above half heat (PLAN 7.2 barrels glow, 7.5 overheat red) and to the energy
     * colour while a Railgun charges.
     */
    static int glowColor(WeaponAnimatable weapon) {
        int base = lerp(EmissiveLayer.MODEL_GLOW, OVERHEAT_RED, Mth.clamp((weapon.heat - 0.5f) / 0.5f, 0, 1));
        return lerp(base, weapon.chargeColor, weapon.chargeGlow);
    }

    private static int lerp(int a, int b, float t) {
        if (t <= 0) return a;
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
        int g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    /** The caller already placed the origin at the mount, so skip GeoObjectRenderer's block-centre offset. */
    @Override
    public void preRender(PoseStack poseStack, WeaponAnimatable animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        objectRenderTranslations = new Matrix4f(poseStack.last().pose());
    }

    /** Glow pass: only the faces with glow pixels (GlowCubes). */
    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        GlowCubes.renderCubesOfBone(this, poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
