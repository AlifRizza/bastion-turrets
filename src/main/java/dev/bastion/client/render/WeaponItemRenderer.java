package dev.bastion.client.render;

import dev.bastion.weapon.WeaponModuleItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Weapon module in hand and inventory: the same model as on the turret, playing held_idle (PLAN 6.1). */
public class WeaponItemRenderer extends GeoItemRenderer<WeaponModuleItem> {
    public WeaponItemRenderer() {
        super(new WeaponModel<>());
        addRenderLayer(new EmissiveLayer<>(this, item -> EmissiveLayer.MODEL_GLOW));
    }

    /** Hooked up by WeaponModuleItem#initializeClient; the renderer is built on first use, after resources load. */
    public static IClientItemExtensions extensions() {
        return new IClientItemExtensions() {
            private WeaponItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new WeaponItemRenderer();
                return renderer;
            }
        };
    }
}
