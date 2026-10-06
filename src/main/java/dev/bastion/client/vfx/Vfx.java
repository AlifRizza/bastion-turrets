package dev.bastion.client.vfx;

import dev.bastion.registry.BastionParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Building blocks a {@link VfxPreset} composes. Counts are scaled by the VFX quality preset (PLAN 5.8);
 * randomness comes from the params seed, so every client plays the same pattern.
 */
public final class Vfx {
    private final Vec3 origin, direction;
    private final VfxParams params;
    private final RandomSource random;

    Vfx(Vec3 origin, Vec3 direction, VfxParams params) {
        this.origin = origin;
        this.direction = direction.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : direction.normalize();
        this.params = params;
        this.random = RandomSource.create(params.seed());
    }

    public Vec3 origin() {
        return origin;
    }

    public Vec3 direction() {
        return direction;
    }

    public VfxParams params() {
        return params;
    }

    public RandomSource random() {
        return random;
    }

    /** Particles thrown along {@code axis} inside a cone, speed jittered +-35%. */
    public Vfx burst(ParticleType<BastionParticles.Options> type, int count, Vec3 axis, float coneDegrees, float speed,
                     float scale, int lifetime, BastionParticles.Curve curve, int argb) {
        int n = VfxManager.scaledCount(count);
        for (int i = 0; i < n; i++) {
            Vec3 v = cone(axis, coneDegrees).scale(speed * (0.65f + random.nextFloat() * 0.7f));
            spawn(type, origin, v, scale * (0.8f + random.nextFloat() * 0.4f), lifetime + random.nextInt(Math.max(1, lifetime / 3)), curve, argb);
        }
        return this;
    }

    /**
     * A continuous stream (Flamethrower): like {@link #burst}, but each particle starts up to {@code spacingTicks} of
     * travel down its own path, so pulses fired every few ticks join into one stream instead of separate puffs.
     */
    public Vfx jet(ParticleType<BastionParticles.Options> type, int count, Vec3 axis, float coneDegrees, float speed,
                   float scale, int lifetime, BastionParticles.Curve curve, int argb, float spacingTicks) {
        int n = VfxManager.scaledCount(count);
        for (int i = 0; i < n; i++) {
            Vec3 v = cone(axis, coneDegrees).scale(speed * (0.65f + random.nextFloat() * 0.7f));
            spawn(type, origin.add(v.scale(random.nextFloat() * spacingTicks)), v, scale * (0.8f + random.nextFloat() * 0.4f),
                    lifetime + random.nextInt(Math.max(1, lifetime / 3)), curve, argb);
        }
        return this;
    }

    /** Particles that start on a sphere of {@code radius} around the origin and stream into it, arriving in about {@code ticks}. */
    public Vfx converge(ParticleType<BastionParticles.Options> type, int count, float radius, int ticks, float scale,
                        BastionParticles.Curve curve, int argb) {
        int n = VfxManager.scaledCount(count);
        for (int i = 0; i < n; i++) {
            Vec3 out = cone(new Vec3(0, 1, 0), 180);
            float r = radius * (0.6f + 0.4f * random.nextFloat()) * params.scale();
            spawn(type, origin.add(out.scale(r)), out.scale(-r / ticks), scale * (0.8f + random.nextFloat() * 0.4f), ticks, curve, argb);
        }
        return this;
    }

    /** One flat ring facing {@code facing}, drifting along it. */
    public Vfx ring(ParticleType<BastionParticles.Options> type, Vec3 at, Vec3 facing, float drift, float scale, int lifetime,
                    BastionParticles.Curve curve, int argb) {
        spawn(type, at, facing.normalize().scale(Math.max(drift, 1e-3f)), scale, lifetime, curve, argb);
        return this;
    }

    /** A single particle at a point with no motion: flashes. */
    public Vfx single(ParticleType<BastionParticles.Options> type, Vec3 at, float scale, int lifetime, BastionParticles.Curve curve, int argb) {
        spawn(type, at, Vec3.ZERO, scale, lifetime, curve, argb);
        return this;
    }

    public Vfx light(int level, int ticks) {
        DynamicLightManager.add(origin, level, ticks);
        return this;
    }

    public Vfx shake(float trauma, float radius) {
        CameraShake.add(origin, trauma, radius);
        return this;
    }

    public Vfx flash(float strength, float radius) {
        ScreenFlash.add(origin, strength, radius);
        return this;
    }

    /** A large blast scorch on the surface at the origin (params normal), glowing hot then cooling. */
    public Vfx scorch(float size, int ticks) {
        DecalRenderer.add(origin, params.normal(), params.color(), size, ticks, DecalRenderer.SCORCH);
        return this;
    }

    /** A burn on the surface that grows instead of stacking when hit again in the same place (DecalRenderer#addOrGrow). */
    public Vfx burnMark(float size, int ticks, float grow, float maxSize) {
        DecalRenderer.addOrGrow(origin, params.normal(), params.color(), size, ticks, DecalRenderer.SCORCH, grow, maxSize);
        return this;
    }

    public Vfx decal(float size, int ticks) {
        DecalRenderer.add(origin, params.normal(), params.color(), size, ticks);
        return this;
    }

    /** Uniform direction inside a cone of half-angle {@code degrees} around {@code axis}. */
    public Vec3 cone(Vec3 axis, float degrees) {
        Vec3 a = axis.normalize();
        double theta = Math.toRadians(degrees) * Math.sqrt(random.nextDouble());
        double phi = random.nextDouble() * Math.PI * 2;
        Vec3 helper = Math.abs(a.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = a.cross(helper).normalize(), w = a.cross(u);
        Vec3 offset = u.scale(Math.cos(phi)).add(w.scale(Math.sin(phi)));
        return a.scale(Math.cos(theta)).add(offset.scale(Math.sin(theta)));
    }

    private void spawn(ParticleType<BastionParticles.Options> type, Vec3 at, Vec3 velocity, float scale, int lifetime,
                       BastionParticles.Curve curve, int argb) {
        // Through the particle engine directly: vanilla addParticle culls beyond 32 blocks, turrets fire further.
        Minecraft.getInstance().particleEngine.createParticle(
                new BastionParticles.Options(type, argb, scale * params.scale(), Mth.clamp(lifetime, 1, 400), curve),
                at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }
}
