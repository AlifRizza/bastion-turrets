package dev.bastion.client.render;

import dev.bastion.Bastion;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretBaseItem;
import dev.bastion.turret.TurretTier;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/** Base model files (PLAN 6.5 layout), shared by the block and item renderers: turret_base or large_turret_base. */
public class TurretBaseModel<T extends GeoAnimatable> extends GeoModel<T> {
    private static String name(Object animatable) {
        boolean large = animatable instanceof TurretBaseBlockEntity base ? base.large() : animatable instanceof TurretBaseItem item && item.large();
        return large ? "large_turret_base" : "turret_base";
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

    /** Tier visuals (PLAN 4.1): armour plates from T2, cooling fins from T3. */
    public static void showTier(BakedGeoModel model, TurretTier tier) {
        model.getBone("armor_t2").ifPresent(bone -> bone.setHidden(tier.ordinal() < TurretTier.T2.ordinal()));
        model.getBone("fins_t3").ifPresent(bone -> bone.setHidden(tier.ordinal() < TurretTier.T3.ordinal()));
    }
}
