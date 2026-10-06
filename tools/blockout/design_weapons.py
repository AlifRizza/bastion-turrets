"""Weapon modules v2 (~1 block): shared yoke + per-weapon receiver and barrels. PLAN 6.2 bone names.
Origin = top of the base's turntable (weapon_mount), +Y = mount axis, barrels point north (-Z). Units px."""
import sys
from kit import Kit, GEO

P = 7.0  # pitch axle height above the mount -> pivot_height 0.4375


def yoke(k):
    """Turntable, base plate and two arms holding the pitch axle (all on yaw_pivot)."""
    cubes = k.octagon("turn", 4.6, 0, 1.4)
    cubes.append(k.box("yplate", -5.6, 1.4, -3.4, 5.6, 2.6, 3.4))
    for side in (-1, 1):
        x0, x1 = sorted((side * 5.0, side * 6.6))
        cubes.append(k.box("arm", x0, 2.6, -2.6, x1, P + 1.2, 2.6))
        cubes.append(k.box("armcap", x0, P + 1.2, -1.8, x1, P + 2.2, 1.8))
        cubes.append(k.box("armrib", min(side * 6.6, side * 7.1), 3.2, -1.2, max(side * 6.6, side * 7.1), P - 0.5, 1.2))
    k.bone("root")
    k.bone("yaw_pivot", "root", cubes=cubes)
    k.bone("axle", "yaw_pivot", pivot=(0, P, 0), cubes=k.rod_x("axle", P, 0, -7.2, 7.2, 2.4))
    k.bone("pitch_pivot", "yaw_pivot", pivot=(0, P, 0))


def gun(path):
    k = Kit(128)
    yoke(k)
    body = [k.box("rcv", -4.2, 3.4, -6.0, 4.2, 10.6, 6.0),
            k.box("rcvtop", -3.4, 10.6, -5.0, 3.4, 11.6, 5.0),
            k.box("rcvrear", -3.4, 4.2, 6.0, 3.4, 9.8, 7.6)]
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=body)
    k.bone("body_frame", "body", pivot=(0, P, 0), cubes=[
        k.box("sideplate", -4.9, 4.0, -5.0, -4.2, 10.0, 4.6), k.box("sideplate", 4.2, 4.0, -5.0, 4.9, 10.0, 4.6),
        k.box("chin", -3.0, 2.6, -6.0, 3.0, 3.4, 1.0)])
    k.bone("glow_strips", "body", pivot=(0, P, 0), cubes=[
        k.box("gstrip", -4.95, 8.6, -4.0, -4.85, 9.1, 3.6), k.box("gstrip", 4.85, 8.6, -4.0, 4.95, 9.1, 3.6)])
    k.bone("vent_l", "body", pivot=(-1.6, 11.6, 5.0), cubes=[k.box("vent", -3.0, 11.6, 1.4, -0.4, 12.2, 5.0)])
    k.bone("vent_r", "body", pivot=(1.6, 11.6, 5.0), cubes=[k.box("vent", 0.4, 11.6, 1.4, 3.0, 12.2, 5.0)])
    k.bone("sensor", "body", pivot=(-2.0, 12.2, -2.0), cubes=[
        k.box("scope", -3.3, 11.6, -4.6, -0.7, 13.6, 1.0), k.box("scopehood", -3.5, 13.0, -5.2, -0.5, 13.8, -3.6)])
    k.bone("glow_lens", "sensor", pivot=(-2.0, 12.6, -4.7), cubes=[k.box("lens", -2.8, 11.9, -4.75, -1.2, 13.0, -4.6)])
    barrel = k.rod_z("gbarrel", 0, P, -23.6, -6.0, 2.4) + [
        k.box("shroud", -2.1, P - 2.1, -12.0, 2.1, P + 2.1, -6.0),
        k.box("collar", -1.5, P - 1.5, -18.0, 1.5, P + 1.5, -17.2)]
    k.bone("barrel_0", "body", pivot=(0, P, -6.0), cubes=barrel)
    k.bone("glow_barrel", "barrel_0", pivot=(0, P, -9.0), cubes=[
        k.box("gring", -2.2, P - 2.2, -8.4, 2.2, P + 2.2, -7.8), k.box("gring", -2.2, P - 2.2, -11.0, 2.2, P + 2.2, -10.4)])
    k.bone("muzzle_brake", "barrel_0", pivot=(0, P, -23.6), cubes=[
        k.box("brake", -1.6, P - 1.3, -25.2, 1.6, P + 1.3, -23.4), k.box("brakefin", -1.9, P - 0.4, -24.9, 1.9, P + 0.4, -23.7)])
    k.bone("bore", "barrel_0", pivot=(0, P, -25.2), cubes=[k.box("bore", -0.8, P - 0.8, -25.25, 0.8, P + 0.8, -25.15)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, -25.4))
    k.bone("eject_0", "body", pivot=(4.9, P + 0.5, 0.5))
    k.write("gun_turret", path, bounds=(3, 2.5), offset=(0, 0.5, 0))


def machine_gun(path):
    k = Kit(128)
    yoke(k)
    body = [k.box("rcv", -4.3, 3.0, -5.0, 4.3, 11.0, 6.0),
            k.box("rcvtop", -3.6, 11.0, -4.2, 3.6, 11.8, 6.0)]
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=body)
    k.bone("body_frame", "body", pivot=(0, P, 0), cubes=[
        k.box("ammobox", -3.0, 11.8, -0.5, 3.4, 15.0, 5.6), k.box("ammolid", -3.2, 15.0, 0.0, 3.6, 15.6, 5.2),
        k.box("feed", 3.4, 12.2, 0.6, 4.4, 14.6, 3.4), k.box("sideplate", -4.9, 3.6, -4.0, -4.3, 10.4, 5.0),
        k.box("sideplate", 4.3, 3.6, -4.0, 4.9, 10.4, 5.0), k.box("collar", -3.3, P - 3.3, -6.4, 3.3, P + 3.3, -5.0)])
    k.bone("glow_strips", "body", pivot=(0, P, 0), cubes=[
        k.box("gstrip", -4.95, 9.0, -3.4, -4.85, 9.5, 4.4), k.box("gstrip", 4.85, 9.0, -3.4, 4.95, 9.5, 4.4),
        k.box("ammoglow", -2.4, 13.0, -0.55, 2.8, 13.6, -0.45)])
    k.bone("vent_l", "body", pivot=(-4.9, 5.0, 3.0), cubes=[k.box("vent", -5.0, 4.2, 2.6, -4.85, 6.6, 5.4)])
    k.bone("vent_r", "body", pivot=(4.9, 5.0, 3.0), cubes=[k.box("vent", 4.85, 4.2, 2.6, 5.0, 6.6, 5.4)])
    k.bone("fan", "body", pivot=(0, P, 6.4), cubes=[
        k.box("fanframe", -2.8, P - 2.8, 6.0, 2.8, P + 2.8, 6.8), k.box("blade", -2.3, P - 0.4, 6.8, 2.3, P + 0.4, 7.1),
        k.box("blade", -0.4, P - 2.3, 6.8, 0.4, P + 2.3, 7.1)])
    k.bone("sensor", "body", pivot=(-2.4, 11.8, -3.0), cubes=[k.box("scope", -3.6, 11.0, -4.8, -1.2, 12.6, -1.4)])
    k.bone("glow_lens", "sensor", pivot=(-2.4, 11.8, -4.9), cubes=[k.box("lens", -3.1, 11.3, -4.9, -1.7, 12.3, -4.75)])
    # rotary cluster around the bore axis (0, P): six barrels, rear hub, mid and front clamps
    import math
    cluster = [k.box("hub", -2.9, P - 2.9, -8.0, 2.9, P + 2.9, -6.4),
               k.box("clamp", -2.9, P - 2.9, -14.6, 2.9, P + 2.9, -13.8),
               k.box("front", -2.8, P - 2.8, -21.4, 2.8, P + 2.8, -20.7),
               k.box("spindle", -0.6, P - 0.6, -21.0, 0.6, P + 0.6, -8.0)]
    for i in range(6):
        a = math.radians(60 * i + 30)
        cx, cy = 1.9 * math.cos(a), P + 1.9 * math.sin(a)
        cluster.append(k.box("mgbarrel", cx - 0.55, cy - 0.55, -24.0, cx + 0.55, cy + 0.55, -8.0))
    k.bone("barrel_cluster", "body", pivot=(0, P, -6.4), cubes=cluster)
    k.bone("glow_barrels", "barrel_cluster", pivot=(0, P, -14), cubes=[
        k.box("gring", -3.0, P - 3.0, -13.75, 3.0, P + 3.0, -13.45), k.box("gring", -2.9, P - 2.9, -20.65, 2.9, P + 2.9, -20.35)])
    for i in range(4):  # four of the six barrel tips; the code reads up to four muzzles
        a = math.radians(60 * (i + 1) - 30)
        k.bone(f"muzzle_{i}", "barrel_cluster", pivot=(round(1.9 * math.cos(a), 4), round(P + 1.9 * math.sin(a), 4), -24.2))
    k.bone("eject_0", "body", pivot=(4.9, P - 1.0, 0.5))
    k.write("machine_gun_turret", path, bounds=(3, 2.5), offset=(0, 0.5, 0))


def shotgun(path):
    k = Kit(128)
    yoke(k)
    body = [k.box("rcv", -4.4, 3.4, -5.0, 4.4, 10.4, 6.0),
            k.box("rcvtop", -3.8, 10.4, -4.2, 3.8, 11.0, 5.4)]
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=body)
    fins = [k.box("sink", x - 0.3, 11.0, -3.0, x + 0.3, 12.6, 4.2) for x in (-2.7, -0.9, 0.9, 2.7)]
    k.bone("body_frame", "body", pivot=(0, P, 0), cubes=fins + [
        k.box("sideplate", -4.95, 4.0, -4.0, -4.4, 9.8, 5.0), k.box("sideplate", 4.4, 4.0, -4.0, 4.95, 9.8, 5.0),
        k.box("stock", -3.0, 4.4, 6.0, 3.0, 9.0, 8.0)])
    k.bone("glow_strips", "body", pivot=(0, P, 0), cubes=[
        k.box("gstrip", -5.0, 5.0, -3.4, -4.9, 5.5, 4.4), k.box("gstrip", 4.9, 5.0, -3.4, 5.0, 5.5, 4.4)])
    k.bone("vent_l", "body", pivot=(-4.95, 8.0, 3.0), cubes=[k.box("vent", -5.05, 6.4, 1.0, -4.95, 9.2, 4.6)])
    k.bone("vent_r", "body", pivot=(4.95, 8.0, 3.0), cubes=[k.box("vent", 4.95, 6.4, 1.0, 5.05, 9.2, 4.6)])
    k.bone("sensor", "body", pivot=(2.4, 11.0, 0.0), cubes=[k.box("scope", 1.2, 11.0, -2.6, 3.6, 12.4, 1.6)])
    k.bone("glow_lens", "sensor", pivot=(2.4, 11.7, -2.7), cubes=[k.box("lens", 1.7, 11.2, -2.75, 3.1, 12.2, -2.6)])
    barrels = k.rod_z("sgbarrel", -1.5, P + 0.4, -21.0, -5.0, 2.4) + k.rod_z("sgbarrel", 1.5, P + 0.4, -21.0, -5.0, 2.4)
    barrels.append(k.box("rib", -0.5, P + 1.4, -20.0, 0.5, P + 1.9, -5.0))
    k.bone("barrel_0", "body", pivot=(0, P + 0.4, -5.0), cubes=barrels)
    k.bone("muzzle_shroud", "barrel_0", pivot=(0, P + 0.4, -21.0), cubes=[
        k.box("collar", -2.95, P - 1.05, -21.4, -0.05, P + 1.85, -20.0), k.box("collar", 0.05, P - 1.05, -21.4, 2.95, P + 1.85, -20.0),
        k.box("bridge", -0.6, P - 0.2, -21.2, 0.6, P + 1.0, -20.2)])
    k.bone("bores", "muzzle_shroud", pivot=(0, P + 0.4, -21.4), cubes=[
        k.box("bore", -2.2, P - 0.3, -21.45, -0.8, P + 1.1, -21.35), k.box("bore", 0.8, P - 0.3, -21.45, 2.2, P + 1.1, -21.35)])
    k.bone("glow_barrel", "barrel_0", pivot=(0, P + 0.4, -18.0), cubes=[
        k.box("gring", -3.0, P - 1.1, -18.6, -0.1, P + 1.9, -18.3), k.box("gring", 0.1, P - 1.1, -18.6, 3.0, P + 1.9, -18.3)])
    k.bone("pump", "barrel_0", pivot=(0, P - 1.4, -12.0), cubes=[
        k.box("pump", -2.9, P - 2.4, -15.0, 2.9, P - 0.8, -9.4), k.box("pumpgrip", -3.1, P - 2.2, -14.2, 3.1, P - 1.0, -13.4),
        k.box("pumpgrip", -3.1, P - 2.2, -11.8, 3.1, P - 1.0, -11.0)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P + 0.4, -21.6))
    k.bone("eject_0", "body", pivot=(4.95, P - 0.6, 1.0))
    k.write("shotgun_turret", path, bounds=(3, 2.5), offset=(0, 0.5, 0))


def sniper(path):
    """Long slim rifle with a big scope; the barrel reaches ~1.8 blocks."""
    k = Kit(128)
    yoke(k)
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[
        k.box("rcv", -3.6, 3.6, -8.0, 3.6, 10.2, 7.0), k.box("rcvtop", -2.8, 10.2, -7.0, 2.8, 10.8, 5.0),
        k.box("stock", -2.4, 4.4, 7.0, 2.4, 9.4, 11.0), k.box("cheek", -1.8, 9.4, 7.4, 1.8, 10.4, 10.4)])
    k.bone("body_frame", "body", pivot=(0, P, 0), cubes=[
        k.box("sideplate", -4.3, 4.2, -6.0, -3.6, 9.6, 5.0), k.box("sideplate", 3.6, 4.2, -6.0, 4.3, 9.6, 5.0)])
    k.bone("glow_strips", "body", pivot=(0, P, 0), cubes=[
        k.box("gstrip", -4.4, 8.0, -5.0, -4.3, 8.4, 4.0), k.box("gstrip", 4.3, 8.0, -5.0, 4.4, 8.4, 4.0)])
    k.bone("vent_l", "body", pivot=(-1.4, 10.8, 4.6), cubes=[k.box("vent", -2.4, 10.8, 1.0, -0.4, 11.2, 4.6)])
    k.bone("vent_r", "body", pivot=(1.4, 10.8, 4.6), cubes=[k.box("vent", 0.4, 10.8, 1.0, 2.4, 11.2, 4.6)])
    k.bone("sensor", "body", pivot=(0, 12.3, -2.0), cubes=[
        k.box("scope", -1.1, 11.2, -7.0, 1.1, 13.4, 3.0), k.box("bell", -1.4, 10.9, -8.2, 1.4, 13.7, -7.0),
        k.box("eyepiece", -0.9, 11.4, 3.0, 0.9, 13.2, 4.2),
        k.box("mount", -0.6, 10.8, -4.6, 0.6, 11.2, -3.4), k.box("mount", -0.6, 10.8, 0.6, 0.6, 11.2, 1.8)])
    k.bone("glow_lens", "sensor", pivot=(0, 12.3, -8.25), cubes=[k.box("lens", -1.0, 11.3, -8.3, 1.0, 13.3, -8.2)])
    k.bone("barrel_0", "body", pivot=(0, P, -8.0), cubes=k.rod_z("snbarrel", 0, P, -27.6, -8.0, 1.8) + [
        k.box("heatshroud", -1.3, P - 1.3, -14.0, 1.3, P + 1.3, -8.0)])
    k.bone("glow_barrel", "barrel_0", pivot=(0, P, -11.0), cubes=[
        k.box("gring", -1.4, P - 1.4, -10.4, 1.4, P + 1.4, -10.0), k.box("gring", -1.4, P - 1.4, -13.0, 1.4, P + 1.4, -12.6)])
    k.bone("muzzle_brake", "barrel_0", pivot=(0, P, -27.6), cubes=[
        k.box("brake", -1.1, P - 0.9, -28.6, 1.1, P + 0.9, -27.2), k.box("brakefin", -1.4, P - 0.3, -28.4, 1.4, P + 0.3, -27.4)])
    k.bone("bore", "barrel_0", pivot=(0, P, -28.6), cubes=[k.box("bore", -0.5, P - 0.5, -28.65, 0.5, P + 0.5, -28.55)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, -28.8))
    k.bone("eject_0", "body", pivot=(4.3, P + 0.5, -1.0))
    k.write("sniper_turret", path, bounds=(4, 2.5), offset=(0, 0.5, 0))


TUBES = ((-2.15, P + 2.15), (2.15, P + 2.15), (2.15, P - 2.15), (-2.15, P - 2.15))  # muzzle_0..3, clockwise from top-left


def rocket_launcher(path):
    """Box pod with four tubes (2x2); loaded rocket noses peek out of the dark bores."""
    k = Kit(128)
    yoke(k)
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[
        k.box("pod", -4.6, 2.8, -8.0, 4.6, 11.2, 5.0), k.box("podtop", -4.0, 11.2, -7.0, 4.0, 11.8, 4.0),
        k.box("exhaust", -3.6, 3.6, 5.0, 3.6, 10.4, 6.4)])
    k.bone("body_frame", "body", pivot=(0, P, 0), cubes=[
        k.box("sideplate", -4.95, 3.4, -7.0, -4.6, 10.6, 4.0), k.box("sideplate", 4.6, 3.4, -7.0, 4.95, 10.6, 4.0),
        k.box("faceplate", -4.8, 2.6, -8.8, 4.8, 11.4, -8.0)])
    tubes, bores = [], []
    for cx in (-2.15, 2.15):
        for cy in (P + 2.15, P - 2.15):
            tubes.append(k.box("tube", cx - 1.7, cy - 1.7, -10.2, cx + 1.7, cy + 1.7, -8.8))
            bores.append(k.box("tbore", cx - 1.2, cy - 1.2, -10.25, cx + 1.2, cy + 1.2, -10.2))
    k.bone("tubes", "body", pivot=(0, P, -9.0), cubes=tubes)
    k.bone("bores", "tubes", pivot=(0, P, -10.2), cubes=bores)
    # One loaded rocket per tube, in muzzle order; the client empties a tube when it fires and slides a new one in.
    for i, (cx, cy) in enumerate(TUBES):
        k.bone(f"rocket_{i}", "body", pivot=(cx, cy, -10.4), cubes=[
            k.box("rbody", cx - 0.8, cy - 0.8, -10.4, cx + 0.8, cy + 0.8, -9.4),
            k.box("rnose", cx - 0.55, cy - 0.55, -10.9, cx + 0.55, cy + 0.55, -10.4),
            k.box("rtip", cx - 0.25, cy - 0.25, -11.15, cx + 0.25, cy + 0.25, -10.9)])
    k.bone("glow_strips", "body", pivot=(0, P, 0), cubes=[
        k.box("gband", -4.4, 11.0, -8.95, 4.4, 11.3, -8.85), k.box("gstrip", -5.0, 9.4, -6.0, -4.95, 9.8, 3.0),
        k.box("gstrip", 4.95, 9.4, -6.0, 5.0, 9.8, 3.0)])
    k.bone("vent_l", "body", pivot=(-4.95, 5.6, 3.3), cubes=[k.box("vent", -5.05, 4.2, 2.0, -4.95, 7.0, 4.6)])
    k.bone("vent_r", "body", pivot=(4.95, 5.6, 3.3), cubes=[k.box("vent", 4.95, 4.2, 2.0, 5.05, 7.0, 4.6)])
    k.bone("sensor", "body", pivot=(-2.7, 11.8, -4.0), cubes=[k.box("scope", -3.8, 11.8, -6.0, -1.6, 13.2, -2.0)])
    k.bone("glow_lens", "sensor", pivot=(-2.7, 12.5, -6.05), cubes=[k.box("lens", -3.4, 12.0, -6.1, -2.0, 13.0, -6.0)])
    for i, (cx, cy) in enumerate(TUBES):
        k.bone(f"muzzle_{i}", "body", pivot=(cx, cy, -10.8))
    k.write("rocket_launcher_turret", path, bounds=(3, 2.5), offset=(0, 0.5, 0))


# Missile Launcher tube layout: MUST match MissileLauncherWeapon (POD_X, COLUMN, ROW, MOUTH) and its tube numbering.
ML_P, ML_POD_X, ML_COLUMN, ML_ROW, ML_MOUTH = 12.0, 11.75, 2.9, 5.2, 12.4


def ml_tube(i):
    """Tube i -> (x, y) in px: pod i // 6 (0 = -X side), row (top first), column (inner first)."""
    pod, k = divmod(i, 6)
    row, col = divmod(k, 2)
    x = (-1 if pod == 0 else 1) * (ML_POD_X + (-ML_COLUMN if col == 0 else ML_COLUMN))
    return x, ML_P + (1 - row) * ML_ROW


def missile_launcher(path):
    """Big module for the Large Turret Base: a turntable and pedestal (yaw only), a centre housing on the axle and two
    pods of six tubes each, held at a fixed upward angle by the code. Every tube shows its missile nose while loaded."""
    k = Kit(128)
    P = ML_P
    k.bone("root")
    k.bone("yaw_pivot", "root", cubes=k.octagon("turn", 7.5, 0, 1.6) + [
        k.box("pedestal", -4.5, 1.6, -5, 4.5, P - 3, 5),
        k.box("holder", -6.4, P - 3, -2.2, -4.5, P + 2, 2.2), k.box("holder", 4.5, P - 3, -2.2, 6.4, P + 2, 2.2)])
    k.bone("axle", "yaw_pivot", pivot=(0, P, 0), cubes=k.rod_x("axle", P, 0, -6.6, 6.6, 2.2))
    k.bone("pitch_pivot", "yaw_pivot", pivot=(0, P, 0))
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[
        k.box("housing", -4.5, P - 4.6, -7, 4.5, P + 4.6, 7), k.box("housingcap", -3.6, P + 4.6, -5, 3.6, P + 5.4, 5)])
    for side, name in ((-1, "pod_l"), (1, "pod_r")):
        x0, x1 = sorted((side * 5.5, side * 18))
        outer = side * 18
        cubes = [k.box("pod", x0, P - 8, -10, x1, P + 8, 9),
                 k.box("podlip", x0 - 0.3, P - 8.3, -10.4, x1 + 0.3, P + 8.3, -9.6)]
        for gx in (side * 8.5, side * 15):  # grooves along the top
            cubes.append(k.box("groove", gx - 0.5, P + 8, -8, gx + 0.5, P + 8.3, 7))
        e0, e1 = sorted((outer, outer + side * 0.4))
        cubes.append(k.box("emblem", e0, P - 5, -6, e1, P + 5, 6))
        k.bone(name, "body", pivot=(side * 11.75, P, 0), cubes=cubes)
        tubes = range(0, 6) if side < 0 else range(6, 12)
        bores = [k.box("bore", x - 2, y - 2, -10.45, x + 2, y + 2, -10.4) for x, y in map(ml_tube, tubes)]
        k.bone(name + "_bores", name, pivot=(side * 11.75, P, -10.4), cubes=bores)
        gl0, gl1 = sorted((outer, outer + side * 0.1))
        k.bone("glow_" + name, name, pivot=(side * 11.75, P + 6.5, 0), cubes=[k.box("podglow", gl0, P + 6.2, -9, gl1, P + 6.8, 8)])
    for i in range(12):
        x, y = ml_tube(i)
        k.bone(f"missile_{i}", "pod_l" if i < 6 else "pod_r", pivot=(x, y, -10.4), cubes=[
            k.box("mnose", x - 1.6, y - 1.6, -11, x + 1.6, y + 1.6, -10.4),
            k.box("mtip", x - 0.9, y - 0.9, -11.8, x + 0.9, y + 0.9, -11),
            k.box("mpoint", x - 0.4, y - 0.4, -12.2, x + 0.4, y + 0.4, -11.8)])
        k.bone(f"muzzle_{i}", "pod_l" if i < 6 else "pod_r", pivot=(x, y, -ML_MOUTH))
    k.bone("sensor", "body", pivot=(0, P + 6, -2), cubes=[k.box("scope", -1.6, P + 5.4, -4, 1.6, P + 7, 1)])
    k.bone("glow_lens", "sensor", pivot=(0, P + 6.2, -4.05), cubes=[k.box("lens", -1, P + 5.7, -4.1, 1, P + 6.7, -4)])
    k.bone("glow_strips", "body", pivot=(0, P, 0), cubes=[k.box("gstrip", -4.6, P + 2.6, -6, -4.5, P + 3.1, 6),
                                                          k.box("gstrip", 4.5, P + 2.6, -6, 4.6, P + 3.1, 6)])
    k.write("missile_launcher_turret", path, bounds=(3, 3), offset=(0, 0.6, 0))


def missile(path):
    """Missile projectile: slimmer than the rocket, origin at its centre, nose north (-Z)."""
    k = Kit(32)
    k.bone("root")
    k.bone("body", "root", cubes=k.rod_z("mbody", 0, 0, -2.4, 2.4, 1.2, 0.9))
    k.bone("nose", "body", pivot=(0, 0, -2.4), cubes=[k.box("nose", -0.4, -0.4, -3.0, 0.4, 0.4, -2.4),
                                                       k.box("tip", -0.18, -0.18, -3.4, 0.18, 0.18, -3.0)])
    k.bone("fins", "body", pivot=(0, 0, 1.8), cubes=[k.box("finh", -1.3, -0.1, 1.3, 1.3, 0.1, 2.4),
                                                      k.box("finv", -0.1, -1.3, 1.35, 0.1, 1.3, 2.35)])
    k.bone("glow_nozzle", "body", pivot=(0, 0, 2.5), cubes=[k.box("nozzle", -0.45, -0.45, 2.4, 0.45, 0.45, 2.7)])
    k.write("turret_missile", path, bounds=(1, 1), offset=(0, 0, 0))


def rocket(path):
    """The projectile: origin at its centre, nose north (-Z)."""
    k = Kit(32)
    k.bone("root")
    k.bone("body", "root", cubes=k.rod_z("rbody", 0, 0, -2.6, 2.6, 1.6, 1.2))
    k.bone("nose", "body", pivot=(0, 0, -2.6), cubes=[k.box("nose", -0.55, -0.55, -3.4, 0.55, 0.55, -2.6),
                                                       k.box("tip", -0.25, -0.25, -3.8, 0.25, 0.25, -3.4)])
    k.bone("fins", "body", pivot=(0, 0, 2.0), cubes=[k.box("finh", -1.8, -0.12, 1.2, 1.8, 0.12, 2.6),
                                                      k.box("finv", -0.12, -1.8, 1.25, 0.12, 1.8, 2.55)])
    k.bone("glow_nozzle", "body", pivot=(0, 0, 2.7), cubes=[k.box("nozzle", -0.6, -0.6, 2.6, 0.6, 0.6, 2.9)])
    k.write("turret_rocket", path, bounds=(1, 1), offset=(0, 0, 0))



def flamethrower(path):
    """Flamethrower: a stubby receiver with two fuel tanks strapped along its back, a finned flame tube, a flared nozzle
    and an igniter rod under the mouth. muzzle_0 = the nozzle mouth, muzzle_1 = down the bore (the barrel's direction)."""
    k = Kit(128)
    yoke(k)
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[
        k.box("rcv", -4.0, 3.4, -5.0, 4.0, 10.2, 5.4), k.box("rcvtop", -3.2, 10.2, -4.2, 3.2, 10.8, 4.6),
        k.box("manifold", -3.0, 4.2, 5.4, 3.0, 8.8, 7.0)])
    k.bone("body_frame", "body", pivot=(0, P, 0), cubes=[
        k.box("sideplate", -4.6, 4.0, -4.0, -4.0, 9.6, 4.4), k.box("sideplate", 4.0, 4.0, -4.0, 4.6, 9.6, 4.4),
        k.box("strap", -4.2, 10.8, 0.6, 4.2, 14.8, 1.4), k.box("strap", -4.2, 10.8, 4.0, 4.2, 14.8, 4.8)])
    tanks = []
    for x in (-2.1, 2.1):
        tanks += k.rod_z("tank", x, 12.6, -2.6, 6.6, 3.6)
        tanks += [k.box("tankcap", x - 1.2, 11.4, 6.6, x + 1.2, 13.8, 7.4), k.box("valve", x - 0.5, 14.4, -2.2, x + 0.5, 15.2, -1.2)]
    k.bone("tanks", "body", pivot=(0, 12.6, 2.0), cubes=tanks)
    k.bone("feed", "body", pivot=(0, 10.5, -3.6), cubes=[k.box("pipe", -0.6, 10.8, -4.0, 0.6, 12.2, -2.6),
                                                        k.box("pipe", -0.6, 10.2, -5.4, 0.6, 11.4, -4.0)])
    tube = k.rod_z("ftube", 0, P, -16.0, -5.0, 3.4)
    tube += [k.box("fin", -2.4, P - 2.4, z - 0.6, 2.4, P + 2.4, z) for z in (-7.0, -9.0, -11.0, -13.0)]
    k.bone("barrel_0", "body", pivot=(0, P, -5.0), cubes=tube)
    k.bone("glow_barrel", "barrel_0", pivot=(0, P, -14.6), cubes=[k.box("gring", -2.0, P - 2.0, -14.9, 2.0, P + 2.0, -14.3)])
    k.bone("nozzle", "barrel_0", pivot=(0, P, -16.0), cubes=[
        k.box("nzl", -1.9, P - 1.9, -18.6, 1.9, P + 1.9, -16.0), k.box("nzllip", -2.4, P - 2.4, -19.6, 2.4, P + 2.4, -18.6)])
    k.bone("bore", "nozzle", pivot=(0, P, -19.6), cubes=[k.box("bore", -1.7, P - 1.7, -19.65, 1.7, P + 1.7, -19.55)])
    k.bone("igniter", "barrel_0", pivot=(0, P - 2.9, -15.0), cubes=[k.box("ign", -0.4, P - 3.3, -19.4, 0.4, P - 2.5, -12.0)])
    k.bone("glow_igniter", "igniter", pivot=(0, P - 2.9, -19.7), cubes=[k.box("igntip", -0.55, P - 3.45, -20.0, 0.55, P - 2.35, -19.4)])
    k.bone("glow_strips", "body", pivot=(0, P, 0), cubes=[
        k.box("gstrip", -4.7, 7.6, -3.4, -4.6, 8.1, 3.8), k.box("gstrip", 4.6, 7.6, -3.4, 4.7, 8.1, 3.8)])
    k.bone("vent_l", "body", pivot=(-4.7, 5.4, 2.0), cubes=[k.box("vent", -4.75, 4.4, 0.6, -4.6, 6.6, 3.6)])
    k.bone("vent_r", "body", pivot=(4.7, 5.4, 2.0), cubes=[k.box("vent", 4.6, 4.4, 0.6, 4.75, 6.6, 3.6)])
    k.bone("sensor", "body", pivot=(-2.6, 10.8, -3.0), cubes=[k.box("scope", -3.6, 10.8, -4.6, -1.6, 12.2, -1.4)])
    k.bone("glow_lens", "sensor", pivot=(-2.6, 11.5, -4.7), cubes=[k.box("lens", -3.2, 11.0, -4.7, -2.0, 12.0, -4.6)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, -20.0))
    k.bone("muzzle_1", "barrel_0", pivot=(0, P, -16.0))
    k.write("flamethrower_turret", path, bounds=(3, 2.5), offset=(0, 0.5, 0))


def laser_rifle(path):
    """Laser Rifle: a capacitor receiver with coil packs on its flanks, a long barrel with focusing rings (the client
    spins focus_rings up while it charges) and a crystal emitter between four prongs at the tip, where the light gathers
    (muzzle_0)."""
    k = Kit(128)
    yoke(k)
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[
        k.box("rcv", -3.8, 3.6, -7.0, 3.8, 10.0, 6.0), k.box("rcvtop", -3.0, 10.0, -6.0, 3.0, 10.8, 4.6),
        k.box("stock", -2.4, 4.6, 6.0, 2.4, 9.0, 9.0)])
    k.bone("body_frame", "body", pivot=(0, P, 0), cubes=[
        k.box("coilpack", -5.0, 4.4, -5.0, -3.8, 9.2, 3.0), k.box("coilpack", 3.8, 4.4, -5.0, 5.0, 9.2, 3.0),
        k.box("capacitor", -2.2, 10.8, -4.0, 2.2, 12.2, 2.4)])
    k.bone("glow_strips", "body", pivot=(0, P, 0), cubes=[
        k.box("gstrip", -5.05, 6.4, -4.4, -4.95, 7.2, 2.4), k.box("gstrip", 4.95, 6.4, -4.4, 5.05, 7.2, 2.4),
        k.box("capglow", -1.6, 12.2, -3.4, 1.6, 12.3, 1.8)])
    k.bone("vent_l", "body", pivot=(-3.85, 5.2, 4.5), cubes=[k.box("vent", -3.9, 4.0, 3.4, -3.75, 6.4, 5.6)])
    k.bone("vent_r", "body", pivot=(3.85, 5.2, 4.5), cubes=[k.box("vent", 3.75, 4.0, 3.4, 3.9, 6.4, 5.6)])
    k.bone("sensor", "body", pivot=(-2.9, 11.4, -4.0), cubes=[k.box("scope", -3.4, 10.8, -5.6, -2.4, 12.0, -2.6)])
    k.bone("glow_lens", "sensor", pivot=(-2.9, 11.4, -5.65), cubes=[k.box("lens", -3.3, 11.0, -5.65, -2.5, 11.8, -5.6)])
    k.bone("barrel_0", "body", pivot=(0, P, -7.0), cubes=k.rod_z("lbarrel", 0, P, -24.0, -7.0, 2.0) + [
        k.box("shroud", -1.6, P - 1.6, -12.0, 1.6, P + 1.6, -7.0)])
    rings, glow = [], []
    for z in (-14.0, -17.5, -21.0):
        rings += [k.box("ring", -2.2, P - 2.2, z - 0.7, 2.2, P + 2.2, z), k.box("ringfin", -2.6, P - 0.35, z - 0.6, 2.6, P + 0.35, z - 0.1)]
        glow.append(k.box("focusglow", -1.7, P - 1.7, z, 1.7, P + 1.7, z + 0.5))
    k.bone("focus_rings", "barrel_0", pivot=(0, P, -17.5), cubes=rings)
    k.bone("glow_focus", "focus_rings", pivot=(0, P, -17.5), cubes=glow)
    prongs = [k.box("prong", sx * 1.3 - 0.25, P + sy * 1.3 - 0.25, -27.2, sx * 1.3 + 0.25, P + sy * 1.3 + 0.25, -26.0)
              for sx in (-1, 1) for sy in (-1, 1)]
    k.bone("emitter", "barrel_0", pivot=(0, P, -24.0), cubes=[k.box("emhousing", -1.5, P - 1.5, -26.0, 1.5, P + 1.5, -24.0)] + prongs)
    k.bone("glow_emitter", "emitter", pivot=(0, P, -26.4), cubes=[k.box("crystal", -0.7, P - 0.7, -26.8, 0.7, P + 0.7, -26.0)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, -27.6))
    k.write("laser_rifle_turret", path, bounds=(4, 2.5), offset=(0, 0.5, 0))


# Tesla Coil: the terminal on top is the pivot the code fires from: pivot_height = TESLA_TERMINAL / 16 in tesla.json.
TESLA_TERMINAL = 31.0


def tesla(path):
    """Tesla Coil for the Large Turret Base: capacitor housing with four cans, a column wound with coil rings (glowing
    between them), a toroid crown, a terminal on top and four electrode posts. It never turns or tilts. muzzle_0 = the
    terminal, muzzle_1..4 = the crown rim (charging arcs leap off them)."""
    k = Kit(128)
    k.bone("root")
    k.bone("yaw_pivot", "root", cubes=k.octagon("turn", 11, 0, 1.6))
    k.bone("pitch_pivot", "yaw_pivot", pivot=(0, TESLA_TERMINAL, 0))
    k.bone("body", "yaw_pivot", pivot=(0, 1.6, 0), cubes=k.chamfer("housing", 8, 2.5, 1.6, 9.0) + k.chamfer("lid", 6.6, 2, 9.0, 10.0))
    k.bone("body_frame", "body", pivot=(0, 1.6, 0), cubes=k.faces4("cap", 8, 2.6, 8.0, 4.0, 1.4))
    k.bone("glow_strips", "body", pivot=(0, 6.9, 0), cubes=k.faces4("gstrip", 9.4, 6.6, 7.2, 2.4, 0.1))
    k.bone("column", "body", pivot=(0, 10, 0), cubes=k.octagon("column", 2.4, 10, 24.2))
    rings, glow = [], []
    for i in range(6):
        y = 11 + 2.2 * i
        rings += k.octagon("coilring", 3.9, y, y + 1.1)
        glow += k.octagon("coilglow", 3.3, y + 1.1, y + 2.2)
    k.bone("coil", "column", pivot=(0, 10, 0), cubes=rings)
    k.bone("glow_coil", "column", pivot=(0, 10, 0), cubes=glow)
    k.bone("crown", "column", pivot=(0, 25.2, 0), cubes=k.octagon("hub", 4.2, 24.0, 26.4) + k.ring8("toroid", 4.2, 23.6, 26.8, 3.6)
           + k.ring8("toroidlip", 4.6, 26.8, 27.4, 2.8) + k.ring8("toroidbase", 4.6, 23.0, 23.6, 2.8))
    k.bone("glow_crown", "crown", pivot=(0, 25.2, 0), cubes=k.ring8("crownglow", 7.8, 24.9, 25.5, 0.12))
    k.bone("terminal", "crown", pivot=(0, 28.6, 0), cubes=k.octagon("term", 1.5, 27.4, 29.8) + [k.box("termcap", -1.0, 29.8, -1.0, 1.0, 30.6, 1.0)])
    k.bone("glow_terminal", "terminal", pivot=(0, 28.6, 0), cubes=k.octagon("termglow", 1.65, 28.3, 28.8))
    posts, tips = [], []
    for x in (-6.2, 6.2):
        for z in (-6.2, 6.2):
            posts += [k.box("post", x - 0.5, 9.0, z - 0.5, x + 0.5, 15.0, z + 0.5), k.box("ball", x - 0.9, 15.0, z - 0.9, x + 0.9, 16.6, z + 0.9)]
            tips.append(k.box("tip", x - 0.6, 16.6, z - 0.6, x + 0.6, 17.0, z + 0.6))
    k.bone("electrodes", "body", pivot=(0, 9.0, 0), cubes=posts)
    k.bone("glow_electrodes", "electrodes", pivot=(0, 16.8, 0), cubes=tips)
    k.bone("sensor", "body", pivot=(0, 10.8, -5.6), cubes=[k.box("scope", -1.4, 10.0, -6.6, 1.4, 11.6, -4.6)])
    k.bone("glow_lens", "sensor", pivot=(0, 10.8, -6.65), cubes=[k.box("lens", -0.9, 10.3, -6.65, 0.9, 11.3, -6.6)])
    k.bone("muzzle_0", "terminal", pivot=(0, TESLA_TERMINAL, 0))
    for i, (x, z) in enumerate(((0, -7.9), (7.9, 0), (0, 7.9), (-7.9, 0))):
        k.bone(f"muzzle_{i + 1}", "crown", pivot=(x, 25.2, z))
    k.write("tesla_turret", path, bounds=(3, 3), offset=(0, 1, 0))

out = sys.argv[1] if len(sys.argv) > 1 else GEO
gun(f"{out}/gun_turret.geo.json")
machine_gun(f"{out}/machine_gun_turret.geo.json")
shotgun(f"{out}/shotgun_turret.geo.json")
sniper(f"{out}/sniper_turret.geo.json")
rocket_launcher(f"{out}/rocket_launcher_turret.geo.json")
rocket(f"{out}/entity/turret_rocket.geo.json")
missile_launcher(f"{out}/missile_launcher_turret.geo.json")
missile(f"{out}/entity/turret_missile.geo.json")
flamethrower(f"{out}/flamethrower_turret.geo.json")
tesla(f"{out}/tesla_turret.geo.json")
laser_rifle(f"{out}/laser_rifle_turret.geo.json")
