package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.function.ToIntFunction;

/**
 * Re-renders the model with its {@code _e} texture at full brightness, tinted with the energy colour
 * and breathing slowly while idle (PLAN 5.1, 7.5), through BastionRenderTypes.translucentEmissive.
 */
public class EmissiveLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    /**
     * One glow for every turret model (user decision 2026-10-06: no per-weapon colours on the models). Weapon
     * identity stays in the VFX, which keep each weapon's energy colour.
     */
    public static final int MODEL_GLOW = 0x4FD8FF;

    private final ToIntFunction<T> color;

    public EmissiveLayer(GeoRenderer<T> renderer, ToIntFunction<T> color) {
        super(renderer);
        this.color = color;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        ResourceLocation texture = getRenderer().getTextureLocation(animatable);
        RenderType glow = BastionRenderTypes.translucentEmissive(texture.withPath(path -> path.replace(".png", "_e.png")));
        int rgb = color.applyAsInt(animatable);
        float pulse = 0.8f + 0.2f * Mth.sin((float) RenderUtils.getCurrentTick() * 0.12f); // ~2.6 s breath
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow),
                partialTick, LightTexture.FULL_BRIGHT, packedOverlay,
                (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, pulse);
    }
}
