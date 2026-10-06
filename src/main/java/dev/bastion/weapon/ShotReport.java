package dev.bastion.weapon;

import dev.bastion.network.BastionNetwork;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.registry.BastionWeaponTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Turns server hit results into the TurretFireEvent clients animate (PLAN 4.8). */
public final class ShotReport {
    private ShotReport() {
    }

    public static TurretFireEvent.Shot of(Level level, HitResult hit, Vec3 direction) {
        if (hit instanceof EntityHitResult entity) {
            return new TurretFireEvent.Shot(entity.getLocation(), direction.reverse(), TurretFireEvent.ENTITY, 0);
        }
        if (hit instanceof BlockHitResult block && block.getType() != HitResult.Type.MISS) {
            return new TurretFireEvent.Shot(block.getLocation(), Vec3.atLowerCornerOf(block.getDirection().getNormal()),
                    TurretFireEvent.BLOCK, Block.getId(level.getBlockState(block.getBlockPos())));
        }
        return new TurretFireEvent.Shot(hit.getLocation(), direction.reverse(), TurretFireEvent.MISS, 0);
    }

    /** Broadcasts the discharge to players within 96 blocks. */
    public static void send(FireContext context, WeaponType type, int muzzle, List<TurretFireEvent.Shot> shots) {
        BastionNetwork.sendNear(context.level(), context.turret().getBlockPos(), new TurretFireEvent(context.turret().getBlockPos(),
                BastionWeaponTypes.REGISTRY.get().getKey(type), muzzle, context.seed(), context.precisionLock(), shots));
    }
}
