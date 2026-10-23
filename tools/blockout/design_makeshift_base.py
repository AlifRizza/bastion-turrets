"""Makeshift turret base (PLAN "Turret early game", Tampilan): a plank crate on a rough stone plinth, held by corner
posts with rope lashings and riveted iron straps, a plank deck and a wooden turntable. No glow, no energy ring.
Origin = centre of the attached face, +Y = mount axis, front = north (-Z). Units px; weapon_mount sits at y 16.
TurretBaseRenderer draws the damage cracks over the stone and wood bones (CRACKED there)."""
import sys
from kit import Kit, GEO

k = Kit(128)
k.bone("root")

# stone plinth: a chamfered slab with a few stones bulging out of its sides
plinth = k.chamfer("stone", 7.6, 1.2, 0, 4.2)
plinth += k.faces4("cobble", 7.6, 0.6, 2.8, 3.6, 0.4, along=-2.6)
plinth += k.faces4("cobble2", 7.6, 2.2, 3.9, 2.8, 0.3, along=3.4)
k.bone("plinth", "root", cubes=plinth)

# plank crate between four corner posts
k.bone("body", "root", pivot=(0, 4.2, 0), cubes=[k.box("planks", -6.2, 4.2, -6.2, 6.2, 12, 6.2)])
posts = [k.box("post", sx * 5.9 - 1.3, 4.2, sz * 5.9 - 1.3, sx * 5.9 + 1.3, 12, sz * 5.9 + 1.3) for sx in (-1, 1) for sz in (-1, 1)]
k.bone("posts", "root", pivot=(0, 4.2, 0), cubes=posts)
rope = [k.box("rope", sx * 5.9 - 1.55, y0, sz * 5.9 - 1.55, sx * 5.9 + 1.55, y0 + 0.8, sz * 5.9 + 1.55)
        for sx in (-1, 1) for sz in (-1, 1) for y0 in (7.2, 8.2)]
k.bone("rope", "posts", pivot=(0, 7.2, 0), cubes=rope)

# plank deck, overhanging the crate
k.bone("deck", "root", pivot=(0, 12, 0), cubes=[k.box("deck", -7.4, 12, -7.4, 7.4, 13.6, 7.4)])

# iron: two straps round the crate between the posts, corner plates on the deck
iron = k.faces4("strap", 6.2, 5.2, 6.2, 9.2, 0.35) + k.faces4("strap", 6.2, 9.8, 10.8, 9.2, 0.35)
iron += [k.box("plate", sx * 6.0 - 1.4, 13.6, sz * 6.0 - 1.4, sx * 6.0 + 1.4, 13.9, sz * 6.0 + 1.4) for sx in (-1, 1) for sz in (-1, 1)]
k.bone("bands", "root", pivot=(0, 5.2, 0), cubes=iron)
rivets = []
for y0 in (5.35, 9.95):
    for along in (-3.5, 3.5):
        rivets += k.faces4("rivet", 6.55, y0, y0 + 0.7, 0.7, 0.3, along=along)
rivets += [k.box("rivet_top", sx * 6.0 - 0.35, 13.9, sz * 6.0 - 0.35, sx * 6.0 + 0.35, 14.2, sz * 6.0 + 0.35) for sx in (-1, 1) for sz in (-1, 1)]
k.bone("rivets", "bands", pivot=(0, 5.2, 0), cubes=rivets)

# wooden turntable the weapon sits on, with an iron pivot plate
k.bone("weapon_mount", "root", pivot=(0, 16, 0), cubes=k.octagon("turntable", 4.6, 13.6, 15.2))
k.bone("mount_plate", "weapon_mount", pivot=(0, 16, 0), cubes=k.octagon("pivot_plate", 3.4, 15.2, 16))

k.write("makeshift_turret_base", sys.argv[1] if len(sys.argv) > 1 else GEO + "makeshift_turret_base.geo.json", bounds=(2, 2.5), offset=(0, 0.6, 0))
