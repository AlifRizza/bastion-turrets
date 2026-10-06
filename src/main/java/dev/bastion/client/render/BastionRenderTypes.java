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
        return RenderType.entityTranslucentEmissive(texture);
    }
}
