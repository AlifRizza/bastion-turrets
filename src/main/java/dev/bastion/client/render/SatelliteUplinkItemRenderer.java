package dev.bastion.client.render;

import dev.bastion.hub.SatelliteUplinkItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** A Satellite Uplink as an item: its whole model, dish turning. */
public class SatelliteUplinkItemRenderer extends GeoItemRenderer<SatelliteUplinkItem> {
    public SatelliteUplinkItemRenderer() {
        super(new SatelliteUplinkModel<>());
        addRenderLayer(new EmissiveLayer<>(this, item -> EmissiveLayer.MODEL_GLOW));
    }

    /** Hooked up by SatelliteUplinkItem#initializeClient; built on first use, after resources load. */
    public static IClientItemExtensions extensions() {
        return new IClientItemExtensions() {
            private SatelliteUplinkItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new SatelliteUplinkItemRenderer();
                return renderer;
            }
        };
    }
}
