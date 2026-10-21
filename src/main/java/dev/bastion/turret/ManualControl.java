package dev.bastion.turret;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The player steering a turret through Overwatch and their latest input (PLAN "Overwatch Link"). Server only, never saved. */
public final class ManualControl {
    /** Input older than this (ticks) lets go of the trigger: a client that stopped sending must not keep the turret firing. */
    public static final int INPUT_TIMEOUT = 20;
    private final UUID player;
    private final String name;
    @Nullable
    private Vec3 point;
    private int entityId = -1;
    private boolean trigger;
    private long inputTime = Long.MIN_VALUE / 2;

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
        this.point = point;
        this.entityId = entityId;
        this.trigger = trigger;
        this.inputTime = gameTime;
    }

    @Nullable
    public Vec3 point() {
        return point;
    }

    public int entityId() {
        return entityId;
    }

    public boolean trigger(long gameTime) {
        return trigger && gameTime - inputTime <= INPUT_TIMEOUT;
    }
}
