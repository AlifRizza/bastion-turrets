package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bastion.Bastion;
import dev.bastion.projectile.BroodCapsuleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Brood capsule: upright, spinning slowly in flight, still on the ground; its core glows cyan; the petals open (GeckoLib clip). */
public class BroodCapsuleRenderer extends GeoEntityRenderer<BroodCapsuleEntity> {
    public BroodCapsuleRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(Bastion.id("turret_brood_capsule")));
        addRenderLayer(new EmissiveLayer<>(this, capsule -> EmissiveLayer.MODEL_GLOW));
        shadowRadius = 0.3f;
    }

    @Override
    protected void applyRotations(BroodCapsuleEntity capsule, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        if (capsule.getDeltaMovement().lengthSqr() > 1e-6) poseStack.mulPose(Axis.YP.rotationDegrees(ageInTicks * 14));
    }
}
