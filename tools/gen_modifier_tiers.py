#!/usr/bin/env python3
"""Mk2/Mk3 of every turret modifier (PLAN 4.6 "Tier modul"): data JSON scaled from the Mk1 file (x1.5, x2; Penetrator's
pierce +1/+2/+3) and the item models. Re-run after changing a Mk1 file; edit a Mk2/Mk3 file by hand to tune one tier
(the next run overwrites it)."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
DATA = ROOT / "data/bastion/turret_modifiers"
MODELS = ROOT / "assets/bastion/models/item"
SCALE = {2: 1.5, 3: 2.0}


def tier(effect, mk):
    out = {}
    for key, value in effect.items():
        if key == "weapons":
            out[key] = value
        elif key == "pierce":
            out[key] = value * mk  # whole targets: +1/+2/+3
        else:
            out[key] = round(value * SCALE[mk], 4)
    return out


def main():
    n = 0
    for path in sorted(DATA.glob("*.json")):
        if "_mk" in path.stem:
            continue
        effect = json.loads(path.read_text())
        for mk in (2, 3):
            name = f"{path.stem}_mk{mk}"
            (DATA / f"{name}.json").write_text(json.dumps(tier(effect, mk), indent=2) + "\n")
            model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"bastion:item/{name}"}}
            (MODELS / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n")
            n += 1
    print(f"wrote {n} modifier tiers")


if __name__ == "__main__":
    main()
