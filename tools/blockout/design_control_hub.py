"""Satellite Uplink models (PLAN "Turret Control Hub"), the turrets' kit and look: gunmetal panels, dark frames, one cyan
glow. Origin = the middle of the block's floor, front = north (-Z), units px, two blocks tall (mast in the lower block;
servo, head and dish in the upper). head turns (yaw) on the servo, dish tilts (pitch) on the head's yoke. Also writes the
clips, the same names for every tier: animations/satellite_uplink_t<N>.animation.json (folded, deploy, spin, offline).

    cd tools/blockout && python3 design_control_hub.py
"""
import json
from pathlib import Path

from kit import Kit, GEO

ANIMATIONS = Path(GEO).parent / "animations"
DISH = {1: 5.5, 2: 7.5, 3: 9.5}  # dish radius per tier, px


def satellite(tier):
    k = Kit(128)
    r = DISH[tier]
    k.bone("root")
    k.bone("base", "root", cubes=k.chamfer("foot", 6, 2, 0, 2) + [k.box("plinth", -4, 2, -4, 4, 4, 4)])
    k.bone("mast", "root", cubes=[k.box("pole", -1.5, 4, -1.5, 1.5, 21, 1.5)])
    k.bone("collars", "root", cubes=[k.box("collar", -2.5, y, -2.5, 2.5, y + 1.5, 2.5) for y in (8, 13.5, 19)])
    k.bone("servo", "root", cubes=[k.box("servo", -3.5, 21, -3.5, 3.5, 25, 3.5)])
    k.bone("glow_servo", "servo", cubes=[k.box("servo_ring", -3.7, 22.6, -3.7, 3.7, 23.4, 3.7)])
    k.bone("head", "root", pivot=(0, 25, 0), cubes=[
        k.box("turntable", -3, 25, -3, 3, 26.5, 3),
        k.box("yoke", -4.5, 26.5, -1, -3.5, 30.5, 1), k.box("yoke", 3.5, 26.5, -1, 4.5, 30.5, 1)])
    # Built facing up (+Y), tilted toward the front by the bone's rest rotation; the clips turn it from there.
    k.bone("dish", "head", pivot=(0, 29.5, 0), rotation=[-50, 0, 0],
           cubes=k.octagon("dish_back", r * 0.55, 28.6, 29.6) + k.octagon("dish_face", r * 0.95, 29.6, 30.2))
    k.bone("dish_rim", "dish", cubes=k.ring8("rim", r, 29.6, 31.2, 0.8))
    tip = 30.2 + r * 0.6
    k.bone("feed", "dish", cubes=[k.box("feed_rod", -0.5, 30.2, -0.5, 0.5, tip, 0.5)])
    if tier < 3:
        k.bone("glow_beacon", "feed", pivot=(0, tip + 0.6, 0), cubes=[k.box("beacon", -0.8, tip, -0.8, 0.8, tip + 1.2, 0.8)])
    else:  # T3: a big glowing feed horn and two lamps on the yoke
        k.bone("glow_beacon", "feed", pivot=(0, tip + 1, 0), cubes=[k.box("horn", -1.4, tip, -1.4, 1.4, tip + 2, 1.4)])
        k.bone("glow_beacons", "head", pivot=(0, 31, 0), cubes=[k.box("lamp", -4.6, 30.5, -0.6, -3.4, 31.7, 0.6),
                                                                k.box("lamp", 3.4, 30.5, -0.6, 4.6, 31.7, 0.6)])
    if tier >= 2:  # T2/T3: a whip antenna on the head
        k.bone("antenna", "head", cubes=[k.box("whip", 4.5, 26, 1.5, 5.5, 37, 2.5), k.box("whip_tip", 4.2, 37, 1.2, 5.8, 38, 2.8)])
    name = f"satellite_uplink_t{tier}"
    k.write(name, f"{GEO}{name}.geo.json", bounds=(3, 3), offset=(0, 1.5, 0))


def keys(frames):
    return {f"{t:g}": list(v) for t, v in frames}


def blink(length, period=1.0):
    frames, t = [], 0.0
    while t <= length + 1e-6:
        frames.append((round(t, 2), (1, 1, 1)))
        if t + period / 2 <= length:
            frames.append((round(t + period / 2, 2), (0.45, 0.45, 0.45)))
        t += period
    return keys(frames)


def clips(tier):
    spin = {"head": {"rotation": keys([(0, (0, 0, 0)), (2.5, (0, -90, 0)), (5, (0, -180, 0)), (7.5, (0, -270, 0)), (10, (0, -360, 0))])},
            "dish": {"rotation": keys([(0, (0, 0, 0)), (2.5, (-8, 0, 0)), (5, (0, 0, 0)), (7.5, (6, 0, 0)), (10, (0, 0, 0))])}}
    for bone in ["glow_beacon"] + (["glow_beacons"] if tier == 3 else []):
        spin[bone] = {"scale": blink(10)}
    return {"format_version": "1.8.0", "animations": {
        "animation.satellite_uplink.folded": {"animation_length": 1, "loop": True, "bones": {"dish": {"rotation": keys([(0, (65, 0, 0))])}}},
        "animation.satellite_uplink.deploy": {"animation_length": 1.2, "bones": {
            "dish": {"rotation": keys([(0, (65, 0, 0)), (0.8, (-8, 0, 0)), (1.2, (0, 0, 0))])}}},
        "animation.satellite_uplink.spin": {"animation_length": 10, "loop": True, "bones": spin},
        "animation.satellite_uplink.offline": {"animation_length": 1, "loop": True, "bones": {"dish": {"rotation": keys([(0, (35, 0, 0))])}}},
    }}


if __name__ == "__main__":
    for tier in (1, 2, 3):
        satellite(tier)
        (ANIMATIONS / f"satellite_uplink_t{tier}.animation.json").write_text(json.dumps(clips(tier), indent=1) + "\n")
