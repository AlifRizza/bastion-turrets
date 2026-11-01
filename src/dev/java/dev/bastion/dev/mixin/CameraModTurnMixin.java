package dev.bastion.dev.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Dev runs only (src/dev, never in a jar): acts like a camera mod that redirects player.turn in turnPlayer, applied
 * before Bastion's own mixins (priority 500). Keeps GitHub issue #1 from coming back: a Bastion injector on that call
 * crashes every runClient on startup.
 */
@Mixin(value = MouseHandler.class, priority = 500)
public abstract class CameraModTurnMixin {
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void bastiondev$cameraModTurn(LocalPlayer player, double yaw, double pitch) {
        player.turn(yaw, pitch);
    }
}
