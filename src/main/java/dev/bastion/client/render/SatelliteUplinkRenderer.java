package dev.bastion.client.render;

import dev.bastion.hub.SatelliteUplinkBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** The satellite's mast and dish; the glow is cyan online, red offline, dim while the hub is not formed. */
public class SatelliteUplinkRenderer extends GeoBlockRenderer<SatelliteUplinkBlockEntity> {
    private static final int DIM = 0x1E5A6A;

    public SatelliteUplinkRenderer() {
        super(new SatelliteUplinkModel<>());
        addRenderLayer(new EmissiveLayer<>(this, satellite -> switch (satellite.status()) {
            case ONLINE -> EmissiveLayer.MODEL_GLOW;
            case OFFLINE -> EmissiveLayer.CRITICAL_RED;
            case UNFORMED -> DIM;
        }));
    }
}
