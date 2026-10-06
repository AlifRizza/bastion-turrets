package dev.bastion.weapon;

import dev.bastion.config.BastionConfig;
import dev.bastion.modifier.ModifierEffect;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretTier;

/**
 * Final numbers a turret fires with: weapon data x tier x modifiers (PLAN 4.6). Rates scale intervals down
 * ({@code fire_rate} +20% = interval / 1.2); everything else scales up by the summed fraction. {@code reloadRate} divides
 * reload times (Missile Launcher tubes), and the fire interval of a weapon that fires as it reloads (Rocket Launcher).
 */
public record StatSheet(
        WeaponData data,
        float range,
        float fireInterval,
        float damage,
        float spread,
        float ammoPerShot,
        float turnSpeed,
        float heatPerShot,
        float heatDissipationPerTick,
        float maxHeat,
        int pierce,
        float ammoSaveChance,
        float healthMultiplier,
        float reloadRate) {

    public static StatSheet of(WeaponData data, TurretTier tier) {
        return of(data, tier, ModifierEffect.NONE);
    }

    public static StatSheet of(WeaponData data, TurretTier tier, ModifierEffect m) {
        float reloadRate = Math.max(0.1f, 1 + m.reloadSpeed());
        WeaponType type = BastionWeaponTypes.REGISTRY.get().getValue(data.type());
        return new StatSheet(data,
                data.range() * (1 + m.range()),
                data.fireInterval() / Math.max(0.1f, 1 + m.fireRate()) / (type != null && type.firesAsItReloads() ? reloadRate : 1),
                data.damage() * (1 + m.damage()),
                data.spread() * Math.max(0, 1 + m.spread()),
                data.ammoPerShot(),
                data.aim().turnSpeed() * BastionConfig.turnSpeedMultiplier(tier) * (1 + m.turnSpeed()),
                data.heat().perShot() * (1 + m.heatPerShot()),
                data.heat().dissipationPerSecond() / 20f * (1 + m.heatDissipation()),
                data.heat().max(),
                m.pierce(),
                m.ammoSaveChance(),
                Math.max(0.1f, 1 + m.maxHealth()),
                reloadRate);
    }
}
