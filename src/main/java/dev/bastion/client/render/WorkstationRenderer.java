package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.bastion.client.WorkstationEffects;
import dev.bastion.workstation.WorkstationBlockEntity;
import dev.bastion.workstation.WorkstationRecipe;
import dev.bastion.workstation.WorkstationType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * A workstation in the world: the whole multiblock model from its core block, centred on the footprint and turned so
 * its front faces the block's FACING. While it works, the item being made sits at the work_0 bone, slowly turning;
 * the tool bones' world positions are handed to WorkstationEffects for sparks.
 */
public class WorkstationRenderer extends GeoBlockRenderer<WorkstationBlockEntity> {
    public WorkstationRenderer() {
        super(new WorkstationModel<>());
        addRenderLayer(new EmissiveLayer<>(this, station -> EmissiveLayer.MODEL_GLOW));
        addRenderLayer(new WorkPiece(this));
    }

    /** Footprint centre, then the usual horizontal turn (the model's front is north). */
    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        if (animatable != null) {
            WorkstationType type = animatable.type();
            Direction right = facing.getCounterClockWise(), back = facing.getOpposite();
            double x = (type.width - 1) / 2.0, z = (type.depth - 1) / 2.0;
            poseStack.translate(right.getStepX() * x + back.getStepX() * z, 0, right.getStepZ() * x + back.getStepZ() * z);
        }
        super.rotateBlock(facing, poseStack);
    }

    @Override
    public void postRender(PoseStack poseStack, WorkstationBlockEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                           VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                           float red, float green, float blue, float alpha) {
        super.postRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        if (isReRender) return;
        Vec3[] tools = new Vec3[WorkstationModel.TOOLS];
        for (int i = 0; i < tools.length; i++) {
            int index = i;
            model.getBone("tool_" + i).filter(bone -> bone.isTrackingMatrices())
                    .ifPresent(bone -> tools[index] = new Vec3(bone.getWorldPosition().x, bone.getWorldPosition().y, bone.getWorldPosition().z));
        }
        WorkstationEffects.tools(animatable, tools);
    }

    /** The item being made, on the work spot, while the station works. Drawn after the model so its buffers cannot cut the batch. */
    private static final class WorkPiece extends GeoRenderLayer<WorkstationBlockEntity> {
        WorkPiece(GeoRenderer<WorkstationBlockEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, WorkstationBlockEntity station, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            WorkstationRecipe recipe = station.working() ? station.recipe() : null;
            if (recipe == null) return;
            bakedModel.getBone("work_0").ifPresent(bone -> {
                poseStack.pushPose();
                RenderUtils.translateToPivotPoint(poseStack, bone);
                float scale = switch (station.type()) {
                    case PART_ASSEMBLER -> 0.95f;
                    case PART_WORKSTATION -> 0.6f;
                    case CHARGING_STATION -> 0.45f;
                    default -> 0.4f;
                };
                poseStack.scale(scale, scale, scale);
                poseStack.mulPose(Axis.YP.rotationDegrees((float) (RenderUtils.getCurrentTick() + partialTick) * 1.5f));
                Minecraft.getInstance().getItemRenderer().renderStatic(recipe.result(), ItemDisplayContext.FIXED, packedLight, packedOverlay,
                        poseStack, bufferSource, station.getLevel(), 0);
                poseStack.popPose();
            });
        }
    }
}
