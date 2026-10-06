"""Workstation models (PLAN Fase 9), same kit and look as the turrets: gunmetal panels, dark frames, one cyan glow.
Origin = the centre of the footprint on the floor, front = north (-Z), units px. Animated bones are named for
assets/bastion/animations/<station>.animation.json; tool_N are locators where sparks fly (WorkstationEffects), work_0
where the item being made sits (WorkstationRenderer)."""
import sys
from kit import Kit, GEO


def part_workstation(path):
    """2 wide x 1 deep x 2 tall machining bench: drawer cabinet and shelf under the top, a back panel with a tool board
    and a holo screen, a gantry over the bench whose carriage runs a spinning spindle over the work."""
    k = Kit(128)
    k.bone("root")
    legs = [k.box("leg", x0, 0, z0, x0 + 2.5, 13, z0 + 2.5) for x0 in (-16, 13.5) for z0 in (-8, 5.5)]
    k.bone("frame", "root", cubes=legs + [
        k.box("shelf", -2, 3, -7, 15, 4, 7),
        k.box("lip", -16, 12, -8.4, 16, 15.5, -8),
        k.box("upright", -16, 15.5, 1, -13.5, 31.5, 5), k.box("upright", 13.5, 15.5, 1, 16, 31.5, 5),
        k.box("beam", -16, 29, 1, 16, 31.5, 5)])
    k.bone("body", "root", cubes=[
        k.box("cabinet", -15, 1, -7, -2, 12, 7),
        k.box("drawer", -14, 2, -7.4, -3, 5, -7), k.box("drawer", -14, 6, -7.4, -3, 9, -7), k.box("drawer", -14, 10, -7.4, -3, 12, -7),
        k.box("backpanel", -13.5, 15.5, 5, 13.5, 29, 8)])
    k.bone("top", "root", cubes=[k.box("tabletop", -16, 13, -8, 16, 15.5, 8)])
    k.bone("tools", "body", cubes=[
        k.box("wrench", -11, 19, 4.4, -10, 26, 5), k.box("wrench", -8.5, 20, 4.4, -7.5, 26, 5), k.box("plier", -6, 21, 4.4, -4.5, 26, 5),
        k.box("vise", 8.5, 15.5, -6.5, 14, 18.5, -2), k.box("vise_jaw", 8.5, 18.5, -5, 14, 19.5, -3.5)])
    k.bone("glow_drawers", "body", cubes=[k.box("handle", -10, 3.2, -7.5, -7, 3.8, -7.4), k.box("handle", -10, 7.2, -7.5, -7, 7.8, -7.4),
                                          k.box("handle", -10, 10.7, -7.5, -7, 11.3, -7.4)])
    k.bone("screen", "body", pivot=(10, 23, 4.5), cubes=[k.box("monitor", 6, 18.5, 3.6, 13, 27, 5)])
    k.bone("glow_screen", "screen", pivot=(9.5, 22.75, 3.5), cubes=[k.box("display", 6.6, 19.1, 3.5, 12.4, 26.4, 3.6)])
    k.bone("carriage", "root", pivot=(0, 28, 3), cubes=[k.box("carriage", -2.5, 26.5, 0.5, 2.5, 32, 5.5)])
    k.bone("spindle", "carriage", pivot=(0, 24, 3), cubes=[k.box("spindle", -1, 22, 2, 1, 26.5, 4), k.box("bit", -0.4, 21, 2.6, 0.4, 22, 3.4)])
    k.bone("glow_spindle", "spindle", pivot=(0, 25, 3), cubes=[k.box("ring", -1.2, 24.6, 1.8, 1.2, 25.2, 4.2)])
    k.bone("tool_0", "spindle", pivot=(0, 21, 3))
    k.bone("work_0", "root", pivot=(0, 18.5, 3))
    k.write("part_workstation", path, bounds=(3, 3), offset=(0, 1, 0))


def part_assembler(path):
    """2x2x2 robotic assembly cell: a deck with a turntable, four corner pillars and a top frame, a scanner ring that
    sweeps down over the work, and two welding arms that bend over it from the sides."""
    k = Kit(256)
    k.bone("root")
    k.bone("base", "root", cubes=k.chamfer("base", 16, 3, 0, 4) + k.octagon("deckring", 10.5, 4, 5))
    pillars = [k.box("pillar", x0, 4, z0, x0 + 3, 30, z0 + 3) for x0 in (-15.5, 12.5) for z0 in (-15.5, 12.5)]
    k.bone("frame", "root", cubes=pillars + [
        k.box("topbeam", -15.5, 28, -15.5, 15.5, 31, -12.5), k.box("topbeam", -15.5, 28, 12.5, 15.5, 31, 15.5),
        k.box("topbeamx", -15.5, 28, -12.5, -12.5, 31, 12.5), k.box("topbeamx", 12.5, 28, -12.5, 15.5, 31, 12.5),
        k.box("crossbeam", -12.5, 28.5, -1, 12.5, 30.5, 1)])
    k.bone("glow_frame", "frame", cubes=[k.box("strip", -14, 29, -15.6, 14, 29.6, -15.5), k.box("strip", -14, 29, 15.5, 14, 29.6, 15.6)])
    k.bone("turntable", "root", pivot=(0, 5, 0), cubes=k.octagon("turntable", 8, 5, 6.5))
    k.bone("glow_turntable", "turntable", pivot=(0, 6, 0), cubes=k.octagon("tablering", 8.25, 5.6, 6.0))
    k.bone("scanner", "root", pivot=(0, 25, 0), cubes=k.octagon("scanner", 5, 24, 26) + [k.box("rod", -0.8, 26, -0.8, 0.8, 28.5, 0.8)])
    k.bone("glow_scanner", "scanner", pivot=(0, 24, 0), cubes=k.octagon("scanring", 4.6, 23.6, 24.0))
    for side, name in ((-1, "l"), (1, "r")):
        x = side * 12
        x0, x1 = sorted((x - 1.5, x + 1.5))
        k.bone(f"mount_{name}", "root", cubes=[k.box("armcolumn", x0, 4, -1.5, x1, 20, 1.5), k.box("armcap", x0 - 0.3, 19, -1.8, x1 + 0.3, 21.5, 1.8)])
        u0, u1 = sorted((x, side * 6))
        k.bone(f"arm_{name}", f"mount_{name}", pivot=(x, 20.5, 0), cubes=[k.box("upperarm", u0, 19.5, -1, u1, 21.5, 1)])
        e0, e1 = sorted((side * 5.5, side * 7.5))
        k.bone(f"fore_{name}", f"arm_{name}", pivot=(side * 6.5, 20.5, 0), cubes=[
            k.box("elbow", e0, 19, -1.4, e1, 22, 1.4), k.box("forearm", side * 6.5 - 0.6, 15, -0.6, side * 6.5 + 0.6, 19, 0.6),
            k.box("torch", side * 6.5 - 0.9, 14, -0.9, side * 6.5 + 0.9, 15, 0.9)])
        k.bone(f"glow_torch_{name}", f"fore_{name}", pivot=(side * 6.5, 13.7, 0),
               cubes=[k.box("tip", side * 6.5 - 0.4, 13.4, -0.4, side * 6.5 + 0.4, 14, 0.4)])
        k.bone(f"tool_{0 if side < 0 else 1}", f"fore_{name}", pivot=(side * 6.5, 13.4, 0))
    k.bone("console", "root", pivot=(-11, 6, -13), cubes=[k.box("console", -14, 4, -15.5, -8, 9, -12.5)])
    k.bone("glow_console", "console", pivot=(-11, 7, -15.6), cubes=[k.box("panel", -13.4, 5.4, -15.6, -8.6, 8.4, -15.5)])
    k.bone("work_0", "root", pivot=(0, 13, 0))
    k.write("part_assembler", path, bounds=(3, 3), offset=(0, 1, 0))


def module_workstation(path):
    """1x1x2 circuit printer: a cabinet with a desk, a back tower with a holo screen, and a gantry whose print head
    travels over the board on the bed."""
    k = Kit(128)
    k.bone("root")
    k.bone("body", "root", cubes=k.chamfer("cabinet", 8, 1.5, 0, 13) + [k.box("tower", -8, 15, 4, 8, 31, 8)])
    k.bone("frame", "root", cubes=[
        k.box("door", -6, 2, -8.2, 6, 11, -8), k.box("rail", -8, 15, -7, -6.5, 21, 4), k.box("rail", 6.5, 15, -7, 8, 21, 4),
        k.box("screenframe", -6.5, 22.5, 3.6, 6.5, 30.5, 4)])
    k.bone("desk", "root", cubes=[k.box("desk", -8, 13, -8, 8, 15, 8), k.box("bed", -5, 15, -6, 5, 15.5, 2)])
    k.bone("glow_body", "body", cubes=[k.box("doorstrip", -5, 9.5, -8.3, 5, 10, -8.2)])
    k.bone("glow_screen", "frame", pivot=(0, 26.5, 3.5), cubes=[k.box("display", -6, 23, 3.5, 6, 30, 3.6)])
    k.bone("gantry", "root", pivot=(0, 20, -1), cubes=[k.box("gantry", -6.5, 19, -2, 6.5, 20.5, 0)])
    k.bone("head", "gantry", pivot=(0, 18, -1), cubes=[k.box("head", -1.5, 16.5, -2.5, 1.5, 19, 0.5)])
    k.bone("glow_head", "head", pivot=(0, 16.2, -1), cubes=[k.box("nozzle", -0.5, 16, -1.5, 0.5, 16.5, -0.5)])
    k.bone("tool_0", "head", pivot=(0, 16, -1))
    k.bone("work_0", "root", pivot=(0, 16.5, -2))
    k.write("module_workstation", path, bounds=(2, 3), offset=(0, 1, 0))


def ammo_workstation(path):
    """1x1x2 ammo press: a cabinet with vents and an output chute, a press frame of four columns under a crown, and a
    ram with a die that slams down on the anvil plate."""
    k = Kit(128)
    k.bone("root")
    k.bone("body", "root", cubes=k.chamfer("cabinet", 8, 1.5, 0, 12) + [k.box("crown", -8, 26, -7, 8, 30, 7)])
    cols = [k.box("column", x0, 14, z0, x0 + 2, 26, z0 + 2) for x0 in (-7, 5) for z0 in (-6, 4)]
    k.bone("frame", "root", cubes=cols + [
        k.box("chute", -2.5, 9, -8.6, 2.5, 12, -7), k.box("hopper", -2, 14, 5, 2, 21, 7),
        k.box("cylinder", -3.5, 30, -3.5, 3.5, 32, 3.5)])
    k.bone("table", "root", cubes=[k.box("table", -7, 12, -7, 7, 14, 7), k.box("anvil", -3.5, 14, -3.5, 3.5, 15, 3.5)])
    k.bone("vents", "body", cubes=[k.box("vent", -8.1, 3, -4, -8, 9, 4), k.box("vent", 8, 3, -4, 8.1, 9, 4)])
    k.bone("glow_body", "body", cubes=[k.box("chutestrip", -2, 10, -8.7, 2, 10.5, -8.6), k.box("crownstrip", -6, 27.5, -7.1, 6, 28, -7)])
    k.bone("ram", "root", pivot=(0, 22, 0), cubes=k.octagon("rod", 1.2, 19, 26) + [k.box("die", -3, 17.5, -3, 3, 19, 3)])
    k.bone("glow_ram", "ram", pivot=(0, 22.25, 0), cubes=k.octagon("ramring", 1.4, 22, 22.5))
    k.bone("tool_0", "ram", pivot=(0, 17.5, 0))
    k.bone("work_0", "root", pivot=(0, 15.8, 0))
    k.write("ammo_workstation", path, bounds=(2, 3), offset=(0, 1, 0))



def charging_station(path):
    """1x1x1 charging dock: an armoured base with a glowing ring, a cradle in the middle where the cell sits, four coil
    pylons around it whose tips (tool_0..3) throw arcs into the cell while it charges."""
    k = Kit(128)
    k.bone("root")
    k.bone("body", "root", cubes=k.chamfer("base", 8, 1.5, 0, 4))
    k.bone("plate", "root", pivot=(0, 4.5, 0), cubes=k.octagon("plate", 6, 4, 5))
    k.bone("glow_plate", "plate", pivot=(0, 4.6, 0), cubes=k.octagon("platering", 6.3, 4.5, 4.8) + [k.box("indicator", -2, 1.5, -8.1, 2, 2.5, -8)])
    k.bone("cradle", "root", pivot=(0, 5, 0), cubes=[k.box("cradle", -2, 5, -2, 2, 6, 2), k.box("clamp", -2.8, 5, -1, -2, 9, 1),
                                                    k.box("clamp", 2, 5, -1, 2.8, 9, 1)])
    pylons, rings, tips = [], [], []
    for i, (x, z) in enumerate(((-5.5, -5.5), (5.5, -5.5), (5.5, 5.5), (-5.5, 5.5))):
        pylons.append(k.box("pylon", x - 0.8, 4, z - 0.8, x + 0.8, 12, z + 0.8))
        rings += [k.box("coilring", x - 1.3, y, z - 1.3, x + 1.3, y + 0.8, z + 1.3) for y in (6.5, 8.5)]
        tips.append(k.box("tip", x - 0.6, 12, z - 0.6, x + 0.6, 13, z + 0.6))
    k.bone("pylons", "root", cubes=pylons)
    k.bone("coils", "pylons", cubes=rings)
    k.bone("glow_tips", "pylons", pivot=(0, 12.5, 0), cubes=tips)
    for i, (x, z) in enumerate(((-5.5, -5.5), (5.5, -5.5), (5.5, 5.5), (-5.5, 5.5))):
        k.bone(f"tool_{i}", "pylons", pivot=(x, 13, z))
    k.bone("work_0", "root", pivot=(0, 8, 0))
    k.write("charging_station", path, bounds=(1.5, 1.5), offset=(0, 0.5, 0))

out = sys.argv[1] if len(sys.argv) > 1 else GEO
part_workstation(f"{out}/part_workstation.geo.json")
part_assembler(f"{out}/part_assembler.geo.json")
module_workstation(f"{out}/module_workstation.geo.json")
ammo_workstation(f"{out}/ammo_workstation.geo.json")
charging_station(f"{out}/charging_station.geo.json")
