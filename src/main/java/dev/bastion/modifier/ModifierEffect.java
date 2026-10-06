package dev.bastion.modifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What one modifier changes (PLAN 4.6), loaded from data/<ns>/turret_modifiers/<item>.json. Fractions are
 * added up across modifiers: 0.25 range = +25%. {@code pierce} adds targets a shot passes through,
 * {@code ammoSaveChance} is the chance a shot costs no ammo, {@code maxHealth} scales base HP.
 */
public record ModifierEffect(float range, float fireRate, float damage, float heatPerShot, float heatDissipation,
                             float turnSpeed, float maxHealth, int pierce, float ammoSaveChance, float leadAccuracy) {
    public static final ModifierEffect NONE = new ModifierEffect(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public static final Codec<ModifierEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("range", 0f).forGetter(ModifierEffect::range),
            Codec.FLOAT.optionalFieldOf("fire_rate", 0f).forGetter(ModifierEffect::fireRate),
            Codec.FLOAT.optionalFieldOf("damage", 0f).forGetter(ModifierEffect::damage),
            Codec.FLOAT.optionalFieldOf("heat_per_shot", 0f).forGetter(ModifierEffect::heatPerShot),
            Codec.FLOAT.optionalFieldOf("heat_dissipation", 0f).forGetter(ModifierEffect::heatDissipation),
            Codec.FLOAT.optionalFieldOf("turn_speed", 0f).forGetter(ModifierEffect::turnSpeed),
            Codec.FLOAT.optionalFieldOf("max_health", 0f).forGetter(ModifierEffect::maxHealth),
            Codec.INT.optionalFieldOf("pierce", 0).forGetter(ModifierEffect::pierce),
            Codec.FLOAT.optionalFieldOf("ammo_save_chance", 0f).forGetter(ModifierEffect::ammoSaveChance),
            Codec.FLOAT.optionalFieldOf("lead_accuracy", 0f).forGetter(ModifierEffect::leadAccuracy)
    ).apply(i, ModifierEffect::new));

    /** Sum of two effects; ammo save chances combine as independent rolls. */
    public ModifierEffect plus(ModifierEffect o) {
        return new ModifierEffect(range + o.range, fireRate + o.fireRate, damage + o.damage, heatPerShot + o.heatPerShot,
                heatDissipation + o.heatDissipation, turnSpeed + o.turnSpeed, maxHealth + o.maxHealth, pierce + o.pierce,
                1 - (1 - ammoSaveChance) * (1 - o.ammoSaveChance), leadAccuracy + o.leadAccuracy);
    }
}
