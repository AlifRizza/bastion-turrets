package dev.bastion.mixin;

import dev.bastion.projectile.TurretArrowEntity;
import net.minecraft.world.entity.projectile.Arrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turret arrows show none of vanilla's tipped-arrow particles (PLAN 1, user rule): no swirls in flight or while stuck
 * (EarlyEffects plays our own mist in the arrow's colour instead) and no puff when a stuck arrow's potion wears off. Client
 * only (these run on the client alone); optional (require 0): if another mod changes these methods, the turret arrows keep
 * vanilla's particles instead of the game failing to start.
 */
@Mixin(Arrow.class)
public abstract class ArrowMixin {
    @Inject(method = "makeParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void bastion$noSwirls(int count, CallbackInfo ci) {
        if ((Object) this instanceof TurretArrowEntity) ci.cancel();
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void bastion$noPotionPuff(byte id, CallbackInfo ci) {
        if (id == 0 && (Object) this instanceof TurretArrowEntity) ci.cancel(); // 0: Arrow's EVENT_POTION_PUFF
    }
}
