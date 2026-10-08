package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/** Draws a PlacementGhost: the real texture, tinted (cyan free, red blocked), 40% alpha, fullbright, no depth write. */
public class GhostRenderer extends GeoObjectRenderer<PlacementGhost> {
    private static final int ALPHA = 0x66; // ~40%
    private int rgb = EmissiveLayer.MODEL_GLOW;

    public GhostRenderer() {
        super(new GhostModel());
    }

    /** The model with its origin (footprint centre on the floor, front north) at the pose's origin. */
    public void draw(PoseStack poseStack, MultiBufferSource buffers, PlacementGhost ghost, int rgb) {
        this.rgb = rgb;
        render(poseStack, ghost, buffers, null, null, LightTexture.FULL_BRIGHT);
    }

    @Override
    public RenderType getRenderType(PlacementGhost ghost, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return BastionRenderTypes.translucentEmissive(texture);
    }

    @Override
    public Color getRenderColor(PlacementGhost ghost, float partialTick, int packedLight) {
        return Color.ofRGBA(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, ALPHA);
    }

    /** The caller already placed the origin, so skip GeoObjectRenderer's block-centre offset. */
    @Override
    public void preRender(PoseStack poseStack, PlacementGhost ghost, BakedGeoModel model, MultiBufferSource buffers,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        objectRenderTranslations = new Matrix4f(poseStack.last().pose());
    }
}
