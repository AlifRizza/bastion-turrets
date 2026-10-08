package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.turret.TurretBaseBlockEntity;
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
    /** Health glows (user 2026-10-07): steady yellow below half HP, red blinking fast (bright/dim) below a quarter. */
    public static final int DAMAGED_YELLOW = 0xFFC21F, CRITICAL_RED = 0xFF2A1A, CRITICAL_DIM = 0x6E100A;

    /** The glow of a turret base at this health fraction (the weapon keeps MODEL_GLOW). */
    public static int healthGlow(float health) {
        if (health < TurretBaseBlockEntity.CRITICAL_FRACTION) return RenderUtils.getCurrentTick() % 10 < 5 ? CRITICAL_RED : CRITICAL_DIM;
        return health < TurretBaseBlockEntity.DAMAGED_FRACTION ? DAMAGED_YELLOW : MODEL_GLOW;
    }

    private final ToIntFunction<T> color;

    public EmissiveLayer(GeoRenderer<T> renderer, ToIntFunction<T> color) {
        super(renderer);
        this.color = color;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        ResourceLocation texture = getRenderer().getTextureLocation(animatable).withPath(path -> path.replace(".png", "_e.png"));
        RenderType glow = BastionRenderTypes.translucentEmissive(texture);
        int rgb = color.applyAsInt(animatable);
        float pulse = 0.8f + 0.2f * Mth.sin((float) RenderUtils.getCurrentTick() * 0.12f); // ~2.6 s breath
        GlowCubes.active = GlowCubes.of(bakedModel, texture); // renderers that route through GlowCubes draw only the lit faces
        try {
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay,
                    (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, pulse);
        } finally {
            GlowCubes.active = null;
        }
    }
}
