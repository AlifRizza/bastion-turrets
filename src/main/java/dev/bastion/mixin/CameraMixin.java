package dev.bastion.mixin;

import dev.bastion.client.overwatch.OverwatchClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overwatch (PLAN "Overwatch Link"): while the player controls a turret through a hub, the camera sits at that turret
 * (behind it, or at the barrel in first person) and the player's body at the hub is drawn as in F5.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void move(double distance, double vertical, double horizontal);

    @Shadow
    protected abstract double getMaxZoom(double distance);

    @Inject(method = "setup", at = @At("TAIL"))
    private void bastion$overwatch(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        OverwatchClient.View view = OverwatchClient.view(partialTick);
        if (view == null) return;
        this.detached = true;
        setRotation(view.yRot(), view.xRot());
        setPosition(view.anchor());
        if (view.distance() > 0) move(-getMaxZoom(view.distance()), 0, 0);
        OverwatchClient.aimFrom((Camera) (Object) this);
    }
}
