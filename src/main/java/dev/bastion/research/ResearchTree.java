package dev.bastion.research;

import dev.bastion.research.ResearchNode.Tab;
import dev.bastion.research.ResearchNode.Tier;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The research tree (PLAN 8 "Riset & Turret Codex"): the user's turret diagram with the five approved changes, the
 * Workshop tab and one Mk1 → Mk2 → Mk3 column per module. A turret also requires the base of its size.
 */
public final class ResearchTree {
    private static final String SMALL = "turret_base", LARGE = "large_turret_base", FORTRESS = "fortress_turret_base";
    /** Module tab columns: the first five left to right at the bottom, the next five above them. */
    public static final List<String> MODULES = List.of("range_module", "damage_amplifier", "overclock", "rapid_cycler", "targeting_ai",
            "penetrator", "ammo_recycler", "coolant_loop", "choke_module", "autoloader");

    public static final List<ResearchNode> ALL = build();
    private static final Map<String, ResearchNode> BY_ID = new LinkedHashMap<>();

    static {
        for (ResearchNode node : ALL) BY_ID.put(node.id(), node);
    }

    private ResearchTree() {
    }

    private static List<ResearchNode> build() {
        List<ResearchNode> n = new ArrayList<>();
        // Turret tab: rows 0 early, 1 first 1x1 turrets, 2-3 later 1x1, 4 the 2x2 turrets, 5-6 the 3x3 ones.
        free(n, "cobblestone", Tab.TURRET, 1, 0, "cobblestone_turret");
        free(n, "snowball", Tab.TURRET, 2, 0, "snowball_repeater");
        free(n, "potato", Tab.TURRET, 4, 0, "potato_shotgun");
        free(n, "arrow_launcher", Tab.TURRET, 7, 0, "arrow_launcher");
        turret(n, "gun", 1, 1, "cobblestone", SMALL, Tier.R1, "gun_turret", "gun_barrel", "gun_receiver", "gun_mount", "kinetic_rounds");
        turret(n, "sniper", 1, 2, "gun", SMALL, Tier.R2, "sniper_turret", "sniper_barrel", "sniper_receiver", "sniper_scope_mount", "sniper_rounds");
        turret(n, "laser_rifle", 1, 3, "sniper", SMALL, Tier.R2, "laser_rifle_turret", "laser_focus_barrel", "laser_emitter_crystal",
                "laser_housing", "laser_cell", "empty_laser_cell", "charging_station");
        turret(n, "railgun", 1, 4, "laser_rifle", LARGE, Tier.R3, "railgun_turret", "railgun_rails", "railgun_receiver", "railgun_mount", "rail_slugs");
        turret(n, "ion_rifle", 0, 3, "gun", SMALL, Tier.R2, "ion_rifle_turret", "ion_barrel", "ion_capacitor", "ion_mount");
        turret(n, "tesla", 0, 4, "ion_rifle", LARGE, Tier.R3, "tesla_turret", "tesla_coil", "tesla_crown", "tesla_capacitor_bank");
        turret(n, "machine_gun", 2, 1, "snowball", SMALL, Tier.R1, "machine_gun_turret", "machine_gun_barrels", "machine_gun_receiver",
                "machine_gun_mount", "kinetic_rounds");
        turret(n, "plasma_repeater", 2, 3, "machine_gun", SMALL, Tier.R2, "plasma_repeater_turret", "plasma_barrel", "plasma_chamber",
                "repeater_mount", "plasma_cell", "empty_plasma_cell", "charging_station");
        turret(n, "plasma_cannon", 2, 4, "plasma_repeater", LARGE, Tier.R3, "plasma_cannon_turret", "plasma_cannon_core", "plasma_cannon_barrel",
                "plasma_cannon_mount", "plasma_cell", "empty_plasma_cell");
        turret(n, "shotgun", 4, 1, "potato", SMALL, Tier.R1, "shotgun_turret", "shotgun_barrels", "shotgun_receiver", "shotgun_mount", "scatter_shells");
        turret(n, "flamethrower", 3, 2, "shotgun", SMALL, Tier.R2, "flamethrower_turret", "flamethrower_nozzle", "flamethrower_fuel_tanks",
                "flamethrower_housing", "fuel_canister");
        turret(n, "repulsor", 5, 2, "shotgun", SMALL, Tier.R2, "repulsor_turret", "repulsor_emitter", "repulsor_coil", "repulsor_mount");
        turret(n, "repulsor_dome", 5, 4, "repulsor", LARGE, Tier.R3, "repulsor_dome_turret", "dome_core", "dome_pylons", "dome_mount");
        turret(n, "rocket_launcher", 6, 1, "arrow_launcher", SMALL, Tier.R1, "rocket_launcher_turret", "rocket_pod", "rocket_pod_housing",
                "rocket_launcher_mount", "rockets");
        turret(n, "missile_launcher", 6, 4, "rocket_launcher", LARGE, Tier.R3, "missile_launcher_turret", "missile_pod", "missile_guidance_core",
                "missile_launcher_mount", "missiles");
        turret(n, "javelin", 6, 5, "missile_launcher", FORTRESS, Tier.R4, "javelin_turret", "javelin_silo", "javelin_radar", "javelin_mount", "javelin_missile");
        turret(n, "mortar", 7, 4, "arrow_launcher", LARGE, Tier.R3, "mortar_turret", "mortar_barrel", "mortar_housing", "mortar_ring", "mortar_shells");
        turret(n, "brood_cannon", 7, 5, "mortar", FORTRESS, Tier.R4, "brood_cannon_turret", "brood_barrel", "brood_chamber", "brood_mount", "brood_capsule");
        turret(n, "hive_carrier", 7, 6, "brood_cannon", FORTRESS, Tier.R4, "hive_carrier_turret", "hive_hangar", "hive_relay", "hive_bays", "hive_drone");

        // Workshop tab.
        free(n, "turret_codex", Tab.WORKSHOP, 0, 0, "turret_codex");
        free(n, "makeshift_turret_base", Tab.WORKSHOP, 1, 0, "makeshift_turret_base");
        workshop(n, "workstations", 1, 1, "makeshift_turret_base", Tier.R1, "part_workstation", "part_assembler", "ammo_workstation", "module_workstation");
        workshop(n, SMALL, 1, 2, "workstations", Tier.R1, "turret_base", "turret_base_plating", "turret_base_housing", "turret_base_turntable",
                "repair_kit", "turret_configurator");
        workshop(n, LARGE, 0, 3, SMALL, Tier.R2, "large_turret_base", "large_base_plating", "large_base_housing", "large_base_turntable");
        workshop(n, FORTRESS, 0, 4, LARGE, Tier.R3, "fortress_turret_base", "fortress_frame");
        workshop(n, "tier_upgrade_kit_t2", 1, 3, SMALL, Tier.R2, "tier_upgrade_kit_t2");
        workshop(n, "tier_upgrade_kit_t3", 1, 4, "tier_upgrade_kit_t2", Tier.R3, "tier_upgrade_kit_t3");
        workshop(n, "feed_hub", 2, 3, SMALL, Tier.R2, "feed_hub");
        workshop(n, "control_hub", 3, 3, SMALL, Tier.R3, "hub_interface", "hub_casing", "hub_power_port", "satellite_uplink_t1",
                "satellite_uplink_t2", "satellite_uplink_t3", "hub_connector");
        workshop(n, "overwatch_link", 3, 4, "control_hub", Tier.R3, "overwatch_link");

        // Module tab: a column per module (Mk1 → Mk2 → Mk3 upward), in two bands of five with an empty row between, so all
        // ten fit the Codex at GUI scale 3 without panning; a weapon-specific module waits for its weapon.
        for (int i = 0; i < MODULES.size(); i++) {
            String m = MODULES.get(i);
            int col = i % 5, row = i / 5 * 4;
            String needs = switch (m) {
                case "choke_module" -> "shotgun";
                case "autoloader" -> "rocket_launcher";
                default -> "workstations";
            };
            n.add(new ResearchNode(m, Tab.MODULE, col, row, List.of(), List.of(needs), List.of(m), Tier.R1));
            n.add(new ResearchNode(m + "_mk2", Tab.MODULE, col, row + 1, List.of(m), List.of(), List.of(m + "_mk2"), Tier.R2));
            n.add(new ResearchNode(m + "_mk3", Tab.MODULE, col, row + 2, List.of(m + "_mk2"), List.of(), List.of(m + "_mk3"), Tier.R3));
        }
        return List.copyOf(n);
    }

    private static void free(List<ResearchNode> n, String id, Tab tab, int col, int row, String item) {
        n.add(new ResearchNode(id, tab, col, row, List.of(), List.of(), List.of(item), Tier.FREE));
    }

    private static void turret(List<ResearchNode> n, String id, int col, int row, String parent, String base, Tier tier, String... unlocks) {
        n.add(new ResearchNode(id, Tab.TURRET, col, row, List.of(parent), List.of(base), List.of(unlocks), tier));
    }

    private static void workshop(List<ResearchNode> n, String id, int col, int row, String parent, Tier tier, String... unlocks) {
        n.add(new ResearchNode(id, Tab.WORKSHOP, col, row, List.of(parent), List.of(), List.of(unlocks), tier));
    }

    @Nullable
    public static ResearchNode get(String id) {
        return BY_ID.get(id);
    }

    /** The nodes that unlock {@code item} (empty: research never locks it). */
    public static List<ResearchNode> unlocking(Item item) {
        return ByItem.MAP.getOrDefault(item, List.of());
    }

    public static List<ResearchNode> children(String id) {
        return ALL.stream().filter(node -> node.parents().contains(id)).toList();
    }

    public static List<ResearchNode> tab(Tab tab) {
        return ALL.stream().filter(node -> node.tab() == tab).toList();
    }

    /** Built on first use, after the item registry is filled (class initialisation is thread-safe). */
    private static final class ByItem {
        static final Map<Item, List<ResearchNode>> MAP = new HashMap<>();

        static {
            for (ResearchNode node : ALL) for (Item item : node.unlockItems()) MAP.computeIfAbsent(item, k -> new ArrayList<>()).add(node);
        }
    }
}
