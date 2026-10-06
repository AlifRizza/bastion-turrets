package dev.bastion.weapon;

/** How a weapon delivers damage, PLAN 7. Bullets and rockets are PROJECTILE (travel time); the Sniper is CHARGED. */
public enum FireProfile {
    PROJECTILE,
    CHARGED,
    AREA_PULSE,
    /** Lightning bolts at several targets at once (Tesla Coil). */
    ARC,
    /** A continuous cone that hits everything in it (Flamethrower). */
    CONE,
    SPAWNER,
    BEAM_CONTINUOUS
}
