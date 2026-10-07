package dev.bastion.registry;

import dev.bastion.Bastion;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Every Bastion sound (PLAN 5.7). Variants and per-file pitch live in assets/bastion/sounds.json; the files
 * are placeholders from tools/gen_sounds.py until release.
 */
public final class BastionSounds {
    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Bastion.MOD_ID);

    public static final RegistryObject<SoundEvent> DEBUG_PING = register("debug.ping");

    public static final RegistryObject<SoundEvent> TURRET_DEPLOY = register("turret.deploy");
    public static final RegistryObject<SoundEvent> TURRET_IDLE_HUM = register("turret.idle_hum");
    public static final RegistryObject<SoundEvent> TURRET_SERVO = register("turret.servo");
    public static final RegistryObject<SoundEvent> IMPACT_KINETIC = register("impact.kinetic");
    public static final RegistryObject<SoundEvent> TURRET_DESTROYED = register("turret.destroyed");
    public static final RegistryObject<SoundEvent> TURRET_TIER_UP = register("turret.tier_up");
    public static final RegistryObject<SoundEvent> TURRET_REPAIR = register("turret.repair");

    public static final RegistryObject<SoundEvent> GUN_FIRE = register("gun.fire");
    public static final RegistryObject<SoundEvent> GUN_FIRE_TAIL = register("gun.fire_tail");
    public static final RegistryObject<SoundEvent> GUN_LOCK = register("gun.lock");

    public static final RegistryObject<SoundEvent> MACHINE_GUN_FIRE = register("machine_gun.fire");
    public static final RegistryObject<SoundEvent> MACHINE_GUN_FIRE_TAIL = register("machine_gun.fire_tail");
    public static final RegistryObject<SoundEvent> MACHINE_GUN_SPIN_UP = register("machine_gun.spin_up");
    public static final RegistryObject<SoundEvent> MACHINE_GUN_SPIN_DOWN = register("machine_gun.spin_down");
    public static final RegistryObject<SoundEvent> MACHINE_GUN_SPIN_LOOP = register("machine_gun.spin_loop");
    public static final RegistryObject<SoundEvent> MACHINE_GUN_OVERHEAT = register("machine_gun.overheat");

    public static final RegistryObject<SoundEvent> SNIPER_CHARGE = register("sniper.charge");
    public static final RegistryObject<SoundEvent> SNIPER_FIRE = register("sniper.fire");
    public static final RegistryObject<SoundEvent> SNIPER_FIRE_TAIL = register("sniper.fire_tail");

    public static final RegistryObject<SoundEvent> ROCKET_LAUNCH = register("rocket.launch");
    public static final RegistryObject<SoundEvent> ROCKET_LAUNCH_TAIL = register("rocket.launch_tail");
    public static final RegistryObject<SoundEvent> ROCKET_FLY = register("rocket.fly");
    public static final RegistryObject<SoundEvent> ROCKET_EXPLODE = register("rocket.explode");

    public static final RegistryObject<SoundEvent> MISSILE_LAUNCH = register("missile.launch");
    public static final RegistryObject<SoundEvent> MISSILE_RELOAD = register("missile.reload");
    public static final RegistryObject<SoundEvent> MISSILE_EXPLODE = register("missile.explode");

    public static final RegistryObject<SoundEvent> TESLA_CHARGE = register("tesla.charge");
    public static final RegistryObject<SoundEvent> TESLA_ZAP = register("tesla.zap");
    public static final RegistryObject<SoundEvent> TESLA_ZAP_TAIL = register("tesla.zap_tail");
    public static final RegistryObject<SoundEvent> TESLA_CRACKLE = register("tesla.crackle");
    public static final RegistryObject<SoundEvent> RAILGUN_CHARGE = register("railgun.charge");
    public static final RegistryObject<SoundEvent> RAILGUN_FIRE = register("railgun.fire");
    public static final RegistryObject<SoundEvent> RAILGUN_FIRE_TAIL = register("railgun.fire_tail");
    public static final RegistryObject<SoundEvent> RAILGUN_IMPACT = register("railgun.impact");
    public static final RegistryObject<SoundEvent> MORTAR_FIRE = register("mortar.fire");
    public static final RegistryObject<SoundEvent> MORTAR_FIRE_TAIL = register("mortar.fire_tail");
    public static final RegistryObject<SoundEvent> MORTAR_WHISTLE = register("mortar.whistle");

    public static final RegistryObject<SoundEvent> LASER_CHARGE = register("laser.charge");
    public static final RegistryObject<SoundEvent> LASER_FIRE = register("laser.fire");
    public static final RegistryObject<SoundEvent> LASER_FIRE_TAIL = register("laser.fire_tail");

    public static final RegistryObject<SoundEvent> FLAMETHROWER_IGNITE = register("flamethrower.ignite");
    public static final RegistryObject<SoundEvent> FLAMETHROWER_LOOP = register("flamethrower.loop");
    public static final RegistryObject<SoundEvent> BURNING = register("flamethrower.burning");

    public static final RegistryObject<SoundEvent> WORKSTATION_LOOP = register("workstation.loop");
    public static final RegistryObject<SoundEvent> WORKSTATION_WELD = register("workstation.weld");
    public static final RegistryObject<SoundEvent> WORKSTATION_PRESS = register("workstation.press");
    public static final RegistryObject<SoundEvent> WORKSTATION_DONE = register("workstation.done");

    public static final RegistryObject<SoundEvent> SHOTGUN_FIRE = register("shotgun.fire");
    public static final RegistryObject<SoundEvent> SHOTGUN_FIRE_TAIL = register("shotgun.fire_tail");
    public static final RegistryObject<SoundEvent> SHOTGUN_PUMP = register("shotgun.pump");
    public static final RegistryObject<SoundEvent> SHOTGUN_SHELL = register("shotgun.shell");

    private static RegistryObject<SoundEvent> register(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(Bastion.id(name)));
    }
}
