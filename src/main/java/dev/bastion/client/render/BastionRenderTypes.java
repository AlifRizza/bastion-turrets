package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Bastion render types, PLAN 5.1. Extends RenderType only to reach its protected state shards.
 * BEAM arrives with the post-MVP beam weapons; SOFT_PARTICLE is a particle render type (VfxParticle).
 */
public final class BastionRenderTypes extends RenderType {
    /** Additive (SRC_ALPHA, ONE), no depth write, fullbright: projectile glow, tracers, muzzle flash, holograms. */
    private static final Function<ResourceLocation, RenderType> ADDITIVE_GLOW = Util.memoize(texture -> create("bastion_additive_glow",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 1024, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                    .setTextureState(new TextureStateShard(texture, true, false)) // smooth glow gradients
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(PARTICLES_TARGET)
                    .createCompositeState(false)));

    /** Like ADDITIVE_GLOW but alpha-blended: a coloured halo that still shows against a bright daytime sky (lightning). */
    private static final Function<ResourceLocation, RenderType> TRANSLUCENT_GLOW = Util.memoize(texture -> create("bastion_translucent_glow",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 1024, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                    .setTextureState(new TextureStateShard(texture, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(PARTICLES_TARGET)
                    .createCompositeState(false)));

    /**
     * GeckoLib emissive layer: vanilla entity_translucent_emissive without the per-frame quad sort. The glow lies on the
     * model's own faces, so the order never shows, while sorting every turret's glow each frame was a top render cost.
     */
    private static final Function<ResourceLocation, RenderType> MODEL_GLOW = Util.memoize(texture -> create("bastion_model_glow",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false)));

    private BastionRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                               boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
    }

    /** Vertex order: position, colour, uv, light (use LightTexture.FULL_BRIGHT). */
    public static RenderType additiveGlow(ResourceLocation texture) {
        return ADDITIVE_GLOW.apply(texture);
    }

    /** Vertex order as {@link #additiveGlow}. */
    public static RenderType translucentGlow(ResourceLocation texture) {
        return TRANSLUCENT_GLOW.apply(texture);
    }

    /** Emissive panels and lights on models (GeckoLib emissive layer): translucent, fullbright. */
    public static RenderType translucentEmissive(ResourceLocation texture) {
        return MODEL_GLOW.apply(texture);
    }
}
