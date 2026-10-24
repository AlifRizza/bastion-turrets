package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bastion.Bastion;
import dev.bastion.unit.HiveDroneEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Hive drone: its status light and eye glow by its health like a turret's lights (cyan, yellow below half, red blinking
 * below a quarter, fast red while it falls); it turns to where it faces, leans into its flight and sways a little while
 * it hovers; diving kamikaze it noses down and rolls.
 */
public class HiveDroneRenderer extends GeoEntityRenderer<HiveDroneEntity> {
    public HiveDroneRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(Bastion.id("hive_drone")));
        addRenderLayer(new EmissiveLayer<>(this, drone -> drone.deathTime > 0 || drone.state() == HiveDroneEntity.State.KAMIKAZE
                ? (RenderUtils.getCurrentTick() % 4 < 2 ? EmissiveLayer.CRITICAL_RED : EmissiveLayer.CRITICAL_DIM)
                : EmissiveLayer.healthGlow(drone.getHealth() / drone.getMaxHealth())));
        shadowRadius = 0.3f;
    }

    @Override
    protected void applyRotations(HiveDroneEntity drone, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partialTick, drone.yBodyRotO, drone.yBodyRot)));
        Vec3 v = drone.getDeltaMovement();
        if (drone.state() == HiveDroneEntity.State.KAMIKAZE) {
            poseStack.translate(0, drone.getBbHeight() / 2, 0);
            poseStack.mulPose(Axis.XP.rotationDegrees(-60));
            poseStack.mulPose(Axis.ZP.rotationDegrees(ageInTicks * 30));
            poseStack.translate(0, -drone.getBbHeight() / 2, 0);
            return;
        }
        float lean = (float) Mth.clamp(v.horizontalDistance() * 60, 0, 20);
        float sway = Mth.sin(ageInTicks * 0.15f + drone.getId()) * 3;
        poseStack.mulPose(Axis.XP.rotationDegrees(-lean));
        poseStack.mulPose(Axis.ZP.rotationDegrees(sway));
    }
}
