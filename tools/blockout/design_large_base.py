"""Large turret base: a 2x2x1 slab (32 x 16 x 32 px) with chamfered corners, recessed panels and a turntable,
for big weapon modules. Origin = centre of the 2x2 footprint on the floor, +Y up, front = north (-Z).
weapon_mount sits on top at y 16."""
import sys
from kit import Kit, GEO

k = Kit(256)
k.bone("root")

k.bone("plinth", "root", cubes=k.chamfer("foot", 16, 3, 0, 2.5) + k.chamfer("skirt", 15.2, 3, 2.5, 3.5))

body = k.chamfer("body", 14.5, 3, 3.5, 12)
body += k.faces4("rib", 14.5, 3.5, 12, 1.2, 0.6, along=10)      # vertical ribs near the corners
body += k.faces4("rib", 14.5, 3.5, 12, 1.2, 0.6, along=-10)
body += k.faces4("panel", 14.5, 5.5, 10.5, 15, 0.3)              # recessed panel frames, one per side
k.bone("body", "root", pivot=(0, 3.5, 0), cubes=body)

# panel slits glow; the north one is the status light (blinks red when damaged)
slits = k.faces4("slit", 14.85, 7.6, 8.4, 10, 0.1)
k.bone("glow_windows", "body", pivot=(0, 8, 0), cubes=slits[1:])
k.bone("status_light", "body", pivot=(0, 8, -14.9), cubes=slits[:1])

deck = k.chamfer("deck", 13.5, 3, 12, 14) + k.chamfer("deckplate", 11, 2.5, 14, 15)
k.bone("deck", "root", pivot=(0, 12, 0), cubes=deck)
k.bone("energy_ring", "root", pivot=(0, 11.8, 0), cubes=k.faces4("seam", 14.5, 11.5, 12, 20, 0.2))

k.bone("weapon_mount", "root", pivot=(0, 16, 0),
       cubes=k.octagon("turnring", 9.5, 14.6, 15.2) + k.octagon("turntable", 8, 15, 16))

# T2: armour plates either side of each panel
armor = k.faces4("armor", 14.5, 4, 11.4, 4.6, 0.7, along=11) + k.faces4("armor", 14.5, 4, 11.4, 4.6, 0.7, along=-11)
k.bone("armor_t2", "body", pivot=(0, 3.5, 0), cubes=armor)

# T3: square cooling posts on the four corners, with a glowing cap strip
posts, caps = [], []
for sx in (-1, 1):
    for sz in (-1, 1):
        cx, cz = sx * 12.2, sz * 12.2
        posts.append(k.box("post", cx - 1.1, 3.5, cz - 1.1, cx + 1.1, 18, cz + 1.1))
        caps.append(k.box("postglow", cx - 1.2, 15.5, cz - 1.2, cx + 1.2, 17, cz + 1.2))
k.bone("fins_t3", "root", pivot=(0, 3.5, 0), cubes=posts)
k.bone("glow_fins_t3", "fins_t3", pivot=(0, 15.5, 0), cubes=caps)

k.write("large_turret_base", sys.argv[1] if len(sys.argv) > 1 else GEO + "large_turret_base.geo.json", bounds=(3, 2.5), offset=(0, 0.6, 0))
