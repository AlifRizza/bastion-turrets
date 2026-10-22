package dev.bastion.turret;

import dev.bastion.weapon.types.MissileLauncherWeapon;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The player steering a turret through Overwatch and their latest input (PLAN "Overwatch Link"). Server only, never saved. */
public final class ManualControl {
    /** Input older than this (ticks) lets go of the trigger: a client that stopped sending must not keep the turret firing. */
    public static final int INPUT_TIMEOUT = 20;
    /** Presses of each key one input may carry (a launcher has 12 tubes; more is a client gone wrong); twice that wait at most. */
    public static final int MAX_PENDING = 12;
    /** Locks at most: one per Missile Launcher tube. */
    public static final int MAX_LOCKS = MissileLauncherWeapon.TUBES;
    /** A use press (undo the last lock) among the presses. */
    public static final int UNLOCK = Integer.MIN_VALUE;
    private final UUID player;
    private final String name;
    @Nullable
    private Vec3 point;
    private int entityId = -1;
    private boolean trigger;
    private long inputTime = Long.MIN_VALUE / 2;
    /**
     * Presses since the turret last took them, in arrival order: for an attack press (fire or lock) the creature its input
     * had under the crosshair (-1: none), UNLOCK for a use press. Kept per input, so presses that arrive together in a lag
     * spike keep their own targets.
     */
    private final IntArrayList presses = new IntArrayList();
    /** Missile Salvo: the creatures locked, in press order, one per missile (one may be locked many times). */
    private final IntArrayList locks = new IntArrayList();
    private boolean locksChanged;
    private boolean released;

    public ManualControl(UUID player, String name) {
        this.player = player;
        this.name = name;
    }

    public UUID player() {
        return player;
    }

    public String name() {
        return name;
    }

    /** One tick of input: the spot under the crosshair, the creature on it (-1: none), the trigger. */
    public void input(Vec3 point, int entityId, boolean trigger, long gameTime) {
        input(point, entityId, trigger, 0, 0, gameTime);
    }

    /** As above, plus attack and use presses since the last input (launchers fire or lock per press, PLAN "tembak per klik"). */
    public void input(Vec3 point, int entityId, boolean trigger, int clicks, int unlocks, long gameTime) {
        this.point = point;
        this.entityId = entityId;
        this.trigger = trigger;
        this.inputTime = gameTime;
        // A press and its undo in one client tick: the press first, so a mis-click undone at once leaves nothing.
        for (int i = 0; i < Math.min(clicks, MAX_PENDING); i++) press(entityId);
        for (int i = 0; i < Math.min(unlocks, MAX_PENDING); i++) press(UNLOCK);
    }

    private void press(int press) {
        if (presses.size() < 2 * MAX_PENDING) presses.add(press);
    }

    /** The presses since the last call, in arrival order (see {@link #presses}). */
    public int[] takePresses() {
        int[] taken = presses.toIntArray();
        presses.clear();
        return taken;
    }

    /** Puts back attack presses (their creatures) a shot could not use this tick (one shot per tick), for the next. */
    public void keepClicks(int[] creatures) {
        for (int creature : creatures) press(creature);
    }

    @Nullable
    public Vec3 point() {
        return point;
    }

    public int entityId() {
        return entityId;
    }

    /** The session ended (OverwatchSessions): the turret lets go on its next tick, even if it could not be reached then. */
    public void release() {
        released = true;
    }

    public boolean released() {
        return released;
    }

    public void addLock(int entityId) {
        if (locks.size() >= MAX_LOCKS) return;
        locks.add(entityId);
        locksChanged = true;
    }

    public void removeLastLock() {
        if (locks.isEmpty()) return;
        locks.removeInt(locks.size() - 1);
        locksChanged = true;
    }

    public void clearLocks() {
        if (locks.isEmpty()) return;
        locks.clear();
        locksChanged = true;
    }

    public int lockCount() {
        return locks.size();
    }

    /** The locked creatures' ids, in press order. */
    public int[] lockIds() {
        return locks.toIntArray();
    }

    /** True once after the locks changed (OverwatchSessions sends them to the controller's HUD). */
    public boolean takeLocksChanged() {
        boolean changed = locksChanged;
        locksChanged = false;
        return changed;
    }

    public boolean trigger(long gameTime) {
        return trigger && gameTime - inputTime <= INPUT_TIMEOUT;
    }
}
