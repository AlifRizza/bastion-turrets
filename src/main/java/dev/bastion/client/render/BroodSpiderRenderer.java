package dev.bastion.client.render;

import dev.bastion.Bastion;
import dev.bastion.unit.BroodSpiderEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Brood spider: its eyes and strip glow by its health like a turret's lights (cyan, yellow below half, red blinking
 * below a quarter); while it dies they blink red fast until the burst, and it never tips over.
 */
public class BroodSpiderRenderer extends GeoEntityRenderer<BroodSpiderEntity> {
    public BroodSpiderRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(Bastion.id("brood_spider")));
        addRenderLayer(new EmissiveLayer<>(this, spider -> spider.deathTime > 0
                ? (RenderUtils.getCurrentTick() % 4 < 2 ? EmissiveLayer.CRITICAL_RED : EmissiveLayer.CRITICAL_DIM)
                : EmissiveLayer.healthGlow(spider.getHealth() / spider.getMaxHealth())));
        shadowRadius = 0.35f;
    }

    @Override
    protected float getDeathMaxRotation(BroodSpiderEntity spider) {
        return 0;
    }
}
