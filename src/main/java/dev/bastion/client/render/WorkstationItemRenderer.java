package dev.bastion.client.render;

import dev.bastion.workstation.WorkstationItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** A workstation's whole model as an item. */
public class WorkstationItemRenderer extends GeoItemRenderer<WorkstationItem> {
    public WorkstationItemRenderer() {
        super(new WorkstationModel<>());
        addRenderLayer(new EmissiveLayer<>(this, item -> EmissiveLayer.MODEL_GLOW));
    }

    /** Hooked up by WorkstationItem#initializeClient; built on first use, after resources load. */
    public static IClientItemExtensions extensions() {
        return new IClientItemExtensions() {
            private WorkstationItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new WorkstationItemRenderer();
                return renderer;
            }
        };
    }
}
