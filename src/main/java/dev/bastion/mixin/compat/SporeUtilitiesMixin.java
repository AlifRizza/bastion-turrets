package dev.bastion.mixin.compat;

import dev.bastion.turret.TurretBaseBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Spore's blast (Utilities.explodeCircle: the Howitzer's flesh bombs, among others) removes every block under a hardness
 * in its radius, turrets with it whatever their HP; it now leaves a turret and the block it stands on (user 2026-10-27,
 * TurretBaseBlock.spared). Only with Spore in the game (@Pseudo); optional (require 0) like the client mixins.
 */
@Pseudo
@Mixin(targets = "com.Harbinger.Spore.ExtremelySusThings.Utilities", remap = false)
public abstract class SporeUtilitiesMixin {
    @Redirect(method = "explodeCircle(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;"
            + "DFLnet/minecraft/core/particles/ParticleOptions;ZDLjava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z", remap = true),
            require = 0)
    private static boolean bastion$spareTurrets(ServerLevel level, BlockPos pos, boolean moving) {
        return !TurretBaseBlock.spared(level, pos) && level.removeBlock(pos, moving);
    }
}
