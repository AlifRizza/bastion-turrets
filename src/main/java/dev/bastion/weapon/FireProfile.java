package dev.bastion.weapon;

/** How a weapon delivers damage, PLAN 7. Only the three hitscan profiles exist in the MVP. */
public enum FireProfile {
    HITSCAN_SINGLE,
    HITSCAN_SPINUP,
    HITSCAN_SPREAD,
    // post-MVP, PLAN 7.6
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
