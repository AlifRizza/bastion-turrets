package dev.bastion.gametest;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import dev.bastion.Bastion;
import dev.bastion.config.BastionConfig;
import dev.bastion.config.TurretStatsConfig;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponDataLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** bastion-turrets.toml (PLAN 8 "Config stat turret"): overrides, the file's keys, live reload. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretStatsConfigTests {
    private static final String ARENA = "arena";

    private static WeaponData weapon(String id) {
        WeaponData data = WeaponDataLoader.get(Bastion.id(id));
        if (data == null) throw new IllegalStateException("no weapon data for " + id);
        return data;
    }

    /** Each key replaces its own stat or param; identity, colour, geometry and the rest stay; ints round. */
    @GameTest(template = ARENA, batch = "overridesReplaceOnlyTheirStats")
    public static void overridesReplaceOnlyTheirStats(GameTestHelper helper) {
        WeaponData sniper = weapon("sniper");
        helper.assertTrue(sniper.withOverrides(Map.of()) == sniper, "no overrides must return the data unchanged");
        WeaponData changed = sniper.withOverrides(Map.of("damage", 77.0, "charge_ticks", 9.0, "aim_lock_ticks", 2.6, "max_heat", 50.0));
        helper.assertTrue(changed.damage() == 77f, "damage " + changed.damage());
        helper.assertTrue(changed.param("charge_ticks") == 9f, "charge_ticks " + changed.param("charge_ticks"));
        helper.assertTrue(changed.aim().lockTicks() == 3, "aim_lock_ticks 2.6 must round to 3, got " + changed.aim().lockTicks());
        helper.assertTrue(changed.heat().max() == 50f, "max_heat " + changed.heat().max());
        helper.assertTrue(changed.range() == sniper.range() && changed.fireInterval() == sniper.fireInterval(), "range or fire_interval changed");
        helper.assertTrue(changed.param("bullet_speed") == sniper.param("bullet_speed"), "another param changed");
        helper.assertTrue(changed.type().equals(sniper.type()) && changed.ammo().equals(sniper.ammo()) && changed.energyColor() == sniper.energyColor(),
                "identity or colour changed");
        helper.assertTrue(changed.aim().pivotHeight() == sniper.aim().pivotHeight() && changed.aim().muzzleLength() == sniper.aim().muzzleLength(),
                "geometry changed");
        helper.succeed();
    }

    /** A key for a param this weapon doesn't have adds nothing. */
    @GameTest(template = ARENA, batch = "overrideOfAParamTheWeaponLacksIsIgnored")
    public static void overrideOfAParamTheWeaponLacksIsIgnored(GameTestHelper helper) {
        WeaponData sniper = weapon("sniper");
        WeaponData changed = sniper.withOverrides(Map.of("blast_radius", 5.0));
        helper.assertTrue(changed.params().equals(sniper.params()), "params became " + changed.params());
        helper.succeed();
    }

    /** min_pitch above max_pitch after overrides: both pitch overrides dropped; a sane one applies. */
    @GameTest(template = ARENA, batch = "crossedPitchOverridesAreDropped")
    public static void crossedPitchOverridesAreDropped(GameTestHelper helper) {
        WeaponData sniper = weapon("sniper");
        WeaponData crossed = sniper.withOverrides(Map.of("min_pitch", 50.0, "max_pitch", 10.0));
        helper.assertTrue(crossed.aim().minPitch() == sniper.aim().minPitch() && crossed.aim().maxPitch() == sniper.aim().maxPitch(),
                "crossed pitches applied: " + crossed.aim().minPitch() + " / " + crossed.aim().maxPitch());
        WeaponData lower = sniper.withOverrides(Map.of("min_pitch", -20.0));
        helper.assertTrue(lower.aim().minPitch() == -20f, "min_pitch -20 not applied: " + lower.aim().minPitch());
        helper.succeed();
    }

    /**
     * Only Bastion weapons that are loaded get overrides: another mod's weapon passes untouched, a built-in weapon a datapack
     * removed stays absent, and a datapack's changed value survives no overrides but loses to a number.
     */
    @GameTest(template = ARENA, batch = "mergeTouchesOnlyBastionWeaponsItHas")
    public static void mergeTouchesOnlyBastionWeaponsItHas(GameTestHelper helper) {
        WeaponData sniper = weapon("sniper");
        WeaponData datapackSniper = sniper.withOverrides(Map.of("damage", 50.0)); // as if a datapack set damage 50
        ResourceLocation other = new ResourceLocation("othermod", "sniper");
        Map<ResourceLocation, WeaponData> raw = Map.of(Bastion.id("sniper"), datapackSniper, other, sniper);
        Map<ResourceLocation, WeaponData> untouched = WeaponDataLoader.merge(raw, weapon -> Map.of());
        helper.assertTrue(untouched.get(Bastion.id("sniper")).damage() == 50f, "datapack value lost with no overrides");
        Map<ResourceLocation, WeaponData> merged = WeaponDataLoader.merge(raw, weapon -> Map.of("damage", 77.0));
        helper.assertTrue(merged.get(Bastion.id("sniper")).damage() == 77f, "config number must beat the datapack");
        helper.assertTrue(merged.get(other).damage() == sniper.damage(), "another mod's weapon was changed");
        helper.assertTrue(!merged.containsKey(Bastion.id("gun")) && merged.size() == 2, "merge invented weapons: " + merged.keySet());
        helper.succeed();
    }

    /** One section per built-in weapon file, holding exactly the 15 common stats and that weapon's JSON params. */
    @GameTest(template = ARENA, batch = "configListsEveryBuiltInWeaponAndKey")
    public static void configListsEveryBuiltInWeaponAndKey(GameTestHelper helper) {
        Set<String> files = new TreeSet<>();
        helper.getLevel().getServer().getResourceManager()
                .listResources("turret_weapons", id -> id.getPath().endsWith(".json"))
                .keySet().stream().filter(id -> id.getNamespace().equals(Bastion.MOD_ID))
                .forEach(id -> files.add(id.getPath().substring("turret_weapons/".length(), id.getPath().length() - ".json".length())));
        helper.assertTrue(files.equals(new TreeSet<>(TurretStatsConfig.WEAPONS)), "config weapons " + TurretStatsConfig.WEAPONS + " vs files " + files);
        Set<String> allParams = new HashSet<>();
        for (String weapon : TurretStatsConfig.WEAPONS) {
            WeaponData data = TurretStatsConfig.builtIn(weapon);
            Set<String> expected = new HashSet<>(TurretStatsConfig.COMMON.keySet());
            expected.addAll(data.params().keySet());
            allParams.addAll(data.params().keySet());
            UnmodifiableConfig section = TurretStatsConfig.SPEC.getValues().get(weapon);
            helper.assertTrue(section != null && section.valueMap().keySet().equals(expected),
                    weapon + " keys " + (section == null ? null : section.valueMap().keySet()) + " vs " + expected);
        }
        helper.assertTrue(TurretStatsConfig.PARAMS.keySet().equals(allParams), "param table has stale or missing keys: "
                + TurretStatsConfig.PARAMS.keySet() + " vs " + allParams);
        helper.succeed();
    }

    /** Numbers inside the key's range pass the file's validator; everything else (text too) is reset by Forge. */
    @GameTest(template = ARENA, batch = "configValidatorAcceptsNumbersInRange")
    public static void configValidatorAcceptsNumbersInRange(GameTestHelper helper) {
        helper.assertTrue(!spec("sniper", "damage").test("default"), "the word \"default\" accepted");
        helper.assertTrue(spec("sniper", "damage").test(30), "integer 30 rejected");
        helper.assertTrue(spec("sniper", "damage").test(30.5), "30.5 rejected");
        helper.assertTrue(!spec("sniper", "damage").test(-1), "negative damage accepted");
        helper.assertTrue(!spec("sniper", "damage").test(20000), "damage 20000 accepted");
        helper.assertTrue(!spec("sniper", "damage").test("abc"), "text accepted");
        helper.assertTrue(!spec("sniper", "fire_interval").test(0.4) && spec("sniper", "fire_interval").test(0.5), "fire_interval floor is not 0.5");
        helper.assertTrue(spec("sniper", "min_pitch").test(-90) && !spec("sniper", "min_pitch").test(-91), "pitch range is not -90 ~ 90");
        helper.assertTrue(spec("railgun", "energy_per_shot").test(8000) && !spec("railgun", "lead_accuracy").test(1.5), "param ranges not wired");
        helper.succeed();
    }

    /**
     * Only values a turret can use: a shot takes at most one item, a weapon that takes no items (FE, or loads its own
     * rounds) can't be made to, and the GUI's 16-bit bars hold up to 3,000,000 FE and 3000 heat.
     */
    @GameTest(template = ARENA, batch = "configRejectsValuesTheTurretCannotUse")
    public static void configRejectsValuesTheTurretCannotUse(GameTestHelper helper) {
        helper.assertTrue(spec("sniper", "ammo_per_shot").test(1) && !spec("sniper", "ammo_per_shot").test(1.5), "sniper ammo_per_shot is not 0 ~ 1");
        for (String weapon : List.of("ion_rifle", "tesla", "repulsor", "repulsor_dome", "missile_launcher")) {
            helper.assertTrue(spec(weapon, "ammo_per_shot").test(0) && !spec(weapon, "ammo_per_shot").test(0.5), weapon + " takes ammo items");
        }
        helper.assertTrue(spec("ion_rifle", "energy_capacity").test(3000000) && !spec("ion_rifle", "energy_capacity").test(3000001),
                "energy_capacity is not capped at 3,000,000");
        helper.assertTrue(spec("sniper", "max_heat").test(3000) && !spec("sniper", "max_heat").test(3001), "max_heat is not capped at 3000");
        helper.succeed();
    }

    /**
     * The file is written with the mod's own numbers (whole numbers without ".0"), and each of them passes its key's
     * validator: a default outside its range would be "corrected" to itself on every start.
     */
    @GameTest(template = ARENA, batch = "configDefaultsAreTheModNumbers")
    public static void configDefaultsAreTheModNumbers(GameTestHelper helper) {
        helper.assertTrue(Integer.valueOf(24).equals(spec("sniper", "damage").getDefault()), "sniper damage default " + spec("sniper", "damage").getDefault());
        helper.assertTrue(Double.valueOf(1.6667).equals(spec("machine_gun", "fire_interval").getDefault()),
                "machine gun fire_interval default " + spec("machine_gun", "fire_interval").getDefault());
        for (String weapon : TurretStatsConfig.WEAPONS) {
            for (String key : TurretStatsConfig.keys(TurretStatsConfig.builtIn(weapon))) {
                ForgeConfigSpec.ValueSpec spec = spec(weapon, key);
                helper.assertTrue(spec.getDefault() instanceof Number && spec.test(spec.getDefault()),
                        weapon + "." + key + " default " + spec.getDefault() + " fails its own range");
            }
        }
        helper.succeed();
    }

    /** A number at the mod's value overrides nothing (a datapack's value stands); any other number does. */
    @GameTest(template = ARENA, batch = "onlyNumbersOffTheModValueOverride")
    public static void onlyNumbersOffTheModValueOverride(GameTestHelper helper) {
        withSetting("sniper", "damage", 24.0, () -> helper.assertTrue(!TurretStatsConfig.overrides("sniper").containsKey("damage"),
                "24.0 (the mod's 24) counted as an override"));
        withSetting("machine_gun", "fire_interval", 1.6667, () -> helper.assertTrue(TurretStatsConfig.overrides("machine_gun").isEmpty(),
                "1.6667 (the mod's value) counted as an override: " + TurretStatsConfig.overrides("machine_gun")));
        withSetting("sniper", "damage", 25, () -> helper.assertTrue(Double.valueOf(25).equals(TurretStatsConfig.overrides("sniper").get("damage")),
                "25 is not an override: " + TurretStatsConfig.overrides("sniper")));
        helper.succeed();
    }

    /** Every key says what it does, the built-in value and its range. */
    @GameTest(template = ARENA, batch = "configCommentsShowTheModDefault")
    public static void configCommentsShowTheModDefault(GameTestHelper helper) {
        String damage = spec("sniper", "damage").getComment();
        helper.assertTrue(damage.contains("Mod default: 24") && damage.contains("Range: 0 ~ 10000"), "sniper damage comment: " + damage);
        String interval = spec("machine_gun", "fire_interval").getComment();
        helper.assertTrue(interval.contains("Mod default: 1.6667"), "machine gun fire_interval comment: " + interval);
        String charge = spec("railgun", "charge_ticks").getComment();
        helper.assertTrue(charge.contains("Mod default: 60") && charge.toLowerCase().contains("tick"), "railgun charge_ticks comment: " + charge);
        helper.succeed();
    }

    private static ForgeConfigSpec.ValueSpec spec(String weapon, String key) {
        return TurretStatsConfig.SPEC.getSpec().get(List.of(weapon, key));
    }

    @SuppressWarnings("unchecked")
    private static ForgeConfigSpec.ConfigValue<Object> value(String weapon, String key) {
        return (ForgeConfigSpec.ConfigValue<Object>) TurretStatsConfig.SPEC.getValues().get(List.of(weapon, key));
    }

    /** Sets weapon.key in the (real) file and runs {@code check}; always puts back what the file had and rebuilds. */
    private static void withSetting(String weapon, String key, Object number, Runnable check) {
        ForgeConfigSpec.ConfigValue<Object> value = value(weapon, key);
        Object before = value.get();
        try {
            value.set(number);
            check.run();
        } finally {
            value.set(before);
            WeaponDataLoader.rebuild();
        }
    }

    /** {@link #withSetting} with {@code check} run on the rebuilt weapons. */
    private static void withValue(String weapon, String key, Object number, Runnable check) {
        withSetting(weapon, key, number, () -> {
            WeaponDataLoader.rebuild();
            check.run();
        });
    }

    /** run/config is shared with runClient: a value set there to try something in game must survive the GameTests. */
    @GameTest(template = ARENA, batch = "testsPutBackTheValueTheFileHad")
    public static void testsPutBackTheValueTheFileHad(GameTestHelper helper) {
        ForgeConfigSpec.ConfigValue<Object> value = value("sniper", "spread");
        try {
            value.set(2.5);
            withValue("sniper", "spread", 7, () -> {});
            helper.assertTrue(Double.valueOf(2.5).equals(value.get()), "the file's 2.5 became " + value.get());
        } finally {
            value.set(value.getDefault());
            WeaponDataLoader.rebuild();
        }
        helper.succeed();
    }

    /** The other GameTests expect the JSON values: a number left in run/config from a game session fails here, by name. */
    @GameTest(template = ARENA, batch = "runConfigLeavesEveryValueAtDefault")
    public static void runConfigLeavesEveryValueAtDefault(GameTestHelper helper) {
        List<String> set = new ArrayList<>();
        for (String weapon : TurretStatsConfig.WEAPONS) {
            TurretStatsConfig.overrides(weapon).forEach((key, number) -> set.add(weapon + "." + key + " = " + number));
        }
        helper.assertTrue(set.isEmpty(), "run/config/" + TurretStatsConfig.FILE + " has numbers changed; put them back to the Mod default for the GameTests: " + set);
        helper.succeed();
    }

    /** A number in the file reaches the weapon (as an int or a decimal); the mod's number brings the JSON's value back. */
    @GameTest(template = ARENA, batch = "configOverrideReachesTheWeapon")
    public static void configOverrideReachesTheWeapon(GameTestHelper helper) {
        float json = TurretStatsConfig.builtIn("sniper").damage();
        withValue("sniper", "damage", 77.5, () -> helper.assertTrue(weapon("sniper").damage() == 77.5f, "damage " + weapon("sniper").damage()));
        withValue("sniper", "damage", Integer.valueOf(31), () -> helper.assertTrue(weapon("sniper").damage() == 31f, "integer 31 gave " + weapon("sniper").damage()));
        helper.assertTrue(weapon("sniper").damage() == json, "the mod's number did not restore " + json + ", got " + weapon("sniper").damage());
        helper.succeed();
    }

    /** The charge time a turret actually waits comes from the file's charge_ticks. */
    @GameTest(template = ARENA, batch = "railgunChargeOverrideSetsTheChargeTime")
    public static void railgunChargeOverrideSetsTheChargeTime(GameTestHelper helper) {
        withValue("railgun", "charge_ticks", 9, () -> {
            int ticks = BastionWeaponTypes.RAILGUN.get().chargeTicks(StatSheet.of(weapon("railgun"), TurretTier.T1));
            helper.assertTrue(ticks == 9, "railgun charges " + ticks + " ticks");
        });
        helper.succeed();
    }

    /** Saving the file while the game runs (Forge's reload) rebuilds the weapons. */
    @GameTest(template = ARENA, batch = "savingTheFileRebuildsTheWeapons")
    public static void savingTheFileRebuildsTheWeapons(GameTestHelper helper) {
        withSetting("sniper", "damage", 66, () -> {
            TurretStatsConfig.reloaded();
            helper.assertTrue(weapon("sniper").damage() == 66f, "after the reload damage is " + weapon("sniper").damage());
        });
        helper.succeed();
    }

    private static ModConfig modConfig(ForgeConfigSpec spec) {
        return ConfigTracker.INSTANCE.fileMap().values().stream().filter(config -> config.getSpec() == spec).findFirst()
                .orElseThrow(() -> new IllegalStateException("config not registered"));
    }

    /** Forge's reload event for this file (fired on the server thread here, so the rebuild runs at once) rebuilds the weapons. */
    @GameTest(template = ARENA, batch = "reloadEventForThisFileRebuildsTheWeapons")
    public static void reloadEventForThisFileRebuildsTheWeapons(GameTestHelper helper) {
        withSetting("sniper", "damage", 66, () -> {
            TurretStatsConfig.onReloading(new ModConfigEvent.Reloading(modConfig(TurretStatsConfig.SPEC)));
            helper.assertTrue(weapon("sniper").damage() == 66f, "after the event damage is " + weapon("sniper").damage());
        });
        helper.succeed();
    }

    /** bastion-common.toml reloading leaves the weapons alone. */
    @GameTest(template = ARENA, batch = "reloadEventForAnotherFileIsIgnored")
    public static void reloadEventForAnotherFileIsIgnored(GameTestHelper helper) {
        float json = TurretStatsConfig.builtIn("sniper").damage();
        withSetting("sniper", "damage", 66, () -> {
            TurretStatsConfig.onReloading(new ModConfigEvent.Reloading(modConfig(BastionConfig.SPEC)));
            helper.assertTrue(weapon("sniper").damage() == json, "another file's event rebuilt the weapons: damage " + weapon("sniper").damage());
        });
        helper.succeed();
    }

    /** damage 0 (or a 0/0 from a 0 base or a 0 radius) must never reach a mob: NaN health is saved with the world. */
    @GameTest(template = ARENA, batch = "turretDamageThatIsNotAPositiveNumberDoesNothing")
    public static void turretDamageThatIsNotAPositiveNumberDoesNothing(GameTestHelper helper) {
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 2));
        float health = husk.getHealth();
        helper.assertTrue(!Hitscan.damage(husk, helper.getLevel(), Float.NaN), "a NaN hit landed");
        helper.assertTrue(!Hitscan.damage(husk, helper.getLevel(), 0), "a 0 hit landed");
        helper.assertTrue(husk.getHealth() == health, "health " + health + " became " + husk.getHealth());
        helper.succeed();
    }

    /** Splash, blast and field damage follow damage modules; a weapon set to damage 0 keeps them at their own value. */
    @GameTest(template = ARENA, batch = "damageScaleIsOneWithoutBaseDamage")
    public static void damageScaleIsOneWithoutBaseDamage(GameTestHelper helper) {
        WeaponData cannon = weapon("plasma_cannon");
        float plain = StatSheet.of(cannon, TurretTier.T1).damageScale();
        float zero = StatSheet.of(cannon.withOverrides(Map.of("damage", 0.0)), TurretTier.T1).damageScale();
        helper.assertTrue(plain == 1f && zero == 1f, "damage scale " + plain + ", with damage 0 " + zero);
        helper.succeed();
    }

    /** A capacitor lowered below what a turret holds: it shows and spends only the new capacity and takes no more FE. */
    @GameTest(template = ARENA, batch = "loweringEnergyCapacityNeverOverfillsATurret")
    public static void loweringEnergyCapacityNeverOverfillsATurret(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 3);
        helper.setBlock(pos, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(pos);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.ION_RIFLE_TURRET.get()));
        IEnergyStorage energy = turret.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
        for (int left = 30000, got; left > 0 && (got = energy.receiveEnergy(left, false)) > 0; ) left -= got;
        helper.assertTrue(turret.energy() == 30000, "charged to " + turret.energy());
        withValue("ion_rifle", "energy_capacity", 1000, () -> {
            helper.assertTrue(turret.energy() == 1000, "holds " + turret.energy() + " with capacity 1000");
            helper.assertTrue(energy.receiveEnergy(500, false) == 0, "took FE above the new capacity");
        });
        helper.succeed();
    }
}
