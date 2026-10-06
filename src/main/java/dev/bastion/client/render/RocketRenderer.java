package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bastion.Bastion;
import dev.bastion.projectile.TurretRocketEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Turret rocket in flight: nose along its velocity, nozzle glowing exhaust-orange. Trail and sound: RocketEffects. */
public class RocketRenderer extends GeoEntityRenderer<TurretRocketEntity> {
    private static final int EXHAUST = 0xFF8A3C;

    public RocketRenderer(EntityRendererProvider.Context context) {
        this(context, "turret_rocket");
    }

    /** @param model geo/animation/texture name under entity/: turret_rocket or turret_missile */
    public RocketRenderer(EntityRendererProvider.Context context, String model) {
        super(context, new DefaultedEntityGeoModel<>(Bastion.id(model)));
        addRenderLayer(new EmissiveLayer<>(this, rocket -> EXHAUST));
        shadowRadius = 0;
    }

    /** The model's nose points north (-Z); turn it onto the velocity (yaw, then climb). */
    @Override
    protected void applyRotations(TurretRocketEntity rocket, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        Vec3 v = rocket.getDeltaMovement();
        if (v.lengthSqr() < 1e-6) return;
        poseStack.translate(0, rocket.getBbHeight() / 2, 0);
        poseStack.mulPose(Axis.YP.rotation((float) Math.atan2(-v.x, -v.z)));
        poseStack.mulPose(Axis.XP.rotation((float) Math.atan2(v.y, v.horizontalDistance())));
    }
}
