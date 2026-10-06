"""Blockout kit for the turret models: Bedrock geo cubes with packed per-face UVs (units = px).

The design scripts next to this file generate assets/bastion/geo/*.geo.json; afterwards run
tools/gen_textures.py (paint) and tools/fit_item_display.py (item transforms), then re-import the geo into
the blockbench/*.bbmodel source if you keep editing there."""
import json, math
from pathlib import Path

GEO = str(Path(__file__).resolve().parents[2] / "src/main/resources/assets/bastion/geo") + "/"

TAN22 = math.tan(math.radians(22.5))


class Kit:
    def __init__(self, tex=128):
        self.tex, self.regions, self.cursor, self.bones = tex, {}, [0, 0, 0], []

    # --- UV packing: faces with the same key share one region (identical repeated parts) ---
    def region(self, key, w, h):
        if key in self.regions: return self.regions[key]
        w, h = max(1, math.ceil(w - 1e-6)), max(1, math.ceil(h - 1e-6))
        if self.cursor[0] + w > self.tex: self.cursor[:] = [0, self.cursor[1] + self.cursor[2] + 1, 0]
        self.regions[key] = (self.cursor[0], self.cursor[1], w, h)
        self.cursor[0] += w + 1; self.cursor[2] = max(self.cursor[2], h)
        return self.regions[key]

    def face(self, key, w, h):
        x, y, _, _ = self.region(key, w, h)
        return {"uv": [x, y], "uv_size": [round(w, 4), round(h, 4)]}

    HIDDEN = None

    def box(self, tag, x0, y0, z0, x1, y1, z1, rot=None, pivot=None, hide=()):
        """Axis box; rot=[rx, ry, rz] around pivot. Faces listed in hide map to one shared 1x1 texel."""
        sx, sy, sz = x1 - x0, y1 - y0, z1 - z0
        def f(name, w, h, kind):
            return self.face("_hidden", 1, 1) if name in hide else self.face(f"{tag}_{kind}", w, h)
        c = {"origin": [r4(x0), r4(y0), r4(z0)], "size": [r4(sx), r4(sy), r4(sz)],
             "uv": {"north": f("north", sx, sy, "ns"), "south": f("south", sx, sy, "ns"),
                    "east": f("east", sz, sy, "ew"), "west": f("west", sz, sy, "ew"),
                    "up": f("up", sx, sz, "ud"), "down": f("down", sx, sz, "ud")}}
        if rot and any(rot):
            c["rotation"] = [r4(v) for v in rot]
            c["pivot"] = [r4(v) for v in (pivot or [0, y0, 0])]
        return c

    def octagon(self, tag, r, y0, y1, cx=0, cz=0, step=0.01):
        """Regular octagon prism, flats at distance r facing the 8 compass directions (4 overlapping boxes)."""
        a = 2 * r * TAN22
        out = []
        for k, rot in enumerate((0, 45, 90, -45)):
            dy = step * k
            out.append(self.box(tag, cx - r, y0 + dy, cz - a / 2, cx + r, y1 + dy, cz + a / 2,
                                rot=[0, rot, 0], pivot=[cx, y0, cz], hide=("north", "south")))
        return out

    def chamfer(self, tag, half, cut, y0, y1, step=0.01):
        """Square slab with stepped (pixel) chamfered corners: three overlapping boxes, no rotation needed."""
        h, c = half, cut
        return [self.box(tag, -h, y0, -(h - c), h, y1, h - c),
                self.box(tag + "b", -(h - c), y0 + step, -h, h - c, y1 + step, h),
                self.box(tag + "c", -(h - c / 2), y0 + 2 * step, -(h - c / 2), h - c / 2, y1 + 2 * step, h - c / 2)]

    def faces4(self, tag, half, y0, y1, width, depth, along=0.0):
        """One box on each of the four sides of a square of half-size {half}: north, south, west, east."""
        w, a = width / 2, along
        return [self.box(tag, a - w, y0, -half - depth, a + w, y1, -half),
                self.box(tag, -a - w, y0, half, -a + w, y1, half + depth),
                self.box(tag + "x", -half - depth, y0, -a - w, -half, y1, -a + w),
                self.box(tag + "x", half, y0, a - w, half + depth, y1, a + w)]

    def ring8(self, tag, r, y0, y1, depth, width=None, offset=0):
        """Eight segments standing on the octagon flats at distance r (bands, merlons, window frames)."""
        w = width if width is not None else 2 * r * TAN22
        return [self.box(tag, -w / 2, y0, -r - depth, w / 2, y1, -r, rot=[0, offset + 45 * k, 0], pivot=[0, y0, 0])
                for k in range(8)]

    def rod_z(self, tag, cx, cy, z0, z1, d, inner=None):
        """Round-ish rod along Z: a plus of two boxes (pixel-art cylinder)."""
        n = inner if inner is not None else d * 0.66
        return [self.box(tag, cx - d / 2, cy - n / 2, z0, cx + d / 2, cy + n / 2, z1),
                self.box(tag + "v", cx - n / 2, cy - d / 2, z0 + 0.005, cx + n / 2, cy + d / 2, z1 - 0.005)]

    def rod_x(self, tag, cy, cz, x0, x1, d, inner=None):
        n = inner if inner is not None else d * 0.66
        return [self.box(tag, x0, cy - n / 2, cz - d / 2, x1, cy + n / 2, cz + d / 2),
                self.box(tag + "v", x0 + 0.005, cy - d / 2, cz - n / 2, x1 - 0.005, cy + d / 2, cz + n / 2)]

    def bone(self, name, parent=None, pivot=(0, 0, 0), cubes=(), **extra):
        b = {"name": name, "pivot": [r4(v) for v in pivot]}
        if parent: b["parent"] = parent
        if cubes: b["cubes"] = list(cubes)
        b.update(extra)
        self.bones.append(b)
        return b

    def write(self, name, path, bounds=(3, 3), offset=(0, 1, 0)):
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{name}", "texture_width": self.tex, "texture_height": self.tex,
                            "visible_bounds_width": bounds[0], "visible_bounds_height": bounds[1], "visible_bounds_offset": list(offset)},
            "bones": self.bones}]}
        json.dump(geo, open(path, "w"), indent="\t")
        used = self.cursor[1] + self.cursor[2]
        print(f"{name}: {sum(len(b.get('cubes', [])) for b in self.bones)} cubes, {len(self.regions)} uv regions, rows {used}/{self.tex}")
        assert used <= self.tex, "texture too small"


def r4(v):
    return round(float(v), 4)
