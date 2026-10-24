"""Assemble the -Pshowcase=trailer frames (build/video-frames-trailer/f_<ms>.png) and title cards into build/trailer/bastion-0.5.0-trailer.mp4: 720p30, no sound, 2-pass x264 sized under 10 MB for Discord (user 2026-10-24). Reads the part marks from build/trailer.log."""
import os, re, subprocess
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FRAMES = f"{ROOT}/build/video-frames-trailer"
OUT = f"{ROOT}/build/trailer"
W, H = 1280, 720
HEAD = ImageFont.truetype("/System/Library/Fonts/Supplemental/DIN Condensed Bold.ttf", 64)
SUB = ImageFont.truetype("/System/Library/Fonts/Avenir Next.ttc", 26, index=0)
CYAN = (62, 216, 255, 255)

marks = {m.group(2): int(m.group(1)) / 1000 for m in re.finditer(r"\[trailer\] at (\d+) (\w+)", open(f"{ROOT}/build/trailer.log").read())}
end = marks["end"]

def card(name, head, sub):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    hw = max(d.textlength(head, font=HEAD), d.textlength(sub, font=SUB)) + 90
    for x in range(int(hw)):  # dark band fading out to the right
        a = int(170 * min(1, (hw - x) / 160))
        d.line([(x, H - 168), (x, H - 52)], fill=(8, 12, 18, a))
    d.rectangle([40, H - 150, 46, H - 70], fill=CYAN)
    d.text((62, H - 156), head, font=HEAD, fill=(255, 255, 255, 255))
    d.text((64, H - 96), sub, font=SUB, fill=(205, 232, 240, 255))
    path = f"{OUT}/{name}.png"
    im.save(path)
    return path

titles = [  # (start s, length s, card)
    (0.4, 4.4, card("t_intro", "BASTION TURRETS 0.5.0", "Three new turrets for the Fortress Turret Base")),
    (marks["javelin"] + 0.3, 3.6, card("t_javelin", "JAVELIN MISSILE", "Out of the silo, gone into the sky... then straight down")),
    (marks["brood"] + 0.3, 3.6, card("t_brood", "BROOD CANNON", "Capsules over the wall, robot spiders inside")),
    (marks["hive"] + 0.3, 3.6, card("t_hive", "HIVE CARRIER", "A hive of combat drones: out the door, and home again")),
    (marks["finale"] + 0.6, end - marks["finale"] - 0.9, card("t_end", "BASTION TURRETS", "Minecraft 1.20.1 Forge  |  Fortress Turret Base, Javelin, Brood Cannon, Hive Carrier")),
]

files = sorted(f for f in os.listdir(FRAMES) if f.endswith(".png"))
times = [int(f[2:8]) / 1000 for f in files]
with open(f"{OUT}/frames.txt", "w") as lst:
    for i, f in enumerate(files):
        dur = (times[i + 1] - times[i]) if i + 1 < len(files) else 1 / 30
        lst.write(f"file '{FRAMES}/{f}'\nduration {dur:.4f}\n")
    lst.write(f"file '{FRAMES}/{files[-1]}'\n")
total = times[-1] + 1 / 30

inputs = ["-f", "concat", "-safe", "0", "-i", f"{OUT}/frames.txt"]
chain, last = [f"[0:v]fps=30,format=yuv420p,fade=t=in:st=0:d=0.6,fade=t=out:st={total - 0.9:.2f}:d=0.9[v0]"], "v0"
for k, (start, length, png) in enumerate(titles, 1):
    inputs += ["-loop", "1", "-t", f"{length:.2f}", "-i", png]
    chain.append(f"[{k}:v]format=rgba,fade=t=in:st=0:d=0.45:alpha=1,fade=t=out:st={length - 0.45:.2f}:d=0.45:alpha=1,setpts=PTS-STARTPTS+{start:.2f}/TB[t{k}]")
    chain.append(f"[{last}][t{k}]overlay=0:0:enable='between(t,{start:.2f},{start + length:.2f})':eof_action=pass[v{k}]")
    last = f"v{k}"
budget_bits = 9.3 * 8 * 1024 * 1024
rate = int(budget_bits / total / 1000)  # kbit/s, video only
common = ["-filter_complex", ";".join(chain), "-map", f"[{last}]", "-an", "-c:v", "libx264", "-preset", "slow", "-b:v", f"{rate}k",
          "-maxrate", f"{int(rate * 1.6)}k", "-bufsize", f"{rate * 2}k", "-pix_fmt", "yuv420p", "-r", "30"]
log = f"{OUT}/x264"
subprocess.run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", *inputs, *common, "-pass", "1", "-passlogfile", log, "-f", "mp4", "/dev/null"], check=True)
subprocess.run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", *inputs, *common, "-pass", "2", "-passlogfile", log, "-movflags", "+faststart",
                f"{OUT}/bastion-0.5.0-trailer.mp4"], check=True)
print(f"total {total:.1f}s, {rate} kbit/s, marks {marks}")
