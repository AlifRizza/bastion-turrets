package dev.bastion.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.bastion.Bastion;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.WeaponDataSync;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponDataLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * config/bastion-turrets.toml (PLAN 8 "Config stat turret", user 2026-10-17): every built-in turret weapon's stats and
 * params, written with the mod's own numbers (user 2026-10-18). A number left at the mod's value overrides nothing, so a
 * datapack's value stands; any other number replaces it. Built from the mod's own JSON in the jar, so the keys, the
 * defaults and the "Mod default" comments always match it. WeaponDataLoader lays the overrides over the loaded JSON.
 */
public final class TurretStatsConfig {
    public static final String FILE = "bastion-turrets.toml";

    /** A key's one-line help (with its unit) and the numbers it accepts. */
    public record Key(String description, double min, double max) {
        public boolean accepts(Object value) {
            return value instanceof Number n && n.doubleValue() >= min && n.doubleValue() <= max;
        }
    }

    /** The built-in weapons (data/bastion/turret_weapons/<id>.json), in the order of the store description's weapon table. */
    public static final List<String> WEAPONS = List.of("gun", "machine_gun", "shotgun", "sniper", "rocket_launcher",
            "flamethrower", "laser_rifle", "repulsor", "ion_rifle", "plasma_repeater", "missile_launcher", "tesla", "railgun",
            "mortar", "repulsor_dome", "plasma_cannon");

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("gun", "Gun Turret"), Map.entry("machine_gun", "Machine Gun Turret"), Map.entry("shotgun", "Shotgun Turret"),
            Map.entry("sniper", "Sniper Turret"), Map.entry("rocket_launcher", "Rocket Launcher Turret"),
            Map.entry("flamethrower", "Flamethrower Turret"), Map.entry("laser_rifle", "Laser Rifle Turret"),
            Map.entry("repulsor", "Repulsor"), Map.entry("ion_rifle", "Ion Rifle Turret"),
            Map.entry("plasma_repeater", "Plasma Repeater Turret"), Map.entry("missile_launcher", "Missile Launcher Turret"),
            Map.entry("tesla", "Tesla Coil Turret"), Map.entry("railgun", "Railgun Turret"), Map.entry("mortar", "Mortar Turret"),
            Map.entry("repulsor_dome", "Repulsor Dome"), Map.entry("plasma_cannon", "Plasma Cannon Turret"));

    private static final String[] HEADER = {
            "Bastion Turrets: weapon stats. Each value starts at the mod's own number (\"Mod default\" in its comment).",
            "  Left at that number, the weapon stays as the mod (or a datapack in data/bastion/turret_weapons) makes it.",
            "  Any other number replaces it. A number out of range, or text in quotes, is reset to the mod's number (see the log).",
            "Times are in ticks (20 ticks = 1 second), distances in blocks, angles in degrees.",
            "A stat a weapon does not use (for example spread on the Tesla Coil) has no effect.",
            "Saving this file while the game runs applies it at once. On a server, the server's copy of this file counts."};

    /** The stats every weapon has, in file order. */
    public static final Map<String, Key> COMMON = ordered(
            "range", new Key("Targeting range in blocks.", 0.5, 256),
            "fire_interval", new Key("Ticks between shots (lower = faster).", 0.5, 12000),
            "damage", new Key("Damage per hit.", 0, 10000),
            "spread", new Key("Inaccuracy in degrees.", 0, 90),
            "ammo_per_shot", new Key("Ammo items used per shot (0.5 = one item every two shots; a shot never takes more than one).", 0, 1),
            "turn_speed", new Key("Degrees the weapon turns per tick.", 0, 180),
            "aim_tolerance", new Key("Degrees off target it still fires at.", 0, 180),
            "aim_lock_ticks", new Key("Ticks it must stay on target before its first shot.", 0, 200),
            "min_pitch", new Key("Lowest barrel angle in degrees (negative = down).", -90, 90),
            "max_pitch", new Key("Highest barrel angle in degrees.", -90, 90),
            "sweep_tolerance", new Key("Degrees off aim it keeps firing while a burst swings to the next target (0 = aim_tolerance).", 0, 180),
            "heat_per_shot", new Key("Heat added per shot (0 = never overheats).", 0, 1000),
            "heat_dissipation_per_second", new Key("Heat lost per second.", 0, 100000),
            "max_heat", new Key("Heat at which it overheats.", 1, 3000),
            "overheat_ticks", new Key("Ticks it stays locked after overheating.", 0, 12000));

    /** Every weapon-specific param a built-in weapon JSON uses (a GameTest checks the list against the JSON). */
    public static final Map<String, Key> PARAMS = Map.ofEntries(
            Map.entry("acceleration", new Key("Missile: speed gained per tick, in blocks per tick per tick.", 0, 10)),
            Map.entry("base_pierce", new Key("Extra targets each round passes through.", 0, 64)),
            Map.entry("blast_damage", new Key("Blast damage at the centre (half at the edge).", 0, 10000)),
            Map.entry("blast_radius", new Key("Blast radius in blocks.", 0, 64)),
            Map.entry("bolt_speed", new Key("Bolt speed in blocks per tick.", 0.01, 100)),
            Map.entry("bolts", new Key("Lightning bolts per discharge.", 1, 64)),
            Map.entry("bolts_per_target", new Key("Most bolts one target takes per discharge.", 1, 64)),
            Map.entry("boss_multiplier", new Key("Damage multiplier against bosses.", 0, 100)),
            Map.entry("bullet_speed", new Key("Bullet speed in blocks per tick.", 0.01, 100)),
            Map.entry("burn_damage", new Key("Damage per second while burning.", 0, 10000)),
            Map.entry("burn_ticks", new Key("Ticks a target keeps burning.", 0, 12000)),
            Map.entry("charge_ticks", new Key("Ticks it charges before each shot.", 0, 12000)),
            Map.entry("cone_angle", new Key("Half-angle of its cone in degrees.", 0, 180)),
            Map.entry("cruise_speed", new Key("Missile: top speed in blocks per tick.", 0.01, 100)),
            Map.entry("energy_capacity", new Key("FE the turret's capacitor holds.", 0, 3000000)),
            Map.entry("energy_per_shot", new Key("FE per shot.", 0, 10000000)),
            Map.entry("falloff_end", new Key("Distance in blocks where pellet damage bottoms out.", 0, 256)),
            Map.entry("falloff_min", new Key("Share of pellet damage left at falloff_end (0 to 1).", 0, 1)),
            Map.entry("falloff_start", new Key("Distance in blocks where pellet damage starts to drop.", 0, 256)),
            Map.entry("field_damage", new Key("Damage the ion core deals to each other target around it.", 0, 10000)),
            Map.entry("field_radius", new Key("Reach of the ion core in blocks.", 0, 64)),
            Map.entry("field_ticks", new Key("Ticks the ion core's beams stay visible.", 0, 200)),
            Map.entry("fire_ticks", new Key("Ticks a fire patch burns.", 0, 12000)),
            Map.entry("fuel_ticks", new Key("Missile: ticks of flight before its fuel runs out.", 0, 12000)),
            Map.entry("gravity", new Key("Shell gravity in blocks per tick per tick.", 0.001, 1)),
            Map.entry("headshot_multiplier", new Key("Damage multiplier on a headshot.", 0, 100)),
            Map.entry("homing_delay", new Key("Missile: ticks after launch before it starts steering.", 0, 200)),
            Map.entry("hot_spread", new Key("Spread in degrees while hot.", 0, 90)),
            Map.entry("hot_spread_threshold", new Key("Share of max heat (0 to 1) above which hot_spread applies.", 0, 1)),
            Map.entry("knockback", new Key("Knockback strength (Repulsor: about how many blocks a target is thrown; Repulsor Dome: that at the edge of its reach, +1 block per block closer).", 0, 100)),
            Map.entry("launch_speed", new Key("Missile: speed out of the tube in blocks per tick.", 0.01, 100)),
            Map.entry("lead_accuracy", new Key("How well it aims ahead of moving targets (0 = not at all, 1 = perfectly).", 0, 1)),
            Map.entry("lift", new Key("Upward push.", 0, 10)),
            Map.entry("max_input", new Key("Most FE it takes in per transfer.", 0, 10000000)),
            Map.entry("max_targets", new Key("Most targets one burst pushes.", 1, 1024)),
            Map.entry("min_range", new Key("Closest distance in blocks it fires at.", 0, 256)),
            Map.entry("min_spin_to_fire", new Key("Barrel spin (0 to 1) needed before it fires.", 0, 1)),
            Map.entry("orb_speed", new Key("Plasma ball speed in blocks per tick.", 0.01, 100)),
            Map.entry("patch_size", new Key("Side of the fire patch in blocks.", 1, 15)),
            Map.entry("pellets", new Key("Pellets per shot.", 1, 64)),
            Map.entry("precision_lock_multiplier", new Key("Damage multiplier of the shot after holding a target for precision_lock_ticks.", 0, 100)),
            Map.entry("precision_lock_ticks", new Key("Ticks on one target before the precision shot.", 0, 12000)),
            Map.entry("reload_ticks", new Key("Ticks to reload one tube.", 1, 12000)),
            Map.entry("rocket_speed", new Key("Rocket speed in blocks per tick.", 0.01, 100)),
            Map.entry("salvo_interval", new Key("Salvo mode: ticks between the rounds of a salvo (0 = all at once).", 0, 200)),
            Map.entry("shell_speed", new Key("Shell launch speed in blocks per tick.", 0.01, 100)),
            Map.entry("shock_knockback", new Key("How hard the impact's shockwave throws mobs.", 0, 100)),
            Map.entry("shock_radius", new Key("Reach of the impact's shockwave in blocks.", 0, 64)),
            Map.entry("slowness_amplifier", new Key("Level of the Slowness its hits apply (0 = Slowness I).", 0, 10)),
            Map.entry("slowness_ticks", new Key("Ticks of Slowness per hit.", 0, 12000)),
            Map.entry("slug_speed", new Key("Slug speed in blocks per tick.", 0.01, 100)),
            Map.entry("spin_down_ticks", new Key("Ticks the barrels take to spin down.", 1, 12000)),
            Map.entry("spin_up_ticks", new Key("Ticks the barrels take to spin up.", 1, 12000)),
            Map.entry("splash_damage", new Key("Splash damage to targets around the impact.", 0, 10000)),
            Map.entry("splash_radius", new Key("Splash radius in blocks.", 0, 64)),
            Map.entry("turn_rate", new Key("Missile: degrees it can turn per tick while homing.", 0, 180)));

    /** ammo_per_shot of a weapon that takes no ammo items: any number above 0 would leave it waiting for items forever. */
    private static final Key NO_AMMO_ITEMS = new Key("This weapon uses no ammo items (it runs on FE or loads its own rounds), so this stays 0.", 0, 0);

    public static final ForgeConfigSpec SPEC;
    private static final Map<String, Map<String, Setting>> VALUES = new LinkedHashMap<>();

    /** A key in the file and the mod's own value for it (a number equal to that overrides nothing). */
    private record Setting(ForgeConfigSpec.ConfigValue<Object> value, float modValue) {
    }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        for (String weapon : WEAPONS) {
            WeaponData data = builtIn(weapon);
            List<String> comment = new ArrayList<>();
            if (weapon.equals(WEAPONS.get(0))) comment.addAll(List.of(HEADER));
            comment.add(NAMES.get(weapon));
            b.comment(comment.toArray(String[]::new)).push(weapon);
            Map<String, Setting> values = new LinkedHashMap<>();
            for (String key : keys(data)) {
                Key spec = key.equals("ammo_per_shot") && data.ammoPerShot() == 0 ? NO_AMMO_ITEMS : key(key);
                double modValue = builtInValue(data, key);
                values.put(key, new Setting(b.comment(spec.description(), "Mod default: " + format(modValue),
                                "Range: " + format(spec.min()) + " ~ " + format(spec.max()))
                        .define(key, number(modValue), spec::accepts), (float) modValue));
            }
            VALUES.put(weapon, values);
            b.pop();
        }
        SPEC = b.build();
    }

    private TurretStatsConfig() {
    }

    /** The mod's own JSON for {@code weapon}, read from the jar (never a datapack's). */
    public static WeaponData builtIn(String weapon) {
        String path = "/data/" + Bastion.MOD_ID + "/turret_weapons/" + weapon + ".json";
        try (InputStream in = TurretStatsConfig.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing built-in turret weapon " + path);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return WeaponData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, error -> {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A weapon's keys in file order: the common stats, then its params alphabetically. */
    public static List<String> keys(WeaponData data) {
        List<String> keys = new ArrayList<>(COMMON.keySet());
        data.params().keySet().stream().sorted().forEach(keys::add);
        return keys;
    }

    /**
     * The numbers changed for {@code weapon} (keys at the mod's value are absent). Empty while the file isn't loaded yet,
     * so the JSON stands as it is.
     */
    public static Map<String, Double> overrides(String weapon) {
        Map<String, Setting> values = VALUES.get(weapon);
        if (values == null || !SPEC.isLoaded()) return Map.of();
        Map<String, Double> set = new HashMap<>();
        values.forEach((key, setting) -> {
            if (setting.value().get() instanceof Number n && (float) n.doubleValue() != setting.modValue()) set.put(key, n.doubleValue());
        });
        return set;
    }

    /**
     * Forge saw the file change (mod bus, its file-watcher thread): rebuild on the server thread. Without a running server
     * (a client on someone else's server) there is nothing to do: that server's file counts and its numbers arrive by packet.
     */
    public static void onReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(TurretStatsConfig::reloaded);
    }

    /** Rebuilds the effective weapons and sends them to every player; turrets use them from their next tick. */
    public static void reloaded() {
        WeaponDataLoader.rebuild();
        BastionNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), WeaponDataSync.current());
        Bastion.LOGGER.info("Reloaded {}", FILE);
    }

    private static Key key(String name) {
        Key key = COMMON.containsKey(name) ? COMMON.get(name) : PARAMS.get(name);
        if (key == null) throw new IllegalStateException("TurretStatsConfig.PARAMS has no entry for weapon param " + name);
        return key;
    }

    private static double builtInValue(WeaponData d, String key) {
        return switch (key) {
            case "range" -> d.range();
            case "fire_interval" -> d.fireInterval();
            case "damage" -> d.damage();
            case "spread" -> d.spread();
            case "ammo_per_shot" -> d.ammoPerShot();
            case "turn_speed" -> d.aim().turnSpeed();
            case "aim_tolerance" -> d.aim().tolerance();
            case "aim_lock_ticks" -> d.aim().lockTicks();
            case "min_pitch" -> d.aim().minPitch();
            case "max_pitch" -> d.aim().maxPitch();
            case "sweep_tolerance" -> d.aim().sweepTolerance();
            case "heat_per_shot" -> d.heat().perShot();
            case "heat_dissipation_per_second" -> d.heat().dissipationPerSecond();
            case "max_heat" -> d.heat().max();
            case "overheat_ticks" -> d.heat().overheatTicks();
            default -> d.param(key);
        };
    }

    /** 24 -> "24", 1.6667 -> "1.6667", 10000000 -> "10000000" (floats from the JSON print as written). */
    private static String format(double value) {
        return new BigDecimal(Float.toString((float) value)).stripTrailingZeros().toPlainString();
    }

    /** The file's value for {@code value}: 24 as an int (written "24", not "24.0"), 1.6667 as the double the JSON wrote. */
    private static Object number(double value) {
        BigDecimal exact = new BigDecimal(format(value));
        return exact.scale() <= 0 ? (Object) exact.intValueExact() : (Object) exact.doubleValue();
    }

    private static <V> Map<String, V> ordered(Object... pairs) {
        Map<String, V> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked") V value = (V) pairs[i + 1];
            map.put((String) pairs[i], value);
        }
        return java.util.Collections.unmodifiableMap(map);
    }
}
