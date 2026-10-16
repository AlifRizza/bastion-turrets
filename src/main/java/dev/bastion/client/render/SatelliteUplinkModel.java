package dev.bastion.client.render;

import dev.bastion.Bastion;
import dev.bastion.hub.SatelliteUplinkBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/** Satellite Uplink model files, named after the tier's block (geo/satellite_uplink_t2.geo.json, ...), shared by block and item. */
public class SatelliteUplinkModel<T extends GeoAnimatable> extends GeoModel<T> {
    private static String name(GeoAnimatable animatable) {
        if (animatable instanceof SatelliteUplinkBlockEntity satellite) return BuiltInRegistries.BLOCK.getKey(satellite.getBlockState().getBlock()).getPath();
        return BuiltInRegistries.ITEM.getKey((Item) animatable).getPath();
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
}
