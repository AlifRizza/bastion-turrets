"""Weapon modules v2 (~1 block): shared yoke + per-weapon receiver and barrels. PLAN 6.2 bone names.
Origin = top of the base's turntable (weapon_mount), +Y = mount axis, barrels point north (-Z). Units px."""
import math
import sys
from kit import Kit, GEO, TAN22

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
        k.box("bladev", -0.4, P - 2.3, 6.8, 0.4, P + 2.3, 7.1)])
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
        k.box("gring", -3.0, P - 3.0, -13.75, 3.0, P + 3.0, -13.45), k.box("gring2", -2.9, P - 2.9, -20.65, 2.9, P + 2.9, -20.35)])
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
                                                        k.box("pipe2", -0.6, 10.2, -5.4, 0.6, 11.4, -4.0)])
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

# Railgun: pitch axle height and where the channel runs; railgun.json pivot_height = RG_P / 16, muzzle_length = -RG_TIP / 16.
RG_P, RG_BREECH, RG_TIP = 18.0, -22.0, -74.5
RG_COILS = (-30.0, -39.0, -48.0, -57.0, -66.0)  # coil clamps along the rails (RailgunEffects lights them in this order)


def railgun(path):
    """Railgun for the Large Turret Base (user reference: a heavy two-rail gun on a yoke, drum magazine on its side).
    A turntable and pedestal with two arms, a long receiver on the axle with the drum on its left, and a barrel of two
    rails (upper and lower) with a glowing channel between them, held by five coil clamps; ~4.6 blocks from the axle to
    the muzzle. muzzle_0 = the muzzle, muzzle_1 = the back of the channel, muzzle_2/3 = the upper/lower rail tips (the
    client lights the channel and runs arcs along the rails from these); eject_0 = the vents (steam after a shot)."""
    k = Kit(256)  # long rails: their faces do not fit a 128 sheet
    P = RG_P
    k.bone("root")
    yaw = k.octagon("turn", 12, 0, 1.6) + k.chamfer("pedestal", 9, 2.5, 1.6, 6.0)
    for side in (-1, 1):
        x0, x1 = sorted((side * 9.4, side * 12.6))
        yaw += [k.box("arm", x0, 6.0, -5.0, x1, P + 3.5, 5.0), k.box("armcap", x0, P + 3.5, -3.4, x1, P + 5.0, 3.4)]
        r0, r1 = sorted((side * 12.6, side * 13.4))
        yaw.append(k.box("armrib", r0, 6.6, -2.0, r1, P - 1.5, 2.0))
    k.bone("yaw_pivot", "root", cubes=yaw)
    k.bone("axle", "yaw_pivot", pivot=(0, P, 0), cubes=k.rod_x("axle", P, 0, -13.0, 13.0, 3.4))
    k.bone("pitch_pivot", "yaw_pivot", pivot=(0, P, 0))
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[
        k.box("rcv", -7.5, P - 6.0, -20.0, 7.5, P + 6.0, 9.0), k.box("rcvtop", -6.0, P + 6.0, -17.0, 6.0, P + 7.4, 6.0),
        k.box("rcvrear", -6.0, P - 4.0, 9.0, 6.0, P + 4.0, 11.5), k.box("chin", -5.0, P - 7.2, -18.0, 5.0, P - 6.0, 2.0)])
    k.bone("body_frame", "body", pivot=(0, P, 0), cubes=[
        k.box("sideplate", -8.3, P - 5.0, -18.0, -7.5, P + 5.0, 7.0), k.box("sideplate", 7.5, P - 5.0, -18.0, 8.3, P + 5.0, 7.0),
        k.box("rearplate", -5.0, P - 3.0, 11.5, 5.0, P + 3.0, 12.1)])
    # Drum magazine on the weapon's left (geo +X, GeckoLib mirrors X): outboard of the arms and forward of them, so it
    # clears them over the whole pitch range; a bracket ties it to the receiver.
    # The fire clip turns the drum a quarter (square caps, so the turn loops without a jump); bracket and gauge stay put.
    k.bone("drum", "body", pivot=(17.4, P - 0.5, -13.5), cubes=k.rod_z("drum", 17.4, P - 0.5, -20.0, -7.0, 7.6) + [
        k.box("drumcap", 14.0, P - 3.9, -20.8, 20.8, P + 2.9, -19.8), k.box("drumcap", 14.0, P - 3.9, -7.2, 20.8, P + 2.9, -6.2)])
    k.bone("drum_mount", "body", pivot=(11.0, P, -13.5), cubes=[k.box("drumbracket", 8.3, P - 2.0, -17.0, 13.8, P + 1.0, -10.0)])
    k.bone("glow_drum", "body", pivot=(17.4, P - 0.5, -13.5), cubes=[k.box("drumglow", 21.25, P - 1.2, -18.0, 21.35, P + 0.2, -9.0)])
    k.bone("glow_strips", "body", pivot=(0, P, 0), cubes=[
        k.box("gstrip", -8.4, P + 2.6, -16.0, -8.3, P + 3.2, 5.0), k.box("gstrip", 8.3, P + 2.6, -16.0, 8.4, P + 3.2, 5.0)])
    k.bone("vent_l", "body", pivot=(-2.5, P + 7.4, 5.0), cubes=[k.box("vent", -4.6, P + 7.4, -2.0, -0.6, P + 8.0, 5.0)])
    k.bone("vent_r", "body", pivot=(2.5, P + 7.4, 5.0), cubes=[k.box("vent", 0.6, P + 7.4, -2.0, 4.6, P + 8.0, 5.0)])
    k.bone("sensor", "body", pivot=(-3.8, P + 8.6, -12.0), cubes=[k.box("scope", -5.6, P + 7.4, -16.0, -2.0, P + 9.8, -8.0)])
    k.bone("glow_lens", "sensor", pivot=(-3.8, P + 8.6, -16.05), cubes=[k.box("lens", -5.0, P + 7.8, -16.1, -2.6, P + 9.4, -16.0)])
    # The barrel slides back on each shot (fire clip): breech block, two rails with the channel between, spines.
    barrel = [k.box("breech", -6.2, P - 6.6, -24.0, 6.2, P + 6.6, -20.0),
              k.box("rail", -4.5, P + 1.5, RG_TIP + 1.0, 4.5, P + 5.5, -24.0), k.box("rail", -4.5, P - 5.5, RG_TIP + 1.0, 4.5, P - 1.5, -24.0),
              k.box("spine", -1.6, P + 5.5, -68.0, 1.6, P + 6.8, -24.0), k.box("spine", -1.6, P - 6.8, -68.0, 1.6, P - 5.5, -24.0),
              k.box("railtip", -5.0, P + 1.5, RG_TIP - 0.5, 5.0, P + 6.2, RG_TIP + 1.0),
              k.box("railtip", -5.0, P - 6.2, RG_TIP - 0.5, 5.0, P - 1.5, RG_TIP + 1.0)]
    k.bone("barrel_0", "body", pivot=(0, P, -20.0), cubes=barrel)
    k.bone("coils", "barrel_0", pivot=(0, P, -48.0), cubes=[k.box("coil", -5.8, P - 7.0, z, 5.8, P + 7.0, z + 2.6) for z in RG_COILS])
    for i, z in enumerate(RG_COILS):  # one bone per band: while charging, the client lights them breech to muzzle
        k.bone(f"glow_coil_{i}", "coils", pivot=(0, P, z + 1.3), cubes=[k.box("coilglow", -5.9, P - 7.1, z + 1.0, 5.9, P + 7.1, z + 1.6)])
    k.bone("glow_channel", "barrel_0", pivot=(0, P, -48.0), cubes=[k.box("channel", -2.6, P - 0.35, RG_TIP + 2.0, 2.6, P + 0.35, -24.0)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, RG_TIP))
    k.bone("muzzle_1", "barrel_0", pivot=(0, P, RG_BREECH))
    k.bone("muzzle_2", "barrel_0", pivot=(0, P + 3.5, RG_TIP))
    k.bone("muzzle_3", "barrel_0", pivot=(0, P - 3.5, RG_TIP))
    k.bone("eject_0", "body", pivot=(0, P + 8.2, 1.5))
    k.write("railgun_turret", path, bounds=(6, 4), offset=(0, 1, 0))


# Mortar: trunnion height and barrel tip; mortar.json pivot_height = MO_P / 16, muzzle_length = -MO_TIP / 16.
MO_P, MO_TIP = 13.0, -24.5


def mortar(path):
    """Mortar for the Large Turret Base (user reference: a squat riveted armoured housing on a turning ring, a short fat
    barrel standing steeply up). The barrel pivots on trunnions in two cheeks on top of the housing; the code keeps it
    between 45 and 85 degrees up, so its breech sinks into the housing. muzzle_0 = the barrel's mouth."""
    k = Kit(128)
    P = MO_P
    k.bone("root")
    yaw = (k.octagon("turn", 12, 0, 1.6) + k.chamfer("housing", 10.5, 3, 1.6, 9.5) + k.chamfer("hood", 8, 2.5, 9.5, 12.5)
           + k.faces4("plate", 10.5, 3.0, 8.5, 9.0, 0.8))
    for side in (-1, 1):
        x0, x1 = sorted((side * 4.6, side * 7.6))
        yaw.append(k.box("cheek", x0, 12.5, -4.0, x1, P + 3.0, 4.0))
    k.bone("yaw_pivot", "root", cubes=yaw)
    k.bone("glow_strips", "yaw_pivot", pivot=(0, 6.0, 0), cubes=k.faces4("gstrip", 11.3, 5.7, 6.3, 6.0, 0.1))
    k.bone("vent_l", "yaw_pivot", pivot=(-3.5, 12.5, 6.5), cubes=[k.box("vent", -6.0, 12.5, 4.5, -1.0, 13.1, 7.5)])
    k.bone("vent_r", "yaw_pivot", pivot=(3.5, 12.5, 6.5), cubes=[k.box("vent", 1.0, 12.5, 4.5, 6.0, 13.1, 7.5)])
    k.bone("sensor", "yaw_pivot", pivot=(-8.5, 11.0, -6.0), cubes=[k.box("scope", -9.6, 9.5, -8.0, -7.0, 12.5, -4.0)])
    k.bone("glow_lens", "sensor", pivot=(-8.3, 11.0, -8.05), cubes=[k.box("lens", -9.2, 10.2, -8.1, -7.4, 11.8, -8.0)])
    k.bone("axle", "yaw_pivot", pivot=(0, P, 0), cubes=k.rod_x("axle", P, 0, -8.2, 8.2, 3.6))
    k.bone("pitch_pivot", "yaw_pivot", pivot=(0, P, 0))
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[k.box("breech", -3.8, P - 3.8, -2.0, 3.8, P + 3.8, 6.0),
                                                          k.box("breechcap", -2.8, P - 2.8, 6.0, 2.8, P + 2.8, 7.2)])
    # The barrel slides down into the breech on each shot (fire clip).
    k.bone("barrel_0", "body", pivot=(0, P, -2.0), cubes=k.rod_z("mbarrel", 0, P, MO_TIP + 1.5, -2.0, 8.0, 6.4) + [
        k.box("collar", -4.7, P - 4.7, -6.5, 4.7, P + 4.7, -3.0), k.box("lip", -4.5, P - 4.5, MO_TIP, 4.5, P + 4.5, MO_TIP + 1.5)])
    k.bone("glow_barrel", "barrel_0", pivot=(0, P, -4.75), cubes=[k.box("collarglow", -4.8, P - 4.8, -5.1, 4.8, P + 4.8, -4.4)])
    k.bone("bore", "barrel_0", pivot=(0, P, MO_TIP), cubes=[k.box("bore", -2.8, P - 2.8, MO_TIP - 0.05, 2.8, P + 2.8, MO_TIP + 0.05)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, MO_TIP - 0.3))
    k.write("mortar_turret", path, bounds=(3, 3), offset=(0, 1, 0))


def mortar_shell(path):
    """Mortar shell: a fat teardrop with tail fins, origin at its centre, nose north (-Z); the tail fuse glows."""
    k = Kit(32)
    k.bone("root")
    k.bone("body", "root", cubes=k.rod_z("sbody", 0, 0, -1.6, 1.6, 2.4, 1.8))
    k.bone("nose", "body", pivot=(0, 0, -1.6), cubes=[k.box("nose", -0.8, -0.8, -2.6, 0.8, 0.8, -1.6),
                                                       k.box("tip", -0.35, -0.35, -3.1, 0.35, 0.35, -2.6)])
    k.bone("fins", "body", pivot=(0, 0, 2.4), cubes=[k.box("tail", -0.6, -0.6, 1.6, 0.6, 0.6, 3.2),
                                                      k.box("finh", -1.4, -0.1, 2.4, 1.4, 0.1, 3.4),
                                                      k.box("finv", -0.1, -1.4, 2.45, 0.1, 1.4, 3.35)])
    k.bone("glow_nozzle", "body", pivot=(0, 0, 3.3), cubes=[k.box("fuse", -0.4, -0.4, 3.2, 0.4, 0.4, 3.5)])
    k.write("turret_mortar_shell", path, bounds=(1, 1), offset=(0, 0, 0))



# Javelin Missile (Fortress Turret Base): muzzle_0 = the silo mouth, JavelinWeapon.MOUTH = JV_MOUTH / 16; the loaded
# missile's tail sits at JV_SEAT, where the flying one starts (JavelinWeapon.SEAT = JV_SEAT / 16).
JV_MOUTH, JV_SEAT, JV_TOP, JV_WELL = 30.0, 4.0, 28.0, 5.0
JV_MAST = (-17.0, 10.0)  # radar mast axis (x, z) on the weapon's right rear: the yaw_pivot turns about it


def javelin_turret(path):
    """Javelin Missile for the Fortress Turret Base (PLAN "Turret 3x3"): an armoured vertical launch silo that stands
    still on a deck plate, tapering in steps, with two blast doors over its well (they open while it aims) and the loaded
    missile's nose in the well under them; beside it a radar mast that turns to face the target (yaw_pivot about the
    mast), its dish sweeping while idle, lens glowing. muzzle_0 = the silo mouth; vents = the uptake hatches."""
    k = Kit(256)  # a 2.5-block deck plate: its faces do not fit a 128 sheet
    mx, mz = JV_MAST
    k.bone("root")
    k.bone("deck", "root", cubes=k.octagon("deckplate", 20, 0, 1.6) + k.octagon("skirt", 18.5, -3, 0))
    silo = k.chamfer("plinth", 14, 3, 1.6, 4) + k.chamfer("lower", 13, 3, 4, 15) + k.chamfer("upper", 12, 2.6, 15, 21)
    w = JV_WELL
    silo += [k.box("rimn", -10.5, 21, -10.5, 10.5, JV_TOP, -w), k.box("rimn", -10.5, 21, w, 10.5, JV_TOP, 10.5),
             k.box("rimw", -10.5, 21, -w, -w, JV_TOP, w), k.box("rimw", w, 21, -w, 10.5, JV_TOP, w)]
    # Sloped glacis plates on the lower body's four sides, leaning in toward the top (a thick armoured trapezoid).
    silo += [k.box("glacis", -11, 3.5, -15.2, 11, 15, -14.0, rot=[-16, 0, 0], pivot=[0, 3.5, -14.6]),
             k.box("glacis", -11, 3.5, 14.0, 11, 15, 15.2, rot=[16, 0, 0], pivot=[0, 3.5, 14.6]),
             k.box("glacisx", -15.2, 3.5, -11, -14.0, 15, 11, rot=[0, 0, 16], pivot=[-14.6, 3.5, 0]),
             k.box("glacisx", 14.0, 3.5, -11, 15.2, 15, 11, rot=[0, 0, -16], pivot=[14.6, 3.5, 0])]
    k.bone("silo", "root", cubes=silo)
    k.bone("well", "silo", pivot=(0, 21, 0), cubes=[k.box("well", -w, 20.95, -w, w, 21.05, w)])
    k.bone("glow_strips", "silo", pivot=(0, 17, 0), cubes=k.faces4("gstrip", 12, 17.2, 17.8, 12, 0.1))
    # Blast doors: each covers half the well and laps onto the rim, hinged at its outer edge (aim swings them up).
    for side, name in ((1, "door_l"), (-1, "door_r")):
        x0, x1 = sorted((0, side * (w + 1.2)))
        r0, r1 = sorted((side * 1.2, side * (w - 0.6)))
        k.bone(name, "silo", pivot=(side * (w + 1.2), JV_TOP, 0), cubes=[
            k.box("door", x0, JV_TOP, -w - 1.2, x1, JV_TOP + 1.4, w + 1.2),
            k.box("doorrib", r0, JV_TOP + 1.4, -1.0, r1, JV_TOP + 1.9, 1.0)])
    # Uptake hatches over the rim's front corners: they pop open as the missile leaves (exhaust gas).
    for side, name in ((1, "vent_l"), (-1, "vent_r")):
        x0, x1 = sorted((side * 7.0, side * 10.0))
        k.bone(name, "silo", pivot=(side * 8.5, JV_TOP, -7.0), cubes=[k.box("vent", x0, JV_TOP, -10.0, x1, JV_TOP + 0.6, -7.0)])
    # The loaded missile: only its top shows, in the well; the fire clip sends it up, the next one rises from below.
    k.bone("round", "silo", pivot=(0, JV_SEAT, 0), cubes=[
        k.box("rbody", -1.7, 14.0, -1.7, 1.7, 23.5, 1.7), k.box("rnose", -1.3, 23.5, -1.3, 1.3, 26.0, 1.3),
        k.box("rtip", -0.7, 26.0, -0.7, 0.7, 27.2, 0.7),
        k.box("rcanard", -2.8, 18.5, -0.15, 2.8, 20.0, 0.15), k.box("rcanardx", -0.15, 18.5, -2.8, 0.15, 20.0, 2.8)])
    k.bone("muzzle_0", "root", pivot=(0, JV_MOUTH, 0))
    # Radar mast: turns with the aim about its own axis.
    k.bone("yaw_pivot", "root", pivot=(mx, 0, mz), cubes=k.octagon("collar", 3.4, 1.6, 3.4, cx=mx, cz=mz) + [
        k.box("mast", mx - 1.4, 3.4, mz - 1.4, mx + 1.4, 29.0, mz + 1.4),
        k.box("mastrib", mx - 0.5, 5.0, mz - 2.0, mx + 0.5, 27.0, mz + 2.0),
        k.box("head", mx - 2.4, 29.0, mz - 2.4, mx + 2.4, 32.0, mz + 2.4)])
    k.bone("pitch_pivot", "yaw_pivot", pivot=(mx, 34, mz))
    # The dish faces forward (-Z), tilted back 20 degrees to the sky; it sweeps round the mast while idle.
    tilt = dict(rot=[20, 0, 0], pivot=[mx, 34, mz])
    k.bone("sensor", "yaw_pivot", pivot=(mx, 34, mz), cubes=[
        k.box("dish", mx - 5.5, 29.5, mz - 1.2, mx + 5.5, 38.5, mz - 0.2, **tilt),
        k.box("dishrim", mx - 6.0, 29.0, mz - 2.0, mx + 6.0, 29.6, mz - 0.2, **tilt),
        k.box("dishrim", mx - 6.0, 38.4, mz - 2.0, mx + 6.0, 39.0, mz - 0.2, **tilt),
        k.box("dishside", mx - 6.0, 29.6, mz - 2.0, mx - 5.4, 38.4, mz - 0.2, **tilt),
        k.box("dishside", mx + 5.4, 29.6, mz - 2.0, mx + 6.0, 38.4, mz - 0.2, **tilt),
        k.box("feed", mx - 0.4, 33.6, mz - 5.0, mx + 0.4, 34.4, mz - 1.2, **tilt),
        k.box("dishback", mx - 1.6, 32.4, mz - 0.2, mx + 1.6, 35.6, mz + 1.6)])
    k.bone("glow_lens", "sensor", pivot=(mx, 34, mz - 5.2), cubes=[k.box("lens", mx - 0.9, 33.1, mz - 5.6, mx + 0.9, 34.9, mz - 4.8, **tilt)])
    k.write("javelin_turret", path, bounds=(5, 4), offset=(0, 1, 0))


def javelin_missile(path):
    """Javelin missile in flight: origin at its centre, nose north (-Z), ~1.4 blocks long; four tail fins, small
    canards, a glowing sensor nose."""
    k = Kit(64)
    k.bone("root")
    k.bone("body", "root", cubes=k.rod_z("jbody", 0, 0, -7.5, 9.0, 3.4, 2.6) + [
        k.box("band", -1.9, -1.9, -2.6, 1.9, 1.9, -1.6), k.box("band", -1.9, -1.9, 5.0, 1.9, 1.9, 6.0)])
    k.bone("nose", "body", pivot=(0, 0, -7.5), cubes=[k.box("nose", -1.3, -1.3, -10.0, 1.3, 1.3, -7.5),
                                                       k.box("cone", -0.8, -0.8, -11.2, 0.8, 0.8, -10.0)])
    k.bone("glow_nose", "nose", pivot=(0, 0, -11.2), cubes=[k.box("tip", -0.45, -0.45, -11.9, 0.45, 0.45, -11.2)])
    k.bone("fins", "body", pivot=(0, 0, 8.0), cubes=[k.box("finh", -3.6, -0.15, 6.0, 3.6, 0.15, 9.6),
                                                      k.box("finv", -0.15, -3.6, 6.05, 0.15, 3.6, 9.55),
                                                      k.box("canard", -2.6, -0.12, -5.6, 2.6, 0.12, -4.0),
                                                      k.box("canardv", -0.12, -2.6, -5.55, 0.12, 2.6, -4.05)])
    k.bone("nozzle", "body", pivot=(0, 0, 9.3), cubes=[k.box("nozzle", -1.1, -1.1, 9.0, 1.1, 1.1, 10.2)])
    k.write("turret_javelin", path, bounds=(2, 2), offset=(0, 0, 0))


# Brood Cannon (Fortress Turret Base): brood_cannon.json pivot_height = BC_P / 16, muzzle_length = -BC_TIP / 16.
BC_P, BC_TIP = 18.0, -17.5


def brood_cannon_turret(path):
    """Brood Cannon for the Fortress Turret Base (PLAN "Turret 3x3"): a short fat wide-bore cannon on trunnions over a
    round armoured brood chamber; lit windows in the chamber show folded spiders waiting; a loader arm at the back swings
    a capsule into the breech after each shot. The barrel pitches 45-85 degrees up like the Mortar's. muzzle_0 = the bore."""
    k = Kit(256)  # a 2-block deck plate: its faces do not fit a 128 sheet
    P = BC_P
    k.bone("root")
    yaw = k.octagon("turn", 16, 0, 1.6) + k.octagon("chamber", 11, 1.6, 13.2) + k.octagon("chambercap", 9.5, 13.2, 14.6)
    yaw += k.ring8("band", 11, 3.0, 4.2, 0.6) + k.ring8("band", 11, 10.6, 11.8, 0.6)
    for side in (-1, 1):  # trunnion cheeks on the chamber's top
        x0, x1 = sorted((side * 6.0, side * 9.0))
        yaw.append(k.box("cheek", x0, 14.6, -4.0, x1, P + 3.0, 4.0))
    k.bone("yaw_pivot", "root", cubes=yaw)
    # Windows on the four diagonals: a lit back plate in a recess, a folded spider in front of it.
    windows, spiders = [], []
    for a in (45, 135, 225, 315):
        windows.append(k.box("window", -2.6, 5.2, -11.15, 2.6, 9.8, -11.05, rot=[0, a, 0], pivot=[0, 5.2, 0]))
        spiders += [k.box("fold", -1.6, 6.0, -11.3, 1.6, 8.0, -11.15, rot=[0, a, 0], pivot=[0, 6.0, 0]),
                    k.box("foldleg", -2.2, 6.6, -11.35, 2.2, 7.2, -11.2, rot=[0, a, 0], pivot=[0, 6.6, 0])]
    k.bone("glow_windows", "yaw_pivot", pivot=(0, 7.5, 0), cubes=windows)
    k.bone("brood", "yaw_pivot", pivot=(0, 7.0, 0), cubes=spiders)
    k.bone("glow_strips", "yaw_pivot", pivot=(0, 12.4, 0), cubes=k.ring8("gstrip", 11.6, 12.2, 12.6, 0.1, width=6))
    k.bone("vent_l", "yaw_pivot", pivot=(-4.0, 14.6, 6.0), cubes=[k.box("vent", -6.5, 14.6, 4.0, -1.5, 15.2, 8.0)])
    k.bone("vent_r", "yaw_pivot", pivot=(4.0, 14.6, 6.0), cubes=[k.box("vent", 1.5, 14.6, 4.0, 6.5, 15.2, 8.0)])
    k.bone("sensor", "yaw_pivot", pivot=(9.8, 17.0, -2.0), cubes=[k.box("scope", 9.0, 15.2, -4.5, 11.4, 18.4, 1.0)])
    k.bone("glow_lens", "sensor", pivot=(10.2, 16.8, -4.55), cubes=[k.box("lens", 9.4, 15.8, -4.6, 11.0, 17.8, -4.5)])
    # Loader arm at the back: it swings a capsule up into the breech (fire clip).
    k.bone("loader", "yaw_pivot", pivot=(0, 14.6, 9.0), cubes=[
        k.box("loaderbase", -2.0, 13.8, 8.0, 2.0, 15.6, 10.6), k.box("loaderarm", -0.9, 15.6, 8.6, 0.9, 22.0, 10.0),
        k.box("loaderclaw", -2.2, 21.4, 7.4, 2.2, 22.6, 10.0)])
    k.bone("loader_capsule", "loader", pivot=(0, 23.5, 8.7), cubes=[
        k.box("lcap", -1.8, 22.6, 7.0, 1.8, 26.4, 10.4), k.box("lcaptip", -1.1, 26.4, 7.7, 1.1, 27.2, 9.7)])
    k.bone("axle", "yaw_pivot", pivot=(0, P, 0), cubes=k.rod_x("axle", P, 0, -9.4, 9.4, 3.6))
    k.bone("pitch_pivot", "yaw_pivot", pivot=(0, P, 0))
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[k.box("breech", -5.2, P - 5.2, -2.0, 5.2, P + 5.2, 6.0),
                                                          k.box("breechcap", -4.0, P - 4.0, 6.0, 4.0, P + 4.0, 7.4)])
    # Short fat barrel with a wide bore; it slides back into the breech on each shot (fire clip).
    k.bone("barrel_0", "body", pivot=(0, P, -2.0), cubes=k.rod_z("bbarrel", 0, P, BC_TIP + 1.5, -2.0, 10.0, 8.0) + [
        k.box("collar", -5.8, P - 5.8, -6.0, 5.8, P + 5.8, -3.6), k.box("lip", -5.6, P - 5.6, BC_TIP, 5.6, P + 5.6, BC_TIP + 1.5)])
    k.bone("glow_barrel", "barrel_0", pivot=(0, P, -4.8), cubes=[k.box("collarglow", -5.9, P - 5.9, -5.1, 5.9, P + 5.9, -4.5)])
    k.bone("bore", "barrel_0", pivot=(0, P, BC_TIP), cubes=[k.box("bore", -3.8, P - 3.8, BC_TIP - 0.05, 3.8, P + 3.8, BC_TIP + 0.05)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, BC_TIP - 0.3))
    k.write("brood_cannon_turret", path, bounds=(4, 4), offset=(0, 1, 0))


def brood_capsule(path):
    """Brood capsule: an armoured pod on a round foot, closed by three petals that open like a flower (petal_0..2 turn
    each to its third; the open clip tips leaf_0..2 out about their foot), a glowing core inside. Origin at its foot."""
    k = Kit(64)
    k.bone("root")
    k.bone("base", "root", cubes=k.octagon("foot", 4.6, 0, 2.2) + k.octagon("rim", 5.0, 2.2, 3.0))
    k.bone("glow_core", "root", pivot=(0, 3, 0), cubes=[k.box("core", -2.0, 3.0, -2.0, 2.0, 8.0, 2.0)])
    for i in range(3):  # petal_i turns its third about the axis; leaf_i is the petal, hinged at its foot
        k.bone(f"petal_{i}", "root", pivot=(0, 3.0, 0), rotation=[0, 120 * i, 0])
        k.bone(f"leaf_{i}", f"petal_{i}", pivot=(0, 3.0, -4.8), cubes=[
            k.box("petal", -4.0, 3.0, -4.8, 4.0, 9.0, -3.2), k.box("petaltop", -3.0, 9.0, -4.0, 3.0, 11.0, -2.0),
            k.box("petaltip", -1.6, 11.0, -2.8, 1.6, 12.2, -0.6)])
    k.write("turret_brood_capsule", path, bounds=(1, 1), offset=(0, 0.4, 0))


def brood_spider(path):
    """Brood spider (~0.6 block): a robot hexapod, segmented body (abdomen, thorax, head), a cluster of glowing eyes, two
    needle fangs, six two-segment legs (leg_l0..2, leg_r0..2, pivots at the thorax). Origin at its feet, facing north."""
    k = Kit(64)
    k.bone("root")
    k.bone("body", "root", pivot=(0, 5.0, 0), cubes=[
        k.box("abdomen", -2.8, 3.6, 0.5, 2.8, 7.4, 6.5), k.box("abdomenplate", -2.2, 7.4, 1.2, 2.2, 8.0, 5.8),
        k.box("thorax", -2.2, 3.8, -3.5, 2.2, 6.6, 0.5)])
    k.bone("head", "body", pivot=(0, 5.2, -3.5), cubes=[k.box("headbox", -1.6, 4.0, -6.2, 1.6, 6.4, -3.5)])
    k.bone("glow_eyes", "head", pivot=(0, 5.6, -6.25), cubes=[
        k.box("eye", -1.2, 5.4, -6.3, -0.4, 6.0, -6.2), k.box("eye", 0.4, 5.4, -6.3, 1.2, 6.0, -6.2),
        k.box("eyesmall", -0.3, 4.7, -6.3, 0.3, 5.2, -6.2), k.box("eyesmall", -0.3, 5.9, -6.3, 0.3, 6.4, -6.2)])
    k.bone("fangs", "head", pivot=(0, 4.4, -6.0), cubes=[
        k.box("fang", -1.1, 2.6, -7.4, -0.6, 4.4, -6.2, rot=[-20, 0, 0], pivot=[0, 4.4, -6.2]),
        k.box("fang", 0.6, 2.6, -7.4, 1.1, 4.4, -6.2, rot=[-20, 0, 0], pivot=[0, 4.4, -6.2])])
    k.bone("glow_strip", "body", pivot=(0, 7.4, 3.5), cubes=[k.box("strip", -0.4, 7.4, 1.0, 0.4, 8.1, 6.0)])
    for side, name in ((1, "l"), (-1, "r")):
        for i, z in enumerate((-2.8, -1.0, 0.8)):
            x0 = side * 2.2
            upper0, upper1 = sorted((x0, side * 6.0))
            low0, low1 = sorted((side * 5.6, side * 6.6))
            k.bone(f"leg_{name}{i}", "body", pivot=(x0, 5.4, z), cubes=[
                k.box("legup", upper0, 5.0, z - 0.4, upper1, 6.6, z + 0.4, rot=[0, 0, side * 25], pivot=[x0, 5.4, z]),
                k.box("legdown", low0, 0.0, z - 0.35, low1, 6.4, z + 0.35)])
    k.write("brood_spider", path, bounds=(1.2, 1), offset=(0, 0.3, 0))


# Hive Carrier (Fortress Turret Base): its landing pads, px over the turntable; MUST match HiveCarrierWeapon.BAYS (x, y, z).
# The floor stands over the armed base's solid top (8 px, FortressTurretBaseBlock.ARMED); each pad sits behind a hole.
HC_FLOOR = 10.0
HC_BAYS = ((0.0, -12.0), (10.392, 6.0), (-10.392, 6.0))
HC_HOLE = (16.0, HC_FLOOR, 20.0)  # width, bottom, top (a drone is ~14 px wide, ~5.5 high)
# Where docked drones stand: on the pad, then on two shelves over it (feet heights); slot i = pad i % 3, level i // 3.
HC_SHELVES = (HC_FLOOR + 0.3, HC_FLOOR + 6.6, HC_FLOOR + 13.1)


def drone_cubes(k, ox, oy, oz):
    """A docked or half-built Hive drone inside the hive (hive_drone() made lighter: ducts as plates), feet at
    (ox, oy, oz), nose north. Returns (legs, body, rotors, glow): the stages it is built in, then its lights."""
    def b(tag, x0, y0, z0, x1, y1, z1, **kw):
        return k.box(tag, ox + x0, oy + y0, oz + z0, ox + x1, oy + y1, oz + z1, **kw)
    legs = [b("dskid", -2.4, 0, -2.6, -1.9, 0.5, 2.6), b("dskid", 1.9, 0, -2.6, 2.4, 0.5, 2.6),
            b("dstrut", -2.4, 0.5, -0.4, -1.9, 2.0, 0.4), b("dstrut", 1.9, 0.5, -0.4, 2.4, 2.0, 0.4)]
    body = [b("dhull", -2.6, 2.0, -3.2, 2.6, 4.4, 3.2), b("dtop", -1.8, 4.4, -2.4, 1.8, 5.0, 2.0), b("dgun", -0.8, 1.2, -3.6, 0.8, 2.0, -1.6)]
    rotors = []
    for x, z in ((-4.6, -4.6), (4.6, -4.6), (4.6, 4.6), (-4.6, 4.6)):
        rotors += [b("darm", min(0, x) - 0.4 if x < 0 else -0.4, 3.2, -0.4, max(0, x) + 0.4 if x > 0 else 0.4, 3.8, 0.4,
                     rot=[0, 45 if x * z > 0 else -45, 0], pivot=[ox, oy + 3.2, oz]),
                   b("dduct", x - 2.3, 3.0, z - 2.3, x + 2.3, 3.6, z + 2.3), b("dblade", x - 1.9, 3.6, z - 0.25, x + 1.9, 3.75, z + 0.25)]
    glow = [b("dstatus", -0.6, 5.0, 1.0, 0.6, 5.4, 2.2), b("deye", -1.2, 3.0, -3.25, 1.2, 3.8, -3.2)]
    return legs, body, rotors, glow


def hexagon(k, tag, r, y0, y1, cx=0.0, cz=0.0):
    """Regular hexagon prism, flats at distance r (three overlapping boxes turned 0/60/120 degrees about its axis)."""
    w = 2 * r * math.tan(math.radians(30))
    return [k.box(tag, cx - w / 2, y0 + 0.01 * i, cz - r, cx + w / 2, y1 + 0.01 * i, cz + r, rot=[0, 60 * i, 0], pivot=[cx, y0, cz],
                  hide=("north", "south")) for i in range(3)]


def hex_face(j):
    """Unit outward normal (x, z) of hexagon face j (rot 60 j about +Y, as GeckoLib turns a cube: Ry(+y), preview.py)."""
    b = math.radians(60 * j)
    return -math.sin(b), -math.cos(b)


def hex_wall(k, tag, r, y0, y1, t, holes=()):
    """A hollow hexagonal wall, flats at distance r (outside), t thick: six panels; on the faces in holes a doorway of
    HC_HOLE (two pillars and a lintel) instead of the panel."""
    w = 2 * r * math.tan(math.radians(30))
    hw, hb, ht = HC_HOLE
    out = []
    for j in range(6):
        rot, piv = [0, 60 * j, 0], [0, y0, 0]
        if j in holes:
            out += [k.box(tag + "pl", -w / 2, y0, -r, -hw / 2, y1, -r + t, rot=rot, pivot=piv),
                    k.box(tag + "pl", hw / 2, y0, -r, w / 2, y1, -r + t, rot=rot, pivot=piv),
                    k.box(tag + "li", -hw / 2, ht, -r, hw / 2, y1, -r + t, rot=rot, pivot=piv)]
        else:
            out.append(k.box(tag, -w / 2, y0, -r, w / 2, y1, -r + t, rot=rot, pivot=piv))
    return out


def hex_shelf(k, tag, r_out, r_in, y0, y1):
    """A flat hexagonal ring between flats r_in and r_out (six trapezoid-ish panels): the ledge where the nest narrows."""
    w = 2 * r_out * math.tan(math.radians(30))
    return [k.box(tag, -w / 2, y0, -r_out, w / 2, y1, -r_in, rot=[0, 60 * j, 0], pivot=[0, y0, 0]) for j in range(6)]


def hive_carrier_turret(path):
    """Hive Carrier for the Fortress Turret Base (PLAN "Turret 3x3", user 2026-10-24): a big hollow wasp-nest hive nearly
    as wide as the 3x3 on a slim pedestal: honeycomb-plated layers (hex material), each with an overhanging lip, swelling
    out above the doorways and narrowing to a cap. Three doorways in its lowest layer (north, south-east, south-west)
    lead into the hollow, where a landing pad with cyan lights waits behind each (bays, glow_bays); drones fly level out
    of a doorway and in again. A relay mast rises out of the cap, its antenna pulsing while it controls its drones. It
    never turns (everything hangs off root)."""
    k = Kit(256)
    k.bone("root")
    holes = [j for j in range(6) if any(abs(hex_face(j)[0] - bx / 12) < 0.05 and abs(hex_face(j)[1] - bz / 12) < 0.05 for bx, bz in HC_BAYS)]
    assert len(holes) == 3, holes
    f = HC_FLOOR
    # Honeycomb layers, (wall radius, bottom, top); the lowest carries the doorways. Between layers: a lip, then a ledge in.
    layers = ((21.0, f, 20.5), (22.0, 22.0, 26.5), (20.5, 27.6, 31.0), (17.0, 31.8, 34.5), (12.8, 35.2, 37.2))
    walls = []
    for i, (r, y0, y1) in enumerate(layers):
        walls += hex_wall(k, f"wall_{i}", r, y0, y1, 1.5, holes if i == 0 else ())
    k.bone("hive", "root", cubes=walls)
    # Armoured frame: the pedestal, the underside, the lips and ledges between the layers, the cap.
    frame = (hexagon(k, "foot", 18.0, 0.0, 1.5) + hexagon(k, "pedestal", 13.0, 1.5, 7.0)
             + hexagon(k, "under", 17.0, 7.0, 8.6) + hexagon(k, "floor", 20.6, 8.6, f)
             + hex_wall(k, "lip_0", 22.6, 20.5, 22.0, 2.6) + hex_shelf(k, "step_1", 22.6, 20.0, 20.5, 21.2)
             + hex_wall(k, "lip_1", 22.8, 26.5, 27.6, 2.4) + hex_shelf(k, "step_2", 22.0, 19.0, 27.0, 27.6)
             + hex_wall(k, "lip_2", 21.2, 31.0, 31.8, 2.0) + hex_shelf(k, "step_3", 20.5, 15.5, 31.0, 31.8)
             + hex_wall(k, "lip_3", 17.6, 34.5, 35.2, 1.8) + hex_shelf(k, "step_4", 17.0, 11.3, 34.5, 35.2)
             + hexagon(k, "cap", 13.2, 37.2, 38.6) + hexagon(k, "crown", 7.0, 38.6, 40.0))
    for j in holes:  # a hood over each doorway
        frame.append(k.box("hood", -HC_HOLE[0] / 2 - 1.2, HC_HOLE[2], -22.4, HC_HOLE[0] / 2 + 1.2, HC_HOLE[2] + 0.5, -20.6,
                           rot=[0, 60 * j, 0], pivot=[0, f, 0]))
    k.bone("hive_frame", "root", cubes=frame)
    pads, lights, rims = [], [], []
    for bx, bz in HC_BAYS:
        pads += hexagon(k, "pad", 4.6, f, f + 0.3, bx, bz)
        for c in range(6):
            ang = math.radians(60 * c)
            lx, lz = bx + math.cos(ang) * 5.4, bz + math.sin(ang) * 5.4
            lights.append(k.box("padlight", lx - 0.35, f, lz - 0.35, lx + 0.35, f + 0.4, lz + 0.35))
    hw, hb, ht = HC_HOLE
    for j in holes:  # cyan frame round each doorway, on the wall's face
        rot, piv = [0, 60 * j, 0], [0, f, 0]
        rims += [k.box("rimv", -hw / 2 - 0.6, hb, -21.15, -hw / 2, ht, -20.9, rot=rot, pivot=piv),
                 k.box("rimv", hw / 2, hb, -21.15, hw / 2 + 0.6, ht, -20.9, rot=rot, pivot=piv),
                 k.box("rimh", -hw / 2 - 0.6, ht - 0.6, -21.15, hw / 2 + 0.6, ht, -20.9, rot=rot, pivot=piv)]
    # Racks: two shelves over each pad, on two posts at its back (docked drones stand three deep, bottoms first).
    pad_faces = [next(j for j in holes if abs(hex_face(j)[0] - bx / 12) < 0.05 and abs(hex_face(j)[1] - bz / 12) < 0.05) for bx, bz in HC_BAYS]
    racks = []
    for (bx, bz), j in zip(HC_BAYS, pad_faces):
        for y in HC_SHELVES[1:]:
            racks += hexagon(k, "shelf", 4.6, y - 0.4, y, bx, bz)
        dx, dz = hex_face(j)
        for side in (-1, 1):
            cx, cz = bx - dx * 4.2 - dz * 3.0 * side, bz - dz * 4.2 + dx * 3.0 * side
            racks.append(k.box("rackpost", cx - 0.4, f, cz - 0.4, cx + 0.4, HC_SHELVES[2], cz + 0.4))
    k.bone("racks", "root", pivot=(0, f, 0), cubes=racks)
    # Docked drones, one bone per slot (WeaponModel shows as many as are docked), nose out to its doorway.
    for i in range(9):
        (bx, bz), j, y = HC_BAYS[i % 3], pad_faces[i % 3], HC_SHELVES[i // 3]
        legs, body, rotors, glow = drone_cubes(k, bx, y, bz)
        k.bone(f"dock_{i}", "root", pivot=(bx, y, bz), rotation=[0, 60 * j, 0], cubes=legs + body + rotors)
        k.bone(f"glow_dock_{i}", f"dock_{i}", pivot=(bx, y, bz), cubes=glow)
    # A new drone being built on each empty pad between waves: legs, body, rotors, then its lights over a lit plate.
    for p, ((bx, bz), j) in enumerate(zip(HC_BAYS, pad_faces)):
        y = HC_SHELVES[0]
        stages = drone_cubes(k, bx, y, bz)
        k.bone(f"build_{p}", "root", pivot=(bx, y, bz), rotation=[0, 60 * j, 0])
        for n in range(3):
            k.bone(f"build_{p}_{n}", f"build_{p}", pivot=(bx, y, bz), cubes=stages[n])
        k.bone(f"glow_build_{p}", f"build_{p}", pivot=(bx, y, bz), cubes=hexagon(k, "buildplate", 4.2, y + 0.02, y + 0.12, bx, bz))
        k.bone(f"glow_build_{p}_lit", f"build_{p}", pivot=(bx, y, bz), cubes=stages[3])
    k.bone("bays", "root", pivot=(0, f, 0), cubes=pads)
    k.bone("glow_bays", "root", pivot=(0, f, 0), cubes=lights + rims)
    k.bone("glow_strips", "root", pivot=(0, 26.0, 0), cubes=hex_wall(k, "gstrip", 22.1, 25.8, 26.2, 0.3))
    # The middle of each doorway's mouth, 1.4 blocks past its pad (HiveDroneEntity.EXIT): a wave's puffs play there.
    for i, (bx, bz) in enumerate(HC_BAYS):
        r = math.hypot(bx, bz)
        k.bone(f"muzzle_{i}", "root", pivot=(bx * (r + 22.4) / r, f + 4.0, bz * (r + 22.4) / r))
    # Relay mast: from the floor up through the cap; it rises a little further on deploy; its antenna pulses.
    k.bone("relay", "root", pivot=(0, 40.0, 0), cubes=hexagon(k, "mastfoot", 2.6, f, f + 2.0) + [
        k.box("mast", -1.2, f + 2.0, -1.2, 1.2, 49.0, 1.2),
        k.box("mastring", -2.6, 42.0, -2.6, 2.6, 42.8, 2.6), k.box("mastring", -2.6, 45.4, -2.6, 2.6, 46.2, 2.6)])
    k.bone("sensor", "relay", pivot=(0, 50.0, 0), cubes=[k.box("masthead", -1.8, 49.0, -1.8, 1.8, 51.2, 1.8),
                                                         k.box("antenna", -0.3, 51.2, -0.3, 0.3, 55.0, 0.3)])
    k.bone("glow_antenna", "sensor", pivot=(0, 55.5, 0), cubes=[k.box("antennatip", -0.7, 55.0, -0.7, 0.7, 56.4, 0.7)])
    k.write("hive_carrier_turret", path, bounds=(4, 4), offset=(0, 1.5, 0))


def hive_drone(path):
    """Hive drone: a compact quadcopter, four ducted fans on short arms (fan_0..3 spin), a small gun under the chin, a
    status light on its back, two skids. Origin at its feet, nose north (-Z)."""
    k = Kit(64)
    k.bone("root")
    body = [k.box("hull", -2.6, 2.0, -3.2, 2.6, 4.4, 3.2), k.box("hulltop", -1.8, 4.4, -2.4, 1.8, 5.0, 2.0),
            k.box("skid", -2.4, 0.0, -2.6, -1.9, 0.5, 2.6), k.box("skid", 1.9, 0.0, -2.6, 2.4, 0.5, 2.6),
            k.box("strut", -2.4, 0.5, -0.4, -1.9, 2.0, 0.4), k.box("strut", 1.9, 0.5, -0.4, 2.4, 2.0, 0.4)]
    for i, (x, z) in enumerate(((-4.6, -4.6), (4.6, -4.6), (4.6, 4.6), (-4.6, 4.6))):
        body.append(k.box("arm", min(0, x) - 0.4 if x < 0 else -0.4, 3.2, -0.4, max(0, x) + 0.4 if x > 0 else 0.4, 3.8, 0.4,
                          rot=[0, 45 if x * z > 0 else -45, 0], pivot=[0, 3.2, 0]))
        body += [k.box("ductwall", x - 2.3, 2.9, z - 2.3, x + 2.3, 4.1, z - 1.9), k.box("ductwall", x - 2.3, 2.9, z + 1.9, x + 2.3, 4.1, z + 2.3),
                 k.box("ductwallx", x - 2.3, 2.9, z - 1.9, x - 1.9, 4.1, z + 1.9), k.box("ductwallx", x + 1.9, 2.9, z - 1.9, x + 2.3, 4.1, z + 1.9)]
        k.bone(f"fan_{i}", "root", pivot=(x, 3.5, z), cubes=[k.box("blade", x - 1.9, 3.4, z - 0.25, x + 1.9, 3.6, z + 0.25),
                                                             k.box("bladex", x - 0.25, 3.42, z - 1.9, x + 0.25, 3.58, z + 1.9)])
    k.bone("body", "root", pivot=(0, 3.0, 0), cubes=body)
    k.bone("gun", "body", pivot=(0, 1.6, -2.6), cubes=[k.box("gunbox", -0.8, 1.2, -3.6, 0.8, 2.0, -1.6),
                                                        k.box("gunbarrel", -0.3, 1.4, -5.4, 0.3, 1.8, -3.6)])
    k.bone("glow_status", "body", pivot=(0, 5.0, 1.6), cubes=[k.box("status", -0.6, 5.0, 1.0, 0.6, 5.4, 2.2),
                                                               k.box("eye", -1.2, 3.0, -3.25, 1.2, 3.8, -3.2)])
    k.write("hive_drone", path, bounds=(1, 1), offset=(0, 0.3, 0))


def square_frame(k, tag, cy, z0, z1, half, t):
    """A square ring around the Z axis: four bars `t` thick, outer half-width `half`."""
    h, v = tag + "h", tag + "v"  # the top/bottom bars and the side bars differ in size: their own UV regions
    return [k.box(h, -half, cy + half - t, z0, half, cy + half, z1), k.box(h, -half, cy - half, z0, half, cy - half + t, z1),
            k.box(v, -half, cy - half + t, z0, -half + t, cy + half - t, z1), k.box(v, half - t, cy - half + t, z0, half, cy + half - t, z1)]


def repulsor(path):
    """Repulsor: a short, wide square horn on the yoke (four frames widening to the mouth) with three glowing rings set
    in it, back to front (glow_coil_0..2: the client lights them one by one while it charges), finned flanks.
    muzzle_0 = the mouth (repulsor.json muzzle_length 0.75 = 12 px)."""
    k = Kit(128)
    yoke(k)
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[
        k.box("rcv", -4.2, 3.4, -4.0, 4.2, 10.6, 5.6), k.box("rcvtop", -3.4, 10.6, -3.0, 3.4, 11.4, 4.6),
        k.box("rcvrear", -3.0, 4.6, 5.6, 3.0, 9.4, 7.0)])
    horn = []
    # One tag per size: faces with the same tag share one UV region, and a bigger face would read past it.
    for i, (z0, z1, half) in enumerate(((-6.0, -4.0, 3.6), (-8.0, -6.0, 4.4), (-10.0, -8.0, 5.2), (-12.0, -10.0, 6.0))):
        horn += square_frame(k, f"horn{i}", P, z0, z1, half, 0.9)
    horn.append(k.box("throat", -2.6, P - 2.6, -4.6, 2.6, P + 2.6, -4.0))
    k.bone("barrel_0", "body", pivot=(0, P, -4.0), cubes=horn)
    for i, (z, half) in enumerate(((-5.4, 2.9), (-7.9, 3.7), (-10.4, 4.5))):
        k.bone(f"glow_coil_{i}", "barrel_0", pivot=(0, P, z), cubes=square_frame(k, f"ring{i}", P, z - 0.25, z + 0.25, half, 0.35))
    fins = []
    for side in (-1, 1):
        x0, x1 = sorted((side * 4.2, side * 4.9))
        fins += [k.box("fin", x0, y, -3.0, x1, y + 0.6, 4.4) for y in (4.4, 5.8, 7.2, 8.6)]
    k.bone("body_frame", "body", pivot=(0, P, 0), cubes=fins)
    k.bone("glow_strips", "body", pivot=(0, P, 0), cubes=[k.box("gstrip", -3.5, 11.4, -2.4, 3.5, 11.5, 4.0)])
    k.bone("vent_l", "body", pivot=(-4.9, 6.0, 0.7), cubes=[k.box("vent", -4.95, 5.0, -2.0, -4.8, 7.0, 3.4)])
    k.bone("vent_r", "body", pivot=(4.9, 6.0, 0.7), cubes=[k.box("vent", 4.8, 5.0, -2.0, 4.95, 7.0, 3.4)])
    k.bone("sensor", "body", pivot=(2.6, 11.4, -1.0), cubes=[k.box("scope", 1.6, 11.4, -2.6, 3.6, 12.6, 0.6)])
    k.bone("glow_lens", "sensor", pivot=(2.6, 12.0, -2.65), cubes=[k.box("lens", 2.0, 11.6, -2.65, 3.2, 12.4, -2.6)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, -12.0))
    k.write("repulsor_turret", path, bounds=(3, 2.5), offset=(0, 0.5, 0))


DOME_ORB = 16.0  # orb centre above the mount; repulsor_dome.json pivot_height = DOME_ORB / 16


def repulsor_dome(path):
    """Repulsor Dome for the Large Turret Base: a squat round generator, an orb (glow_orb) in a cage of four bent
    pylons, a ring around the orb (dome_ring: the client spins it up while it charges, WeaponModel turns it about Y).
    It never turns or tilts. muzzle_0 = the orb centre."""
    k = Kit(128)
    k.bone("root")
    k.bone("yaw_pivot", "root", cubes=k.octagon("turn", 11, 0, 1.6))
    k.bone("pitch_pivot", "yaw_pivot", pivot=(0, DOME_ORB, 0))
    k.bone("body", "yaw_pivot", pivot=(0, 1.6, 0), cubes=k.chamfer("housing", 9, 3, 1.6, 7.0) + k.chamfer("lid", 7, 2.4, 7.0, 8.4)
           + k.octagon("socket", 3.4, 8.4, 11.0))
    k.bone("glow_strips", "body", pivot=(0, 5.0, 0), cubes=k.faces4("gstrip", 9.1, 4.6, 5.4, 6.0, 0.1))
    pylons = []
    for x, z in ((-6.5, -6.5), (6.5, -6.5), (-6.5, 6.5), (6.5, 6.5)):
        ix, iz = (-1 if x > 0 else 1), (-1 if z > 0 else 1)  # towards the middle
        pylons += [k.box("pylon", x - 1.0, 8.4, z - 1.0, x + 1.0, 18.0, z + 1.0),
                   k.box("pylonbend", min(x, x + ix * 2.6) - 0.9, 18.0, min(z, z + iz * 2.6) - 0.9,
                         max(x, x + ix * 2.6) + 0.9, 19.4, max(z, z + iz * 2.6) + 0.9),
                   k.box("pyloncap", x - 1.3, 7.8, z - 1.3, x + 1.3, 8.6, z + 1.3)]
    k.bone("pylons", "body", pivot=(0, 8.4, 0), cubes=pylons)
    k.bone("dome_ring", "body", pivot=(0, DOME_ORB, 0), cubes=k.ring8("ring", 6.2, DOME_ORB - 0.5, DOME_ORB + 0.5, 1.0))
    k.bone("glow_orb", "body", pivot=(0, DOME_ORB, 0), cubes=k.octagon("orbcap", 2.0, DOME_ORB - 3.4, DOME_ORB - 2.4)
           + k.octagon("orb", 3.4, DOME_ORB - 2.4, DOME_ORB + 2.4) + k.octagon("orbtop", 2.0, DOME_ORB + 2.4, DOME_ORB + 3.4))
    k.bone("sensor", "body", pivot=(0, 9.6, -7.4), cubes=[k.box("scope", -1.4, 8.6, -8.4, 1.4, 10.4, -6.4)])
    k.bone("glow_lens", "sensor", pivot=(0, 9.5, -8.45), cubes=[k.box("lens", -0.9, 9.0, -8.45, 0.9, 10.0, -8.4)])
    k.bone("muzzle_0", "body", pivot=(0, DOME_ORB, 0))
    k.write("repulsor_dome_turret", path, bounds=(3, 3), offset=(0, 1, 0))

def octagon_z(k, tag, r, cy, z0, z1, step=0.01):
    """Octagon prism along Z (a barrel or chamber seen end-on), flats at distance r: four boxes turned about Z."""
    a = 2 * r * TAN22
    return [k.box(tag, -r, cy - a / 2, z0 - step * i, r, cy + a / 2, z1 - step * i, rot=[0, 0, rz], pivot=[0, cy, z0], hide=("up", "down"))
            for i, rz in enumerate((0, 45, 90, -45))]


def ion_rifle(path):
    """Ion Rifle (after Crimsonland's): a slim receiver with a capacitor block behind, a long thin barrel with three coil
    rings near the tip (glow_coils; the fire clip pulses them) and a short emitter tip. muzzle_0 = the tip
    (ion_rifle.json muzzle_length 1.5375 = 24.6 px)."""
    k = Kit(128)
    yoke(k)
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[
        k.box("rcv", -3.4, 3.8, -6.0, 3.4, 10.0, 5.0), k.box("rcvtop", -2.6, 10.0, -5.0, 2.6, 10.8, 3.6),
        k.box("cap", -3.0, 4.4, 5.0, 3.0, 9.4, 8.6)])
    k.bone("body_frame", "body", pivot=(0, P, 0), cubes=[k.box("capfin", -3.4, y, 5.4, 3.4, y + 0.5, 8.2) for y in (5.0, 6.6, 8.2)] + [
        k.box("rail", -4.2, 5.6, -5.0, -3.4, 8.4, 3.0), k.box("rail", 3.4, 5.6, -5.0, 4.2, 8.4, 3.0)])
    k.bone("glow_strips", "body", pivot=(0, P, 0), cubes=[
        k.box("gstrip", -4.25, 6.6, -4.4, -4.15, 7.4, 2.4), k.box("gstrip", 4.15, 6.6, -4.4, 4.25, 7.4, 2.4),
        k.box("capglow", -2.0, 9.4, 5.8, 2.0, 9.5, 7.8)])
    k.bone("vent_l", "body", pivot=(-3.45, 5.0, 2.0), cubes=[k.box("vent", -3.5, 4.2, 0.6, -3.35, 5.8, 3.4)])
    k.bone("vent_r", "body", pivot=(3.45, 5.0, 2.0), cubes=[k.box("vent", 3.35, 4.2, 0.6, 3.5, 5.8, 3.4)])
    k.bone("sensor", "body", pivot=(2.6, 11.2, -3.0), cubes=[k.box("scope", 1.8, 10.8, -4.6, 3.4, 11.8, -1.6)])
    k.bone("glow_lens", "sensor", pivot=(2.6, 11.3, -4.65), cubes=[k.box("lens", 2.0, 11.0, -4.65, 3.2, 11.6, -4.6)])
    k.bone("barrel_0", "body", pivot=(0, P, -6.0), cubes=k.rod_z("ibarrel", 0, P, -23.0, -6.0, 1.8) + [
        k.box("shroud", -1.4, P - 1.4, -10.0, 1.4, P + 1.4, -6.0), k.box("tip", -1.1, P - 1.1, -24.2, 1.1, P + 1.1, -23.0)])
    coils, glow = [], []
    for z in (-15.0, -18.0, -21.0):
        coils += square_frame(k, "icoil", P, z - 0.6, z + 0.6, 2.4, 0.8)
        glow += square_frame(k, "icoilglow", P, z - 0.25, z + 0.25, 1.6, 0.3)
    k.bone("coils", "barrel_0", pivot=(0, P, -18.0), cubes=coils)
    k.bone("glow_coils", "coils", pivot=(0, P, -18.0), cubes=glow)
    k.bone("glow_tip", "barrel_0", pivot=(0, P, -24.25), cubes=[k.box("tipglow", -0.6, P - 0.6, -24.3, 0.6, P + 0.6, -24.2)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, -24.6))
    k.write("ion_rifle_turret", path, bounds=(3.5, 2.5), offset=(0, 0.5, 0))


def plasma_repeater(path):
    """Plasma Repeater: a squat finned receiver, a round containment chamber at the base of a short thick barrel
    (glow_chamber windows; the client tints the model glow teal as it heats), two flaps on top that swing open when it
    overheats (vent_l / vent_r). muzzle_0 = the mouth (plasma_repeater.json muzzle_length 1.05 = 16.8 px)."""
    k = Kit(128)
    yoke(k)
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[
        k.box("rcv", -3.8, 3.4, -4.0, 3.8, 10.4, 5.4), k.box("rcvtop", -3.0, 10.4, -3.0, 3.0, 11.2, 4.4),
        k.box("rcvrear", -2.8, 4.6, 5.4, 2.8, 9.2, 6.8)])
    k.bone("body_frame", "body", pivot=(0, P, 0), cubes=[k.box("fin", x0, y, -2.4, x1, y + 0.6, 4.6)
                                                         for side in (-1, 1) for x0, x1 in [sorted((side * 3.8, side * 4.7))]
                                                         for y in (4.4, 5.8, 7.2, 8.6)])
    k.bone("chamber", "body", pivot=(0, P, -6.5), cubes=octagon_z(k, "chamber", 3.4, P, -9.0, -4.0) + octagon_z(k, "chambercap", 2.6, P, -9.8, -3.2))
    k.bone("glow_chamber", "chamber", pivot=(0, P, -6.5), cubes=[
        k.box("window", -3.5, P - 0.9, -7.6, -3.4, P + 0.9, -5.4), k.box("window", 3.4, P - 0.9, -7.6, 3.5, P + 0.9, -5.4),
        k.box("windowtop", -0.9, P + 3.4, -7.6, 0.9, P + 3.5, -5.4)])
    k.bone("barrel_0", "body", pivot=(0, P, -9.8), cubes=k.rod_z("pbarrel", 0, P, -15.8, -9.8, 3.2, 2.4) + [
        k.box("lip", -2.1, P - 2.1, -16.8, 2.1, P + 2.1, -15.8)])
    k.bone("bore", "barrel_0", pivot=(0, P, -16.8), cubes=[k.box("bore", -1.3, P - 1.3, -16.85, 1.3, P + 1.3, -16.8)])
    k.bone("glow_strips", "body", pivot=(0, P, 0), cubes=[k.box("gstrip", -3.0, 11.2, 3.8, 3.0, 11.3, 4.3)])
    k.bone("vent_l", "body", pivot=(-2.9, 11.2, 0.6), cubes=[k.box("vent", -2.9, 11.2, -2.2, -0.3, 11.7, 3.4)])
    k.bone("vent_r", "body", pivot=(2.9, 11.2, 0.6), cubes=[k.box("vent", 0.3, 11.2, -2.2, 2.9, 11.7, 3.4)])
    k.bone("sensor", "body", pivot=(0, 11.7, -3.2), cubes=[k.box("scope", -1.0, 11.2, -4.0, 1.0, 12.2, -2.4)])
    k.bone("glow_lens", "sensor", pivot=(0, 11.7, -4.05), cubes=[k.box("lens", -0.6, 11.4, -4.05, 0.6, 12.0, -4.0)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, -16.8))
    k.write("plasma_repeater_turret", path, bounds=(3, 2.5), offset=(0, 0.5, 0))


# Plasma Cannon v2 (user 2026-10-10 06:28, reference: Kromlech "Battle Tank Turret: Plasma Cannon"): a big armoured tank
# turret. The hull only turns (yaw); the barrel pitches on a mantlet at the hull's front. plasma_cannon.json pivot_height =
# PC_P / 16 (the server aims from the turning axis at that height), muzzle_length = -PC_TIP / 16.
PC_P, PC_MANTLET, PC_TIP = 11.5, -10.0, -34.3


def plasma_cannon(path):
    """Plasma Cannon for the Large Turret Base: a tall armoured hull (user 06:38: it looked flat) standing on a raised
    turret ring (stepped glacis, riveted side skirts, a rear bustle with vent flaps, a domed hatch, glowing coil tubes on the
    rear deck, the sensor up front) and, on a round mantlet, a thick coil barrel: dark ribs over a glowing core (glow_coils;
    the client turns the model glow green while it charges), a heavy cap with one dark round muzzle. muzzle_0 = the cap's
    face."""
    k = Kit(256)
    P = PC_P
    k.bone("root")
    hull = k.octagon("turn", 12, 0, 1.6) + k.chamfer("ring", 10, 2.5, 1.6, 5.0) + [
        k.box("hull", -11, 5, -9, 11, 13, 14), k.box("hullup", -9.5, 13, -7, 9.5, 18, 13),
        k.box("glacis", -10, 5, -11.5, 10, 11, -9), k.box("glacisup", -9.5, 11, -9, 9.5, 13, -7),
        k.box("bustle", -8, 8, 14, 8, 16, 18)]
    k.bone("yaw_pivot", "root", cubes=hull)
    k.bone("hull_plates", "yaw_pivot", pivot=(0, 12, 0), cubes=[
        k.box("skirt", -11.8, 6.5, -7, -11, 13, 12), k.box("skirt", 11, 6.5, -7, 11.8, 13, 12),
        k.box("cheek", -10.4, 13, -6, -9.5, 17, 10), k.box("cheek", 9.5, 13, -6, 10.4, 17, 10),
        k.box("deck", -7.5, 18, 6, 7.5, 18.7, 12.5), k.box("brow", -8.5, 17, -8, 8.5, 18, -7)])
    rivets = [k.box("rivet", x - 0.3, 13, z - 0.3, x + 0.3, 13.5, z + 0.3) for x in (-11.4, 11.4) for z in (-5, -1, 3, 7, 11)]
    rivets += [k.box("rivet", x - 0.3, 11, -10.6, x + 0.3, 11.5, -10.0) for x in (-8, -4, 0, 4, 8)]
    rivets += [k.box("rivet", x - 0.3, 16, 17.0, x + 0.3, 16.5, 17.6) for x in (-7.2, 7.2)]
    k.bone("rivets", "yaw_pivot", pivot=(0, 12, 0), cubes=rivets)
    k.bone("dome", "yaw_pivot", pivot=(0, 18.5, 0), cubes=k.octagon("dome", 4.5, 18, 19.7) + k.octagon("cupola", 2.6, 19.7, 20.9))
    glow = [k.box("slit", -11.85, 9, -5, -11.75, 9.8, 10), k.box("slit", 11.75, 9, -5, 11.85, 9.8, 10)]
    glow += k.ring8("domeglow", 4.55, 18.6, 19.1, 0.1) + k.faces4("ringglow", 10, 3.0, 3.6, 8.0, 0.1)
    for cz in (8.3, 10.7):
        glow += k.rod_x("rearcoil", 19.4, cz, -6.5, -1.5, 1.6) + k.rod_x("rearcoil", 19.4, cz, 1.5, 6.5, 1.6)
    k.bone("glow_strips", "yaw_pivot", pivot=(0, 12, 0), cubes=glow)
    k.bone("vent_l", "yaw_pivot", pivot=(-4, 16, 16), cubes=[k.box("vent", -7, 16, 14.5, -1, 16.6, 17.5)])
    k.bone("vent_r", "yaw_pivot", pivot=(4, 16, 16), cubes=[k.box("vent", 1, 16, 14.5, 7, 16.6, 17.5)])
    k.bone("sensor", "yaw_pivot", pivot=(-6, 19, -5), cubes=[k.box("scope", -7.4, 18, -6.5, -4.6, 19.9, -3.5)])
    k.bone("glow_lens", "sensor", pivot=(-6, 18.95, -6.55), cubes=[k.box("lens", -7, 18.4, -6.55, -5, 19.5, -6.5)])
    # The barrel turns about the mantlet, not the hull's centre, so the long hull never tips into the base.
    k.bone("pitch_pivot", "yaw_pivot", pivot=(0, P, PC_MANTLET))
    k.bone("body", "pitch_pivot", pivot=(0, P, PC_MANTLET), cubes=octagon_z(k, "mantlet", 5.2, P, -13.0, -8.0) + [
        k.box("mantletplate", -6.5, P - 4.5, -12.2, 6.5, P + 4.5, -11.4)])
    k.bone("barrel_0", "body", pivot=(0, P, -13.0), cubes=octagon_z(k, "core", 2.8, P, -30.0, -13.0)
           + octagon_z(k, "collar", 4.9, P, -30.6, -29.2) + octagon_z(k, "cap", 5.4, P, PC_TIP, -30.6)
           + [k.box("spine", -1.0, P + 4.7, -29.0, 1.0, P + 5.6, -13.5)])
    ribs = []
    for i in range(9):
        z = -28.6 + i * 1.7
        ribs += octagon_z(k, "rib", 4.6, P, z, z + 0.7)
    k.bone("coils", "barrel_0", pivot=(0, P, -21.0), cubes=ribs)
    k.bone("glow_coils", "coils", pivot=(0, P, -21.0), cubes=octagon_z(k, "coilglow", 4.1, P, -29.0, -13.6))
    # One round muzzle (user 06:35: two slots looked odd): a dark octagon in the cap's face, a lip of cap around it.
    k.bone("bore", "barrel_0", pivot=(0, P, PC_TIP), cubes=octagon_z(k, "bore", 3.0, P, PC_TIP - 0.05, PC_TIP + 0.05))
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, PC_TIP - 0.3))
    k.write("plasma_cannon_turret", path, bounds=(4, 3), offset=(0, 1, 0))


# --- early game (PLAN "Turret early game", Tampilan): rustic, wood / stone / iron / rope / leather, no glow, no sensor ---

def rustic_yoke(k):
    """yoke() for the early line: the same pivot (painted wood) with an iron strap and two rivets round each arm."""
    yoke(k)
    straps, rivets = [], []
    for side in (-1, 1):
        x0, x1 = sorted((side * 4.85, side * 7.25))
        straps.append(k.box("armstrap", x0, 3.6, -2.75, x1, 4.4, 2.75))
        r0, r1 = sorted((side * 7.25, side * 7.5))
        rivets += [k.box("yrivet", r0, 3.75, z - 0.25, r1, 4.25, z + 0.25) for z in (-1.8, 1.8)]
    k.bone("yoke_iron", "yaw_pivot", pivot=(0, 4.0, 0), cubes=straps)
    k.bone("yoke_rivets", "yoke_iron", pivot=(0, 4.0, 0), cubes=rivets)


def cobblestone_turret(path):
    """Cobblestone Turret: a short, wide spring cannon on a wooden pivot. A stave barrel bound with iron hoops lies in a
    wooden cradle with a cobblestone loaded in its mouth; behind it a coil spring runs over two guide rods to an iron
    back plate. The fire clip kicks the barrel back, squeezing the spring, and the stone is gone until it reloads.
    muzzle_0 = the mouth (cobblestone.json muzzle_length 0.8625 = 13.8 px)."""
    k = Kit(128)
    rustic_yoke(k)
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[
        k.box("bed", -4.4, 3.0, -5.6, 4.4, 4.2, 6.6),
        k.box("cheek", -4.6, 4.2, -4.4, -3.6, 9.8, 1.4), k.box("cheek", 3.6, 4.2, -4.4, 4.6, 9.8, 1.4)])
    k.bone("body_iron", "body", pivot=(0, P, 0), cubes=[
        k.box("backplate", -3.4, P - 3.4, 5.4, 3.4, P + 3.4, 6.4),
        k.box("guide", -3.5, 4.2, 1.4, -2.9, 4.8, 5.4), k.box("guide", 2.9, 4.2, 1.4, 3.5, 4.8, 5.4),
        k.box("bedstrap", -4.6, 2.8, -5.4, 4.6, 4.4, -4.6), k.box("bedstrap", -4.6, 2.8, 3.0, 4.6, 4.4, 3.8)])
    k.bone("body_rivets", "body_iron", pivot=(0, P, 0), cubes=[
        k.box("crivet", x0, y, z, x0 + 0.3, y + 0.6, z + 0.6) for x0 in (-4.9, 4.6) for y in (4.8, 8.6) for z in (-3.6, -0.4)])
    # The spring squeezes towards the back plate: its pivot sits on the plate.
    k.bone("spring", "body", pivot=(0, P, 5.4), cubes=k.rod_z("coil", 0, P, 0.9, 5.4, 4.8, 3.6))
    k.bone("barrel_0", "body", pivot=(0, P, 0), cubes=k.rod_z("stave", 0, P, -12.0, 0.0, 7.0, 5.0))
    k.bone("barrel_iron", "barrel_0", pivot=(0, P, -6.0), cubes=k.rod_z("hoop", 0, P, -6.2, -5.3, 7.6, 5.6)
           + k.rod_z("hoop", 0, P, -9.2, -8.3, 7.6, 5.6) + k.rod_z("lip", 0, P, -13.4, -12.0, 8.6, 6.6)
           + [k.box("breech", -2.8, P - 2.8, 0.0, 2.8, P + 2.8, 0.9)])
    rivets = []
    for z in (-5.75, -8.75):
        rivets += [k.box("hrivet", -0.3, P + 3.8, z - 0.3, 0.3, P + 4.1, z + 0.3),
                   k.box("hrivetx", -4.1, P - 0.3, z - 0.3, -3.8, P + 0.3, z + 0.3), k.box("hrivetx", 3.8, P - 0.3, z - 0.3, 4.1, P + 0.3, z + 0.3)]
    k.bone("barrel_rivets", "barrel_iron", pivot=(0, P, -6.0), cubes=rivets)
    k.bone("bore", "barrel_0", pivot=(0, P, -13.4), cubes=[k.box("bore", -2.9, P - 2.9, -13.45, 2.9, P + 2.9, -13.4)])
    k.bone("stone_load", "barrel_0", pivot=(0, P, -12.6), cubes=[
        k.box("pebble", -2.0, P - 1.7, -13.7, 2.0, P + 1.6, -11.6), k.box("pebblev", -1.5, P - 2.1, -13.6, 1.5, P + 2.1, -11.7)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, -13.8))
    k.write("cobblestone_turret", path, bounds=(3, 2.5), offset=(0, 0.5, 0))


def snowball_repeater(path):
    """Snowball Repeater: a crank gun. A wooden gearbox with a snow hopper on top turns a drum of four iron barrels (2x2
    with square plates, so a quarter turn looks the same: the fire clip turns it 90 degrees a shot, which reads as steady
    turning while it fires), cranked by a handle at the back through a gear on its flank. muzzle_0 = the upper right
    barrel's tip, where a barrel always stands when a shot leaves (snowball.json muzzle_length 1.0125 = 16.2 px)."""
    k = Kit(128)
    rustic_yoke(k)
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[k.box("gearbox", -3.8, 3.4, -2.0, 3.8, 10.2, 5.6)])
    k.bone("body_iron", "body", pivot=(0, P, 0), cubes=[
        k.box("gbstrap", -4.0, 3.2, 0.4, 4.0, 10.4, 1.2), k.box("gbstrap", -4.0, 3.2, 3.6, 4.0, 10.4, 4.4),
        k.box("bearing", -2.6, P - 2.6, -2.6, 2.6, P + 2.6, -2.0)])
    k.bone("hopper", "body", pivot=(0, 10.2, 1.8), cubes=[
        k.box("neck", -1.8, 10.2, 0.0, 1.8, 11.2, 3.6), k.box("funnel", -2.8, 11.2, -0.8, 2.8, 12.2, 4.4),
        k.box("bowl", -3.6, 12.2, -1.6, 3.6, 14.2, 5.2),
        k.box("rimx", -3.6, 14.2, -1.6, 3.6, 15.0, -1.0), k.box("rimx", -3.6, 14.2, 4.6, 3.6, 15.0, 5.2),
        k.box("rimz", -3.6, 14.2, -1.0, -3.0, 15.0, 4.6), k.box("rimz", 3.0, 14.2, -1.0, 3.6, 15.0, 4.6)])
    k.bone("hopper_iron", "hopper", pivot=(0, 13.2, 1.8), cubes=[k.box("hband", -3.8, 12.8, -1.8, 3.8, 13.6, 5.4)])
    k.bone("snow", "hopper", pivot=(0, 14.2, 1.8), cubes=[
        k.box("snowfill", -3.0, 14.2, -1.0, 3.0, 14.8, 4.6), k.box("heap", -2.0, 14.8, 0.0, 2.0, 15.5, 3.6),
        k.box("heaptop", -1.0, 15.5, 0.8, 1.0, 16.0, 2.8)])
    # The drum turns about the bore axis (fire clip); everything on it is 4-fold symmetric.
    k.bone("drum", "body", pivot=(0, P, -4.6), cubes=k.rod_z("drum", 0, P, -7.0, -2.6, 7.6, 5.6))
    k.bone("drum_iron", "drum", pivot=(0, P, -4.6), cubes=k.rod_z("dhoop", 0, P, -6.6, -5.8, 8.0, 6.0)
           + k.rod_z("dhoop", 0, P, -3.8, -3.0, 8.0, 6.0) + [
               k.box("clamp", -3.4, P - 3.4, -11.0, 3.4, P + 3.4, -10.2), k.box("clamp2", -3.2, P - 3.2, -14.8, 3.2, P + 3.2, -14.0)])
    tubes = [(sx * 1.9, P + sy * 1.9) for sx in (-1, 1) for sy in (-1, 1)]
    k.bone("barrel_0", "drum", pivot=(0, P, -7.0), cubes=[c for x, y in tubes for c in k.rod_z("sbarrel", x, y, -16.0, -7.0, 2.4, 1.6)])
    k.bone("bores", "barrel_0", pivot=(0, P, -16.0), cubes=[k.box("sbore", x - 0.6, y - 0.6, -16.05, x + 0.6, y + 0.6, -16.0) for x, y in tubes])
    gy, gz = P + 1.0, 3.6
    k.bone("gear", "body", pivot=(4.3, gy, gz), cubes=[
        k.box("gear", 4.0, gy - 1.7, gz - 1.7, 4.6, gy + 1.7, gz + 1.7),
        k.box("gear", 4.0, gy - 1.7, gz - 1.7, 4.6, gy + 1.7, gz + 1.7, rot=[45, 0, 0], pivot=[4.3, gy, gz])])
    k.bone("crank", "body", pivot=(0, P, 5.6), cubes=[
        k.box("crankhub", -1.0, P - 1.0, 5.6, 1.0, P + 1.0, 6.6), k.box("crankarm", -0.5, P - 0.5, 6.6, 0.5, P + 4.0, 7.4)])
    k.bone("crank_handle", "crank", pivot=(0, P + 3.6, 7.4), cubes=[k.box("handle", -0.7, P + 2.9, 7.4, 0.7, P + 4.3, 9.8)])
    k.bone("muzzle_0", "body", pivot=(-1.9, P + 1.9, -16.2))
    k.write("snowball_repeater", path, bounds=(3, 2.5), offset=(0, 0.5, 0))


def potato_shotgun(path):
    """Potato Shotgun, a spud gun: a fat iron pipe strapped with leather to a wooden fore-stock, a riveted pressure tank
    behind it and a hand pump standing on the tank (the fire clip pumps it down and up). One muzzle_0 for all the pellets
    (potato.json muzzle_length 1.20625 = 19.3 px)."""
    k = Kit(128)
    rustic_yoke(k)
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[k.box("forestock", -2.2, 3.0, -13.0, 2.2, 4.8, -3.6)])
    k.bone("tank", "body", pivot=(0, P, 1.5), cubes=k.rod_z("tank", 0, P, -3.0, 6.0, 8.6, 6.6)
           + k.rod_z("tankcap", 0, P, 6.0, 6.8, 6.4, 4.4) + k.rod_z("reducer", 0, P, -3.8, -3.0, 6.8, 5.0))
    rivets = []
    for z in (-2.3, 5.3):
        rivets += [k.box("trivet", -0.35, P + 4.3, z - 0.35, 0.35, P + 4.6, z + 0.35),
                   k.box("trivetx", -4.6, P - 0.35, z - 0.35, -4.3, P + 0.35, z + 0.35), k.box("trivetx", 4.3, P - 0.35, z - 0.35, 4.6, P + 0.35, z + 0.35)]
    k.bone("tank_rivets", "tank", pivot=(0, P, 1.5), cubes=rivets)
    k.bone("straps", "body", pivot=(0, P, 0), cubes=k.rod_z("tstrap", 0, P, 0.2, 1.0, 9.0, 7.0) + [
        k.box("pstrap", -2.9, 2.8, -8.4, 2.9, 9.9, -7.6), k.box("pstrap", -2.9, 2.8, -14.6, 2.9, 9.9, -13.8)])
    k.bone("pump_barrel", "body", pivot=(0, P + 6.5, 3.6), cubes=[
        k.box("pumptube", -1.1, P + 4.0, 2.5, 1.1, P + 9.0, 4.7), k.box("pumptubev", -0.75, P + 4.0, 2.15, 0.75, P + 9.0, 5.05),
        k.box("pumpcap", -1.4, P + 9.0, 2.2, 1.4, P + 9.6, 5.0)])
    # The plunger: it drops into the tube on each shot (fire clip).
    k.bone("pump", "body", pivot=(0, P + 9.6, 3.6), cubes=[k.box("plunger", -0.35, P + 9.6, 3.25, 0.35, P + 12.0, 3.95)])
    k.bone("pump_handle", "pump", pivot=(0, P + 12.4, 3.6), cubes=[k.box("tbar", -3.0, P + 12.0, 3.0, 3.0, P + 12.9, 4.2)])
    k.bone("pump_grips", "pump_handle", pivot=(0, P + 12.4, 3.6), cubes=[
        k.box("grip", -3.2, P + 11.9, 2.9, -2.0, P + 13.0, 4.3), k.box("grip", 2.0, P + 11.9, 2.9, 3.2, P + 13.0, 4.3)])
    k.bone("barrel_0", "body", pivot=(0, P, -3.8), cubes=k.rod_z("pipe", 0, P, -18.0, -3.8, 5.4, 4.0)
           + k.rod_z("coupling", 0, P, -11.0, -9.6, 6.4, 5.0) + k.rod_z("mouth", 0, P, -19.0, -17.4, 6.6, 5.2))
    k.bone("bore", "barrel_0", pivot=(0, P, -19.0), cubes=[k.box("bore", -2.1, P - 2.1, -19.05, 2.1, P + 2.1, -19.0)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, -19.3))
    k.write("potato_shotgun", path, bounds=(3, 2.5), offset=(0, 0.5, 0))


# Arrow Launcher: the arrow tips sit at the JSON muzzle (arrow_launcher.json pivot_height 0.4375 = P / 16, muzzle_length
# 1.5875 = -AL_TIP / 16; kept as they are, its min-range reach margin is thin). muzzle_0..5 = AL_ARROWS, top row first.
AL_TIP = -25.4
AL_ARROWS = [(x, P + y) for y in (1.3, -1.3) for x in (-2.4, 0.0, 2.4)]
# Hwacha box (user 2026-10-23: the ballista read as a crossbow aimed level): half width, half height, front and back z.
# Every point of it is further than 6 px from the axle, so it clears the yoke arms over the whole pitch range.
AL_BOX = (6.0, 5.2, -20.0, -8.0)
# Bow (per side, geo +X shown) on the box's back corners: the arm swept back AL_SWEEP degrees from its root, the string
# from its tip to the slider's lug (AL_LUG), all behind the box. The fire clip straightens the strings (keyframe -string
# angle) and slides the slider forward.
AL_ROOT, AL_ARM, AL_SWEEP, AL_LUG = (6.0, -8.6), 4.5, 25.0, (5.0, -4.7)
# Cart wheels on the yaw pivot, round the axle: inside the bow and string's sweep (both stay over 5.5 px from the axle).
AL_WHEEL_X, AL_WHEEL_R = (7.7, 8.9), 5.0


def al_string():
    """Bow tip, string length and string angle (degrees, geo +X side)."""
    tip = (AL_ROOT[0] + AL_ARM * math.cos(math.radians(AL_SWEEP)), AL_ROOT[1] + AL_ARM * math.sin(math.radians(AL_SWEEP)))
    dx, dz = AL_LUG[0] - tip[0], AL_LUG[1] - tip[1]
    return tip, math.hypot(dx, dz), math.degrees(math.atan2(dz, -dx))


def arrow_launcher(path):
    """Arrow Launcher, a hwacha (user 2026-10-23): a wooden box with a grid of 20 arrow holes in its face, six of them
    loaded (2 rows of 3, tips out), on a two-wheeled cart with pull shafts. A small bow on the box's back corners is drawn
    by two strings to a slider behind the arrows; a windlass with rope sits on the stock. The code keeps it pitched 40-85
    degrees up, so it never looks level. Fire clip: the strings snap straight, the slider jumps forward, the rack
    empties, the windlass winds it back and six new arrows slide in from behind. muzzle_0..5 = the arrow tips (AL_TIP)."""
    k = Kit(128)
    rustic_yoke(k)
    wx0, wx1 = AL_WHEEL_X
    R, a, at = AL_WHEEL_R, 2 * AL_WHEEL_R * TAN22, 2 * (AL_WHEEL_R + 0.35) * TAN22
    wheels, tyres = [], []
    for side in (-1, 1):
        x0, x1 = sorted((side * wx0, side * wx1))
        for i in range(8):
            spin = dict(rot=[45 * i, 0, 0], pivot=[x0, P, 0])
            wheels.append(k.box("rim", x0, P + R - 1.0, -a / 2, x1, P + R, a / 2, **spin))
            tyres.append(k.box("tyre", x0 + 0.15, P + R, -at / 2, x1 - 0.15, P + R + 0.35, at / 2, **spin))
        wheels += [k.box("wspoke", x0 + 0.3, P - R + 0.6, -0.4, x1 - 0.3, P + R - 0.6, 0.4, rot=[45 * i, 0, 0], pivot=[x0, P, 0])
                   for i in range(4)]
        tyres += k.rod_x("hub", P, 0, *sorted((side * 7.0, side * 9.6)), 2.0)
    k.bone("wheels", "yaw_pivot", pivot=(0, P, 0), cubes=wheels)
    k.bone("wheel_iron", "wheels", pivot=(0, P, 0), cubes=tyres)
    k.bone("shafts", "yaw_pivot", pivot=(0, 2.0, 3.4), cubes=[
        k.box("pole", x0, 1.6, 3.4, x0 + 0.8, 2.4, 7.6) for x0 in (-4.4, 3.6)])
    bx, by, z0, z1 = AL_BOX
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[
        k.box("stock", -4.4, P - 3.0, -3.0, 4.4, P + 2.0, 2.5), k.box("trough", -4.4, P - 3.4, z1 - 0.4, 4.4, P - 2.6, -3.0),
        k.box("rail", -4.4, P - 2.6, z1 - 0.4, -3.6, P - 1.6, -3.0), k.box("rail", 3.6, P - 2.6, z1 - 0.4, 4.4, P - 1.6, -3.0),
        k.box("post", -3.8, P + 2.0, 0.6, -3.0, P + 4.4, 2.0), k.box("post", 3.0, P + 2.0, 0.6, 3.8, P + 4.4, 2.0)])
    k.bone("barrel_0", "body", pivot=(0, P, (z0 + z1) / 2), cubes=[  # the box the arrows leave from, open at the back
        k.box("lid", -bx, P + by - 0.8, z0 + 1.0, bx, P + by, z1), k.box("floor", -bx, P - by, z0 + 1.0, bx, P - by + 0.8, z1),
        k.box("wall", -bx, P - by + 0.8, z0 + 1.0, -bx + 0.8, P + by - 0.8, z1), k.box("wall", bx - 0.8, P - by + 0.8, z0 + 1.0, bx, P + by - 0.8, z1)])
    k.bone("box_face", "barrel_0", pivot=(0, P, z0), cubes=[k.box("face", -bx, P - by, z0, bx, P + by, z0 + 1.0)])
    k.bone("holes", "box_face", pivot=(0, P, z0), cubes=[
        k.box("hole", x - 0.7, P + y - 0.7, z0 - 0.05, x + 0.7, P + y + 0.7, z0) for y in (-3.9, -1.3, 1.3, 3.9) for x in (-4.8, -2.4, 0.0, 2.4, 4.8)])
    iron = [k.box("bowmount", x0, P - 1.0, AL_ROOT[1] - 0.8, x0 + 0.6, P + 1.0, AL_ROOT[1] + 0.8) for x0 in (-bx - 0.6, bx)]
    for zb in (z0 + 2.4, z1 - 3.0):  # bands round the box
        iron += [k.box("bandtop", -bx - 0.2, P + by, zb, bx + 0.2, P + by + 0.2, zb + 0.6),
                 k.box("bandtop", -bx - 0.2, P - by - 0.2, zb, bx + 0.2, P - by, zb + 0.6),
                 k.box("bandside", -bx - 0.2, P - by, zb, -bx, P + by, zb + 0.6), k.box("bandside", bx, P - by, zb, bx + 0.2, P + by, zb + 0.6)]
    k.bone("body_iron", "body", pivot=(0, P, (z0 + z1) / 2), cubes=iron)
    k.bone("body_rivets", "body_iron", pivot=(0, P, (z0 + z1) / 2), cubes=[
        k.box("arivet", x0, P + y - 0.25, z + 0.05, x0 + 0.25, P + y + 0.25, z + 0.55)
        for x0 in (-bx - 0.45, bx + 0.2) for y in (-3.0, 3.0) for z in (z0 + 2.4, z1 - 3.0)])
    k.bone("slider", "body", pivot=(0, P, -4.9), cubes=[k.box("slider", -3.5, P - 2.5, -5.6, 3.5, P + 2.6, -4.2)])
    k.bone("slider_iron", "slider", pivot=(0, P, -4.7), cubes=[
        k.box("lug", -5.2, P - 0.5, -5.2, -3.5, P + 0.5, -4.2), k.box("lug", 3.5, P - 0.5, -5.2, 5.2, P + 0.5, -4.2)])
    # The rack empties and refills by scaling this bone (fire clip); its pivot sits on the arrow tails.
    k.bone("arrows", "body", pivot=(0, P, -5.6), cubes=[k.box("shaft", x - 0.3, y - 0.3, -24.0, x + 0.3, y + 0.3, -5.6) for x, y in AL_ARROWS])
    k.bone("arrowheads", "arrows", pivot=(0, P, -24.5), cubes=[
        c for x, y in AL_ARROWS for c in (k.box("head", x - 0.6, y - 0.2, -25.0, x + 0.6, y + 0.2, -24.0),
                                          k.box("headtip", x - 0.25, y - 0.15, AL_TIP, x + 0.25, y + 0.15, -25.0))])
    k.bone("fletching", "arrows", pivot=(0, P, -7.4), cubes=[
        c for x, y in AL_ARROWS for c in (k.box("fletch", x - 0.8, y - 0.08, -9.0, x + 0.8, y + 0.08, -5.8),
                                          k.box("fletchv", x - 0.08, y - 0.8, -9.0, x + 0.08, y + 0.8, -5.8))])
    tip, length, angle = al_string()
    for side, name in ((1, "l"), (-1, "r")):
        rx, rz = side * AL_ROOT[0], AL_ROOT[1]
        x0, x1 = sorted((rx, rx + side * AL_ARM))
        r0, r1 = sorted((rx, rx + side * 3.0))
        k.bone(f"bow_{name}", "body", pivot=(rx, P, rz), cubes=[
            k.box("bowarm", x0, P - 0.6, rz - 0.6, x1, P + 0.6, rz + 0.6, rot=[0, -side * AL_SWEEP, 0], pivot=[rx, P, rz]),
            k.box("bowroot", r0, P - 0.9, rz - 0.8, r1, P + 0.9, rz + 0.8, rot=[0, -side * AL_SWEEP, 0], pivot=[rx, P, rz])])
        tx = side * tip[0]
        s0, s1 = sorted((tx, tx - side * length))
        k.bone(f"string_{name}", f"bow_{name}", pivot=(tx, P, tip[1]), cubes=[
            k.box("string", s0, P - 0.15, tip[1] - 0.15, s1, P + 0.15, tip[1] + 0.15, rot=[0, side * angle, 0], pivot=[tx, P, tip[1]])])
    k.bone("windlass", "body", pivot=(0, P + 3.4, 1.3), cubes=k.rod_x("roller", P + 3.4, 1.3, -3.0, 3.0, 2.0))
    spokes = []
    for x0 in (-4.4, 3.8):
        spokes += [k.box("spoke", x0, P + 1.2, 1.0, x0 + 0.6, P + 5.6, 1.6), k.box("spokez", x0, P + 3.1, -0.9, x0 + 0.6, P + 3.7, 3.5)]
    k.bone("windlass_spokes", "windlass", pivot=(0, P + 3.4, 1.3), cubes=spokes)
    for i, (x, y) in enumerate(AL_ARROWS):
        k.bone(f"muzzle_{i}", "body", pivot=(x, y, AL_TIP))
    k.write("arrow_launcher", path, bounds=(3.5, 2.5), offset=(0, 0.5, 0))


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
railgun(f"{out}/railgun_turret.geo.json")
mortar(f"{out}/mortar_turret.geo.json")
mortar_shell(f"{out}/entity/turret_mortar_shell.geo.json")
repulsor(f"{out}/repulsor_turret.geo.json")
repulsor_dome(f"{out}/repulsor_dome_turret.geo.json")
ion_rifle(f"{out}/ion_rifle_turret.geo.json")
plasma_repeater(f"{out}/plasma_repeater_turret.geo.json")
plasma_cannon(f"{out}/plasma_cannon_turret.geo.json")
cobblestone_turret(f"{out}/cobblestone_turret.geo.json")
snowball_repeater(f"{out}/snowball_repeater.geo.json")
potato_shotgun(f"{out}/potato_shotgun.geo.json")
arrow_launcher(f"{out}/arrow_launcher.geo.json")
javelin_turret(f"{out}/javelin_turret.geo.json")
javelin_missile(f"{out}/entity/turret_javelin.geo.json")
brood_cannon_turret(f"{out}/brood_cannon_turret.geo.json")
brood_capsule(f"{out}/entity/turret_brood_capsule.geo.json")
brood_spider(f"{out}/entity/brood_spider.geo.json")
hive_carrier_turret(f"{out}/hive_carrier_turret.geo.json")
hive_drone(f"{out}/entity/hive_drone.geo.json")
