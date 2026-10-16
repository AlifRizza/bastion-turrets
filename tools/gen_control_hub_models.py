#!/usr/bin/env python3
"""Turret Control Hub block models and blockstates (PLAN "Turret Control Hub"). Hub Casing and Power Port pick their look
from their place in a formed 3x3x3 hub (x, y, z = alone/low/middle/high; alone = in no hub): formed, a casing becomes the
Feed Hub structure's plain panel, the port keeps its sockets with a glowing ring, and both get the Feed Hub's frame and
cyan glow line on every face at an end of the cube, so the lines run along the cube's outer edges (the Feed Hub's edge
models, reused; faces between parts are culled). The Hub Interface (facing x status) is a console block with a screen
tilted back in a recess: dark, a radar sweep (online) or a blinking power glyph (offline); it sits in the middle of a side,
away from every edge, so it has no lines. Satellites: a particle-only block model (GeckoLib draws them).

    python3 tools/gen_control_hub_models.py
"""
import json
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bastion"
B = "bastion:block/"
FACES = ["down", "up", "north", "south", "west", "east"]
AXIS = {"down": "y", "up": "y", "north": "z", "south": "z", "west": "x", "east": "x"}
LOW_END = {"down", "north", "west"}
TURN = {"north": 0, "east": 90, "south": 180, "west": 270}
GLOW = {"block_light": 15, "sky_light": 15}
FORMED = {"x": "low|middle|high"}


def write(rel, data):
    path = ASSETS / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")
    print("wrote", rel)


def model(textures, elements):
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": textures, "elements": elements}


def cube(texture, glow=False):
    element = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {f: {"texture": texture, "cullface": f} for f in FACES}}
    if glow:
        element["forge_data"], element["shade"] = GLOW, False
    return element


def interface(screen, lit):
    """Front = north: one full cube, so the Feed Hub edge overlays lie exactly on its faces (partial faces under them
    z-fought), its front texture cut open over a recess 3 px deep: a back plate, four inner walls and a screen tilted
    back in it (its top into the block)."""
    faces = {f: {"texture": "#c", "cullface": f} for f in FACES}
    faces["north"] = {"texture": "#f", "cullface": "north"}
    shell = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}
    back = {"from": [2, 4, 3], "to": [14, 12, 3], "faces": {"north": {"texture": "#r"}}}
    walls = [{"from": [2, 12, 0], "to": [14, 12, 3], "faces": {"down": {"texture": "#c"}}},
             {"from": [2, 4, 0], "to": [14, 4, 3], "faces": {"up": {"texture": "#c"}}},
             {"from": [2, 4, 0], "to": [2, 12, 3], "faces": {"east": {"texture": "#c"}}},
             {"from": [14, 4, 0], "to": [14, 12, 3], "faces": {"west": {"texture": "#c"}}}]
    glass = {"from": [2.5, 4.5, 1.5], "to": [13.5, 12.5, 2.5], "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 4.5, 2.5]},
             "faces": {"north": {"texture": "#s"}}}
    if lit:
        glass["forge_data"], glass["shade"] = GLOW, False
    textures = {"particle": B + "hub_casing", "c": B + "hub_casing", "f": B + "hub_interface_front", "r": B + "hub_interface_recess",
                "s": B + screen}
    return model(textures, [shell, back, *walls, glass])


def edges():
    """Feed Hub frames, then glow lines (later quads win), on every side where the block is at that end of the cube."""
    return [{"when": layer_end(end), "apply": {"model": f"{B}feed_hub_{kind}_{end}"}} for kind in ("frame", "glow") for end in FACES]


def layer_end(end):
    return {AXIS[end]: "low" if end in LOW_END else "high"}


if __name__ == "__main__":
    write("models/block/hub_casing.json", model({"particle": B + "hub_casing", "t": B + "hub_casing"}, [cube("#t")]))
    write("models/block/hub_power_port.json", model({"particle": B + "hub_power_port", "t": B + "hub_power_port"}, [cube("#t")]))
    write("models/block/hub_power_port_glow.json", model({"particle": B + "hub_power_port", "t": B + "hub_power_port_glow"}, [cube("#t", glow=True)]))
    for status, screen, lit in (("unformed", "hub_screen_off", False), ("online", "hub_screen_radar", True), ("offline", "hub_screen_alert", True)):
        write(f"models/block/hub_interface_{status}.json", interface(screen, lit))

    write("blockstates/hub_casing.json", {"multipart": [
        {"when": {"x": "alone"}, "apply": {"model": B + "hub_casing"}},
        {"when": FORMED, "apply": {"model": B + "feed_hub_panel"}},
        *edges()]})
    write("blockstates/hub_power_port.json", {"multipart": [
        {"apply": {"model": B + "hub_power_port"}},
        {"when": FORMED, "apply": {"model": B + "hub_power_port_glow"}},
        *edges()]})
    interface_parts = [{"when": {"facing": f, "status": s}, "apply": {"model": f"{B}hub_interface_{s}", "y": turn}}
                       for f, turn in TURN.items() for s in ("unformed", "online", "offline")]
    write("blockstates/hub_interface.json", {"multipart": interface_parts})

    write("models/item/hub_casing.json", {"parent": B + "hub_casing"})
    write("models/item/hub_power_port.json", {"parent": B + "hub_power_port"})
    write("models/item/hub_interface.json", {"parent": B + "hub_interface_online"})
    for tier in (1, 2, 3):
        write(f"models/block/satellite_uplink_t{tier}.json", {"textures": {"particle": f"{B}satellite_uplink_t{tier}"}})
        write(f"blockstates/satellite_uplink_t{tier}.json", {"variants": {"": {"model": f"{B}satellite_uplink_t{tier}"}}})
