"""Project icon from an in-game render (no generated art): keys the lime backdrop out of a `-Pshowcase=icon`
screenshot and sets the turret on a dark navy background with a soft cyan glow.

    python3 tools/make_icon.py [angle]   # angle of run/screenshots/showcase_icon_raw_<angle>.png, default 335
    -> docs/release/icon.png (512 x 512)
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
SIZE = 512
GLOW = (0x4F, 0xD8, 0xFF)  # EmissiveLayer.MODEL_GLOW


def key(raw):
    """Alpha from how many times greener than red and blue a pixel is, so lime concrete keys out lit or in shade
    while gunmetal (grey) and the cyan glow (bluer than green) stay; the green spill is taken off the edges."""
    px = np.asarray(raw.convert("RGB")).astype(float)
    r, g, b = px[..., 0], px[..., 1], px[..., 2]
    ratio = g / (np.maximum(r, b) + 1)
    alpha = np.clip((1.35 - ratio) / 0.25, 0, 1)
    px[..., 1] = np.minimum(g, np.maximum(r, b) * 1.05)  # despill
    return np.dstack([px, alpha * 255]).astype(np.uint8)


def background():
    y, x = np.mgrid[0:SIZE, 0:SIZE] / (SIZE - 1) * 2 - 1
    r = np.hypot(x, y * 1.05 + 0.05)
    t = np.clip(r / 1.35, 0, 1) ** 1.2
    inner, outer = np.array([24, 40, 62]), np.array([6, 9, 15])
    img = inner * (1 - t[..., None]) + outer * t[..., None]
    return Image.fromarray(img.astype(np.uint8), "RGB").convert("RGBA")


def glow(alpha, radius, strength):
    """A cyan halo the shape of the turret, blurred wide."""
    halo = Image.new("RGBA", (SIZE, SIZE), GLOW + (0,))
    halo.putalpha(alpha.filter(ImageFilter.GaussianBlur(radius)).point(lambda a: int(a * strength)))
    return halo


def main():
    angle = sys.argv[1] if len(sys.argv) > 1 else "335"
    raw = Image.open(ROOT / f"run/screenshots/showcase_icon_raw_{angle}.png")
    turret = Image.fromarray(key(raw), "RGBA")
    turret = turret.crop(turret.getchannel("A").point(lambda a: 255 if a > 40 else 0).getbbox())
    scale = 430 / max(turret.size)
    turret = turret.resize((round(turret.width * scale), round(turret.height * scale)), Image.LANCZOS)

    icon = background()
    at = ((SIZE - turret.width) // 2, (SIZE - turret.height) // 2 + 8)
    placed = Image.new("RGBA", (SIZE, SIZE))
    placed.paste(turret, at)
    shadow = Image.new("L", (SIZE, SIZE))  # soft contact shadow under the base
    sx, sy = SIZE // 2, at[1] + turret.height - 6
    shadow.paste(255, (sx - turret.width // 3, sy - 10, sx + turret.width // 3, sy + 10))
    dark = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    dark.putalpha(shadow.filter(ImageFilter.GaussianBlur(14)).point(lambda a: int(a * 0.6)))
    icon.alpha_composite(dark)
    icon.alpha_composite(glow(placed.getchannel("A"), 34, 0.55))
    icon.alpha_composite(glow(placed.getchannel("A"), 8, 0.35))
    icon.alpha_composite(placed)
    out = ROOT / "docs/release/icon.png"
    icon.convert("RGB").save(out)
    print("wrote", out.relative_to(ROOT), icon.size)


if __name__ == "__main__":
    main()
