package dev.bastion.turret;

/**
 * Base tiers, PLAN 4.1. Slot counts are structural (they fix the inventory layout);
 * HP, regen and turn speed join as config values with the tier system in Fase 7.
 */
public enum TurretTier {
    T1(3, 1),
    T2(6, 2),
    T3(9, 4);

    public static final int MAX_AMMO_SLOTS = 9;
    public static final int MAX_MODIFIER_SLOTS = 4;

    public final int ammoSlots;
    public final int modifierSlots;

    TurretTier(int ammoSlots, int modifierSlots) {
        this.ammoSlots = ammoSlots;
        this.modifierSlots = modifierSlots;
    }

    /** Unknown or missing names fall back to T1, so a damaged save never crashes the chunk. */
    public static TurretTier byName(String name) {
        for (TurretTier tier : values()) {
            if (tier.name().equals(name)) return tier;
        }
        return T1;
    }
}
