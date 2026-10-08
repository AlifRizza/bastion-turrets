package dev.bastion.client.render;

import dev.bastion.turret.TurretTier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;

/** What the placement hologram draws: a block's model at rest (name = block id path), at a base tier (null: not a base). */
public final class PlacementGhost implements GeoAnimatable {
    private static final Map<String, PlacementGhost> ALL = new HashMap<>();
    final String model;
    @Nullable
    final TurretTier tier;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private PlacementGhost(String model, @Nullable TurretTier tier) {
        this.model = model;
        this.tier = tier;
    }

    /** One per model and tier, so GeckoLib keeps one instance cache for it. */
    public static PlacementGhost of(String model, @Nullable TurretTier tier) {
        return ALL.computeIfAbsent(model + "/" + tier, key -> new PlacementGhost(model, tier));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return 0;
    }
}
