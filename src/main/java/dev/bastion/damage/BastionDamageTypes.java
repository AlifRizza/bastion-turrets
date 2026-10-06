package dev.bastion.damage;

import dev.bastion.Bastion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/** Damage types defined in data/bastion/damage_type. */
public final class BastionDamageTypes {
    public static final ResourceKey<DamageType> TURRET_SHOT = ResourceKey.create(Registries.DAMAGE_TYPE, Bastion.id("turret_shot"));
    public static final ResourceKey<DamageType> TURRET_LASER = ResourceKey.create(Registries.DAMAGE_TYPE, Bastion.id("turret_laser"));
    public static final ResourceKey<DamageType> TURRET_SHOCK = ResourceKey.create(Registries.DAMAGE_TYPE, Bastion.id("turret_shock"));
    /** Flamethrower hits and the burning they leave; both in minecraft:is_fire, so fire-immune mobs and Fire Resistance ignore them. */
    public static final ResourceKey<DamageType> TURRET_FLAME = ResourceKey.create(Registries.DAMAGE_TYPE, Bastion.id("turret_flame"));
    public static final ResourceKey<DamageType> TURRET_BURN = ResourceKey.create(Registries.DAMAGE_TYPE, Bastion.id("turret_burn"));

    private BastionDamageTypes() {
    }

    public static DamageSource turretShot(Level level) {
        return of(level, TURRET_SHOT);
    }

    public static DamageSource of(Level level, ResourceKey<DamageType> type) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type));
    }
}
