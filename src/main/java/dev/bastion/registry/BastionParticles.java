package dev.bastion.registry;

import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.bastion.Bastion;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Locale;

/**
 * Bastion particle types (PLAN 5.4). Every type shares {@link Options}: ARGB colour, scale, lifetime
 * and a curve, so one effect preset can recolour and resize any particle.
 */
public final class BastionParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Bastion.MOD_ID);

    public static final RegistryObject<ParticleType<Options>> SPARK = register("spark");
    public static final RegistryObject<ParticleType<Options>> EMBER = register("ember");
    public static final RegistryObject<ParticleType<Options>> SMOKE_PUFF = register("smoke_puff");
    public static final RegistryObject<ParticleType<Options>> HEAT_HAZE = register("heat_haze");
    public static final RegistryObject<ParticleType<Options>> SHOCKWAVE_RING = register("shockwave_ring");
    public static final RegistryObject<ParticleType<Options>> MUZZLE_FLASH = register("muzzle_flash");
    public static final RegistryObject<ParticleType<Options>> DEBRIS = register("debris");
    public static final RegistryObject<ParticleType<Options>> MUZZLE_RING = register("muzzle_ring");
    public static final RegistryObject<ParticleType<Options>> IMPACT_SPLASH = register("impact_splash");
    public static final RegistryObject<ParticleType<Options>> SMOKE_WISP = register("smoke_wisp");
    // Rocket explosions and exhaust
    public static final RegistryObject<ParticleType<Options>> FIREBALL = register("fireball");
    public static final RegistryObject<ParticleType<Options>> BLAST_SMOKE = register("blast_smoke");
    public static final RegistryObject<ParticleType<Options>> FLAME = register("flame");
    public static final RegistryObject<ParticleType<Options>> DUST_RING = register("dust_ring");
    // Flamethrower: billowing fire that piles up against walls instead of passing through them
    public static final RegistryObject<ParticleType<Options>> FLAME_JET = register("flame_jet");

    private static RegistryObject<ParticleType<Options>> register(String name) {
        return REGISTER.register(name, () -> new ParticleType<>(false, Options.DESERIALIZER) {
            private final Codec<Options> codec = Options.codec(this);

            @Override
            public Codec<Options> codec() {
                return codec;
            }
        });
    }

    /** How size and opacity evolve over a particle's life (t = 0..1). */
    public enum Curve {
        /** Grows quickly, fades smoothly. Smoke, rings, splashes. */
        EASE_OUT,
        /** Constant size, linear fade. Sparks, debris. */
        LINEAR,
        /** Swells then shrinks: flashes. */
        PULSE,
        /** Flickers while fading: embers. */
        FLICKER,
        /** Swells and stays solid for 60% of its life, then thins out quickly: fireballs and blast smoke. */
        BILLOW,
        /** Starts small and widens a lot as it flies, solid then thinning like BILLOW: a flamethrower's stream. */
        FLARE;

        public float scale(float t) {
            return switch (this) {
                case EASE_OUT -> 0.35f + 0.65f * (1 - (1 - t) * (1 - t) * (1 - t));
                case LINEAR, FLICKER -> 1f;
                case PULSE -> Mth.sin(Mth.PI * Math.min(1f, t * 1.4f + 0.15f));
                case BILLOW -> 0.45f + 0.55f * (1 - (1 - t) * (1 - t));
                case FLARE -> 0.18f + 0.82f * (1 - (1 - t) * (1 - t));
            };
        }

        public float alpha(float t, float age) {
            return switch (this) {
                case EASE_OUT -> (1 - t) * (1 - t) * Math.min(1f, t * 8f + 0.25f);
                case LINEAR -> 1 - t;
                case PULSE -> t < 0.34f ? 1f : (float) Math.pow(1 - (t - 0.34f) / 0.66f, 1.5); // TaCZ-style hold then fall
                case FLICKER -> (1 - t) * (0.65f + 0.35f * Mth.sin(age * 1.7f));
                case BILLOW, FLARE -> Math.min(1f, t * 10f + 0.3f) * (t < 0.6f ? 1f : (float) Math.pow(1 - (t - 0.6f) / 0.4f, 1.5));
            };
        }
    }

    public record Options(ParticleType<Options> type, int argb, float scale, int lifetime, Curve curve) implements ParticleOptions {
        static Codec<Options> codec(ParticleType<Options> type) {
            return RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("argb").forGetter(Options::argb),
                    Codec.FLOAT.fieldOf("scale").forGetter(Options::scale),
                    Codec.INT.fieldOf("lifetime").forGetter(Options::lifetime),
                    Codec.STRING.xmap(s -> Curve.valueOf(s.toUpperCase(Locale.ROOT)), c -> c.name().toLowerCase(Locale.ROOT))
                            .fieldOf("curve").forGetter(Options::curve)
            ).apply(i, (argb, scale, lifetime, curve) -> new Options(type, argb, scale, lifetime, curve)));
        }

        @SuppressWarnings("deprecation")
        static final Deserializer<Options> DESERIALIZER = new Deserializer<>() {
            @Override
            public Options fromCommand(ParticleType<Options> type, StringReader reader) {
                return new Options(type, 0xFFFFFFFF, 1f, 10, Curve.EASE_OUT); // /particle bastion:x with defaults
            }

            @Override
            public Options fromNetwork(ParticleType<Options> type, FriendlyByteBuf buf) {
                return new Options(type, buf.readInt(), buf.readFloat(), buf.readVarInt(), buf.readEnum(Curve.class));
            }
        };

        @Override
        public ParticleType<?> getType() {
            return type;
        }

        @Override
        public void writeToNetwork(FriendlyByteBuf buf) {
            buf.writeInt(argb);
            buf.writeFloat(scale);
            buf.writeVarInt(lifetime);
            buf.writeEnum(curve);
        }

        @Override
        public String writeToString() {
            return ForgeRegistries.PARTICLE_TYPES.getKey(type) + " " + Integer.toHexString(argb) + " " + scale + " " + lifetime + " " + curve;
        }
    }
}
