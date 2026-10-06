package dev.bastion.workstation;

import dev.bastion.client.render.PartItemRenderer;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * A turret part (PLAN Fase 9): made at the Part Workstation, assembled with its siblings into a weapon module or base at
 * the Part Assembler. It is literally a piece of that model: assets/bastion/parts.json names the source model and the
 * bones the part shows, so it renders in 3D like the module it becomes.
 */
public class PartItem extends Item implements GeoItem {
    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);

    public PartItem(Properties properties) {
        super(properties);
    }

    // Only ever called on the client, so the client-package renderer is never loaded on a server.
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(PartItemRenderer.extensions());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animations;
    }
}
