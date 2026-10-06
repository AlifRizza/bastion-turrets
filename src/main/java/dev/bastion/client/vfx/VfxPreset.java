package dev.bastion.client.vfx;

/** One reusable effect, composed from {@link Vfx} building blocks (particles, light, shake, flash, decal). */
@FunctionalInterface
public interface VfxPreset {
    void play(Vfx vfx);
}
