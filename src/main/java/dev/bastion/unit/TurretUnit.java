package dev.bastion.unit;

import net.minecraft.core.BlockPos;

/**
 * Something a turret sent out (PLAN "Turret 3x3": Brood Cannon spiders and their capsules, Hive Carrier drones): it counts
 * toward that turret's unit cap, turrets never aim at it, and hostile mobs go after the living ones.
 */
public interface TurretUnit {
    /** The core block of the turret it belongs to (kept after that turret is gone). */
    BlockPos home();

    /** How many places it takes in its turret's cap (a capsule: the spiders it will release). */
    default int unitWeight() {
        return 1;
    }
}
