package dev.bastion.client.vfx;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;

/**
 * Client look and sound of each weapon type (PLAN 7.1-7.3): presets, tracer shape, recoil kick, casing and
 * sounds. Colours come from the weapon JSON's energy_color.
 *
 * @param tracerSpeed blocks per tick of the visual bullet; the tracer takes 2-4 ticks to arrive (PLAN 5.3)
 * @param recoilKick  impulse on the pitch recoil spring, degrees per second
 * @param casing      eject a casing per shot from eject_0
 * @param pump        play the pump clip after each shot and eject a big shell mid-pump (Shotgun)
 */
public record WeaponFx(VfxPreset muzzle, VfxPreset impact, VfxPreset lockImpact, float tracerWidth, float tracerSpeed,
                       float recoilKick, boolean casing, boolean pump, RegistryObject<SoundEvent> fire, RegistryObject<SoundEvent> fireTail) {
    public static final WeaponFx GUN = new WeaponFx(VfxPresets.GUN_MUZZLE, VfxPresets.GUN_IMPACT, VfxPresets.GUN_LOCK_IMPACT,
            0.075f, 12f, 55f, true, false, BastionSounds.GUN_FIRE, BastionSounds.GUN_FIRE_TAIL);
    public static final WeaponFx MACHINE_GUN = new WeaponFx(VfxPresets.MG_MUZZLE, VfxPresets.MG_IMPACT, VfxPresets.MG_IMPACT,
            0.05f, 10f, 14f, true, false, BastionSounds.MACHINE_GUN_FIRE, BastionSounds.MACHINE_GUN_FIRE_TAIL);
    public static final WeaponFx SHOTGUN = new WeaponFx(VfxPresets.SG_MUZZLE, VfxPresets.SG_PELLET_IMPACT, VfxPresets.SG_PELLET_IMPACT,
            0.04f, 6f, 120f, false, true, BastionSounds.SHOTGUN_FIRE, BastionSounds.SHOTGUN_FIRE_TAIL);

    public static final WeaponFx SNIPER = new WeaponFx(VfxPresets.SNIPER_MUZZLE, VfxPresets.SNIPER_IMPACT, VfxPresets.SNIPER_IMPACT,
            0.11f, 32f, 95f, true, false, BastionSounds.SNIPER_FIRE, BastionSounds.SNIPER_FIRE_TAIL);
    /** No tracer: the rocket is a real entity with its own renderer and trail (RocketEffects). */
    public static final WeaponFx ROCKET_LAUNCHER = new WeaponFx(VfxPresets.ROCKET_LAUNCH, v -> {}, v -> {},
            0, 1f, 70f, false, false, BastionSounds.ROCKET_LAUNCH, BastionSounds.ROCKET_LAUNCH_TAIL);

    public static final WeaponFx MISSILE_LAUNCHER = new WeaponFx(VfxPresets.MISSILE_LAUNCH, v -> {}, v -> {},
            0, 1f, 12f, false, false, BastionSounds.MISSILE_LAUNCH, BastionSounds.ROCKET_LAUNCH_TAIL);

    /** Lightning bolts instead of tracers (TeslaEffects); a coil has no recoil. */
    public static final WeaponFx TESLA = new WeaponFx(VfxPresets.TESLA_DISCHARGE, VfxPresets.TESLA_IMPACT, VfxPresets.TESLA_IMPACT,
            0.07f, 1f, 0f, false, false, BastionSounds.TESLA_ZAP, BastionSounds.TESLA_ZAP_TAIL);
    /** A flame stream instead of tracers (FlameEffects); the roar is a loop (ClientTurret), the fire sound starts a burst. */
    public static final WeaponFx FLAMETHROWER = new WeaponFx(VfxPresets.FLAME_STREAM, VfxPresets.FLAME_SPLASH, VfxPresets.FLAME_SPLASH,
            0, 1f, 2f, false, false, BastionSounds.FLAMETHROWER_IGNITE, BastionSounds.FLAMETHROWER_IGNITE);

    /** A beam instead of a tracer (LaserEffects); a heavy kick. */
    public static final WeaponFx LASER_RIFLE = new WeaponFx(VfxPresets.LASER_MUZZLE, VfxPresets.LASER_IMPACT, VfxPresets.LASER_IMPACT,
            0.13f, 1f, 80f, false, false, BastionSounds.LASER_FIRE, BastionSounds.LASER_FIRE_TAIL);

    /** A slug, rings and an ion trail (RailgunEffects); the heaviest kick of all. */
    public static final WeaponFx RAILGUN = new WeaponFx(VfxPresets.RAIL_MUZZLE, VfxPresets.RAIL_IMPACT, VfxPresets.RAIL_IMPACT,
            0.2f, 1f, 140f, false, false, BastionSounds.RAILGUN_FIRE, BastionSounds.RAILGUN_FIRE_TAIL);

    /** No tracer: the shell is a real entity (RocketEffects); the barrel slams down. */
    public static final WeaponFx MORTAR = new WeaponFx(VfxPresets.MORTAR_LAUNCH, v -> {}, v -> {},
            0, 1f, 40f, false, false, BastionSounds.MORTAR_FIRE, BastionSounds.MORTAR_FIRE_TAIL);

    private static final Map<ResourceLocation, WeaponFx> BY_TYPE = Map.ofEntries(
            Map.entry(Bastion.id("gun"), GUN), Map.entry(Bastion.id("machine_gun"), MACHINE_GUN), Map.entry(Bastion.id("shotgun"), SHOTGUN),
            Map.entry(Bastion.id("sniper"), SNIPER), Map.entry(Bastion.id("rocket_launcher"), ROCKET_LAUNCHER),
            Map.entry(Bastion.id("missile_launcher"), MISSILE_LAUNCHER), Map.entry(Bastion.id("tesla"), TESLA),
            Map.entry(Bastion.id("flamethrower"), FLAMETHROWER), Map.entry(Bastion.id("laser_rifle"), LASER_RIFLE),
            Map.entry(Bastion.id("railgun"), RAILGUN), Map.entry(Bastion.id("mortar"), MORTAR));

    public static WeaponFx of(ResourceLocation weaponType) {
        return BY_TYPE.getOrDefault(weaponType, GUN);
    }
}
