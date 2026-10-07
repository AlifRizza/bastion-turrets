#!/usr/bin/env python3
"""Centres and sizes the GeckoLib item models (builtin/entity) from their geo bounds.

Re-run after resizing a model:  python3 tools/fit_item_display.py
For each display context: keep its rotation, scale the model so its longest side is TARGET blocks, and pick the
translation that puts the model's bounding-box centre on the item centre (vanilla clamps translation to +-80px).
"""
import json
import math
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bastion"
GECKO_OFFSET = 0.51  # GeoItemRenderer lifts the model origin to y 0.51 above the item corner plane
CONTEXTS = {  # rotation (deg), longest side in blocks
    "gui": ([25, 135, 0], 0.95), "ground": ([0, 0, 0], 0.45), "fixed": ([0, 90, 0], 0.85), "head": ([0, 180, 0], 0.9),
    "thirdperson_righthand": ([75, 180, 0], 0.6), "thirdperson_lefthand": ([75, 180, 0], 0.6),
    "firstperson_righthand": ([0, 170, 0], 0.7), "firstperson_lefthand": ([0, 190, 0], 0.7),
}
MODELS = {"turret_base": "turret_base", "gun_turret": "gun_turret", "machine_gun_turret": "machine_gun_turret",
          "shotgun_turret": "shotgun_turret", "sniper_turret": "sniper_turret",
          "rocket_launcher_turret": "rocket_launcher_turret", "large_turret_base": "large_turret_base",
          "missile_launcher_turret": "missile_launcher_turret", "flamethrower_turret": "flamethrower_turret",
          "tesla_turret": "tesla_turret", "laser_rifle_turret": "laser_rifle_turret", "railgun_turret": "railgun_turret", "mortar_turret": "mortar_turret",
          "repulsor_turret": "repulsor_turret", "repulsor_dome_turret": "repulsor_dome_turret",
          "part_workstation": "part_workstation", "part_assembler": "part_assembler",
          "module_workstation": "module_workstation", "ammo_workstation": "ammo_workstation", "charging_station": "charging_station"}
# Parts (PLAN Fase 9) show some bones of a weapon or base model: fit each to just those bones.
PARTS = json.loads((ASSETS / "parts.json").read_text())  # item model -> geo
SIZE_FACTOR = {"turret_base": 0.68, "large_turret_base": 0.8, "part_workstation": 0.8, "part_assembler": 0.8,
               "module_workstation": 0.75, "ammo_workstation": 0.75, "charging_station": 0.68}  # a block-sized model reads right a bit smaller, like vanilla's 0.625 blocks


def bounds(geo, only=None, rotated=False):
    """Model bounds in blocks; {rotated}: turn each cube's corners by its rotation (rings of rotated segments, parts)."""
    lo, hi = [1e9] * 3, [-1e9] * 3
    for bone in json.loads((ASSETS / f"geo/{geo}.geo.json").read_text())["minecraft:geometry"][0]["bones"]:
        if only is not None and bone["name"] not in only:
            continue
        for c in bone.get("cubes", []):
            o, sz = c["origin"], c["size"]
            corners = [(o[0] + dx * sz[0], o[1] + dy * sz[1], o[2] + dz * sz[2]) for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
            if rotated and c.get("rotation"):
                pv = c.get("pivot", [0, 0, 0])
                corners = [tuple(a + b for a, b in zip(rotate(tuple(v - q for v, q in zip(corner, pv)), c["rotation"]), pv)) for corner in corners]
            for corner in corners:
                for i in range(3):
                    lo[i] = min(lo[i], corner[i])
                    hi[i] = max(hi[i], corner[i])
    lo[0], hi[0] = -hi[0], -lo[0]  # GeckoLib mirrors X
    return [v / 16 for v in lo], [v / 16 for v in hi]


def rotate(v, deg):
    """Vanilla ItemTransform: rotationXYZ, i.e. R = Rx * Ry * Rz applied to v."""
    x, y, z = v
    rx, ry, rz = (math.radians(a) for a in deg)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    return x, y, z


def display(geo, factor=1.0, only=None):
    lo, hi = bounds(geo, only, rotated=only is not None)
    centre = [(a + b) / 2 for a, b in zip(lo, hi)]
    centre[1] += GECKO_OFFSET - 0.5  # relative to the item centre
    longest = max(b - a for a, b in zip(lo, hi))
    out = {}
    for name, (rot, size) in CONTEXTS.items():
        s = round(size * factor / longest, 3)
        offset = rotate([c * s for c in centre], rot)
        t = [round(max(-80, min(80, -o * 16)), 2) for o in offset]
        out[name] = {"rotation": rot, "translation": t, "scale": [s, s, s]}
    return out


if __name__ == "__main__":
    for item, geo in MODELS.items():
        path = ASSETS / f"models/item/{item}.json"
        model = json.loads(path.read_text())
        model["display"] = display(geo, SIZE_FACTOR.get(item, 1.0))
        path.write_text(json.dumps(model, indent=2) + "\n")
        print(item, model["display"]["gui"])
    for item, part in PARTS.items():
        path = ASSETS / f"models/item/{item}.json"
        model = {"parent": "builtin/entity", "gui_light": "side", "display": display(part["model"], 0.85, set(part["bones"]))}
        path.write_text(json.dumps(model, indent=2) + "\n")
    print(len(PARTS), "parts fitted")
