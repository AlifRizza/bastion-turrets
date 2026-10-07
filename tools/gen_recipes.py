#!/usr/bin/env python3
"""Survival recipes (PLAN Fase 9): the crafting table only makes the workstations; everything else is a
bastion:workstation recipe. Balance lives here (time in ticks, energy in FE per craft); re-run after editing:

    python3 tools/gen_recipes.py

Writes data/bastion/recipes/workstation/<station>/<item>.json and the four workstation crafting recipes, and removes the
old crafting-table recipes of turret items.
"""
import json
import shutil
from pathlib import Path

DATA = Path(__file__).resolve().parent.parent / "src/main/resources/data/bastion/recipes"


def item(i):
    return i if ":" in i or i.startswith("#") else "minecraft:" + i


def ingredient(name, count):
    name = item(name)
    entry = {"tag": name[1:]} if name.startswith("#") else {"item": name}
    entry["count"] = count
    return entry


def recipe(station, category, result, ingredients, time, energy, count=1):
    return {"type": "bastion:workstation", "station": station, "category": item(category),
            "ingredients": [ingredient(n, c) for n, c in ingredients.items()],
            "result": {"item": item(result), "count": count} if count > 1 else {"item": item(result)},
            "time": time, "energy": energy}


B = "bastion:"
SMALL_PART, SPECIAL_PART, LARGE_PART = (100, 3000), (140, 5000), (160, 6000)

# Part Workstation: three parts per base and weapon (the old crafting recipe's materials, split up).
PARTS = {
    "turret_base": {"turret_base_plating": ({"smooth_stone": 3, "iron_ingot": 1}, SMALL_PART),
                    "turret_base_housing": ({"iron_ingot": 2, "redstone": 1}, SMALL_PART),
                    "turret_base_turntable": ({"iron_ingot": 1, "observer": 1}, SMALL_PART)},
    "large_turret_base": {"large_base_plating": ({"iron_block": 2, "smooth_stone": 4}, LARGE_PART),
                          "large_base_housing": ({"iron_block": 2, "redstone_block": 1}, LARGE_PART),
                          "large_base_turntable": ({"iron_ingot": 4, "copper_ingot": 2, "observer": 1}, LARGE_PART)},
    "gun_turret": {"gun_barrel": ({"iron_ingot": 3, "copper_ingot": 1}, SMALL_PART),
                   "gun_receiver": ({"iron_ingot": 2, "dispenser": 1, "redstone": 1}, SMALL_PART),
                   "gun_mount": ({"iron_ingot": 1, "observer": 1}, SMALL_PART)},
    "machine_gun_turret": {"machine_gun_barrels": ({"iron_ingot": 3, "copper_ingot": 2}, SMALL_PART),
                           "machine_gun_receiver": ({"iron_ingot": 2, "dispenser": 2, "redstone": 1}, SMALL_PART),
                           "machine_gun_mount": ({"iron_ingot": 1, "observer": 1}, SMALL_PART)},
    "shotgun_turret": {"shotgun_barrels": ({"iron_ingot": 3, "gunpowder": 1}, SMALL_PART),
                       "shotgun_receiver": ({"iron_ingot": 1, "dispenser": 1, "redstone": 1}, SMALL_PART),
                       "shotgun_mount": ({"iron_ingot": 1, "observer": 1}, SMALL_PART)},
    "sniper_turret": {"sniper_barrel": ({"iron_ingot": 3, "copper_ingot": 1}, SMALL_PART),
                      "sniper_receiver": ({"iron_ingot": 1, "dispenser": 1, "redstone": 1}, SMALL_PART),
                      "sniper_scope_mount": ({"iron_ingot": 1, "observer": 1, "spyglass": 1}, SPECIAL_PART)},
    "rocket_launcher_turret": {"rocket_pod": ({"iron_ingot": 2, "dispenser": 2, "firework_rocket": 1}, SPECIAL_PART),
                               "rocket_pod_housing": ({"iron_ingot": 2, "redstone": 1}, SMALL_PART),
                               "rocket_launcher_mount": ({"iron_ingot": 1, "observer": 1}, SMALL_PART)},
    "missile_launcher_turret": {"missile_pod": ({"iron_ingot": 4, "dispenser": 2, "firework_rocket": 1}, LARGE_PART),
                                "missile_guidance_core": ({"diamond": 1, "observer": 1, "redstone_block": 1}, LARGE_PART),
                                "missile_launcher_mount": ({"iron_ingot": 4, "copper_ingot": 2}, LARGE_PART)},
    "tesla_turret": {"tesla_coil": ({"copper_block": 2, "lightning_rod": 1}, LARGE_PART),
                     "tesla_crown": ({"lightning_rod": 1, "iron_ingot": 2}, LARGE_PART),
                     "tesla_capacitor_bank": ({"diamond": 1, "redstone_block": 1, "observer": 1}, LARGE_PART)},
    "flamethrower_turret": {"flamethrower_nozzle": ({"iron_ingot": 2, "flint_and_steel": 1}, SPECIAL_PART),
                            "flamethrower_fuel_tanks": ({"iron_ingot": 3, "copper_ingot": 1}, SMALL_PART),
                            "flamethrower_housing": ({"iron_ingot": 1, "dispenser": 1, "observer": 1, "redstone": 1}, SMALL_PART)},
    "laser_rifle_turret": {"laser_focus_barrel": ({"iron_ingot": 3, "glass": 2}, SMALL_PART),
                           "laser_emitter_crystal": ({"amethyst_shard": 1, "glass": 1, "redstone": 1}, SPECIAL_PART),
                           "laser_housing": ({"iron_ingot": 1, "redstone_block": 1, "observer": 1, "dispenser": 1}, SMALL_PART)},
    "railgun_turret": {"railgun_rails": ({"iron_block": 2, "copper_block": 2, "lightning_rod": 1}, LARGE_PART),
                       "railgun_receiver": ({"diamond": 2, "redstone_block": 1, "observer": 1, "dispenser": 1}, LARGE_PART),
                       "railgun_mount": ({"iron_ingot": 4, "copper_ingot": 2, "observer": 1}, LARGE_PART)},
    "mortar_turret": {"mortar_barrel": ({"iron_block": 2, "blast_furnace": 1}, LARGE_PART),
                      "mortar_housing": ({"iron_block": 2, "smooth_stone": 4, "redstone": 2}, LARGE_PART),
                      "mortar_ring": ({"iron_ingot": 4, "copper_ingot": 2, "observer": 1}, LARGE_PART)},
}
LARGE = {"large_turret_base", "missile_launcher_turret", "tesla_turret", "railgun_turret", "mortar_turret"}
PART_COUNT = {"missile_pod": 2}  # the launcher has two pods

# Module Workstation: the old crafting recipes as ingredient counts; ammo likewise at the Ammo Workstation.
MODULE = {"range_module": "spyglass", "rapid_cycler": "clock", "damage_amplifier": "blaze_powder", "penetrator": "amethyst_shard",
          "ammo_recycler": "hopper", "coolant_loop": "packed_ice", "overclock": "redstone_block", "targeting_ai": "ender_eye"}
TOOLS = {"repair_kit": ({"iron_ingot": 2, "copper_ingot": 1, "redstone": 1}, 60, 1000, 1),
         "tier_upgrade_kit_t2": ({"gold_ingot": 4, "iron_ingot": 4, "diamond": 1}, 160, 6000, 1),
         "tier_upgrade_kit_t3": ({"diamond": 4, "netherite_scrap": 4, B + "tier_upgrade_kit_t2": 1}, 240, 12000, 1),
         "turret_configurator": ({"iron_ingot": 6, "redstone": 1, "glass_pane": 1, "comparator": 1}, 80, 1500, 1),
         "feed_hub": ({"iron_block": 1, "hopper": 2, "copper_ingot": 4, "redstone": 2}, 100, 3000, 1)}
AMMO = {  # result: (ingredients, time, energy, count, tab)
    "kinetic_rounds": ({"copper_ingot": 1, "gunpowder": 1, "iron_nugget": 1}, 40, 400, 16, "kinetic_rounds"),
    "scatter_shells": ({"paper": 1, "gunpowder": 1, "copper_ingot": 1}, 40, 400, 8, "kinetic_rounds"),
    "sniper_rounds": ({"copper_ingot": 1, "gunpowder": 1, "iron_ingot": 1}, 40, 500, 8, "kinetic_rounds"),
    "rockets": ({"iron_ingot": 3, "tnt": 1, "firework_rocket": 1}, 60, 800, 4, "rockets"),
    "missiles": ({"iron_nugget": 3, "gunpowder": 1, "firework_rocket": 1}, 60, 800, 6, "rockets"),
    "fuel_canister": ({"iron_ingot": 3, "iron_nugget": 1, "#minecraft:coals": 1}, 40, 300, 3, "laser_cell"),
    "empty_laser_cell": ({"iron_ingot": 2, "glass": 1, "redstone": 1}, 40, 400, 4, "laser_cell"),
    "rail_slugs": ({"iron_ingot": 2, "copper_ingot": 1, "redstone": 1}, 60, 800, 4, "kinetic_rounds"),
    "mortar_shells": ({"iron_ingot": 2, "gunpowder": 2, "#minecraft:coals": 1}, 60, 600, 4, "rockets"),
}
# Charging Station: ammo that stores energy (Plasma Cells will go here too).
CHARGE = {"laser_cell": ({B + "empty_laser_cell": 1}, 60, 3000, 1)}

WORKSTATIONS = {  # crafting table: pattern, key
    "part_workstation": (["PSP", "RCR", "III"], {"P": "piston", "S": "smithing_table", "R": "redstone", "C": "crafting_table", "I": "iron_ingot"}),
    "part_assembler": (["SWS", "RDR", "BIB"], {"S": "sticky_piston", "W": B + "part_workstation", "R": "redstone", "D": "diamond",
                                               "B": "iron_block", "I": "iron_ingot"}),
    "module_workstation": (["GQG", "RCR", "III"], {"G": "gold_ingot", "Q": "quartz", "R": "redstone", "C": "crafting_table", "I": "iron_ingot"}),
    "ammo_workstation": (["IPI", "GCG", "III"], {"P": "piston", "G": "gunpowder", "C": "crafting_table", "I": "iron_ingot"}),
    "charging_station": (["ILI", "RCR", "IBI"], {"L": "lightning_rod", "R": "redstone", "C": "copper_block", "B": "redstone_block",
                                                 "I": "iron_ingot"}),
}

# Crafting-table recipes that the workstations replace.
OLD = ["turret_base", "large_turret_base", "turret_base_from_damaged", "large_turret_base_from_damaged", "gun_turret",
       "machine_gun_turret", "shotgun_turret", "sniper_turret", "rocket_launcher_turret", "missile_launcher_turret", "tesla_turret",
       "flamethrower_turret", "laser_rifle_turret", *MODULE, "choke_module", *TOOLS, "kinetic_rounds", "scatter_shells",
       "sniper_rounds", "rockets", "missiles", "fuel_canister"]


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    out = DATA / "workstation"
    shutil.rmtree(out, ignore_errors=True)
    n = 0
    for target, parts in PARTS.items():
        for part, (ingredients, (time, energy)) in parts.items():
            write(out / "part_workstation" / f"{part}.json", recipe("part_workstation", B + target, B + part, ingredients, time, energy))
            n += 1
        tab = "turret_base" if target.endswith("base") else "missile_launcher_turret" if target in LARGE else "gun_turret"
        time, energy = (300, 15000) if target in LARGE else (160, 5000) if target == "turret_base" else (200, 8000)
        write(out / "part_assembler" / f"{target}.json", recipe("part_assembler", B + tab, B + target,
                                                                {B + p: PART_COUNT.get(p, 1) for p in parts}, time, energy))
        n += 1
    write(out / "part_assembler" / "turret_base_from_damaged.json", recipe("part_assembler", B + "turret_base", B + "turret_base",
                                                                         {B + "damaged_turret_base": 1, B + "repair_kit": 1}, 160, 4000))
    write(out / "part_assembler" / "large_turret_base_from_damaged.json", recipe("part_assembler", B + "turret_base", B + "large_turret_base",
                                                                               {B + "damaged_large_turret_base": 1, B + "repair_kit": 2}, 240, 8000))
    for module, core in MODULE.items():
        write(out / "module_workstation" / f"{module}.json", recipe("module_workstation", B + "range_module", B + module,
                                                                  {"iron_nugget": 4, "redstone": 2, "copper_ingot": 2, core: 1}, 80, 2000))
    write(out / "module_workstation" / "choke_module.json", recipe("module_workstation", B + "choke_module", B + "choke_module",
                                                                 {"iron_nugget": 4, "redstone": 2, "copper_ingot": 2, "hopper": 1}, 80, 2000))
    write(out / "module_workstation" / "autoloader.json", recipe("module_workstation", B + "choke_module", B + "autoloader",
                                                               {"iron_nugget": 4, "redstone": 2, "copper_ingot": 2, "piston": 1}, 80, 2000))
    for tool, (ingredients, time, energy, count) in TOOLS.items():
        write(out / "module_workstation" / f"{tool}.json", recipe("module_workstation", B + "repair_kit", B + tool, ingredients, time, energy, count))
    for ammo, (ingredients, time, energy, count, tab) in AMMO.items():
        write(out / "ammo_workstation" / f"{ammo}.json", recipe("ammo_workstation", B + tab, B + ammo, ingredients, time, energy, count))
    for cell, (ingredients, time, energy, count) in CHARGE.items():
        write(out / "charging_station" / f"{cell}.json", recipe("charging_station", B + cell, B + cell, ingredients, time, energy, count))
    n += 2 + len(MODULE) + 1 + len(TOOLS) + len(AMMO) + len(CHARGE)
    for station, (pattern, key) in WORKSTATIONS.items():
        write(DATA / f"{station}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
                                        "key": {k: {"item": item(v)} for k, v in key.items()}, "result": {"item": B + station, "count": 1}})
    removed = [name for name in OLD if (DATA / f"{name}.json").exists()]
    for name in removed:
        (DATA / f"{name}.json").unlink()
    print(f"{n} workstation recipes, {len(WORKSTATIONS)} workstation crafting recipes, removed {len(removed)} old recipes")


if __name__ == "__main__":
    main()
