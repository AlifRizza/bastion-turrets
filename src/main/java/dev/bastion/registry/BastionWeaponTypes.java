package dev.bastion.registry;

import dev.bastion.Bastion;
import dev.bastion.weapon.WeaponType;
import dev.bastion.weapon.types.GunWeapon;
import dev.bastion.weapon.types.MachineGunWeapon;
import dev.bastion.weapon.types.FlamethrowerWeapon;
import dev.bastion.weapon.types.LaserRifleWeapon;
import dev.bastion.weapon.types.MissileLauncherWeapon;
import dev.bastion.weapon.types.MortarWeapon;
import dev.bastion.weapon.types.RailgunWeapon;
import dev.bastion.weapon.types.RepulsorDomeWeapon;
import dev.bastion.weapon.types.RepulsorWeapon;
import dev.bastion.weapon.types.RocketLauncherWeapon;
import dev.bastion.weapon.types.ShotgunWeapon;
import dev.bastion.weapon.types.SniperWeapon;
import dev.bastion.weapon.types.TeslaWeapon;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/** Custom registry {@code bastion:weapon_type}; weapon JSON (PLAN 2) refers to entries by id. */
public final class BastionWeaponTypes {
    public static final DeferredRegister<WeaponType> REGISTER = DeferredRegister.create(Bastion.id("weapon_type"), Bastion.MOD_ID);
    public static final Supplier<IForgeRegistry<WeaponType>> REGISTRY = REGISTER.makeRegistry(RegistryBuilder::new);

    public static final RegistryObject<WeaponType> GUN = REGISTER.register("gun", GunWeapon::new);
    public static final RegistryObject<WeaponType> MACHINE_GUN = REGISTER.register("machine_gun", MachineGunWeapon::new);
    public static final RegistryObject<WeaponType> SHOTGUN = REGISTER.register("shotgun", ShotgunWeapon::new);
    public static final RegistryObject<WeaponType> SNIPER = REGISTER.register("sniper", SniperWeapon::new);
    public static final RegistryObject<WeaponType> ROCKET_LAUNCHER = REGISTER.register("rocket_launcher", RocketLauncherWeapon::new);
    public static final RegistryObject<WeaponType> MISSILE_LAUNCHER = REGISTER.register("missile_launcher", MissileLauncherWeapon::new);
    public static final RegistryObject<WeaponType> TESLA = REGISTER.register("tesla", TeslaWeapon::new);
    public static final RegistryObject<WeaponType> FLAMETHROWER = REGISTER.register("flamethrower", FlamethrowerWeapon::new);
    public static final RegistryObject<WeaponType> LASER_RIFLE = REGISTER.register("laser_rifle", LaserRifleWeapon::new);
    public static final RegistryObject<WeaponType> RAILGUN = REGISTER.register("railgun", RailgunWeapon::new);
    public static final RegistryObject<WeaponType> MORTAR = REGISTER.register("mortar", MortarWeapon::new);
    public static final RegistryObject<WeaponType> REPULSOR = REGISTER.register("repulsor", RepulsorWeapon::new);
    public static final RegistryObject<WeaponType> REPULSOR_DOME = REGISTER.register("repulsor_dome", RepulsorDomeWeapon::new);
}
