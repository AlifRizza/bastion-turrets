package dev.bastion.mixin;

import dev.bastion.client.overwatch.OverwatchClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overwatch zoom (0.3.0 review): the mouse turns the view slower the further it is zoomed in, like a spyglass. Scales the
 * mouse movement at the start of turnPlayer, never the player.turn call: camera mods redirect that call, and a
 * @ModifyArg on a redirected call crashed the game on startup (GitHub issue #1).
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void bastion$zoomedTurn(CallbackInfo ci) {
        double scale = OverwatchClient.turnScale();
        accumulatedDX *= scale;
        accumulatedDY *= scale;
    }
}
