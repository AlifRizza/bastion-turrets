package dev.bastion.mixin;

import dev.bastion.client.overwatch.OverwatchClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Overwatch zoom (0.3.0 review): the mouse turns the view slower the further it is zoomed in, like a spyglass. Optional
 * (require 0): if a camera mod already redirects this call, zoom keeps the normal speed instead of the game failing to start.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0, require = 0)
    private double bastion$zoomedYaw(double yaw) {
        return yaw * OverwatchClient.turnScale();
    }

    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1, require = 0)
    private double bastion$zoomedPitch(double pitch) {
        return pitch * OverwatchClient.turnScale();
    }
}
