#!/usr/bin/env python3
"""Procedural texture generator for Bastion Turrets (docs/PLAN.md 5.4, 6.5, 7.5 and 9 "Aset").

Every texture is a pure function of fixed seeds, so re-running reproduces identical PNGs.
- Sprites (items, GUI): functions returning an RGBA float array, listed in TEXTURES.
- Blockbench models: paint_model() reads the exported geo.json and paints every UV face with the
  material of its bone (MODEL_MATERIALS), giving <name>.png plus the emissive <name>_e.png.
  Re-run after every model export.

    pip install numpy pillow
    python3 tools/gen_textures.py
"""
import json
import math
from functools import cache
from pathlib import Path

import numpy as np
from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bastion"
OUT = ASSETS / "textures"


def rgb(hex_code):
    return np.array([int(hex_code[i:i + 2], 16) for i in (0, 2, 4)], dtype=float) / 255


# Palette, PLAN 7.5: off-white matte panels, graphite frame, small metal accents, per-weapon energy colour.
PANEL = rgb("D9DDE2")
GRAPHITE = rgb("2A2E35")
METAL = rgb("8A929C")
EMITTER = rgb("EEF6FF")
KINETIC = rgb("BDEBFF")
MAGENTA = rgb("FF4FD8")
OUTLINE = rgb("15181D")

# GUI palette
UI_BG = rgb("1E232A")
UI_EDGE = rgb("3C4550")
UI_LIGHT = rgb("5F6B79")
UI_SLOT = rgb("111418")
UI_ACCENT = rgb("9FC4E8")


# --- primitives -------------------------------------------------------------------------------

def noise(shape, amount, seed):
    """Per-pixel brightness jitter that breaks up flat fills."""
    return 1 + np.random.default_rng(seed).normal(0, amount, shape)


def rect(img, x, y, w, h, colour):
    img[y:y + h, x:x + w, :3] = colour
    img[y:y + h, x:x + w, 3] = 1


def pixel_art(rows, palette, size=16):
    """Hand-placed pixels: one character per pixel, '.' is transparent."""
    img = np.zeros((size, size, 4))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                rect(img, x, y, 1, 1, palette[ch])
    return img


def outline(img, colour):
    """1px outline around every opaque pixel (4-neighbour), the usual Minecraft item readability trick."""
    solid = img[..., 3] > 0
    grown = solid.copy()
    grown[1:] |= solid[:-1]
    grown[:-1] |= solid[1:]
    grown[:, 1:] |= solid[:, :-1]
    grown[:, :-1] |= solid[:, 1:]
    ring = grown & ~solid
    img[ring, :3] = colour
    img[ring, 3] = 1
    return img


def stamp(img, sprite, x, y):
    """Copies the opaque pixels of `sprite` (a list of rows) onto img at (x, y)."""
    h, w = len(sprite.rows), len(sprite.rows[0])
    part = pixel_art(sprite.rows, sprite.palette, size=max(w, h))[:h, :w]
    mask = part[..., 3] > 0
    img[y:y + h, x:x + w][mask] = part[mask]


class Sprite:
    def __init__(self, rows, palette):
        self.rows, self.palette = rows, palette


# --- model painter ----------------------------------------------------------------------------

# Material per bone of each Blockbench model. Every bone that owns cubes must be listed.
MODEL_MATERIALS = {
    "turret_base": {"plinth": "hex", "buttress": "armor", "body": "armor", "glow_windows": "emitter",
                    "status_light": "emitter", "deck": "frame", "energy_ring": "emitter", "weapon_mount": "steel",
                    "armor_t2": "hex", "fins_t3": "frame", "glow_fins_t3": "emitter"},
    "gun_turret": {"yaw_pivot": "armor", "axle": "steel", "body": "armor", "body_frame": "hex", "glow_strips": "emitter",
                   "vent_l": "vent", "vent_r": "vent", "sensor": "steel", "glow_lens": "emitter", "barrel_0": "frame",
                   "glow_barrel": "emitter", "muzzle_brake": "steel", "bore": "bore"},
    "machine_gun_turret": {"yaw_pivot": "armor", "axle": "steel", "body": "armor", "body_frame": "hex",
                           "glow_strips": "emitter", "vent_l": "vent", "vent_r": "vent", "fan": "steel", "sensor": "steel",
                           "glow_lens": "emitter", "barrel_cluster": "frame", "glow_barrels": "emitter"},
    "shotgun_turret": {"yaw_pivot": "armor", "axle": "steel", "body": "armor", "body_frame": "frame", "glow_strips": "emitter",
                       "vent_l": "vent", "vent_r": "vent", "sensor": "steel", "glow_lens": "emitter", "barrel_0": "frame",
                       "muzzle_shroud": "armor", "bores": "bore", "glow_barrel": "emitter", "pump": "steel"},
    "sniper_turret": {"yaw_pivot": "armor", "axle": "steel", "body": "armor", "body_frame": "hex", "glow_strips": "emitter",
                      "vent_l": "vent", "vent_r": "vent", "sensor": "steel", "glow_lens": "emitter", "barrel_0": "frame",
                      "glow_barrel": "emitter", "muzzle_brake": "steel", "bore": "bore"},
    "rocket_launcher_turret": {"yaw_pivot": "armor", "axle": "steel", "body": "armor", "body_frame": "hex", "tubes": "frame",
                               "bores": "bore", "rocket_0": "steel", "rocket_1": "steel", "rocket_2": "steel",
                               "rocket_3": "steel", "glow_strips": "emitter", "vent_l": "vent",
                               "vent_r": "vent", "sensor": "steel", "glow_lens": "emitter"},
    "entity/turret_rocket": {"body": "armor", "nose": "steel", "fins": "frame", "glow_nozzle": "emitter"},
    "entity/turret_missile": {"body": "steel", "nose": "armor", "fins": "frame", "glow_nozzle": "emitter"},
    "large_turret_base": {"plinth": "hex", "body": "armor", "glow_windows": "emitter", "status_light": "emitter",
                          "deck": "frame", "energy_ring": "emitter", "weapon_mount": "steel", "armor_t2": "hex",
                          "fins_t3": "frame", "glow_fins_t3": "emitter"},
    "missile_launcher_turret": {"yaw_pivot": "armor", "axle": "steel", "body": "armor", "pod_l": "armor", "pod_r": "armor",
                                "pod_l_bores": "bore", "pod_r_bores": "bore", "glow_pod_l": "emitter", "glow_pod_r": "emitter",
                                **{f"missile_{i}": "steel" for i in range(12)}, "sensor": "steel", "glow_lens": "emitter",
                                "glow_strips": "emitter"},
    "flamethrower_turret": {"yaw_pivot": "armor", "axle": "steel", "body": "armor", "body_frame": "hex", "tanks": "steel",
                            "feed": "frame", "barrel_0": "frame", "glow_barrel": "emitter", "nozzle": "steel", "bore": "bore",
                            "igniter": "frame", "glow_igniter": "emitter", "glow_strips": "emitter", "vent_l": "vent",
                            "vent_r": "vent", "sensor": "steel", "glow_lens": "emitter"},
    "laser_rifle_turret": {"yaw_pivot": "armor", "axle": "steel", "body": "armor", "body_frame": "hex", "glow_strips": "emitter",
                           "vent_l": "vent", "vent_r": "vent", "sensor": "steel", "glow_lens": "emitter", "barrel_0": "frame",
                           "focus_rings": "steel", "glow_focus": "emitter", "emitter": "steel", "glow_emitter": "emitter"},
    # workstations (PLAN Fase 9)
    "part_workstation": {"frame": "frame", "body": "armor", "top": "hex", "tools": "steel", "glow_drawers": "emitter",
                         "screen": "frame", "glow_screen": "emitter", "carriage": "steel", "spindle": "frame",
                         "glow_spindle": "emitter"},
    "part_assembler": {"base": "hex", "frame": "frame", "glow_frame": "emitter", "turntable": "steel", "glow_turntable": "emitter",
                       "scanner": "steel", "glow_scanner": "emitter", "mount_l": "armor", "mount_r": "armor", "arm_l": "steel",
                       "arm_r": "steel", "fore_l": "frame", "fore_r": "frame", "glow_torch_l": "emitter", "glow_torch_r": "emitter",
                       "console": "armor", "glow_console": "emitter"},
    "module_workstation": {"body": "armor", "frame": "frame", "desk": "hex", "glow_body": "emitter", "glow_screen": "emitter",
                           "gantry": "steel", "head": "frame", "glow_head": "emitter"},
    "ammo_workstation": {"body": "armor", "frame": "frame", "table": "hex", "vents": "vent", "glow_body": "emitter",
                         "ram": "steel", "glow_ram": "emitter"},
    "charging_station": {"body": "armor", "plate": "hex", "glow_plate": "emitter", "cradle": "frame", "pylons": "steel",
                         "coils": "frame", "glow_tips": "emitter"},
    "tesla_turret": {"yaw_pivot": "armor", "body": "hex", "body_frame": "frame", "glow_strips": "emitter", "column": "steel",
                     "coil": "frame", "glow_coil": "emitter", "crown": "armor", "glow_crown": "emitter", "terminal": "steel",
                     "glow_terminal": "emitter", "electrodes": "steel", "glow_electrodes": "emitter", "sensor": "steel",
                     "glow_lens": "emitter"},
}


# Model palette (redesign 2026-10-06, user: "jangan warna-warni"): gunmetal hard-surface, one cyan glow for
# every turret. Weapon identity lives in the VFX colours, not on the models.
GUNMETAL = rgb("4E555E")
STEEL = rgb("858E99")
DARKSTEEL = rgb("272B30")
BORE = rgb("111316")
GLOW_BASE = rgb("2E9FBF")  # emitter texels as seen without the emissive layer
# v1 material names still map, so an unconverted model keeps painting
MATERIAL_ALIASES = {"panel": "armor", "panel_plain": "armor", "graphite": "frame", "metal": "steel"}
MATERIAL_COLOURS = {"armor": GUNMETAL, "hex": GUNMETAL, "frame": DARKSTEEL, "steel": STEEL, "vent": DARKSTEEL,
                    "bore": BORE, "emitter": GLOW_BASE}


def hex_lines(h, w, size=3.0):
    """Outline mask of a small hex grid (the panelling on the reference turrets)."""
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    q = (xx * np.sqrt(3) / 3 - yy / 3) / size
    r = yy * 2 / 3 / size
    # cube rounding distance: near a cell border when two of the three cube coords sit near .5
    cx, cz = q, r
    cy = -cx - cz
    fx, fy, fz = [np.abs(c - np.round(c)) for c in (cx, cy, cz)]
    return np.maximum(np.maximum(np.minimum(fx, fy), np.minimum(fy, fz)), np.minimum(fx, fz)) > 0.36


def face_pixels(material, face, w, h, seed):
    """Colour and glow intensity of one UV face. Side faces are painted top row first."""
    material = MATERIAL_ALIASES.get(material, material)
    base = MATERIAL_COLOURS[material]
    shade = np.ones((h, w))
    glow = np.zeros((h, w))
    rough = 0.035
    if material == "steel":  # brushed: streaks along the face
        shade *= 1 + np.random.default_rng(seed + 1).normal(0, 0.05, (h, 1))
        rough = 0.02
    if material == "vent" and h >= 2:  # slats with dark gaps
        shade[0::2] *= 0.55
        shade[1::2] *= 1.55
    # Detail only on side faces: octagon tops are overlapping boxes, where edge lines would draw a star.
    side = face not in ("up", "down")
    if side and material == "hex" and w >= 4 and h >= 4:
        shade[hex_lines(h, w)] *= 0.72
    if side and material in ("armor", "hex") and w >= 8:
        shade[:, w // 2] *= 0.7  # panel seam
    if side and material in ("armor", "hex") and w >= 6 and h >= 6:
        for y, x in ((1, 1), (1, w - 2), (h - 2, 1), (h - 2, w - 2)):
            shade[y, x] *= 1.35  # rivets
    # bevels: light catches the top-left edges, the bottom-right ones fall into shadow
    if side and material not in ("bore", "emitter"):
        if h > 1:
            shade[0] *= 1.28
            shade[-1] *= 0.66
        if w > 2:
            shade[:, 0] *= 1.1
            shade[:, -1] *= 0.82
    if face == "up":
        shade *= 1.1
    elif face == "down":
        shade *= 0.55
    if material == "emitter":
        glow[:] = 1
        shade *= 1 + 0.15 * np.linspace(1, -1, h)[:, None]  # a little gradient inside the strip
        rough = 0.01
    colour = base * (shade * noise((h, w), rough, seed))[..., None]
    return np.clip(colour, 0, 1), glow


@cache
def paint_model(name):
    """Returns (base, emissive) textures for assets/bastion/geo/<name>.geo.json.

    Faces sharing a UV region keep the paint of the first bone that reaches it, so put the
    bone whose look should win for shared hidden faces first in the outliner.
    """
    geo = json.loads((ASSETS / f"geo/{name}.geo.json").read_text())["minecraft:geometry"][0]
    size = (geo["description"]["texture_height"], geo["description"]["texture_width"])
    base, glow = np.zeros(size + (4,)), np.zeros(size + (4,))
    for bone in geo["bones"]:
        for cube in bone.get("cubes", []):
            for face, f in cube["uv"].items():
                (u, v), (w, h) = f["uv"], f["uv_size"]
                x0, y0 = math.floor(min(u, u + w)), math.floor(min(v, v + h))
                x1, y1 = math.ceil(max(u, u + w)), math.ceil(max(v, v + h))
                if base[y0:y1, x0:x1, 3].all():
                    continue
                colour, light = face_pixels(MODEL_MATERIALS[name][bone["name"]], face, x1 - x0, y1 - y0, x0 * 977 + y0)
                base[y0:y1, x0:x1, :3], base[y0:y1, x0:x1, 3] = colour, 1
                glow[y0:y1, x0:x1] = light[..., None]  # white, alpha = intensity
    return base, glow


# --- items ------------------------------------------------------------------------------------

# Shading letters: upper case = lit (left), lower = mid, last = shade (right).
ROUND = Sprite([
    ".M.",
    "MMm",
    "Wws",
    "Wws",
    "Wws",
    "ccc",
    "Wws",
    "Ggk",
    "Ggk",
    "Mmn",
], {"M": METAL * 1.25, "m": METAL, "n": METAL * 0.7, "W": PANEL * 1.08, "w": PANEL, "s": PANEL * 0.75,
    "c": KINETIC, "G": GRAPHITE * 1.6, "g": GRAPHITE * 1.2, "k": GRAPHITE * 0.9})

SHELL = Sprite([
    "WWws",
    "Wwws",
    "Mmmn",
    "Gggk",
    "PPpq",
    "PPpq",
    "Gggk",
    "Gggk",
    "MMmn",
    "MMmn",
    "nnnn",
], {"W": PANEL * 1.08, "w": PANEL, "s": PANEL * 0.75, "M": METAL * 1.25, "m": METAL, "n": METAL * 0.65,
    "G": GRAPHITE * 1.7, "g": GRAPHITE * 1.25, "k": GRAPHITE * 0.9, "P": MAGENTA * 1.0, "p": MAGENTA * 0.8,
    "q": MAGENTA * 0.55})


def kinetic_rounds():
    """Three staggered kinetic rounds with a pale energy band (PLAN 7.4)."""
    img = np.zeros((16, 16, 4))
    for x, y in ((2, 4), (6, 2), (10, 4)):
        stamp(img, ROUND, x, y)
    return outline(img, OUTLINE)


def creative_ammo():
    """Kinetic rounds banded in all three weapon colours: fits every turret (creative only)."""
    img = np.zeros((16, 16, 4))
    for (x, y), band in zip(((2, 4), (6, 2), (10, 4)), ("4FD8FF", "FFB347", "FF4FD8")):
        stamp(img, Sprite(ROUND.rows, {**ROUND.palette, "c": rgb(band)}), x, y)
    return outline(img, OUTLINE)


SNIPER_ROUND = Sprite([
    ".M.",
    "MMm",
    "MMm",
    "Wws",
    "Wws",
    "Wws",
    "Wws",
    "ccc",
    "Wws",
    "Ggk",
    "Ggk",
    "Mmn",
], {**ROUND.palette, "c": rgb("9DFF6A")})

ROCKET = Sprite([
    "..R..",
    ".RRr.",
    ".MMm.",
    ".WWs.",
    ".WWs.",
    ".ccc.",
    ".WWs.",
    "GWWsG",
    "GGgkG",
    ".kkk.",
], {"R": rgb("FF7A2E"), "r": rgb("C24E16"), "M": METAL * 1.25, "m": METAL, "W": GUNMETAL * 1.5, "s": GUNMETAL,
    "c": rgb("FF7A2E") * 0.85, "G": GRAPHITE * 1.7, "g": GRAPHITE * 1.25, "k": GRAPHITE * 0.8})


MISSILE = Sprite([
    ".m.",
    "MMm",
    "WWs",
    "WWs",
    "ccc",
    "WWs",
    "WWs",
    "GgG",
    "GkG",
], {"m": METAL, "M": METAL * 1.25, "W": STEEL * 1.15, "s": STEEL * 0.8, "c": rgb("4FD8FF"), "G": GRAPHITE * 1.7,
    "g": GRAPHITE * 1.25, "k": GRAPHITE * 0.8})


def missiles():
    """Three small homing missiles, tilted in a little rack."""
    img = np.zeros((16, 16, 4))
    for x, y in ((2, 4), (6, 2), (10, 4)):
        stamp(img, MISSILE, x, y)
    return outline(img, OUTLINE)


FUEL_CAN = Sprite([
    "...MMMMm......",
    "...M...m..NN..",
    "..GGGGGGGGNNg.",
    "..GWWWWWWWWWsk",
    "..GWwwwwwwwwsk",
    "..GOOOOOOOOOok",
    "..GWwwwYwwwwsk",
    "..GWwwYFYwwwsk",
    "..GWwwYFFYwwsk",
    "..GWwwwYYwwwsk",
    "..GOOOOOOOOOok",
    "..GWwwwwwwwwsk",
    "..Gggggggggggk",
    "...kkkkkkkkkk.",
], {"M": METAL * 1.25, "m": METAL * 0.85, "N": rgb("C24E16"), "W": GUNMETAL * 1.55, "w": GUNMETAL * 1.2, "s": GUNMETAL * 0.8,
    "G": GRAPHITE * 1.7, "g": GRAPHITE * 1.25, "k": GRAPHITE * 0.8, "O": rgb("FF7A1E"), "o": rgb("C24E16"),
    "Y": rgb("FFC266"), "F": rgb("FF7A1E")})


def fuel_canister():
    """Flamethrower fuel: a gunmetal jerrycan with orange hazard bands and a flame glyph."""
    img = np.zeros((16, 16, 4))
    stamp(img, FUEL_CAN, 0, 1)
    return outline(img, OUTLINE)


LASER_CELL = Sprite([
    "..MMMm..",
    ".GMMMmg.",
    ".GWWWsk.",
    ".GRrrqk.",
    ".GYRrqk.",
    ".GRrrqk.",
    ".GRrrqk.",
    ".GYRrqk.",
    ".GRrrqk.",
    ".GWWWsk.",
    ".GMMMmg.",
    "..MMMm..",
], {"M": METAL * 1.25, "m": METAL * 0.85, "W": GUNMETAL * 1.55, "s": GUNMETAL * 0.8, "G": GRAPHITE * 1.7, "g": GRAPHITE * 1.25,
    "k": GRAPHITE * 0.8, "R": rgb("FF3046"), "r": rgb("C21F33"), "q": rgb("7A1420"), "Y": rgb("FFC2C8")})


def laser_cell():
    """Laser Rifle ammo: two charged cells, a red core glowing through a window."""
    img = np.zeros((16, 16, 4))
    for x, y in ((1, 3), (7, 1)):
        stamp(img, LASER_CELL, x, y)
    return outline(img, OUTLINE)


def damaged_large_base():
    """The small damaged base, twice: a broken 2x2 slab."""
    img = np.zeros((16, 16, 4))
    stamp(img, Sprite(DAMAGED_BASE.rows, DAMAGED_BASE.palette), 0, 5)
    top = Sprite(["gggggggggggggggg", "gPPPkPPPPPPkPPPg", "gPPPPkPPPPkPPPPg", "gggggggggggggggg"], DAMAGED_BASE.palette)
    stamp(img, top, 0, 1)
    return outline(img, OUTLINE)


def sniper_rounds():
    img = np.zeros((16, 16, 4))
    for x, y in ((4, 1), (9, 3)):
        stamp(img, SNIPER_ROUND, x, y)
    return outline(img, OUTLINE)


def rockets():
    img = np.zeros((16, 16, 4))
    for x, y in ((2, 2), (9, 4)):
        stamp(img, ROCKET, x, y)
    return outline(img, OUTLINE)


def scatter_shells():
    """Two staggered scatter shells with the Shotgun's magenta stripe (PLAN 7.4)."""
    img = np.zeros((16, 16, 4))
    for x, y in ((3, 3), (9, 1)):
        stamp(img, SHELL, x, y)
    return outline(img, OUTLINE)


# --- modifier chips & tools (PLAN 4.6, 4.1, 4.7) -------------------------------------------------

CHIP = Sprite([
    "...m.m.m.m.m...",
    "..ggggggggggg..",
    ".mgPPPPPPPPPgm.",
    "..gPcccccccPg..",
    ".mgPcccccccPgm.",
    "..gPcccccccPg..",
    ".mgPcccccccPgm.",
    "..gPcccccccPg..",
    ".mgPcccccccPgm.",
    "..gPcccccccPg..",
    ".mgPPPPPPPPPgm.",
    "..ggggggggggg..",
    "...m.m.m.m.m...",
], {"m": METAL, "g": GRAPHITE, "P": PANEL * 0.85, "c": GRAPHITE * 0.6})

# 7x7 glyph per modifier, drawn in its accent colour ('a') with a darker shade ('d').
MODIFIER_GLYPHS = {
    "range_module": ("4FD8FF", ["...a...", "..a.a..", ".a...a.", "aa.d.aa", ".a...a.", "..a.a..", "...a..."]),
    "rapid_cycler": ("FFB347", ["a..a...", ".a..a..", "..a..a.", "...a..a", "..a..a.", ".a..a..", "a..a..."]),
    "damage_amplifier": ("FF5A4F", ["...a...", "..aaa..", ".aaaaa.", "...a...", "...a...", "..ddd..", "..ddd.."]),
    "penetrator": ("D7C6FF", ["...d...", "...d...", "aaaaaaa", ".aaaaa.", "..aaa..", "...a...", "...d..."]),
    "ammo_recycler": ("7CFF8A", ["..aaa..", ".a...a.", "a.....a", "a..d..a", "a.....d", ".a...dd", "..aa.dd"]),
    "coolant_loop": ("A8E6FF", ["...a...", ".a.a.a.", "..aaa..", "aaadaaa", "..aaa..", ".a.a.a.", "...a..."]),
    "overclock": ("FFE14F", ["....aa.", "...aa..", "..aa...", ".aaaaa.", "...aa..", "..aa...", ".aa...."]),
    "targeting_ai": ("FF4FD8", [".aaaaa.", "a.....a", "a.ddd.a", "a.dad.a", "a.ddd.a", "a.....a", ".aaaaa."]),
    # weapon-specific: a funnel squeezing the pellets into a tight stream (Shotgun)
    "choke_module": ("FF8AE6", ["a.....a", "a.....a", ".a...a.", "..a.a..", "..ada..", "..ada..", "...d..."]),
}


def modifier_chip(name):
    """Circuit chip with a per-modifier glyph; the glyph also goes into the emissive layer (PLAN 7.5)."""
    img = np.zeros((16, 16, 4))
    stamp(img, CHIP, 0, 1)
    hex_code, rows = MODIFIER_GLYPHS[name]
    accent = rgb(hex_code)
    stamp(img, Sprite(rows, {"a": accent, "d": accent * 0.55}), 4, 4)
    return outline(img, OUTLINE)


REPAIR_KIT = Sprite([
    "................",
    ".....kkkkkk.....",
    ".....k....k.....",
    "..gggggggggggg..",
    ".gPPPPPPPPPPPPg.",
    ".gPPPPPaaPPPPPg.",
    ".gPPPPPaaPPPPPg.",
    ".gPPPaaaaaaPPPg.",
    ".gPPPaaaaaaPPPg.",
    ".gPPPPPaaPPPPPg.",
    ".gPPPPPaaPPPPPg.",
    ".gPPPPPPPPPPPPg.",
    ".gmmmmmmmmmmmmg.",
    "..gggggggggggg..",
], {"k": GRAPHITE * 1.4, "g": GRAPHITE, "P": PANEL, "a": rgb("7CFF8A"), "m": METAL})


def repair_kit():
    img = np.zeros((16, 16, 4))
    stamp(img, REPAIR_KIT, 0, 1)
    return outline(img, OUTLINE)


def upgrade_kit(level):
    """Hex plate with one chevron per tier it reaches: two for T2, three for T3."""
    rows = [
        "....gggggggg....",
        "...gPPPPPPPPg...",
        "..gPPPPPPPPPPg..",
        ".gPPPPPPPPPPPPg.",
        "gPPPPPPPPPPPPPPg",
        "gPPPPPPPPPPPPPPg",
        "gPPPPPPPPPPPPPPg",
        "gPPPPPPPPPPPPPPg",
        "gPPPPPPPPPPPPPPg",
        "gPPPPPPPPPPPPPPg",
        ".gPPPPPPPPPPPPg.",
        "..gPPPPPPPPPPg..",
        "...gPPPPPPPPg...",
        "....gggggggg....",
    ]
    img = np.zeros((16, 16, 4))
    stamp(img, Sprite(rows, {"g": METAL * 0.8, "P": GRAPHITE * 1.2}), 0, 1)
    chevron = Sprite(["...a...", "..aaa..", ".aa.aa.", "aa...aa"], {"a": rgb("4FD8FF")})
    top = 3 if level == 3 else 4
    for i in range(level):
        stamp(img, chevron, 4, top + 3 * i + (1 if level == 2 else 0))
    return outline(img, OUTLINE)


CONFIGURATOR = Sprite([
    "......mm........",
    "......mm........",
    "....gggggggg....",
    "...gPPPPPPPPg...",
    "...gPssssssPg...",
    "...gPsaaaasPg...",
    "...gPsabbasPg...",
    "...gPsaaaasPg...",
    "...gPssssssPg...",
    "...gPPPPPPPPg...",
    "...gPkPPPPkPg...",
    "...gPPPkkPPPg...",
    "...gPPPPPPPPg...",
    "....gggggggg....",
], {"m": METAL, "g": GRAPHITE, "P": PANEL, "s": GRAPHITE * 0.6, "a": KINETIC * 0.8, "b": rgb("4FD8FF"), "k": GRAPHITE * 1.5})


def configurator():
    img = np.zeros((16, 16, 4))
    stamp(img, CONFIGURATOR, 0, 1)
    return outline(img, OUTLINE)


DAMAGED_BASE = Sprite([
    "....gggggggg....",
    "...gPPPkPPPPg...",
    "..gPPPPkPPPPPg..",
    ".gPPPPPPkkPPPPg.",
    "gggggggggkgggggg",
    "gmmmmmmmmkmmmmmg",
    "gaggggrgggkgaggg",
    "gggggggggkgggggg",
    ".gGGGGGGkGGGGGg.",
    ".gGGGGGkGGGGGGg.",
    "..gggggggggggg..",
], {"g": GRAPHITE, "P": PANEL * 0.75, "k": OUTLINE, "m": METAL * 0.7, "a": rgb("FF8A3C"), "r": rgb("FF4F4F"), "G": GRAPHITE * 1.4})


def damaged_base():
    """Turret base side view: cracked, with a dead status light and one red fault light."""
    img = np.zeros((16, 16, 4))
    stamp(img, DAMAGED_BASE, 0, 3)
    return outline(img, OUTLINE)


# --- GUI --------------------------------------------------------------------------------------

# Layout (GUI pixels) mirrored in TurretMenu / TurretScreen: group labels sit above their slots so
# longer translations never run into the next group.
GUI_W, GUI_H = 216, 200
SLOT, LOCKED_SLOT = (216, 0), (234, 0)  # sprite positions
TAB, TAB_ACTIVE = (216, 18), (216, 42)  # 24x24 side tabs, left of the panel
WEAPON_SLOT = (17, 43)  # slot frame; the item sits 1px inside
PLAYER_INV = (27, 114)  # top-left slot frame of the 3x9 grid; hotbar 58px lower
DIVIDER_Y = 98


def slot(img, x, y, locked=False):
    """18x18 inset slot frame whose top-left corner is (x, y)."""
    rect(img, x, y, 18, 18, UI_LIGHT)
    rect(img, x, y, 17, 17, UI_SLOT * 0.6)
    rect(img, x + 1, y + 1, 16, 16, UI_SLOT)
    if locked:
        yy, xx = np.mgrid[0:16, 0:16]
        stripes = (xx + yy) % 4 == 0
        img[y + 1:y + 17, x + 1:x + 17][stripes, :3] = UI_EDGE


def tab(img, x, y, active):
    """Side tab, open on its right edge so the active one merges into the panel."""
    rect(img, x, y, 24, 24, UI_EDGE)
    rect(img, x + 1, y + 1, 23, 22, UI_BG * (1.0 if active else 0.7))
    rect(img, x + 1, y + 1, 1, 22, UI_LIGHT if active else UI_EDGE)
    rect(img, x + 1, y + 1, 22 if active else 21, 1, UI_LIGHT if active else UI_EDGE)
    if not active:
        rect(img, x + 23, y, 1, 24, UI_EDGE)
    for i in range(2):  # chamfered left corners
        img[y + i, x:x + 2 - i, 3] = 0
        img[y + 23 - i, x:x + 2 - i, 3] = 0


WS_W, WS_H = 256, 236
WS_INPUT, WS_OUTPUT, WS_PLAYER = (8, 130), (154, 130), (47, 155)  # top-left of the slot frames (WorkstationMenu - 1)


def workstation_gui():
    """Workstation screen background (PLAN Fase 9): the panel, title rule, the six input slots and the output (with a
    bracket like the weapon bay), the player inventory. Lists, preview, gauges and buttons are drawn by the screen."""
    img = np.zeros((256, 256, 4))
    yy, _ = np.mgrid[0:WS_H, 0:WS_W]
    img[:WS_H, :WS_W, :3] = UI_BG * (1.08 - 0.12 * yy / WS_H)[..., None] * noise((WS_H, WS_W), 0.012, seed=8)[..., None]
    img[:WS_H, :WS_W, 3] = 1
    img[0, :WS_W, :3] = img[WS_H - 1, :WS_W, :3] = UI_EDGE
    img[:WS_H, 0, :3] = img[:WS_H, WS_W - 1, :3] = UI_EDGE
    img[1, 1:WS_W - 1, :3] = img[1:WS_H - 1, 1, :3] = UI_LIGHT
    for i in range(3):  # chamfered corners
        for cx, cy, dx, dy in ((0, 0, 1, 1), (WS_W - 1, 0, -1, 1), (0, WS_H - 1, 1, -1), (WS_W - 1, WS_H - 1, -1, -1)):
            for j in range(3 - i):
                img[cy + dy * i, cx + dx * j, 3] = 0
    rect(img, 8, 16, WS_W - 16, 1, UI_ACCENT * 0.55)  # under the title
    rect(img, 8, 151, WS_W - 16, 1, UI_EDGE)  # station / player divider
    ix, iy = WS_INPUT
    for i in range(6):
        slot(img, ix + i * 18, iy)
    ox, oy = WS_OUTPUT
    rect(img, ox - 3, oy - 3, 24, 24, UI_EDGE)
    rect(img, ox - 2, oy - 2, 22, 22, UI_BG * 0.8)
    for bx, by in ((ox - 3, oy - 3), (ox + 18, oy - 3), (ox - 3, oy + 18), (ox + 18, oy + 18)):
        rect(img, bx, by, 3, 3, UI_ACCENT * 0.7)
    slot(img, ox, oy)
    px, py = WS_PLAYER
    for row in range(3):
        for col in range(9):
            slot(img, px + col * 18, py + row * 18)
    for col in range(9):
        slot(img, px + col * 18, py + 58)
    return img


def creative_power_source():
    """Creative FE block: a dark armoured cube with a glowing bolt on every face."""
    img = np.zeros((16, 16, 4))
    img[..., :3], img[..., 3] = GRAPHITE * 1.3 * noise((16, 16), 0.04, seed=91)[..., None], 1
    img[0, :, :3] = img[:, 0, :3] = METAL * 0.9
    img[15, :, :3] = img[:, 15, :3] = GRAPHITE * 0.7
    bolt = ["....aa..", "...aa...", "..aaaa..", "....aa..", "...aa...", "..aa...."]
    stamp(img, Sprite(bolt, {"a": rgb("4FD8FF")}), 4, 5)
    return img


EMPTY_CELL = Sprite(LASER_CELL.rows, {**LASER_CELL.palette, "R": GRAPHITE * 1.9, "r": GRAPHITE * 1.5, "q": GRAPHITE * 1.1,
                                      "Y": GRAPHITE * 2.4})


def empty_laser_cell():
    """A Laser Cell with a dark, uncharged core."""
    img = np.zeros((16, 16, 4))
    for x, y in ((1, 3), (7, 1)):
        stamp(img, EMPTY_CELL, x, y)
    return outline(img, OUTLINE)


def turret_gui(plain=False):
    """Turret screen background plus slot and tab sprites (PLAN 4.7). plain = Targeting/Info tabs, no slots."""
    img = np.zeros((256, 256, 4))
    yy, _ = np.mgrid[0:GUI_H, 0:GUI_W]
    body = UI_BG * (1.08 - 0.12 * yy / GUI_H)[..., None] * noise((GUI_H, GUI_W), 0.012, seed=7)[..., None]
    img[:GUI_H, :GUI_W, :3], img[:GUI_H, :GUI_W, 3] = body, 1

    # frame: edge line, inner highlight, chamfered corners
    img[0, :GUI_W, :3] = img[GUI_H - 1, :GUI_W, :3] = UI_EDGE
    img[:GUI_H, 0, :3] = img[:GUI_H, GUI_W - 1, :3] = UI_EDGE
    img[1, 1:GUI_W - 1, :3] = img[1:GUI_H - 1, 1, :3] = UI_LIGHT
    for i in range(3):
        for cx, cy, dx, dy in ((0, 0, 1, 1), (GUI_W - 1, 0, -1, 1), (0, GUI_H - 1, 1, -1), (GUI_W - 1, GUI_H - 1, -1, -1)):
            for j in range(3 - i):
                img[cy + dy * i, cx + dx * j, 3] = 0
        img[i, 3 - i, :3] = img[i, GUI_W - 4 + i, :3] = UI_EDGE
        img[GUI_H - 1 - i, 3 - i, :3] = img[GUI_H - 1 - i, GUI_W - 4 + i, :3] = UI_EDGE

    rect(img, 10, 19, GUI_W - 20, 1, UI_ACCENT * 0.55)  # under the title
    if plain:
        return img
    rect(img, 10, DIVIDER_Y, GUI_W - 20, 1, UI_EDGE)  # turret / player divider

    # weapon bay: bracketed frame around the weapon slot
    wx, wy = WEAPON_SLOT
    rect(img, wx - 8, wy - 8, 34, 34, UI_EDGE)
    rect(img, wx - 7, wy - 7, 32, 32, UI_BG * 0.8)
    for bx, by in ((wx - 8, wy - 8), (wx + 22, wy - 8), (wx - 8, wy + 22), (wx + 22, wy + 22)):
        rect(img, bx, by, 4, 4, UI_ACCENT * 0.7)
    slot(img, wx, wy)

    px, py = PLAYER_INV
    for row in range(3):
        for col in range(9):
            slot(img, px + col * 18, py + row * 18)
    for col in range(9):
        slot(img, px + col * 18, py + 58)

    slot(img, *SLOT)
    slot(img, *LOCKED_SLOT, locked=True)
    tab(img, *TAB, active=False)
    tab(img, *TAB_ACTIVE, active=True)
    return img


# --- VFX sprites (PLAN 5.2-5.4) -----------------------------------------------------------------
# White/greyscale with alpha: particles and renderers tint them with the weapon energy colour at runtime.

def grid(w, h=None):
    h = h or w
    y, x = np.mgrid[0:h, 0:w]
    return (x + 0.5) / w * 2 - 1, (y + 0.5) / h * 2 - 1  # -1..1 across the sprite

def white(alpha):
    img = np.ones(alpha.shape + (4,))
    img[..., 3] = np.clip(alpha, 0, 1)
    return img

def soft(r, power=2.0):
    return np.clip(1 - r, 0, 1) ** power

def value_noise(size, cells, seed):
    """Smooth value noise in 0..1 (bilinear over a random lattice with smoothstep)."""
    rng = np.random.default_rng(seed)
    lat = rng.random((cells + 1, cells + 1))
    t = (np.arange(size) + 0.5) / size * cells
    i = np.minimum(t.astype(int), cells - 1); f = t - i; f = f * f * (3 - 2 * f)
    a = lat[np.ix_(i, i)]; b = lat[np.ix_(i, i + 1)]; c = lat[np.ix_(i + 1, i)]; d = lat[np.ix_(i + 1, i + 1)]
    fx, fy = f[None, :], f[:, None]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy

# --- particles -----------------------------------------------------------------------------------

def spark():
    """Hot streak: bright core line along X with soft falloff; the renderer stretches it along velocity."""
    x, y = grid(16)
    a = soft(np.abs(y) * 4, 1.5) * soft(np.abs(x), 0.6)
    return white(a)

def ember():
    x, y = grid(8)
    return white(soft(np.hypot(x, y), 2.2) * 1.2)

def smoke_puff(seed):
    x, y = grid(32)
    r = np.hypot(x, y)
    n = value_noise(32, 4, seed) * 0.6 + value_noise(32, 8, seed + 1) * 0.4
    a = soft(r * (0.8 + 0.5 * n), 1.4) * (0.55 + 0.45 * n)
    img = white(a)
    img[..., :3] = (0.75 + 0.25 * n)[..., None]  # slight grey variation, tinted later
    return img

def heat_haze(seed):
    x, y = grid(32)
    n = value_noise(32, 6, seed)
    a = soft(np.hypot(x, y), 1.0) * (0.25 + 0.35 * n) * (0.5 + 0.5 * np.sin(y * 9 + n * 6))
    return white(a * 0.6)

def shockwave_ring():
    x, y = grid(64)
    r = np.hypot(x, y)
    a = np.exp(-((r - 0.82) / 0.06) ** 2) + 0.35 * np.exp(-((r - 0.74) / 0.12) ** 2)
    return white(a)

def muzzle_flash(frame):
    """4-frame flash: a star of spikes plus a hot core; spikes shorten and the core fades per frame."""
    x, y = grid(32)
    r, th = np.hypot(x, y), np.arctan2(y, x)
    rng = np.random.default_rng(40 + frame)
    spikes = 6 + frame % 2
    phase = rng.random() * np.pi
    star = np.abs(np.cos(th * spikes / 2 + phase)) ** 6
    reach = [1.0, 0.85, 0.62, 0.4][frame]
    a = soft(r / reach, 1.2) * (0.35 + 0.65 * star) + soft(r / (0.38 * reach + 0.08), 1.5) * 1.4
    return white(a * [1.0, 0.95, 0.8, 0.6][frame])

def debris(seed):
    """Small irregular chip, grey with lit top edge; tinted with the hit block's colour at spawn."""
    rng = np.random.default_rng(seed)
    img = np.zeros((8, 8, 4))
    pts = rng.integers(1, 7, size=(5, 2))
    for yy in range(8):
        for xx in range(8):
            if min(abs(xx - px) + abs(yy - py) for px, py in pts) <= 1:
                img[yy, xx] = [0.85, 0.85, 0.85, 1]
    solid = img[..., 3] > 0
    top = solid & ~np.roll(solid, 1, axis=0)
    img[top, :3] = 1.0
    bottom = solid & ~np.roll(solid, -1, axis=0)
    img[bottom, :3] = 0.55
    return img

def muzzle_ring():
    x, y = grid(32)
    r = np.hypot(x, y)
    return white(np.exp(-((r - 0.78) / 0.07) ** 2) * 1.2 + 0.25 * soft(r, 3))

def impact_splash(frame):
    """Energy crown: radial spikes from the impact point, opening and thinning over 3 frames."""
    x, y = grid(32)
    r, th = np.hypot(x, y), np.arctan2(y, x)
    rng = np.random.default_rng(70)
    lens = 0.6 + 0.4 * rng.random(9)
    k = (th + np.pi) / (2 * np.pi) * 9
    idx = k.astype(int) % 9
    spike = np.exp(-((k - np.floor(k) - 0.5) / 0.12) ** 2)
    open_ = [0.55, 0.8, 1.0][frame]
    a = spike * soft(r / (lens[idx] * open_), 1.0) * (r > 0.15 * open_) + soft(r / (0.3 * open_), 2) * [1.2, 0.7, 0.3][frame]
    return white(a)

def smoke_wisp(frame):
    x, y = grid(16, 32)
    n = value_noise(32, 5, 90 + frame)[:, :16]
    sway = np.sin((y + 1) * 3 + frame) * 0.25
    a = soft(np.abs(x - sway) * 2.2, 1.2) * soft((y + 1) / 2, 0.7)[::-1] * (0.4 + 0.6 * n)
    return white(a * 0.8)

# --- rocket explosion & exhaust ------------------------------------------------------------------

def fireball(frame):
    """6-frame burning puff: dense turbulent ball that swells, then tears into holes as it burns out.
    Greyscale with a brighter core; the particle ramps its colour white-hot -> orange -> red -> soot."""
    x, y = grid(32)
    r = np.hypot(x, y)
    f = frame / 5
    turb = value_noise(32, 4, 200 + frame) * 0.5 + value_noise(32, 8, 210 + frame) * 0.3 + value_noise(32, 16, 220) * 0.2
    edge = 0.6 + 0.25 * f + 0.4 * (turb - 0.5)
    body = soft(r / np.maximum(edge, 0.2), 0.8)
    # Torn, not thinned: holes have crisp edges and the rest stays solid, so a burning-out puff never turns into a
    # see-through haze (which reads pale against a daytime sky).
    holes = np.clip((turb - (0.08 + 0.5 * f)) / 0.08, 0, 1)
    a = np.clip(body * 1.6, 0, 1) * holes
    img = white(a)
    core = soft(r / (0.5 - 0.3 * f + 0.15), 1.2)
    img[..., :3] = np.clip(0.55 + 0.45 * core + 0.25 * (turb - 0.5), 0, 1)[..., None]
    return img

def blast_smoke(seed):
    """Billowing black smoke: a cluster of round puffs, lit from above so it reads as volume."""
    rng = np.random.default_rng(seed)
    x, y = grid(32)
    d = np.zeros_like(x)
    for _ in range(6):
        cx, cy, rr = rng.uniform(-0.35, 0.35), rng.uniform(-0.3, 0.35), rng.uniform(0.32, 0.52)
        d = np.maximum(d, soft(np.hypot(x - cx, y - cy) / rr, 0.6))
    n = value_noise(32, 6, seed) * 0.5 + value_noise(32, 12, seed + 1) * 0.5
    img = white(np.clip(d * (0.8 + 0.6 * (n - 0.5)) * 1.25, 0, 1))
    light = np.clip(0.5 - y * 0.55, 0, 1)  # top lit, belly in shadow
    img[..., :3] = np.clip(0.45 + 0.55 * light + 0.2 * (n - 0.5), 0, 1)[..., None]
    return img

def flame(variant):
    """Flame tongue along X for stretched particles: soft teardrop with flickering density."""
    x, y = grid(32, 16)
    n = value_noise(32, 6, 400 + variant)[:16, :]
    taper = 1.4 + 1.2 * (1 - (x + 1) / 2)  # thicker at the head (+X)
    a = soft(np.abs(y) * taper, 1.1) * soft(np.abs(x), 0.7) * (0.6 + 0.7 * n)
    img = white(np.clip(a * 1.3, 0, 1))
    img[..., :3] = np.clip(0.6 + 0.4 * soft(np.abs(y) * 3, 1), 0, 1)[..., None]
    return img

def flame_jet(frame):
    """6-frame flame puff for the Flamethrower stream: a ragged blob with licking tongues that tears apart as it burns
    out. Greyscale with a bright core; the particle ramps it white-hot -> orange -> red -> soot."""
    x, y = grid(32)
    f = frame / 5
    turb = 0.6 * value_noise(32, 5, 700 + frame) + 0.4 * value_noise(32, 10, 710 + frame)
    lick = 0.5 * (value_noise(32, 6, 720 + frame) - 0.5)  # tongues: the outline wavers
    r = np.hypot(x * 1.05, (y + lick * (1 - y)) * 0.9)
    edge = 0.62 + 0.18 * f + 0.45 * (turb - 0.5)
    body = soft(r / np.maximum(edge, 0.2), 0.8)
    holes = np.clip((turb - (0.05 + 0.55 * f)) / 0.1, 0, 1)
    inside = np.clip((1 - np.hypot(x, y)) / 0.2, 0, 1)  # never reaches the sprite's edge
    img = white(np.clip(body * 1.7, 0, 1) * holes * inside)
    core = soft(r / (0.6 - 0.25 * f), 1.1)
    img[..., :3] = np.clip(0.6 + 0.4 * core + 0.25 * (turb - 0.5), 0, 1)[..., None]
    return img

def dust_ring():
    """Ground dust sweeping out from a blast: ragged ring over a faint fill."""
    x, y = grid(64)
    r = np.hypot(x, y)
    n = value_noise(64, 10, 300)
    jag = 0.78 + 0.18 * (n - 0.5)
    a = (np.exp(-((r - jag) / 0.13) ** 2) + soft(r / jag, 1.6) * 0.3) * (0.55 + 0.45 * value_noise(64, 20, 301))
    return white(np.clip(a, 0, 1))

def scorch():
    """Blast scorch decal: dark burnt centre, ragged rim, radial streaks thrown out by the blast."""
    x, y = grid(64)
    r, th = np.hypot(x, y), np.arctan2(y, x)
    n = value_noise(64, 8, 310)
    rim = 0.62 + 0.12 * np.sin(th * 5 + 0.7) + 0.08 * np.sin(th * 11) + 0.15 * (n - 0.5)
    body = soft(r / rim, 0.9)
    streaks = np.abs(np.sin(th * 9 + 2.0 * value_noise(64, 3, 311))) ** 12 * soft(r / 0.98, 1.0) * (r > rim * 0.7)
    return white(np.clip(body * (0.75 + 0.35 * n) + streaks * 0.7, 0, 1))

# --- renderer textures (textures/vfx) -----------------------------------------------------------

def tracer():
    """Stretched tracer: white-hot core line, coloured glow falloff across, fading tail along X (head at x=+1)."""
    x, y = grid(64, 16)
    along = np.clip((x + 1) / 2, 0, 1) ** 1.6
    core = np.exp(-(y / 0.12) ** 2)
    glow = np.exp(-(y / 0.55) ** 2) * 0.6
    img = white((core + glow) * along)
    img[..., 0] = img[..., 1] = img[..., 2] = np.clip(0.55 + core, 0, 1)  # core whiter than the glow (tint multiplies)
    return img

def arc():
    """Lightning strip (ArcRenderer): even along u, across v a hard white core in a soft glow."""
    x, y = grid(16, 32)
    core = np.exp(-(y / 0.14) ** 2)
    img = white(core + np.exp(-(y / 0.6) ** 2) * 0.55)
    img[..., :3] = np.clip(0.5 + core, 0, 1)[..., None]
    return img

def wrapped_noise(w, h, cells_u, cells_v, seed):
    """Smooth value noise that repeats along u (strips the renderers scroll)."""
    lat = np.random.default_rng(seed).random((cells_v + 1, cells_u + 1)); lat[:, -1] = lat[:, 0]
    tu = (np.arange(w) + 0.5) / w * cells_u; tv = (np.arange(h) + 0.5) / h * cells_v
    iu = np.minimum(tu.astype(int), cells_u - 1); iv = np.minimum(tv.astype(int), cells_v - 1)
    fu = tu - iu; fv = tv - iv; fu = fu * fu * (3 - 2 * fu); fv = fv * fv * (3 - 2 * fv)
    a = lat[np.ix_(iv, iu)]; b = lat[np.ix_(iv, iu + 1)]; c = lat[np.ix_(iv + 1, iu)]; d = lat[np.ix_(iv + 1, iu + 1)]
    return (a * (1 - fu) + b * fu) * (1 - fv[:, None]) + (c * (1 - fu) + d * fu) * fv[:, None]

def beam_strip():
    """Laser beam energy (BeamRenderer), tileable along u: bright streaks flowing along a soft shaft."""
    w, h = 64, 32
    n = 0.6 * wrapped_noise(w, h, 6, 3, 820) + 0.4 * wrapped_noise(w, h, 12, 5, 821)
    x, y = grid(w, h)
    img = white(np.clip(np.exp(-(y / 0.4) ** 2) * (0.45 + 0.9 * (n - 0.3)), 0, 1))
    img[..., :3] = np.clip(0.6 + 0.4 * np.exp(-(y / 0.15) ** 2), 0, 1)[..., None]
    return img

def orb():
    """Gathering light at the Laser Rifle's emitter: a hot centre in a wide soft glow."""
    x, y = grid(32)
    r = np.hypot(x, y)
    img = white(np.clip(soft(r, 1.8) * 0.8 + np.exp(-(r / 0.22) ** 2), 0, 1))
    img[..., :3] = np.clip(0.55 + 0.45 * np.exp(-(r / 0.3) ** 2), 0, 1)[..., None]
    return img

def flare():
    """Lens flare streak: a thin horizontal line of light, brightest in the middle."""
    x, y = grid(64, 16)
    return white(np.clip(np.exp(-(y / 0.18) ** 2) * soft(np.abs(x), 1.4) * 1.3, 0, 1))

def flame_strip():
    """Flamethrower jet strip (FlameJetRenderer), tileable along u, which the renderer scrolls: streaky turbulent fire,
    dense along the middle and ragged at the edges across v."""
    w, h = 64, 32
    n = 0.55 * wrapped_noise(w, h, 4, 6, 810) + 0.3 * wrapped_noise(w, h, 8, 12, 811) + 0.15 * wrapped_noise(w, h, 16, 16, 812)
    x, y = grid(w, h)
    edge = 0.62 + 0.5 * (n - 0.5)  # ragged edge across the strip
    a = soft(np.abs(y) / np.maximum(edge, 0.15), 0.9) * (0.65 + 0.7 * (n - 0.5))
    img = white(np.clip(a * 1.5, 0, 1))
    img[..., :3] = np.clip(0.7 + 0.3 * soft(np.abs(y) / 0.5, 1) + 0.2 * (n - 0.5), 0, 1)[..., None]
    return img

def reticle():
    """Hologram lock-on reticle: broken outer ring, inner ring, 4 ticks, centre dot."""
    x, y = grid(64)
    r, th = np.hypot(x, y), np.arctan2(y, x)
    outer = np.exp(-((r - 0.86) / 0.035) ** 2) * (np.cos(th * 4) > -0.35)
    inner = np.exp(-((r - 0.5) / 0.03) ** 2) * 0.7
    ticks = ((np.abs(x) < 0.035) | (np.abs(y) < 0.035)) & (r > 0.58) & (r < 0.78)
    dot = soft(r / 0.07, 2)
    return white(outer + inner + ticks * 0.9 + dot)

def scanlines():
    """Tiling hologram scanline mask (multiplied over hologram quads)."""
    y = np.arange(16)[:, None] * np.ones((1, 16))
    a = 0.55 + 0.45 * (y % 4 < 2)
    return white(a)

def decal():
    """Impact scorch: hot centre and jagged rim; renderer cools it from energy colour to dark over 3-5 s."""
    x, y = grid(16)
    r, th = np.hypot(x, y), np.arctan2(y, x)
    jag = 0.75 + 0.2 * np.sin(th * 7 + 1.3) + 0.08 * np.sin(th * 13)
    return white(soft(r / jag, 1.3))

def casing():
    """Casing texture: metal shell with a glowing energy band, mapped on a tiny box by CasingRenderer."""
    img = np.zeros((8, 8, 4)); img[..., 3] = 1
    img[..., :3] = 0.62
    img[0, :, :3] = 0.85; img[-1, :, :3] = 0.35
    img[3:5, :, :3] = 1.0  # band, emissive-tinted
    return img

VFX_SPRITES = {
    "particle/spark.png": spark, "particle/ember.png": ember,
    **{f"particle/smoke_puff_{i}.png": (lambda i=i: smoke_puff(10 + i)) for i in range(4)},
    **{f"particle/heat_haze_{i}.png": (lambda i=i: heat_haze(20 + i)) for i in range(2)},
    "particle/shockwave_ring.png": shockwave_ring,
    **{f"particle/muzzle_flash_{i}.png": (lambda i=i: muzzle_flash(i)) for i in range(4)},
    **{f"particle/debris_{i}.png": (lambda i=i: debris(30 + i)) for i in range(4)},
    "particle/muzzle_ring.png": muzzle_ring,
    **{f"particle/impact_splash_{i}.png": (lambda i=i: impact_splash(i)) for i in range(3)},
    **{f"particle/smoke_wisp_{i}.png": (lambda i=i: smoke_wisp(i)) for i in range(3)},
    "vfx/tracer.png": tracer, "vfx/reticle.png": reticle, "vfx/scanlines.png": scanlines,
    "vfx/decal.png": decal, "vfx/casing.png": casing, "vfx/scorch.png": scorch,
    **{f"particle/fireball_{i}.png": (lambda i=i: fireball(i)) for i in range(6)},
    **{f"particle/blast_smoke_{i}.png": (lambda i=i: blast_smoke(500 + i)) for i in range(4)},
    **{f"particle/flame_{i}.png": (lambda i=i: flame(i)) for i in range(3)},
    "particle/dust_ring.png": dust_ring,
    **{f"particle/flame_jet_{i}.png": (lambda i=i: flame_jet(i)) for i in range(6)},
    "vfx/arc.png": arc, "vfx/flame_jet.png": flame_strip, "vfx/beam.png": beam_strip, "vfx/orb.png": orb, "vfx/flare.png": flare,
}


TEXTURES = {
    "block/turret_base.png": lambda: paint_model("turret_base")[0],
    "block/turret_base_e.png": lambda: paint_model("turret_base")[1],
    "item/gun_turret.png": lambda: paint_model("gun_turret")[0],
    "item/gun_turret_e.png": lambda: paint_model("gun_turret")[1],
    "item/machine_gun_turret.png": lambda: paint_model("machine_gun_turret")[0],
    "item/machine_gun_turret_e.png": lambda: paint_model("machine_gun_turret")[1],
    "item/shotgun_turret.png": lambda: paint_model("shotgun_turret")[0],
    "item/shotgun_turret_e.png": lambda: paint_model("shotgun_turret")[1],
    "item/kinetic_rounds.png": kinetic_rounds,
    "item/scatter_shells.png": scatter_shells,
    "item/creative_ammo.png": creative_ammo,
    "item/sniper_rounds.png": sniper_rounds,
    "item/rockets.png": rockets,
    "item/sniper_turret.png": lambda: paint_model("sniper_turret")[0],
    "item/sniper_turret_e.png": lambda: paint_model("sniper_turret")[1],
    "item/rocket_launcher_turret.png": lambda: paint_model("rocket_launcher_turret")[0],
    "item/rocket_launcher_turret_e.png": lambda: paint_model("rocket_launcher_turret")[1],
    "entity/turret_rocket.png": lambda: paint_model("entity/turret_rocket")[0],
    "entity/turret_missile.png": lambda: paint_model("entity/turret_missile")[0],
    "entity/turret_missile_e.png": lambda: paint_model("entity/turret_missile")[1],
    "block/large_turret_base.png": lambda: paint_model("large_turret_base")[0],
    "block/large_turret_base_e.png": lambda: paint_model("large_turret_base")[1],
    "item/missile_launcher_turret.png": lambda: paint_model("missile_launcher_turret")[0],
    "item/missile_launcher_turret_e.png": lambda: paint_model("missile_launcher_turret")[1],
    "item/missiles.png": missiles,
    "item/flamethrower_turret.png": lambda: paint_model("flamethrower_turret")[0],
    "item/flamethrower_turret_e.png": lambda: paint_model("flamethrower_turret")[1],
    "item/tesla_turret.png": lambda: paint_model("tesla_turret")[0],
    "item/tesla_turret_e.png": lambda: paint_model("tesla_turret")[1],
    "item/fuel_canister.png": fuel_canister,
    "item/laser_cell.png": laser_cell,
    "item/empty_laser_cell.png": empty_laser_cell,
    "block/creative_power_source.png": creative_power_source,
    "gui/workstation.png": workstation_gui,
    **{f"block/{n}.png": (lambda n=n: paint_model(n)[0]) for n in ("part_workstation", "part_assembler", "module_workstation", "ammo_workstation", "charging_station")},
    **{f"block/{n}_e.png": (lambda n=n: paint_model(n)[1]) for n in ("part_workstation", "part_assembler", "module_workstation", "ammo_workstation", "charging_station")},
    "item/laser_rifle_turret.png": lambda: paint_model("laser_rifle_turret")[0],
    "item/laser_rifle_turret_e.png": lambda: paint_model("laser_rifle_turret")[1],
    "item/damaged_large_turret_base.png": damaged_large_base,
    "entity/turret_rocket_e.png": lambda: paint_model("entity/turret_rocket")[1],
    **{f"item/{name}.png": (lambda name=name: modifier_chip(name)) for name in MODIFIER_GLYPHS},
    "item/repair_kit.png": repair_kit,
    "item/tier_upgrade_kit_t2.png": lambda: upgrade_kit(2),
    "item/tier_upgrade_kit_t3.png": lambda: upgrade_kit(3),
    "item/turret_configurator.png": configurator,
    "item/damaged_turret_base.png": damaged_base,
    "gui/turret.png": turret_gui,
    "gui/turret_plain.png": lambda: turret_gui(plain=True),
    **VFX_SPRITES,
}

if __name__ == "__main__":
    for rel, make in TEXTURES.items():
        path = OUT / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        pixels = (np.clip(make(), 0, 1) * 255).round().astype(np.uint8)
        Image.fromarray(pixels, "RGBA").save(path)
        print("wrote", path.relative_to(OUT.parents[5]))
