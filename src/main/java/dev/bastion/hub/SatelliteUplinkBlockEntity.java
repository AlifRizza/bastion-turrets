package dev.bastion.hub;

import dev.bastion.client.ControlHubEffects;
import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.registry.BastionBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Bottom block of a Satellite Uplink: GeckoLib animatable for its dish. Folded while the hub is not formed, unfolding once
 * when it is seen forming online (not when the chunk loads with it already up), turning while online, stopped offline.
 */
public class SatelliteUplinkBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation FOLDED = RawAnimation.begin().thenLoop("animation.satellite_uplink.folded");
    private static final RawAnimation DEPLOY = RawAnimation.begin().thenPlay("animation.satellite_uplink.deploy")
            .thenLoop("animation.satellite_uplink.spin");
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.satellite_uplink.spin");
    private static final RawAnimation OFFLINE = RawAnimation.begin().thenLoop("animation.satellite_uplink.offline");
    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);
    /** Client: the status one tick ago (null before the first tick). */
    @Nullable
    private Status seen;
    /** Client: online since it was seen forming, so the deploy clip plays before the spin. */
    private boolean deploying;

    public SatelliteUplinkBlockEntity(BlockPos pos, BlockState state) {
        super(BastionBlockEntities.SATELLITE_UPLINK.get(), pos, state);
    }

    public Status status() {
        return getBlockState().getValue(SatelliteUplinkBlock.STATUS);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SatelliteUplinkBlockEntity satellite) {
        Status now = state.getValue(SatelliteUplinkBlock.STATUS);
        if (now != Status.ONLINE) satellite.deploying = false;
        else if (satellite.seen == Status.UNFORMED) satellite.deploying = true;
        satellite.seen = now;
        if (now == Status.ONLINE) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ControlHubEffects.dishHum(satellite));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "dish", 5, state -> state.setAndContinue(switch (status()) {
            case UNFORMED -> FOLDED;
            case OFFLINE -> OFFLINE;
            case ONLINE -> deploying ? DEPLOY : SPIN;
        })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animations;
    }

    /** The dish reaches past the two blocks. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5).expandTowards(0, 2, 0);
    }
}
