package dev.bastion.client.render;

import dev.bastion.Bastion;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.model.GeoModel;

/**
 * The placement hologram's model: the block's own geo and texture. Bones are shared with the block renderers, so every
 * frame this puts them at rest and shows them all, then hides the tier parts above the item's tier (bases).
 */
public class GhostModel extends GeoModel<PlacementGhost> {
    @Override
    public ResourceLocation getModelResource(PlacementGhost ghost) {
        return Bastion.id("geo/" + ghost.model + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PlacementGhost ghost) {
        return Bastion.id("textures/block/" + ghost.model + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(PlacementGhost ghost) {
        return Bastion.id("animations/" + ghost.model + ".animation.json");
    }

    @Override
    public void setCustomAnimations(PlacementGhost ghost, long instanceId, AnimationState<PlacementGhost> animationState) {
        for (CoreGeoBone bone : getAnimationProcessor().getRegisteredBones()) {
            BoneSnapshot rest = bone.getInitialSnapshot();
            bone.setRotX(rest.getRotX());
            bone.setRotY(rest.getRotY());
            bone.setRotZ(rest.getRotZ());
            bone.setPosX(rest.getOffsetX());
            bone.setPosY(rest.getOffsetY());
            bone.setPosZ(rest.getOffsetZ());
            bone.setScaleX(rest.getScaleX());
            bone.setScaleY(rest.getScaleY());
            bone.setScaleZ(rest.getScaleZ());
            bone.setHidden(false);
            bone.setChildrenHidden(false);
        }
        if (ghost.tier != null) TurretBaseModel.showTier(getBakedModel(getModelResource(ghost)), ghost.tier);
    }
}
