#!/usr/bin/env python3
"""Feed Hub block models (PLAN "Feed Hub", 4a). The blockstate picks parts from the hub's place in its structure
(properties x, y, z = alone/low/middle/high): a lone hub keeps block/feed_hub; a merged one gets plain panels, then a
frame along every edge where its structure ends, then a cyan glow line on top of all frames. Faces between hubs are
culled (cullface), so the structure reads as one block. Frames and glow lines are full-face overlays with the strip on
the texture's top, turned onto the right edge with the face rotation.

    python3 tools/gen_feed_hub_models.py
"""
import json
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bastion"
FACES = ["down", "up", "north", "south", "west", "east"]
AXIS = {"down": "y", "up": "y", "north": "z", "south": "z", "west": "x", "east": "x"}
LOW_END = {"down", "north", "west"}
# Where each world direction lies on a face's texture with default UVs, as the clockwise rotation that turns the
# texture's top there: 0 top, 90 right, 180 bottom, 270 left (vanilla BlockElement default UVs).
ROTATION = {
    "north": {"up": 0, "west": 90, "down": 180, "east": 270},
    "south": {"up": 0, "east": 90, "down": 180, "west": 270},
    "west": {"up": 0, "south": 90, "down": 180, "north": 270},
    "east": {"up": 0, "north": 90, "down": 180, "south": 270},
    "up": {"north": 0, "east": 90, "south": 180, "west": 270},
    "down": {"south": 0, "east": 90, "north": 180, "west": 270},
}
MERGED = {"OR": [{"x": "!alone"}, {"y": "!alone"}, {"z": "!alone"}]}


def model(texture, faces, glow=False):
    element = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}
    if glow:
        element = {"from": [0, 0, 0], "to": [16, 16, 16], "forge_data": {"block_light": 15, "sky_light": 15}, "shade": False,
                   "faces": faces}
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"particle": "bastion:block/feed_hub_panel", "t": texture}, "elements": [element]}


def edge(texture, end, glow=False):
    """The strip on every face that touches the structure's end `end` (one of FACES)."""
    faces = {f: {"texture": "#t", "cullface": f, "rotation": ROTATION[f][end]} for f in FACES if AXIS[f] != AXIS[end]}
    return model(texture, faces, glow)


def write(rel, data):
    path = ASSETS / rel
    path.write_text(json.dumps(data, indent=2) + "\n")
    print("wrote", rel)


if __name__ == "__main__":
    write("models/block/feed_hub_panel.json",
          model("bastion:block/feed_hub_panel", {f: {"texture": "#t", "cullface": f} for f in FACES}))
    parts = [{"when": {"x": "alone", "y": "alone", "z": "alone"}, "apply": {"model": "bastion:block/feed_hub"}},
             {"when": MERGED, "apply": {"model": "bastion:block/feed_hub_panel"}}]
    for kind, texture, glow in (("frame", "bastion:block/feed_hub_edge", False), ("glow", "bastion:block/feed_hub_edge_glow", True)):
        for end in FACES:  # all frames before all glow lines: later coplanar quads win
            write(f"models/block/feed_hub_{kind}_{end}.json", edge(texture, end, glow))
            at_end = "alone|low" if end in LOW_END else "alone|high"
            parts.append({"when": {"AND": [{AXIS[end]: at_end}, MERGED]}, "apply": {"model": f"bastion:block/feed_hub_{kind}_{end}"}})
    write("blockstates/feed_hub.json", {"multipart": parts})
