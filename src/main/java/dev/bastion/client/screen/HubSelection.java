package dev.bastion.client.screen;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What the Control Hub screen keeps while the player visits a turret's GUI through it and comes back (PLAN: selection,
 * tab, zoom and map position last for that trip, not past closing the hub screen).
 */
public final class HubSelection {
    /** Set by the turret GUI's "◀ Map" button: the next hub screen for the same hub keeps this state. */
    public static boolean returning;
    static final Set<BlockPos> selected = new LinkedHashSet<>();
    static BlockPos hub = BlockPos.ZERO;
    static int tab, zoom, sort, listScroll;
    /** Map view: blocks from the area's north-west corner to the view's. */
    static double viewX, viewZ;

    private HubSelection() {
    }

    /** Selects these turrets (dev scenes). */
    public static void select(Collection<BlockPos> turrets) {
        selected.addAll(turrets);
    }

    /** A hub screen opens: coming back to the same hub keeps everything, anything else starts fresh. */
    static void open(BlockPos hubPos) {
        boolean keep = returning && hubPos.equals(hub);
        returning = false;
        if (keep) return;
        hub = hubPos;
        selected.clear();
        tab = zoom = sort = listScroll = 0;
        viewX = viewZ = 0;
    }
}
