package dev.bastion.turret;

import net.minecraft.world.level.block.Block;

/**
 * Which kind of base a turret stands on; decides which weapon modules fit and which slots and systems it has. FORTRESS is
 * the 3x3 base for the "huge" weapon size class. Appended in order: the ordinal goes over the wire.
 */
public enum BaseKind {
    STANDARD, LARGE, MAKESHIFT, FORTRESS;

    public static BaseKind of(Block block) {
        return block instanceof FortressTurretBaseBlock ? FORTRESS : block instanceof MakeshiftTurretBaseBlock ? MAKESHIFT
                : block instanceof LargeTurretBaseBlock ? LARGE : STANDARD;
    }
}
