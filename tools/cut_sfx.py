#!/usr/bin/env python3
"""Real SFX for Bastion Turrets cut from the user's source files in sfx/ (docs/SOUND_SHEET.md): each source is decoded
to mono 48 kHz, cut, faded, looped where the event loops, levelled by loudness (RMS dBFS of the whole clip: the
placeholder it replaces +2 dB, peaks capped at -0.5) and written as mono OGG Vorbis into
src/main/resources/assets/bastion/sounds/. Real takes are ~10 dB denser than the synthetic placeholders at the same
peak: levelled by peak they came out twice as loud as the turrets still on placeholders.
Variants are the same take a few percent higher or lower (the game adds its own +-8% on top). Re-run after replacing a
source file; tools/gen_sounds.py only writes missing files, so it never overwrites these.

    pip install numpy soundfile pillow    # and ffmpeg on PATH (decoding, spectrograms)
    python3 tools/cut_sfx.py                                  # cut every source into the mod's sounds
    python3 tools/cut_sfx.py --sheet "minigun.mp3" 0.55 3.4   # waveform + spectrogram with cut marks, to check by eye
"""
import subprocess
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "sfx"
OUT = ROOT / "src/main/resources/assets/bastion/sounds"
RATE = 48000


def load(name):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(SRC / name), "-ac", "1", "-ar", str(RATE), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(np.float64)


def cut(x, t0, t1=None, fade_in=0.003, fade_out=0.03):
    """x between t0 and t1 seconds (to the end without t1), with short linear fades so no cut clicks."""
    y = x[int(t0 * RATE):None if t1 is None else int(t1 * RATE)].copy()
    a, b = max(1, int(fade_in * RATE)), max(1, int(fade_out * RATE))
    y[:a] *= np.linspace(0, 1, a)
    y[-b:] *= np.linspace(1, 0, b)
    return y


def join(*parts, gap=0.0):
    silence = np.zeros(int(gap * RATE))
    out = [parts[0]]
    for p in parts[1:]:
        out += [silence, p]
    return np.concatenate(out)


def loop(x, crossfade):
    """A seamless loop: the end crossfades (equal power) into the start, so it is len(x) - crossfade long."""
    n = int(crossfade * RATE)
    out = x[:len(x) - n].copy()
    k = np.linspace(0, np.pi / 2, n)
    out[:n] = x[:n] * np.sin(k) + x[len(x) - n:] * np.cos(k)
    return out


def chain(*parts, crossfade):
    """parts one after another, each crossfading (equal power) into the next: one longer stretch of a steady sound."""
    n = int(crossfade * RATE)
    k = np.linspace(0, np.pi / 2, n)
    out = parts[0]
    for p in parts[1:]:
        out = np.concatenate([out[:-n], out[-n:] * np.cos(k) + p[:n] * np.sin(k), p[n:]])
    return out


def texture(x, spans, seconds, grain=0.25, seed=7):
    """A long stretch of a steady sound (an engine roar) that never repeats: random grain-long pieces of the (t0, t1)
    spans, overlapped by half under a sine window (constant power for unrelated pieces, so no pulsing at the grain
    rate). A short loop of the same roar repeats audibly on a long flight (user 10-14)."""
    rng = np.random.default_rng(seed)
    g = int(grain * RATE)
    window = np.sin(np.pi * (np.arange(g) + 0.5) / g)
    pieces = [x[int(a * RATE):int(b * RATE)] for a, b in spans]
    out = np.zeros(int(seconds * RATE) + g)
    for k in range(0, len(out) - g, g // 2):
        piece = pieces[rng.integers(len(pieces))]
        start = rng.integers(0, len(piece) - g)
        out[k:k + g] += piece[start:start + g] * window
    return out[g // 2:g // 2 + int(seconds * RATE)]


def muffle(x, hz):
    """x rolled off above hz (12 dB per octave, no phase shift): the same sound heard from far away."""
    f = np.fft.rfftfreq(len(x), 1 / RATE)
    return np.fft.irfft(np.fft.rfft(x) / (1 + (f / hz) ** 2), len(x))


def pitch(x, factor):
    """Speed and pitch up by factor (a variant: a gunshot a few percent higher or lower still sounds like the same gun)."""
    n = int(len(x) / factor)
    return np.interp(np.arange(n) * factor, np.arange(len(x)), x)


def level(x, rms_db, peak_cap=-0.5):
    """x at rms_db loudness, turned down further if its peak would pass peak_cap dBFS."""
    y = x * (10 ** (rms_db / 20) / max(np.sqrt(np.mean(x ** 2)), 1e-12))
    peak = np.max(np.abs(y))
    return y if peak <= 10 ** (peak_cap / 20) else y * (10 ** (peak_cap / 20) / peak)


def scaled(x, t0, t1, rms_db):
    """x turned up or down so its stretch t0-t1 s is at rms_db: clips cut from one take afterwards keep their loudness
    relative to each other (a spin-down that starts as loud as the loop it follows; levelled on its own, its quiet tail
    would have pulled its start 10 dB above the loop)."""
    ref = x[int(t0 * RATE):int(t1 * RATE)]
    return x * (10 ** (rms_db / 20) / np.sqrt(np.mean(ref ** 2)))


def write(rel, x):
    path = OUT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    sf.write(path, x.astype(np.float32), RATE, format="OGG", subtype="VORBIS")  # as gen_sounds.py (ffmpeg may lack libvorbis)
    rms = 20 * np.log10(max(np.sqrt(np.mean(x ** 2)), 1e-9))
    print(f"wrote {rel:32s} {len(x) / RATE:5.2f} s  peak {20 * np.log10(np.max(np.abs(x))):5.1f}  rms {rms:6.1f} dBFS")


def variants(rel_fmt, x, rms_db, factors):
    for i, f in enumerate(factors):
        write(rel_fmt.format(i), level(pitch(x, f) if f != 1 else x, rms_db))


def sheet(name, marks=(), width=2000):
    """sfx/<name> as a waveform over a spectrogram (time left to right, a tick every 0.5 s, low pitches at the bottom),
    with red lines at the seconds in marks, into build/sfx-sheets/<name>.png: the cuts are checked by looking at it,
    since nobody here can listen (spin-up vs firing, a cut inside a shot, a loop seam)."""
    out = ROOT / "build/sfx-sheets" / (Path(name).stem + ".png")
    out.parent.mkdir(parents=True, exist_ok=True)
    wave, spec = out.with_suffix(".wave.png"), out.with_suffix(".spec.png")
    run = lambda graph, path: subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(SRC / name), "-lavfi", graph, str(path)], check=True)
    run(f"aformat=channel_layouts=mono,showwavespic=s={width}x160:colors=white", wave)
    run(f"aformat=channel_layouts=mono,showspectrumpic=s={width}x480:legend=0:scale=log:fscale=log:color=intensity", spec)
    top, bottom = Image.open(wave).convert("RGB"), Image.open(spec).convert("RGB").resize((width, 480))
    img = Image.new("RGB", (width, 160 + 480), (12, 14, 18))
    img.paste(top, (0, 0))
    img.paste(bottom, (0, 160))
    duration = len(load(name)) / RATE
    draw = ImageDraw.Draw(img)
    for t in np.arange(0, duration, 0.5):
        x = int(t / duration * width)
        draw.line([(x, 0), (x, img.height)], fill=(70, 80, 90))
        draw.text((x + 3, 3), f"{t:.1f}", fill=(220, 230, 240))
    for t in marks:
        x = int(t / duration * width)
        draw.line([(x, 0), (x, img.height)], fill=(255, 60, 50), width=3)
        draw.text((x + 4, 145), f"{t:.3f}", fill=(255, 90, 80))
    img.save(out)
    wave.unlink()
    spec.unlink()
    print("wrote", out.relative_to(ROOT))


if __name__ == "__main__" and len(sys.argv) > 2 and sys.argv[1] == "--sheet":
    sheet(sys.argv[2], [float(m) for m in sys.argv[3:]])
    sys.exit()

if __name__ == "__main__":
    # Loudness targets: the placeholder's RMS (committed placeholders, measured 2026-10-10) + 2 dB.
    # Turret base
    write("turret/deploy.ogg", level(cut(load("turret_deploy.wav"), 0, 0.45, fade_out=0.08), -16.1))
    write("turret/idle_hum.ogg", level(loop(load("turret_idle_hum.wav"), 0.25), -14.9, peak_cap=-3.0))  # a loop: as loud as before
    write("turret/servo_loop.ogg", level(loop(load("turret_servo.wav"), 0.2), -16.0, peak_cap=-3.0))  # registered, not played yet
    write("turret/repair.ogg", level(cut(load("turret_repair.wav"), 0, fade_out=0.08), -18.3))
    # The explosion itself, from its first frame (the take's lead-in would lag the destruction effect).
    write("turret/destroyed.ogg", level(cut(load("turret_destroyed_small_turret.wav"), 1.54, fade_out=0.15), -17.5))
    write("turret/destroyed_large.ogg", level(cut(load("turret_destroyed_large_turret.wav"), 0.645, fade_out=0.15), -16.0))

    # Gun Turret (its distant tail also serves the Machine Gun)
    variants("gun/fire_{}.ogg", cut(load("gun_fire.wav"), 0, fade_out=0.06), -17.9, (1, 0.97, 1.03))
    variants("gun/fire_tail_{}.ogg", cut(load("rifle_gun_tail.wav"), 0, fade_out=0.1), -18.5, (1, 0.95))
    write("gun/lock.ogg", level(cut(load("gun_lock.wav"), 0, fade_out=0.05), -15.5))

    # Machine Gun (user 10-11): the barrels' whir from one take (spin-up 0-1.3 s, steady 1.3-7.2 s, spin-down 7.2-8.6 s),
    # its steady part ~10 dB under the barrage. The barrage from a separate take of one burst: a solid roar 0.715-0.865 s,
    # then its decay to ~1.05 s. Each turret shot (50-100 ms apart at full spin) plays the roar from a point inside it
    # through its decay: the next shot's roar arrives before the last one's ends, so they chain into one barrage, and the
    # last shot's decay is the burst's own. The far version: two or three of the "trail" take's distant shots per turret shot.
    spin = scaled(load("minigun spin up - spinning - spin down.mp3"), 2.0, 4.2, -24.0)
    write("machine_gun/spin_up.ogg", cut(spin, 0.0, 1.3, fade_out=0.15))
    write("machine_gun/spin_loop.ogg", loop(spin[int(2.0 * RATE):int(4.2 * RATE)], 0.2))
    write("machine_gun/spin_down.ogg", cut(spin, 7.15, 8.6, fade_in=0.03, fade_out=0.3))
    fire = scaled(load("machine_gun_fire.wav"), 0.715, 0.865, -15.0)
    for i, t0 in enumerate((0.715, 0.735, 0.755)):
        write(f"machine_gun/fire_{i}.ogg", cut(fire, t0, 1.05, fade_in=0.002, fade_out=0.06))
    trail = load("machine_gun_fire_trail.wav")
    for i, (t0, t1) in enumerate(((0.365, 0.442), (0.442, 0.528))):
        write(f"machine_gun/fire_tail_{i}.ogg", level(cut(trail, t0, t1, fade_in=0.002, fade_out=0.015), -20.0))

    # Shotgun: the bang from its first frame; the pump's two strokes closer together, without the take's opening rattle;
    # each shell variant one bounce (the take bounced four times).
    variants("shotgun/fire_{}.ogg", cut(load("shotgun_fire.wav"), 0.315, fade_out=0.1), -18.2, (1, 0.97, 1.03))
    variants("shotgun/fire_tail_{}.ogg", cut(load("shotgun_fire_tail.wav"), 0.325, fade_out=0.1), -18.5, (1, 0.95))
    pump = load("shotgun_pump (edit to make the pumping sound closer and remove artifact in the beginning).wav")
    write("shotgun/pump.ogg", level(join(cut(pump, 0.29, 0.53), cut(pump, 0.835, 1.05, fade_out=0.04), gap=0.08), -23.1))
    shell = load("shotgun _shell (edit if the bounce only one).wav")
    write("shotgun/shell_0.ogg", level(cut(shell, 0.335, 0.56, fade_out=0.05), -25.2))
    write("shotgun/shell_1.ogg", level(cut(shell, 0.67, 0.80, fade_out=0.04), -25.2))

    # Sniper (user 10-16: redone from Omni's soundtrack for VideoShowcase video_sniper, three charge-and-fire cycles).
    # Shots at 1.80 / 4.79 / 7.775 s: a crack and boom whose ring-out dies by ~2.1 s; shots 2 and 3 start from silence,
    # one variant each. One gain for the take: shot 2's first 0.3 s as loud as the old take's (and the Laser's, the
    # Gun's). Far: shot 2 muffled. Omni scored no charge in that take (the laser sight is faint from behind the turret),
    # so the charge comes from a second take filmed from the side (sniper_charge_generated.wav): each charge is a hiss as
    # the sight appears and a buzz that grows brighter into the shot (3 kHz +16 dB); only the 3rd does that, the others
    # stay flat or fade. Its last 1.5 s before the shot (7.778 s), the game's charge_ticks 30. Same gain rule (that
    # take's shot 2, 4.815 s), +6 dB: Omni put it ~11 dB under the shot, and the game plays it at 0.8 (ClientTurret),
    # so in game it sits where the Laser's charge does.
    sniper = scaled(load("sniper_generated.wav"), 4.775, 5.075, -14.5)
    for i, t0 in enumerate((4.787, 7.772)):
        write(f"sniper/fire_{i}.ogg", cut(sniper, t0, t0 + 2.1, fade_in=0.002, fade_out=0.4))
    write("sniper/fire_tail.ogg", level(muffle(cut(sniper, 4.787, 6.887, fade_out=0.4), 900), -23.7))
    sniper_charge = scaled(load("sniper_charge_generated.wav"), 4.815, 5.115, -14.5)
    write("sniper/charge.ogg", cut(sniper_charge, 7.778 - 1.5, 7.778, fade_in=0.05, fade_out=0.003) * 10 ** (6 / 20))

    # Rocket Launcher (user 10-14): Gemini Omni's soundtrack for a silent clip of the turret firing (VideoShowcase,
    # build/omni): five shots, each a launch then the explosion 0.4 s later, whose rumble runs into the next launch. Only
    # the first launch starts from silence (the others carry the last rumble, 4-7 dB under them), so its second variant
    # is the same take a little lower. Explosions: shots 1 and 4 (the sharpest onsets), each up to the next launch.
    rocket = load("rocket_launcher_generated.wav")
    variants("rocket/launch_{}.ogg", cut(rocket, 0.555, 0.95, fade_in=0.002, fade_out=0.08), -17.0, (1, 0.94))
    for i, (t0, t1) in enumerate(((0.95, 2.37), (6.305, 7.69))):
        write(f"rocket/explode_{i}.ogg", level(cut(rocket, t0, t1, fade_in=0.002, fade_out=0.4), -16.0))

    # Missile Launcher (user 10-14): Omni's soundtrack for single-mode shots, one at a time (VideoShowcase video_missile):
    # three launches from silence (0.49 / 3.79 / 6.96 s), a steady motor roar for ~1 s that thins as the missile gets far,
    # explosions at 1.89 / 5.17 / 8.55 s that die out before the next launch. Launches fade into the flight loop, which
    # plays from the same moment. The flight loop is rocket.fly (rockets and missiles share it): 8 s rebuilt from
    # random quarter-second pieces of the steady flight of all three shots (a 1.3 s loop of two of them repeated
    # audibly on a long flight, user 10-14), looped; no flight lasts 8 s. The far launch (also shared): shot 1's
    # launch and flight, muffled.
    missile = load("missile_launcher_generated.wav")
    for i, t0 in enumerate((0.49, 3.79)):
        write(f"missile/launch_{i}.ogg", level(cut(missile, t0, t0 + 0.46, fade_in=0.002, fade_out=0.25), -18.5))
    for i, (t0, t1) in enumerate(((1.89, 3.75), (5.17, 6.95))):
        write(f"missile/explode_{i}.ogg", level(cut(missile, t0, t1, fade_in=0.002, fade_out=0.2), -17.0))
    flight = texture(missile, ((0.70, 1.35), (3.95, 4.70), (7.12, 7.95)), 8.25)
    write("rocket/fly.ogg", level(loop(flight, 0.25), -16.1, peak_cap=-3.0))
    write("rocket/launch_tail.ogg", level(muffle(cut(missile, 0.49, 1.85, fade_out=0.6), 900), -20.5))

    # Reloads (user 10-14, a plain SFX generator): both takes came out looped, the sound wrapping round the file's end.
    # Rocket: its start sits at 1.09 s, the rest at 0-0.40 s. Missile: the slide and clunk 0.50-0.72 s, without the
    # steady hiss that follows (user: "delete the artifact after the main sound effect").
    rr = load("rocket_reload.wav")
    write("rocket/reload.ogg", level(cut(np.concatenate([rr[int(1.09 * RATE):], rr[:int(0.40 * RATE)]]), 0, fade_in=0.005, fade_out=0.1), -24.0))
    mr = load("missile_reload (delete the artifact after the main sound effect).wav")
    write("missile/reload.ogg", level(cut(mr, 0.50, 0.76, fade_in=0.005, fade_out=0.06), -24.0))

    # Laser Rifle (user 10-15): Omni's soundtrack for VideoShowcase video_laser, two charge-and-fire cycles. The charge
    # is a rising electric hum; the first came in late (1.06 s), the second runs 5.355-7.945 s, 0.09 s longer than the
    # game's 2.5 s (charge_ticks 50): its last 2.5 s, so it ends on the shot. Shots at 3.033 / 7.945 s: a heavy pulsing
    # discharge whose ring-out dies by ~4.9 / ~10 s, one variant each. One gain for the take; the charge 4 dB up (Omni
    # put it 12 dB under the shot, and in game it plays on its own as the warning). Far: shot 1 muffled.
    laser = scaled(load("laser_rifle_generated.wav"), 3.05, 3.95, -14.5)
    write("laser/charge.ogg", cut(laser, 7.94 - 2.5, 7.94, fade_in=0.05, fade_out=0.02) * 10 ** (4 / 20))
    for i, (t0, t1) in enumerate(((3.025, 4.95), (7.937, 10.0))):
        write(f"laser/fire_{i}.ogg", cut(laser, t0, t1, fade_in=0.002, fade_out=0.4))
    write("laser/fire_tail.ogg", level(muffle(cut(laser, 3.025, 4.95, fade_out=0.4), 900), -20.5))

    # Flamethrower (user 10-15): Omni's soundtrack for VideoShowcase video_flame, two bursts (0.30-3.7 s, 6.10-8.75 s),
    # each a fast swell into a steady roar, a 0.3 s collapse when it shuts off, then the burning crackle. Burst 1 runs
    # 4 dB darker until 1.1 s, so the loop only takes the steady roar after that. Ignite: each burst's swell and first
    # roar, as loud as the roar (Omni gave no distinct "whoomph"; over the loop's start it makes a short flare, and it
    # also plays alone for the Mortar's fire patch). Loop: 6 s of random quarter-second pieces of both steady
    # roars (a short loop of turbulent noise repeats audibly, see rocket.fly). One gain for the take. Burning: a stretch
    # of the crackle after burst 1. Ignite and burning levelled on their own (in game they play over the loop or alone).
    flame = scaled(load("flamethrower_generated.wav"), 1.2, 3.6, -16.0)
    for i, t0 in enumerate((0.29, 6.09)):
        write(f"flamethrower/ignite_{i}.ogg", level(cut(flame, t0, t0 + 0.6, fade_in=0.002, fade_out=0.3), -18.0))
    roar = texture(flame, ((1.2, 3.6), (6.4, 8.65)), 6.25)
    write("flamethrower/loop.ogg", level(loop(roar, 0.25), -16.0, peak_cap=-3.0))
    write("flamethrower/burning.ogg", level(cut(flame, 4.2, 5.1, fade_in=0.05, fade_out=0.25), -29.5))

    # Tesla Coil (user 10-15): Omni's soundtrack for VideoShowcase video_tesla, four discharges on their own. Each charge
    # is a crackling 120 Hz buzz that starts late and short (0.495 / 2.785 / 5.075 / 7.58 s), each discharge a crack and
    # sizzle that dies within ~1.4 s (0.91 / 3.305 / 5.645 / 8.19 s). Charge: the 4th, the only one as long as the
    # game's (12 ticks), 3 dB up (Omni put it 9 dB under the strike). Zap: strikes 2 and 4 (the sharpest), to the end of
    # their sizzle. Crackle (idle sparks, Railgun charge, Workstation; it plays alone): the first 0.3 s of charges 2 and
    # 3, a spark and buzz from silence. Far: strike 2 muffled.
    tesla = scaled(load("tesla_coil_generated.wav"), 3.31, 3.61, -14.0)
    write("tesla/charge.ogg", cut(tesla, 7.58, 8.18, fade_in=0.01, fade_out=0.01) * 10 ** (3 / 20))
    for i, t0 in enumerate((3.302, 8.185)):
        write(f"tesla/zap_{i}.ogg", cut(tesla, t0, t0 + 1.3, fade_in=0.002, fade_out=0.4))
    for i, t0 in enumerate((2.783, 5.073)):
        write(f"tesla/crackle_{i}.ogg", level(cut(tesla, t0, t0 + 0.3, fade_in=0.002, fade_out=0.1), -24.0))
    write("tesla/zap_tail.ogg", level(muffle(cut(tesla, 3.302, 4.6, fade_out=0.4), 900), -24.0))

    # Railgun (user 10-16): two Omni takes, since a slug lands the tick it is fired. railgun_generated.wav
    # (VideoShowcase video_railgun): charges from 0.315 / 5.322 s, a crackling attack then a steady hum that brightens
    # ~10 dB into the shot; shots at 3.408 / 8.146 s, a crack and an electric sizzle that dies by ~1.85 s. Charge: the
    # first 3 s of charge 1 (charge_ticks 60; charge 2 came in short); Omni put it ~7 dB under the shot, left there.
    # The arcs' crackle is not in it (Omni scored none): in game RailgunEffects plays tesla.crackle over the charge once
    # the arcs appear. One gain: shot 1's first 0.3 s 1.5 dB over the Sniper and Laser (the heaviest gun). Far: shot 1
    # muffled. railgun_impact_generated.wav (video_railgun_impact, slugs landing on the floor): impacts at 0.515 /
    # 2.885 / 5.305 / 7.598 s, a crack and boom that dies by ~1.8 s; 3 and 4 start from silence. Its own gain: as loud
    # as a missile blast.
    rail = scaled(load("railgun_generated.wav"), 3.405, 3.705, -13.0)
    write("railgun/charge.ogg", cut(rail, 0.312, 3.312, fade_in=0.002, fade_out=0.003))
    for i, (t0, t1) in enumerate(((3.405, 5.25), (8.143, 10.0))):
        write(f"railgun/fire_{i}.ogg", cut(rail, t0, t1, fade_in=0.002, fade_out=0.4))
    write("railgun/fire_tail.ogg", level(muffle(cut(rail, 3.405, 5.25, fade_out=0.4), 900), -19.5))
    hit = scaled(load("railgun_impact_generated.wav"), 5.303, 5.603, -14.0)
    for i, t0 in enumerate((5.303, 7.597)):
        write(f"railgun/impact_{i}.ogg", cut(hit, t0, t0 + 1.8, fade_in=0.002, fade_out=0.4))

    # Plasma Cannon (user 10-16): Omni's soundtrack for VideoShowcase video_plasma_cannon (balls ~1 s to magma cubes 20
    # blocks out; husks there made Omni drop the audio). One charge only (0.33 s, rising energy noise, 1 s to the shot,
    # the game's charge_ticks 20); shots at 1.33 / 4.63 / 8.05 s, a thoom and sizzle gone in ~1 s; blasts at 2.37 /
    # 5.63 / 9.01 s, a boom, crackle and rush (the first dies out by ~4.55 s, the second meets a stray burst at 7.20 s).
    # Shots 1 and 2, blasts 1 and 2; shot 3 runs into blast 3. One gain: shot 1's first 0.3 s between the Railgun and
    # the Sniper. Charge and blasts as Omni balanced them.
    plasma_cannon = scaled(load("plasma_cannon_generated.wav"), 1.33, 1.63, -14.0)
    write("plasma/cannon_charge.ogg", cut(plasma_cannon, 0.325, 1.33, fade_in=0.002, fade_out=0.003))
    for i, (t0, t1) in enumerate(((1.33, 2.33), (4.62, 5.60))):
        write(f"plasma/cannon_fire_{i}.ogg", cut(plasma_cannon, t0, t1, fade_in=0.002, fade_out=0.3))
    for i, (t0, t1) in enumerate(((2.36, 4.56), (5.62, 7.18))):
        write(f"plasma/cannon_hit_{i}.ogg", cut(plasma_cannon, t0, t1, fade_in=0.002, fade_out=0.4))

    # Repulsor (user 10-16): Omni's soundtrack for VideoShowcase video_repulsor, three waves from silence (0.90 / 3.66 /
    # 6.11 s), each a whoosh that swells for ~0.5 s and dies out in ~1.5 s. Omni scored no charge, so the charge is a
    # wave's tail played backwards (1.81-2.41 s reversed): a suck-in that rises into the push. Push: waves 1 and 3.
    # One gain: wave 1's loudest 0.3 s at the guns' level. Far: wave 1 muffled.
    repulsor = scaled(load("repulsor_generated.wav"), 1.35, 1.65, -14.5)
    # 0.6 s: the last 0.1 s (fading) runs under the push, which itself swells from silence: no hole at the seam.
    write("repulsor/charge.ogg", cut(repulsor, 1.81, 2.41, fade_in=0.1, fade_out=0.003)[::-1].copy())
    for i, (t0, t1) in enumerate(((0.895, 2.85), (6.105, 7.70))):
        write(f"repulsor/push_{i}.ogg", cut(repulsor, t0, t1, fade_in=0.002, fade_out=0.3))
    write("repulsor/push_tail.ogg", level(muffle(cut(repulsor, 0.895, 2.85, fade_out=0.3), 900), -20.5))

    # Repulsor Dome (user 10-16): Omni's soundtrack for VideoShowcase video_repulsor_dome, second take (the first, kept as
    # repulsor_dome_take1_generated.wav, came out a crack and boom, "like a sniper shot"; the new prompt asked for wind
    # and a pressure wave). Two gusts from silence (0.79 / 5.32 s), a roar of wind held ~1 s that dies out by ~3 s; one
    # variant each. No charge again: the same trick as the Repulsor, gust 1's tail reversed. One gain: gust 1's loudest
    # 0.3 s a little under the push (it is held far longer). Far: gust 1 muffled.
    dome = scaled(load("repulsor_dome_generated.wav"), 1.25, 1.55, -15.0)
    write("repulsor/dome_charge.ogg", cut(dome, 1.75, 2.35, fade_in=0.1, fade_out=0.003)[::-1].copy())
    # From ~70 ms into each gust: its first rise from silence left a hole under the charge's fading end.
    for i, (t0, t1) in enumerate(((0.85, 3.80), (5.37, 8.40))):
        write(f"repulsor/dome_burst_{i}.ogg", cut(dome, t0, t1, fade_in=0.002, fade_out=0.5))
    write("repulsor/dome_burst_tail.ogg", level(muffle(cut(dome, 0.85, 3.80, fade_out=0.5), 900), -20.5))

    # Ion Rifle (user 10-16): Omni's soundtrack for VideoShowcase video_ion, five shots from silence (0.600 / 2.230 /
    # 4.070 / 5.820 / 7.675 s), each an electric zap-crack and sizzle gone in ~0.9 s. Shots 2 and 5, the least alike
    # (spectra). One gain: shot 2's first 0.3 s a little under the Gun and Laser (it fires every 0.8 s). ion.fire is also
    # the far sound, so no tail.
    ion = scaled(load("ion_generated.wav"), 2.23, 2.53, -15.5)
    for i, (t0, t1) in enumerate(((2.227, 3.15), (7.672, 8.75))):
        write(f"ion/fire_{i}.ogg", cut(ion, t0, t1, fade_in=0.002, fade_out=0.25))

    # Mortar (user 10-16): Omni's soundtrack for VideoShowcase video_mortar: launches at 0.49 / 4.85 s (a hollow thoomp
    # gone in ~1.3 s), blasts at 3.595 / 7.835 s with a quiet fire crackle after (the user asked for it; in game the patch
    # already crackles with flamethrower.burning), and no whistle at all. Launch 2 sits on blast 1's crackle, so both
    # variants come from launch 1 (the second a little lower, as the Rocket's). Far: launch 1 muffled.
    mortar = load("mortar_generated.wav")
    variants("mortar/fire_{}.ogg", cut(mortar, 0.487, 1.85, fade_in=0.002, fade_out=0.4), -20.0, (1, 0.94))  # first 0.3 s ~ -14
    write("mortar/fire_tail.ogg", level(muffle(cut(mortar, 0.487, 1.85, fade_out=0.4), 900), -20.5))

    # Plasma Repeater: from 1.8 s the take is a stream of shots every 250 ms, the Repeater's own rate (fire_interval 5);
    # three of them from 3-4 s (user), each up to the next shot's onset (spectral flux onsets 3.085 / 3.325 / 3.575 / 3.825).
    plasma = load("plasma_gun.mp3")
    for i, (t0, t1) in enumerate(((3.082, 3.322), (3.322, 3.572), (3.572, 3.822))):
        write(f"plasma/repeater_fire_{i}.ogg", level(cut(plasma, t0, t1, fade_out=0.06), -14.5))
