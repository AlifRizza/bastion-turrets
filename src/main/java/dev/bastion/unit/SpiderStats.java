package dev.bastion.unit;

import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;

/** A Brood spider's numbers (the Brood Cannon's JSON params, taken at launch). */
public record SpiderStats(float health, float speed, float bite, int biteInterval, float sight, float deathDamage, float deathRadius,
                          int selfDestructTicks, float leapRange, int leapTicks, int leapCooldown) {
    /** From the Brood Cannon's stats: damage modules scale the bite and the death burst. */
    public static SpiderStats of(StatSheet stats) {
        WeaponData data = stats.data();
        return new SpiderStats(data.param("spider_health"), data.param("spider_speed"), stats.damage(), Math.round(data.param("bite_interval")),
                data.param("spider_sight"), data.param("death_damage") * stats.damageScale(), data.param("death_radius"),
                Math.round(data.param("self_destruct_ticks")), data.param("leap_range"), Math.round(data.param("leap_ticks")),
                Math.round(data.param("leap_cooldown")));
    }
}
