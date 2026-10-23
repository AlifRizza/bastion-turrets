package dev.bastion.mixin;

import dev.bastion.projectile.TurretArrowEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Turret arrows leave no vanilla particles in flight (PLAN 1, user rule): no water bubbles (and no crit sparks, which they
 * never have), with nothing in their place; their physics are untouched. Client only, optional (require 0) like ArrowMixin:
 * if another mod already redirects these calls, this one stands aside.
 */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"), require = 0)
    private void bastion$noVanillaParticles(Level level, ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz) {
        if (!((Object) this instanceof TurretArrowEntity)) level.addParticle(particle, x, y, z, dx, dy, dz);
    }
}
