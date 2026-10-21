package dev.bastion.config;

import dev.bastion.turret.TurretTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Common (server-authoritative) config: base tier stats and protection (PLAN 4.1, 4.7). Weapon numbers live in JSON. */
public final class BastionConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue OWNER_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue TRUSTED_CAN_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue MOBS_ATTACK_TURRETS;
    public static final ForgeConfigSpec.IntValue TURRET_HURT_SOUND_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue TURRET_HURT_SOUND_VOLUME;
    public static final ForgeConfigSpec.DoubleValue LARGE_HEALTH_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue WORKSTATION_ENERGY_CAPACITY;
    public static final ForgeConfigSpec.IntValue WORKSTATION_MAX_INPUT;
    public static final ForgeConfigSpec.BooleanValue MORTAR_BLOCK_FIRE;
    public static final ForgeConfigSpec.IntValue FEED_HUB_ENERGY_CAPACITY;
    public static final ForgeConfigSpec.IntValue FEED_HUB_MAX_INPUT;
    public static final ForgeConfigSpec.IntValue FEED_HUB_ITEMS_PER_TICK;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REPULSOR_HEAVY_MOBS;
    public static final ForgeConfigSpec.DoubleValue REPULSOR_HEAVY_PUSH;
    public static final ForgeConfigSpec.IntValue CONTROL_HUB_ENERGY_CAPACITY;
    public static final ForgeConfigSpec.IntValue CONTROL_HUB_PORT_MAX_INPUT;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> CONTROL_HUB_UPKEEP;
    public static final ForgeConfigSpec.IntValue CONTROL_HUB_RESTART_TICKS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> CONTROL_HUB_RANGE;
    private static final ForgeConfigSpec.DoubleValue OVERWATCH_RANGE_MULTIPLIER;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> TIER_HEALTH;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> TIER_REGEN;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> TIER_TURN_SPEED;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        OWNER_PROTECTION = b
                .comment("Only the owner and trusted players may open a turret GUI, remove its weapon or reconfigure it (PLAN 4.7).")
                .define("ownerProtection", true);
        TRUSTED_CAN_DAMAGE = b
                .comment("Whether the owner and trusted players can damage their own turret.")
                .define("trustedCanDamage", false);
        MOBS_ATTACK_TURRETS = b
                .comment("Whether hostile mobs seek out and attack turrets (players still take priority).")
                .define("mobsAttackTurrets", true);
        TURRET_HURT_SOUND_INTERVAL = b
                .comment("Ticks between a mob's hurt sounds when turrets hit it (each turret hit is a fresh hit; 0 = a sound per hit).")
                .defineInRange("turretHurtSoundInterval", 10, 0, 200);
        TURRET_HURT_SOUND_VOLUME = b
                .comment("Volume of a mob's hurt sound when turrets hit it, relative to normal (its death sound is unchanged).")
                .defineInRange("turretHurtSoundVolume", 0.5, 0.0, 1.0);
        LARGE_HEALTH_MULTIPLIER = b
                .comment("Max HP of a Large Turret Base (2x2) relative to a standard base of the same tier.")
                .defineInRange("largeBaseHealthMultiplier", 2.5, 0.1, 100);
        WORKSTATION_ENERGY_CAPACITY = b
                .comment("FE a workstation stores (PLAN Fase 9).")
                .defineInRange("workstationEnergyCapacity", 50000, 1000, Integer.MAX_VALUE);
        WORKSTATION_MAX_INPUT = b
                .comment("Most FE a workstation accepts per tick.")
                .defineInRange("workstationMaxInput", 2000, 1, Integer.MAX_VALUE);
        MORTAR_BLOCK_FIRE = b
                .comment("Whether Mortar shells set burnable blocks in their fire patch on (vanilla) fire. The patch burns mobs and players either way.")
                .define("mortarBlockFire", true);
        FEED_HUB_ENERGY_CAPACITY = b
                .comment("FE each Feed Hub block stores; a merged structure holds this times its blocks.")
                .defineInRange("feedHubEnergyCapacity", 50000, 0, Integer.MAX_VALUE);
        FEED_HUB_MAX_INPUT = b
                .comment("Most FE each Feed Hub block accepts per tick; a merged structure takes this times its blocks.")
                .defineInRange("feedHubMaxInput", 4000, 0, Integer.MAX_VALUE);
        FEED_HUB_ITEMS_PER_TICK = b
                .comment("Ammo items each Feed Hub block moves into its turrets per tick.")
                .defineInRange("feedHubItemsPerTick", 8, 1, 64);
        REPULSOR_HEAVY_MOBS = b
                .comment("Mobs too big or heavy for the Repulsor: it never fires at them, and the Repulsor Dome throws them only",
                        "repulsorHeavyMobPush of its push (their vanilla knockback resistance is ignored). Entity ids; add big mobs from",
                        "other mods here, e.g. \"somemod:stone_giant\".")
                .defineListAllowEmpty(List.of("repulsorHeavyMobs"), () -> List.of("minecraft:iron_golem", "minecraft:ravager", "minecraft:warden"),
                        o -> o instanceof String s && ResourceLocation.isValidResourceLocation(s));
        REPULSOR_HEAVY_PUSH = b
                .comment("Share of the Repulsor Dome's push that heavy mobs (repulsorHeavyMobs) still get: 0.4 = 60 % less.")
                .defineInRange("repulsorHeavyMobPush", 0.4, 0, 1);
        CONTROL_HUB_ENERGY_CAPACITY = b
                .comment("FE a Turret Control Hub's Interface stores (PLAN \"Turret Control Hub\").")
                .defineInRange("controlHubEnergyCapacity", 200000, 0, Integer.MAX_VALUE);
        CONTROL_HUB_PORT_MAX_INPUT = b
                .comment("Most FE each Hub Power Port takes from cables per tick.")
                .defineInRange("controlHubPortMaxInput", 2000, 0, Integer.MAX_VALUE);
        CONTROL_HUB_UPKEEP = b
                .comment("FE per tick a formed Control Hub uses, per Satellite Uplink tier T1, T2, T3; without it the hub goes offline.")
                .defineList("controlHubUpkeep", List.<Number>of(20, 60, 150), o -> o instanceof Number n && n.intValue() >= 0);
        CONTROL_HUB_RESTART_TICKS = b
                .comment("An offline Control Hub comes back online once it holds this many ticks of upkeep (fed less than its upkeep, it would flip on and off every tick).")
                .defineInRange("controlHubRestartTicks", 100, 1, 1200);
        CONTROL_HUB_RANGE = b
                .comment("Side of the square of chunks a Control Hub reaches, centred on its satellite's chunk, per tier T1, T2, T3 (odd).")
                .defineList("controlHubRange", List.<Number>of(1, 3, 7), o -> o instanceof Number n && n.intValue() >= 1 && n.intValue() % 2 == 1);
        OVERWATCH_RANGE_MULTIPLIER = b
                .comment("Range of a turret while a player controls it through an Overwatch Link, relative to its own (modules included).",
                        "A Mortar's shells still only fly as far as their speed allows.")
                .defineInRange("overwatchRangeMultiplier", 1.5, 1.0, 4.0);
        b.push("tiers");
        TIER_HEALTH = b.comment("Max HP per tier T1, T2, T3.")
                .defineList("health", List.of(100.0, 250.0, 500.0), o -> o instanceof Double d && d > 0);
        TIER_REGEN = b.comment("HP regenerated per second per tier.")
                .defineList("regenPerSecond", List.of(0.0, 0.5, 1.0), o -> o instanceof Double d && d >= 0);
        TIER_TURN_SPEED = b.comment("Turn speed multiplier per tier.")
                .defineList("turnSpeedMultiplier", List.of(1.0, 1.15, 1.3), o -> o instanceof Double d && d > 0);
        b.pop();
        SPEC = b.build();
    }

    private static float tierValue(ForgeConfigSpec.ConfigValue<List<? extends Double>> list, TurretTier tier, double fallback) {
        List<? extends Double> values = list.get();
        return (float) (tier.ordinal() < values.size() ? values.get(tier.ordinal()) : fallback);
    }

    public static float maxHealth(TurretTier tier) {
        return tierValue(TIER_HEALTH, tier, 100);
    }

    public static float regenPerSecond(TurretTier tier) {
        return tierValue(TIER_REGEN, tier, 0);
    }

    public static float turnSpeedMultiplier(TurretTier tier) {
        return tierValue(TIER_TURN_SPEED, tier, 1);
    }

    /** Range under Overwatch control relative to the turret's own (user 2026-10-21: the override reaches farther). */
    public static float overwatchRange() {
        return OVERWATCH_RANGE_MULTIPLIER.get().floatValue();
    }

    /** FE per tick of a formed Control Hub with a tier 1-3 satellite. */
    public static int controlHubUpkeep(int tier) {
        return tierInt(CONTROL_HUB_UPKEEP, tier, 0);
    }

    /** Chunks per side a Control Hub reaches with a tier 1-3 satellite; always odd, so the satellite's chunk is the middle one. */
    public static int controlHubRange(int tier) {
        int side = Math.max(1, tierInt(CONTROL_HUB_RANGE, tier, 1));
        return side % 2 == 0 ? side + 1 : side;
    }

    private static int tierInt(ForgeConfigSpec.ConfigValue<List<? extends Number>> list, int tier, int fallback) {
        List<? extends Number> values = list.get();
        int index = Mth.clamp(tier, 1, 3) - 1;
        return index < values.size() ? values.get(index).intValue() : fallback;
    }
}
