package dev.bastion.weapon;

import dev.bastion.config.BastionConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Repulsors' push (PLAN "Repulsor & Repulsor Dome"): no damage, a launch away from a point so the mob lands about
 * {@code blocks} away on flat ground. Heavy mobs (config repulsorHeavyMobs) go repulsorHeavyMobPush of that; others are
 * shortened by their knockback resistance (1 = not moved at all). The hop is the same for all, so the distance scales
 * evenly.
 */
public final class Repulsion {
    /** Horizontal launch speed (blocks/tick) per block of travel at lift 0.35; measured by repulsionTravelsAsSet. */
    public static final double SPEED_PER_BLOCK = 0.2;

    private Repulsion() {
    }

    /** Too big or heavy for the Repulsor (config repulsorHeavyMobs): iron golems, ravagers, wardens, whatever packs add. */
    public static boolean heavy(LivingEntity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && BastionConfig.REPULSOR_HEAVY_MOBS.get().contains(id.toString());
    }

    /**
     * Pushes {@code target} horizontally away from {@code from} (or along {@code fallback} when it stands right on that
     * point), {@code lift} up. False when it resists knockback fully and did not move.
     */
    public static boolean push(LivingEntity target, Vec3 from, Vec3 fallback, float blocks, float lift) {
        double keep = heavy(target) ? BastionConfig.REPULSOR_HEAVY_PUSH.get()
                : 1 - Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1);
        if (keep <= 0) return false;
        Vec3 away = new Vec3(target.getX() - from.x, 0, target.getZ() - from.z);
        if (away.lengthSqr() < 1e-4) away = new Vec3(fallback.x, 0, fallback.z);
        away = away.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : away.normalize(); // straight up or down: any way out
        double speed = blocks * SPEED_PER_BLOCK * keep;
        target.setDeltaMovement(away.x * speed, lift, away.z * speed);
        target.hasImpulse = true;
        target.hurtMarked = true; // the new motion reaches clients at once
        return true;
    }
}
