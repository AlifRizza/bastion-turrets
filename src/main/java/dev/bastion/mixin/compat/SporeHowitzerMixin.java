package dev.bastion.mixin.compat;

import dev.bastion.turret.TurretBaseBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Spore's Howitzer bulldozes the blocks in its way (grief) and throws up the ones round where it lands (damageStomp), a
 * turret's and its platform too; it now leaves those (user 2026-10-27, TurretBaseBlock.spared). Its bombs: SporeUtilitiesMixin.
 * Only with Spore in the game (@Pseudo); optional (require 0).
 */
@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sentities.Calamities.Howitzer", remap = false)
public abstract class SporeHowitzerMixin {
    @Redirect(method = "grief(Lnet/minecraft/world/phys/AABB;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z",
                    remap = true), require = 0)
    private boolean bastion$griefSparesTurrets(Level level, BlockPos pos, boolean drop, @Nullable Entity by) {
        return !TurretBaseBlock.spared(level, pos) && level.destroyBlock(pos, drop, by);
    }

    /** The stomp throws a block up (fall), then clears its place (removeBlock): both pass a spared one by. */
    @Redirect(method = "damageStomp(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;DD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;fall(Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/entity/item/FallingBlockEntity;",
                    remap = true), require = 0)
    @Nullable
    private FallingBlockEntity bastion$stompSparesTurrets(Level level, BlockPos pos, BlockState state) {
        return TurretBaseBlock.spared(level, pos) ? null : FallingBlockEntity.fall(level, pos, state);
    }

    @Redirect(method = "damageStomp(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;DD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z", remap = true),
            require = 0)
    private boolean bastion$stompKeepsTurrets(ServerLevel level, BlockPos pos, boolean moving) {
        return !TurretBaseBlock.spared(level, pos) && level.removeBlock(pos, moving);
    }
}
