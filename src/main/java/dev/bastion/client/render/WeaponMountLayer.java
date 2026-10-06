package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.client.ClientTurret;
import dev.bastion.client.anim.WeaponAnimatable;
import dev.bastion.turret.TurretBaseBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Draws the mounted weapon at the pivot of the base's weapon_mount bone (PLAN 6.1). Runs as a layer, after
 * the base geometry is written, so switching to the weapon's buffers cannot cut the base batch. weapon_mount
 * is never animated, so its rest pivot is where the weapon sits. After drawing, the world positions of the
 * muzzle, eject and sensor bones are stored on the ClientTurret for VFX (PLAN 6.2).
 */
public class WeaponMountLayer extends GeoRenderLayer<TurretBaseBlockEntity> {
    private final WeaponModelRenderer weaponRenderer = new WeaponModelRenderer();

    public WeaponMountLayer(GeoRenderer<TurretBaseBlockEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, TurretBaseBlockEntity turret, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        ClientTurret client = ClientTurret.of(turret.getBlockPos());
        WeaponAnimatable weapon = client.updateWeapon(turret);
        if (weapon == null) return;
        weapon.setAim(turret.renderYaw(partialTick), turret.renderPitch(partialTick));
        bakedModel.getBone("weapon_mount").ifPresent(mount -> {
            poseStack.pushPose();
            RenderUtils.translateToPivotPoint(poseStack, mount);
            weaponRenderer.render(poseStack, weapon, bufferSource, null, null, packedLight);
            poseStack.popPose();

            // The weapon's object space starts at the mount pivot, in the turret frame (floor, wall or ceiling).
            Vec3 origin = turret.modelToWorld(mount.getPivotX() / 16f, mount.getPivotY() / 16f, mount.getPivotZ() / 16f);
            BakedGeoModel weaponModel = weaponRenderer.getGeoModel().getBakedModel(weaponRenderer.getGeoModel().getModelResource(weapon));
            for (int i = 0; i < client.muzzles.length; i++) client.muzzles[i] = world(turret, weaponModel, "muzzle_" + i, origin);
            client.eject = world(turret, weaponModel, "eject_0", origin);
            client.sensor = world(turret, weaponModel, "sensor", origin);
        });
    }

    private static Vec3 world(TurretBaseBlockEntity turret, BakedGeoModel model, String bone, Vec3 origin) {
        GeoBone geoBone = model.getBone(bone).orElse(null);
        if (geoBone == null || !geoBone.isTrackingMatrices()) return null;
        Vector3f pivot = new Vector3f(geoBone.getPivotX() / 16f, geoBone.getPivotY() / 16f, geoBone.getPivotZ() / 16f);
        geoBone.getLocalSpaceMatrix().transformPosition(pivot);
        return origin.add(turret.toWorld(new Vec3(pivot.x, pivot.y, pivot.z)));
    }
}
