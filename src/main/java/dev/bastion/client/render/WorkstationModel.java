package dev.bastion.client.render;

import dev.bastion.Bastion;
import dev.bastion.workstation.WorkstationBlockEntity;
import dev.bastion.workstation.WorkstationItem;
import dev.bastion.workstation.WorkstationType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * Workstation model files, named after the station (geo/part_workstation.geo.json, ...), shared by the block and its
 * item. Bones tool_0..3 mark where the tools touch the work (sparks, WorkstationEffects); work_0 where the item being
 * made sits (WorkstationRenderer).
 */
public class WorkstationModel<T extends GeoAnimatable> extends GeoModel<T> {
    static final int TOOLS = 4;

    private static String name(GeoAnimatable animatable) {
        WorkstationType type = animatable instanceof WorkstationBlockEntity station ? station.type() : ((WorkstationItem) animatable).type();
        return type.id();
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return Bastion.id("geo/" + name(animatable) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return Bastion.id("textures/block/" + name(animatable) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return Bastion.id("animations/" + name(animatable) + ".animation.json");
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        if (animatable instanceof WorkstationBlockEntity) {
            for (int i = 0; i < TOOLS; i++) getBone("tool_" + i).ifPresent(bone -> bone.setTrackingMatrices(true));
        }
    }
}
