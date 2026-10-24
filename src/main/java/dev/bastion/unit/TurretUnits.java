package dev.bastion.unit;

import dev.bastion.Bastion;
import dev.bastion.config.BastionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Every turret unit ticking on the server, for the per-turret caps and the per-dimension total (PLAN "Turret 3x3"). Units
 * are never saved, but a chunk unload does not remove them either: they linger in memory without ticking. So a unit
 * counts only while it ticks, and one that skipped ticks (its chunk came back) goes at its next tick: counts start from
 * 0 after a load, and no orphan resumes.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID)
public final class TurretUnits {
    /** Ticks without a tick after which a unit counts as gone. */
    private static final int STALE = 20;
    private static final Map<Entity, Long> SEEN = new IdentityHashMap<>();

    private TurretUnits() {
    }

    /** Call first in each server tick of a unit; false when it skipped ticks: it must go (without a blast). */
    public static boolean ticked(Entity unit) {
        long now = unit.level().getGameTime();
        Long last = SEEN.put(unit, now);
        return last == null || now - last <= STALE;
    }

    /** The server stops: never-saved units are not removed then either, and the map would keep that world (and server) alive. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SEEN.clear();
    }

    /** Units in the map (for the GameTests). */
    public static int tracked() {
        return SEEN.size();
    }

    /** The unit is gone (killed, discarded, burst). */
    public static void forget(Entity unit) {
        SEEN.remove(unit);
    }

    /** Places taken in the cap of the turret at {@code home}. */
    public static int count(Level level, BlockPos home) {
        return sum(level, home);
    }

    /** Places taken in {@code level}, all turrets together. */
    public static int total(Level level) {
        return sum(level, null);
    }

    /** How many more units the turret at {@code home} may send out under its cap and the dimension's total. */
    public static int room(Level level, BlockPos home, int cap) {
        return Math.max(0, Math.min(cap - count(level, home), BastionConfig.maxTurretUnits() - total(level)));
    }

    // ponytail: a scan of every loaded unit (at most maxTurretUnits per dimension, by default 96) per call
    private static int sum(Level level, @org.jetbrains.annotations.Nullable BlockPos home) {
        int sum = 0;
        long now = level.getGameTime();
        for (Iterator<Map.Entry<Entity, Long>> it = SEEN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Entity, Long> entry = it.next();
            Entity unit = entry.getKey();
            if (unit.isRemoved()) {
                it.remove();
                continue;
            }
            if (unit.level() != level || now - entry.getValue() > STALE) continue;
            if (unit instanceof TurretUnit u && (home == null || u.home().equals(home))) sum += u.unitWeight();
        }
        return sum;
    }
}
