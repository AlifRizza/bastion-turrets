package dev.bastion.modifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.bastion.weapon.WeaponData;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What one modifier changes (PLAN 4.6), loaded from data/<ns>/turret_modifiers/<item>.json. Fractions are
 * added up across modifiers: 0.25 range = +25%. {@code pierce} adds targets a shot passes through,
 * {@code ammoSaveChance} is the chance a shot costs no ammo, {@code maxHealth} scales base HP, {@code spread} scales the
 * weapon's spread (-0.4 = 40% tighter), {@code reloadSpeed} is a rate like fire_rate for weapons that reload (Autoloader).
 * {@code weapons} lists the weapon ids a weapon-specific module fits (Choke Module: Shotgun only); empty = every weapon.
 */
public record ModifierEffect(float range, float fireRate, float damage, float heatPerShot, float heatDissipation,
                             float turnSpeed, float maxHealth, int pierce, float ammoSaveChance, float leadAccuracy,
                             float spread, float reloadSpeed, List<ResourceLocation> weapons) {
    public static final ModifierEffect NONE = new ModifierEffect(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of());

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
            Codec.FLOAT.optionalFieldOf("lead_accuracy", 0f).forGetter(ModifierEffect::leadAccuracy),
            Codec.FLOAT.optionalFieldOf("spread", 0f).forGetter(ModifierEffect::spread),
            Codec.FLOAT.optionalFieldOf("reload_speed", 0f).forGetter(ModifierEffect::reloadSpeed),
            ResourceLocation.CODEC.listOf().optionalFieldOf("weapons", List.of()).forGetter(ModifierEffect::weapons)
    ).apply(i, ModifierEffect::new));

    /** Whether this module works with the weapon (null = no weapon mounted: nothing to refuse yet). */
    public boolean fits(@Nullable ResourceLocation weapon) {
        return weapons.isEmpty() || weapon == null || weapons.contains(weapon);
    }

    /**
     * Whether this module changes anything on that weapon (the Shift tooltip lists these, user 2026-10-07). It may fit
     * where it does nothing: a Coolant Loop goes on a Tesla Coil, which has no heat to cool.
     */
    public boolean affects(ResourceLocation weapon, WeaponData w) {
        if (!fits(weapon)) return false;
        return range != 0 || fireRate != 0 || maxHealth != 0 // every turret has a range, a fire interval and a base
                || damage != 0 && w.damage() > 0
                || (heatPerShot != 0 || heatDissipation != 0) && w.heat().perShot() > 0 // max_heat defaults to 100: heating is perShot
                || turnSpeed != 0 && w.aim().turnSpeed() > 0
                || leadAccuracy != 0 && w.params().containsKey("lead_accuracy")
                // ponytail: bullets and slugs are what read pierce (Bullets.fire); a new piercing weapon adds its key here
                || pierce != 0 && (w.params().containsKey("bullet_speed") || w.params().containsKey("slug_speed"))
                || ammoSaveChance != 0 && w.ammoPerShot() > 0
                || spread != 0 && w.spread() > 0
                || reloadSpeed != 0;
    }

    /** Sum of two effects; ammo save chances combine as independent rolls. */
    public ModifierEffect plus(ModifierEffect o) {
        return new ModifierEffect(range + o.range, fireRate + o.fireRate, damage + o.damage, heatPerShot + o.heatPerShot,
                heatDissipation + o.heatDissipation, turnSpeed + o.turnSpeed, maxHealth + o.maxHealth, pierce + o.pierce,
                1 - (1 - ammoSaveChance) * (1 - o.ammoSaveChance), leadAccuracy + o.leadAccuracy, spread + o.spread,
                reloadSpeed + o.reloadSpeed, List.of());
    }
}
