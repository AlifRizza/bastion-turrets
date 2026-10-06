package dev.bastion.client.vfx;

import net.minecraft.world.phys.Vec3;

/** Per-call tuning for a preset: {@code VfxParams.of().scale(1).color(0xFF4FD8FF)} (PLAN 5). */
public final class VfxParams {
    private int color = 0xFFFFFFFF;
    private float scale = 1f;
    private long seed;
    private Vec3 normal = new Vec3(0, 1, 0);
    private int blockColor = 0xFF888888;

    public static VfxParams of() {
        return new VfxParams();
    }

    /** ARGB energy colour. */
    public VfxParams color(int argb) {
        this.color = argb;
        return this;
    }

    public VfxParams scale(float scale) {
        this.scale = scale;
        return this;
    }

    /** Same seed, same random pattern on every client. */
    public VfxParams seed(long seed) {
        this.seed = seed;
        return this;
    }

    /** Surface normal for impacts and decals. */
    public VfxParams normal(Vec3 normal) {
        this.normal = normal;
        return this;
    }

    /** Colour of the block that was hit, for debris. */
    public VfxParams blockColor(int argb) {
        this.blockColor = argb;
        return this;
    }

    public int color() {
        return color;
    }

    public float scale() {
        return scale;
    }

    public long seed() {
        return seed;
    }

    public Vec3 normal() {
        return normal;
    }

    public int blockColor() {
        return blockColor;
    }
}
