package dev.bastion.weapon;

/**
 * Per-turret runtime state a weapon type keeps between ticks (server side; only the loaded tubes are saved): Machine
 * Gun spin, Precision Lock progress, which barrel fires next, Missile Launcher tubes. Owned by the turret, reset when
 * the weapon changes.
 */
public final class WeaponState {
    /** Machine Gun barrel spin, 0 (still) .. 1 (full rate). */
    public float spin;
    /** Spin last sent to clients (SpinSync is only sent on a significant change). */
    public float syncedSpin;
    /** Ticks aimed at {@link #lockTarget}; Precision Lock fires once per target when it reaches the threshold. */
    public int lockTicks;
    public int lockTarget = -1;
    public boolean lockSpent;
    /** Rotating muzzle index (Machine Gun: muzzle_0..3 by barrel position). */
    public int muzzle;

    // Missile Launcher: tubes load one at a time ahead of firing; a salvo empties them all.
    /** Loaded tubes, bit i = tube i (saved with the turret). */
    public int tubes;
    /** Tubes last sent to clients (RackSync). */
    public int syncedTubes;
    /** Ticks until the next empty tube is loaded. */
    public int reloadTimer;
    /** Copy of the turret's fire mode, so ready() can see it. */
    public boolean salvoMode;
    /** A salvo is under way: keep firing until the tubes are empty. */
    public boolean salvoActive;
    /** Entity ids a running salvo spreads its missiles over, and how many it has fired. */
    public int[] salvoTargets = new int[0];
    public int salvoFired;
    /** Whether the turret still holds ammo to load more tubes (a salvo fires a partial load once it runs out). */
    public boolean ammoLeft;
}
