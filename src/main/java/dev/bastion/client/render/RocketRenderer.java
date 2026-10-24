package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bastion.Bastion;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Turret rocket in flight: nose along its velocity, nozzle glowing exhaust-orange. Trail and sound: RocketEffects. Also the
 * Javelin missile (its sensor nose glows cyan; JavelinEffects).
 */
public class RocketRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    private static final int EXHAUST = 0xFF8A3C;

    public RocketRenderer(EntityRendererProvider.Context context) {
        this(context, "turret_rocket");
    }

    /** @param model geo/animation/texture name under entity/: turret_rocket or turret_missile */
    public RocketRenderer(EntityRendererProvider.Context context, String model) {
        this(context, model, EXHAUST);
    }

    /** @param glow the colour its glowing parts are drawn in */
    public RocketRenderer(EntityRendererProvider.Context context, String model, int glow) {
        super(context, new DefaultedEntityGeoModel<>(Bastion.id(model)));
        addRenderLayer(new EmissiveLayer<>(this, rocket -> glow));
        shadowRadius = 0;
    }

    /** Not drawn while invisible (a Javelin out of sight at the top of its climb). */
    @Override
    public boolean shouldRender(T rocket, Frustum frustum, double x, double y, double z) {
        return !rocket.isInvisible() && super.shouldRender(rocket, frustum, x, y, z);
    }

    /** The model's nose points north (-Z); turn it onto the velocity (yaw, then climb), still, onto its pitch. */
    @Override
    protected void applyRotations(T rocket, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        Vec3 v = rocket.getDeltaMovement();
        if (v.lengthSqr() < 1e-6) v = new Vec3(0, -Mth.sin(rocket.getXRot() * Mth.DEG_TO_RAD), 0); // a Javelin held over its silo, nose up
        if (v.lengthSqr() < 1e-6) return;
        poseStack.translate(0, rocket.getBbHeight() / 2, 0);
        poseStack.mulPose(Axis.YP.rotation((float) Math.atan2(-v.x, -v.z)));
        poseStack.mulPose(Axis.XP.rotation((float) Math.atan2(v.y, v.horizontalDistance())));
    }
}
