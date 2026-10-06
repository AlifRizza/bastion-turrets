package dev.bastion.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.Map;

/**
 * Balance numbers of one weapon, loaded from data/<ns>/turret_weapons/<id>.json (PLAN 2, 7). The JSON
 * is flat; aim and heat are grouped here only for readability. Angles are degrees, times ticks unless
 * named seconds, distances blocks. {@code params} holds numbers one weapon type alone understands.
 */
public record WeaponData(
        ResourceLocation type,
        TagKey<Item> ammo,
        int energyColor,
        float range,
        float fireInterval,
        float damage,
        float spread,
        float ammoPerShot,
        Aim aim,
        Heat heat,
        Map<String, Float> params) {

    /**
     * Rotation and muzzle geometry. pivotHeight is above the base top; muzzleLength is pivot to muzzle.
     * min_pitch == max_pitch makes a fixed-elevation weapon that only turns left and right (Missile Launcher).
     */
    public record Aim(float turnSpeed, float tolerance, int lockTicks, float minPitch, float maxPitch,
                      float pivotHeight, float muzzleLength) {
        public boolean fixedPitch() {
            return minPitch == maxPitch;
        }

        static final MapCodec<Aim> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("turn_speed").forGetter(Aim::turnSpeed),
                Codec.FLOAT.optionalFieldOf("aim_tolerance", 2f).forGetter(Aim::tolerance),
                Codec.INT.optionalFieldOf("aim_lock_ticks", 2).forGetter(Aim::lockTicks),
                Codec.FLOAT.optionalFieldOf("min_pitch", -30f).forGetter(Aim::minPitch),
                Codec.FLOAT.optionalFieldOf("max_pitch", 60f).forGetter(Aim::maxPitch),
                Codec.FLOAT.fieldOf("pivot_height").forGetter(Aim::pivotHeight),
                Codec.FLOAT.fieldOf("muzzle_length").forGetter(Aim::muzzleLength)
        ).apply(i, Aim::new));
    }

    public record Heat(float perShot, float dissipationPerSecond, float max, int overheatTicks) {
        static final MapCodec<Heat> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.optionalFieldOf("heat_per_shot", 0f).forGetter(Heat::perShot),
                Codec.FLOAT.optionalFieldOf("heat_dissipation_per_second", 0f).forGetter(Heat::dissipationPerSecond),
                Codec.FLOAT.optionalFieldOf("max_heat", 100f).forGetter(Heat::max),
                Codec.INT.optionalFieldOf("overheat_ticks", 60).forGetter(Heat::overheatTicks)
        ).apply(i, Heat::new));
    }

    private static final Codec<Integer> COLOR = Codec.STRING.xmap(
            s -> (int) Long.parseLong(s.startsWith("#") ? s.substring(1) : s, 16), c -> String.format("#%06X", c & 0xFFFFFF));

    public static final Codec<WeaponData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(WeaponData::type),
            TagKey.codec(Registries.ITEM).fieldOf("ammo").forGetter(WeaponData::ammo),
            COLOR.optionalFieldOf("energy_color", 0xFFFFFF).forGetter(WeaponData::energyColor),
            Codec.FLOAT.fieldOf("range").forGetter(WeaponData::range),
            Codec.FLOAT.fieldOf("fire_interval").forGetter(WeaponData::fireInterval),
            Codec.FLOAT.fieldOf("damage").forGetter(WeaponData::damage),
            Codec.FLOAT.optionalFieldOf("spread", 0f).forGetter(WeaponData::spread),
            Codec.FLOAT.optionalFieldOf("ammo_per_shot", 1f).forGetter(WeaponData::ammoPerShot),
            Aim.CODEC.forGetter(WeaponData::aim),
            Heat.CODEC.forGetter(WeaponData::heat),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("params", Map.of()).forGetter(WeaponData::params)
    ).apply(i, WeaponData::new));

    /** A weapon-specific number; fails loudly when the JSON lacks it, so a typo never silently becomes 0. */
    public float param(String key) {
        Float value = params.get(key);
        if (value == null) throw new IllegalStateException("turret weapon JSON is missing params." + key);
        return value;
    }
}
