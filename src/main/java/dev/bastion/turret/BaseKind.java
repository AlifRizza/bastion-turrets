package dev.bastion.turret;

import net.minecraft.world.level.block.Block;

/** Which kind of base a turret stands on; decides which weapon modules fit and which slots and systems it has. */
public enum BaseKind {
    STANDARD, LARGE, MAKESHIFT;

    public static BaseKind of(Block block) {
        return block instanceof MakeshiftTurretBaseBlock ? MAKESHIFT : block instanceof LargeTurretBaseBlock ? LARGE : STANDARD;
    }
}
