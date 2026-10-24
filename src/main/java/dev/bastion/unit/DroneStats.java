package dev.bastion.unit;

import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;

/** A Hive drone's numbers (the Hive Carrier's JSON params, taken at launch). */
public record DroneStats(float health, float bullet, int ammo, int shotInterval, float shotRange, float patrolRadius, float kamikazeHealth,
                         float kamikazeDamage, float kamikazeRadius, float speed, int idleTicks) {
    /** From the Hive Carrier's stats: damage modules scale the bullets and the kamikaze. */
    public static DroneStats of(StatSheet stats) {
        WeaponData data = stats.data();
        return new DroneStats(data.param("drone_health"), stats.damage(), Math.round(data.param("drone_ammo")), Math.round(data.param("shot_interval")),
                data.param("shot_range"), data.param("patrol_radius"), data.param("kamikaze_health"), data.param("kamikaze_damage") * stats.damageScale(),
                data.param("kamikaze_radius"), data.param("drone_speed"), Math.round(data.param("idle_ticks")));
    }
}
