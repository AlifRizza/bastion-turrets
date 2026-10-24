"""Fortress turret base (PLAN "Fortress Turret Base 3x3", Tampilan): a low, wide 3x3x1 platform after the user's "Weapons
Platform Large" reference. An octagonal hull with a raised octagonal core and a round turntable hatch; four armoured
buttresses on the corners slope down to the floor, a glow strip along each edge of their plates (the health light).
T2 adds vented side blocks with sloped armour plates between the buttresses, T3 banks of cooling fins on the buttresses
with a glowing spine. Origin = centre of the core block on the floor, +Y up, front = north (-Z), footprint +-24 px.
weapon_mount sits on top at y 16."""
import math
import sys
from kit import Kit, GEO, r4

k = Kit(256)
k.bone("root")


def place(tag, yaw, rho, a, y, w, h, d, tilt=0.0):
    """A w x h x d box (across, up, outward) centred {rho} out along the yaw's direction, {a} across it (to the right seen
    from outside) and at height {y}, its outer end tilted down by {tilt} radians. It turns about its own centre, so its
    unrotated box stays inside the footprint (the item-display fit reads unrotated bounds); quarter turns are built
    axis-aligned, tilted about a world axis."""
    b = math.radians(yaw)
    cx, cz = rho * math.sin(b) + a * math.cos(b), rho * math.cos(b) - a * math.sin(b)
    t = math.degrees(tilt)
    if yaw % 90 == 0:
        q = round(yaw / 90) % 4  # outward +Z, +X, -Z, -X
        sx, sz = (w, d) if q % 2 == 0 else (d, w)
        rot = ([-t, 0, 0], [0, 0, t], [t, 0, 0], [0, 0, -t])[q]
        tag += "" if q % 2 == 0 else "x"  # a quarter-turned box has its faces swapped: its own uv regions
    else:
        sx, sz, rot = w, d, [-t, yaw, 0]
    return k.box(tag, cx - sx / 2, y - h / 2, cz - sz / 2, cx + sx / 2, y + h / 2, cz + sz / 2, rot=rot, pivot=[cx, y, cz])


class Slope:
    """A sloped plate given by its top line, (rho, y) inner to outer end, and its thickness."""

    def __init__(self, top_in, top_out, thick):
        self.top_in, self.top_out, self.thick = top_in, top_out, thick
        self.angle = math.atan2(top_in[1] - top_out[1], top_out[0] - top_in[0])

    def top(self, rho):
        return self.top_in[1] - (rho - self.top_in[0]) * math.tan(self.angle)

    def along(self, tag, yaw, rho0, rho1, a, w, lift, thick):
        """A box lying on the slope from rho0 to rho1 (measured on the top line), {lift} px above it (below if negative)."""
        s, c = math.sin(self.angle), math.cos(self.angle)
        mid, off = (rho0 + rho1) / 2, lift + thick / 2
        return place(tag, yaw, mid + s * off, a, self.top(mid) + c * off, w, thick, (rho1 - rho0) / c, self.angle)

    def plate(self, tag, yaw, w):
        return self.along(tag, yaw, self.top_in[0], self.top_out[0], 0, w, -self.thick, self.thick)


k.bone("plinth", "root", cubes=k.chamfer("foot", 23.5, 7, 0, 1.5))

# hull: low octagon, a step ring and the raised core, panel frames on the four sides
body = k.octagon("hull", 19.5, 1.5, 8) + k.octagon("step", 17, 8, 9) + k.octagon("core", 12.5, 9, 13)
body += k.ring8("coreband", 12.5, 9, 10, 0.5)
body += k.faces4("panel", 19.5, 3, 6.6, 12, 0.3)
body += k.faces4("rib", 19.5, 2, 7.6, 1.2, 0.5, along=5.2) + k.faces4("rib", 19.5, 2, 7.6, 1.2, 0.5, along=-5.2)
k.bone("body", "root", pivot=(0, 1.5, 0), cubes=body)

# Buttresses run out along the diagonals (rho = distance from the centre along the diagonal). The plate's inner end
# hides in the core; its outer bottom edge just clears the floor.
CORNERS = (("ne", 135), ("nw", -135), ("se", 45), ("sw", -45))  # yaw that turns +Z onto the corner's diagonal
PLATE = Slope((11.5, 12.0), (28.0, 4.4), 4.2)
butt, strips = [], {}
for name, yaw in CORNERS:
    butt.append(PLATE.plate("buttplate", yaw, 12))
    butt.append(PLATE.along("buttpanel", yaw, 15.0, 26.4, 0, 6.4, 0, 0.45))  # raised inset panel
    # stepped frame under the plate's overhang, closing the wedge between hull and floor (tops hidden in the plate)
    butt.append(place("buttstep", yaw, 20.5, 0, 1.5 + 2.6 / 2, 10, 2.6, 4))
    butt.append(place("buttstep2", yaw, 23.6, 0, 1.5 + 1.0 / 2, 10, 1.0, 2.4))
    butt.append(place("buttfoot", yaw, 26.2, 0, 0.8, 10.6, 1.6, 3.2))
    # the health light: a strip along each long edge of the plate
    strips[name] = [PLATE.along("edge", yaw, 14.2, 27.6, s * 4.8, 1.0, 0, 0.3) for s in (-1, 1)]
k.bone("buttress", "root", cubes=butt)

# glow strips: the front pair's are the status light (blinks red when damaged), the rear pair's the windows
k.bone("glow_windows", "body", pivot=(0, 8, 0), cubes=strips["se"] + strips["sw"])
k.bone("status_light", "body", pivot=(0, 8, -16), cubes=strips["ne"] + strips["nw"])

# collar over the core, the hatch plate, a glowing seam on the core under the collar
k.bone("deck", "root", pivot=(0, 13, 0), cubes=k.octagon("collar", 13.3, 13, 14) + k.octagon("hatchplate", 11.5, 14, 14.8))
k.bone("energy_ring", "root", pivot=(0, 12.3, 0), cubes=k.ring8("seam", 12.5, 11.9, 12.7, 0.35))


def disc(tag, r, y0, y1, step=0.01):
    """16-sided prism (a round hatch), flats at distance r: eight overlapping boxes."""
    a = r * math.tan(math.radians(11.25))
    return [k.box(tag, -r, y0 + step * i, -a, r, y1 + step * i, a, rot=[0, 22.5 * i, 0], pivot=[0, y0, 0], hide=("north", "south"))
            for i in range(8)]


k.bone("weapon_mount", "root", pivot=(0, 16, 0), cubes=k.octagon("turnring", 10.6, 14.6, 15.2) + disc("turntable", 9.5, 15, 15.92))

# T2: a vented block on each side between the buttresses, a sloped armour plate over it and a row of slots in front
SIDES = (180, -90, 0, 90)  # north, west, south, east
GLACIS = Slope((16.5, 8.6), (24.0, 5.6), 1.4)
side = []
for yaw in SIDES:
    side.append(place("sideblock", yaw, 20.25, 0, 1.5 + 4.1 / 2, 15.6, 4.1, 6.5))
    side.append(GLACIS.plate("glacis", yaw, 14.8))
k.bone("armor_t2", "body", pivot=(0, 1.5, 0), cubes=side)
slots = [place("slot", yaw, 23.55, x, 3.7, 2.4, 2.2, 0.3) for yaw in SIDES for x in (-5.4, -1.8, 1.8, 5.4)]
k.bone("vents_t2", "armor_t2", pivot=(0, 1.5, 0), cubes=slots)

# T3: a bank of cooling fins across each buttress plate, stepping down with it, and a glowing spine over their tops
FIN_RHO, FIN_UP, FIN_W = (15.6, 18.2, 20.8, 23.4, 26.0), 3.0, 7.6
fins, spines = [], []
for name, yaw in CORNERS:
    for i, rho in enumerate(FIN_RHO):
        fins.append(place(f"fin{i}", yaw, rho, 0, PLATE.top(rho) + (FIN_UP - 0.6) / 2, FIN_W, FIN_UP + 0.6, 0.8))
    lift = FIN_UP * math.cos(PLATE.angle) - 0.05  # rests on the fin tops
    spines.append(PLATE.along("spine", yaw, FIN_RHO[0] - 0.6, FIN_RHO[-1] + 0.6, 0, 1.2, lift, 0.5))
k.bone("fins_t3", "root", pivot=(0, 3, 0), cubes=fins)
k.bone("glow_fins_t3", "fins_t3", pivot=(0, 3, 0), cubes=spines)


def extent():
    """World bounds of every cube with its rotation (GeckoLib in geo space: Rz(-z) Ry(y) Rx(-x) about the cube pivot)."""
    lo, hi = [1e9] * 3, [-1e9] * 3
    for bone in k.bones:
        for c in bone.get("cubes", []):
            rx, ry, rz = (math.radians(v) for v in c.get("rotation", (0, 0, 0)))
            p = c.get("pivot", (0, 0, 0))
            for corner in ((dx, dy, dz) for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)):
                x, y, z = (c["origin"][i] + corner[i] * c["size"][i] - p[i] for i in range(3))
                y, z = y * math.cos(-rx) - z * math.sin(-rx), y * math.sin(-rx) + z * math.cos(-rx)
                x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
                x, y = x * math.cos(-rz) - y * math.sin(-rz), x * math.sin(-rz) + y * math.cos(-rz)
                for i, v in enumerate((x + p[0], y + p[1], z + p[2])):
                    lo[i], hi[i] = min(lo[i], v), max(hi[i], v)
    return lo, hi


lo, hi = extent()
print("extent px", [r4(v) for v in lo], [r4(v) for v in hi])
assert max(-lo[0], hi[0], -lo[2], hi[2]) <= 26, "pokes more than 2 px past the 3x3 footprint"
assert lo[1] >= -0.01, "below the floor"

k.write("fortress_turret_base", sys.argv[1] if len(sys.argv) > 1 else GEO + "fortress_turret_base.geo.json", bounds=(4, 2.5), offset=(0, 0.6, 0))
