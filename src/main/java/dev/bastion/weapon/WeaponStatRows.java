package dev.bastion.weapon;

import dev.bastion.registry.BastionWeaponTypes;

import java.util.List;
import java.util.Locale;

/**
 * The stat table of a turret weapon: the turret GUI's Info tab and JEI's weapon pages draw the same rows. Keys name the
 * label (gui.bastion.info.<key>); values are ready to draw.
 */
public final class WeaponStatRows {
    public record Row(String key, String value) {
    }

    private WeaponStatRows() {
    }

    public static List<Row> left(WeaponData data, StatSheet stats) {
        return List.of(
                new Row("damage", fmt(stats.damage())),
                new Row("fire_rate", fmt(20f / stats.fireInterval()) + "/s"),
                new Row("range", fmt(stats.range())),
                new Row("turn_speed", fmt(stats.turnSpeed() * 20) + "°/s"),
                // The Laser Rifle passes through every valid target on its line.
                new Row("pierce", data.type().equals(BastionWeaponTypes.LASER_RIFLE.getId()) ? "∞" : String.valueOf(stats.pierce())),
                new Row("spread", fmt(stats.spread()) + "°"),
                new Row("ammo_save", Math.round(stats.ammoSaveChance() * 100) + "%"));
    }

    public static List<Row> right(WeaponData data, StatSheet stats, float maxHealth, float regenPerSecond) {
        if (data.params().containsKey("energy_per_shot")) { // energy weapons: capacitor, no heat
            return List.of(
                    new Row("energy_per_shot", fe(data.param("energy_per_shot")) + " FE"),
                    new Row("energy_capacity", fe(data.param("energy_capacity")) + " FE"),
                    new Row("max_input", fe(data.param("max_input")) + " FE/t"),
                    new Row("max_hp", fmt(maxHealth)),
                    new Row("regen", fmt(regenPerSecond) + "/s"),
                    // Tesla: bolts per discharge; the Railgun also takes a slug per shot.
                    data.params().containsKey("bolts") ? new Row("bolts", String.valueOf(Math.round(data.param("bolts"))))
                            : new Row("ammo", String.valueOf(data.ammoPerShot())));
        }
        return List.of(
                new Row("heat_per_shot", fmt(stats.heatPerShot())),
                new Row("max_heat", fmt(stats.maxHeat())),
                new Row("cooling", fmt(stats.heatDissipationPerTick() * 20) + "/s"),
                new Row("max_hp", fmt(maxHealth)),
                new Row("regen", fmt(regenPerSecond) + "/s"),
                // Weapons that load ahead (Missile Launcher) show their per-missile reload instead of ammo per shot.
                data.params().containsKey("reload_ticks") ? new Row("reload", fmt(data.param("reload_ticks") / stats.reloadRate() / 20f) + "s")
                        : new Row("ammo", String.valueOf(data.ammoPerShot())));
    }

    /** 7 -> "7", 1.666 -> "1.7". */
    public static String fmt(float value) {
        return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** 3000 -> "3k", 1500 -> "1.5k", 800 -> "800": energy figures that fit the info columns. */
    public static String fe(float value) {
        return value >= 1000 ? fmt(value / 1000f) + "k" : fmt(value);
    }
}
