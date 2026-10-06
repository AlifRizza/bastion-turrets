package dev.bastion.workstation;

import dev.bastion.client.render.WorkstationItemRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/** A workstation as an item: its whole animated model in hand and inventory. */
public class WorkstationItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);

    public WorkstationItem(WorkstationBlock block, Properties properties) {
        super(block, properties);
    }

    public WorkstationType type() {
        return ((WorkstationBlock) getBlock()).type();
    }

    // Only ever called on the client, so the client-package renderer is never loaded on a server.
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(WorkstationItemRenderer.extensions());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation idle = RawAnimation.begin().thenLoop("animation." + type().id() + ".idle");
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(idle)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animations;
    }
}
