package dev.bastion.workstation;

import java.util.Locale;

/**
 * The crafting stations of PLAN Fase 9 and their footprints (width to the right of the front, depth away from it,
 * height up, in blocks). Instant stations also craft straight from the GUI for the player; every station processes
 * over time from its input slots, which is what automation uses.
 */
public enum WorkstationType {
    PART_WORKSTATION(2, 1, 2, false),
    PART_ASSEMBLER(2, 2, 2, false),
    MODULE_WORKSTATION(1, 1, 2, true),
    AMMO_WORKSTATION(1, 1, 2, true),
    /** Charges ammo that runs on stored energy (Laser Cells now, Plasma Cells later). */
    CHARGING_STATION(1, 1, 1, false);

    public final int width, depth, height;
    public final boolean instant;

    WorkstationType(int width, int depth, int height, boolean instant) {
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.instant = instant;
    }

    /** Registry and file name: part_workstation, part_assembler, module_workstation, ammo_workstation. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int blocks() {
        return width * depth * height;
    }

    public static WorkstationType byId(String id) {
        for (WorkstationType type : values()) {
            if (type.id().equals(id)) return type;
        }
        throw new IllegalArgumentException("unknown workstation: " + id);
    }
}
