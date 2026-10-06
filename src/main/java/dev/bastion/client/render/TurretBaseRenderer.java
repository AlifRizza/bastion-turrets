package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.turret.TurretBaseBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.util.RenderUtils;

/** Turret base in the world plus its mounted weapon (PLAN 6.1). */
public class TurretBaseRenderer extends GeoBlockRenderer<TurretBaseBlockEntity> {
    private static final int DAMAGED_RED = 0xFF3A30;

    public TurretBaseRenderer() {
        super(new TurretBaseModel<>());
        // The shared model glow; below 30% HP it blinks red (PLAN 4.1).
        addRenderLayer(new EmissiveLayer<>(this, turret ->
                turret.clientDamaged() && RenderUtils.getCurrentTick() % 16 < 8 ? DAMAGED_RED : EmissiveLayer.MODEL_GLOW));
        addRenderLayer(new WeaponMountLayer(this));
    }

    /**
     * Floor, wall or ceiling (PLAN 4.1): the same Direction#getRotation the server aims with, about the block centre.
     * GeckoLib calls this after moving to the floor centre and before the layers, so the weapon turns along.
     */
    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        // A large base renders from its core block but turns around the middle of its 2x2.
        if (animatable != null && animatable.large()) {
            Vec3 offset = animatable.mountCenter().subtract(Vec3.atCenterOf(animatable.getBlockPos()));
            poseStack.translate(offset.x, 0, offset.z);
        }
        poseStack.translate(0, 0.5, 0);
        poseStack.mulPose(facing.getRotation());
        poseStack.translate(0, -0.5, 0);
    }

    @Override
    public void preRender(PoseStack poseStack, TurretBaseBlockEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        TurretBaseModel.showTier(model, animatable.tier());
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /**
     * Main pass: only the animated bones, the rest of the base is in the chunk mesh (BakedTurretBaseModel). Glow pass:
     * only the faces with glow pixels (GlowCubes), from every bone, so the glow still breathes and blinks red.
     */
    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if (GlowCubes.active == null && !animated(bone)) return;
        GlowCubes.renderCubesOfBone(this, poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    private static boolean animated(GeoBone bone) {
        for (GeoBone b = bone; b != null; b = b.getParent()) if (BakedTurretBaseModel.ANIMATED.contains(b.getName())) return true;
        return false;
    }
}
