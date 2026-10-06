#!/usr/bin/env python3
"""Placeholder SFX synthesiser for Bastion Turrets (docs/PLAN.md 5.7).

PLACEHOLDER ONLY: every sound generated here must be replaced with real SFX before release (see README).
Output is mono OGG Vorbis, because Minecraft only attenuates mono sounds with distance.
Seeded, so re-running reproduces the same audio. Add a sound by writing a function that returns float
samples in [-1, 1] at RATE, list it in SOUNDS, then register it in sounds.json and BastionSounds.
Loops (idle hum, spin) crossfade their end into their start so they repeat without a click.

    pip install numpy soundfile
    python3 tools/gen_sounds.py
"""
from pathlib import Path

import numpy as np
import soundfile as sf

RATE = 44100
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bastion/sounds"


# --- primitives -------------------------------------------------------------------------------

def n(sec): return int(RATE * sec)
def t(sec): return np.arange(n(sec)) / RATE
def noise(sec, seed): return np.random.default_rng(seed).uniform(-1, 1, n(sec))
def sweep(sec, f0, f1, shape="exp"):
    tt = t(sec)
    f = f0 * (f1 / f0) ** (tt / sec) if shape == "exp" else f0 + (f1 - f0) * tt / sec
    return np.sin(2 * np.pi * np.cumsum(f) / RATE)
def env(sec, attack, decay):
    tt = t(sec)
    return np.minimum(tt / max(attack, 1e-4), 1) * np.exp(-np.maximum(tt - attack, 0) / decay)
def onepole_lp(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / RATE); y = np.empty_like(x); acc = 0.0
    for i, s in enumerate(x):
        acc = (1 - a) * s + a * acc; y[i] = acc
    return y
def hp(x, cutoff): return x - onepole_lp(x, cutoff)
def bp(x, lo, hi): return hp(onepole_lp(x, hi), lo)
def pad(x, sec): return np.concatenate([x, np.zeros(max(0, n(sec) - len(x)))])[:n(sec)]
def norm(x, peak=0.9): return x / (np.max(np.abs(x)) + 1e-9) * peak
def fade_out(x, sec=0.02):
    k = min(len(x), n(sec)); x = x.copy(); x[-k:] *= np.linspace(1, 0, k); return x
def loopable(x, xfade=0.05):
    """Crossfade the end into the start so the file loops without a click."""
    k = n(xfade); out = x[k:].copy(); out[-k:] = out[-k:] * np.linspace(1, 0, k) + x[:k] * np.linspace(0, 1, k); return out
def jitter(seed):  # small per-variant timbre change
    return 1 + (np.random.default_rng(seed).random() - 0.5) * 0.12

# --- sounds -----------------------------------------------------------------------------------

def debug_ping():
    """Fase 0 pipeline check: a short descending synth ping with a soft click."""
    length = 0.6
    tone = sweep(length, 1760, 1320) * env(length, 0.004, 0.12)
    click = onepole_lp(noise(length, 1), 3000) * env(length, 0.001, 0.008)
    return norm(tone + 0.6 * click)


# --- Gun (cyan, precision) ---------------------------------------------------------------
def gun_fire(v):
    j = jitter(100 + v); L = 0.7
    crack = bp(noise(L, 101 + v), 900, 7000) * env(L, 0.001, 0.035)
    thump = sweep(L, 140 * j, 48) * env(L, 0.002, 0.09)
    zap = sweep(L, 2600 * j, 700) * env(L, 0.001, 0.05) * 0.35
    tail = onepole_lp(noise(L, 103 + v), 1800) * env(L, 0.01, 0.22) * 0.5
    return fade_out(norm(crack + 0.9 * thump + zap + tail))
def gun_fire_tail(v):
    L = 1.2
    body = onepole_lp(noise(L, 110 + v), 700) * env(L, 0.01, 0.35)
    thump = sweep(L, 90, 40) * env(L, 0.005, 0.18)
    return fade_out(norm(body + 0.6 * thump, 0.7))
def kinetic_impact(v):
    L = 0.35
    click = hp(noise(L, 120 + v), 2500) * env(L, 0.0005, 0.012)
    crackle = bp(noise(L, 121 + v), 1500, 6000) * env(L, 0.003, 0.06) * (np.random.default_rng(122 + v).random(n(L)) > 0.7)
    ping = sweep(L, 3200 * jitter(123 + v), 2400) * env(L, 0.001, 0.04) * 0.3
    return fade_out(norm(click + crackle + ping, 0.8))
def gun_lock():
    L = 0.3
    beep = lambda start: pad(np.zeros(n(start)), start).tolist() + (np.sin(2 * np.pi * 1760 * t(0.06)) * env(0.06, 0.003, 0.03)).tolist()
    a = np.array(beep(0.0)); b = np.array(beep(0.1))
    return fade_out(norm(pad(a, L) + pad(b, L) * 1.0, 0.6))
def deploy():
    L = 0.9
    whir = sweep(L, 260, 620, "lin") * 0.5 + np.sign(sweep(L, 130, 310, "lin")) * 0.12
    whir = onepole_lp(whir, 2500) * np.minimum(t(L) / 0.05, 1) * np.clip((0.7 - t(L)) / 0.1, 0, 1)
    clack = np.zeros(n(L)); s = n(0.62); c = hp(noise(0.12, 130), 1200) * env(0.12, 0.0005, 0.02) + sweep(0.12, 400, 180) * env(0.12, 0.001, 0.03)
    clack[s:s + len(c)] = c
    return fade_out(norm(whir + clack, 0.75))
def idle_hum():
    L = 2.0  # 80 Hz and harmonics: whole cycles in 2 s, so the loop is seamless
    tt = t(L + 0.05)
    hum = np.sin(2 * np.pi * 80 * tt) + 0.4 * np.sin(2 * np.pi * 160 * tt) + 0.15 * np.sin(2 * np.pi * 400 * tt)
    hum *= 0.8 + 0.2 * np.sin(2 * np.pi * 0.5 * tt)
    return norm(loopable(hum), 0.35)
def servo_loop():
    L = 1.0
    tt = t(L + 0.05)
    motor = np.sign(np.sin(2 * np.pi * 180 * tt)) * 0.3 + np.sin(2 * np.pi * 360 * tt) * 0.5
    motor = onepole_lp(motor, 1600) + onepole_lp(noise(L + 0.05, 140), 900) * 0.15
    return norm(loopable(motor), 0.4)

# --- Machine Gun (amber, spin-up) --------------------------------------------------------
def mg_fire(v):
    j = jitter(200 + v); L = 0.25
    crack = bp(noise(L, 201 + v), 1200, 8000) * env(L, 0.0005, 0.018)
    thump = sweep(L, 180 * j, 70) * env(L, 0.001, 0.04)
    zap = sweep(L, 1900 * j, 900) * env(L, 0.001, 0.025) * 0.3
    return fade_out(norm(crack + 0.8 * thump + zap, 0.85))
def mg_spin(direction):
    L = 1.0 if direction == "up" else 1.5
    f0, f1 = (120, 1100) if direction == "up" else (1100, 90)
    whine = sweep(L, f0, f1) * 0.6 + sweep(L, f0 * 2, f1 * 2) * 0.2
    gear = np.sign(sweep(L, f0 / 4, f1 / 4)) * 0.12
    e = np.minimum(t(L) / 0.04, 1) * (np.clip((L - t(L)) / 0.3, 0, 1) if direction == "down" else 1)
    return fade_out(norm(onepole_lp(whine + gear, 4000) * e, 0.6))
def mg_spin_loop():
    L = 0.5  # pitch-shifted by spin speed in code; 1100 Hz fits whole cycles in 0.5 s
    tt = t(L + 0.05)
    s = np.sin(2 * np.pi * 1100 * tt) * 0.6 + np.sin(2 * np.pi * 2200 * tt) * 0.2 + np.sign(np.sin(2 * np.pi * 275 * tt)) * 0.12
    return norm(loopable(onepole_lp(s, 4000)), 0.5)
def overheat():
    L = 1.8
    hiss = hp(noise(L, 220), 2500) * env(L, 0.02, 0.7)
    alarm = np.zeros(n(L))
    for k in range(3):
        s = n(0.05 + 0.22 * k); b = np.sin(2 * np.pi * 920 * t(0.12)) * env(0.12, 0.004, 0.08)
        alarm[s:s + len(b)] += b * 0.5
    return fade_out(norm(hiss + alarm, 0.75))

# --- Shotgun (magenta, kinetic blast) ----------------------------------------------------
def sg_fire(v):
    j = jitter(300 + v); L = 1.3
    blast = onepole_lp(noise(L, 301 + v), 3500) * env(L, 0.001, 0.12)
    crack = hp(noise(L, 302 + v), 2000) * env(L, 0.0005, 0.03)
    boom = sweep(L, 110 * j, 32) * env(L, 0.003, 0.25)
    zap = sweep(L, 1500 * j, 260) * env(L, 0.001, 0.09) * 0.3
    tail = onepole_lp(noise(L, 303 + v), 600) * env(L, 0.03, 0.45) * 0.6
    return fade_out(norm(blast + crack + 1.1 * boom + zap + tail))
def sg_pump():
    L = 0.95
    out = np.zeros(n(L))
    def clack(at, pitch, seed):
        c = hp(noise(0.1, seed), 1500) * env(0.1, 0.0005, 0.015) + sweep(0.1, pitch, pitch * 0.5) * env(0.1, 0.001, 0.025) * 0.7
        s = n(at); out[s:s + len(c)] += c
    slide = bp(noise(L, 310), 600, 3000) * 0.18 * ((t(L) > 0.05) & (t(L) < 0.32) | (t(L) > 0.45) & (t(L) < 0.7))
    clack(0.30, 520, 311); clack(0.70, 640, 312)
    return fade_out(norm(out + slide, 0.8))
def shell_drop(v):
    L = 0.5
    out = np.zeros(n(L))
    for k, (at, amp) in enumerate(((0.0, 1.0), (0.14, 0.45), (0.23, 0.2))):
        p = 2400 * jitter(320 + v + k)
        c = np.sin(2 * np.pi * p * t(0.12)) * env(0.12, 0.0005, 0.03) + hp(noise(0.12, 321 + v + k), 3000) * env(0.12, 0.0005, 0.006)
        s = n(at); out[s:s + len(c)] += c * amp
    return fade_out(norm(out, 0.5))

# --- Sniper (charged heavy round) --------------------------------------------------------
def sniper_charge():
    L = 1.5  # matches charge_ticks 30: a capacitor whine rising to a lock tone
    tt = t(L)
    whine = sweep(L, 260, 2100) * (0.25 + 0.75 * tt / L) * 0.6
    tremolo = 0.75 + 0.25 * np.sin(2 * np.pi * (6 + 18 * tt / L) * tt)
    hum = onepole_lp(noise(L, 500), 900) * 0.15
    return fade_out(norm((whine * tremolo + hum) * np.minimum(1, tt / 0.08), 0.6), 0.05)
def sniper_fire(v):
    j = jitter(510 + v); L = 2.2
    crack = hp(noise(L, 511 + v), 3000) * env(L, 0.0003, 0.02)
    snap = sweep(L, 3200 * j, 600) * env(L, 0.0005, 0.03) * 0.5
    boom = sweep(L, 95 * j, 30) * env(L, 0.002, 0.35)
    body = onepole_lp(noise(L, 512 + v), 2400) * env(L, 0.001, 0.09)
    tail = onepole_lp(noise(L, 513 + v), 500) * env(L, 0.04, 0.8) * 0.55
    return fade_out(norm(1.2 * crack + snap + 1.1 * boom + body + tail), 0.2)
def sniper_fire_tail():
    L = 2.4
    crack = bp(noise(L, 520), 800, 3000) * env(L, 0.002, 0.04)
    roll = onepole_lp(noise(L, 521), 300) * env(L, 0.05, 0.9)
    return fade_out(norm(crack + roll, 0.7), 0.3)

# --- Rocket launcher ---------------------------------------------------------------------
def rocket_launch(v):
    j = jitter(530 + v); L = 1.4
    thump = sweep(L, 80 * j, 35) * env(L, 0.002, 0.12)
    whoosh = bp(noise(L, 531 + v), 400, 5000) * env(L, 0.03, 0.4) * np.linspace(1.4, 0.4, n(L))
    hiss = hp(noise(L, 532 + v), 4000) * env(L, 0.01, 0.25) * 0.4
    return fade_out(norm(1.2 * thump + whoosh + hiss), 0.15)
def rocket_launch_tail():
    L = 1.8
    return fade_out(norm(onepole_lp(noise(L, 540), 600) * env(L, 0.05, 0.6), 0.6), 0.2)
def rocket_fly():
    L = 1.0  # loop: a rough motor roar with a slow flutter
    tt = t(L + 0.05)
    roar = bp(noise(L + 0.05, 550), 150, 2600) * (0.8 + 0.2 * np.sin(2 * np.pi * 9 * tt))
    return norm(loopable(roar), 0.5)
def rocket_explode(v):
    j = jitter(560 + v); L = 2.8
    boom = sweep(L, 70 * j, 22) * env(L, 0.004, 0.55)
    blast = onepole_lp(noise(L, 561 + v), 2200) * env(L, 0.002, 0.3)
    crack = hp(noise(L, 562 + v), 3000) * env(L, 0.0005, 0.04)
    debris = np.zeros(n(L))
    rng = np.random.default_rng(563 + v)
    for _ in range(14):  # rattling fragments
        at = n(0.15 + rng.random() * 1.2); c = hp(noise(0.05, int(rng.integers(1e6))), 2500) * env(0.05, 0.0005, 0.01)
        debris[at:at + len(c)] += c * rng.uniform(0.1, 0.35)
    rumble = onepole_lp(noise(L, 564 + v), 200) * env(L, 0.08, 1.0) * 0.9
    return fade_out(norm(1.3 * boom + blast + crack + debris + rumble), 0.25)

# --- Missile launcher (salvos of twelve: short, quick-decaying sounds) ---------------------------
def missile_launch(v):
    j = jitter(600 + v); L = 0.9
    pop = sweep(L, 140 * j, 60) * env(L, 0.002, 0.06)
    hiss = bp(noise(L, 601 + v), 1200, 7000) * env(L, 0.01, 0.22) * np.linspace(1.3, 0.3, n(L))
    whine = sweep(L, 900 * j, 2600) * env(L, 0.02, 0.25) * 0.25
    return fade_out(norm(pop + hiss + whine, 0.8), 0.1)
def missile_reload():
    L = 0.5
    out = np.zeros(n(L))
    for k, (at, pitch) in enumerate(((0.0, 700), (0.16, 980))):  # slide in, latch
        c = hp(noise(0.08, 610 + k), 1500) * env(0.08, 0.0005, 0.012) + sweep(0.08, pitch, pitch * 0.6) * env(0.08, 0.001, 0.02) * 0.6
        s = n(at); out[s:s + len(c)] += c
    slide = bp(noise(L, 612), 500, 2500) * 0.12 * (t(L) < 0.16)
    return fade_out(norm(out + slide, 0.6))
def missile_explode(v):
    j = jitter(620 + v); L = 1.6
    boom = sweep(L, 95 * j, 30) * env(L, 0.003, 0.3)
    blast = onepole_lp(noise(L, 621 + v), 2600) * env(L, 0.002, 0.18)
    crack = hp(noise(L, 622 + v), 3200) * env(L, 0.0005, 0.03)
    rumble = onepole_lp(noise(L, 623 + v), 220) * env(L, 0.05, 0.5) * 0.6
    return fade_out(norm(1.2 * boom + blast + crack + rumble), 0.2)

# --- turret base events ------------------------------------------------------------------
def destroyed():
    L = 2.6
    boom = sweep(L, 90, 24) * env(L, 0.004, 0.5)
    blast = onepole_lp(noise(L, 400), 1800) * env(L, 0.002, 0.35)
    crack = hp(noise(L, 401), 2500) * env(L, 0.0005, 0.05)
    zap = sweep(L, 2200, 180) * env(L, 0.001, 0.2) * 0.35
    rumble = onepole_lp(noise(L, 402), 250) * env(L, 0.05, 0.9) * 0.8
    return fade_out(norm(1.2 * boom + blast + crack + zap + rumble), 0.2)
def tier_up():
    L = 1.2
    out = np.zeros(n(L))
    for k, f in enumerate((523, 659, 784, 1046)):  # rising arpeggio
        s = n(0.09 * k); b = (np.sin(2 * np.pi * f * t(0.5)) + 0.3 * np.sin(4 * np.pi * f * t(0.5))) * env(0.5, 0.004, 0.18)
        out[s:s + len(b)] += b
    whoosh = bp(noise(L, 410), 800, 5000) * env(L, 0.25, 0.25) * 0.25
    return fade_out(norm(out + whoosh, 0.8))
def repair():
    L = 0.8
    out = np.zeros(n(L))
    for k in range(4):  # ratchet clicks then a confirm chirp
        c = hp(noise(0.06, 420 + k), 1800) * env(0.06, 0.0005, 0.01); s = n(0.08 * k); out[s:s + len(c)] += c
    chirp = sweep(0.25, 900, 1500) * env(0.25, 0.005, 0.08); s = n(0.4); out[s:s + len(chirp)] += chirp * 0.7
    return fade_out(norm(out, 0.7))

# --- Tesla Coil ----------------------------------------------------------------------------------
def crackles(L, seed, count, lo, amp, window=(0.0, 1.0)):
    """Short high-passed noise ticks scattered over [window] of the sound: electric crackle, burning wood."""
    out = np.zeros(n(L)); rng = np.random.default_rng(seed)
    for _ in range(count):
        at = n(L * (window[0] + rng.random() * (window[1] - window[0]))); dur = 0.004 + rng.random() * 0.02
        c = hp(noise(dur, int(rng.integers(1e6))), lo) * env(dur, 0.0003, dur / 3)
        out[at:at + len(c)] += c[:len(out) - at] * rng.uniform(*amp)
    return out
def square(sec, f0, f1):
    return np.sign(np.sin(2 * np.pi * np.cumsum(np.linspace(f0, f1, n(sec))) / RATE))
def tesla_charge():
    L = 0.7; ramp = np.linspace(0.2, 1, n(L))
    whine = sweep(L, 180, 900) * ramp * 0.35
    buzz = onepole_lp(square(L, 60, 120), 1800) * 0.12 * ramp
    return fade_out(norm(whine + buzz + crackles(L, 700, 40, 2500, (0.1, 0.6)) * ramp, 0.8), 0.03)
def tesla_zap(v):
    j = jitter(710 + v); L = 0.9
    snap = hp(noise(L, 711 + v), 1500) * env(L, 0.0003, 0.02)
    buzz = onepole_lp(square(L, 120 * j, 110 * j), 2500) * env(L, 0.002, 0.18) * 0.35
    thump = sweep(L, 90 * j, 40) * env(L, 0.002, 0.08) * 0.8
    sizzle = bp(noise(L, 713 + v), 3000, 9000) * env(L, 0.01, 0.3) * 0.25
    return fade_out(norm(snap + crackles(L, 712 + v, 60, 1800, (0.2, 0.9), (0, 0.35)) + buzz + thump + sizzle), 0.1)
def tesla_zap_tail():
    L = 1.4
    return fade_out(norm(onepole_lp(crackles(L, 720, 50, 800, (0.2, 1), (0, 0.4)) + noise(L, 721) * env(L, 0.005, 0.25) * 0.3, 900), 0.6), 0.2)
def tesla_crackle(v):
    L = 0.35
    return fade_out(norm(crackles(L, 730 + v, 14, 2500, (0.2, 1), (0, 0.6)), 0.5), 0.05)

# --- Laser Rifle ---------------------------------------------------------------------------------
def laser_charge():
    L = 2.5; ramp = np.linspace(0, 1, n(L)) ** 1.5
    tt = t(L)
    rise = sweep(L, 160, 1900) * (0.25 + 0.75 * ramp) * (0.75 + 0.25 * np.sin(2 * np.pi * (3 + 22 * ramp) * tt))
    hum = onepole_lp(square(L, 55, 110), 900) * 0.15
    crackle = crackles(L, 830, 30, 3000, (0.05, 0.4), (0.6, 1.0))
    return fade_out(norm(rise + hum + crackle, 0.7), 0.02)
def laser_fire(v):
    j = jitter(840 + v); L = 1.2
    zap = sweep(L, 2600 * j, 260) * env(L, 0.001, 0.18)
    body = sweep(L, 900 * j, 120) * env(L, 0.002, 0.3) * 0.6
    thump = sweep(L, 90, 35) * env(L, 0.002, 0.1)
    burst = hp(noise(L, 841 + v), 1200) * env(L, 0.0005, 0.04) * 0.8
    sizzle = bp(noise(L, 842 + v), 3000, 10000) * env(L, 0.02, 0.45) * 0.3
    return fade_out(norm(zap + body + thump + burst + sizzle), 0.15)
def laser_fire_tail():
    L = 1.6
    return fade_out(norm(onepole_lp(sweep(L, 700, 120) * env(L, 0.005, 0.4) + noise(L, 850) * env(L, 0.005, 0.3) * 0.4, 800), 0.6), 0.2)

# --- Workstations (PLAN Fase 9) --------------------------------------------------------------------
def ws_loop():
    L = 2.0  # loop: a low machine hum, a servo whir that swells and settles, a faint mechanical tick
    tt = t(L + 0.05)
    hum = (np.sin(2 * np.pi * 55 * tt) + 0.5 * np.sin(2 * np.pi * 110 * tt) + 0.2 * np.sin(2 * np.pi * 165 * tt)) * 0.3
    whir = bp(noise(L + 0.05, 870), 600, 2400) * (0.5 + 0.5 * np.sin(2 * np.pi * 1.0 * tt)) * 0.35
    return norm(loopable(hum + whir + crackles(L + 0.05, 871, 8, 3000, (0.02, 0.08))), 0.5)
def ws_weld():
    L = 0.5
    return fade_out(norm(crackles(L, 880, 30, 2000, (0.2, 1), (0, 0.7)) + bp(noise(L, 881), 2500, 9000) * env(L, 0.01, 0.2) * 0.4, 0.6), 0.05)
def ws_press():
    L = 0.7
    thump = sweep(L, 110, 45) * env(L, 0.003, 0.09)
    clank = hp(noise(L, 890), 1800) * env(L, 0.0005, 0.03) * 0.7
    hiss = bp(noise(L, 891), 1500, 6000) * env(L, 0.08, 0.25) * 0.35 * (t(L) > 0.05)
    return fade_out(norm(thump + clank + hiss, 0.8), 0.08)
def ws_done():
    L = 0.9
    out = np.zeros(n(L))
    for at, f in ((0.0, 880), (0.12, 1320)):  # two-tone chime
        tone = np.sin(2 * np.pi * f * t(L - at)) * env(L - at, 0.004, 0.25)
        out[n(at):] += tone[:len(out) - n(at)]
    return fade_out(norm(out, 0.6), 0.1)

# --- Flamethrower --------------------------------------------------------------------------------
def flame_ignite():
    L = 0.8
    whoomp = onepole_lp(noise(L, 740), 500) * env(L, 0.02, 0.18) * 1.2
    thump = sweep(L, 70, 35) * env(L, 0.005, 0.12)
    hiss = bp(noise(L, 741), 1500, 6000) * env(L, 0.01, 0.3) * 0.3
    return fade_out(norm(whoomp + thump + hiss, 0.85), 0.15)
def flame_loop():
    L = 1.0  # loop: a low roar with a slow flutter, hiss and the odd pop
    tt = t(L + 0.05)
    roar = onepole_lp(noise(L + 0.05, 750), 900) * (0.85 + 0.15 * np.sin(2 * np.pi * 7 * tt))
    hiss = bp(noise(L + 0.05, 751), 2000, 7000) * 0.25
    return norm(loopable(roar + hiss + crackles(L + 0.05, 752, 20, 1200, (0.05, 0.3))), 0.6)
def burning():
    L = 0.9
    return fade_out(norm(crackles(L, 760, 22, 1000, (0.1, 0.8)) + onepole_lp(noise(L, 761), 400) * 0.15, 0.4), 0.1)

SOUNDS = {
    "debug/ping.ogg": debug_ping,
    **{f"gun/fire_{v}.ogg": (lambda v=v: gun_fire(v)) for v in range(3)},
    **{f"gun/fire_tail_{v}.ogg": (lambda v=v: gun_fire_tail(v)) for v in range(2)},
    **{f"impact/kinetic_{v}.ogg": (lambda v=v: kinetic_impact(v)) for v in range(3)},
    "gun/lock.ogg": gun_lock, "turret/deploy.ogg": deploy, "turret/idle_hum.ogg": idle_hum, "turret/servo_loop.ogg": servo_loop,
    **{f"machine_gun/fire_{v}.ogg": (lambda v=v: mg_fire(v)) for v in range(3)},
    "machine_gun/spin_up.ogg": lambda: mg_spin("up"), "machine_gun/spin_down.ogg": lambda: mg_spin("down"),
    "machine_gun/spin_loop.ogg": mg_spin_loop, "machine_gun/overheat.ogg": overheat,
    **{f"shotgun/fire_{v}.ogg": (lambda v=v: sg_fire(v)) for v in range(3)},
    "sniper/charge.ogg": sniper_charge, "sniper/fire_tail.ogg": sniper_fire_tail,
    **{f"sniper/fire_{v}.ogg": (lambda v=v: sniper_fire(v)) for v in range(2)},
    **{f"rocket/launch_{v}.ogg": (lambda v=v: rocket_launch(v)) for v in range(2)},
    "rocket/launch_tail.ogg": rocket_launch_tail, "rocket/fly.ogg": rocket_fly,
    **{f"rocket/explode_{v}.ogg": (lambda v=v: rocket_explode(v)) for v in range(2)},
    **{f"missile/launch_{v}.ogg": (lambda v=v: missile_launch(v)) for v in range(2)},
    "missile/reload.ogg": missile_reload,
    **{f"missile/explode_{v}.ogg": (lambda v=v: missile_explode(v)) for v in range(2)},
    "turret/destroyed.ogg": destroyed, "turret/tier_up.ogg": tier_up, "turret/repair.ogg": repair,
    "tesla/charge.ogg": tesla_charge, **{f"tesla/zap_{v}.ogg": (lambda v=v: tesla_zap(v)) for v in range(2)},
    "tesla/zap_tail.ogg": tesla_zap_tail, **{f"tesla/crackle_{v}.ogg": (lambda v=v: tesla_crackle(v)) for v in range(2)},
    "laser/charge.ogg": laser_charge, **{f"laser/fire_{v}.ogg": (lambda v=v: laser_fire(v)) for v in range(2)},
    "laser/fire_tail.ogg": laser_fire_tail,
    "workstation/loop.ogg": ws_loop, "workstation/weld.ogg": ws_weld, "workstation/press.ogg": ws_press, "workstation/done.ogg": ws_done,
    "flamethrower/ignite.ogg": flame_ignite, "flamethrower/loop.ogg": flame_loop, "flamethrower/burning.ogg": burning,
    "shotgun/pump.ogg": sg_pump, **{f"shotgun/shell_{v}.ogg": (lambda v=v: shell_drop(v)) for v in range(2)},
}

if __name__ == "__main__":
    for rel, make in SOUNDS.items():
        path = OUT / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        sf.write(path, make(), RATE, format="OGG", subtype="VORBIS")
        print("wrote", path.relative_to(OUT.parents[5]))
