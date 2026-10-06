"""Turret base v2: a 1-block octagonal tower (flared foot, diagonal buttresses, window slits, crenellated rim).
Origin = centre of the attached face, +Y = mount axis, front = north (-Z). Units px; weapon_mount sits at y 16."""
import sys
from kit import Kit, TAN22, GEO

k = Kit(128)
k.bone("root")

# foot: wide octagon plus a stepped skirt
k.bone("plinth", "root", cubes=k.octagon("foot", 8, 0, 2) + k.octagon("skirt", 7.3, 2, 3.4))

# buttresses on the four diagonal flats, each with a sloped cap
butt = []
for ang in (45, 135, -135, -45):
    butt.append(k.box("butt", -1.6, 0, -8.6, 1.6, 5.2, -6.2, rot=[0, ang, 0], pivot=[0, 0, 0]))
    butt.append(k.box("buttcap", -1.2, 5.2, -8.0, 1.2, 6.2, -6.2, rot=[0, ang, 0], pivot=[0, 0, 0]))
k.bone("buttress", "plinth", cubes=butt)

# body: octagonal core, ribs on the diagonals, recessed window frames on the four sides
body = k.octagon("body", 6.4, 3.4, 11.6)
for ang in (45, 135, -135, -45):
    body.append(k.box("rib", -0.9, 3.4, -7.2, 0.9, 11.6, -6.3, rot=[0, ang, 0], pivot=[0, 0, 0]))
for ang in (0, 90, 180, -90):
    body.append(k.box("window", -2.3, 6.2, -6.9, 2.3, 10.4, -6.3, rot=[0, ang, 0], pivot=[0, 0, 0]))
    body.append(k.box("sill", -2.6, 5.7, -7.15, 2.6, 6.2, -6.3, rot=[0, ang, 0], pivot=[0, 0, 0]))
    body.append(k.box("lintel", -2.6, 10.4, -7.15, 2.6, 10.9, -6.3, rot=[0, ang, 0], pivot=[0, 0, 0]))
k.bone("body", "root", pivot=(0, 3.4, 0), cubes=body)

# window slits glow; the front one doubles as the status light (blinks red when damaged)
slits = [k.box("slit", -1.3, 7.6, -7.0, 1.3, 8.4, -6.85, rot=[0, ang, 0], pivot=[0, 0, 0]) for ang in (90, 180, -90)]
k.bone("glow_windows", "body", pivot=(0, 8, 0), cubes=slits)
k.bone("status_light", "body", pivot=(0, 8, -6.9), cubes=[k.box("status", -1.3, 7.6, -7.0, 1.3, 8.4, -6.85)])

# rim: collar band, glowing seam beneath it, merlons on top
k.bone("deck", "root", pivot=(0, 11.6, 0), cubes=k.octagon("collar", 7.4, 11.6, 13.6) + k.octagon("deckplate", 5.2, 13.6, 14.8)
       + k.ring8("merlon", 6.0, 13.6, 15.6, 1.4, width=2.6))
k.bone("energy_ring", "root", pivot=(0, 11.4, 0), cubes=k.ring8("seam", 6.45, 11.1, 11.6, 0.25))

# turntable the weapon sits on
k.bone("weapon_mount", "root", pivot=(0, 16, 0), cubes=k.octagon("turntable", 4.2, 14.8, 16))

# T2: armour plates over the diagonal ribs
plates = [k.box("armor", -2.4, 4.4, -7.6, 2.4, 10.6, -6.9, rot=[0, ang, 0], pivot=[0, 0, 0]) for ang in (45, 135, -135, -45)]
k.bone("armor_t2", "body", pivot=(0, 3.4, 0), cubes=plates)

# T3: tall cooling fins rising from the buttresses, glowing edge on each
fins = [k.box("fin", -0.45, 5.0, -8.4, 0.45, 14.6, -6.6, rot=[0, ang, 0], pivot=[0, 0, 0]) for ang in (45, 135, -135, -45)]
k.bone("fins_t3", "root", pivot=(0, 5, 0), cubes=fins)
strips = [k.box("finglow", -0.5, 6.5, -8.55, 0.5, 13.5, -8.35, rot=[0, ang, 0], pivot=[0, 0, 0]) for ang in (45, 135, -135, -45)]
k.bone("glow_fins_t3", "fins_t3", pivot=(0, 5, 0), cubes=strips)

k.write("turret_base", sys.argv[1] if len(sys.argv) > 1 else GEO + "turret_base.geo.json", bounds=(2, 2.5), offset=(0, 0.6, 0))
