package dev.bastion.turret;

/** Fire-cycle state machine, PLAN 4.4. Synced to clients so animations follow it. */
public enum TurretState {
    DISABLED,
    IDLE,
    ACQUIRING,
    AIMING,
    CHARGING,
    FIRING,
    COOLDOWN,
    OVERHEAT,
    NO_AMMO;

    /** Mid-engagement: the weapon cannot be pulled out (PLAN 4.3). */
    public boolean isEngaged() {
        return this == CHARGING || this == FIRING || this == COOLDOWN;
    }

    public static TurretState byId(int id) {
        TurretState[] values = values();
        return id >= 0 && id < values.length ? values[id] : DISABLED;
    }
}
