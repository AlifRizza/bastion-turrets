package dev.bastion.hub;

import dev.bastion.client.render.SatelliteUplinkItemRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/** A Satellite Uplink as an item: its animated model in hand and inventory. */
public class SatelliteUplinkItem extends BlockItem implements GeoItem {
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.satellite_uplink.spin");
    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);

    public SatelliteUplinkItem(SatelliteUplinkBlock block, Properties properties) {
        super(block, properties);
    }

    // Only ever called on the client, so the client-package renderer is never loaded on a server.
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SatelliteUplinkItemRenderer.extensions());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "dish", 0, state -> state.setAndContinue(SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animations;
    }
}
