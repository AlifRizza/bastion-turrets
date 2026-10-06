package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.workstation.PartItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** A part in 3D: only its bones of the source model (PartModel), with the model glow. */
public class PartItemRenderer extends GeoItemRenderer<PartItem> {
    public PartItemRenderer() {
        super(new PartModel());
        addRenderLayer(new EmissiveLayer<>(this, item -> EmissiveLayer.MODEL_GLOW));
    }

    /** The source model is shared with the turret renderers: show every bone again once the part is drawn. */
    @Override
    public void postRender(PoseStack poseStack, PartItem animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                           boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        super.postRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        if (!isReRender) for (CoreGeoBone bone : getGeoModel().getAnimationProcessor().getRegisteredBones()) bone.setHidden(false);
    }

    /** Hooked up by PartItem#initializeClient; built on first use, after resources load. */
    public static IClientItemExtensions extensions() {
        return new IClientItemExtensions() {
            private PartItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new PartItemRenderer();
                return renderer;
            }
        };
    }
}
