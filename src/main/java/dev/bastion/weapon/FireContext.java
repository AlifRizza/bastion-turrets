package dev.bastion.weapon;

import dev.bastion.turret.TurretBaseBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a weapon needs for one shot. {@code seed} drives spread so clients can rebuild the same pattern;
 * {@code precisionLock} is true for the one locked shot per target (PLAN 7.1). {@code manualPoint} is non-null only for a
 * shot an Overwatch controller fired: the spot under their crosshair (PLAN "Overwatch Link").
 */
public record FireContext(ServerLevel level, TurretBaseBlockEntity turret, Vec3 muzzle, Vec3 direction,
                          Entity target, StatSheet stats, WeaponState state, long seed, boolean precisionLock,
                          @Nullable Vec3 manualPoint) {
}
