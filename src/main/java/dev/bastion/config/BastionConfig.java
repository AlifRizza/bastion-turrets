package dev.bastion.config;

import dev.bastion.turret.TurretTier;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Common (server-authoritative) config: base tier stats and protection (PLAN 4.1, 4.7). Weapon numbers live in JSON. */
public final class BastionConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue OWNER_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue TRUSTED_CAN_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue MOBS_ATTACK_TURRETS;
    public static final ForgeConfigSpec.DoubleValue LARGE_HEALTH_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue WORKSTATION_ENERGY_CAPACITY;
    public static final ForgeConfigSpec.IntValue WORKSTATION_MAX_INPUT;
    public static final ForgeConfigSpec.BooleanValue MORTAR_BLOCK_FIRE;
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
}
