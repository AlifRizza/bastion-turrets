package dev.bastion.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bastion.registry.BastionParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Every Bastion particle (PLAN 5.4): one class, configured per type by a {@link Behavior}. Colour,
 * scale, lifetime and curve come from {@link BastionParticles.Options}. All sprites are original
 * (tools/gen_textures.py) and greyscale, tinted here with the options colour.
 */
public class VfxParticle extends TextureSheetParticle {
    /** PLAN 9 budget: active Bastion particles; the oldest are dropped first. */
    public static final int MAX_ACTIVE = 1500;
    private static final Deque<VfxParticle> LIVE = new ArrayDeque<>();

    /** Additive, fullbright, no depth write: glow that never darkens what is behind it. */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "BASTION_ADDITIVE";
        }
    };

    /**
     * Alpha-blended like vanilla's translucent sheet but without depth writes: smoke and fire blend in creation order
     * (newest on top) instead of a half-transparent puff hiding everything behind it. Blocks still occlude them.
     */
    public static final ParticleRenderType SOFT = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "BASTION_SOFT";
        }
    };

    public enum Shape {BILLBOARD, STRETCHED, FLAT}

    /**
     * Per-type motion and look. {@code gravity} uses vanilla units (positive pulls down), {@code drag} is the
     * per-tick velocity multiplier, {@code flatDrift} scales the spawn velocity of FLAT particles, whose
     * direction instead sets the ring's facing.
     */
    public record Behavior(Shape shape, boolean glow, boolean animated, float gravity, float drag, boolean bounce,
                           float spin, boolean cools, float stretch, float flatDrift, boolean fire,
                           boolean collides) {
        public static Behavior billboard(boolean glow) {
            return new Behavior(Shape.BILLBOARD, glow, false, 0, 0.96f, false, 0, false, 1, 0, false, false);
        }

        public Behavior withFrames() {
            return new Behavior(shape, glow, true, gravity, drag, bounce, spin, cools, stretch, flatDrift, fire, collides);
        }

        public Behavior physics(float gravity, float drag, boolean bounce) {
            return new Behavior(shape, glow, animated, gravity, drag, bounce, spin, cools, stretch, flatDrift, fire, collides);
        }

        public Behavior spinning(float spin) {
            return new Behavior(shape, glow, animated, gravity, drag, bounce, spin, cools, stretch, flatDrift, fire, collides);
        }

        public Behavior stretched(float stretch, boolean cools) {
            return new Behavior(Shape.STRETCHED, glow, animated, gravity, drag, bounce, spin, cools, stretch, flatDrift, fire, collides);
        }

        /** Fire colour ramp over the particle's life: white-hot, the options colour, deep red, soot. */
        public Behavior burning() {
            return new Behavior(shape, glow, animated, gravity, drag, bounce, spin, cools, stretch, flatDrift, true, collides);
        }

        /** Stops against blocks (piles up on walls and floors) even without gravity or bounce. */
        public Behavior colliding() {
            return new Behavior(shape, glow, animated, gravity, drag, bounce, spin, cools, stretch, flatDrift, fire, true);
        }

        public Behavior flat(float drift) {
            return new Behavior(Shape.FLAT, glow, animated, gravity, drag, bounce, spin, cools, stretch, drift, fire, collides);
        }
    }

    private final SpriteSet sprites;
    private final Behavior behavior;
    private final BastionParticles.Curve curve;
    private final float baseSize, baseAlpha, red, green, blue;
    private final Vector3f facing = new Vector3f(0, 1, 0);
    private boolean bounced;
    private float spinSpeed;

    protected VfxParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                          BastionParticles.Options options, SpriteSet sprites, Behavior behavior) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.behavior = behavior;
        this.curve = options.curve();
        this.lifetime = Math.max(1, options.lifetime());
        this.baseSize = options.scale() * 0.1f; // 1.0 scale = a vanilla-sized particle
        this.quadSize = baseSize * curve.scale(0);
        this.alpha = this.baseAlpha = (options.argb() >>> 24) / 255f;
        this.red = (options.argb() >> 16 & 255) / 255f;
        this.green = (options.argb() >> 8 & 255) / 255f;
        this.blue = (options.argb() & 255) / 255f;
        setColor(red, green, blue);
        this.gravity = behavior.gravity();
        this.friction = behavior.drag();
        this.hasPhysics = behavior.bounce() || behavior.gravity() > 0 || behavior.collides();
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        this.spinSpeed = behavior.spin() * (random.nextBoolean() ? 1 : -1);
        if (behavior.shape() == Shape.FLAT) {
            Vec3 normal = new Vec3(dx, dy, dz);
            if (normal.lengthSqr() > 1e-8) facing.set((float) normal.x, (float) normal.y, (float) normal.z).normalize();
            this.xd = dx * behavior.flatDrift();
            this.yd = dy * behavior.flatDrift();
            this.zd = dz * behavior.flatDrift();
        } else {
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
        }
        if (behavior.animated()) setSpriteFromAge(sprites);
        else pickSprite(sprites);

        LIVE.addLast(this);
        while (LIVE.size() > MAX_ACTIVE) LIVE.pollFirst().remove();
    }

    /** Drops finished particles from the budget queue; called once per client tick. */
    public static void pruneBudget() {
        LIVE.removeIf(p -> !p.isAlive());
    }

    public static int activeCount() {
        return LIVE.size();
    }

    @Override
    public void tick() {
        double fallingSpeed = yd;
        super.tick();
        if (!isAlive()) return;
        if (behavior.bounce() && !bounced && onGround && fallingSpeed < -0.02) {
            yd = -fallingSpeed * 0.4; // one bounce, then rest
            xd *= 0.6;
            zd *= 0.6;
            bounced = true;
        }
        oRoll = roll;
        roll += spinSpeed;
        if (onGround) spinSpeed *= 0.7f;
        if (behavior.animated()) setSpriteFromAge(sprites);

        float t = (float) age / lifetime;
        quadSize = baseSize * curve.scale(t);
        alpha = baseAlpha * Mth.clamp(curve.alpha(t, age), 0, 1);
        if (behavior.fire()) {
            float[] c = fireRamp(t);
            setColor(c[0], c[1], c[2]);
        } else if (behavior.cools()) {
            // white-hot, then the energy colour, then a dim ember (PLAN 5.4 spark)
            float heat = Mth.clamp(1 - t / 0.25f, 0, 1), dim = Mth.clamp((t - 0.25f) / 0.75f, 0, 1) * 0.65f;
            setColor(Mth.lerp(heat, red, 1) * (1 - dim), Mth.lerp(heat, green, 1) * (1 - dim), Mth.lerp(heat, blue, 1) * (1 - dim));
        }
    }

    /** Bright yellow -> options colour (t 0.22) -> deep red (0.55) -> soot (1): the soot phase reads as smoke. */
    private float[] fireRamp(float t) {
        float[][] stops = {{1f, 0.86f, 0.42f}, {red, green, blue}, {0.45f, 0.1f, 0.03f}, {0.09f, 0.07f, 0.06f}};
        float[] at = {0f, 0.22f, 0.55f, 1f};
        int i = t < at[1] ? 0 : t < at[2] ? 1 : 2;
        float k = Mth.clamp((t - at[i]) / (at[i + 1] - at[i]), 0, 1);
        return new float[]{Mth.lerp(k, stops[i][0], stops[i + 1][0]), Mth.lerp(k, stops[i][1], stops[i + 1][1]),
                Mth.lerp(k, stops[i][2], stops[i + 1][2])};
    }

    /**
     * Glow particles add light; fire is alpha-blended instead (still full-bright), so it stays solid against a
     * daylight sky and its soot phase darkens into smoke. Fire and smoke share SOFT, so the newest draws on top.
     */
    @Override
    public ParticleRenderType getRenderType() {
        return behavior.glow() && !behavior.fire() ? ADDITIVE : SOFT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return behavior.glow() ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        switch (behavior.shape()) {
            case BILLBOARD -> super.render(buffer, camera, partialTick);
            case STRETCHED -> renderStretched(buffer, camera, partialTick);
            case FLAT -> renderFlat(buffer, camera, partialTick);
        }
    }

    /** A streak along the velocity, widened toward the camera: sparks read as fast. */
    private void renderStretched(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 cam = camera.getPosition();
        Vector3f center = new Vector3f((float) (Mth.lerp(partialTick, xo, x) - cam.x),
                (float) (Mth.lerp(partialTick, yo, y) - cam.y), (float) (Mth.lerp(partialTick, zo, z) - cam.z));
        Vector3f along = new Vector3f((float) xd, (float) yd, (float) zd);
        float speed = along.length();
        if (speed < 1e-4f) {
            super.render(buffer, camera, partialTick);
            return;
        }
        along.mul(1 / speed);
        Vector3f side = new Vector3f(along).cross(center);
        if (side.lengthSquared() < 1e-8f) side.set(0, 1, 0);
        side.normalize(getQuadSize(partialTick) * 0.35f);
        along.mul(getQuadSize(partialTick) * (1 + speed * behavior.stretch() * 10));
        quad(buffer, center, along, side, partialTick);
    }

    /** A ring lying in the plane facing {@link #facing}, drawn from both sides. */
    private void renderFlat(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 cam = camera.getPosition();
        Vector3f center = new Vector3f((float) (Mth.lerp(partialTick, xo, x) - cam.x),
                (float) (Mth.lerp(partialTick, yo, y) - cam.y), (float) (Mth.lerp(partialTick, zo, z) - cam.z));
        Vector3f helper = Math.abs(facing.y) < 0.99f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f u = new Vector3f(facing).cross(helper).normalize();
        Vector3f w = new Vector3f(facing).cross(u);
        float size = getQuadSize(partialTick);
        Quaternionf spin = new Quaternionf().fromAxisAngleRad(facing, Mth.lerp(partialTick, oRoll, roll));
        spin.transform(u.mul(size));
        spin.transform(w.mul(size));
        quad(buffer, center, u, w, partialTick);
        quad(buffer, center, w, u, partialTick); // reversed winding: visible from behind too
    }

    private void quad(VertexConsumer buffer, Vector3f c, Vector3f a, Vector3f b, float partialTick) {
        int light = getLightColor(partialTick);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        vertex(buffer, c.x - a.x - b.x, c.y - a.y - b.y, c.z - a.z - b.z, u1, v1, light);
        vertex(buffer, c.x - a.x + b.x, c.y - a.y + b.y, c.z - a.z + b.z, u1, v0, light);
        vertex(buffer, c.x + a.x + b.x, c.y + a.y + b.y, c.z + a.z + b.z, u0, v0, light);
        vertex(buffer, c.x + a.x - b.x, c.y + a.y - b.y, c.z + a.z - b.z, u0, v1, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.vertex(x, y, z).uv(u, v).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
    }

    /** One provider per particle type, each with its own behaviour. */
    public record Provider(SpriteSet sprites, Behavior behavior) implements ParticleProvider<BastionParticles.Options> {
        @Override
        public Particle createParticle(BastionParticles.Options options, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new VfxParticle(level, x, y, z, dx, dy, dz, options, sprites, behavior);
        }
    }
}
